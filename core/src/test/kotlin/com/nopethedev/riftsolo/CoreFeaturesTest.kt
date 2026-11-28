package com.nopethedev.riftsolo

import com.nopethedev.riftsolo.core.Playlist
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.data.ArtistRef
import com.nopethedev.riftsolo.core.data.Cache
import com.nopethedev.riftsolo.core.data.Library
import com.nopethedev.riftsolo.core.data.LibraryStore
import com.nopethedev.riftsolo.core.engine.LavalinkEngine
import com.nopethedev.riftsolo.core.lyrics.Lyrics
import com.nopethedev.riftsolo.core.lyrics.LyricsService
import com.nopethedev.riftsolo.core.sync.AccountStore
import com.nopethedev.riftsolo.core.sync.LibraryMerge
import com.nopethedev.riftsolo.core.sync.SyncManager
import com.nopethedev.riftsolo.core.taste.ArtistGraph
import com.nopethedev.riftsolo.core.taste.Taste
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files

class CoreFeaturesTest {
    private fun t(id: String, artist: String = "Artist $id", title: String = "Song $id") = Track(id, title, artist, 200, "https://www.youtube.com/watch?v=$id", source = "youtube")

    // ---- sync merge ----

    @Test fun `newer change wins per item, including removals`() {
        val a = Library(liked = listOf(t("1"), t("2")), changes = mapOf("like:1" to 10L, "like:2" to 10L))
        // The other device unliked 2 later, and liked 3.
        val b = Library(liked = listOf(t("1"), t("3")), changes = mapOf("like:1" to 10L, "like:2" to 20L, "like:3" to 20L))
        val m = LibraryMerge.merge(a, b, now = 30)
        assertEquals(listOf("1", "3"), m.liked.map { it.id })
        // ...but if this device liked 2 again after that, it stays.
        val a2 = a.copy(changes = a.changes + ("like:2" to 25L))
        assertEquals(setOf("1", "2", "3"), LibraryMerge.merge(a2, b, now = 30).liked.map { it.id }.toSet())
    }

    @Test fun `playlists merge entry by entry and renames follow the newest`() {
        val a = Library(playlists = listOf(Playlist("p", "Old name", listOf(t("1"), t("2")), createdAt = 5)),
            changes = mapOf("pl:p" to 5L, "pl:p:1" to 5L, "pl:p:2" to 5L))
        val b = Library(playlists = listOf(Playlist("p", "New name", listOf(t("1"), t("4")), createdAt = 5), Playlist("q", "Other", createdAt = 9)),
            changes = mapOf("pl:p" to 9L, "pl:p:1" to 5L, "pl:p:2" to 8L, "pl:p:4" to 8L, "pl:q" to 9L))
        val m = LibraryMerge.merge(a, b, now = 10)
        assertEquals(listOf("p", "q"), m.playlists.map { it.id })
        assertEquals("New name", m.playlists[0].name)
        assertEquals(listOf("1", "4"), m.playlists[0].tracks.map { it.id })
        // Merging again changes nothing.
        assertTrue(LibraryMerge.same(m, LibraryMerge.merge(m, b, now = 10)))
        // A playlist deleted on one device (newer) disappears.
        val deleted = Library(changes = mapOf("pl:q" to 12L))
        assertEquals(listOf("p"), LibraryMerge.merge(m, deleted, now = 13).playlists.map { it.id })
    }

    @Test fun `old items without change times are kept from both sides`() {
        val m = LibraryMerge.merge(Library(liked = listOf(t("a"))), Library(liked = listOf(t("b")), plays = 7), now = 1)
        assertEquals(listOf("a", "b"), m.liked.map { it.id })
        assertEquals(7, m.plays)
    }

    // ---- taste: follows and exclusions ----

    @Test fun `followed artists count and excluded songs are not learned or recommended`() {
        val lib = Library(liked = listOf(t("1", "Daft Punk"), t("2", "Justice")), followed = listOf(ArtistRef("Kavinsky")), excluded = listOf(t("2", "Justice")))
        val p = Taste.build(lib, emptySet())
        assertTrue((p.artists["kavinsky"] ?: 0.0) >= Taste.W_FOLLOW)
        assertNull(p.artists["justice"])
        val ranked = Taste.rank(Taste.fromList(listOf(t("2", "Justice"), t("9", "Kavinsky"))), p, 5)
        assertEquals(listOf("9"), ranked.map { it.id })
    }

    // ---- lyrics ----

