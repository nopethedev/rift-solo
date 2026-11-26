package com.nopethedev.riftsolo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nopethedev.riftsolo.RiftSoloApp
import com.nopethedev.riftsolo.core.EngineChoice
import com.nopethedev.riftsolo.core.GENRES
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.data.Settings
import com.nopethedev.riftsolo.core.engine.ComponentId
import com.nopethedev.riftsolo.core.engine.ComponentState
import com.nopethedev.riftsolo.core.engine.LavalinkNode
import com.nopethedev.riftsolo.ui.home.HomeRow
import com.nopethedev.riftsolo.ui.onboarding.ONBOARDING_STEPS
import com.nopethedev.riftsolo.ui.onboarding.OnboardingState
import com.nopethedev.riftsolo.ui.search.SearchState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.builtins.ListSerializer
import com.nopethedev.riftsolo.core.Playlist
import com.nopethedev.riftsolo.core.data.Cache
import com.nopethedev.riftsolo.core.system.Battery
import com.nopethedev.riftsolo.core.taste.Taste
import com.nopethedev.riftsolo.core.taste.TasteProfile

class AppViewModel(private val app: RiftSoloApp) : ViewModel() {
    val settings: StateFlow<Settings?> = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val library = app.library.library
    val components = app.components.state
    val nodeStatus = app.components.nodes
    val player = app.player.state

    private val _onboarding = MutableStateFlow(OnboardingState())
    val onboarding = _onboarding.asStateFlow()

    private val _home = MutableStateFlow<List<HomeRow>>(emptyList())
    val home = _home.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search = _search.asStateFlow()
    private var homeJob: Job? = null
    private val _taste = MutableStateFlow(TasteProfile())
    val taste = _taste.asStateFlow()

    /** Main search box. Requests go out 1 s after the last keystroke; cached queries show at once. */
    private val mainSearch = DebouncedSearch(viewModelScope, _search, app.engines)
    /** "Add songs" search inside a playlist, same rules. */
    private val _picker = MutableStateFlow(SearchState())
    val picker = _picker.asStateFlow()
    private val pickerSearch = DebouncedSearch(viewModelScope, _picker, app.engines)

    init {
        viewModelScope.launch {
            val s = settings.filterNotNull().first()
            _onboarding.update { it.copy(name = s.name, avatar = s.avatar, genres = s.genres, engine = s.engine, nodes = s.nodes, allowAllSongs = s.allowAllSongs) }
            if (s.onboarded) startMain()
        }
        viewModelScope.launch {
            components.collect { c -> _onboarding.update { it.copy(components = c) } }
        }
        viewModelScope.launch {
            nodeStatus.collect { n -> _onboarding.update { it.copy(nodeStatus = n) } }
        }
        viewModelScope.launch {
            // The taste profile follows the library (likes, plays, skips, playlists) and the picked genres.
            combine(library, settings.filterNotNull()) { l, st -> l to st.genres }.collectLatest { (l, g) ->
                _taste.value = withContext(Dispatchers.Default) { Taste.build(l, g) }
            }
        }
    }

    private fun startMain() {
        app.player.connect()
        viewModelScope.launch {
            // Make sure the runtime is unpacked (fast after the first run) and show versions in Profile.
            if (components.value[ComponentId.EXTRACTOR] == ComponentState.Idle) app.components.testExtractor()
            loadHome()
        }
        viewModelScope.launch { app.settings.current().let { if (it.engine != EngineChoice.PHONE) app.components.testNodes(it.enabledNodes) } }
    }

    // ---------------- onboarding ----------------
    fun onboardingNext() {
        _onboarding.update { it.copy(step = (it.step + 1).coerceAtMost(ONBOARDING_STEPS - 1)) }
        // The last step checks the sources by itself instead of making people press a "Run the check" button.
        if (_onboarding.value.step == ONBOARDING_STEPS - 1 && !_onboarding.value.setupDone) installComponents()
    }
    fun onboardingBack() = _onboarding.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
    fun onboardingEdit(block: (OnboardingState) -> OnboardingState) = _onboarding.update(block)
    fun onboardingEngine(e: EngineChoice) {
        _onboarding.update { it.copy(engine = e) }
        if (e != EngineChoice.PHONE && _onboarding.value.nodeStatus.isEmpty()) checkOnboardingNodes()
    }
    fun checkOnboardingNodes() = viewModelScope.launch { app.components.testNodes(_onboarding.value.nodes.filter { it.enabled }.map { it.config }, force = true) }
    fun onboardingNodes(block: (List<LavalinkNode>) -> List<LavalinkNode>) = _onboarding.update { it.copy(nodes = block(it.nodes)) }
    fun addOnboardingNodes(n: List<LavalinkNode>) { onboardingNodes { it + n }; viewModelScope.launch { app.components.testNodes(n.map { it.config }) } }

