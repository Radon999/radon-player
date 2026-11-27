package org.omarchy.music.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
    // (Linux on a desktop that places windows itself: started again with its one setting; that start does the rest)
    if (startedAgainForItsDesktop(args)) return
    // One Radon Player at a time, whatever version: another running (or starting this moment) is handed what was asked
    // (a song file, next, show…) and this start ends here, as the Python app's did. Never a second window.
    if (org.omarchy.music.Instance.claim() == org.omarchy.music.Instance.Claim.RUNNING) {
        org.omarchy.music.Instance.handOffRunning(args.ifEmpty { arrayOf("show") })
        return
    }
    org.omarchy.music.SoftwareDrawing.applyAtStart()   // Linux: drawn without the graphics card, if that's switched on
    app(args)
}

/** Linux on a desktop that places windows itself (Hyprland, Sway…, [org.omarchy.music.WindowSystemRules]): the app is
 *  started again, the same way, with the one setting that makes Java follow its window's size there, and this start
 *  ends. Before any window is made. True: it was started again. Anything going wrong: it carries on as it is. */
private fun startedAgainForItsDesktop(args: Array<String>): Boolean = runCatching {
    val R = org.omarchy.music.WindowSystemRules
    if (!R.startAgain(System.getProperty("os.name").orEmpty(), System.getenv())) return@runCatching false
    val me = ProcessHandle.current().info()
    val exe = me.command().orElse(null) ?: return@runCatching false
    val asked: List<String> = me.arguments().map { it.toList() }.orElse(args.toList())
    val again = ProcessBuilder(listOf(exe) + asked).inheritIO()
    again.environment()[R.SETTING] = "1"
    again.start()
    true
}.getOrDefault(false)

