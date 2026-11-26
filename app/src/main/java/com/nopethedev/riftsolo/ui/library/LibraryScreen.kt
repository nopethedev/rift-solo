package com.nopethedev.riftsolo.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.nopethedev.riftsolo.ui.RecState
import com.nopethedev.riftsolo.ui.ImportState
import com.nopethedev.riftsolo.ui.search.SearchState
import com.nopethedev.riftsolo.ui.components.Shimmer
import com.nopethedev.riftsolo.ui.components.Spinner
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nopethedev.riftsolo.core.Playlist
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.ui.components.Artwork
import com.nopethedev.riftsolo.ui.components.Avatar
import com.nopethedev.riftsolo.ui.components.TrackRow
import com.nopethedev.riftsolo.ui.theme.Rift

data class LibraryState(
    val avatar: String = "headphones:violet",
    val liked: List<Track> = emptyList(),
    val recent: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val filter: String = "all",
)

/** What a "collection" page shows: liked songs, history or a playlist. */
sealed interface Collection {
    data object Liked : Collection
    data object Recent : Collection
    data class Of(val playlistId: String) : Collection
}

class LibraryActions(
    val open: (Collection) -> Unit = {},
    val create: () -> Unit = {},
    val filter: (String) -> Unit = {},
    val openProfile: () -> Unit = {},
    val importSpotify: () -> Unit = {},
)

@Composable
fun LibraryScreen(state: LibraryState, actions: LibraryActions, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Row(Modifier.statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(state.avatar, 34.dp, Modifier.clickable(onClick = actions.openProfile))
                Spacer(Modifier.width(12.dp))
                Text("Your Library", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(actions.importSpotify) { Icon(Icons.Rounded.Download, "Import from Spotify") }
                IconButton(actions.create) { Icon(Icons.Rounded.Add, "New playlist") }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "All", "playlists" to "Playlists", "songs" to "Songs").forEach { (id, label) ->
                    FilterChip(state.filter == id, { actions.filter(id) }, { Text(label) }, shape = RoundedCornerShape(50), border = null,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Rift.Accent, selectedLabelColor = Color.White, containerColor = Rift.Surface2))
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(16.dp), tint = Rift.Muted)
                Spacer(Modifier.width(6.dp))
                Text("Recents", style = MaterialTheme.typography.labelLarge, color = Rift.Muted)
            }
        }
        if (state.filter != "songs") {
            item { LibraryRow(Icons.Rounded.Favorite, Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFFA78BFA))), "Liked Songs", "Playlist · ${state.liked.size} songs", pinned = true) { actions.open(Collection.Liked) } }
            item { LibraryRow(Icons.Rounded.History, Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF14B8A6))), "Recently played", "${state.recent.size} songs") { actions.open(Collection.Recent) } }
            items(state.playlists, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth().clickable { actions.open(Collection.Of(p.id)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (p.tracks.isEmpty() && p.artworkUrl == null) Box(Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)).background(Rift.Surface2), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = Rift.Muted) }
                    else Artwork(p.artworkUrl ?: p.tracks.first().artworkUrl, p.tracks.firstOrNull()?.id ?: p.id, 64.dp)
                    Spacer(Modifier.width(12.dp))
                    Column { Text(p.name, fontWeight = FontWeight.SemiBold); Text((if (p.importedFrom != null) "From Spotify" else "Playlist") + " · ${p.tracks.size} songs", style = MaterialTheme.typography.bodySmall, color = Rift.Muted) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().clickable(onClick = actions.create).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Rift.Surface2), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, Modifier.size(30.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Text("Create playlist", fontWeight = FontWeight.SemiBold)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().clickable(onClick = actions.importSpotify).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Rift.Surface2), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Download, null, Modifier.size(26.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column { Text("Import from Spotify", fontWeight = FontWeight.SemiBold); Text("Paste a playlist or album link", style = MaterialTheme.typography.bodySmall, color = Rift.Muted) }
                }
            }
        }
        if (state.filter == "songs") itemsIndexed(state.liked, key = { _, t -> t.id }) { _, t -> TrackRow(t, { actions.open(Collection.Liked) }) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun LibraryRow(icon: ImageVector, brush: Brush, title: String, sub: String, pinned: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)).background(brush), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(28.dp), tint = Color.White) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pinned) { Icon(Icons.Rounded.PushPin, null, Modifier.size(13.dp), tint = Rift.AccentLight); Spacer(Modifier.width(4.dp)) }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
            }
        }
    }
}

