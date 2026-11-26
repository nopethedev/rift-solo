package com.nopethedev.riftsolo.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Rift's palette (src/app/globals.css): near-black background, violet accent. */
object Rift {
    val Bg = Color(0xFF0B0B12)
    val Surface = Color(0xFF181824)
    val Surface2 = Color(0xFF212133)
    val Line = Color(0xFF2A2A3D)
    val Accent = Color(0xFF8B5CF6)
    val AccentDark = Color(0xFF6D28D9)
    val AccentLight = Color(0xFFA78BFA)
    val AccentPale = Color(0xFFC4B5FD)
    val Muted = Color(0xFF9A9AB2)
    val Text = Color(0xFFF3F3FA)
    val Success = Color(0xFF34D399)
    val Danger = Color(0xFFF87171)
}

private val scheme = darkColorScheme(
    primary = Rift.Accent,
    onPrimary = Color.White,
    primaryContainer = Rift.AccentDark,
    onPrimaryContainer = Color.White,
    secondary = Rift.AccentLight,
    onSecondary = Color(0xFF1E1033),
    secondaryContainer = Color(0xFF2E1F55),
    onSecondaryContainer = Rift.AccentPale,
    tertiary = Rift.AccentPale,
    background = Rift.Bg,
    onBackground = Rift.Text,
    surface = Rift.Bg,
    onSurface = Rift.Text,
    surfaceVariant = Rift.Surface2,
    onSurfaceVariant = Rift.Muted,
    surfaceContainerLowest = Color(0xFF07070C),
    surfaceContainerLow = Color(0xFF12121B),
    surfaceContainer = Rift.Surface,
    surfaceContainerHigh = Rift.Surface2,
    surfaceContainerHighest = Color(0xFF2A2A3F),
    outline = Rift.Line,
    outlineVariant = Color(0xFF1F1F2E),
    error = Rift.Danger,
)

private val base = Typography()
private val type = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyMedium = base.bodyMedium,
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun RiftSoloTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, typography = type) {
    // Screens draw on Rift.Bg without a Surface, so set the default text / icon colour here.
    CompositionLocalProvider(LocalContentColor provides Rift.Text, content = content)
}
