package com.nopethedev.riftsolo.core.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nopethedev.riftsolo.core.EngineChoice
import com.nopethedev.riftsolo.core.SourceProvider
import com.nopethedev.riftsolo.core.engine.EngineConfig
import com.nopethedev.riftsolo.core.engine.LavalinkNode
import com.nopethedev.riftsolo.core.engine.LavalinkNodeConfig
import com.nopethedev.riftsolo.core.engine.LavalinkPresets
import com.nopethedev.riftsolo.core.engine.NodeParser
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val onboarded: Boolean = false,
    val name: String = "",
    /** "icon:color", same format as Rift profile avatars. */
    val avatar: String = "headphones:violet",
    val genres: Set<String> = emptySet(),
    val engine: EngineChoice = EngineChoice.LAVALINK,
    val providers: Set<String> = setOf(SourceProvider.YOUTUBE.id, SourceProvider.SOUNDCLOUD.id),
    /** "Allow all songs": the user confirms they may listen to whatever the sources return (Rift's "attested" policy). */
    val allowAllSongs: Boolean = false,
    /** Public presets + custom nodes, in search order. */
    val nodes: List<LavalinkNode> = LavalinkPresets.nodes,
) {
    val enabledNodes: List<LavalinkNodeConfig> get() = nodes.filter { it.enabled }.map { it.config }
    val engineConfig: EngineConfig get() = EngineConfig(engine, providers.mapNotNull(SourceProvider::of), if (engine == EngineChoice.PHONE) emptyList() else enabledNodes)
}

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val name = stringPreferencesKey("name")
        val avatar = stringPreferencesKey("avatar")
        val genres = stringSetPreferencesKey("genres")
        val engine = stringPreferencesKey("engine")
        val providers = stringSetPreferencesKey("providers")
        val nodes = stringPreferencesKey("lavalink_nodes")
        val allowAll = booleanPreferencesKey("allow_all_songs")
        // v0.1.0 had a single node; migrated into the list on first read.
        val llUrl = stringPreferencesKey("lavalink_url")
        val llPass = stringPreferencesKey("lavalink_password")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun update(block: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = block(p.toSettings())
            p[K.onboarded] = s.onboarded
            p[K.name] = s.name
            p[K.avatar] = s.avatar
            p[K.genres] = s.genres
            p[K.engine] = s.engine.id
            p[K.providers] = s.providers
            p[K.allowAll] = s.allowAllSongs
            p[K.nodes] = json.encodeToString(ListSerializer(LavalinkNode.serializer()), s.nodes)
            p.remove(K.llUrl); p.remove(K.llPass)
        }
    }

    private fun Preferences.toSettings() = Settings(
        onboarded = this[K.onboarded] ?: false,
        name = this[K.name] ?: "",
        avatar = this[K.avatar] ?: "headphones:violet",
        genres = this[K.genres] ?: emptySet(),
        engine = EngineChoice.of(this[K.engine]),
        providers = this[K.providers] ?: setOf(SourceProvider.YOUTUBE.id, SourceProvider.SOUNDCLOUD.id),
        allowAllSongs = this[K.allowAll] ?: false,
        nodes = readNodes(),
    )

    private fun Preferences.readNodes(): List<LavalinkNode> {
        val saved = this[K.nodes]?.let { runCatching { json.decodeFromString(ListSerializer(LavalinkNode.serializer()), it) }.getOrNull() }.orEmpty()
        val legacy = this[K.llUrl]?.takeIf { it.isNotBlank() }?.let { url -> NodeParser.parse(url).firstOrNull()?.copy(password = this[K.llPass].orEmpty(), name = "My node") }
        return LavalinkPresets.merge(saved + listOfNotNull(legacy))
    }
}
