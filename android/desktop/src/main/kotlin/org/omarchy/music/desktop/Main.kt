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
import androidx.compose.ui.input.key.isShiftPressed
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
    // Closing or quitting turns bit-perfect off: its exclusive hold on the sound card would keep every other app silent
    fun bitPerfectOff(): Boolean = Music.bitPerfect.also { if (it) Music.chooseBitPerfect(false) }
    fun quit() { bitPerfectOff(); Music.player?.release(); exitApplication() }
    LaunchedEffect(Unit) {
        val act = { a: List<String> -> org.omarchy.music.Instance.act(a, show = { shown = true; PcWindows.mini = false },
            hide = { shown = false }, quit = { quit() }) }
        org.omarchy.music.Instance.listen(act)
        if (args.any { !it.startsWith("--") || it in setOf("--tidal-login", "--lastfm-login", "--spotify-login") }) act(args.toList())   // opened with a song file, or a command
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
    if (shown && !PcWindows.mini) Window(onCloseRequest = {
            shown = false
            if (bitPerfectOff()) tray.sendNotification(androidx.compose.ui.window.Notification("Bit-perfect turned off",
                "So the PC's other sound is heard while Radon Player is in the tray. Turn it on again in Settings › Sound quality."))
            // The first time: say where it went (the Python app's hint)
            if (!org.omarchy.music.Prefs.on("tray_hint_shown")) {
                org.omarchy.music.Prefs.set("tray_hint_shown", true)
                tray.sendNotification(androidx.compose.ui.window.Notification("Radon Player is still playing",
                    "It's in the system tray by the clock: click its icon to open it, or right-click to quit."))
            }
        }, title = "Radon Player", icon = AppIcon,
        state = rememberWindowState(width = 1100.dp, height = 800.dp),
        onPreviewKeyEvent = { e ->
            org.omarchy.music.ui.HeldKeys.ctrl = e.isCtrlPressed; org.omarchy.music.ui.HeldKeys.shift = e.isShiftPressed
            e.type == KeyEventType.KeyDown && e.key == Key.Escape && !org.omarchy.music.ui.Typing.now &&
                (Back.press() || (org.omarchy.music.ui.Ui.current?.tab == "player").also { if (it) shown = false })
        },
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
    var queue by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(C.darker)) {
        // The cover, or (the queue button) Up next in its place, with Shuffle, Repeat, Autoplay and your queue, as the Python app
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val ui = org.omarchy.music.ui.Ui.current
            if (queue && ui != null) org.omarchy.music.ui.QueueTab(ui) else MiniPlayer()
        }
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
            Box(Modifier.clickable { queue = !queue }.padding(6.dp)) { Icon(Ico.QUEUE, if (queue) C.accent else C.muted, 22.dp) }
            Box(Modifier.clickable { PcWindows.mini = false }.padding(6.dp)) { Icon(Ico.UP, C.muted, 22.dp) }
        }
    }
}

/** The Python app's keyboard shortcuts: Ctrl+Q quits, Ctrl+, Settings, F9 or Ctrl+M the ☰ menu, Ctrl+F or Ctrl+L search;
 *  and, while you aren't typing in a box: Space play/pause, → or N next, ← or P previous, R repeat, L like, F favorite,
 *  + and − the volume, M mute, W Wrapped, E the Equalizer, / search. */
private var unmuted = 1f
/** Alt+1… the places, as the Python app's tabs: Now playing, Search, My music, Albums, Artists, Playlists, Recently
 *  played, Most played, Up next. */
private val ALT_TABS = mapOf(Key.One to ("player" to null), Key.Two to ("search" to null), Key.Three to ("music" to "all"),
    Key.Four to ("albums" to null), Key.Five to ("artists" to null), Key.Six to ("music" to "playlists"),
    Key.Seven to ("music" to "recent"), Key.Eight to ("music" to "top"), Key.Nine to ("queue" to null))
private fun shortcut(e: androidx.compose.ui.input.key.KeyEvent, quit: () -> Unit): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val ui = org.omarchy.music.ui.Ui.current ?: return false
    val ctrl = e.isCtrlPressed
    when {
        ctrl && e.key == Key.Q -> quit()
        ctrl && e.key == Key.Comma -> ui.openSettings()
        e.key == Key.F9 || (ctrl && e.key == Key.M) -> ui.drawer = !ui.drawer
        ctrl && (e.key == Key.F || e.key == Key.L) -> ui.tab = "search"
        ctrl && e.key == Key.A && ui.selecting && !org.omarchy.music.ui.Typing.now -> ui.selectAllHere()
        e.isAltPressed && ALT_TABS.containsKey(e.key) -> ALT_TABS[e.key]!!.let { (tab, view) ->
            org.omarchy.music.ui.goTo(ui, tab, view); if (tab == "player") ui.pane = "art" }
        ctrl || e.isAltPressed || org.omarchy.music.ui.Typing.now -> return false
        e.key == Key.A && ui.selecting && ui.selected.isNotEmpty() ->
            Music.enqueue(ui.selectedTracks(), if (e.isShiftPressed) "next" else "queue").also { ui.afterOption() }
        e.key == Key.S -> {   // the next source to browse and search: your music, then each service
            val order = listOf("local") + org.omarchy.music.SOURCES.keys
            val now = if (Music.browseLocal) "local" else Music.source
            val nxt = order[(order.indexOf(now) + 1) % order.size]
            if (nxt == "local") { Music.browseLocal = true; Music.toast("Browsing your own music") }
            else { Music.browseLocal = false; Music.setSource(nxt); Music.toast("Browsing ${org.omarchy.music.SOURCES[nxt]}") }
        }
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
        e.key == Key.Slash -> if (org.omarchy.music.ui.laidOut(ui) && ui.tab != "search") ui.filter = ui.filter ?: "" else ui.tab = "search"
        else -> return false
    }
    return true
}

