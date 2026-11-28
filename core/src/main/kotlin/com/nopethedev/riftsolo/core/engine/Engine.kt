package com.nopethedev.riftsolo.core.engine

import com.nopethedev.riftsolo.core.EngineChoice
import com.nopethedev.riftsolo.core.PlayableStream
import com.nopethedev.riftsolo.core.SourceProvider
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.data.Cache
import kotlinx.serialization.builtins.ListSerializer

/**
 * Same shape as Rift's worker engines (worker/music/engines/types.ts): an engine can search and can open a track.
 * On a phone, "open" returns a stream URL for ExoPlayer instead of a byte stream for ffmpeg.
 */
interface Engine {
    val id: String
    suspend fun search(text: String, limit: Int): List<Track>
    suspend fun open(track: Track): PlayableStream
}

class EngineException(message: String, val code: Code = Code.FAILED, cause: Throwable? = null) : Exception(message, cause) {
    enum class Code { UNAVAILABLE, NOT_FOUND, FAILED }
}

/** Settings the engines need, read fresh for each call (like Rift sends MusicConfig with every request). */
data class EngineConfig(
    val choice: EngineChoice = EngineChoice.LAVALINK,
    val providers: List<SourceProvider> = listOf(SourceProvider.YOUTUBE, SourceProvider.SOUNDCLOUD),
    /** Enabled nodes, in the user's order. */
    val lavalink: List<LavalinkNodeConfig> = emptyList(),
)

data class LavalinkNodeConfig(val url: String, val password: String, val searchPrefix: String = "auto", val id: String = url, val name: String = url)

/** The engines to try, in order. "auto" = Lavalink only when a node is configured, then the phone (Rift's engineChain). */
fun engineChain(cfg: EngineConfig, phone: Engine, lavalink: Engine): List<Engine> = when (cfg.choice) {
    EngineChoice.AUTO -> if (cfg.lavalink.isNotEmpty()) listOf(lavalink, phone) else listOf(phone)
    EngineChoice.PHONE -> listOf(phone)
    EngineChoice.LAVALINK -> listOf(lavalink)
}

