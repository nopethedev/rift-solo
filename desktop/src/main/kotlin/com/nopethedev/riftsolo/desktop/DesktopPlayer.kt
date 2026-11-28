package com.nopethedev.riftsolo.desktop

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
    val loading: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
    val volume: Int = 80,
) {
    val current: Track? get() = queue.getOrNull(index)
    val hasNext get() = index in 0 until queue.lastIndex
}

/**
 * The desktop queue, same rules as the phone's PlayerController: a song is resolved right before it plays (EngineHub:
 * cache → extractor → alternate uploads), listening stats feed the taste profile, autoplay continues when the queue ends.
 */
class DesktopPlayer(
    private val engines: EngineHub,
    private val library: LibraryStore,
    private val scope: CoroutineScope,
    private val audio: LocalAudio = LocalAudio(),
    initialVolume: Int = 80,
) {
    var autoplay: (suspend (last: Track, queue: List<Track>) -> List<Track>)? = null
    private val _state = MutableStateFlow(PlayerState(volume = initialVolume))
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private var job: Job? = null
    private var listening: Track? = null
    private var retried = false

    init {
        audio.volume = initialVolume
        audio.onEnded = { finished, error ->
            scope.launch {
                if (finished) { finishCurrent(completed = true); next(fromEnd = true) }
                else if (!retried) { retried = true; _state.value.current?.let { load(it, _state.value.positionMs, fresh = true) } }
                else _state.update { it.copy(isPlaying = false, loading = false, error = error ?: "Playback stopped") }
            }
        }
        // Position ticker for the UI and Discord.
        scope.launch {
            while (isActive) {
                delay(500)
                val p = audio.positionMs
                val d = audio.durationMs
                _state.update { s -> if (s.loading) s else s.copy(positionMs = p, durationMs = if (d > 0) d else s.durationMs, isPlaying = audio.playing) }
            }
        }
    }

    private fun finishCurrent(completed: Boolean = false) {
        val t = listening ?: return
        listening = null
        val s = _state.value
        val dur = if (s.durationMs > 0) s.durationMs else t.durationSec * 1000L
        when {
            completed || (dur > 0 && s.positionMs >= dur * 0.8) -> library.listen(t, Listen.COMPLETED)
            s.positionMs < 30_000 && (dur <= 0 || s.positionMs < dur / 2) -> library.listen(t, Listen.SKIPPED)
        }
    }

    fun play(queue: List<Track>, start: Int) {
        if (queue.isEmpty()) return
        finishCurrent()
        val i = start.coerceIn(queue.indices)
        _state.update { it.copy(queue = queue, index = i, error = null) }
        retried = false
        load(queue[i], 0)
    }

    private fun load(track: Track, startAt: Long, fresh: Boolean = false) {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null, positionMs = startAt, durationMs = track.durationSec * 1000L) }
        job = scope.launch {
            try {
                if (fresh) engines.forgetStream(track)
                val stream = engines.open(track, fresh)
                try { audio.play(stream.url, startAt) } catch (e: Exception) {
                    if (fresh) throw e
                    // A cached link that stopped working: extract again once.
                    engines.forgetStream(track)
                    audio.play(engines.open(track, fresh = true).url, startAt)
                }
                _state.update { it.copy(loading = false, isPlaying = true, error = audio.lastOutputError) }
                if (startAt == 0L) { library.played(track); library.listen(track, Listen.STARTED); listening = track }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _state.update { it.copy(loading = false, isPlaying = false, error = e.message ?: "Couldn't play this song") }
            }
        }
    }

    fun toggle() {
        val s = _state.value
        if (s.current == null) return
        if (audio.durationMs == 0L && !s.loading) { load(s.current!!, s.positionMs); return }
        audio.paused = !audio.paused
        _state.update { it.copy(isPlaying = !audio.paused) }
    }

    fun seek(ms: Long) { audio.seek(ms); _state.update { it.copy(positionMs = ms) } }
    fun setVolume(v: Int) { audio.volume = v; _state.update { it.copy(volume = v) } }

    fun next(fromEnd: Boolean = false) {
        if (!fromEnd) finishCurrent()
        val s = _state.value
        if (!s.hasNext) {
            val more = autoplay; val cur = s.current
            if (more != null && cur != null) {
                scope.launch {
                    val add = runCatching { more(cur, s.queue) }.getOrDefault(emptyList())
                    if (add.isNotEmpty() && _state.value.current?.id == cur.id) {
                        _state.update { it.copy(queue = it.queue + add) }
                        next(fromEnd = true)
                    } else { audio.stop(); _state.update { it.copy(isPlaying = false, positionMs = 0) } }
                }
            } else { audio.stop(); _state.update { it.copy(isPlaying = false, positionMs = 0) } }
            return
        }
        _state.update { it.copy(index = s.index + 1) }
        retried = false
        load(s.queue[s.index + 1], 0)
    }

    fun previous() {
        val s = _state.value
        if (s.positionMs > 3000 || s.index <= 0) { seek(0); return }
        finishCurrent()
        _state.update { it.copy(index = s.index - 1) }
        retried = false
        load(s.queue[s.index - 1], 0)
    }

    fun jumpTo(i: Int) { val q = _state.value.queue; if (i in q.indices) { finishCurrent(); _state.update { it.copy(index = i) }; retried = false; load(q[i], 0) } }

    fun playNext(t: Track) {
        if (_state.value.current == null) { play(listOf(t), 0); return }
        _state.update { st -> st.copy(queue = st.queue.toMutableList().apply { add((st.index + 1).coerceAtMost(size), t) }) }
    }

    fun addToQueue(t: Track) {
        if (_state.value.current == null) { play(listOf(t), 0); return }
        _state.update { it.copy(queue = it.queue + t) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
    fun shutdown() { finishCurrent(); audio.shutdown() }
}