    fun toggleGenre(id: String) = _onboarding.update { it.copy(genres = if (id in it.genres) it.genres - id else it.genres + id) }

    fun installComponents() {
        if (_onboarding.value.installing) return
        _onboarding.update { it.copy(installing = true) }
        viewModelScope.launch {
            try {
                val o = _onboarding.value
                saveOnboardingFields(o, onboarded = false)
                app.components.installAll(if (o.usesLavalink) o.nodes.filter { it.enabled }.map { it.config } else null)
            } finally { _onboarding.update { it.copy(installing = false) } }
        }
    }

    fun finishOnboarding() = viewModelScope.launch {
        saveOnboardingFields(_onboarding.value, onboarded = true)
        startMain()
    }

    private suspend fun saveOnboardingFields(o: OnboardingState, onboarded: Boolean) = app.settings.update {
        it.copy(onboarded = onboarded, name = o.name.trim(), avatar = o.avatar, genres = o.genres, engine = o.engine, nodes = o.nodes, allowAllSongs = o.allowAllSongs)
    }

    // ---------------- home ----------------
    /**
     * Home feed: "Made for you" and "More like …" from the taste profile, then genre mixes re-ranked for the user.
     * The last feed is cached, so the app opens on it instantly; rows are only fetched again when older than 6 h, one
     * at a time. Refresh reshuffles from cached songs instead of hitting the node again.
     */
    fun loadHome(refresh: Boolean = false) {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            val cached = app.cache.get("home", "rows", HOME_ROWS, HOME_TTL)
            if (cached != null) {
                _home.value = cached.value
                if (cached.fresh && !refresh) return@launch
            }
            homeSalt = if (refresh) homeSalt + 1 else homeSalt
            val s = app.settings.current()
            val profile = Taste.build(app.library.library.value, s.genres)
            val name = s.name.ifBlank { "you" }
            val rows = ArrayList<HomeRow>()
            if (profile.seeds.isNotEmpty()) {
                rows += HomeRow("foryou", "Made for $name", "Picked from what you play", loading = true)
                profile.seeds.getOrNull(homeSalt % minOf(5, profile.seeds.size))?.let { rows += HomeRow("like:${it.id}", "More like ${it.title}", it.artist, loading = true) }
            }
            val genreOrder = profile.topGenres.map { it.first }.filter { g -> GENRES.any { it.id == g } }.ifEmpty { s.genres.toList() }.ifEmpty { GENRES.take(3).map { it.id } }
            genreOrder.take(4).forEach { g -> GENRES.first { it.id == g }.let { rows += HomeRow(it.id, "${it.label} mix", "Made for $name", loading = true) } }
            GENRES.filterNot { it.id in genreOrder }.shuffled(java.util.Random(homeSalt.toLong())).take(1).forEach { rows += HomeRow(it.id, "Try ${it.label}", "Something different", loading = true) }
            val old = _home.value.associateBy { it.id }
            _home.value = rows.map { r -> old[r.id]?.takeIf { it.tracks.isNotEmpty() }?.let { r.copy(tracks = it.tracks) } ?: r }

            rows.forEachIndexed { i, row ->
                if (i > 0 && row.id !in old) delay(HOME_ROW_GAP_MS)
                val r = try {
                    Result.success(when {
                        row.id == "foryou" -> app.recommender.forYou(profile, 16, homeSalt)
                        row.id.startsWith("like:") -> app.recommender.fromSeeds(listOfNotNull(profile.seeds.firstOrNull { "like:${it.id}" == row.id }), profile, 16, emptyList())
                        else -> app.recommender.rerank(app.engines.search(GENRES.first { it.id == row.id }.query, 25), profile, 12)
                    })
                } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
                _home.update { list -> list.map { if (it.id == row.id) it.copy(tracks = r.getOrNull()?.ifEmpty { null } ?: it.tracks, loading = false, error = r.exceptionOrNull()?.message) else it } }
            }
            _home.value.filter { it.tracks.isNotEmpty() }.takeIf { it.isNotEmpty() }?.let { app.cache.put("home", "rows", HOME_ROWS, it.map { r -> r.copy(loading = false, error = null) }) }
        }
    }
    private var homeSalt = (System.currentTimeMillis() / Cache.DAY).toInt()

    // ---------------- search ----------------
    fun setQuery(q: String) = mainSearch.type(q)
    fun submitSearch() = mainSearch.submit()
    fun setSearchFilter(f: String) = _search.update { it.copy(filter = f) }
    fun browse(genreQuery: String) { mainSearch.type(genreQuery); mainSearch.submit() }
    fun setPickerQuery(q: String) = pickerSearch.type(q)
    fun submitPicker() = pickerSearch.submit()
    fun clearPicker() = pickerSearch.type("")

    // ---------------- playlists: recommendations, editing, import ----------------
    private val _recs = MutableStateFlow<Map<String, RecState>>(emptyMap())
    /** "Recommended" songs at the end of each collection, by collection key ("liked" or a playlist id). */
    val recs = _recs.asStateFlow()
    private val recJobs = HashMap<String, Job>()

    fun loadRecs(key: String, tracks: List<Track>, refresh: Boolean = false) {
        val cur = _recs.value[key]
        if (!refresh && cur != null && (cur.loading || cur.basedOn == tracks.size)) return
        recJobs[key]?.cancel()
        val salt = (cur?.salt ?: 0) + if (refresh) 1 else 0
        _recs.update { it + (key to RecState(loading = true, tracks = cur?.tracks.orEmpty(), salt = salt, basedOn = tracks.size)) }
        recJobs[key] = viewModelScope.launch {
            val r = try { Result.success(app.recommender.forCollection(tracks, _taste.value, 15, salt)) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
            _recs.update { it + (key to RecState(tracks = r.getOrDefault(emptyList()), error = r.exceptionOrNull()?.message, salt = salt, basedOn = tracks.size)) }
        }
    }

    fun removeFromPlaylist(id: String, t: Track) = app.library.removeFromPlaylist(id, t.id)
    fun renamePlaylist(id: String, name: String) = app.library.renamePlaylist(id, name)
    fun deletePlaylist(id: String) = app.library.deletePlaylist(id)

    private val _import = MutableStateFlow<ImportState>(ImportState.Idle)
    val import = _import.asStateFlow()
    fun resetImport() { _import.value = ImportState.Idle }

    /** Spotify link → a new playlist (or the existing one from that link, refreshed). */
    fun importSpotify(link: String) {
        if (_import.value is ImportState.Working) return
        _import.value = ImportState.Working
        viewModelScope.launch {
            try {
                val r = app.engines.importSpotify(link)
                val existing = library.value.playlists.firstOrNull { it.importedFrom == r.link }
                val id = existing?.id ?: java.util.UUID.randomUUID().toString()
                if (existing != null) app.library.deletePlaylist(existing.id)
                app.library.addPlaylist(Playlist(id, r.name, r.tracks.distinctBy { it.id }, existing?.createdAt ?: System.currentTimeMillis(), r.artworkUrl, r.link))
                _import.value = ImportState.Done(id, r.name, r.tracks.size)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _import.value = ImportState.Failed(e.message ?: "Import failed")
            }
        }
    }

    // ---------------- background playback ----------------
    private val _system = MutableStateFlow(SystemState())
    val system = _system.asStateFlow()
    fun refreshSystem() { _system.value = SystemState(Battery.isUnrestricted(app), Battery.notificationsAllowed(app)) }

    // ---------------- cache ----------------
    private val _cacheBytes = MutableStateFlow(-1L)
    val cacheBytes = _cacheBytes.asStateFlow()
    fun refreshCacheSize() = viewModelScope.launch { _cacheBytes.value = app.cache.sizeBytes() }
    fun clearCache() = viewModelScope.launch { app.cache.clear(); _cacheBytes.value = app.cache.sizeBytes() }

    // ---------------- player & library ----------------
    private val _consentPrompt = MutableStateFlow(false)
    /** Shown when something should play but "Allow all songs" is off. */
    val consentPrompt = _consentPrompt.asStateFlow()
    private var pendingPlay: Pair<List<Track>, Int>? = null

    fun play(queue: List<Track>, index: Int) {
        if (settings.value?.allowAllSongs == true) app.player.play(queue, index)
        else { pendingPlay = queue to index; _consentPrompt.value = true }
    }
    fun answerConsent(allow: Boolean) {
        _consentPrompt.value = false
        val p = pendingPlay; pendingPlay = null
        if (allow) viewModelScope.launch { app.settings.update { it.copy(allowAllSongs = true) }; p?.let { app.player.play(it.first, it.second) } }
    }
    fun toggle() = app.player.toggle()
    fun next() = app.player.next()
    fun previous() = app.player.previous()
    fun seek(ms: Long) = app.player.seekTo(ms)
    fun like(t: Track) = app.library.toggleLike(t)
    fun createPlaylist(name: String) = app.library.createPlaylist(name)
    fun addToPlaylist(id: String, t: Track) = app.library.addToPlaylist(id, t)
    fun playlist(id: String) = library.value.playlists.firstOrNull { it.id == id }

    // ---------------- profile ----------------
    fun updateSettings(block: (Settings) -> Settings) = viewModelScope.launch { app.settings.update(block) }
    fun checkNodes() = viewModelScope.launch { app.components.testNodes(app.settings.current().enabledNodes, force = true) }
    fun setNodes(block: (List<LavalinkNode>) -> List<LavalinkNode>) = updateSettings { it.copy(nodes = block(it.nodes)) }
    fun addNodes(n: List<LavalinkNode>) { setNodes { it + n }; viewModelScope.launch { app.components.testNodes(n.map { it.config }) } }
    fun testExtractor() = viewModelScope.launch { app.components.testExtractor() }

    companion object {
        /** Wait for 1 s without typing before searching: free Lavalink nodes rate-limit bursts. */
        const val SEARCH_DELAY_MS = 1000L
        /** Gap between home-row searches for the same reason. */
        const val HOME_ROW_GAP_MS = 1200L
        const val HOME_TTL = 6 * Cache.HOUR
        private val HOME_ROWS = ListSerializer(HomeRow.serializer())

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RiftSoloApp) }
        }
    }

}

