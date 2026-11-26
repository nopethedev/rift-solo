package com.nopethedev.riftsolo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import com.nopethedev.riftsolo.ui.components.Spinner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.data.Library
import com.nopethedev.riftsolo.core.player.PlayerState
import com.nopethedev.riftsolo.ui.components.Artwork
import com.nopethedev.riftsolo.ui.components.NodeActions
import com.nopethedev.riftsolo.ui.home.HomeActions
import com.nopethedev.riftsolo.ui.home.HomeScreen
import com.nopethedev.riftsolo.ui.home.HomeState
import com.nopethedev.riftsolo.ui.home.greetingFor
import com.nopethedev.riftsolo.ui.library.Collection
import com.nopethedev.riftsolo.ui.library.CollectionScreen
import com.nopethedev.riftsolo.ui.library.LibraryActions
import com.nopethedev.riftsolo.ui.library.LibraryScreen
import com.nopethedev.riftsolo.ui.library.LibraryState
import com.nopethedev.riftsolo.ui.onboarding.OnboardingActions
import com.nopethedev.riftsolo.ui.onboarding.CONSENT_TEXT
import com.nopethedev.riftsolo.ui.onboarding.OnboardingScreen
import com.nopethedev.riftsolo.ui.onboarding.fieldColors
import com.nopethedev.riftsolo.ui.player.MiniPlayer
import com.nopethedev.riftsolo.ui.player.NowPlayingScreen
import com.nopethedev.riftsolo.ui.player.PlayerActions
import com.nopethedev.riftsolo.ui.profile.ProfileActions
import com.nopethedev.riftsolo.ui.profile.ProfileScreen
import com.nopethedev.riftsolo.ui.profile.ProfileState
import com.nopethedev.riftsolo.ui.search.SearchActions
import com.nopethedev.riftsolo.ui.search.SearchScreen
import com.nopethedev.riftsolo.ui.theme.Rift
import java.util.Calendar
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nopethedev.riftsolo.core.system.Battery
import com.nopethedev.riftsolo.ui.library.AddSongsSheet
import com.nopethedev.riftsolo.ui.library.CollectionExtras
import com.nopethedev.riftsolo.ui.library.ImportDialog

