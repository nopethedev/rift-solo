package com.nopethedev.riftsolo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nopethedev.riftsolo.core.Track
import com.nopethedev.riftsolo.core.formatDuration
import com.nopethedev.riftsolo.ui.theme.Rift
import kotlin.math.abs
import kotlin.math.sin

private val ART_PALETTES = listOf(
    Color(0xFF7C3AED) to Color(0xFFEC4899), Color(0xFF0EA5E9) to Color(0xFF1E1B4B), Color(0xFFF59E0B) to Color(0xFF7C2D12),
    Color(0xFF14B8A6) to Color(0xFF0F172A), Color(0xFFF43F5E) to Color(0xFF3B0764), Color(0xFFA78BFA) to Color(0xFF1E1B4B),
    Color(0xFF22D3EE) to Color(0xFF312E81), Color(0xFF84CC16) to Color(0xFF14532D),
)

/** Procedural cover used while artwork loads, when a source has none, and offline (same motifs as the teaser posters). */
@Composable
fun GeneratedArt(seed: String, modifier: Modifier = Modifier) {
    val h = abs(seed.hashCode())
    val (a, b) = ART_PALETTES[h % ART_PALETTES.size]
    val kind = (h / 7) % 4
    Canvas(modifier.background(Brush.linearGradient(listOf(a, b)))) {
        val w = size.width; val ht = size.height
        when (kind) {
            0 -> { // sun over hills
                drawCircle(Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF472B6)), ht * 0.25f, ht * 0.7f), w * 0.28f, Offset(w / 2, ht * 0.52f))
                val p = Path().apply { moveTo(0f, ht); lineTo(0f, ht * 0.62f); quadraticTo(w * 0.3f, ht * 0.48f, w * 0.55f, ht * 0.6f); quadraticTo(w * 0.8f, ht * 0.7f, w, ht * 0.55f); lineTo(w, ht); close() }
                drawPath(p, Color(0xD90F0A23))
            }
            1 -> { // planet with a ring
                drawCircle(Color.White.copy(alpha = 0.85f), w * 0.2f, Offset(w * 0.6f, ht * 0.4f))
                drawOval(Color.White.copy(alpha = 0.55f), Offset(w * 0.22f, ht * 0.33f), androidx.compose.ui.geometry.Size(w * 0.76f, ht * 0.14f), style = Stroke(w * 0.022f))
            }
            2 -> { // concentric rings
                for (i in 1..5) drawCircle(Color.White.copy(alpha = 0.10f + i * 0.05f), w * 0.09f * i, Offset(w * 0.3f, ht * 0.7f), style = Stroke(w * 0.018f))
            }
            else -> { // waves
                for (j in 0 until 6) {
                    val p = Path()
                    for (i in 0..24) {
                        val x = w * i / 24f; val y = ht * (0.25f + j * 0.11f) + sin(i * 0.8f + j + h % 5) * ht * 0.03f
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    drawPath(p, Color.White.copy(alpha = 0.4f), style = Stroke(w * 0.016f))
                }
            }
        }
    }
}

@Composable
fun Artwork(url: String?, seed: String, size: Dp?, modifier: Modifier = Modifier, corner: Dp = 6.dp) {
    val m = (if (size != null) modifier.size(size) else modifier).clip(RoundedCornerShape(corner))
    Box(m) {
        GeneratedArt(seed, Modifier.fillMaxSize())
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    liked: Boolean = false,
    onLike: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    index: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val menu = LocalTrackMenu.current
    val haptic = LocalHapticFeedback.current
    Row(
        modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = menu?.let { m -> { haptic.performHapticFeedback(HapticFeedbackType.LongPress); m(track) } }, onLongClickLabel = "Song options")
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) Text("$index", Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Artwork(track.artworkUrl, track.id, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = if (playing) Rift.AccentLight else MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(track.source)
                Spacer(Modifier.width(6.dp))
                Text(listOf(track.artist, track.durationSec.formatDuration()).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (playing) EqualizerBars(Modifier.padding(horizontal = 8.dp))
        if (onLike != null) IconButton(onLike) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", tint = if (liked) Rift.AccentLight else MaterialTheme.colorScheme.onSurfaceVariant) }
        if (onMore != null) IconButton(onMore) { Icon(Icons.Rounded.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        trailing?.invoke()
    }
}

@Composable
fun SourceBadge(source: String) {
    val (label, color) = when (source) {
        "youtube" -> "YT" to Color(0xFFEF4444)
        "soundcloud" -> "SC" to Color(0xFFFF7A1A)
        "bandcamp" -> "BC" to Color(0xFF22D3EE)
        "spotify" -> "SP" to Color(0xFF1ED760)
        else -> return
    }
    Box(Modifier.clip(RoundedCornerShape(3.dp)).background(color.copy(alpha = 0.2f)).padding(horizontal = 4.dp, vertical = 1.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

/** Big square card for home rows. */
@Composable
fun CoverCard(title: String, subtitle: String, seed: String, artworkUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 148.dp, circle: Boolean = false, track: Track? = null) {
    val menu = LocalTrackMenu.current
    val haptic = LocalHapticFeedback.current
    Column(modifier.width(size).clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = onClick, onLongClick = if (track != null && menu != null) ({ haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu(track) }) else null)) {
        Artwork(artworkUrl, seed, size, corner = if (circle) size / 2 else 8.dp)
        Spacer(Modifier.height(8.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EqualizerBars(modifier: Modifier = Modifier, color: Color = Rift.AccentLight, animated: Boolean = !LocalInspectionMode.current) {
    val phase = if (animated) rememberInfiniteTransition(label = "eq").animateFloat(0f, 6.28f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "p").value else 1f
    Row(modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 0 until 3) {
            val k = 0.35f + 0.65f * abs(sin(phase + i * 1.3f))
            Box(Modifier.width(3.dp).fillMaxHeightFraction(k).clip(RoundedCornerShape(1.dp)).background(color))
        }
    }
}

private fun Modifier.fillMaxHeightFraction(f: Float) = this.then(Modifier.height((14 * f).dp))

/** Placeholder block while rows load. */
@Composable
fun Shimmer(modifier: Modifier) {
    val a = if (LocalInspectionMode.current) 0.6f else rememberInfiniteTransition(label = "sh").animateFloat(0.35f, 0.7f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a").value
    Box(modifier.graphicsLayer { alpha = a }.clip(RoundedCornerShape(8.dp)).background(Rift.Surface2))
}

@Composable
fun EmptyArt(size: Dp) = Box(Modifier.size(size).clip(RoundedCornerShape(6.dp)).background(Rift.Surface2), contentAlignment = Alignment.Center) {
    Icon(Icons.Rounded.MusicNote, null, tint = Rift.Muted)
}

/** Spinners that freeze in previews / screenshot tests (an endless animation never lets Compose go idle). */
@Composable
fun Spinner(modifier: Modifier = Modifier, color: Color = Rift.Accent, strokeWidth: Dp = 2.5.dp) =
    if (LocalInspectionMode.current) androidx.compose.material3.CircularProgressIndicator(progress = { 0.7f }, modifier = modifier, color = color, strokeWidth = strokeWidth)
    else androidx.compose.material3.CircularProgressIndicator(modifier, color = color, strokeWidth = strokeWidth)
