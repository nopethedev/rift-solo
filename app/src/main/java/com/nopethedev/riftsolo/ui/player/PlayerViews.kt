package com.nopethedev.riftsolo.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import com.nopethedev.riftsolo.ui.components.Spinner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nopethedev.riftsolo.core.player.PlayerState
import com.nopethedev.riftsolo.ui.components.Artwork
import com.nopethedev.riftsolo.ui.components.SourceBadge
import com.nopethedev.riftsolo.ui.theme.Rift

data class PlayerActions(
    val toggle: () -> Unit = {},
    val next: () -> Unit = {},
    val previous: () -> Unit = {},
    val seek: (Long) -> Unit = {},
    val like: () -> Unit = {},
    val open: () -> Unit = {},
    val close: () -> Unit = {},
)

private fun Long.clock() = (this / 1000).let { "%d:%02d".format(it / 60, it % 60) }

@Composable
fun MiniPlayer(state: PlayerState, liked: Boolean, actions: PlayerActions, modifier: Modifier = Modifier) {
    val t = state.current ?: return
    Box(
        modifier.padding(horizontal = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF3B1F70), Color(0xFF241A40))))
            .clickable(onClick = actions.open)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(t.artworkUrl, t.id, 42.dp, corner = 6.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(state.error ?: t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = if (state.error != null) Rift.Danger else Rift.AccentPale.copy(alpha = 0.8f))
            }
            IconButton(actions.like) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", tint = if (liked) Rift.AccentLight else Color.White) }
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (state.loading && !state.isPlaying) Spinner(Modifier.size(22.dp), Color.White, 2.dp)
                else IconButton(actions.toggle) { Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause", tint = Color.White) }
            }
        }
        val p = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { p },
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp).fillMaxWidth().height(2.dp),
            color = Color.White, trackColor = Color.White.copy(alpha = 0.2f), drawStopIndicator = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(state: PlayerState, liked: Boolean, actions: PlayerActions, modifier: Modifier = Modifier) {
    val t = state.current ?: return
    var dragging by remember { mutableStateOf<Float?>(null) }
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF4C1D95), Color(0xFF1A1033), Rift.Bg)))) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(actions.close) { Icon(Icons.Rounded.KeyboardArrowDown, "Close", Modifier.size(30.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PLAYING VIA ${if (t.engine == "lavalink") "LAVALINK" else "THIS PHONE"}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f)
                    Text(if (state.queue.size > 1) "Queue · ${state.index + 1} of ${state.queue.size}" else "Single", style = MaterialTheme.typography.titleSmall)
                }
                IconButton({}) { Icon(Icons.Rounded.MoreVert, "More") }
            }
            Spacer(Modifier.weight(0.6f))
            Artwork(t.artworkUrl, t.id, null, Modifier.fillMaxWidth().aspectRatio(1f).shadow(32.dp, RoundedCornerShape(12.dp), spotColor = Rift.Accent), corner = 12.dp)
            Spacer(Modifier.weight(0.6f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SourceBadge(t.source); Spacer(Modifier.width(6.dp))
                        Text(t.artist, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(actions.like) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Like", tint = if (liked) Rift.AccentLight else Color.White, modifier = Modifier.size(28.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            val dur = state.durationMs.coerceAtLeast(1)
            Slider(
                value = dragging ?: (state.positionMs.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { actions.seek((it * dur).toLong()) }; dragging = null },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.25f)),
                thumb = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White)) },
            )
            Row(Modifier.fillMaxWidth()) {
                Text(((dragging?.let { (it * dur).toLong() }) ?: state.positionMs).clock(), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.weight(1f))
                Text(if (state.durationMs > 0) state.durationMs.clock() else "--:--", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(actions.previous, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(38.dp)) }
                FilledIconButton(actions.toggle, Modifier.size(72.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color(0xFF111111))) {
                    if (state.loading && !state.isPlaying) Spinner(Modifier.size(28.dp), Rift.AccentDark, 3.dp)
                    else Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause", Modifier.size(40.dp))
                }
                IconButton(actions.next, Modifier.size(56.dp), enabled = state.hasNext) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(38.dp)) }
            }
            if (state.error != null) Text(state.error, color = Rift.Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.weight(0.5f))
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.width(8.dp))
                val upNext = state.queue.getOrNull(state.index + 1)
                Text(if (upNext != null) "Up next: ${upNext.title}" else "End of queue", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
