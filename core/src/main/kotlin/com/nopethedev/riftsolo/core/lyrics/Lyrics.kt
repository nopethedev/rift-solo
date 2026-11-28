package com.nopethedev.riftsolo.core.lyrics

import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.data.Cache
import com.nopethedev.riftsolo.core.engine.LavalinkEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@Serializable
data class Lyrics(
    /** Timed lines (timeMs set) or plain lines (timeMs null). Empty = this song has no lyrics anywhere we looked. */
    val lines: List<Line> = emptyList(),
    /** Who supplied them, shown under the lyrics ("YouTube Music via Lavalink", "LRCLIB"...). */
    val source: String = "",
) {
    @Serializable
    data class Line(val text: String, val timeMs: Long? = null)

    val synced get() = lines.isNotEmpty() && lines.all { it.timeMs != null }
    val found get() = lines.isNotEmpty()

    /** Index of the line playing at [positionMs] (synced lyrics), or -1 before the first. */
    fun lineAt(positionMs: Long): Int {
        if (!synced) return -1
        var lo = 0; var hi = lines.size - 1; var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs!! <= positionMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}

/**
 * Lyrics for the player. Asks the Lavalink nodes first (LavaLyrics / java-lyrics-plugin, when a node has one), then
 * LRCLIB (free, open database of synced lyrics). Results, including "none found", are cached for a month and shared.
 */
class LyricsService(
    private val lavalink: LavalinkEngine,
    private val cache: Cache?,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build(),
    private val lrclibBase: String = "https://lrclib.net",
) {
    suspend fun get(track: Track, force: Boolean = false): Lyrics {
        val key = "${Cache.norm(cleanArtist(track.artist))}|${Cache.norm(cleanTitle(track.title, track.artist))}"
        val load: suspend () -> Lyrics = { fetch(track) }
        return cache?.getOrLoad("lyrics", key, Lyrics.serializer(), if (force) 0 else 30 * Cache.DAY, staleForMs = 30 * Cache.DAY, load = load) ?: load()
    }

    private suspend fun fetch(track: Track): Lyrics {
        try {
            lavalink.lyricsRaw(track)?.let { parseLavalink(it) }?.takeIf { it.found }?.let { return it }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        return lrclib(track)
    }

    private suspend fun lrclib(track: Track): Lyrics = withContext(Dispatchers.IO) {
        val artist = cleanArtist(track.artist)
        val title = cleanTitle(track.title, track.artist)
        val exact = "$lrclibBase/api/get".toHttpUrl().newBuilder().addQueryParameter("artist_name", artist).addQueryParameter("track_name", title)
            .apply { if (track.durationSec > 0) addQueryParameter("duration", track.durationSec.toString()) }.build()
        fetchJson(exact.toString())?.let { o -> parseLrclib(JSONObject(o)).takeIf { it.found }?.let { return@withContext it } }
        // No exact match (title has extra words, duration off by a few seconds): search, pick the closest duration.
        val search = "$lrclibBase/api/search".toHttpUrl().newBuilder().addQueryParameter("track_name", title).addQueryParameter("artist_name", artist).build()
        val arr = fetchJson(search.toString())?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return@withContext Lyrics()
        val best = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .filter { track.durationSec <= 0 || kotlin.math.abs(it.optDouble("duration", 0.0) - track.durationSec) < 8 }
            .sortedBy { if (it.optString("syncedLyrics").isNullOrBlank() || it.isNull("syncedLyrics")) 1 else 0 }
            .firstOrNull() ?: return@withContext Lyrics()
        parseLrclib(best)
    }

    private fun fetchJson(url: String): String? = http.newCall(Request.Builder().url(url).header("User-Agent", "RiftSolo (github.com/nopethedev/rift-solo)").build()).execute().use { r ->
        if (r.code == 404) null else if (!r.isSuccessful) throw java.io.IOException("LRCLIB answered ${r.code}") else r.body.string()
    }

    companion object {
        /** LavaLyrics: {text, lines:[{timestamp, line}], provider, sourceName}. java-lyrics-plugin: {type, text, lines:[{line, range:{start}}], source}. */
        fun parseLavalink(json: String): Lyrics {
            val o = runCatching { JSONObject(json) }.getOrNull()
                ?: runCatching { JSONArray(json).optJSONObject(0) }.getOrNull() ?: return Lyrics()
            val provider = listOf("provider", "sourceName", "source").map { o.optString(it) }.firstOrNull { it.isNotBlank() && it != "null" } ?: "Lavalink"
            val arr = o.optJSONArray("lines")
            val lines = if (arr != null && arr.length() > 0) (0 until arr.length()).mapNotNull { i ->
                val l = arr.optJSONObject(i) ?: return@mapNotNull null
                val text = l.optString("line").ifBlank { l.optString("text") }
                val t = when {
                    l.has("timestamp") -> l.optLong("timestamp")
                    l.optJSONObject("range") != null -> l.optJSONObject("range")!!.optLong("start")
                    else -> null
                }
                Lyrics.Line(text.trim(), t)
            } else plain(o.optString("text").takeIf { it != "null" }.orEmpty())
            return Lyrics(trimBlank(lines), "${prettyProvider(provider)} via Lavalink")
        }

        fun parseLrclib(o: JSONObject): Lyrics {
            val synced = o.optString("syncedLyrics").takeIf { !o.isNull("syncedLyrics") && it.isNotBlank() }
            if (synced != null) parseLrc(synced).takeIf { it.isNotEmpty() }?.let { return Lyrics(it, "LRCLIB") }
            val plainText = o.optString("plainLyrics").takeIf { !o.isNull("plainLyrics") }.orEmpty()
            return if (plainText.isBlank()) Lyrics() else Lyrics(trimBlank(plain(plainText)), "LRCLIB")
        }

        private val LRC = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

        /** "[01:02.50] words" → (62500 ms, "words"). Lines with several stamps repeat. */
        fun parseLrc(text: String): List<Lyrics.Line> = text.lines().flatMap { raw ->
            val stamps = LRC.findAll(raw).toList()
            if (stamps.isEmpty()) return@flatMap emptyList()
            val words = raw.substring(stamps.last().range.last + 1).trim()
            stamps.map { m ->
                val (mm, ss, frac) = m.destructured
                val ms = frac.takeIf { it.isNotEmpty() }?.let { it.padEnd(3, '0').take(3).toLong() } ?: 0
                Lyrics.Line(words, mm.toLong() * 60_000 + ss.toLong() * 1000 + ms)
            }
        }.sortedBy { it.timeMs }

        private fun plain(text: String) = text.lines().map { Lyrics.Line(it.trim()) }
        private fun trimBlank(l: List<Lyrics.Line>) = l.dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }
        private fun prettyProvider(p: String) = when (p.lowercase()) {
            "youtube", "youtubemusic", "youtube music" -> "YouTube Music"; "spotify" -> "Spotify"; "deezer" -> "Deezer"
            "genius" -> "Genius"; "applemusic" -> "Apple Music"; else -> p
        }

        private val NOISE = Regex("""\s*[(\[](?:[^)\]]*?(?:official|video|audio|lyrics?|visuali[sz]er|hd|hq|4k|remaster(?:ed)?|explicit|clean|mv|m/v|feat\.?|ft\.|featuring)[^)\]]*)[)\]]""", RegexOption.IGNORE_CASE)
        private val FEAT = Regex("""\s+(?:ft\.?|feat\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE)

        fun cleanArtist(a: String) = a.replace(Regex("""\s*-\s*topic$""", RegexOption.IGNORE_CASE), "").replace(Regex("VEVO$"), "")
            .split(Regex("""\s*(?:,|&| x | feat\.? | ft\.? )\s*""", RegexOption.IGNORE_CASE)).first().trim()

        /** "Rick Astley - Never Gonna Give You Up (Official Video) [4K]" → "Never Gonna Give You Up". */
        fun cleanTitle(title: String, artist: String = ""): String {
            var t = title.replace(NOISE, "")
            val a = cleanArtist(artist)
            val dash = Regex("""^(.+?)\s+[-–—]\s+(.+)$""").find(t)
            if (dash != null && (a.isBlank() || dash.groupValues[1].contains(a, ignoreCase = true) || a.contains(dash.groupValues[1], ignoreCase = true))) t = dash.groupValues[2]
            return t.replace(FEAT, "").replace(Regex("""\s*\|.*$"""), "").replace("\"", "").trim().ifBlank { title }
        }
    }
}
