package com.nopethedev.riftsolo.desktop

import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry
import com.sedmelluq.discord.lavaplayer.format.AudioPlayerInputStream
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Plays an audio URL on the computer's speakers. Lavaplayer (what Lavalink itself runs on) fetches and decodes the
 * stream (MP4/AAC, WebM/Opus, MP3, HLS...) into PCM, which is written to the default sound output.
 */
class LocalAudio(output: Boolean = true) {
    private val manager = DefaultAudioPlayerManager().apply {
        configuration.outputFormat = StandardAudioDataFormats.COMMON_PCM_S16_BE
        registerSourceManager(HttpAudioSourceManager(MediaContainerRegistry.DEFAULT_REGISTRY))
        registerSourceManager(LocalAudioSourceManager())
    }
    private val player: AudioPlayer = manager.createPlayer()

    /** Called on the audio thread when a track ends by itself (finished, or failed while playing). */
    var onEnded: ((finished: Boolean, error: String?) -> Unit)? = null

    @Volatile private var outputError: String? = null
    val lastOutputError get() = outputError

    init {
        player.addListener(object : AudioEventAdapter() {
            override fun onTrackEnd(p: AudioPlayer, track: AudioTrack, reason: AudioTrackEndReason) {
                if (reason == AudioTrackEndReason.FINISHED || reason == AudioTrackEndReason.LOAD_FAILED) onEnded?.invoke(reason == AudioTrackEndReason.FINISHED, null)
            }
            override fun onTrackException(p: AudioPlayer, track: AudioTrack, e: FriendlyException) { onEnded?.invoke(false, e.message) }
        })
        // One output line for the whole session; it carries silence while nothing plays.
        if (output) Thread({
            try {
                val stream = AudioPlayerInputStream.createStream(player, manager.configuration.outputFormat, 10_000L, true)
                val line = AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, stream.format)) as SourceDataLine
                line.open(stream.format)
                line.start()
                val buf = ByteArray(StandardAudioDataFormats.COMMON_PCM_S16_BE.maximumChunkSize())
                while (true) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    line.write(buf, 0, n)
                }
            } catch (e: Throwable) {
                outputError = "No sound output: ${e.message ?: e.javaClass.simpleName}"
            }
        }, "rift-audio-out").apply { isDaemon = true; start() }
    }

    /** Opens [url] and starts it at [startMs]. Throws if Lavaplayer can't read it. */
    suspend fun play(url: String, startMs: Long = 0) {
        val track = load(url)
        if (startMs > 0) track.position = startMs
        player.isPaused = false
        player.playTrack(track)
    }

    suspend fun load(url: String): AudioTrack = suspendCancellableCoroutine { c ->
        manager.loadItemOrdered(this, url, object : AudioLoadResultHandler {
            override fun trackLoaded(track: AudioTrack) { c.resume(track) }
            override fun playlistLoaded(playlist: AudioPlaylist) {
                val t = playlist.selectedTrack ?: playlist.tracks.firstOrNull()
                if (t != null) c.resume(t) else c.resumeWithException(IllegalStateException("Empty playlist"))
            }
            override fun noMatches() { c.resumeWithException(IllegalStateException("This audio format isn't supported")) }
            override fun loadFailed(e: FriendlyException) { c.resumeWithException(IllegalStateException(e.message ?: "Couldn't open the audio", e)) }
        })
    }

    var paused: Boolean
        get() = player.isPaused
        set(v) { player.isPaused = v }

    val playing get() = player.playingTrack != null && !player.isPaused
    val positionMs get() = player.playingTrack?.position ?: 0L
    val durationMs get() = player.playingTrack?.duration?.takeIf { it != Long.MAX_VALUE } ?: 0L
    fun seek(ms: Long) { player.playingTrack?.let { if (it.isSeekable) it.position = ms } }
    fun stop() = player.stopTrack()
    /** 0..100. */
    var volume: Int
        get() = player.volume
        set(v) { player.volume = v.coerceIn(0, 100) }

    /** Decodes a few frames of [url] without playing it (tests and "Test" in settings). */
    suspend fun probe(url: String): Long {
        val t = load(url)
        return t.duration
    }

    /** Raw decoded PCM (16-bit big-endian, 48 kHz stereo), for tests that run without a sound card. */
    fun pcm(): javax.sound.sampled.AudioInputStream = AudioPlayerInputStream.createStream(player, manager.configuration.outputFormat, 10_000L, false)

    fun shutdown() { player.destroy(); manager.shutdown() }
}
