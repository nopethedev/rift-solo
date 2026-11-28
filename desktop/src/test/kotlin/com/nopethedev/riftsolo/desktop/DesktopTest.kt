package com.nopethedev.riftsolo.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.taste.Album
import com.nopethedev.riftsolo.core.taste.AlbumPick
import com.nopethedev.riftsolo.core.taste.Artist
import com.nopethedev.riftsolo.core.taste.ArtistPick
import com.nopethedev.riftsolo.core.taste.Discovery
import com.nopethedev.riftsolo.desktop.ui.App
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.file.Files

class DesktopTest {
    private fun track(i: Int) = Track("youtube:$i", listOf("Nightcall", "Midnight City", "Genesis", "Digital Love", "Instant Crush", "Odd Look")[i % 6],
        listOf("Kavinsky", "M83", "Justice", "Daft Punk", "Daft Punk", "Kavinsky")[i % 6], 200 + i, "https://www.youtube.com/watch?v=v$i", source = "youtube")

    @Test fun `discord presence speaks Discord's IPC protocol`() = runBlocking {
        assumeTrue(!System.getProperty("os.name").lowercase().contains("win"))
        val path = Files.createTempDirectory("ipc").resolve("discord-ipc-0")
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply { bind(UnixDomainSocketAddress.of(path)) }
        val got = ArrayList<Pair<Int, String>>()
        val fake = Thread {
            server.accept().use { ch ->
                val pipe = object : DiscordPresence.Pipe {
                    override fun write(b: ByteArray) { ch.write(java.nio.ByteBuffer.wrap(b)) }
                    override fun readFully(b: ByteArray) { val bb = java.nio.ByteBuffer.wrap(b); while (bb.hasRemaining()) if (ch.read(bb) < 0) throw java.io.EOFException() }
                    override fun close() {}
                }
                got += DiscordPresence.read(pipe)
                DiscordPresence.frame(pipe, 1, """{"cmd":"DISPATCH","evt":"READY","data":{"v":1}}""")
                got += DiscordPresence.read(pipe)
                DiscordPresence.frame(pipe, 1, """{"cmd":"SET_ACTIVITY","data":{}}""")
            }
        }.apply { start() }
        val presence = DiscordPresence(CoroutineScope(Dispatchers.IO)) { "123456789012345678" }
        presence.connector = { DiscordPresence.unixPipe(path.toString()) }
        val t = track(0).copy(artworkUrl = "https://i.ytimg.com/vi/x/hq.jpg")
        presence.show(t, playing = true, positionMs = 30_000, durationMs = 200_000)
        withTimeout(10_000) { while (got.size < 2) delay(50) }
        fake.join(5000)
        assertEquals(0, got[0].first)
        assertTrue(got[0].second.contains("\"client_id\":\"123456789012345678\""))
        val act = got[1].second
        assertTrue(act, act.contains("\"cmd\":\"SET_ACTIVITY\"") && act.contains("\"type\":2") && act.contains("\"details\":\"Nightcall\"") && act.contains("\"state\":\"by Kavinsky\""))
        assertTrue(act.contains("\"large_image\":\"https://i.ytimg.com/vi/x/hq.jpg\"") && act.contains("\"end\""))
        withTimeout(5000) { while (presence.status != "Showing what you play") delay(20) }
        server.close()
    }

