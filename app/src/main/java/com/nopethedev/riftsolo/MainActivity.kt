package com.nopethedev.riftsolo

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.nopethedev.riftsolo.ui.AppViewModel
import com.nopethedev.riftsolo.ui.RiftSoloRoot
import com.nopethedev.riftsolo.ui.components.GlassBar
import com.nopethedev.riftsolo.ui.components.GlassOverlay
import com.nopethedev.riftsolo.ui.components.LocalGlassBar
import com.nopethedev.riftsolo.ui.theme.RiftSoloTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels { AppViewModel.Factory }
    private lateinit var glass: GlassOverlay

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val bar = GlassBar()
        val app = ComposeView(this).apply {
            setContent { RiftSoloTheme { CompositionLocalProvider(LocalGlassBar provides bar) { RiftSoloRoot(vm) } } }
        }
        // The app, and next to it (not inside it) the liquid glass bar when it's on.
        val root = FrameLayout(this).apply { addView(app, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
        setContentView(root)
        glass = GlassOverlay(this, root, app, bar)
        if (glass.crashedLastTime()) {
            vm.updateSettings { it.copy(liquidGlass = false) }
            Toast.makeText(this, "Liquid glass crashed on this phone, so it was turned off.", Toast.LENGTH_LONG).show()
        }
        lifecycleScope.launch { snapshotFlow { bar.content != null }.collect { glass.update() } }
    }

    override fun onStart() { super.onStart(); if (::glass.isInitialized) glass.onStart() }
    override fun onStop() { super.onStop(); glass.onStop() }
}
