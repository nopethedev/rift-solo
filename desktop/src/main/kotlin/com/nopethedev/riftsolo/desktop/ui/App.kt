package com.nopethedev.riftsolo.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.taste.Taste
import com.nopethedev.riftsolo.desktop.DesktopState
import com.nopethedev.riftsolo.desktop.Screen

@Composable
fun App(app: DesktopState) {
    val screen by app.screen.collectAsState()
    val library by app.library.library.collectAsState()
    val player by app.player.state.collectAsState()
    val settings by app.settings.collectAsState()
    val lyrics by app.lyrics.collectAsState()
    val consent by app.consent.collectAsState()
    val auth by app.auth.collectAsState()
    val clipboard = LocalClipboardManager.current
    var addTo by remember { mutableStateOf<Track?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val likedIds = library.liked.map { it.id }.toSet()

    /** The song menu, for the 3-dot buttons and right-click. */
    val menu: (String?) -> TrackMenuBuilder = { playlistId -> { t ->
        val artist = DesktopState.cleanArtist(t.artist)
        val following = library.followed.any { it.key == artist.lowercase() }
        val excluded = library.excluded.any { it.id == t.id }
        listOf(
            listOf(MenuItem("Play next") { app.playNext(t) }, MenuItem("Add to queue") { app.addToQueue(t) }, MenuItem("Start radio") { app.startRadio(t) }),
            listOfNotNull(
                MenuItem("Add to playlist…") { addTo = t },
                MenuItem(if (t.id in likedIds) "Remove from Liked Songs" else "Add to Liked Songs") { app.like(t) },
                playlistId?.let { id -> MenuItem("Remove from this playlist") { app.library.removeFromPlaylist(id, t.id) } },
            ),
            listOf(
                MenuItem("Go to $artist") { app.openArtist(artist) },
                MenuItem(if (following) "Unfollow $artist" else "Follow $artist") { app.follow(artist) },
                MenuItem(if (excluded) "Include in your taste again" else "Exclude from your taste", danger = !excluded) { app.exclude(t) },
                MenuItem("Copy link") { clipboard.setText(AnnotatedString(t.url)) },
            ),
        )
    } }

    RiftTheme {
        Column(Modifier.fillMaxSize().background(Rift.Side)) {
            Row(Modifier.weight(1f).fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sidebar(app, screen, library.playlists, onNew = { newPlaylist = true }, onImport = { importing = true })
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Rift.Bg)) {
                    val ctx = Ctx(app, player.current?.id, likedIds, menu)
                    when (val s = screen) {
                        Screen.Home -> HomeView(ctx)
                        Screen.Search -> SearchView(ctx)
                        Screen.Liked -> CollectionView(ctx, "Liked Songs", "${library.liked.size} songs", library.liked, null, key = "liked")
                        Screen.Recent -> CollectionView(ctx, "Recently played", "${library.recent.size} songs", library.recent, null, key = null)
                        is Screen.PlaylistS -> library.playlists.firstOrNull { it.id == s.id }?.let { p ->
                            CollectionView(ctx, p.name, (if (p.importedFrom != null) "Imported from Spotify · " else "") + "${p.tracks.size} songs", p.tracks, p.id, key = p.id, art = p.artworkUrl,
                                onDelete = { app.library.deletePlaylist(p.id); app.go(Screen.Home) })
                        } ?: Text("Playlist not found", Modifier.padding(24.dp))
                        is Screen.ArtistS -> ArtistView(ctx, s.name)
                        is Screen.AlbumS -> AlbumView(ctx, s.album)
                        Screen.Settings -> SettingsView(app)
                    }
                }
                if (lyrics.open && player.current != null) LyricsView(app, Modifier.width(340.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(Color(0xFF3B2470), Rift.Bg))))
            }
            PlayerBar(app, likedIds, menu(null))
        }

        if (consent) AlertDialog(
            { app.answerConsent(false) }, containerColor = Rift.Surface,
            title = { Text("Allow all songs?") },
            text = { Text("Rift Solo plays whatever YouTube, SoundCloud and the other sources return. Confirm you're allowed to listen to it where you live (Rift's \"attested\" policy).") },
            confirmButton = { TextButton({ app.answerConsent(true) }) { Text("Allow and play") } },
            dismissButton = { TextButton({ app.answerConsent(false) }) { Text("Not now") } },
        )
        addTo?.let { t ->
            AlertDialog({ addTo = null }, containerColor = Rift.Surface, title = { Text("Add to playlist") },
                text = {
                    LazyColumn(Modifier.height(320.dp)) {
                        item { TextButton({ newPlaylist = true }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("New playlist") } }
                        items(library.playlists, key = { it.id }) { p ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { app.library.addToPlaylist(p.id, t); addTo = null }.padding(10.dp)) {
                                Text(p.name, Modifier.weight(1f)); Text("${p.tracks.size}", color = Rift.Muted)
                            }
                        }
                    }
                },
                confirmButton = { TextButton({ addTo = null }) { Text("Close") } })
        }
        if (newPlaylist) NameDialog("New playlist", "Create", onDismiss = { newPlaylist = false }) { n ->
            val id = app.createPlaylist(n.ifBlank { "My playlist #${library.playlists.size + 1}" }, addTo)
            newPlaylist = false; addTo = null; app.go(Screen.PlaylistS(id))
        }
        if (importing) ImportDialog(app) { importing = false }
        if (auth.open) AuthDialog(app)
        if (!settings.onboarded) WelcomeDialog(app)
    }
}

