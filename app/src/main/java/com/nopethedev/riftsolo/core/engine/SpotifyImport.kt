package com.nopethedev.riftsolo.core.engine

import com.nopethedev.riftsolo.core.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Spotify playlist / album import without a Spotify account or API key. Songs come in as catalogue tracks
 * (source "spotify") and are played through their YouTube match, like Spotify results from a LavaSrc node.
 */
object SpotifyImport {
    data class Ref(val type: String, val id: String) { val url get() = "https://open.spotify.com/$type/$id" }

    @Serializable
    data class Result(val name: String, val artworkUrl: String?, val tracks: List<Track>, val link: String)

    private val LINK = Regex("""(?:open\.spotify\.com/(?:intl-[a-z-]+/)?(?:embed/)?|spotify:)(playlist|album)[/:]([A-Za-z0-9]{22})""")

    /** Accepts share links (with ?si=...), intl- links, embed links and spotify: URIs. */
    fun parse(text: String): Ref? = LINK.find(text.trim())?.let { Ref(it.groupValues[1], it.groupValues[2]) }

    private val http by lazy { OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build() }

    /** The public embed player page carries the list as JSON (Next.js `__NEXT_DATA__`). */
    suspend fun fromEmbed(ref: Ref): Result = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://open.spotify.com/embed/${ref.type}/${ref.id}").header("User-Agent", OkHttpDownloader.WEB_UA).build()
        val html = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw EngineException("Spotify answered ${r.code}", EngineException.Code.NOT_FOUND)
            r.body.string()
        }
        parseEmbed(html, ref)
    }

    fun parseEmbed(html: String, ref: Ref): Result {
        val raw = Regex("""<script id="__NEXT_DATA__" type="application/json">(.+?)</script>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
            ?: throw EngineException("Spotify's page changed; couldn't read the playlist", EngineException.Code.FAILED)
        val entity = JSONObject(raw).optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("entity")
            ?: throw EngineException("This playlist is private or doesn't exist", EngineException.Code.NOT_FOUND)
        val list = entity.optJSONArray("trackList") ?: throw EngineException("This playlist is empty", EngineException.Code.NOT_FOUND)
        val cover = entity.optJSONObject("coverArt")?.optJSONArray("sources")?.optJSONObject(0)?.optString("url")
        val tracks = (0 until list.length()).mapNotNull { i ->
            val t = list.optJSONObject(i) ?: return@mapNotNull null
            val id = t.optString("uri").substringAfterLast(':').ifBlank { return@mapNotNull null }
            Track(
                id = "spotify:$id",
                title = t.optString("title"),
                artist = t.optString("subtitle").replace(' ', ' '),
                durationSec = (t.optLong("duration") / 1000).toInt(),
                url = "https://open.spotify.com/track/$id",
                artworkUrl = cover,
                source = "spotify",
                engine = "spotify",
            )
        }
        return Result(entity.optString("name").ifBlank { "Imported playlist" }, cover, tracks, ref.url)
    }
}
