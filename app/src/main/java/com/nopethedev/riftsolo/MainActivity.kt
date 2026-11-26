package com.nopethedev.riftsolo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.nopethedev.riftsolo.ui.AppViewModel
import com.nopethedev.riftsolo.ui.RiftSoloRoot
import com.nopethedev.riftsolo.ui.theme.RiftSoloTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RiftSoloTheme { RiftSoloRoot(vm) } }
    }
}
