package com.nopethedev.riftsolo.ui.components

import android.app.Activity
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.liquidglass.GlassMaterial
import com.example.liquidglass.LiquidGlassView
import com.nopethedev.riftsolo.ui.theme.RiftSoloTheme

/**
 * Liquid glass bar (QWEA0/Liquid-Glass-Android).
 *
 * The library is a View that captures another view as its backdrop, and that backdrop must not contain the glass
 * itself (it would draw itself into itself and crash). In Compose the whole app is a single view, so the glass can't
 * live inside it: it is a sibling laid over the app's ComposeView, and the app hands it the bar's content through
 * [GlassBar]. Its native fallback ships for ARM only, so x86 emulators keep the plain bar.
 */
object Glass {
    val supported: Boolean get() = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "armeabi-v7a" }
}

/** What the glass overlay shows (set by the app while the bar should be glass) and how tall it turned out. */
class GlassBar {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    var heightPx by mutableIntStateOf(0)
}

val LocalGlassBar = staticCompositionLocalOf<GlassBar?> { null }

/**
 * Lays the glass over [app] inside [root] while [bar] has content. If the glass crashed the app last time (it was
 * started and the app died before the "seems fine" mark), [onCrashedBefore] is called instead so it can be turned off.
 */
class GlassOverlay(private val activity: Activity, private val root: FrameLayout, private val app: View, private val bar: GlassBar) {
    private val prefs = activity.getSharedPreferences("glass_guard", Activity.MODE_PRIVATE)
    private var glass: LiquidGlassView? = null

    /** True when the glass was being shown when the app last died. Clears the mark. */
    fun crashedLastTime(): Boolean {
        val crashed = prefs.getBoolean(KEY, false)
        if (crashed) prefs.edit().putBoolean(KEY, false).commit()
        return crashed
    }

    fun update() {
        val want = bar.content != null && Glass.supported
        if (want && glass == null) show() else if (!want && glass != null) hide()
    }

    private fun show() {
        prefs.edit().putBoolean(KEY, true).commit()
        val d = activity.resources.displayMetrics.density
        val g = try {
            LiquidGlassView(activity).apply {
                cornerRadius = 28 * d
                material = GlassMaterial.REGULAR
                enableDynamicBackground = true
                enableSensorHighlight = true
                enablePressEffect = false
                collectFrameStats = false
                setGlassTint(0xFF1A1230.toInt(), 0.35f)
                // The app's own view, which sits beside the glass (never one that contains it).
                backdropSource = app
                addView(ComposeView(activity).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setContent { RiftSoloTheme { bar.content?.invoke() } }
                    addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> bar.heightPx = v.height }
                }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        } catch (e: Throwable) {
            prefs.edit().putBoolean(KEY, false).commit()
            return
        }
        val m = (12 * d).toInt()
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { setMargins(m, 0, m, m) }
        root.addView(g, lp)
        ViewCompat.setOnApplyWindowInsetsListener(g) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            (v.layoutParams as FrameLayout.LayoutParams).bottomMargin = m + nav
            v.requestLayout()
            insets
        }
        ViewCompat.requestApplyInsets(g)
        glass = g
        // Still alive a few seconds later: the glass works on this phone.
        g.postDelayed({ prefs.edit().putBoolean(KEY, false).apply() }, 4000)
    }

    private fun hide() {
        glass?.let { root.removeView(it) }
        glass = null
        bar.heightPx = 0
    }

    fun onStart() { if (glass != null) prefs.edit().putBoolean(KEY, true).apply() }

    /** Leaving the app normally isn't a crash. */
    fun onStop() = prefs.edit().putBoolean(KEY, false).apply()

    private companion object { const val KEY = "glass_started" }
}
