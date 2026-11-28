package com.nopethedev.riftsolo.core.taste

import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.data.Cache
import com.nopethedev.riftsolo.core.data.Library
import com.nopethedev.riftsolo.core.engine.EngineHub
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

@Serializable
data class Artist(val name: String, val deezerId: Long? = null, val pictureUrl: String? = null, val fans: Long = 0)

@Serializable
data class Album(
    val title: String, val artist: String, val deezerId: Long, val coverUrl: String? = null,
    val year: String = "", val type: String = "album", val fans: Long = 0,
)

/** An artist or album suggested to the user, and which of their artists it came from ("Because you like X"). */
@Serializable
data class ArtistPick(val artist: Artist, val because: List<String>, val score: Double)

@Serializable
data class AlbumPick(val album: Album, val because: String)

@Serializable
data class Discovery(val artists: List<ArtistPick> = emptyList(), val albums: List<AlbumPick> = emptyList())

/**
 * "Fans of your artists also listen to…". Seeds are the artists the user follows and the ones they play most. For each,
 * Deezer's related-artists list (built from what that artist's fans listen to) is read, and artists that show up for
 * several seeds rank highest. YouTube's mixes for the user's top songs add a second, independent co-listening signal.
 * Artists already in heavy rotation are left out (this is for discovering), and each pick brings its best album.
 *
 * Deezer's public API needs no key; every answer is cached for a week (shared through Rift Server too).
 */
