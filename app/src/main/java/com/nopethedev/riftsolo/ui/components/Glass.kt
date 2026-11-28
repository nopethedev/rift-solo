package com.nopethedev.riftsolo.ui.components

import android.os.Build
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView
import com.nopethedev.riftsolo.ui.theme.RiftSoloTheme

/**
 * Liquid glass (QWEA0/Liquid-Glass-Android) around Compose content. The library is a View, so the panel is a
 * LiquidGlassView hosting its own ComposeView, and it samples the app's root view: whatever scrolls under the bar is
 * refracted at the rim. Its native fallback ships for ARM only, so x86 emulators get the plain bar.
 */
object Glass {
    val supported: Boolean get() = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "armeabi-v7a" }
}

@Composable
fun GlassPanel(corner: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val latest by rememberUpdatedState(content)
    val root = LocalView.current
    val radius = with(LocalDensity.current) { corner.toPx() }
    if (LocalInspectionMode.current) { content(); return }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LiquidGlassView(ctx).apply {
                cornerRadius = radius
                material = GlassMaterial.REGULAR
                // The list scrolls under the bar: keep re-sampling it.
                enableDynamicBackground = true
                enableSensorHighlight = true
                enablePressEffect = false
                collectFrameStats = false
                setGlassTint(0xFF1A1230.toInt(), 0.35f)
                backdropSource = root
                addView(ComposeView(ctx).apply { setContent { RiftSoloTheme { latest() } } }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        },
    )
}