    @Test fun `parses LRC, LavaLyrics and java-lyrics-plugin answers`() {
        val lrc = LyricsService.parseLrc("[ar: x]\n[00:01.50]Hello\n[00:03.00][00:10.00]Again\n[01:02.123]Bye")
        assertEquals(listOf(1500L, 3000L, 10_000L, 62_123L), lrc.map { it.timeMs })
        assertEquals("Again", lrc[2].text)
        val lava = LyricsService.parseLavalink("""{"sourceName":"youtube","provider":"YouTube Music","text":null,"lines":[{"timestamp":0,"duration":900,"line":"One","plugin":{}},{"timestamp":1200,"line":"Two"}]}""")
        assertTrue(lava.synced)
        assertEquals("YouTube Music via Lavalink", lava.source)
        assertEquals(1, lava.lineAt(1300))
        assertEquals(-1, Lyrics(listOf(Lyrics.Line("x", 500))).lineAt(100))
        val duncte = LyricsService.parseLavalink("""{"type":"timed","source":"youtube","lines":[{"line":"A","range":{"start":10,"end":20}}]}""")
        assertEquals(10L, duncte.lines.single().timeMs)
        val text = LyricsService.parseLavalink("""{"type":"text","text":"\nline one\nline two\n","source":"genius"}""")
        assertFalse(text.synced)
        assertEquals(listOf("line one", "line two"), text.lines.map { it.text })
    }

    @Test fun `cleans video titles for lyrics lookups`() {
        assertEquals("Never Gonna Give You Up", LyricsService.cleanTitle("Rick Astley - Never Gonna Give You Up (Official Video) [4K Remaster]", "Rick Astley"))
        assertEquals("Get Lucky", LyricsService.cleanTitle("Get Lucky (feat. Pharrell Williams)", "Daft Punk"))
        assertEquals("Blinding Lights", LyricsService.cleanTitle("Blinding Lights ft. Someone", "The Weeknd - Topic"))
        assertEquals("Rick Astley", LyricsService.cleanArtist("Rick Astley - Topic"))
        assertEquals("Daft Punk", LyricsService.cleanArtist("Daft Punk, Pharrell Williams"))
    }

