package com.nopethedev.riftsolo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nopethedev.riftsolo.ui.theme.Rift

private const val R_PATH = "M11 8h6.5a5 5 0 0 1 1.8 9.66L22 24h-3.6l-2.4-5.5H14V24h-3V8Zm3 3v4.5h3.4a2.25 2.25 0 0 0 0-4.5H14Z"

/** Rift's slashed-R mark (src/components/brand.tsx), drawn from the same path. */
@Composable
fun RMark(size: Dp, modifier: Modifier = Modifier) {
    val path = remember { PathParser().parsePathString(R_PATH).toPath() }
    Canvas(modifier.size(size)) {
        val s = this.size.width / 32f
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)), Offset.Zero, Offset(this.size.width, this.size.height)), cornerRadius = CornerRadius(9 * s))
        scale(s, s, pivot = Offset.Zero) {
            drawPath(path, Color.White)
            drawLine(Color(0x8C0B0B12), Offset(9f, 26f), Offset(25f, 6f), strokeWidth = 1.6f, cap = StrokeCap.Round)
        }
    }
}

/** "Rift" + violet "SOLO" pill, as in the teaser. */
@Composable
fun Wordmark(fontSize: TextUnit = 22.sp, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((fontSize.value * 0.3f).dp)) {
        Text("Rift", fontSize = fontSize, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02f * fontSize.value).sp, color = Color.White)
        Box(
            Modifier.clip(RoundedCornerShape((fontSize.value * 0.3f).dp))
                .background(Brush.linearGradient(listOf(Color(0xFFA78BFA), Color(0xFF7C3AED))))
                .padding(horizontal = (fontSize.value * 0.25f).dp, vertical = (fontSize.value * 0.05f).dp)
        ) {
            Text("SOLO", fontSize = fontSize * 0.6f, fontWeight = FontWeight.Bold, letterSpacing = (fontSize.value * 0.03f).sp, color = Color.White)
        }
    }
}

@Composable
fun Logo(markSize: Dp = 28.dp, fontSize: TextUnit = 20.sp, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RMark(markSize)
        Wordmark(fontSize)
    }
}

// ---- Avatars: same "icon:color" scheme as Rift profiles ----
val AVATAR_ICONS: Map<String, ImageVector> = linkedMapOf(
    "headphones" to Icons.Rounded.Headphones, "note" to Icons.Rounded.MusicNote, "rocket" to Icons.Rounded.RocketLaunch,
    "star" to Icons.Rounded.Star, "flame" to Icons.Rounded.LocalFireDepartment, "heart" to Icons.Rounded.Favorite,
    "bolt" to Icons.Rounded.Bolt, "cat" to Icons.Rounded.Pets, "sparkle" to Icons.Rounded.AutoAwesome,
)
val AVATAR_COLORS: Map<String, Pair<Color, Color>> = linkedMapOf(
    "violet" to (Color(0xFF8B5CF6) to Color(0xFF5B21B6)), "rose" to (Color(0xFFFB7185) to Color(0xFFBE123C)),
    "amber" to (Color(0xFFFBBF24) to Color(0xFFB45309)), "emerald" to (Color(0xFF34D399) to Color(0xFF047857)),
    "sky" to (Color(0xFF38BDF8) to Color(0xFF0369A1)), "fuchsia" to (Color(0xFFE879F9) to Color(0xFFA21CAF)),
)

@Composable
fun Avatar(avatar: String, size: Dp, modifier: Modifier = Modifier) {
    val (icon, color) = avatar.split(":").let { it.getOrElse(0) { "headphones" } to it.getOrElse(1) { "violet" } }
    val c = AVATAR_COLORS[color] ?: AVATAR_COLORS.getValue("violet")
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Brush.linearGradient(listOf(c.first, c.second))),
        contentAlignment = Alignment.Center,
    ) { Icon(AVATAR_ICONS[icon] ?: Icons.Rounded.Headphones, null, Modifier.size(size * 0.55f), tint = Color.White) }
}

val SoloGradient = Brush.linearGradient(listOf(Rift.AccentLight, Rift.AccentDark))