class ArtistGraph(
    private val hub: EngineHub?,
    private val cache: Cache?,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build(),
    private val base: String = "https://api.deezer.com",
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val gate = Mutex()
    private var lastCall = 0L

    // ---- Deezer ----
    private suspend fun deezer(path: String): JsonObject = gate.withLock {
        // Deezer allows 50 requests / 5 s; stay well under it.
        val wait = lastCall + 150 - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try {
            withContext(Dispatchers.IO) {
                http.newCall(Request.Builder().url("$base/$path").build()).execute().use { r ->
                    if (!r.isSuccessful) throw java.io.IOException("Deezer answered ${r.code}")
                    val o = json.parseToJsonElement(r.body.string()).jsonObject
                    o["error"]?.let { throw java.io.IOException("Deezer: ${it.jsonObject["message"]?.jsonPrimitive?.contentOrNull}") }
                    o
                }
            }
        } finally { lastCall = System.currentTimeMillis() }
    }

    private suspend fun <T> cached(key: String, s: kotlinx.serialization.KSerializer<T>, ttl: Long, load: suspend () -> T): T =
        cache?.getOrLoad("artist", key, s, ttl, load = load) ?: load()

    private fun JsonElement?.str(k: String) = (this as? JsonObject)?.get(k)?.jsonPrimitive?.contentOrNull
    private fun JsonElement?.long(k: String) = (this as? JsonObject)?.get(k)?.jsonPrimitive?.longOrNull
    private fun data(o: JsonObject): JsonArray = (o["data"] as? JsonArray) ?: JsonArray(emptyList())
    private fun artistOf(e: JsonElement) = Artist(e.str("name").orEmpty(), e.long("id"), e.str("picture_xl") ?: e.str("picture_big") ?: e.str("picture_medium"), e.long("nb_fan") ?: 0)

    /** The Deezer artist for a name (best match by name, then fans). */
    suspend fun findArtist(name: String): Artist? {
        val clean = Taste.artistKeys(name).firstOrNull() ?: return null
        val list = cached("find|$clean", ListSerializer(Artist.serializer()), 30 * Cache.DAY) {
            data(deezer("search/artist?limit=5&q=" + java.net.URLEncoder.encode(clean, "UTF-8"))).map(::artistOf)
        }
        return list.firstOrNull { Taste.artistKey(it.name) == clean } ?: list.firstOrNull()?.takeIf { similarName(it.name, clean) }
    }

    /** Artists Deezer's listeners pair with [a]. */
    suspend fun related(a: Artist): List<Artist> {
        val id = a.deezerId ?: findArtist(a.name)?.deezerId ?: return emptyList()
        return cached("related|$id", ListSerializer(Artist.serializer()), 7 * Cache.DAY) { data(deezer("artist/$id/related?limit=25")).map(::artistOf) }
    }

    suspend fun albums(a: Artist): List<Album> {
        val id = a.deezerId ?: findArtist(a.name)?.deezerId ?: return emptyList()
        return cached("albums|$id", ListSerializer(Album.serializer()), 7 * Cache.DAY) {
            data(deezer("artist/$id/albums?limit=25")).map { e ->
                Album(e.str("title").orEmpty(), a.name, e.long("id") ?: 0, e.str("cover_xl") ?: e.str("cover_big") ?: e.str("cover_medium"),
                    e.str("release_date").orEmpty().take(4), e.str("record_type") ?: "album", e.long("fans") ?: 0)
            }.filter { it.deezerId != 0L }
        }
    }

    suspend fun topTracks(a: Artist, limit: Int = 10): List<Track> {
        val id = a.deezerId ?: findArtist(a.name)?.deezerId ?: return emptyList()
        return cached("top|$id", ListSerializer(Track.serializer()), 7 * Cache.DAY) { data(deezer("artist/$id/top?limit=15")).mapNotNull { trackOf(it, null) } }.take(limit)
    }

    suspend fun albumTracks(album: Album): List<Track> = cached("album|${album.deezerId}", ListSerializer(Track.serializer()), 30 * Cache.DAY) {
        val o = deezer("album/${album.deezerId}")
        val cover = o.str("cover_xl") ?: o.str("cover_big") ?: album.coverUrl
        ((o["tracks"] as? JsonObject)?.let(::data) ?: JsonArray(emptyList())).mapNotNull { trackOf(it, cover) }
    }

    private fun trackOf(e: JsonElement, cover: String?): Track? {
        val id = e.long("id") ?: return null
        val artist = (e as JsonObject)["artist"].str("name").orEmpty()
        val art = cover ?: e["album"].str("cover_xl") ?: e["album"].str("cover_medium")
        return Track("deezer:$id", e.str("title_short") ?: e.str("title").orEmpty(), artist, e["duration"]?.jsonPrimitive?.intOrNull ?: 0,
            "https://www.deezer.com/track/$id", art, source = "deezer", engine = "catalog")
    }

    // ---- recommendations ----

    /** Display names for artist keys, from the songs in the library. */
    private fun names(lib: Library): Map<String, String> {
        val out = HashMap<String, String>()
        (lib.liked + lib.recent + lib.playlists.flatMap { it.tracks } + lib.stats.values.mapNotNull { it.track }).forEach { t ->
            val k = Taste.artistKeys(t.artist).firstOrNull() ?: return@forEach
            out.putIfAbsent(k, t.artist.replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "").trim())
        }
        lib.followed.forEach { out[it.key] = it.name }
        return out
    }

    data class Seed(val name: String, val weight: Double)

    fun seeds(lib: Library, profile: TasteProfile, max: Int = 8): List<Seed> {
        val names = names(lib)
        val followed = lib.followed.take(max).map { Seed(it.name, 3.0) }
        val top = profile.topArtists.filter { (k, _) -> lib.followed.none { it.key == k } }
        val best = top.firstOrNull()?.second ?: 1.0
        val played = top.take(max).mapNotNull { (k, v) -> names[k]?.let { Seed(it, 0.5 + 1.5 * v / best) } }
        // Followed artists first, then most-played, alternating so both count when there are many.
        val out = ArrayList<Seed>()
        val a = followed.iterator(); val b = played.iterator()
        while (out.size < max && (a.hasNext() || b.hasNext())) { if (a.hasNext()) out += a.next(); if (out.size < max && b.hasNext()) out += b.next() }
        return out.distinctBy { Taste.artistKey(it.name) }
    }

    /**
     * Artists (and one album each) for the user: what their artists' fans listen to.
     * Works without Deezer too (from YouTube mixes alone), just without albums.
     */
    suspend fun discover(lib: Library, profile: TasteProfile, artistLimit: Int = 12, albumLimit: Int = 8): Discovery {
        val seeds = seeds(lib, profile)
        if (seeds.isEmpty()) return Discovery()
        val known = profile.artists.filter { it.value > 6.0 }.keys + lib.followed.map { it.key } + seeds.map { Taste.artistKey(it.name) }
        val disliked = profile.artists.filter { it.value < 0 }.keys
        val score = HashMap<String, Double>()
        val because = HashMap<String, LinkedHashSet<String>>()
        val info = HashMap<String, Artist>()
        fun add(a: Artist, w: Double, seed: String) {
            val k = Taste.artistKey(a.name)
            if (k.isBlank() || k in known || k in disliked) return
            score.merge(k, w, Double::plus)
            because.getOrPut(k) { LinkedHashSet() } += seed
            val prev = info[k]
            if (prev == null || (prev.deezerId == null && a.deezerId != null)) info[k] = a
        }

        // 1) Deezer: "fans also like", per seed artist.
        for (s in seeds) {
            try {
                related(Artist(s.name)).forEachIndexed { i, r -> add(r, s.weight / sqrt(i + 1.0), s.name) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        // 2) YouTube mixes of their top songs: the artists that come up there are co-listened too.
        if (hub != null) for (t in profile.seeds.take(4)) {
            try {
                val mix = hub.similar(t)
                val seedName = t.artist.replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")
                mix.forEachIndexed { i, m -> add(Artist(m.artist.replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "").trim(), pictureUrl = null), 0.6 / sqrt(i + 1.0), seedName) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        // An artist several of your artists' fans like beats one that's big with a single seed's fans.
        val ranked = score.entries.map { (k, v) -> k to v * (1 + 0.35 * ((because[k]?.size ?: 1) - 1)) }
            .sortedByDescending { it.second }
            .filter { (k, _) -> info[k]!!.name.length in 2..60 && !k.contains("various") }
            .take(artistLimit)
        val artists = ranked.map { (k, v) -> ArtistPick(info[k]!!, because[k]!!.take(2), v) }

        // 3) An album from each of the best picks: their most popular full album (or newest release if none).
        val albums = ArrayList<AlbumPick>()
        for (p in artists) {
            if (albums.size >= albumLimit) break
            val list = try { albums(p.artist) } catch (e: CancellationException) { throw e } catch (_: Exception) { continue }
            val pick = list.filter { it.type == "album" }.maxByOrNull { it.fans } ?: list.firstOrNull() ?: continue
            albums += AlbumPick(pick, p.artist.name)
        }
        return Discovery(artists, albums)
    }

    private fun similarName(a: String, b: String): Boolean {
        val x = a.lowercase().filter(Char::isLetterOrDigit); val y = b.lowercase().filter(Char::isLetterOrDigit)
        return x.isNotEmpty() && (x == y || x.startsWith(y) || y.startsWith(x))
    }
}
