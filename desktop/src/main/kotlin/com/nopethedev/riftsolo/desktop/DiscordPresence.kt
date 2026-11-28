package com.nopethedev.riftsolo.desktop

import com.nopethedev.riftsolo.core.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.util.UUID

/**
 * Discord Rich Presence over Discord's local IPC socket (what the official SDK does), with no library: "Listening to
 * Rift Solo" with the song, artist, cover, a progress bar and a button to the song, like Spotify's.
 *
 * Needs a Discord application id (create an app named "Rift Solo" at discord.com/developers/applications; its name is
 * what Discord shows). Set it at build time with -PdiscordAppId=..., or RIFT_DISCORD_APP_ID, or in Settings.
 */
class DiscordPresence(private val scope: CoroutineScope, private val appId: () -> String) {
    /** One frame: little-endian opcode and length, then UTF-8 JSON. */
    interface Pipe : Closeable { fun write(b: ByteArray); fun readFully(b: ByteArray) }

    private class UnixPipe(path: String) : Pipe {
        private val ch = SocketChannel.open(StandardProtocolFamily.UNIX).apply { connect(UnixDomainSocketAddress.of(path)) }
        override fun write(b: ByteArray) { val buf = ByteBuffer.wrap(b); while (buf.hasRemaining()) ch.write(buf) }
        override fun readFully(b: ByteArray) { val buf = ByteBuffer.wrap(b); while (buf.hasRemaining()) if (ch.read(buf) < 0) throw java.io.EOFException() }
        override fun close() = ch.close()
    }

    private class WindowsPipe(path: String) : Pipe {
        private val f = RandomAccessFile(path, "rw")
        override fun write(b: ByteArray) = f.write(b)
        override fun readFully(b: ByteArray) = f.readFully(b)
        override fun close() = f.close()
    }

    var connector: () -> Pipe? = ::connectDefault
    private var pipe: Pipe? = null
    private var connectedId: String? = null
    @Volatile var status: String = "Not connected"; private set
    private var pending: Job? = null
    private var lastSent = 0L

    /** Shows [track] (or clears the presence when null / paused). Coalesced: Discord allows 5 updates per 20 s. */
    fun show(track: Track?, playing: Boolean, positionMs: Long, durationMs: Long) {
        pending?.cancel()
        pending = scope.launch(Dispatchers.IO) {
            val wait = lastSent + 4_000 - System.currentTimeMillis()
            delay(maxOf(300L, wait))
            lastSent = System.currentTimeMillis()
            runCatching { send(activity(track?.takeIf { playing }, positionMs, durationMs)) }.onFailure { close(); status = "Discord isn't running" }
        }
    }

    fun clear() = show(null, false, 0, 0)

    private fun send(activity: JsonObject?) {
        val id = appId().trim()
        if (id.isEmpty()) { status = "Add a Discord application id"; return }
        val p = ensure(id) ?: run { status = "Discord isn't running"; return }
        frame(p, 1, buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            putJsonObject("args") { put("pid", ProcessHandle.current().pid()); put("activity", activity ?: kotlinx.serialization.json.JsonNull) }
            put("nonce", UUID.randomUUID().toString())
        }.toString())
        read(p)
        status = if (activity == null) "Connected" else "Showing what you play"
    }

    private fun ensure(id: String): Pipe? {
        if (pipe != null && connectedId == id) return pipe
        close()
        val p = connector() ?: return null
        frame(p, 0, buildJsonObject { put("v", 1); put("client_id", id) }.toString())
        val (op, body) = read(p)
        if (op != 1 || !body.contains("READY")) { p.close(); throw IllegalStateException("Discord refused: $body") }
        pipe = p; connectedId = id
        return p
    }

    fun close() { runCatching { pipe?.close() }; pipe = null; connectedId = null }

    companion object {
        fun frame(p: Pipe, op: Int, json: String) {
            val body = json.toByteArray(Charsets.UTF_8)
            val head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(body.size).array()
            p.write(head + body)
        }

        fun read(p: Pipe): Pair<Int, String> {
            val head = ByteArray(8); p.readFully(head)
            val bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
            val op = bb.int; val len = bb.int
            require(len in 0..1_000_000) { "Bad frame" }
            val body = ByteArray(len); p.readFully(body)
            return op to String(body, Charsets.UTF_8)
        }

        fun unixPipe(path: String): Pipe = UnixPipe(path)

        /** "Listening to" activity for [t]; null clears it. */
        fun activity(t: Track?, positionMs: Long, durationMs: Long, now: Long = System.currentTimeMillis()): JsonObject? {
            if (t == null) return null
            return buildJsonObject {
                put("type", 2) // LISTENING
                put("status_display_type", 2) // member list: "Listening to <song>" instead of the app name
                put("details", t.title.take(128).padEnd(2))
                put("state", ("by " + t.artist.replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "")).take(128))
                if (durationMs > 0) putJsonObject("timestamps") { put("start", now - positionMs); put("end", now - positionMs + durationMs) }
                putJsonObject("assets") {
                    t.artworkUrl?.takeIf { it.startsWith("https://") && it.length <= 256 }?.let { put("large_image", it) }
                    put("large_text", t.title.take(128).padEnd(2))
                }
                if (t.url.startsWith("https://") && t.url.length <= 512) put("buttons", buildJsonArray { add(buildJsonObject { put("label", "Open song"); put("url", t.url) }) })
            }
        }

        private fun connectDefault(): Pipe? {
            val win = System.getProperty("os.name").lowercase().contains("win")
            for (i in 0..9) {
                if (win) { runCatching { return WindowsPipe("\\\\.\\pipe\\discord-ipc-$i") }; continue }
                for (dir in unixDirs()) {
                    val f = File(dir, "discord-ipc-$i")
                    if (f.exists()) runCatching { return UnixPipe(f.path) }
                }
            }
            return null
        }

        private fun unixDirs(): List<String> {
            val base = listOfNotNull(System.getenv("XDG_RUNTIME_DIR"), System.getenv("TMPDIR"), System.getenv("TMP"), System.getenv("TEMP"), "/tmp").distinct()
            // Flatpak and Snap Discord put the socket in a sub-folder.
            return base.flatMap { listOf(it, "$it/app/com.discordapp.Discord", "$it/snap.discord", "$it/.flatpak/dev.vencord.Vesktop/xdg-run") }
        }
    }
}
