package com.nopethedev.riftsolo.ui.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.ui.components.Artwork
import com.nopethedev.riftsolo.ui.components.Avatar
import com.nopethedev.riftsolo.ui.components.CoverCard
import com.nopethedev.riftsolo.ui.components.Shimmer
import com.nopethedev.riftsolo.ui.theme.Rift

@kotlinx.serialization.Serializable
data class HomeRow(val id: String, val title: String, val subtitle: String = "", val tracks: List<Track> = emptyList(), val loading: Boolean = false, val error: String? = null)

data class HomeState(
    val name: String = "",
    val avatar: String = "headphones:violet",
    val greeting: String = "Good evening",
    val likedCount: Int = 0,
    val recent: List<Track> = emptyList(),
    val rows: List<HomeRow> = emptyList(),
)

class HomeActions(
    val play: (List<Track>, Int) -> Unit = { _, _ -> },
    val openLiked: () -> Unit = {},
    val openRecent: () -> Unit = {},
    val openProfile: () -> Unit = {},
    val refresh: () -> Unit = {},
)

@Composable
fun HomeScreen(state: HomeState, actions: HomeActions, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize().drawBehind {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2A1A52), Rift.Bg), 0f, size.height * 0.35f))
        },
        contentPadding = contentPadding,
    ) {
        item {
            Row(Modifier.statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(state.avatar, 34.dp, Modifier.clickable(onClick = actions.openProfile))
                Spacer(Modifier.width(12.dp))
                Text("${state.greeting}${if (state.name.isNotBlank()) ", ${state.name}" else ""}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(actions.refresh) { Icon(Icons.Rounded.Refresh, "Refresh") }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Music", "Mixes").forEachIndexed { i, s ->
                    FilterChip(i == 0, {}, { Text(s) }, shape = RoundedCornerShape(50), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Rift.Accent, selectedLabelColor = Color.White, containerColor = Rift.Surface2), border = null)
                }
            }
        }
        item { QuickGrid(state, actions) }
        if (state.recent.isNotEmpty()) item {
            Section("Jump back in", "Your recent plays")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(state.recent.take(12), key = { _, t -> "r" + t.id }) { i, t -> CoverCard(t.title, t.artist, t.id, t.artworkUrl, { actions.play(state.recent, i) }) }
            }
        }
        state.rows.forEach { row ->
            item(key = row.id) {
                Section(row.title, row.subtitle)
                when {
                    row.loading && row.tracks.isEmpty() -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), userScrollEnabled = false) {
                        items(4) { Column { Shimmer(Modifier.size(148.dp)); Spacer(Modifier.height(8.dp)); Shimmer(Modifier.width(110.dp).height(12.dp)) } }
                    }
                    row.error != null && row.tracks.isEmpty() -> Text(row.error, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    else -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(row.tracks, key = { _, t -> row.id + t.id }) { i, t -> CoverCard(t.title, t.artist, t.id, t.artworkUrl, { actions.play(row.tracks, i) }) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Section(title: String, subtitle: String) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)) {
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun QuickGrid(state: HomeState, actions: HomeActions) {
    // Spotify-style 2-column shortcuts: liked songs, history, then recent songs.
    data class Quick(val title: String, val onClick: () -> Unit, val art: @Composable () -> Unit)
    val items = buildList {
        add(Quick("Liked Songs", actions.openLiked) { IconTile(Icons.Rounded.Favorite, Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFFA78BFA)))) })
        add(Quick("Recently played", actions.openRecent) { IconTile(Icons.Rounded.History, Brush.linearGradient(listOf(Color(0xFF0F766E), Color(0xFF14B8A6)))) })
        state.recent.take(4).forEachIndexed { i, t -> add(Quick(t.title, { actions.play(state.recent, i) }) { Artwork(t.artworkUrl, t.id, 56.dp, corner = 0.dp) }) }
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { q ->
                    Row(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(6.dp)).background(Rift.Surface2).clickable(onClick = q.onClick), verticalAlignment = Alignment.CenterVertically) {
                        q.art()
                        Text(q.title, Modifier.padding(horizontal = 10.dp), style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun IconTile(icon: androidx.compose.ui.graphics.vector.ImageVector, brush: Brush) =
    Box(Modifier.size(56.dp).background(brush), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White) }

fun greetingFor(hour: Int) = when (hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }

