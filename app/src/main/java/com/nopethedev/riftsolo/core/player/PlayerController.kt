package com.nopethedev.riftsolo.core.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.data.LibraryStore
import com.nopethedev.riftsolo.core.data.Listen
import com.nopethedev.riftsolo.core.engine.EngineHub
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    /** Resolving the stream through the extractor, or buffering. */
    val loading: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
) {
    val current: Track? get() = queue.getOrNull(index)
    val hasNext get() = index in 0 until queue.lastIndex
}

/**
 * The app's queue. Like Rift's music cache, a song is resolved only when it's about to play (stream URLs expire), then
 * handed to the PlaybackService's ExoPlayer through a MediaController.
 */
class PlayerController(
    private val context: Context,
    private val engines: EngineHub,
    private val library: LibraryStore,
    private val scope: CoroutineScope,
) {
    /** Called when the queue runs out: returns songs to keep going with (Rift Solo's autoplay). */
    var autoplay: (suspend (last: Track, queue: List<Track>) -> List<Track>)? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private val waiting = ArrayList<(MediaController) -> Unit>()
    private var resolveJob: Job? = null
    private var retried = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { _state.update { it.copy(isPlaying = isPlaying) } }
        override fun onPlaybackStateChanged(s: Int) {
            _state.update { it.copy(loading = s == Player.STATE_BUFFERING, durationMs = controller?.duration?.takeIf { d -> d > 0 } ?: it.durationMs) }
            if (s == Player.STATE_ENDED) { finishCurrent(completed = true); next(fromEnd = true) }
        }
        override fun onPlayerError(error: PlaybackException) {
            // Stream URLs can expire (a paused song resumed hours later): re-resolve once before giving up.
            val cur = _state.value.current
            if (cur != null && !retried) { retried = true; load(cur, _state.value.positionMs, fresh = true) }
            else _state.update { it.copy(error = "Playback failed: ${error.errorCodeName}", loading = false) }
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            waiting.forEach { it(c) }
            waiting.clear()
        }, ContextCompat.getMainExecutor(context))
        scope.launch {
            while (isActive) {
                controller?.let { c -> if (c.isPlaying) _state.update { it.copy(positionMs = c.currentPosition, durationMs = c.duration.coerceAtLeast(0)) } }
                delay(500)
            }
        }
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let(block) ?: run { waiting += block; connect() }
    }

    /** The song being listened to and whether its listen was already counted as finished/skipped. */
    private var listening: Track? = null

    /** Finished = reached the end or 80 %; skipped = left within 30 s and before half of it. Feeds the taste profile. */
    private fun finishCurrent(completed: Boolean = false) {
        val t = listening ?: return
        listening = null
        val s = _state.value
        val dur = s.durationMs.takeIf { it > 0 } ?: (t.durationSec * 1000L)
        when {
            completed || (dur > 0 && s.positionMs >= dur * 0.8) -> library.listen(t, Listen.COMPLETED)
            s.positionMs < 30_000 && (dur <= 0 || s.positionMs < dur / 2) -> library.listen(t, Listen.SKIPPED)
        }
    }

    fun play(queue: List<Track>, start: Int) {
        if (queue.isEmpty()) return
        finishCurrent()
        _state.update { PlayerState(queue = queue, index = start.coerceIn(queue.indices)) }
        retried = false
        load(queue[start.coerceIn(queue.indices)], 0)
    }

    private fun load(track: Track, startAt: Long, fresh: Boolean = false) {
        resolveJob?.cancel()
        _state.update { it.copy(loading = true, error = null, positionMs = startAt, durationMs = track.durationSec * 1000L) }
        resolveJob = scope.launch {
            try {
                if (fresh) engines.forgetStream(track)
                val stream = engines.open(track, fresh)
                StreamHeaders.put(stream.url, stream.headers)
                val item = MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(stream.url)
                    .apply { stream.mimeType?.let { setMimeType(it) } }
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setArtworkUri(track.artworkUrl?.toUri()).build())
                    .build()
                withController { c -> c.setMediaItem(item, startAt); c.prepare(); c.play() }
                if (startAt == 0L) { library.played(track); library.listen(track, Listen.STARTED); listening = track }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't play this song") }
            }
        }
    }

    fun toggle() = withController { if (it.isPlaying) it.pause() else if (it.mediaItemCount > 0) it.play() else _state.value.current?.let { t -> load(t, 0) } }
    fun seekTo(ms: Long) { _state.update { it.copy(positionMs = ms) }; withController { it.seekTo(ms) } }

    fun next(fromEnd: Boolean = false) {
        if (!fromEnd) finishCurrent()
        val s = _state.value
        if (!s.hasNext) {
            val more = autoplay
            val cur = s.current
            if (more != null && cur != null) {
                scope.launch {
                    val add = runCatching { more(cur, s.queue) }.getOrDefault(emptyList())
                    if (add.isNotEmpty() && _state.value.current?.id == cur.id) {
                        _state.update { it.copy(queue = it.queue + add) }
                        next(fromEnd = true)
                    } else withController { it.pause(); it.seekTo(0) }
                }
                return
            }
            withController { it.pause(); it.seekTo(0) }; return
        }
        _state.update { it.copy(index = s.index + 1) }
        retried = false
        load(s.queue[s.index + 1], 0)
    }

    fun previous() {
        val s = _state.value
        if (s.positionMs > 3000 || s.index <= 0) { seekTo(0); return }
        finishCurrent()
        _state.update { it.copy(index = s.index - 1) }
        retried = false
        load(s.queue[s.index - 1], 0)
    }

    /** Song menu: right after the current song. */
    fun playNext(t: Track) {
        val s = _state.value
        if (s.current == null) { play(listOf(t), 0); return }
        _state.update { st -> st.copy(queue = st.queue.toMutableList().apply { add((st.index + 1).coerceAtMost(size), t) }) }
    }

    /** Song menu: at the end of the queue. */
    fun addToQueue(t: Track) {
        if (_state.value.current == null) { play(listOf(t), 0); return }
        _state.update { it.copy(queue = it.queue + t) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
