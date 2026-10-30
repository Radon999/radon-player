package org.omarchy.music.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.omarchy.music.ui.App
import org.omarchy.music.ui.Back
import org.omarchy.music.ui.OmarchyTheme

/** Radon Player on the PC: the phone's own screens (the shared code), played through mpv. Esc is the phone's back. */
fun main() = application {
    remember { org.omarchy.music.PcApp.start() }
    Window(onCloseRequest = { org.omarchy.music.Music.player?.release(); exitApplication() }, title = "Radon Player",
        state = rememberWindowState(width = 1100.dp, height = 800.dp),
        onPreviewKeyEvent = { e -> e.type == KeyEventType.KeyDown && e.key == Key.Escape && Back.press() }) {
        LaunchedEffect(Unit) {
            val M = org.omarchy.music.Music
            if (M.folders == listOf("Music/") && Library.folders().isNotEmpty())   // the Python app's folders, at the first start
                M.setFolders(Library.folders().map { it.path })
        }
        OmarchyTheme { App() }
    }
}
