package org.omarchy.music.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.omarchy.music.Music
import org.omarchy.music.ui.Back
import org.omarchy.music.ui.C
import org.omarchy.music.ui.Ico
import org.omarchy.music.ui.Icon
import org.omarchy.music.ui.MiniPlayer
import org.omarchy.music.ui.OmarchyTheme
import org.omarchy.music.ui.PcWindows
import org.omarchy.music.ui.App
import org.omarchy.music.ui.drawIco

/** The app's icon (the tray's, the windows'): a note on the accent colour. */
private object AppIcon : Painter() {
    override val intrinsicSize = Size(64f, 64f)
    override fun DrawScope.onDraw() {
        drawCircle(C.accent, size.minDimension / 2, Offset(size.width / 2, size.height / 2))
        inset(size.width * 0.2f) { scale(size.width / 24f, size.height / 24f, Offset.Zero) { drawIco(Ico.NOTE, Color.White) } }
    }
}

/** Radon Player on the PC: the phone's own screens (the shared code), played through mpv. Esc is the phone's back.
 *  Closing the window keeps the music going in the tray (Quit there ends it), as the Python app does. */
fun main() = application {
    remember { org.omarchy.music.PcApp.start() }
    var shown by remember { mutableStateOf(true) }
    fun quit() { Music.player?.release(); exitApplication() }
    Tray(icon = AppIcon, tooltip = Music.current()?.let { "${it.title} · ${it.artist}" } ?: "Radon Player",
        onAction = { shown = true; PcWindows.mini = false }) {
        Item(if (Music.paused) "Play" else "Pause", onClick = { Music.togglePause() })
        Item("Next", onClick = { Music.next() })
        Item("Previous", onClick = { Music.previous() })
        Separator()
        Item("Show Radon Player", onClick = { shown = true; PcWindows.mini = false })
        Item("Mini player", onClick = { PcWindows.mini = true })
        Separator()
        Item("Quit", onClick = { quit() })
    }
    if (shown && !PcWindows.mini) Window(onCloseRequest = { shown = false }, title = "Radon Player", icon = AppIcon,
        state = rememberWindowState(width = 1100.dp, height = 800.dp),
        onPreviewKeyEvent = { e -> e.type == KeyEventType.KeyDown && e.key == Key.Escape && Back.press() }) {
        LaunchedEffect(Unit) {
            val M = Music
            if (M.folders == listOf("Music/") && Library.folders().isNotEmpty())   // the Python app's folders, at the first start
                M.setFolders(Library.folders().map { it.path })
        }
        OmarchyTheme { App() }
    }
    if (PcWindows.mini) Window(onCloseRequest = { PcWindows.mini = false }, title = "Radon Player", icon = AppIcon, alwaysOnTop = true,
        resizable = true, state = rememberWindowState(size = DpSize(380.dp, 300.dp), position = WindowPosition(Alignment.BottomEnd))) {
        OmarchyTheme { MiniWindow() }
    }
}

/** The mini player on the PC: the phone's picture-in-picture look, with the buttons under it, always on top. */
@Composable
private fun MiniWindow() {
    Column(Modifier.fillMaxSize().background(C.darker)) {
        Box(Modifier.weight(1f).fillMaxWidth()) { MiniPlayer() }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable { Music.previous() }.padding(6.dp)) { Icon(Ico.PREV, C.fg, 24.dp) }
            Box(Modifier.clickable { Music.togglePause() }.padding(6.dp)) { Icon(if (Music.paused) Ico.PLAY else Ico.PAUSE, C.fg, 28.dp) }
            Box(Modifier.clickable { Music.next() }.padding(6.dp)) { Icon(Ico.NEXT, C.fg, 24.dp) }
            Box(Modifier.clickable { PcWindows.mini = false }.padding(6.dp)) { Icon(Ico.UP, C.muted, 22.dp) }
        }
    }
}
