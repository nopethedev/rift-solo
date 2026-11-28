package com.nopethedev.riftsolo.core.engine

import com.nopethedev.riftsolo.core.SourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** The external pieces Rift Solo fetches or unpacks on the phone during onboarding (and updates later from Profile). */
enum class ComponentId(val title: String, val description: String) {
    EXTRACTOR("Audio extractor", "Built in (NewPipe Extractor). Turns songs into playable audio on your phone."),
    LAVALINK("Lavalink nodes", "Search through public or your own Lavalink nodes."),
}

sealed interface NodeStatus {
    data object Checking : NodeStatus
    data class Online(val version: String, val latencyMs: Long, val sources: List<String>) : NodeStatus
    data class Offline(val message: String) : NodeStatus
}

sealed interface ComponentState {
    data object Idle : ComponentState
    data object Skipped : ComponentState
    data class Working(val label: String, val progress: Float? = null) : ComponentState
    data class Ready(val detail: String) : ComponentState
    data class Failed(val message: String) : ComponentState
}

class Components(
    private val phone: NewPipeEngine,
    private val lavalink: LavalinkEngine,
) {
    private val _state = MutableStateFlow(ComponentId.entries.associateWith<ComponentId, ComponentState> { ComponentState.Idle })
    val state: StateFlow<Map<ComponentId, ComponentState>> = _state.asStateFlow()
    private val _nodes = MutableStateFlow<Map<String, NodeStatus>>(emptyMap())
    /** Health of each Lavalink node by id, shown next to the node in onboarding and Profile. */
    val nodes: StateFlow<Map<String, NodeStatus>> = _nodes.asStateFlow()

    private fun set(id: ComponentId, s: ComponentState) = _state.update { it + (id to s) }

    /** Everything is built in now; this checks the extractor can reach YouTube, then the nodes. */
    suspend fun installAll(nodes: List<LavalinkNodeConfig>?) {
        testExtractor()
        if (nodes == null) set(ComponentId.LAVALINK, ComponentState.Skipped) else testNodes(nodes)
    }

    /** One small YouTube Music search through the extractor: proves the phone can fetch audio info. */
    suspend fun testExtractor() {
        set(ComponentId.EXTRACTOR, ComponentState.Working("Checking YouTube and SoundCloud…"))
        val t0 = System.currentTimeMillis()
        try {
            phone.searchProvider(SourceProvider.YOUTUBE, "never gonna give you up", 1)
            set(ComponentId.EXTRACTOR, ComponentState.Ready("Ready · ${System.currentTimeMillis() - t0} ms"))
        } catch (e: Exception) {
            set(ComponentId.EXTRACTOR, ComponentState.Failed(NewPipeEngine.wrap(e).message ?: "Couldn't reach YouTube"))
        }
    }

    /** Checks every node at once. Some public nodes being down is fine as long as one answers. */
    suspend fun testNodes(nodes: List<LavalinkNodeConfig>, force: Boolean = false) = coroutineScope {
        if (nodes.isEmpty()) { set(ComponentId.LAVALINK, ComponentState.Failed("No node is enabled")); return@coroutineScope }
        set(ComponentId.LAVALINK, ComponentState.Working("Checking ${nodes.size} node${if (nodes.size > 1) "s" else ""}…"))
        _nodes.update { m -> m + nodes.associate { it.id to NodeStatus.Checking } }
        nodes.map { n ->
            async {
                val st = runCatching { lavalink.info(n, force) }.fold(
                    { NodeStatus.Online(it.version, it.latencyMs, it.sourceManagers) },
                    { NodeStatus.Offline(it.message?.take(100) ?: "Unreachable") },
                )
                _nodes.update { it + (n.id to st) }
                st
            }
        }.awaitAll().let { results ->
            val up = results.filterIsInstance<NodeStatus.Online>()
            set(ComponentId.LAVALINK, if (up.isEmpty()) ComponentState.Failed("None of the ${nodes.size} nodes answered right now. Try again, or add another node.")
                else ComponentState.Ready("${up.size} of ${nodes.size} online · Lavalink ${up.first().version}"))
        }
    }
}