enum class Tab(val label: String, val on: ImageVector, val off: ImageVector) {
    HOME("Home", Icons.Rounded.Home, Icons.Outlined.Home),
    SEARCH("Search", Icons.Rounded.Search, Icons.Outlined.Search),
    LIBRARY("Library", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
    PROFILE("Profile", Icons.Rounded.Person, Icons.Outlined.Person),
}

@Composable
fun RiftSoloRoot(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val system by vm.system.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshSystem() }
    // Battery dialog / notification prompt: re-read the real state when they close.
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.refreshSystem() }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshSystem() }
    val requestBattery = {
        runCatching { batteryLauncher.launch(Battery.requestIntent(context)) }
            .recoverCatching { batteryLauncher.launch(Battery.listIntent()) }
            .onFailure { Battery.open(context, Battery.appDetailsIntent(context)) }
        Unit
    }
    val requestNotifications = {
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else Battery.open(context, Battery.appDetailsIntent(context))
    }
    val s = settings
    if (s == null) {
        Box(Modifier.fillMaxSize().background(Rift.Bg), contentAlignment = Alignment.Center) { Spinner(Modifier.size(40.dp)) }
        return
    }
    if (!s.onboarded) {
        val o by vm.onboarding.collectAsStateWithLifecycle()
        BackHandler(o.step > 0) { vm.onboardingBack() }
        val oem = remember { if (Battery.aggressiveOem) Build.MANUFACTURER.replaceFirstChar(Char::uppercase) else null }
        OnboardingScreen(o.copy(batteryUnrestricted = system.batteryUnrestricted, notificationsAllowed = system.notificationsAllowed, manufacturer = oem), OnboardingActions(
            next = vm::onboardingNext, back = vm::onboardingBack,
            name = { n -> vm.onboardingEdit { it.copy(name = n) } }, avatar = { a -> vm.onboardingEdit { it.copy(avatar = a) } },
            genre = vm::toggleGenre, engine = vm::onboardingEngine,
            nodes = NodeActions(
                toggle = { id, on -> vm.onboardingNodes { l -> l.map { if (it.id == id) it.copy(enabled = on) else it } } },
                delete = { id -> vm.onboardingNodes { l -> l.filterNot { it.id == id } } },
                add = vm::addOnboardingNodes, check = { vm.checkOnboardingNodes() },
            ),
            install = vm::installComponents, finish = { vm.finishOnboarding() },
            allowAllSongs = { a -> vm.onboardingEdit { it.copy(allowAllSongs = a) } },
            requestBattery = requestBattery, requestNotifications = requestNotifications,
            openAppSettings = { Battery.open(context, Battery.appDetailsIntent(context)) },
        ))
        return
    }

    val library by vm.library.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val homeRows by vm.home.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val components by vm.components.collectAsStateWithLifecycle()
    val nodeStatus by vm.nodeStatus.collectAsStateWithLifecycle()
    val consentPrompt by vm.consentPrompt.collectAsStateWithLifecycle()
    val recs by vm.recs.collectAsStateWithLifecycle()
    val picker by vm.picker.collectAsStateWithLifecycle()
    val taste by vm.taste.collectAsStateWithLifecycle()
    val importState by vm.import.collectAsStateWithLifecycle()
    val cacheBytes by vm.cacheBytes.collectAsStateWithLifecycle()
    var addingTo by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }

    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var collection by remember { mutableStateOf<Collection?>(null) }
    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    var newPlaylist by remember { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf<Track?>(null) }
    var editProfile by remember { mutableStateOf(false) }
    val likedIds = library.liked.map { it.id }.toSet()

    BackHandler(collection != null && !nowPlaying) { collection = null }
    BackHandler(tab != Tab.HOME && collection == null && !nowPlaying) { tab = Tab.HOME }

    MainScaffold(
        tab = tab, onTab = { tab = it; collection = null }, player = player, liked = player.current?.id in likedIds,
        playerActions = PlayerActions(toggle = vm::toggle, next = vm::next, previous = vm::previous, seek = vm::seek, like = { player.current?.let(vm::like) }, open = { nowPlaying = true }, close = { nowPlaying = false }),
        nowPlaying = nowPlaying,
    ) { pad ->
        val c = collection
        if (c != null) {
            val playlist = (c as? Collection.Of)?.let { o -> library.playlists.firstOrNull { it.id == o.playlistId } }
            val (title, tracks, brush) = when (c) {
                Collection.Liked -> Triple("Liked Songs", library.liked, Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFFA78BFA))))
                Collection.Recent -> Triple("Recently played", library.recent, Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF14B8A6))))
                is Collection.Of -> Triple(playlist?.name ?: "Playlist", playlist?.tracks.orEmpty(), Brush.linearGradient(listOf(Rift.Accent, Rift.AccentDark)))
            }
            val recKey = when (c) { Collection.Liked -> "liked"; Collection.Recent -> null; is Collection.Of -> c.playlistId }
            val extras = CollectionExtras(
                artworkUrl = playlist?.artworkUrl,
                onAddSongs = playlist?.let { p -> { addingTo = p.id; vm.clearPicker(); vm.loadRecs(p.id, p.tracks) } },
                onRemove = playlist?.let { p -> { t: Track -> vm.removeFromPlaylist(p.id, t) } },
                onRename = playlist?.let { p -> { renaming = p.id } },
                onDelete = playlist?.let { p -> { deleting = p.id } },
                onMore = { addTarget = it },
                recs = recKey?.let { recs[it] ?: RecState(loading = true) },
                onRecPlay = { list, i -> vm.play(list, i) },
                onRecAdd = when { playlist != null -> { t -> vm.addToPlaylist(playlist.id, t) }; c == Collection.Liked -> { t -> vm.like(t) }; else -> null },
                onRecRefresh = { recKey?.let { vm.loadRecs(it, tracks, refresh = true) } },
                onRecsShown = { recKey?.let { vm.loadRecs(it, tracks) } },
            )
            CollectionScreen(title, "${if (playlist?.importedFrom != null) "Imported from Spotify" else s.name.ifBlank { "You" }} · ${tracks.size} songs", tracks, brush, player.current?.id, likedIds,
                onBack = { collection = null }, onPlay = { vm.play(tracks, it) }, onShuffle = { vm.play(tracks.shuffled(), 0) }, onLike = vm::like, contentPadding = pad, extras = extras)
        } else when (tab) {
            Tab.HOME -> HomeScreen(
                HomeState(s.name, s.avatar, greetingFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)), library.liked.size, library.recent, homeRows),
                HomeActions(play = vm::play, openLiked = { collection = Collection.Liked }, openRecent = { collection = Collection.Recent }, openProfile = { tab = Tab.PROFILE }, refresh = { vm.loadHome(refresh = true) }),
                pad,
            )
            Tab.SEARCH -> SearchScreen(
                search.copy(playingId = player.current?.id, likedIds = likedIds),
                SearchActions(submit = vm::submitSearch, query = vm::setQuery, filter = vm::setSearchFilter, play = vm::play, like = vm::like, more = { addTarget = it }, browse = { vm.browse(it.query) }),
                pad,
            )
            Tab.LIBRARY -> LibraryScreen(
                LibraryState(s.avatar, library.liked, library.recent, library.playlists),
                LibraryActions(open = { collection = it }, create = { newPlaylist = true }, openProfile = { tab = Tab.PROFILE }, importSpotify = { vm.resetImport(); importing = true }),
                pad,
            )
            Tab.PROFILE -> ProfileScreen(
                profileState(s, library, components, nodeStatus).copy(taste = taste, batteryUnrestricted = system.batteryUnrestricted, notificationsAllowed = system.notificationsAllowed, cacheBytes = cacheBytes).also { LaunchedEffect(Unit) { vm.refreshCacheSize() } },
                ProfileActions(
                    engine = { e -> vm.updateSettings { it.copy(engine = e) } },
                    provider = { id, on -> vm.updateSettings { it.copy(providers = if (on) it.providers + id else it.providers - id) } },
                    nodes = NodeActions(
                        toggle = { id, on -> vm.setNodes { l -> l.map { if (it.id == id) it.copy(enabled = on) else it } } },
                        delete = { id -> vm.setNodes { l -> l.filterNot { it.id == id } } },
                        add = vm::addNodes, check = { vm.checkNodes() },
                    ),
                    testExtractor = { vm.testExtractor() },
                    allowAllSongs = { a -> vm.updateSettings { it.copy(allowAllSongs = a) } }, editProfile = { editProfile = true },
                    fixBattery = requestBattery, allowNotifications = requestNotifications, clearCache = { vm.clearCache() },
                ),
                pad,
            )
        }
    }

    if (newPlaylist) {
        NameDialog("New playlist", "", "Create", onDismiss = { newPlaylist = false }) { name -> val id = vm.createPlaylist(name); addTarget?.let { vm.addToPlaylist(id, it) }; addTarget = null; newPlaylist = false }
    }
    addTarget?.let { t -> if (!newPlaylist) AddToPlaylistSheet(t, library, onDismiss = { addTarget = null }, onNew = { newPlaylist = true }, onPick = { vm.addToPlaylist(it, t); addTarget = null }) }
    if (consentPrompt) AlertDialog(
        onDismissRequest = { vm.answerConsent(false) }, containerColor = Rift.Surface,
        title = { Text("Allow all songs?") },
        text = { Text(CONSENT_TEXT) },
        confirmButton = { TextButton({ vm.answerConsent(true) }) { Text("Allow and play") } },
        dismissButton = { TextButton({ vm.answerConsent(false) }) { Text("Not now") } },
    )
    addingTo?.let { id ->
        val p = library.playlists.firstOrNull { it.id == id }
        if (p == null) addingTo = null
        else AddSongsSheet(p.name, picker, p.tracks.map { it.id }.toSet(), recs[id]?.tracks.orEmpty(), vm::setPickerQuery, vm::submitPicker, { t -> vm.addToPlaylist(id, t) }, onDismiss = { addingTo = null })
    }
    if (importing) ImportDialog(importState, vm::importSpotify, onOpen = { id -> importing = false; tab = Tab.LIBRARY; collection = Collection.Of(id) }, onDismiss = { importing = false })
    renaming?.let { id -> NameDialog("Rename playlist", library.playlists.firstOrNull { it.id == id }?.name.orEmpty(), "Save", onDismiss = { renaming = null }) { n -> vm.renamePlaylist(id, n.trim()); renaming = null } }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null }, containerColor = Rift.Surface,
            title = { Text("Delete \"${library.playlists.firstOrNull { it.id == id }?.name}\"?") },
            text = { Text("The songs stay in Liked Songs and your history.") },
            confirmButton = { TextButton({ vm.deletePlaylist(id); deleting = null; collection = null }) { Text("Delete") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } },
        )
    }
    if (editProfile) NameDialog("Your name", s.name, "Save", onDismiss = { editProfile = false }) { n -> vm.updateSettings { it.copy(name = n.trim()) }; editProfile = false }
}

