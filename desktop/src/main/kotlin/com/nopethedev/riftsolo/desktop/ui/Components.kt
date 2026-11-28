package com.nopethedev.riftsolo.desktop.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.formatDuration
import kotlin.math.abs

object Rift {
    val Bg = Color(0xFF0B0B12)
    val Side = Color(0xFF07070C)
    val Surface = Color(0xFF181824)
    val Surface2 = Color(0xFF212133)
    val Line = Color(0xFF2A2A3D)
    val Accent = Color(0xFF8B5CF6)
    val AccentDark = Color(0xFF6D28D9)
    val AccentLight = Color(0xFFA78BFA)
    val Muted = Color(0xFF9A9AB2)
    val Text = Color(0xFFF3F3FA)
    val Success = Color(0xFF34D399)
    val Danger = Color(0xFFF87171)
}

private val scheme = darkColorScheme(
    primary = Rift.Accent, onPrimary = Color.White, secondary = Rift.AccentLight, background = Rift.Bg, onBackground = Rift.Text,
    surface = Rift.Bg, onSurface = Rift.Text, surfaceVariant = Rift.Surface2, onSurfaceVariant = Rift.Muted, surfaceContainer = Rift.Surface,
    surfaceContainerHigh = Rift.Surface2, surfaceContainerHighest = Color(0xFF2A2A3F), outline = Rift.Line, error = Rift.Danger,
)

@Composable
fun RiftTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = Typography()) {
    CompositionLocalProvider(LocalContentColor provides Rift.Text, content = content)
}

private val ART = listOf(
    Color(0xFF7C3AED) to Color(0xFFEC4899), Color(0xFF0EA5E9) to Color(0xFF1E1B4B), Color(0xFFF59E0B) to Color(0xFF7C2D12),
    Color(0xFF14B8A6) to Color(0xFF0F172A), Color(0xFFF43F5E) to Color(0xFF3B0764), Color(0xFFA78BFA) to Color(0xFF1E1B4B),
)

@Composable
fun Artwork(url: String?, seed: String, size: Dp?, modifier: Modifier = Modifier, corner: Dp = 6.dp) {
    val (a, b) = ART[abs(seed.hashCode()) % ART.size]
    Box((if (size != null) modifier.size(size) else modifier).clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(a, b)))) {
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** One row of the song menu (3-dot button and right-click). */
data class MenuItem(val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/** Song menu entries, grouped; built by the caller so it knows the context (playlist, liked...). */
typealias TrackMenuBuilder = (Track) -> List<List<MenuItem>>

@Composable
fun TrackMenuDropdown(open: Boolean, groups: List<List<MenuItem>>, onDismiss: () -> Unit) {
    DropdownMenu(open, onDismiss, containerColor = Rift.Surface2) {
        groups.forEachIndexed { gi, g ->
            if (gi > 0) HorizontalDivider(color = Rift.Line)
            g.forEach { m -> DropdownMenuItem({ Text(m.label, color = if (m.danger) Rift.Danger else Rift.Text) }, { m.onClick(); onDismiss() }) }
        }
    }
}

/** A song in a list: double-click or the play button plays it, ⋯ (or right-click) opens the song menu. */
@Composable
fun TrackRow(
    t: Track, index: Int?, playing: Boolean, liked: Boolean, onPlay: () -> Unit, onLike: () -> Unit, menu: TrackMenuBuilder,
    onArtist: (() -> Unit)? = null,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var open by remember { mutableStateOf(false) }
    val groups = remember(open, t) { if (open) menu(t) else emptyList() }
    ContextMenuArea(items = { menu(t).flatten().map { ContextMenuItem(it.label, it.onClick) } }) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (hovered) Rift.Surface else Color.Transparent).hoverable(hover)
                .clickable(onClick = onPlay).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                if (hovered) Icon(Icons.Rounded.PlayArrow, "Play", Modifier.size(18.dp))
                else if (index != null) Text("$index", color = if (playing) Rift.AccentLight else Rift.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Artwork(t.artworkUrl, t.id, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = if (playing) Rift.AccentLight else Rift.Text)
                Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Rift.Muted,
                    modifier = if (onArtist != null) Modifier.clickable(onClick = onArtist) else Modifier)
            }
            if (hovered || liked) IconButton(onLike, Modifier.size(32.dp)) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", Modifier.size(18.dp), tint = if (liked) Rift.AccentLight else Rift.Muted) }
            else Spacer(Modifier.width(32.dp))
            Text(t.durationSec.formatDuration(), Modifier.width(48.dp).padding(start = 8.dp), color = Rift.Muted, style = MaterialTheme.typography.bodySmall)
            Box {
                IconButton({ open = true }, Modifier.size(32.dp)) { Icon(Icons.Rounded.MoreHoriz, "More", tint = if (hovered || open) Rift.Text else Rift.Muted.copy(alpha = 0.5f)) }
                TrackMenuDropdown(open, groups) { open = false }
            }
        }
    }
}

@Composable
fun Card(title: String, subtitle: String, seed: String, image: String?, onClick: () -> Unit, circle: Boolean = false, size: Dp = 168.dp, onMore: (() -> List<List<MenuItem>>)? = null) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var open by remember { mutableStateOf(false) }
    Column(Modifier.width(size + 24.dp).clip(RoundedCornerShape(10.dp)).background(if (hovered) Rift.Surface2 else Rift.Surface).hoverable(hover).clickable(onClick = onClick).padding(12.dp)) {
        Box {
            Artwork(image, seed, size, corner = if (circle) size / 2 else 8.dp)
            if (onMore != null && hovered) Box(Modifier.align(Alignment.TopEnd)) {
                IconButton({ open = true }, Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f))) { Icon(Icons.Rounded.MoreHoriz, "More", Modifier.size(18.dp)) }
                TrackMenuDropdown(open, if (open) onMore() else emptyList()) { open = false }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String = "") {
    Column(Modifier.padding(top = 26.dp, bottom = 12.dp)) {
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Rift.Muted)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun SideItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (selected) Rift.Surface2 else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Rift.Text else Rift.Muted)
        Spacer(Modifier.width(14.dp))
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) Rift.Text else Rift.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun Long.clock() = (this / 1000).let { "%d:%02d".format(it / 60, it % 60) }
