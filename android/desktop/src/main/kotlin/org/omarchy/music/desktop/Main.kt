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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isAltPressed
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
fun main(args: Array<String>) {
    // Already running: hand it what was asked (a song file, next, show…) and end here, as the Python app does
    if (org.omarchy.music.Instance.handOff(args.ifEmpty { arrayOf("show") })) return
    app(args)
}

private fun app(args: Array<String>) = application {
    remember { org.omarchy.music.PcApp.start() }
    var shown by remember { mutableStateOf("--tray" !in args) }   // started with Windows: in the tray
    val tray = androidx.compose.ui.window.rememberTrayState()
    // A new song while the window is closed: a note by the clock, as the phone's notification
    val cur = Music.current()
    LaunchedEffect(cur?.id) {
        if (cur != null && !shown && !PcWindows.mini && !Music.paused)
            tray.sendNotification(androidx.compose.ui.window.Notification(cur.title, cur.artist))
    }
    fun quit() { Music.player?.release(); exitApplication() }
    LaunchedEffect(Unit) {
        val act = { a: List<String> -> org.omarchy.music.Instance.act(a, show = { shown = true; PcWindows.mini = false },
            hide = { shown = false }, quit = { quit() }) }
        org.omarchy.music.Instance.listen(act)
        if (args.any { !it.startsWith("--") }) act(args.toList())   // opened with a song file, or a command
    }
    Tray(icon = AppIcon, state = tray, tooltip = Music.current()?.let { "${it.title} · ${it.artist}" } ?: "Radon Player",
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
        onPreviewKeyEvent = { e -> e.type == KeyEventType.KeyDown && e.key == Key.Escape && Back.press() },
        onKeyEvent = { e -> shortcut(e) { quit() } }) {
        LaunchedEffect(Unit) {
            val M = Music
            if (M.folders == listOf("Music/") && Library.folders().isNotEmpty())   // the Python app's folders, at the first start
                M.setFolders(Library.folders().map { it.path })
        }
        OmarchyTheme { App() }
    }
    if (PcWindows.mini) Window(onCloseRequest = { PcWindows.mini = false }, title = "Radon Player", icon = AppIcon, alwaysOnTop = true,
        resizable = true, state = rememberWindowState(size = DpSize(380.dp, 340.dp), position = WindowPosition(Alignment.BottomEnd))) {
        OmarchyTheme { MiniWindow() }
    }
}

/** The mini player on the PC: the phone's picture-in-picture look, with the buttons under it, always on top. */
@Composable
private fun MiniWindow() {
    Column(Modifier.fillMaxSize().background(C.darker)) {
        Box(Modifier.weight(1f).fillMaxWidth()) { MiniPlayer() }
        // Where the song is (drag to seek), and what plays next, as the Python app's mini player
        val dur = Music.duration
        if (dur > 0) androidx.compose.material3.Slider(
            value = Music.position.toFloat().coerceIn(0f, dur.toFloat()), onValueChange = { Music.seek(it.toDouble()) },
            valueRange = 0f..dur.toFloat(), modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(24.dp),
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent))
        Music.queue.getOrNull(Music.index + 1)?.let { n ->
            androidx.compose.material3.Text("Up next: ${n.title} · ${n.artist}", color = C.muted, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = androidx.compose.ui.text.TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)),
                modifier = Modifier.padding(horizontal = 12.dp))
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable { Music.previous() }.padding(6.dp)) { Icon(Ico.PREV, C.fg, 24.dp) }
            Box(Modifier.clickable { Music.togglePause() }.padding(6.dp)) { Icon(if (Music.paused) Ico.PLAY else Ico.PAUSE, C.fg, 28.dp) }
            Box(Modifier.clickable { Music.next() }.padding(6.dp)) { Icon(Ico.NEXT, C.fg, 24.dp) }
            Box(Modifier.clickable { PcWindows.mini = false }.padding(6.dp)) { Icon(Ico.UP, C.muted, 22.dp) }
        }
    }
}

/** The Python app's keyboard shortcuts: Ctrl+Q quits, Ctrl+, Settings, F9 or Ctrl+M the ☰ menu, Ctrl+F or Ctrl+L search;
 *  and, while you aren't typing in a box: Space play/pause, → or N next, ← or P previous, R repeat, L like, F favorite,
 *  + and − the volume, M mute, W Wrapped, E the Equalizer, / search. */
private var unmuted = 1f
private fun shortcut(e: androidx.compose.ui.input.key.KeyEvent, quit: () -> Unit): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val ui = org.omarchy.music.ui.Ui.current ?: return false
    val ctrl = e.isCtrlPressed
    when {
        ctrl && e.key == Key.Q -> quit()
        ctrl && e.key == Key.Comma -> ui.openSettings()
        e.key == Key.F9 || (ctrl && e.key == Key.M) -> ui.drawer = !ui.drawer
        ctrl && (e.key == Key.F || e.key == Key.L) -> ui.tab = "search"
        ctrl || e.isAltPressed || org.omarchy.music.ui.Typing.now -> return false
        e.key == Key.Spacebar -> Music.togglePause()
        e.key == Key.DirectionRight || e.key == Key.N -> Music.next()
        e.key == Key.DirectionLeft || e.key == Key.P -> Music.previous()
        e.key == Key.R -> Music.cycleRepeat()
        e.key == Key.L -> Music.toggleLike()
        e.key == Key.F -> Music.current()?.let { Music.setFavorite(listOf(it)) }
        e.key == Key.Plus || e.key == Key.Equals || e.key == Key.NumPadAdd ->
            org.omarchy.music.ui.SysVolume.set((org.omarchy.music.ui.SysVolume.get() + 0.05f).coerceAtMost(1f))
        e.key == Key.Minus || e.key == Key.NumPadSubtract ->
            org.omarchy.music.ui.SysVolume.set((org.omarchy.music.ui.SysVolume.get() - 0.05f).coerceAtLeast(0f))
        e.key == Key.M -> org.omarchy.music.ui.SysVolume.get().let { v ->
            if (v > 0f) { unmuted = v; org.omarchy.music.ui.SysVolume.set(0f) } else org.omarchy.music.ui.SysVolume.set(unmuted) }
        e.key == Key.W -> ui.sheet = "wrapped"
        e.key == Key.E -> ui.sheet = "eq"
        e.key == Key.Slash -> ui.tab = "search"
        else -> return false
    }
    return true
}

