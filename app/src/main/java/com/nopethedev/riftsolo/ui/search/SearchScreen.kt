package com.nopethedev.riftsolo.ui.search

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import com.nopethedev.riftsolo.ui.components.Spinner
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nopethedev.riftsolo.core.GENRES
import com.nopethedev.riftsolo.core.Genre
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.ui.components.GeneratedArt
import com.nopethedev.riftsolo.ui.components.TrackRow
import com.nopethedev.riftsolo.ui.theme.Rift

data class SearchState(
    val query: String = "",
    val filter: String = "all",
    val results: List<Track> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val engineLabel: String = "Lavalink",
    val playingId: String? = null,
    val likedIds: Set<String> = emptySet(),
) {
    val visible get() = if (filter == "all") results else results.filter { it.source == filter }
}

class SearchActions(
    val query: (String) -> Unit = {},
    val filter: (String) -> Unit = {},
    val play: (List<Track>, Int) -> Unit = { _, _ -> },
    val like: (Track) -> Unit = {},
    val more: (Track) -> Unit = {},
    val browse: (Genre) -> Unit = {},
    /** Keyboard search key: search now instead of waiting for the typing pause. */
    val submit: () -> Unit = {},
)

@Composable
fun SearchScreen(state: SearchState, actions: SearchActions, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Text("Search", Modifier.statusBarsPadding().padding(start = 16.dp, top = 16.dp, bottom = 12.dp), style = MaterialTheme.typography.headlineMedium)
            TextField(
                value = state.query, onValueChange = actions.query,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Songs, artists, or a link") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Color(0xFF111111)) },
                trailingIcon = { if (state.query.isNotEmpty()) IconButton({ actions.query("") }) { Icon(Icons.Rounded.Close, "Clear", tint = Color(0xFF111111)) } },
                singleLine = true, shape = RoundedCornerShape(10.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { actions.submit() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedTextColor = Color(0xFF111111), unfocusedTextColor = Color(0xFF111111),
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Rift.AccentDark,
                    focusedPlaceholderColor = Color(0xFF555566), unfocusedPlaceholderColor = Color(0xFF555566),
                ),
            )
        }
        if (state.query.isBlank()) {
            item { Text("Browse all", Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp), style = MaterialTheme.typography.titleMedium) }
            GENRES.chunked(2).forEach { pair ->
                item(key = pair.first().id) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { g -> GenreTile(g, { actions.browse(g) }, Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("all" to "All", "youtube" to "YouTube", "soundcloud" to "SoundCloud").forEach { (id, label) ->
                        FilterChip(state.filter == id, { actions.filter(id) }, { Text(label) }, shape = RoundedCornerShape(50), border = null,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Rift.Accent, selectedLabelColor = Color.White, containerColor = Rift.Surface2))
                    }
                    Spacer(Modifier.weight(1f))
                    if (state.loading) Spinner(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            when {
                state.error != null && state.results.isEmpty() -> item { Message(Icons.Rounded.SearchOff, "Search failed", state.error) }
                !state.loading && state.visible.isEmpty() -> item { Message(Icons.Rounded.SearchOff, "No results", "Try other words, or switch sources in Profile.") }
                else -> {
                    item { Text("Results via ${state.engineLabel}", Modifier.padding(start = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    itemsIndexed(state.visible, key = { _, t -> t.id }) { i, t ->
                        TrackRow(t, { actions.play(state.visible, i) }, playing = t.id == state.playingId, liked = t.id in state.likedIds, onLike = { actions.like(t) }, onMore = { actions.more(t) })
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun GenreTile(g: Genre, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.height(96.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(Color(g.colors.first), Color(g.colors.second)))).clickable(onClick = onClick)) {
        Text(g.label, Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        GeneratedArt(g.id, Modifier.align(Alignment.BottomEnd).padding(end = 0.dp).size(64.dp).rotate(22f).clip(RoundedCornerShape(6.dp)))
    }
}

@Composable
private fun Message(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(48.dp), tint = Rift.Muted)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4)
    }
}