/** What every view needs. */
class Ctx(val app: DesktopState, val playingId: String?, val likedIds: Set<String>, val menu: (String?) -> TrackMenuBuilder)

@Composable
private fun Sidebar(app: DesktopState, screen: Screen, playlists: List<com.nopethedev.riftsolo.core.Playlist>, onNew: () -> Unit, onImport: () -> Unit) {
    Column(Modifier.width(250.dp).fillMaxHeight()) {
        Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Rift.Bg).padding(10.dp)) {
            Row(Modifier.padding(start = 10.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(Rift.AccentLight, Rift.AccentDark))))
                Spacer(Modifier.width(10.dp))
                Text("Rift Solo", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
            }
            SideItem(Icons.Rounded.Home, "Home", screen == Screen.Home) { app.go(Screen.Home) }
            SideItem(Icons.Rounded.Search, "Search", screen == Screen.Search) { app.go(Screen.Search) }
            SideItem(Icons.Rounded.Settings, "Settings", screen == Screen.Settings) { app.go(Screen.Settings) }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Rift.Bg).padding(10.dp)) {
            Row(Modifier.padding(start = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Your library", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Rift.Muted)
                IconButton(onNew, Modifier.size(32.dp)) { Icon(Icons.Rounded.Add, "New playlist", tint = Rift.Muted) }
            }
            LazyColumn {
                item { SideItem(Icons.Rounded.Favorite, "Liked Songs", screen == Screen.Liked) { app.go(Screen.Liked) } }
                item { SideItem(Icons.Rounded.History, "Recently played", screen == Screen.Recent) { app.go(Screen.Recent) } }
                items(playlists, key = { it.id }) { p -> SideItem(Icons.AutoMirrored.Rounded.QueueMusic, p.name, screen == Screen.PlaylistS(p.id)) { app.go(Screen.PlaylistS(p.id)) } }
                item { TextButton(onImport, Modifier.padding(top = 6.dp)) { Text("Import from Spotify", color = Rift.AccentLight) } }
            }
        }
    }
}

@Composable
fun TopBar(app: DesktopState, title: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(app::back, enabled = app.canGoBack, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f))) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun HomeView(c: Ctx) {
    val rows by c.app.home.collectAsState()
    val disc by c.app.discovery.collectAsState()
    val recent by c.app.library.library.collectAsState()
    val name = c.app.settings.collectAsState().value.name
    LazyColumn(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A1A52), Rift.Bg), 0f, 700f)), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                Text("${if (h in 5..11) "Good morning" else if (h in 12..17) "Good afternoon" else "Good evening"}${if (name.isNotBlank()) ", $name" else ""}",
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                IconButton({ c.app.loadHome(refresh = true) }) { Icon(Icons.Rounded.Refresh, "Refresh") }
            }
        }
        if (recent.recent.isNotEmpty()) item { TrackShelf(c, "Jump back in", "Your recent plays", recent.recent.take(14)) }
        rows.forEachIndexed { i, row ->
            if (i == 2) item { Discovery(c, disc) }
            item(key = row.id) {
                when {
                    row.loading && row.tracks.isEmpty() -> { SectionTitle(row.title, row.subtitle); CircularProgressIndicator(Modifier.size(24.dp), color = Rift.Accent) }
                    row.tracks.isEmpty() -> { SectionTitle(row.title, row.subtitle); Text(row.error ?: "Nothing here yet", color = Rift.Muted) }
                    else -> TrackShelf(c, row.title, row.subtitle, row.tracks)
                }
            }
        }
        if (rows.size < 3) item { Discovery(c, disc) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TrackShelf(c: Ctx, title: String, subtitle: String, tracks: List<Track>) {
    SectionTitle(title, subtitle)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(tracks, key = { _, t -> title + t.id }) { i, t -> Card(t.title, t.artist, t.id, t.artworkUrl, { c.app.play(tracks, i) }, onMore = { c.menu(null)(t) }) }
    }
}