private fun app(args: Array<String>) = application {
    remember { org.omarchy.music.PcApp.start() }
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { org.omarchy.music.Startup.moveOld() }; org.omarchy.music.Shortcuts.tidy() } }
    // Started with Windows (or Linux): in the tray. On a desktop with no tray (some Linux ones) its window opens
    // instead: started hidden there, nothing would show it was running.
    // Each time something asks for the window (a widget's Lyrics card, a command, the tray): it comes to the front
    var raise by remember { mutableStateOf(0) }
    var shown by remember { mutableStateOf("--tray" !in args || !runCatching { java.awt.SystemTray.isSupported() }.getOrDefault(true)) }
    val tray = androidx.compose.ui.window.rememberTrayState()
    remember { org.omarchy.music.ui.DesktopWidgets.atAppStart(shown) }   // a saved widget layout opened as the app starts
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
        // The main window as commands and the desktop widgets reach it (Instance.inWindow)
        org.omarchy.music.Instance.window = org.omarchy.music.Instance.MainWindow(show = { shown = true; PcWindows.mini = false; raise++ },
            isShown = { shown && !PcWindows.mini }, hide = { shown = false }, quit = { quit() })
        val act = { a: List<String> -> org.omarchy.music.Instance.act(a) }
        org.omarchy.music.Instance.listen(act)
        if (args.any { !it.startsWith("--") || it in setOf("--tidal-login", "--lastfm-login", "--spotify-login") }) act(args.toList())   // opened with a song file, or a command
    }
    Tray(icon = AppIcon, state = tray, tooltip = Music.current()?.let { "${it.title} · ${it.artist}" } ?: "Radon Player",
        onAction = { shown = true; PcWindows.mini = false; raise++ }) {
        Item(if (Music.paused) "Play" else "Pause", onClick = { Music.togglePause() })
        Item("Next", onClick = { Music.next() })
        Item("Previous", onClick = { Music.previous() })
        Separator()
        Item("Show Radon Player", onClick = { shown = true; PcWindows.mini = false; raise++ })
        Item("Mini player", onClick = { PcWindows.mini = true })
        // The desktop widgets: each its own small window (ticked: open)
        Menu("Widgets") {
            org.omarchy.music.DesktopWidgetRules.KINDS.forEach { (kind, name) ->
                CheckboxItem(name, checked = org.omarchy.music.ui.DesktopWidgets.isOpen(kind),
                    onCheckedChange = { org.omarchy.music.ui.DesktopWidgets.open(kind, it) })
            }
            Separator()
            Item("Save widget layout", onClick = { Music.bg({ org.omarchy.music.ui.DesktopWidgets.saveLayout() }) { m, _ -> m?.let { Music.toast(it) } } })
            Item("Put widgets back", onClick = { org.omarchy.music.ui.DesktopWidgets.putBackLayout() })
        }
        Separator()
        Item("Quit", onClick = { quit() })
    }
    // With no tray (some Linux desktops) a closed window would leave the app playing with no way back to it: there
    // closing minimizes it instead, and the music goes on
    val hasTray = remember { runCatching { java.awt.SystemTray.isSupported() }.getOrDefault(true) }
    val noMinimize = remember { runCatching { org.omarchy.music.WindowSystemRules.placesWindowsItself(System.getProperty("os.name").orEmpty(), System.getenv()) }.getOrDefault(false) }
    // The app goes on with no window and no tray (hidden on such a desktop) until it is told to quit
    LaunchedEffect(Unit) { kotlinx.coroutines.awaitCancellation() }
    val mainWindow = rememberWindowState(width = 1100.dp, height = 800.dp)
    if (shown && !PcWindows.mini) Window(onCloseRequest = {
            when (org.omarchy.music.OpeningRules.onClose(hasTray, noMinimize, playing = !Music.paused && Music.current() != null)) {
                "minimize" -> { mainWindow.isMinimized = true; return@Window }
                "quit" -> { quit(); return@Window }
                // No tray and no minimized windows: hidden, the music goes on. Said once, by the desktop's own notes
                "hide" -> { shown = false
                    if (!org.omarchy.music.Prefs.on("hide_hint_shown")) { org.omarchy.music.Prefs.set("hide_hint_shown", true)
                        runCatching { ProcessBuilder("notify-send", "-a", "Radon Player", "Radon Player is still playing",
                            "Open it again to see its window. To close it for good, pause the music and close the window.").start() } }
                    return@Window }
            }
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
        state = mainWindow,
        onPreviewKeyEvent = { e ->
            org.omarchy.music.ui.HeldKeys.ctrl = e.isCtrlPressed; org.omarchy.music.ui.HeldKeys.shift = e.isShiftPressed
            e.type == KeyEventType.KeyDown && e.key == Key.Escape && !org.omarchy.music.ui.Typing.now &&
                (Back.press() || (org.omarchy.music.ui.Ui.current?.tab == "player").also { if (it) { if (hasTray || noMinimize) shown = false else mainWindow.isMinimized = true } })
        },
        onKeyEvent = { e -> shortcut(e) { quit() } }) {
        // Asked for (Instance.window.show: a widget, a command, the tray): out of the taskbar if minimized, and in front
        // of every other window, with the keyboard, even when it was open behind them already
        LaunchedEffect(raise) {
            if (raise == 0) return@LaunchedEffect
            mainWindow.isMinimized = false
            kotlinx.coroutines.delay(60)
            org.omarchy.music.ui.bringToFront(window)
        }
        LaunchedEffect(Unit) {
            val M = Music
            if (M.folders == listOf("Music/") && Library.folders().isNotEmpty())   // the Python app's folders, at the first start
                M.setFolders(Library.folders().map { it.path })
        }
        org.omarchy.music.ui.LayoutWindow("main", mainWindow)
        org.omarchy.music.ui.UiSized { OmarchyTheme { App() } }
    }
    val miniWindow = rememberWindowState(size = DpSize(380.dp, 600.dp), position = WindowPosition(Alignment.BottomEnd))
    if (PcWindows.mini) Window(onCloseRequest = { PcWindows.mini = false }, title = "Radon Player", icon = AppIcon, alwaysOnTop = true,
        resizable = true, state = miniWindow) {
        org.omarchy.music.ui.LayoutWindow("mini", miniWindow)
        OmarchyTheme { MiniWindow() }
    }
    org.omarchy.music.ui.DesktopWidgetWindows(AppIcon)
}

/** The mini player on the PC: Now playing made small (the look, its stars and effects, the visualizer, the cover, the song
 *  and Now playing's own buttons, the queue and lyrics in the cover's place), always on top. */
@Composable
private fun MiniWindow() {
    val ui = org.omarchy.music.ui.Ui.current
    if (ui != null) org.omarchy.music.ui.MiniNowPlaying(ui) { PcWindows.mini = false }
    else Box(Modifier.fillMaxSize().background(C.bg))
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