fun profileState(
    s: com.nopethedev.riftsolo.core.data.Settings, library: Library,
    components: Map<com.nopethedev.riftsolo.core.engine.ComponentId, com.nopethedev.riftsolo.core.engine.ComponentState>,
    nodeStatus: Map<String, com.nopethedev.riftsolo.core.engine.NodeStatus> = emptyMap(),
) = ProfileState(
    name = s.name, avatar = s.avatar, genres = s.genres, liked = library.liked.size, playlists = library.playlists.size, plays = library.plays,
    engine = s.engine, providers = s.providers, nodes = s.nodes, allowAllSongs = s.allowAllSongs, nodeStatus = nodeStatus, components = components,
)

/** Bottom navigation + mini player + full-screen player; shared by the app and the screenshot tests. */
@Composable
fun MainScaffold(
    tab: Tab, onTab: (Tab) -> Unit, player: PlayerState, liked: Boolean, playerActions: PlayerActions, nowPlaying: Boolean,
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Rift.Bg)) {
        Scaffold(
            containerColor = Rift.Bg,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
            bottomBar = {
                Column(Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Rift.Bg.copy(alpha = 0.97f), Rift.Bg), 0f, 120f))) {
                    MiniPlayer(player, liked, playerActions, Modifier.padding(bottom = 4.dp))
                    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = t == tab, onClick = { onTab(t) },
                                icon = { Icon(if (t == tab) t.on else t.off, t.label) }, label = { Text(t.label) },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = Color.White, indicatorColor = Rift.Accent.copy(alpha = 0.35f), unselectedIconColor = Rift.Muted, unselectedTextColor = Rift.Muted),
                            )
                        }
                    }
                }
            },
        ) { pad -> content(pad) }
        AnimatedVisibility(nowPlaying && player.current != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            NowPlayingScreen(player, liked, playerActions)
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Rift.Surface,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors()) },
        confirmButton = { TextButton({ onConfirm(text) }) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistSheet(track: Track, library: Library, onDismiss: () -> Unit, onNew: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismiss, containerColor = Rift.Surface) {
        ListItem(headlineContent = { Text(track.title) }, supportingContent = { Text(track.artist) }, leadingContent = { Artwork(track.artworkUrl, track.id, 48.dp) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            item { ListItem(headlineContent = { Text("New playlist") }, leadingContent = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent), trailingContent = { TextButton(onNew) { Text("Create") } }) }
            items(library.playlists, key = { it.id }) { p ->
                ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text("${p.tracks.size} songs") }, colors = ListItemDefaults.colors(containerColor = Color.Transparent), trailingContent = { TextButton({ onPick(p.id) }) { Text("Add") } })
            }
        }
    }
}

