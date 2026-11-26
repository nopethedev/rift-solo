package com.nopethedev.riftsolo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nopethedev.riftsolo.core.engine.EngineException
import com.nopethedev.riftsolo.core.engine.LavalinkEngine
import com.nopethedev.riftsolo.core.engine.LavalinkNode
import com.nopethedev.riftsolo.core.engine.LavalinkNodeConfig
import com.nopethedev.riftsolo.core.engine.LavalinkPresets
import com.nopethedev.riftsolo.core.engine.NodeParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Robolectric only for a real org.json; no Android UI involved. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LavalinkNodesTest {

    @Test fun parsesTheJsonPublicListsShare() {
        val n = NodeParser.parse("""{ "host": "lavalinkv4.serenetia.com", "port": 443, "password": "https://seretia.link/discord", "secure": true }""").single()
        assertEquals("lavalinkv4.serenetia.com", n.host)
        assertEquals(443, n.port)
        assertEquals("https://seretia.link/discord", n.password)
        assertEquals("https://lavalinkv4.serenetia.com", n.url)
    }

    @Test fun parsesArraysIdentifiersAndUrls() {
        val list = NodeParser.parse("""[{"identifier":"Home","host":"192.168.1.20","port":2333,"password":"youshallnotpass","secure":false},{"host":"node.example.com:8443","password":"x"}]""")
        assertEquals(2, list.size)
        assertEquals("Home", list[0].name)
        assertEquals("http://192.168.1.20:2333", list[0].url)
        assertEquals(8443, list[1].port)
        val u = NodeParser.parse("http://10.0.0.5:2333").single()
        assertEquals("http://10.0.0.5:2333", u.url)
    }

    @Test fun presetsKeepUserChoiceAndCustomNodes() {
        val saved = listOf(LavalinkPresets.nodes.first().copy(enabled = false), LavalinkNode(name = "Mine", host = "a.b", preset = false))
        val merged = LavalinkPresets.merge(saved)
        assertEquals(false, merged.first { it.preset }.enabled)
        assertTrue(merged.any { it.name == "Mine" })
    }

    @Test fun readsARealLoadTracksResponse() {
        val json = javaClass.classLoader!!.getResource("loadtracks-ytmsearch.json")!!.readText()
        val tracks = LavalinkEngine.parseLoadResult(json)
        assertEquals(4, tracks.size)
        assertTrue(tracks.all { it.source == "youtube" && it.url.startsWith("https://www.youtube.com/watch") && it.durationSec > 0 })
        assertTrue(tracks.first().title.contains("Get Lucky"))
    }

    @Test fun searchPrefixRulesMatchRift() {
        assertEquals(listOf("ytmsearch", "ytsearch"), LavalinkEngine.searchPrefixes("ytmsearch, ytsearch"))
        assertEquals(listOf("scsearch"), LavalinkEngine.searchPrefixes(""))
        assertEquals(listOf("ytmsearch", "scsearch"), LavalinkEngine.searchPrefixes("auto", listOf("soundcloud", "bandcamp", "youtube", "spotify")))
        assertEquals(listOf("scsearch"), LavalinkEngine.searchPrefixes("auto", listOf("soundcloud", "http")))
    }

    @Test fun failsOverToTheNextNode() = runBlocking {
        val dead = LavalinkNodeConfig("http://127.0.0.1:9", "x", id = "dead", name = "dead")
        val engine = LavalinkEngine({ listOf(dead) })
        val e = runCatching { engine.search("anything", 5) }.exceptionOrNull()
        assertTrue(e is EngineException && e.code == EngineException.Code.UNAVAILABLE)
    }

    /** Hits the real public node: ./gradlew :app:testDebugUnitTest -PliveNodes --tests '*LavalinkNodesTest*' */
    @Test fun liveSearchThroughPresetNodes() = runBlocking {
        assumeTrue(System.getProperty("liveNodes") == "true")
        val configs = LavalinkPresets.nodes.map { it.config }
        val engine = LavalinkEngine({ configs })
        configs.forEach { println("node ${it.name}: ${engine.info(it)}") }
        val r = engine.search("daft punk get lucky", 10)
        r.groupBy { it.source }.forEach { (k, v) -> println("source $k: ${v.size}") }
        r.take(6).forEach { println("  ${it.source} | ${it.title} | ${it.artist} | ${it.url}") }
        assertTrue(r.isNotEmpty())
    }
}