/** Front door used by the UI and the player: runs the chain, falls back when an engine can't deliver, caches everything. */
class EngineHub(
    private val config: suspend () -> EngineConfig,
    val phone: NewPipeEngine,
    val lavalink: LavalinkEngine,
    private val cache: Cache? = null,
) {
    private val tracks = ListSerializer(Track.serializer())

    /**
     * Search results are cached for [SEARCH_TTL] per engine choice and query, so going back to a search, the home feed
     * and "similar songs" don't hit the node again. If the node fails (rate limit, offline), the last results for that
     * query are shown instead, however old.
     */
    private fun searchKey(cfg: EngineConfig, text: String) = "${cfg.choice.id}|${cfg.providers.joinToString(",") { it.id }}|${Cache.norm(text)}"

    /** Fresh cached results for [text], without touching the network (null if none). */
    suspend fun cachedSearch(text: String, limit: Int = 25): List<Track>? =
        cache?.get("search", searchKey(config(), text), tracks, SEARCH_TTL)?.takeIf { it.fresh }?.value?.take(limit)

    suspend fun search(text: String, limit: Int = 20, force: Boolean = false): List<Track> {
        val cfg = config()
        val key = searchKey(cfg, text)
        val n = maxOf(limit, SEARCH_FETCH)
        val load: suspend () -> List<Track> = { searchNow(cfg, text, n) }
        val r = cache?.getOrLoad("search", key, tracks, SEARCH_TTL, force = force, load = load) ?: load()
        return r.take(limit)
    }

    private suspend fun searchNow(cfg: EngineConfig, text: String, limit: Int): List<Track> {
        val chain = engineChain(cfg, phone, lavalink)
        var last: Exception? = null
        for (e in chain) {
            try {
                val r = e.search(text, limit)
                if (r.isNotEmpty() || e === chain.last()) return r
            } catch (ex: Exception) { last = ex }
        }
        throw last ?: EngineException("No engine available", EngineException.Code.UNAVAILABLE)
    }

    /**
     * Audio is always extracted on the device (NewPipe Extractor). A Lavalink node is used to find songs (its search is
     * fast and plugin-rich); turning its track into sound would need Rift's voice-sink bridge, which a phone can't host
     * for a remote node, so the track's page URL is handed to the extractor instead.
     *
     * Tracks from catalogue-only sources (Spotify, Deezer, Apple Music... via LavaSrc, Spotify imports) have no audio of
     * their own, so they're mirrored: the best YouTube match for "artist title" is played, like LavaSrc does on a server.
     * The match is remembered, and the stream URL is reused until YouTube's expiry, so replaying a song costs nothing.
     */
    suspend fun open(track: Track, fresh: Boolean = false): PlayableStream {
        if (!fresh) cachedStream(track.url)?.let { return it }
        var last: Exception? = null
        val tried = HashSet<String>()
        val mirrored = track.source in LavalinkEngine.MIRRORED_SOURCES
        if (!mirrored) {
            try { return openAndKeep(track, track) } catch (e: EngineException) { last = e; tried += track.url }
        } else {
            cache?.getShared("mirror", track.url, Track.serializer(), MIRROR_TTL)?.let { m ->
                if (!fresh) cachedStream(m.url)?.let { return it }
                try { return openAndKeep(m, track) } catch (e: EngineException) { last = e; tried += m.url }
            }
        }
        // That upload can't be played here (region/label restrictions, removed...), or it's a catalogue-only track:
        // try other recordings of the same song, like Rift's resolver keeps the next-best candidates.
        val q = "${track.artist} ${track.title}".trim()
        // Song uploads (YouTube Music "Topic" tracks) and regular video uploads are licensed differently: when one
        // kind is refused, the other often plays. Video uploads come first (and from the phone's own search, so the
        // rate-limited node isn't asked again); the node's results are only used if YouTube search finds nothing.
        val alternates = runCatching { cachedList("ytvideos", q, MIRROR_TTL) { phone.searchVideos(q, 6) } }.getOrDefault(emptyList())
            .ifEmpty { runCatching { search(q, 8) }.getOrDefault(emptyList()) }
            .distinctBy { it.url }
            .filter { it.url !in tried && it.source !in LavalinkEngine.MIRRORED_SOURCES && it.source in PLAYABLE }
            .filter { track.durationSec <= 0 || it.durationSec <= 0 || kotlin.math.abs(it.durationSec - track.durationSec) <= 25 }
            .take(4)
        for (alt in alternates) {
            try {
                val s = openAndKeep(alt, track)
                if (mirrored) cache?.put("mirror", track.url, Track.serializer(), alt)
                return s
            } catch (e: EngineException) { last = e }
        }
        throw last ?: EngineException("Couldn't find a playable recording of \"${track.title}\"", EngineException.Code.NOT_FOUND)
    }

    /** Extracts [t], keeps the stream until it expires and the page's related songs as "similar to [original]". */
    private suspend fun openAndKeep(t: Track, original: Track): PlayableStream {
        val (stream, related) = phone.openWithRelated(t)
        cache?.put("stream", t.url, PlayableStream.serializer(), stream, expiresAt = streamExpiry(stream.url))
        if (related.size >= 5) {
            cache?.put("related", t.url, tracks, related)
            if (original.url != t.url) cache?.put("related", original.url, tracks, related)
        }
        return stream
    }

    private suspend fun cachedStream(url: String): PlayableStream? =
        cache?.getShared("stream", url, PlayableStream.serializer(), STREAM_TTL)

    /** The player calls this when a cached stream fails, so the next try extracts again. */
    suspend fun forgetStream(track: Track) {
        cache?.remove("stream", track.url)
        cache?.get("mirror", track.url, Track.serializer(), Long.MAX_VALUE)?.let { cache.remove("stream", it.value.url) }
    }

    private suspend fun cachedList(ns: String, key: String, ttl: Long, load: suspend () -> List<Track>): List<Track> =
        cache?.getOrLoad(ns, Cache.norm(key), tracks, ttl, load = load) ?: load()

    /**
     * Songs similar to [track], for "Recommended" in playlists, the home feed and autoplay. Uses YouTube's mix/related
     * songs (cached for days; free when the song was already played), then a search for the artist as a last resort.
     */
    suspend fun similar(track: Track): List<Track> = cachedList("related", track.url, RELATED_TTL) {
        val ytSeed = when {
            NewPipeEngine.youtubeId(track.url) != null || track.source == "soundcloud" -> track
            else -> cache?.get("mirror", track.url, Track.serializer(), MIRROR_TTL)?.value
                ?: runCatching { cachedList("ytvideos", "${track.artist} ${track.title}", MIRROR_TTL) { phone.searchVideos("${track.artist} ${track.title}", 6) } }.getOrNull()?.firstOrNull()
        }
        val found = ytSeed?.let { runCatching { phone.similar(it) }.getOrNull() }.orEmpty()
        found.ifEmpty { if (track.artist.isNotBlank()) search(track.artist, 15) else emptyList() }
    }

    /**
     * Imports a Spotify playlist or album from its link. A Lavalink node with LavaSrc reads it in one request (the whole
     * playlist); without one, the public embed page is read (its first 100 songs). Cached for a day.
     */
    suspend fun importSpotify(link: String): SpotifyImport.Result {
        val ref = SpotifyImport.parse(link) ?: throw EngineException("That doesn't look like a Spotify playlist or album link", EngineException.Code.NOT_FOUND)
        val load: suspend () -> SpotifyImport.Result = {
            val viaNode = runCatching { lavalink.loadUrl(ref.url, needsSource = "spotify") }
            viaNode.getOrNull()?.let { SpotifyImport.Result(it.name ?: "Imported playlist", it.artworkUrl, it.tracks, ref.url) }
                ?: runCatching { SpotifyImport.fromEmbed(ref) }.getOrElse { e ->
                    throw EngineException("Couldn't read that playlist. Is it public? (${viaNode.exceptionOrNull()?.message?.take(80) ?: e.message})", EngineException.Code.NOT_FOUND, e)
                }
        }
        return cache?.getOrLoad("spotify", ref.url, SpotifyImport.Result.serializer(), Cache.DAY, load = load) ?: load()
    }

    private companion object {
        val PLAYABLE = setOf("youtube", "soundcloud", "bandcamp")
        const val SEARCH_FETCH = 25
        const val SEARCH_TTL = 3 * Cache.DAY
        const val STREAM_TTL = 5 * Cache.HOUR
        const val MIRROR_TTL = 60 * Cache.DAY
        const val RELATED_TTL = 7 * Cache.DAY

        /** YouTube stream URLs carry their expiry (`expire=<unix seconds>`); stop using them 15 minutes before it. */
        fun streamExpiry(url: String): Long {
            val exp = Regex("[?&/]expire[=/](\\d{9,11})").find(url)?.groupValues?.get(1)?.toLongOrNull()
            return if (exp != null) exp * 1000 - 15 * Cache.MINUTE else System.currentTimeMillis() + Cache.HOUR
        }
    }
}