/** Extra controls a playlist page has (Liked Songs and history get a subset). */
class CollectionExtras(
    val artworkUrl: String? = null,
    val onAddSongs: (() -> Unit)? = null,
    val onRemove: ((Track) -> Unit)? = null,
    val onRename: (() -> Unit)? = null,
    val onDelete: (() -> Unit)? = null,
    val onMore: ((Track) -> Unit)? = null,
    /** "Recommended" at the end of the list; null = not shown. */
    val recs: RecState? = null,
    val onRecPlay: (List<Track>, Int) -> Unit = { _, _ -> },
    val onRecAdd: ((Track) -> Unit)? = null,
    val onRecRefresh: () -> Unit = {},
    val onRecsShown: () -> Unit = {},
)

/** Liked songs / history / playlist page. */
@Composable
fun CollectionScreen(
    title: String, subtitle: String, tracks: List<Track>, header: Brush, playingId: String?, likedIds: Set<String>,
    onBack: () -> Unit, onPlay: (Int) -> Unit, onShuffle: () -> Unit, onLike: (Track) -> Unit, contentPadding: PaddingValues, modifier: Modifier = Modifier,
    extras: CollectionExtras = CollectionExtras(),
) {
    var menu by remember { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize().drawBehind { drawRect(header, alpha = 0.55f, size = size.copy(height = 360.dp.toPx())); drawRect(Brush.verticalGradient(listOf(Color.Transparent, Rift.Bg), 0f, 360.dp.toPx()), size = size.copy(height = 360.dp.toPx())) }, contentPadding = contentPadding) {
        item {
            Row(Modifier.statusBarsPadding().padding(4.dp).fillMaxWidth()) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Spacer(Modifier.weight(1f))
                if (extras.onRename != null || extras.onDelete != null) Box {
                    IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Playlist options") }
                    DropdownMenu(menu, { menu = false }, containerColor = Rift.Surface2) {
                        extras.onRename?.let { DropdownMenuItem({ Text("Rename") }, { menu = false; it() }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }) }
                        extras.onDelete?.let { DropdownMenuItem({ Text("Delete playlist") }, { menu = false; it() }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }) }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                val art = extras.artworkUrl ?: tracks.firstOrNull()?.artworkUrl
                if (art != null || tracks.isNotEmpty()) {
                    Artwork(art, tracks.firstOrNull()?.id ?: title, 168.dp, Modifier.align(Alignment.CenterHorizontally), corner = 6.dp)
                    Spacer(Modifier.height(20.dp))
                } else Spacer(Modifier.height(48.dp))
                Text(title, style = MaterialTheme.typography.headlineLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    extras.onAddSongs?.let { add ->
                        OutlinedButton(add, shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 14.dp)) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add songs")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onShuffle, enabled = tracks.isNotEmpty()) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = Rift.AccentLight) }
                    FilledIconButton({ onPlay(0) }, Modifier.size(56.dp), enabled = tracks.isNotEmpty(), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Rift.Accent)) { Icon(Icons.Rounded.PlayArrow, "Play", Modifier.size(32.dp)) }
                }
            }
        }
        if (tracks.isEmpty()) item {
            Text(if (extras.onAddSongs != null) "This playlist is empty. Add songs, or pick from the suggestions below." else "Nothing here yet. Songs you like and play show up here.", Modifier.padding(16.dp), color = Rift.Muted)
        }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            TrackRow(t, { onPlay(i) }, playing = t.id == playingId, liked = t.id in likedIds, onLike = { onLike(t) },
                trailing = extras.onRemove?.let { rm -> { TrackMenu(onRemove = { rm(t) }, onAddTo = extras.onMore?.let { m -> { m(t) } }) } })
        }
        extras.recs?.let { recs ->
            item {
                LaunchedEffect(Unit) { extras.onRecsShown() }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Recommended", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(if (tracks.isEmpty()) "Based on what you listen to" else "Based on the songs in this playlist", style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
                    }
                    IconButton(extras.onRecRefresh, enabled = !recs.loading) { Icon(Icons.Rounded.Refresh, "More suggestions") }
                }
            }
            when {
                recs.loading && recs.tracks.isEmpty() -> items(4) { RowPlaceholder() }
                recs.tracks.isEmpty() -> item { Text(recs.error ?: "No suggestions yet. Play or add a few songs first.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = Rift.Muted, maxLines = 3) }
                else -> itemsIndexed(recs.tracks, key = { _, t -> "rec" + t.id }) { i, t ->
                    TrackRow(t, { extras.onRecPlay(recs.tracks, i) }, playing = t.id == playingId,
                        trailing = extras.onRecAdd?.let { add -> { IconButton({ add(t) }) { Icon(Icons.Rounded.AddCircleOutline, "Add to playlist", tint = Rift.AccentLight) } } })
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TrackMenu(onRemove: () -> Unit, onAddTo: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Rift.Muted) }
        DropdownMenu(open, { open = false }, containerColor = Rift.Surface2) {
            onAddTo?.let { DropdownMenuItem({ Text("Add to another playlist") }, { open = false; it() }) }
            DropdownMenuItem({ Text("Remove from this playlist") }, { open = false; onRemove() })
        }
    }
}

@Composable
private fun RowPlaceholder() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Shimmer(Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column { Shimmer(Modifier.width(160.dp).height(12.dp)); Spacer(Modifier.height(6.dp)); Shimmer(Modifier.width(100.dp).height(10.dp)) }
    }
}

