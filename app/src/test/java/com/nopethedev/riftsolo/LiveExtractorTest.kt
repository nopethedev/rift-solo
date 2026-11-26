package com.nopethedev.riftsolo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nopethedev.riftsolo.core.SourceProvider
import com.nopethedev.riftsolo.core.engine.EngineConfig
import com.nopethedev.riftsolo.core.engine.EngineHub
import com.nopethedev.riftsolo.core.engine.LavalinkEngine
import com.nopethedev.riftsolo.core.engine.LavalinkPresets
import com.nopethedev.riftsolo.core.engine.NewPipeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The real path the app takes: search on a public Lavalink node, then extract that track's audio on the "phone".
 * Network test, opt-in: ./gradlew :app:testDebugUnitTest -PliveNodes --tests '*LiveExtractorTest*'
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LiveExtractorTest {
    private val phone = NewPipeEngine({ listOf(SourceProvider.YOUTUBE, SourceProvider.SOUNDCLOUD) })
    private val nodes = LavalinkPresets.nodes.map { it.config }
    private val hub = EngineHub({ EngineConfig(lavalink = nodes) }, phone, LavalinkEngine({ nodes }))

    @Before fun live() = assumeTrue(System.getProperty("liveNodes") == "true")

    @Test fun lavalinkSearchThenExtractOnPhone() = runBlocking {
        // Label-owned music videos are often refused for data-centre IPs (CI), so the check uses a song that isn't.
        val found = hub.search("rick astley never gonna give you up", 5)
        println("lavalink found ${found.size}: ${found.first().title} | ${found.first().url}")
        val t0 = System.currentTimeMillis()
        val s = hub.open(found.first())
        println("extracted in ${System.currentTimeMillis() - t0} ms: host=${java.net.URI(s.url).host} mime=${s.mimeType} dur=${s.durationSec}s headers=${s.headers.keys}")
        assertTrue(s.url.startsWith("https://") && s.durationSec > 0)
    }

    /** Similar songs (YouTube mix / related) and a Spotify playlist through the node's LavaSrc, both cached. */
    @Test fun similarAndSpotifyImport() = runBlocking {
        val cache = com.nopethedev.riftsolo.core.data.Cache(java.nio.file.Files.createTempDirectory("rs").toFile())
        val h = EngineHub({ EngineConfig(lavalink = nodes) }, phone, LavalinkEngine({ nodes }, cache), cache)
        val seed = h.search("rick astley never gonna give you up", 3).first()
        val t0 = System.currentTimeMillis()
        val sim = h.similar(seed)
        println("similar to ${seed.title}: ${sim.size} in ${System.currentTimeMillis() - t0} ms: " + sim.take(6).joinToString(" | ") { "${it.artist} - ${it.title}" })
        val t1 = System.currentTimeMillis(); h.similar(seed); println("cached similar: ${System.currentTimeMillis() - t1} ms")
        val profile = com.nopethedev.riftsolo.core.taste.Taste.build(com.nopethedev.riftsolo.core.data.Library(liked = listOf(seed)), setOf("pop"))
        val recs = com.nopethedev.riftsolo.core.taste.Recommender(h).forCollection(listOf(seed), profile, 8)
        println("recommended: " + recs.joinToString(" | ") { "${it.artist} - ${it.title}" })
        val imp = h.importSpotify("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=x")
        println("spotify import: ${imp.name} · ${imp.tracks.size} songs · first: ${imp.tracks.first().artist} - ${imp.tracks.first().title}")
        val t2 = System.currentTimeMillis(); h.search("rick astley never gonna give you up", 3); println("cached search: ${System.currentTimeMillis() - t2} ms")
        val s = h.open(imp.tracks.first())
        println("mirrored first spotify song -> ${s.mimeType} ${s.durationSec}s")
        assertTrue(sim.isNotEmpty() && imp.tracks.size > 10)
    }

    @Test fun phoneSearchAndSoundCloud() = runBlocking {
        val yt = phone.searchProvider(SourceProvider.YOUTUBE, "daft punk get lucky", 3)
        yt.forEach { println("ytm | ${it.title} | ${it.artist} | ${it.durationSec}s | ${it.url}") }
        val sc = phone.searchProvider(SourceProvider.SOUNDCLOUD, "lofi hip hop", 3)
        sc.forEach { println("sc  | ${it.title} | ${it.artist} | ${it.url}") }
        assertTrue(yt.isNotEmpty() && sc.isNotEmpty())
        // SoundCloud's stream hosts may be firewalled where tests run; print instead of failing.
        println("soundcloud open: " + runCatching { phone.open(sc.first()) }.fold({ "OK ${it.mimeType}" }, { "FAIL ${it.message}" }))
    }
}
