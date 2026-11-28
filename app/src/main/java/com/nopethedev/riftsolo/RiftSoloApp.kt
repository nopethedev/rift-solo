package com.nopethedev.riftsolo

import android.app.Application
import com.nopethedev.riftsolo.core.SourceProvider
import com.nopethedev.riftsolo.core.data.LibraryStore
import com.nopethedev.riftsolo.core.data.SettingsStore
import com.nopethedev.riftsolo.core.engine.Components
import com.nopethedev.riftsolo.core.engine.EngineHub
import com.nopethedev.riftsolo.core.engine.LavalinkEngine
import com.nopethedev.riftsolo.core.engine.NewPipeEngine
import com.nopethedev.riftsolo.core.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.nopethedev.riftsolo.core.data.Cache
import com.nopethedev.riftsolo.core.lyrics.LyricsService
import com.nopethedev.riftsolo.core.sync.AccountStore
import com.nopethedev.riftsolo.core.sync.RiftServer
import com.nopethedev.riftsolo.core.sync.ServerCache
import com.nopethedev.riftsolo.core.sync.SyncManager
import com.nopethedev.riftsolo.core.taste.ArtistGraph
import com.nopethedev.riftsolo.core.taste.Recommender
import com.nopethedev.riftsolo.core.taste.Taste
import com.nopethedev.riftsolo.core.taste.TasteProfile

/** Hand-wired dependencies; small enough not to need a DI framework. */
class RiftSoloApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings by lazy { SettingsStore(this) }
    val library by lazy { LibraryStore(filesDir, scope) }
    val phone by lazy { NewPipeEngine({ settings.current().providers.mapNotNull(SourceProvider::of) }) }
    val accounts by lazy { AccountStore(filesDir) }
    val server by lazy { RiftServer({ accounts.account.value.serverUrl }) }
    val cache by lazy { Cache(filesDir).also { c -> c.remote = ServerCache(server, accounts); scope.launch { c.prune() } } }
    val sync by lazy { SyncManager(accounts, library, server, device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()) }
    val lyrics by lazy { LyricsService(lavalink, cache) }
    val artists by lazy { ArtistGraph(engines, cache) }
    val lavalink by lazy { LavalinkEngine({ settings.current().enabledNodes }, cache) }
    val engines by lazy { EngineHub({ settings.current().engineConfig }, phone, lavalink, cache) }
    val components by lazy { Components(phone, lavalink) }
    val recommender by lazy { Recommender(engines) }
    /** Rebuilt from the library on demand: cheap (a few thousand entries at most). */
    suspend fun taste(): TasteProfile = Taste.build(library.library.value, settings.current().genres)
    val player by lazy {
        PlayerController(this, engines, library, scope).apply {
            autoplay = { last, queue -> recommender.next(last, queue, taste()) }
        }
    }
}