/** Full-height sheet to search and add songs to a playlist without leaving it. Same 1 s typing pause as Search. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsSheet(
    playlistName: String, state: SearchState, addedIds: Set<String>, suggestions: List<Track>,
    onQuery: (String) -> Unit, onSubmit: () -> Unit, onAdd: (Track) -> Unit, onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Rift.Surface) {
        AddSongsContent(playlistName, state, addedIds, suggestions, onQuery, onSubmit, onAdd)
    }
}

@Composable
fun AddSongsContent(
    playlistName: String, state: SearchState, addedIds: Set<String>, suggestions: List<Track>,
    onQuery: (String) -> Unit, onSubmit: () -> Unit, onAdd: (Track) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text("Add to $playlistName", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(12.dp))
        TextField(
            state.query, onQuery, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search songs or artists") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (state.query.isNotEmpty()) IconButton({ onQuery("") }) { Icon(Icons.Rounded.Close, "Clear") } },
            singleLine = true, shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            colors = TextFieldDefaults.colors(focusedContainerColor = Rift.Surface2, unfocusedContainerColor = Rift.Surface2, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        )
        val list = if (state.query.isBlank()) suggestions else state.results
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
            if (state.query.isBlank()) item { Text(if (suggestions.isEmpty()) "Type to search." else "Suggested", Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall, color = Rift.Muted) }
            if (state.loading && state.results.isEmpty() && state.query.isNotBlank()) items(5) { RowPlaceholder() }
            state.error?.takeIf { state.query.isNotBlank() }?.let { e -> item { Text(e, Modifier.padding(16.dp), color = Rift.Danger, style = MaterialTheme.typography.bodySmall) } }
            items(list, key = { "pick" + it.id }) { t ->
                val added = t.id in addedIds
                TrackRow(t, { if (!added) onAdd(t) }, trailing = {
                    IconButton({ if (!added) onAdd(t) }) {
                        Icon(if (added) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline, if (added) "Added" else "Add", tint = if (added) Rift.Success else Color.White)
                    }
                })
            }
        }
    }
}

/** Paste a Spotify link → playlist. */
@Composable
fun ImportDialog(state: ImportState, onImport: (String) -> Unit, onOpen: (String) -> Unit, onDismiss: () -> Unit, initial: String = "") {
    var link by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { if (state !is ImportState.Working) onDismiss() }, containerColor = Rift.Surface,
        title = { Text("Import from Spotify") },
        text = {
            Column {
                when (state) {
                    is ImportState.Done -> Text("Added \"${state.name}\" with ${state.count} songs. They play through their YouTube match.")
                    else -> {
                        Text("Paste the link of a public playlist or album (Share → Copy link in Spotify).", style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth(), placeholder = { Text("https://open.spotify.com/playlist/…") }, singleLine = true, enabled = state !is ImportState.Working,
                            shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Rift.Line, focusedBorderColor = Rift.Accent))
                        if (state is ImportState.Failed) { Spacer(Modifier.height(8.dp)); Text(state.message, color = Rift.Danger, style = MaterialTheme.typography.bodySmall) }
                        if (state is ImportState.Working) { Spacer(Modifier.height(12.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Spinner(Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text("Reading the playlist…", style = MaterialTheme.typography.bodySmall) } }
                    }
                }
            }
        },
        confirmButton = {
            if (state is ImportState.Done) TextButton({ onOpen(state.playlistId) }) { Text("Open playlist") }
            else TextButton({ onImport(link) }, enabled = link.isNotBlank() && state !is ImportState.Working) { Text("Import") }
        },
        dismissButton = { TextButton(onDismiss, enabled = state !is ImportState.Working) { Text(if (state is ImportState.Done) "Close" else "Cancel") } },
    )
}