@Composable
private fun Discovery(c: Ctx, d: com.nopethedev.riftsolo.core.taste.Discovery) {
    Column {
        if (d.artists.isNotEmpty()) {
            val because = d.artists.flatMap { it.because }.distinct().take(2)
            SectionTitle("Fans also like", if (because.isEmpty()) "Artists for you" else "Because you listen to ${because.joinToString(" and ")}")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(d.artists, key = { "a" + it.artist.name }) { p -> Card(p.artist.name, "Like ${p.because.first()}", p.artist.name, p.artist.pictureUrl, { c.app.openArtist(p.artist.name, p.artist) }, circle = true, size = 150.dp) }
            }
        }
        if (d.albums.isNotEmpty()) {
            SectionTitle("Albums for you", "From artists your artists' fans play")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(d.albums, key = { "al" + it.album.deezerId }) { p -> Card(p.album.title, listOf(p.album.artist, p.album.year).filter { it.isNotBlank() }.joinToString(" · "), "al${p.album.deezerId}", p.album.coverUrl, { c.app.openAlbum(p.album) }) }
            }
        }
    }
}

@Composable
private fun SearchView(c: Ctx) {
    val s by c.app.search.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar(c.app) {}
        OutlinedTextField(s.query, c.app::setQuery, Modifier.fillMaxWidth().padding(horizontal = 24.dp), placeholder = { Text("What do you want to play?") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(50),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { c.app.submitSearch() }),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search))
        when {
            s.loading && s.results.isEmpty() -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Rift.Accent) }
            s.error != null -> Text(s.error!!, Modifier.padding(24.dp), color = Rift.Muted)
            s.query.isBlank() -> Column(Modifier.padding(24.dp)) {
                Text("Browse", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.nopethedev.riftsolo.core.GENRES.take(6).forEach { g ->
                        Box(Modifier.size(140.dp, 80.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(g.colors.first), Color(g.colors.second)))).clickable { c.app.setQuery(g.query); c.app.submitSearch() }.padding(12.dp)) {
                            Text(g.label, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                itemsIndexed(s.results, key = { _, t -> t.id }) { i, t ->
                    TrackRow(t, i + 1, t.id == c.playingId, t.id in c.likedIds, { c.app.play(s.results, i) }, { c.app.like(t) }, c.menu(null), onArtist = { c.app.openArtist(t.artist) })
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String, image: String?, seed: String, circle: Boolean, kind: String, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF3B2470), Rift.Bg))).padding(24.dp), verticalAlignment = Alignment.Bottom) {
        Artwork(image, seed, 200.dp, corner = if (circle) 100.dp else 8.dp)
        Spacer(Modifier.width(24.dp))
        Column {
            Text(kind, style = MaterialTheme.typography.labelMedium)
            Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Rift.Muted)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { actions() }
        }
    }
}

@Composable
private fun PlayAll(c: Ctx, tracks: List<Track>) {
    FilledIconButton({ if (tracks.isNotEmpty()) c.app.play(tracks, 0) }, Modifier.size(52.dp), enabled = tracks.isNotEmpty(), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Rift.Accent)) {
        Icon(Icons.Rounded.PlayArrow, "Play", Modifier.size(30.dp), tint = Color.White)
    }
    IconButton({ if (tracks.isNotEmpty()) c.app.play(tracks.shuffled(), 0) }, enabled = tracks.isNotEmpty()) { Icon(Icons.Rounded.Shuffle, "Shuffle") }
}

