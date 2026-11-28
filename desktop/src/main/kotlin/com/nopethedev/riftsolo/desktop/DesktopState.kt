package com.nopethedev.riftsolo.desktop

import com.nopethedev.riftsolo.core.GENRES
import com.nopethedev.riftsolo.core.Playlist
import com.nopethedev.riftsolo.core.SourceProvider
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.data.ArtistRef
import com.nopethedev.riftsolo.core.data.Cache
import com.nopethedev.riftsolo.core.data.LibraryStore
import com.nopethedev.riftsolo.core.data.Settings
import com.nopethedev.riftsolo.core.engine.EngineHub
import com.nopethedev.riftsolo.core.engine.LavalinkEngine
import com.nopethedev.riftsolo.core.engine.NewPipeEngine
import com.nopethedev.riftsolo.core.lyrics.Lyrics
import com.nopethedev.riftsolo.core.lyrics.LyricsService
import com.nopethedev.riftsolo.core.sync.AccountStore
import com.nopethedev.riftsolo.core.sync.RiftServer
import com.nopethedev.riftsolo.core.sync.ServerCache
import com.nopethedev.riftsolo.core.sync.SyncManager
import com.nopethedev.riftsolo.core.taste.Album
import com.nopethedev.riftsolo.core.taste.Artist
import com.nopethedev.riftsolo.core.taste.ArtistGraph
import com.nopethedev.riftsolo.core.taste.Discovery
import com.nopethedev.riftsolo.core.taste.Recommender
import com.nopethedev.riftsolo.core.taste.Taste
import com.nopethedev.riftsolo.core.taste.TasteProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

@Serializable
data class HomeRow(val id: String, val title: String, val subtitle: String = "", val tracks: List<Track> = emptyList(), val loading: Boolean = false, val error: String? = null)

data class SearchState(val query: String = "", val loading: Boolean = false, val results: List<Track> = emptyList(), val error: String? = null)
data class ArtistPage(val artist: Artist, val loading: Boolean = true, val top: List<Track> = emptyList(), val albums: List<Album> = emptyList(), val related: List<Artist> = emptyList())
data class AlbumPage(val album: Album, val loading: Boolean = true, val tracks: List<Track> = emptyList(), val error: String? = null)
data class LyricsState(val open: Boolean = false, val loading: Boolean = false, val lyrics: Lyrics? = null)
data class AuthState(val open: Boolean = false, val signUp: Boolean = true, val busy: Boolean = false, val error: String? = null)

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Liked : Screen
    data object Recent : Screen
    data object Settings : Screen
    data class PlaylistS(val id: String) : Screen
    data class ArtistS(val name: String) : Screen
    data class AlbumS(val album: Album) : Screen
}

