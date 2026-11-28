package com.nopethedev.riftsolo.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.nopethedev.riftsolo.desktop.ui.App

fun main() {
    // NewPipe and Lavaplayer log a lot through java.util.logging / slf4j; keep the console quiet.
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn")
    val state = DesktopState()
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { state.shutdown() } })
    application {
        Window(
            onCloseRequest = { runCatching { state.shutdown() }; exitApplication() },
            title = "Rift Solo",
            state = rememberWindowState(size = DpSize(1280.dp, 820.dp)),
        ) {
            window.minimumSize = java.awt.Dimension(980, 640)
            App(state)
        }
    }
}