    // ---- fake HTTP for Deezer / LRCLIB ----
    private var server: HttpServer? = null
    private val hits = ArrayList<String>()
    private fun serve(routes: Map<String, String>): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { ex ->
            val path = ex.requestURI.toString()
            synchronized(hits) { hits += path }
            val body = routes.entries.firstOrNull { path.startsWith(it.key) }?.value
            val bytes = (body ?: """{"error":{"message":"no route $path"}}""").toByteArray()
            ex.sendResponseHeaders(if (body == null) 404 else 200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        s.start(); server = s
        return "http://127.0.0.1:${s.address.port}"
    }
    @After fun stop() { server?.stop(0) }

    @Test fun `LRCLIB fallback finds synced lyrics and caches the answer`() = runBlocking {
        val base = serve(mapOf("/api/get" to """{"id":1,"trackName":"Song","plainLyrics":"a\nb","syncedLyrics":"[00:01.00]a\n[00:02.00]b"}"""))
        val cache = Cache(Files.createTempDirectory("c").toFile())
        val svc = LyricsService(LavalinkEngine({ emptyList() }), cache, lrclibBase = base)
        val l = svc.get(t("x", "Rick Astley - Topic", "Song (Official Audio)"))
        assertTrue(l.synced)
        assertEquals("LRCLIB", l.source)
        assertTrue(hits.single().contains("artist_name=Rick+Astley") || hits.single().contains("artist_name=Rick%20Astley"))
        assertTrue(hits.single().contains("track_name=Song"))
        svc.get(t("x", "Rick Astley - Topic", "Song (Official Audio)"))
        assertEquals(1, hits.size)
    }

    @Test fun `fans also like ranks artists shared by several of your artists, with albums`() = runBlocking {
        val base = serve(mapOf(
            "/search/artist?limit=5&q=daft+punk" to """{"data":[{"id":27,"name":"Daft Punk","nb_fan":100}]}""",
            "/search/artist?limit=5&q=justice" to """{"data":[{"id":28,"name":"Justice","nb_fan":50}]}""",
            "/artist/27/related" to """{"data":[{"id":1,"name":"Kavinsky","nb_fan":9},{"id":2,"name":"Air","nb_fan":8},{"id":28,"name":"Justice"}]}""",
            "/artist/28/related" to """{"data":[{"id":3,"name":"SebastiAn"},{"id":1,"name":"Kavinsky"}]}""",
            "/artist/1/albums" to """{"data":[{"id":11,"title":"OutRun","record_type":"album","fans":500,"release_date":"2013-02-22"},{"id":12,"title":"Nightcall","record_type":"single","fans":900}]}""",
            "/artist/2/albums" to """{"data":[{"id":21,"title":"Moon Safari","record_type":"album","fans":700}]}""",
            "/artist/3/albums" to """{"data":[]}""",
            "/album/11" to """{"title":"OutRun","cover_xl":"c","tracks":{"data":[{"id":101,"title":"Nightcall","duration":258,"artist":{"name":"Kavinsky"}}]}}""",
        ))
        val graph = ArtistGraph(null, Cache(Files.createTempDirectory("c").toFile()), base = base)
        val lib = Library(liked = listOf(t("1", "Daft Punk"), t("2", "Daft Punk")), followed = listOf(ArtistRef("Justice")))
        val d = graph.discover(lib, Taste.build(lib, emptySet()))
        assertEquals("Kavinsky", d.artists.first().artist.name)
        assertEquals(setOf("Justice", "Daft Punk"), d.artists.first().because.toSet())
        assertFalse(d.artists.any { it.artist.name == "Justice" || it.artist.name == "Daft Punk" })
        assertEquals("OutRun", d.albums.first().album.title)
        val tracks = graph.albumTracks(d.albums.first().album)
        assertEquals("deezer", tracks.single().source)
        assertEquals("https://www.deezer.com/track/101", tracks.single().url)
        assertTrue(tracks.single().source in LavalinkEngine.MIRRORED_SOURCES)
    }

    // ---- shared cache layer ----

    @Test fun `shared cache is asked on a miss and given what was loaded`() = runBlocking {
        val remote = object : Cache.Remote {
            val store = HashMap<String, Pair<JsonElement, Long>>()
            override suspend fun get(ns: String, key: String) = store["$ns|$key"]
            override suspend fun put(ns: String, key: String, value: JsonElement, expiresAt: Long) { store["$ns|$key"] = value to expiresAt }
        }
        val c1 = Cache(Files.createTempDirectory("a").toFile()).apply { this.remote = remote }
        val s = kotlinx.serialization.serializer<String>()
        assertEquals("loaded", c1.getOrLoad("search", "q", s, Cache.DAY) { "loaded" })
        repeat(50) { if (remote.store.isEmpty()) delay(20) }
        assertEquals(JsonPrimitive("loaded"), remote.store["search|q"]!!.first)
        // Another user's device: nothing local, the shared copy is used instead of loading.
        val c2 = Cache(Files.createTempDirectory("b").toFile()).apply { this.remote = remote }
        assertEquals("loaded", c2.getOrLoad("search", "q", s, Cache.DAY) { error("should not load") })
        // Private namespaces never leave the device.
        c1.put("home", "feed", s, "mine")
        delay(100)
        assertNull(remote.store["home|feed"])
    }

    // ---- against a real rift-server: RIFT_SERVER_URL=http://127.0.0.1:8787 ./gradlew :core:test ----

    @Test fun `two devices sync through rift-server`() = runBlocking {
        val url = System.getenv("RIFT_SERVER_URL")
        assumeTrue("set RIFT_SERVER_URL to run", !url.isNullOrBlank())
        val scope = CoroutineScope(Dispatchers.IO)
        fun device(): Pair<LibraryStore, SyncManager> {
            val dir: File = Files.createTempDirectory("dev").toFile()
            val acc = AccountStore(dir).apply { update { it.copy(serverUrl = url!!) } }
            val lib = LibraryStore(dir, scope)
            return lib to SyncManager(acc, lib)
        }
        val (libA, syncA) = device()
        val (libB, syncB) = device()
        val email = "test${System.nanoTime()}@example.com"
        libA.toggleLike(t("1"))
        val pid = libA.createPlaylist("Road trip")
        libA.addToPlaylist(pid, t("2"))
        syncA.signUp(email, "password123", "Tester")
        assertNotNull(syncA.account.value.token)

        libB.toggleLike(t("3"))
        syncB.signIn(email, "password123")
        assertEquals(setOf("1", "3"), libB.library.value.liked.map { it.id }.toSet())
        assertEquals(listOf("2"), libB.library.value.playlists.single().tracks.map { it.id })

        // B removes a like; A syncs and loses it too, and gets B's like.
        libB.toggleLike(t("1"))
        assertTrue(syncB.sync())
        assertTrue(syncA.sync())
        assertEquals(listOf("3"), libA.library.value.liked.map { it.id })
        syncA.deleteAccount()
        assertFalse(syncB.sync())
    }
}