data class RecState(val loading: Boolean = false, val tracks: List<Track> = emptyList(), val error: String? = null, val salt: Int = 0, val basedOn: Int = -1)

sealed interface ImportState {
    data object Idle : ImportState
    data object Working : ImportState
    data class Done(val playlistId: String, val name: String, val count: Int) : ImportState
    data class Failed(val message: String) : ImportState
}

data class SystemState(val batteryUnrestricted: Boolean = true, val notificationsAllowed: Boolean = true)

/**
 * A search box that waits until typing stops for [AppViewModel.SEARCH_DELAY_MS] before sending anything. Every
 * keystroke cancels the pending request; a query already in the cache is shown straight away (no request at all).
 */
class DebouncedSearch(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val state: MutableStateFlow<SearchState>,
    private val hub: com.nopethedev.riftsolo.core.engine.EngineHub,
) {
    private var job: Job? = null

    fun type(q: String) {
        job?.cancel()
        state.update { it.copy(query = q, loading = q.isNotBlank(), error = null, results = if (q.isBlank()) emptyList() else it.results) }
        if (q.isBlank()) return
        job = scope.launch {
            hub.cachedSearch(q)?.let { show(q, it); return@launch }
            delay(AppViewModel.SEARCH_DELAY_MS)
            run(q)
        }
    }

    /** Keyboard "search" key or a tap on a genre: no need to wait. */
    fun submit() {
        val q = state.value.query
        if (q.isBlank()) return
        job?.cancel()
        job = scope.launch { run(q) }
    }

    private fun show(q: String, r: List<Track>) { if (state.value.query == q) state.update { it.copy(results = r, loading = false, error = null) } }

    private suspend fun run(q: String) {
        try { show(q, hub.search(q, 25)) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            if (state.value.query == q) state.update { it.copy(loading = false, error = e.message ?: "Search failed", results = emptyList()) }
        }
    }
}
