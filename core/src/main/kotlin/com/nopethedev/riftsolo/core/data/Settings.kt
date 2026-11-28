package com.nopethedev.riftsolo.core.data

import com.nopethedev.riftsolo.core.EngineChoice
import com.nopethedev.riftsolo.core.SourceProvider
import com.nopethedev.riftsolo.core.engine.EngineConfig
import com.nopethedev.riftsolo.core.engine.LavalinkNode
import com.nopethedev.riftsolo.core.engine.LavalinkNodeConfig
import com.nopethedev.riftsolo.core.engine.LavalinkPresets

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
    /** Android: liquid glass navigation bar (QWEA0/Liquid-Glass-Android). */
    val liquidGlass: Boolean = false,
    /** Desktop: show what's playing on Discord. */
    val discordPresence: Boolean = true,
) {
    val enabledNodes: List<LavalinkNodeConfig> get() = nodes.filter { it.enabled }.map { it.config }
    val engineConfig: EngineConfig get() = EngineConfig(engine, providers.mapNotNull(SourceProvider::of), if (engine == EngineChoice.PHONE) emptyList() else enabledNodes)
}