/** Everything the desktop app runs on (the phone's RiftSoloApp + AppViewModel in one). */
class DesktopState(dataDir: File = AppDirs.data) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    val settingsStore = DesktopSettingsStore(dataDir)
    val settings = settingsStore.settings
    val library = LibraryStore(dataDir, scope)
    val accounts = AccountStore(dataDir)
    val server = RiftServer({ accounts.account.value.serverUrl })
    val cache = Cache(dataDir).also { c -> c.remote = ServerCache(server, accounts); scope.launch(Dispatchers.IO) { c.prune() } }
    val phone = NewPipeEngine({ settings.value.providers.mapNotNull(SourceProvider::of) })
    val lavalink = LavalinkEngine({ settings.value.enabledNodes }, cache)
    val engines = EngineHub({ settings.value.engineConfig }, phone, lavalink, cache)
    val recommender = Recommender(engines)
    val artists = ArtistGraph(engines, cache)
    val lyricsService = LyricsService(lavalink, cache)
    val sync = SyncManager(accounts, library, server, device = "Rift Solo desktop (${System.getProperty("os.name")})")
    val player = DesktopPlayer(engines, library, scope, initialVolume = settingsStore.volume)
    val discord = DiscordPresence(scope) { System.getenv("RIFT_DISCORD_APP_ID") ?: BuildInfo.discordAppId }

    private val _taste = MutableStateFlow(TasteProfile())
    val taste = _taste.asStateFlow()
    val screen = MutableStateFlow<Screen>(Screen.Home)
    private val history = ArrayList<Screen>()
    val home = MutableStateFlow<List<HomeRow>>(emptyList())
    val discovery = MutableStateFlow(Discovery())
    val search = MutableStateFlow(SearchState())
    val artistPages = MutableStateFlow<Map<String, ArtistPage>>(emptyMap())
    val albumPages = MutableStateFlow<Map<Long, AlbumPage>>(emptyMap())
    val lyrics = MutableStateFlow(LyricsState())
    val auth = MutableStateFlow(AuthState())
    val consent = MutableStateFlow(false)
    private var pendingPlay: Pair<List<Track>, Int>? = null

    init {
        player.autoplay = { last, queue -> recommender.next(last, queue, taste()) }
        scope.launch {
            combine(library.library, settings) { l, s -> l to s.genres }.collect { (l, g) -> _taste.value = withContext(Dispatchers.Default) { Taste.build(l, g) } }
        }
        // Sync when the app opens, then shortly after the library changes.
        scope.launch { sync.sync() }
        @OptIn(FlowPreview::class)
        scope.launch { library.library.drop(1).debounce(20_000).collect { if (accounts.account.value.signedIn) sync.sync() } }
        // Discord presence follows the player.
        scope.launch {
            combine(player.state.map { Triple(it.current, it.isPlaying, it.durationMs) }.distinctUntilChanged(), settings.map { it.discordPresence }.distinctUntilChanged()) { a, on -> a to on }
                .collect { (p, on) -> if (on) discord.show(p.first, p.second, player.state.value.positionMs, p.third) else discord.clear() }
        }
        scope.launch { player.state.map { it.current }.distinctUntilChanged().collect { t -> if (lyrics.value.open && t != null) loadLyrics(t) } }
        loadHome()
    }

    suspend fun taste(): TasteProfile = Taste.build(library.library.value, settings.value.genres)

    // ---- navigation ----
    fun go(s: Screen) { if (screen.value != s) { history += screen.value; screen.value = s }; if (history.size > 50) history.removeAt(0) }
    fun back() { history.removeLastOrNull()?.let { screen.value = it } }
    val canGoBack get() = history.isNotEmpty()

    // ---- playback ----
    fun play(queue: List<Track>, index: Int) {
        if (settings.value.allowAllSongs) player.play(queue, index) else { pendingPlay = queue to index; consent.value = true }
    }
    fun answerConsent(allow: Boolean) {
        consent.value = false
        val p = pendingPlay; pendingPlay = null
        if (allow) { settingsStore.update { it.copy(allowAllSongs = true) }; p?.let { player.play(it.first, it.second) } }
    }
    fun playNext(t: Track) = if (settings.value.allowAllSongs) player.playNext(t) else play(listOf(t), 0)
    fun addToQueue(t: Track) = if (settings.value.allowAllSongs) player.addToQueue(t) else play(listOf(t), 0)
    fun startRadio(t: Track) = scope.launch {
        val more = runCatching { recommender.fromSeeds(listOf(t), _taste.value, 25, emptyList()) }.getOrDefault(emptyList())
        play(listOf(t) + more, 0)
    }
    fun setVolume(v: Int) { player.setVolume(v); settingsStore.setVolume(v) }

    // ---- library ----
    fun like(t: Track) = library.toggleLike(t)
    fun follow(name: String, a: Artist? = null) = library.toggleFollow(ArtistRef(cleanArtist(name), a?.pictureUrl, a?.deezerId))
    fun exclude(t: Track) = library.toggleExcluded(t)
    fun createPlaylist(name: String, with: Track? = null): String { val id = library.createPlaylist(name); with?.let { library.addToPlaylist(id, it) }; return id }

    fun importSpotify(link: String, done: (String?) -> Unit) = scope.launch {
        try {
            val r = engines.importSpotify(link)
            val existing = library.library.value.playlists.firstOrNull { it.importedFrom == r.link }
            val id = existing?.id ?: java.util.UUID.randomUUID().toString()
            if (existing != null) library.deletePlaylist(existing.id)
            library.addPlaylist(Playlist(id, r.name, r.tracks.distinctBy { it.id }, existing?.createdAt ?: System.currentTimeMillis(), r.artworkUrl, r.link))
            done(null); go(Screen.PlaylistS(id))
        } catch (e: CancellationException) { throw e } catch (e: Exception) { done(e.message ?: "Import failed") }
    }

    // ---- search: 1 s after typing stops, cached queries at once ----
    private var searchJob: Job? = null
    fun setQuery(q: String) {
        searchJob?.cancel()
        search.update { it.copy(query = q, loading = q.isNotBlank(), error = null, results = if (q.isBlank()) emptyList() else it.results) }
        if (q.isBlank()) return
        searchJob = scope.launch {
            engines.cachedSearch(q)?.let { r -> search.update { it.copy(results = r, loading = false) }; return@launch }
            delay(1000)
            runSearch(q)
        }
    }
    fun submitSearch() { val q = search.value.query; if (q.isBlank()) return; searchJob?.cancel(); searchJob = scope.launch { runSearch(q) } }
    private suspend fun runSearch(q: String) {
        try { val r = engines.search(q, 25); if (search.value.query == q) search.update { it.copy(results = r, loading = false) } }
        catch (e: CancellationException) { throw e } catch (e: Exception) { if (search.value.query == q) search.update { it.copy(loading = false, error = e.message ?: "Search failed", results = emptyList()) } }
    }

    // ---- home ----
    private var homeJob: Job? = null
    private var salt = (System.currentTimeMillis() / Cache.DAY).toInt()
    fun loadHome(refresh: Boolean = false) {
        homeJob?.cancel()
        homeJob = scope.launch {
            launch {
                val cachedD = cache.get("home", "discovery", Discovery.serializer(), 12 * Cache.HOUR)
                if (cachedD != null) discovery.value = cachedD.value
                if (cachedD == null || !cachedD.fresh || refresh) runCatching { artists.discover(library.library.value, taste()) }.getOrNull()?.takeIf { it.artists.isNotEmpty() }?.let {
                    discovery.value = it; cache.put("home", "discovery", Discovery.serializer(), it)
                }
            }
            val rowsS = ListSerializer(HomeRow.serializer())
            val cached = cache.get("home", "rows", rowsS, 6 * Cache.HOUR)
            if (cached != null) { home.value = cached.value; if (cached.fresh && !refresh) return@launch }
            if (refresh) salt++
            val s = settings.value
            val profile = taste()
            val name = s.name.ifBlank { "you" }
            val rows = ArrayList<HomeRow>()
            if (profile.seeds.isNotEmpty()) {
                rows += HomeRow("foryou", "Made for $name", "Picked from what you play", loading = true)
                profile.seeds.getOrNull(salt % minOf(5, profile.seeds.size))?.let { rows += HomeRow("like:${it.id}", "More like ${it.title}", it.artist, loading = true) }
            }
            val genres = profile.topGenres.map { it.first }.filter { g -> GENRES.any { it.id == g } }.ifEmpty { s.genres.toList() }.ifEmpty { GENRES.take(3).map { it.id } }
            genres.take(4).forEach { g -> GENRES.first { it.id == g }.let { rows += HomeRow(it.id, "${it.label} mix", "Made for $name", loading = true) } }
            val old = home.value.associateBy { it.id }
            home.value = rows.map { r -> old[r.id]?.takeIf { it.tracks.isNotEmpty() }?.let { r.copy(tracks = it.tracks) } ?: r }
            rows.forEachIndexed { i, row ->
                if (i > 0 && row.id !in old) delay(1200)
                val r = try {
                    Result.success(when {
                        row.id == "foryou" -> recommender.forYou(profile, 16, salt)
                        row.id.startsWith("like:") -> recommender.fromSeeds(listOfNotNull(profile.seeds.firstOrNull { "like:${it.id}" == row.id }), profile, 16, emptyList())
                        else -> recommender.rerank(engines.search(GENRES.first { it.id == row.id }.query, 25), profile, 14)
                    })
                } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
                home.update { l -> l.map { if (it.id == row.id) it.copy(tracks = r.getOrNull()?.ifEmpty { null } ?: it.tracks, loading = false, error = r.exceptionOrNull()?.message) else it } }
            }
            home.value.filter { it.tracks.isNotEmpty() }.takeIf { it.isNotEmpty() }?.let { cache.put("home", "rows", rowsS, it.map { r -> r.copy(loading = false, error = null) }) }
        }
    }

    // ---- recommendations at the end of a playlist ----
    val recs = MutableStateFlow<Map<String, List<Track>>>(emptyMap())
    fun loadRecs(key: String, tracks: List<Track>) = scope.launch {
        runCatching { recommender.forCollection(tracks, _taste.value, 12) }.getOrNull()?.let { r -> recs.update { it + (key to r) } }
    }

    // ---- artists / albums ----
    fun openArtist(name: String, known: Artist? = null) {
        val clean = cleanArtist(name)
        val key = Taste.artistKey(clean)
        go(Screen.ArtistS(clean))
        if (artistPages.value[key]?.loading == false) return
        artistPages.update { it + (key to ArtistPage(known ?: Artist(clean))) }
        scope.launch {
            val a = known?.takeIf { it.deezerId != null } ?: runCatching { artists.findArtist(clean) }.getOrNull() ?: known ?: Artist(clean)
            val top = runCatching { artists.topTracks(a) }.getOrDefault(emptyList()).ifEmpty { runCatching { engines.search(a.name, 12) }.getOrDefault(emptyList()) }
            val albums = runCatching { artists.albums(a) }.getOrDefault(emptyList())
            val related = runCatching { artists.related(a) }.getOrDefault(emptyList())
            artistPages.update { it + (key to ArtistPage(a, false, top, albums, related)) }
        }
    }

    fun openAlbum(album: Album) {
        go(Screen.AlbumS(album))
        if (albumPages.value[album.deezerId]?.tracks?.isNotEmpty() == true) return
        albumPages.update { it + (album.deezerId to AlbumPage(album)) }
        scope.launch {
            val r = runCatching { artists.albumTracks(album) }
            albumPages.update { it + (album.deezerId to AlbumPage(album, false, r.getOrDefault(emptyList()), r.exceptionOrNull()?.let { "Couldn't load this album" })) }
        }
    }

    // ---- lyrics ----
    private var lyricsJob: Job? = null
    fun toggleLyrics() {
        val open = !lyrics.value.open
        lyrics.update { it.copy(open = open) }
        if (open) player.state.value.current?.let(::loadLyrics)
    }
    private fun loadLyrics(t: Track) {
        lyricsJob?.cancel()
        lyrics.update { it.copy(loading = true, lyrics = null) }
        lyricsJob = scope.launch {
            val l = runCatching { lyricsService.get(t) }.getOrNull()
            if (player.state.value.current?.id == t.id) lyrics.update { it.copy(loading = false, lyrics = l) }
        }
    }

    // ---- account ----
    fun openAuth(signUp: Boolean) { auth.value = AuthState(open = true, signUp = signUp) }
    fun submitAuth(serverUrl: String, email: String, password: String, name: String) {
        if (auth.value.busy) return
        auth.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                sync.setServer(normalizeServer(serverUrl))
                if (auth.value.signUp) sync.signUp(email, password, name.ifBlank { settings.value.name }) else sync.signIn(email, password)
                auth.value = AuthState()
                loadHome(refresh = true)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { auth.update { it.copy(busy = false, error = e.message ?: "Couldn't reach the server") } }
        }
    }

    fun updateSettings(block: (Settings) -> Settings) = settingsStore.update(block)

    fun shutdown() { discord.clear(); Thread.sleep(400); discord.close(); player.shutdown() }

    companion object {
        fun cleanArtist(a: String) = a.replace(Regex("\\s*-\\s*Topic$", RegexOption.IGNORE_CASE), "").trim()
        fun normalizeServer(s: String): String { val t = s.trim().trimEnd('/'); return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t" }
    }
}

object BuildInfo {
    private val props by lazy { java.util.Properties().apply { BuildInfo::class.java.getResourceAsStream("/rift-solo.properties")?.use { load(it) } } }
    val version: String get() = props.getProperty("version", "dev")
    val discordAppId: String get() = props.getProperty("discordAppId", "").takeUnless { it.startsWith("\${") }.orEmpty()
}