@Composable
private fun CollectionView(c: Ctx, title: String, subtitle: String, tracks: List<Track>, playlistId: String?, key: String?, art: String? = null, onDelete: (() -> Unit)? = null) {
    val recs by c.app.recs.collectAsState()
    LaunchedEffect(key, tracks.size) { if (key != null) c.app.loadRecs(key, tracks) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { TopBar(c.app) }
        item {
            Header(title, subtitle, art ?: tracks.firstOrNull()?.artworkUrl, title, false, if (playlistId != null) "Playlist" else "Collection") {
                PlayAll(c, tracks)
                if (onDelete != null) TextButton(onDelete) { Text("Delete playlist", color = Rift.Danger) }
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            Box(Modifier.padding(horizontal = 14.dp)) { TrackRow(t, i + 1, t.id == c.playingId, t.id in c.likedIds, { c.app.play(tracks, i) }, { c.app.like(t) }, c.menu(playlistId), onArtist = { c.app.openArtist(t.artist) }) }
        }
        val more = key?.let { recs[it] }.orEmpty()
        if (more.isNotEmpty()) {
            item { Box(Modifier.padding(horizontal = 24.dp)) { SectionTitle("Recommended", "Based on what's in here") } }
            itemsIndexed(more, key = { _, t -> "rec" + t.id }) { i, t ->
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { TrackRow(t, null, t.id == c.playingId, t.id in c.likedIds, { c.app.play(more, i) }, { c.app.like(t) }, c.menu(null)) }
                    if (playlistId != null) OutlinedButton({ c.app.library.addToPlaylist(playlistId, t) }, shape = RoundedCornerShape(50)) { Text("Add") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ArtistView(c: Ctx, name: String) {
    val pages by c.app.artistPages.collectAsState()
    val library by c.app.library.library.collectAsState()
    val p = pages[Taste.artistKey(name)]
    val following = library.followed.any { it.key == name.lowercase() }
    LazyColumn(Modifier.fillMaxSize()) {
        item { TopBar(c.app) }
        item {
            Header(p?.artist?.name ?: name, p?.artist?.fans?.takeIf { it > 0 }?.let { "%,d fans".format(it) }.orEmpty(), p?.artist?.pictureUrl, name, true, "Artist") {
                PlayAll(c, p?.top.orEmpty())
                if (following) OutlinedButton({ c.app.follow(name, p?.artist) }, shape = RoundedCornerShape(50)) { Text("Following") }
                else Button({ c.app.follow(name, p?.artist) }, shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF111111))) { Text("Follow") }
            }
        }
        if (p == null || (p.loading && p.top.isEmpty())) item { CircularProgressIndicator(Modifier.padding(24.dp), color = Rift.Accent) }
        else {
            item { Box(Modifier.padding(horizontal = 24.dp)) { SectionTitle("Popular") } }
            itemsIndexed(p.top.take(10), key = { _, t -> "t" + t.id }) { i, t -> Box(Modifier.padding(horizontal = 14.dp)) { TrackRow(t, i + 1, t.id == c.playingId, t.id in c.likedIds, { c.app.play(p.top, i) }, { c.app.like(t) }, c.menu(null)) } }
            if (p.albums.isNotEmpty()) item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    SectionTitle("Albums")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(p.albums, key = { it.deezerId }) { a -> Card(a.title, listOf(a.year, a.type).filter { it.isNotBlank() }.joinToString(" · "), "al${a.deezerId}", a.coverUrl, { c.app.openAlbum(a) }) } }
                }
            }
            if (p.related.isNotEmpty()) item {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    SectionTitle("Fans also like")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(p.related.take(15), key = { "r" + it.name }) { a -> Card(a.name, "Artist", a.name, a.pictureUrl, { c.app.openArtist(a.name, a) }, circle = true, size = 140.dp) } }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AlbumView(c: Ctx, album: com.nopethedev.riftsolo.core.taste.Album) {
    val pages by c.app.albumPages.collectAsState()
    val p = pages[album.deezerId]
    LazyColumn(Modifier.fillMaxSize()) {
        item { TopBar(c.app) }
        item {
            Header(album.title, listOf(album.artist, album.year).filter { it.isNotBlank() }.joinToString(" · "), album.coverUrl, "al${album.deezerId}", false, "Album") {
                PlayAll(c, p?.tracks.orEmpty())
                OutlinedButton({ c.app.openArtist(album.artist) }, shape = RoundedCornerShape(50)) { Text(album.artist) }
            }
        }
        if (p == null || p.loading) item { CircularProgressIndicator(Modifier.padding(24.dp), color = Rift.Accent) }
        else {
            if (p.error != null) item { Text(p.error, Modifier.padding(24.dp), color = Rift.Muted) }
            itemsIndexed(p.tracks, key = { _, t -> t.id }) { i, t -> Box(Modifier.padding(horizontal = 14.dp)) { TrackRow(t, i + 1, t.id == c.playingId, t.id in c.likedIds, { c.app.play(p.tracks, i) }, { c.app.like(t) }, c.menu(null)) } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LyricsView(app: DesktopState, modifier: Modifier) {
    val l by app.lyrics.collectAsState()
    val pos by app.player.state.collectAsState()
    Column(modifier.padding(20.dp)) {
        Text("Lyrics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        val ly = l.lyrics
        when {
            l.loading -> CircularProgressIndicator(color = Rift.Accent)
            ly == null || !ly.found -> Text("No lyrics found for this song", color = Rift.Muted)
            else -> {
                val current = ly.lineAt(pos.positionMs)
                val list = rememberLazyListState()
                LaunchedEffect(current) { if (current >= 0) list.animateScrollToItem((current - 3).coerceAtLeast(0)) }
                LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(ly.lines) { i, line ->
                        Text(line.text.ifBlank { "♪" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            color = when { !ly.synced || i == current -> Color.White; i < current -> Color.White.copy(alpha = 0.45f); else -> Color.White.copy(alpha = 0.3f) },
                            modifier = line.timeMs?.let { t -> Modifier.clickable { app.player.seek(t) } } ?: Modifier)
                    }
                    item { Text(ly.source, style = MaterialTheme.typography.labelSmall, color = Rift.Muted, modifier = Modifier.padding(top = 12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PlayerBar(app: DesktopState, likedIds: Set<String>, menu: TrackMenuBuilder) {
    val s by app.player.state.collectAsState()
    val lyrics by app.lyrics.collectAsState()
    var dragging by remember { mutableStateOf<Float?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        val t = s.current
        Row(Modifier.width(300.dp), verticalAlignment = Alignment.CenterVertically) {
            if (t != null) {
                Artwork(t.artworkUrl, t.id, 54.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Rift.Muted, modifier = Modifier.clickable { app.openArtist(t.artist) })
                }
                IconButton({ app.like(t) }) { Icon(if (t.id in likedIds) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", tint = if (t.id in likedIds) Rift.AccentLight else Rift.Muted) }
                Box {
                    IconButton({ menuOpen = true }) { Icon(Icons.Rounded.MoreHoriz, "More", tint = Rift.Muted) }
                    TrackMenuDropdown(menuOpen, if (menuOpen) menu(t) else emptyList()) { menuOpen = false }
                }
            }
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconButton(app.player::previous, enabled = t != null) { Icon(Icons.Rounded.SkipPrevious, "Previous") }
                FilledIconButton(app.player::toggle, Modifier.size(40.dp), enabled = t != null, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color(0xFF111111))) {
                    if (s.loading) CircularProgressIndicator(Modifier.size(18.dp), color = Rift.AccentDark, strokeWidth = 2.dp)
                    else Icon(if (s.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause")
                }
                IconButton({ app.player.next() }, enabled = t != null) { Icon(Icons.Rounded.SkipNext, "Next") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(560.dp)) {
                val dur = s.durationMs.coerceAtLeast(1)
                Text(((dragging?.let { (it * dur).toLong() }) ?: s.positionMs).clock(), style = MaterialTheme.typography.labelSmall, color = Rift.Muted)
                ThinSlider(dragging ?: (s.positionMs.toFloat() / dur).coerceIn(0f, 1f), { dragging = it }, Modifier.weight(1f).padding(horizontal = 8.dp),
                    onDone = { dragging?.let { app.player.seek((it * dur).toLong()) }; dragging = null })
                Text(if (s.durationMs > 0) s.durationMs.clock() else "--:--", style = MaterialTheme.typography.labelSmall, color = Rift.Muted)
            }
            s.error?.let { Text(it, color = Rift.Danger, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
        }
        Row(Modifier.width(300.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton(app::toggleLyrics, enabled = t != null) { Icon(Icons.Rounded.Lyrics, "Lyrics", tint = if (lyrics.open) Rift.AccentLight else Rift.Muted) }
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = Rift.Muted)
            ThinSlider(s.volume / 100f, { app.setVolume((it * 100).toInt()) }, Modifier.width(120.dp))
        }
    }
}


/** Spotify-style thin bar with a small round knob. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ThinSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    val colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Rift.Line)
    Slider(value, onChange, modifier.height(20.dp), onValueChangeFinished = onDone, colors = colors,
        thumb = { Box(Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White)) },
        track = { st -> SliderDefaults.Track(st, Modifier.height(4.dp), colors = colors, thumbTrackGapSize = 0.dp, drawStopIndicator = null) })
}

@Composable
fun NameDialog(title: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismiss, containerColor = Rift.Surface, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, shape = RoundedCornerShape(10.dp)) },
        confirmButton = { TextButton({ onConfirm(text.trim()) }) { Text(confirm) } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
private fun ImportDialog(app: DesktopState, onDismiss: () -> Unit) {
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog({ if (!busy) onDismiss() }, containerColor = Rift.Surface, title = { Text("Import from Spotify") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paste a public playlist or album link.", color = Rift.Muted)
                OutlinedTextField(link, { link = it }, singleLine = true, placeholder = { Text("https://open.spotify.com/playlist/…") }, shape = RoundedCornerShape(10.dp))
                error?.let { Text(it, color = Rift.Danger) }
            }
        },
        confirmButton = {
            TextButton({ busy = true; error = null; app.importSpotify(link) { e -> busy = false; if (e == null) onDismiss() else error = e } }, enabled = !busy && link.isNotBlank()) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Import")
            }
        },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancel") } })
}

@Composable
fun AuthDialog(app: DesktopState) {
    val a by app.auth.collectAsState()
    val account by app.accounts.account.collectAsState()
    var server by remember { mutableStateOf(account.serverUrl) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog({ if (!a.busy) app.auth.value = com.nopethedev.riftsolo.desktop.AuthState() }, containerColor = Rift.Surface,
        title = { Text(if (a.signUp) "Create your account" else "Sign in") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(360.dp)) {
                OutlinedTextField(server, { server = it }, label = { Text("Server") }, placeholder = { Text("https://rift.example.com") }, singleLine = true)
                if (a.signUp) OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                a.error?.let { Text(it, color = Rift.Danger) }
                TextButton({ app.auth.value = a.copy(signUp = !a.signUp, error = null) }, enabled = !a.busy) { Text(if (a.signUp) "Have an account? Sign in" else "New here? Sign up", color = Rift.AccentLight) }
            }
        },
        confirmButton = {
            Button({ app.submitAuth(server, email, password, name) }, enabled = !a.busy && server.isNotBlank() && email.isNotBlank() && password.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Rift.Accent)) {
                if (a.busy) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp) else Text(if (a.signUp) "Sign up" else "Sign in")
            }
        },
        dismissButton = { TextButton({ app.auth.value = com.nopethedev.riftsolo.desktop.AuthState() }, enabled = !a.busy) { Text("Cancel") } })
}

/** First run on this computer: a name and the "allow all songs" consent, then straight in. */
@Composable
private fun WelcomeDialog(app: DesktopState) {
    var name by remember { mutableStateOf("") }
    AlertDialog({}, containerColor = Rift.Surface, title = { Text("Welcome to Rift Solo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.width(380.dp)) {
                Text("Search through public Lavalink nodes and play straight from the source. Already use Rift Solo on your phone? Sign in under Settings to bring your library over.", color = Rift.Muted)
                OutlinedTextField(name, { name = it }, label = { Text("What should we call you?") }, singleLine = true)
            }
        },
        confirmButton = { Button({ app.updateSettings { it.copy(onboarded = true, name = name.trim()) }; app.loadHome(refresh = true) }, colors = ButtonDefaults.buttonColors(containerColor = Rift.Accent)) { Text("Start listening") } },
        dismissButton = { TextButton({ app.updateSettings { it.copy(onboarded = true, name = name.trim()) }; app.go(Screen.Settings); app.openAuth(false) }) { Text("Sign in instead") } })
}