    @Test fun `plays audio from a URL through Lavaplayer`() = runBlocking {
        val dir = Files.createTempDirectory("aud").toFile()
        // The formats YouTube and SoundCloud serve: AAC in MP4, Opus in WebM, MP3.
        val ok = listOf("tone.mp3", "tone.m4a", "tone.webm").all { name ->
            val extra = when { name.endsWith("m4a") -> listOf("-c:a", "aac", "-movflags", "+faststart"); name.endsWith("webm") -> listOf("-c:a", "libopus"); else -> emptyList() }
            ProcessBuilder(listOf("ffmpeg", "-loglevel", "error", "-f", "lavfi", "-i", "sine=frequency=440:duration=3", "-ac", "2") + extra + File(dir, name).path).inheritIO().start().waitFor() == 0
        }
        assumeTrue("ffmpeg needed", ok)
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/") { ex ->
            val f = File(dir, ex.requestURI.path.trimStart('/'))
            ex.responseHeaders.add("Content-Type", when { f.name.endsWith("mp3") -> "audio/mpeg"; f.name.endsWith("webm") -> "audio/webm"; else -> "audio/mp4" })
            ex.sendResponseHeaders(200, f.length()); ex.responseBody.use { it.write(f.readBytes()) }
        }
        http.start()
        val audio = LocalAudio(output = false)
        var ended: String? = null
        audio.onEnded = { fin, err -> ended = "finished=$fin error=$err" }
        for (name in listOf("tone.mp3", "tone.m4a", "tone.webm")) {
            val pcm = audio.pcm()
            audio.play("http://127.0.0.1:${http.address.port}/$name")
            val buf = ByteArray(48_000 * 4) // one second of 16-bit stereo
            var read = 0
            val reader = Thread { try { while (read < buf.size) { val n = pcm.read(buf, read, buf.size - read); if (n < 0) break; read += n } } catch (_: Exception) {} }
            reader.start(); reader.join(15_000)
            if (reader.isAlive) reader.interrupt()
            assertEquals("$name ($ended)", buf.size, read)
            assertTrue("$name decoded to silence", buf.any { it.toInt() != 0 })
            assertTrue(name, audio.durationMs in 2_800..3_300)
            audio.stop()
        }
        audio.shutdown(); http.stop(0)
    }

    @Test fun `settings survive a restart`() {
        val dir = Files.createTempDirectory("set").toFile()
        DesktopSettingsStore(dir).apply { update { it.copy(name = "Sam", discordPresence = false, onboarded = true) }; setVolume(42) }
        val again = DesktopSettingsStore(dir)
        assertEquals("Sam", again.current().name)
        assertEquals(false, again.current().discordPresence)
        assertEquals(42, again.volume)
    }

    /** Renders the desktop UI off-screen (no window needed) to build/desktop-*.png. */
    @Test fun `renders the desktop app`() {
        val dir = Files.createTempDirectory("app").toFile()
        DesktopSettingsStore(dir).update { it.copy(onboarded = true, name = "Sam", allowAllSongs = true, engine = com.nopethedev.riftsolo.core.EngineChoice.PHONE) }
        val state = DesktopState(dir)
        val tracks = (0 until 8).map(::track)
        tracks.forEach { state.library.toggleLike(it); state.library.played(it) }
        listOf("Night drive", "Focus", "Gym").forEach { state.library.createPlaylist(it) }
        state.home.value = listOf(HomeRow("foryou", "Made for Sam", "Picked from what you play", tracks), HomeRow("like:1", "More like Midnight City", "M83", tracks.reversed()), HomeRow("synthwave", "Synthwave mix", "Made for Sam", tracks.shuffled(java.util.Random(3))))
        state.discovery.value = Discovery(
            listOf("The Midnight", "FM-84", "Gunship", "Timecop1983", "Carpenter Brut", "College").map { ArtistPick(Artist(it), listOf("Kavinsky", "M83"), 1.0) },
            listOf(AlbumPick(Album("Endless Summer", "The Midnight", 1, year = "2016"), "The Midnight"), AlbumPick(Album("Atlas", "FM-84", 2, year = "2016"), "FM-84")),
        )
        val out = File(System.getProperty("user.dir"), "build").apply { mkdirs() }
        fun shot(name: String) {
            val scene = ImageComposeScene(1440, 900, Density(1f)) { App(state) }
            repeat(3) { scene.render(it * 16_000_000L) }
            File(out, "desktop-$name.png").writeBytes(scene.render(64_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
        shot("home")
        state.go(Screen.Liked)
        shot("liked")
        state.go(Screen.Settings)
        shot("settings")
        assertTrue(File(out, "desktop-home.png").length() > 10_000)
    }
}
