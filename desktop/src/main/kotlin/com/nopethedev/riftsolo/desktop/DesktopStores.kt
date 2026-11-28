package com.nopethedev.riftsolo.desktop

import com.nopethedev.riftsolo.core.EngineChoice
import com.nopethedev.riftsolo.core.SourceProvider
import com.nopethedev.riftsolo.core.data.Settings
import com.nopethedev.riftsolo.core.engine.LavalinkNode
import com.nopethedev.riftsolo.core.engine.LavalinkPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Where Rift Solo keeps its files on each OS. */
object AppDirs {
    val data: File by lazy {
        val home = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        val dir = System.getenv("RIFT_SOLO_HOME")?.let(::File) ?: when {
            os.contains("win") -> File(System.getenv("APPDATA") ?: "$home\\AppData\\Roaming", "RiftSolo")
            os.contains("mac") -> File(home, "Library/Application Support/RiftSolo")
            else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "rift-solo")
        }
        dir.apply { mkdirs() }
    }
}

/** Settings as a JSON file (the phone uses DataStore; same fields). */
class DesktopSettingsStore(dir: File) {
    @Serializable
    private data class Saved(
        val onboarded: Boolean = false,
        val name: String = "",
        val avatar: String = "headphones:violet",
        val genres: Set<String> = emptySet(),
        val engine: String = EngineChoice.LAVALINK.id,
        val providers: Set<String> = setOf(SourceProvider.YOUTUBE.id, SourceProvider.SOUNDCLOUD.id),
        val allowAllSongs: Boolean = false,
        val nodes: List<LavalinkNode> = emptyList(),
        val discordPresence: Boolean = true,
        val volume: Int = 80,
    )

    private val file = File(dir, "settings.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private fun load() = runCatching { json.decodeFromString(Saved.serializer(), file.readText()) }.getOrDefault(Saved())
    private fun Saved.toSettings() = Settings(
        onboarded = onboarded, name = name, avatar = avatar, genres = genres, engine = EngineChoice.of(engine), providers = providers,
        allowAllSongs = allowAllSongs, nodes = LavalinkPresets.merge(nodes), discordPresence = discordPresence,
    )

    private var saved = load()
    private val _settings = MutableStateFlow(saved.toSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    val volume get() = saved.volume

    fun current() = _settings.value

    fun update(block: (Settings) -> Settings) {
        _settings.update(block)
        val s = _settings.value
        saved = saved.copy(onboarded = s.onboarded, name = s.name, avatar = s.avatar, genres = s.genres, engine = s.engine.id, providers = s.providers,
            allowAllSongs = s.allowAllSongs, nodes = s.nodes, discordPresence = s.discordPresence)
        write()
    }

    fun setVolume(v: Int) { saved = saved.copy(volume = v); write() }

    private fun write() = runCatching {
        val tmp = File(file.parentFile, "settings.json.tmp")
        tmp.writeText(json.encodeToString(Saved.serializer(), saved))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}
