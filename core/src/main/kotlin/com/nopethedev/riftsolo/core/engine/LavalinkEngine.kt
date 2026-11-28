package com.nopethedev.riftsolo.core.engine

import com.nopethedev.riftsolo.core.PlayableStream
import com.nopethedev.riftsolo.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import com.nopethedev.riftsolo.core.data.Cache
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Engine B: a Lavalink v4 node (for example the one bundled with a Rift server), REST only.
 * Port of the search half of Rift's worker/music/lavalink.ts + engines/lavalink.ts. Playback goes through the extractor on the
 * phone (see EngineHub.open), because Lavalink only emits audio into Discord-style voice connections.
 */
class LavalinkEngine(
    private val nodes: suspend () -> List<LavalinkNodeConfig>,
    private val cache: Cache? = null,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build(),
) : Engine {
    override val id = "lavalink"

    @Serializable
    data class NodeInfo(val version: String, val sourceManagers: List<String>, val plugins: List<String>, val latencyMs: Long = 0)

    /**
     * Free nodes ban clients that burst. Requests to one node go out one at a time, at least [MIN_GAP_MS] apart, and a
     * node that answered 429 is left alone for [COOL_DOWN_MS] (another node is used, or the cached copy).
     */
    private val gates = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val lastCall = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val coolUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private suspend fun <T> throttled(cfg: LavalinkNodeConfig, block: suspend () -> T): T {
        val until = coolUntil[cfg.id] ?: 0L
        if (System.currentTimeMillis() < until) throw EngineException("${cfg.name} is rate limiting. Trying again in ${(until - System.currentTimeMillis()) / 1000 + 1} s.", EngineException.Code.UNAVAILABLE)
        return gates.getOrPut(cfg.id) { Mutex() }.withLock {
            val wait = (lastCall[cfg.id] ?: 0L) + MIN_GAP_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            try { block() } finally { lastCall[cfg.id] = System.currentTimeMillis() }
        }
    }

    /** When each node last failed; a node that failed in the last 2 minutes is tried last (Rift's pool puts online nodes first). */
    private val failedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private fun ordered(list: List<LavalinkNodeConfig>): List<LavalinkNodeConfig> {
        val now = System.currentTimeMillis()
        return list.sortedBy { if (now - (failedAt[it.id] ?: 0L) < 120_000) 1 else 0 }
    }

    private suspend fun get(cfg: LavalinkNodeConfig, path: String, query: Map<String, String> = emptyMap()): String = throttled(cfg) { getNow(cfg, path, query) }

    private suspend fun getNow(cfg: LavalinkNodeConfig, path: String, query: Map<String, String>): String = withContext(Dispatchers.IO) {
        val base = cfg.url.trim().trimEnd('/').toHttpUrlOrNull() ?: throw EngineException("\"${cfg.url}\" isn't a valid node URL", EngineException.Code.UNAVAILABLE)
        val url = base.newBuilder().addPathSegments(path.trimStart('/')).apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val req = Request.Builder().url(url).header("Authorization", cfg.password).header("Client-Name", "rift-solo/0.1").build()
        try {
            http.newCall(req).execute().use { r ->
                val body = r.body.string()
                if (r.code == 429) coolUntil[cfg.id] = System.currentTimeMillis() + (r.header("Retry-After")?.toLongOrNull()?.times(1000) ?: COOL_DOWN_MS)
                if (r.code == 429) throw EngineException("${cfg.name} is rate limiting (too many requests). Wait a moment, or add another node.", EngineException.Code.UNAVAILABLE)
                if (!r.isSuccessful) throw EngineException("Lavalink GET ${url.encodedPath} -> ${r.code} ${body.take(160)}", if (r.code == 401 || r.code == 403) EngineException.Code.UNAVAILABLE else EngineException.Code.FAILED)
                body
            }
        } catch (e: EngineException) { throw e } catch (e: Exception) {
            throw EngineException("Can't reach the Lavalink node: ${e.message}", EngineException.Code.UNAVAILABLE, e)
        }
    }

    /** Node health for onboarding / Profile. Cached for 10 minutes unless [force] (Profile → Check all). */
    suspend fun info(cfg: LavalinkNodeConfig, force: Boolean = false): NodeInfo =
        cache?.getOrLoad("nodeinfo", cfg.url + "|" + cfg.password, NodeInfo.serializer(), 10 * Cache.MINUTE, staleForMs = 0, force = force) { infoNow(cfg) } ?: infoNow(cfg)

    private suspend fun infoNow(cfg: LavalinkNodeConfig): NodeInfo {
        val t0 = System.currentTimeMillis()
        val j = try { JSONObject(get(cfg, "v4/info")) } catch (e: Exception) { failedAt[cfg.id] = System.currentTimeMillis(); throw e }
        failedAt.remove(cfg.id)
        return NodeInfo(
            version = j.optJSONObject("version")?.optString("semver") ?: "?",
            sourceManagers = j.optJSONArray("sourceManagers").strings(),
            plugins = (0 until (j.optJSONArray("plugins")?.length() ?: 0)).map { j.getJSONArray("plugins").getJSONObject(it).optString("name") },
            latencyMs = System.currentTimeMillis() - t0,
        )
    }

    /** Each node's source managers (from /v4/info), for "auto" prefixes. Kept for a day: they rarely change. */
    private suspend fun sourcesOf(cfg: LavalinkNodeConfig): List<String> {
        cache?.get("nodeinfo", cfg.url + "|" + cfg.password, NodeInfo.serializer(), Cache.DAY)?.let { if (it.fresh || it.ageMs < 7 * Cache.DAY) return it.value.sourceManagers }
        return runCatching { info(cfg).sourceManagers }.getOrDefault(emptyList())
    }

    data class Loaded(val name: String?, val artworkUrl: String?, val tracks: List<Track>)

    /**
     * Loads a URL on the first node that can (Spotify / Deezer / Apple Music playlists through LavaSrc, YouTube mixes...).
     * [needsSource] skips nodes known not to have that source manager.
     */
    suspend fun loadUrl(url: String, needsSource: String? = null): Loaded {
        val list = ordered(nodes())
        if (list.isEmpty()) throw EngineException("No Lavalink node is enabled", EngineException.Code.UNAVAILABLE)
        var last: Exception? = null
        for (cfg in list) {
            if (needsSource != null) {
                val src = sourcesOf(cfg)
                if (src.isNotEmpty() && needsSource !in src) { last = EngineException("${cfg.name} can't open $needsSource links", EngineException.Code.UNAVAILABLE); continue }
            }
            try {
                val body = get(cfg, "v4/loadtracks", mapOf("identifier" to url))
                val j = JSONObject(body)
                val tracks = parseLoadResult(body)
                if (tracks.isEmpty()) { last = EngineException("Nothing found at that link", EngineException.Code.NOT_FOUND); continue }
                val data = j.optJSONObject("data")
                return Loaded(
                    data?.optJSONObject("info")?.optString("name")?.takeIf { it.isNotBlank() },
                    data?.optJSONObject("pluginInfo")?.optString("artworkUrl")?.takeIf { it.isNotBlank() && it != "null" },
                    tracks,
                )
            } catch (e: Exception) { last = e }
        }
        throw last ?: EngineException("No node could open that link", EngineException.Code.UNAVAILABLE)
    }

    /** Tries the enabled nodes in order (healthy first) until one answers, like Rift's searchNodes. */
    override suspend fun search(text: String, limit: Int): List<Track> {
        val list = ordered(nodes())
        if (list.isEmpty()) throw EngineException("No Lavalink node is enabled", EngineException.Code.UNAVAILABLE)
        var last: Exception? = null
        for (cfg in list) {
            try {
                val r = searchNode(cfg, text, limit)
                failedAt.remove(cfg.id)
                return r
            } catch (e: Exception) {
                failedAt[cfg.id] = System.currentTimeMillis()
                last = e
            }
        }
        throw EngineException("No Lavalink node answered (${last?.message?.take(120)})", EngineException.Code.UNAVAILABLE, last)
    }

    private suspend fun searchNode(cfg: LavalinkNodeConfig, text: String, limit: Int): List<Track> = coroutineScope {
        val sources = if ("auto" in cfg.searchPrefix.lowercase()) sourcesOf(cfg) else emptyList()
        val identifiers = if (text.startsWith("http")) listOf(text) else searchPrefixes(cfg.searchPrefix, sources).map { "$it:$text" }
        val settled = identifiers.map { id -> async { runCatching { parseLoadResult(get(cfg, "v4/loadtracks", mapOf("identifier" to id))) } } }.map { it.await() }
        if (settled.all { it.isFailure }) throw settled.first().exceptionOrNull()!!
        NewPipeEngine.interleave(settled.map { it.getOrDefault(emptyList()) })
            // Sources the phone can extract directly, or catalogue sources mirrored to YouTube when opened.
            .filter { it.url.startsWith("http") && (it.source in PLAYABLE_SOURCES || it.source in MIRRORED_SOURCES) }
            .take(limit)
    }

    /**
     * Lyrics from the first node with a lyrics plugin, as the plugin's raw JSON (parsed by Lyrics.parseLavalink), or null.
     * LavaLyrics (with LavaSrc / youtube-source providers) needs the track loaded first to get its encoded form;
     * DuncteBot's java-lyrics-plugin takes a YouTube video id or a search.
     */
    suspend fun lyricsRaw(track: Track): String? {
        for (cfg in ordered(nodes())) {
            val plugins = runCatching { info(cfg).plugins.map { it.lowercase() } }.getOrNull() ?: continue
            try {
                if (plugins.any { "lavalyrics" in it }) {
                    val enc = JSONObject(get(cfg, "v4/loadtracks", mapOf("identifier" to track.url))).let { j ->
                        when (j.optString("loadType")) {
                            "track" -> j.optJSONObject("data")?.optString("encoded")
                            "search" -> j.optJSONArray("data")?.optJSONObject(0)?.optString("encoded")
                            else -> null
                        }
                    }?.takeIf { it.isNotBlank() } ?: continue
                    val body = lyricsGet(cfg, "v4/lyrics", mapOf("track" to enc, "skipTrackSource" to "false"))
                    if (body != null) return body
                }
                if (plugins.any { "java-lyrics" in it || it == "lyrics" }) {
                    val vid = NewPipeEngine.youtubeId(track.url)
                    val body = if (vid != null) lyricsGet(cfg, "v4/lyrics/$vid", emptyMap())
                    else lyricsGet(cfg, "v4/lyrics/search", mapOf("query" to "${track.artist} ${track.title}", "source" to "youtube"))
                    if (body != null) return body
                }
            } catch (e: EngineException) { if (e.code == EngineException.Code.UNAVAILABLE) continue }
        }
        return null
    }

    /** Lyrics endpoints answer 404 / 204 / an empty body when a song has none. */
    private suspend fun lyricsGet(cfg: LavalinkNodeConfig, path: String, q: Map<String, String>): String? = try {
        get(cfg, path, q).takeIf { it.isNotBlank() && it != "null" }
    } catch (e: EngineException) { if (e.code == EngineException.Code.FAILED) null else throw e }

    override suspend fun open(track: Track): PlayableStream =
        throw EngineException("Lavalink tracks are played through the extractor on this device", EngineException.Code.UNAVAILABLE)

    companion object {
        const val MIN_GAP_MS = 400L
        const val COOL_DOWN_MS = 45_000L
        val PLAYABLE_SOURCES = setOf("youtube", "soundcloud", "bandcamp", "vimeo", "http", "twitch")
        /** LavaSrc catalogue sources: metadata only, played through a YouTube match. */
        val MIRRORED_SOURCES = setOf("spotify", "applemusic", "deezer", "tidal", "yandexmusic", "amazonmusic")

        /** Same rules as Rift's shared/lavalink-search.ts: comma/space separated prefixes, max 6, default scsearch. */
        /** Source manager -> search prefixes used by "auto": song-focused sources whose audio the phone can fetch. */
        val AUTO_PREFIXES = linkedMapOf("youtube" to listOf("ytmsearch"), "soundcloud" to listOf("scsearch"))

        /**
         * `setting` holds one prefix, several separated by commas/spaces, or `auto` = one prefix per source the node
         * reports (Rift's shared/lavalink-search.ts). Empty = scsearch.
         */
        fun searchPrefixes(setting: String?, nodeSources: List<String> = emptyList()): List<String> {
            val out = LinkedHashSet<String>()
            for (tok in setting.orEmpty().lowercase().split(Regex("[\\s,;]+")).filter(String::isNotEmpty)) {
                if (tok == "auto") {
                    val src = nodeSources.map { it.lowercase() }.ifEmpty { AUTO_PREFIXES.keys.toList() }
                    AUTO_PREFIXES.forEach { (s, p) -> if (s in src) out += p }
                } else if (Regex("^[a-z]{2,20}$").matches(tok)) out += tok
            }
            return (out.ifEmpty { linkedSetOf("scsearch") }).take(6)
        }

        fun parseLoadResult(json: String): List<Track> {
            val j = JSONObject(json)
            val tracks: JSONArray = when (j.optString("loadType")) {
                "search" -> j.optJSONArray("data") ?: JSONArray()
                "track" -> JSONArray().put(j.getJSONObject("data"))
                "playlist" -> j.optJSONObject("data")?.optJSONArray("tracks") ?: JSONArray()
                "error" -> throw EngineException("Lavalink: ${j.optJSONObject("data")?.optString("message") ?: "load error"}")
                else -> JSONArray()
            }
            return (0 until tracks.length()).mapNotNull { i ->
                val info = tracks.optJSONObject(i)?.optJSONObject("info") ?: return@mapNotNull null
                if (info.optBoolean("isStream")) return@mapNotNull null
                val source = info.optString("sourceName")
                Track(
                    id = "lavalink:$source:${info.optString("identifier")}",
                    title = info.optString("title"),
                    artist = info.optString("author"),
                    durationSec = (info.optLong("length") / 1000).toInt(),
                    url = info.optString("uri"),
                    artworkUrl = info.optString("artworkUrl").takeIf { it.isNotEmpty() && it != "null" },
                    source = source,
                    engine = "lavalink",
                )
            }
        }

        private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }
    }
}
