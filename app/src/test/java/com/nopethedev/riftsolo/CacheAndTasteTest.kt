package com.nopethedev.riftsolo

import com.nopethedev.riftsolo.core.Playlist
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.data.Cache
import com.nopethedev.riftsolo.core.data.Library
import com.nopethedev.riftsolo.core.data.ListenStat
import com.nopethedev.riftsolo.core.engine.SpotifyImport
import com.nopethedev.riftsolo.core.taste.Taste
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [35])
class CacheAndTasteTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun t(id: String, title: String, artist: String, d: Int = 200) = Track("youtube:$id", title, artist, d, "https://www.youtube.com/watch?v=$id", null, "youtube")
    private val ser = ListSerializer(Track.serializer())

    @Test fun cacheServesFreshThenStaleOnError() = runBlocking {
        var now = 1_000_000L
        val c = Cache(tmp.root) { now }
        var calls = 0
        val a = c.getOrLoad("search", "q", ser, maxAgeMs = 1000) { calls++; listOf(t("a", "A", "X")) }
        val b = c.getOrLoad("search", "q", ser, maxAgeMs = 1000) { calls++; emptyList() }
        assertEquals(1, calls); assertEquals(a, b)
        now += 5000 // expired: the loader runs, fails, and the old copy is returned instead of the error
        val stale = c.getOrLoad("search", "q", ser, maxAgeMs = 1000) { calls++; error("429") }
        assertEquals(2, calls); assertEquals(a, stale)
        // Survives a restart (new instance, same folder).
        assertNotNull(Cache(tmp.root) { now }.get("search", "q", ser, Long.MAX_VALUE))
    }

    @Test fun cacheSharesInflightRequests() = runBlocking {
        val c = Cache(tmp.root)
        var calls = 0
        val load: suspend () -> List<Track> = { calls++; delay(200); listOf(t("a", "A", "X")) }
        val x = async { c.getOrLoad("search", "same", ser, 1000, load = load) }
        val y = async { c.getOrLoad("search", "same", ser, 1000, load = load) }
        assertEquals(x.await(), y.await()); assertEquals(1, calls)
    }

    @Test fun cacheRespectsExpiry() = runBlocking {
        var now = 0L
        val c = Cache(tmp.root) { now }
        c.put("stream", "u", ser, emptyList(), expiresAt = 100)
        assertTrue(c.get("stream", "u", ser, Long.MAX_VALUE)!!.fresh)
        now = 200
        assertTrue(!c.get("stream", "u", ser, Long.MAX_VALUE)!!.fresh)
    }

    @Test fun artistAndSongKeys() {
        assertEquals("daft punk", Taste.artistKey("Daft Punk - Topic"))
        assertEquals("daft punk", Taste.artistKey("Daft Punk, Pharrell Williams"))
        assertEquals(listOf("daft punk", "pharrell williams"), Taste.artistKeys("Daft Punk feat. Pharrell Williams"))
        assertEquals(Taste.songKey(t("1", "Get Lucky (Official Video)", "Daft Punk")), Taste.songKey(t("2", "Get Lucky", "Daft Punk - Topic")))
        assertEquals(Taste.songKey(t("1", "Daft Punk - Get Lucky [Lyrics]", "Daft Punk")), Taste.songKey(t("2", "Get Lucky", "Daft Punk")))
    }

    @Test fun profileLearnsFromLikesPlaysAndSkips() {
        val liked = t("l1", "Nightcall", "Kavinsky")
        val skipped = t("s1", "Loud Song", "Noise Band")
        val now = System.currentTimeMillis()
        val lib = Library(
            liked = listOf(liked),
            recent = listOf(skipped, liked),
            playlists = listOf(Playlist("p", "Drive", listOf(t("p1", "Turbo Killer", "Carpenter Brut")), createdAt = now)),
            stats = mapOf(skipped.id to ListenStat(plays = 3, skips = 3, lastAt = now, track = skipped), liked.id to ListenStat(plays = 5, completes = 5, lastAt = now - 3 * 86_400_000L, track = liked)),
        )
        val p = Taste.build(lib, setOf("synthwave"), now)
        assertEquals("kavinsky", p.topArtists.first().first)
        assertTrue((p.artists["noise band"] ?: 0.0) < 0)
        assertTrue(skipped.id in p.skipped)
        assertEquals("synthwave", p.topGenres.first().first)
        assertEquals(liked.id, p.seeds.first().id)

        // Ranking: a known-good artist beats an unknown one, a skipped song and a 10-hour compilation sink, duplicates merge.
        val cands = Taste.fromList(listOf(
            t("c1", "10 Hours Synthwave Mix", "Some Channel", 36000),
            t("c2", "Random Pop Song", "Stranger"),
            t("c3", "Odd Look", "Kavinsky"),
            t("c3b", "Odd Look (Official Audio)", "Kavinsky - Topic"),
            skipped,
            t("c4", "Roller Mobster", "Carpenter Brut"),
        ))
        val ranked = Taste.rank(cands, p, 10)
        assertEquals(1, ranked.count { it.title.startsWith("Odd Look") })
        assertTrue(ranked.indexOfFirst { it.artist.startsWith("Kavinsky") } < ranked.indexOfFirst { it.artist == "Stranger" })
        assertTrue(ranked.last().id == skipped.id || ranked.last().id == "youtube:c1")
    }

    @Test fun rankKeepsArtistsVaried() {
        val p = Taste.build(Library(liked = listOf(t("x", "Song", "A"))), emptySet())
        val cands = Taste.fromList((1..6).map { t("a$it", "A song $it", "A") } + (1..3).map { t("b$it", "B song $it", "B") })
        val r = Taste.rank(cands, p, 6, perArtist = 2)
        assertTrue(r.count { it.artist == "A" } <= 2)
    }

    @Test fun spotifyLinks() {
        assertEquals("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M", SpotifyImport.parse("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc123")!!.url)
        assertEquals("album", SpotifyImport.parse("https://open.spotify.com/intl-de/album/4m2880jivSbbyEGAKfITCa")!!.type)
        assertEquals("playlist", SpotifyImport.parse("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M")!!.type)
        assertNull(SpotifyImport.parse("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"))
    }

    @Test fun spotifyEmbedPage() {
        val html = """<html><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{"data":{"entity":{"name":"Road trip","coverArt":{"sources":[{"url":"https://i.scdn.co/image/x"}]},
            "trackList":[{"uri":"spotify:track:4uLU6hMCjMI75M1A2tKUQC","title":"Never Gonna Give You Up","subtitle":"Rick Astley","duration":213573}]}}}}}}</script></html>"""
        val r = SpotifyImport.parseEmbed(html, SpotifyImport.parse("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M")!!)
        assertEquals("Road trip", r.name)
        assertEquals("spotify", r.tracks.single().source)
        assertEquals(213, r.tracks.single().durationSec)
    }
}
