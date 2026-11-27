package org.omarchy.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.omarchy.music.DesktopWidgetRules
import org.omarchy.music.Instance
import org.omarchy.music.Lyrics
import org.omarchy.music.Music
import org.omarchy.music.Playlist
import org.omarchy.music.Prefs
import org.omarchy.music.Track
import kotlin.math.roundToInt

/** The PC's widgets: small windows of the app's own on the desktop (Windows and Linux have no widget board the app can
 *  join), each like a widget on the phone: the cover's colour, white text; on Windows (and macOS) a little see-through
 *  with rounded corners. On Linux the window is plain (a see-through window there was never shown on the screen by
 *  Hyprland with the graphics card drawing it, and the app stalled waiting on it), and the desktop rounds its corners
 *  if it does that. Each one is opened and closed from the tray's Widgets or Settings › General, moved by dragging its
 *  body, sized by its corner, and kept (open or not, where, how big, above other windows or not) across restarts. On
 *  Hyprland, which places new windows itself, it's put back where it was through hyprctl ([org.omarchy.music.Hyprland]).
 *  Save widget layout keeps the whole arrangement (each widget's place, size, workspace, choices; the main window's and
 *  the mini player's places), and the app opens it again as it starts (or now, with Put widgets back).
 *  What a widget does in the main window goes through the same commands a second launch sends ([Instance]). */
object DesktopWidgets {
    private const val PREF = "pc_widget."   // pc_: kept on this PC, never synced to the phone
    private const val TOP_OFF = "pc_widget_top_off"   // Always on top switched off once for all (it was on by itself)
    /** The layout saved with Save widget layout, and whether the app opens it as it starts. */
    const val LAYOUT = "pc_widget_layout"
    const val LAYOUT_ON = "pc_widget_layout_on"
    private val places by lazy {
        val firstTime = !Prefs.on(TOP_OFF)
        val last = DesktopWidgetRules.KINDS.associate { (k, _) ->
            val p = DesktopWidgetRules.decode(k, Prefs.text(PREF + k))
            k to (if (firstTime && p.onTop) p.copy(onTop = false) else p)
        }
        if (firstTime) Prefs.set(TOP_OFF, true)
        // As the app starts, the saved layout wins (with Open this layout when the app starts on) over where the widgets
        // were last dragged
        val start = DesktopWidgetRules.atStart(last, layout(), Prefs.on(LAYOUT_ON))
        mutableStateMapOf<String, DesktopWidgetRules.Place>().apply {
            for ((k, p) in start) { put(k, p); if (p != DesktopWidgetRules.decode(k, Prefs.text(PREF + k))) Prefs.setText(PREF + k, DesktopWidgetRules.encode(p)) }
        }
    }

    fun place(kind: String): DesktopWidgetRules.Place = places[kind] ?: DesktopWidgetRules.start(kind)
    fun save(kind: String, p: DesktopWidgetRules.Place) {
        if (places[kind] == p) return
        places[kind] = p; Prefs.setText(PREF + kind, DesktopWidgetRules.encode(p))
    }
    fun isOpen(kind: String) = place(kind).open
    fun open(kind: String, on: Boolean) = save(kind, place(kind).copy(open = on))
    val anyOpen: Boolean get() = DesktopWidgetRules.KINDS.any { isOpen(it.first) }
    fun title(kind: String) = "Radon Player · " + (DesktopWidgetRules.KINDS.firstOrNull { it.first == kind }?.second ?: kind)

    /** A line the widgets show at their foot for a few seconds (what's happening, or why something can't play): the
     *  app's own messages appear in its window, which may well be hidden in the tray while a widget is used. */
    var note by mutableStateOf<Pair<String, Long>?>(null)
        private set
    fun say(text: String) { note = text to System.currentTimeMillis(); Music.toast(text) }

    /** The cover playing's main colour (null: none), worked out once a song for every widget (the host keeps it). */
    var coverRgb by mutableStateOf<Int?>(null)
        internal set

    /** The app's look as the widgets wear it, with Widgets in the app's look on (Settings › Personalization); null: the
     *  cover's colour. Follows the look, the preset and the cover as they change. */
    val copied by androidx.compose.runtime.derivedStateOf { WidgetLook.colors(coverRgb, live = true) }

    // ---- the saved layout ----

    /** The layout saved (null: none yet). Read in Compose: it changes when one's saved. */
    fun layout(): DesktopWidgetRules.Layout? = DesktopWidgetRules.decodeLayout(Prefs.text(LAYOUT))
    /** Put widgets back pressed: open windows go back to their saved places (each window follows it). */
    var putBack by mutableStateOf(0)
        private set
    /** The app's own windows open now (main, mini), for Save widget layout. */
    internal val windows = mutableMapOf<String, androidx.compose.ui.window.WindowState>()
    const val MAIN_TITLE = "Radon Player"
    private const val MAIN_RULE = "radon-player-layout-main"
    /** The main window's target, when the app started with its saved place set up for Hyprland (see [atAppStart]). */
    @Volatile private var mainTarget: DesktopWidgetRules.Hypr.Target? = null
    private var mainDone = false

    /** Java's own scale (dp to real pixels before a window is there to ask: what its density will be). */
    internal fun awtScale(): Double = runCatching {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
    }.getOrDefault(1.0)

    /** (blocking, once as the app starts, before its window opens) With Open this layout when the app starts on: on
     *  Hyprland the main window opens at its saved place (a window rule set for it now; not when it starts in the tray). */
    fun atAppStart(mainShown: Boolean) {
        places.size   // the widgets' places worked out now (the layout's, if it wins)
        if (!mainShown || !org.omarchy.music.Hyprland.on || !Prefs.on(LAYOUT_ON)) return
        val p = layout()?.windows?.get("main") ?: return
        mainTarget = runCatching { org.omarchy.music.Hyprland.prepare(MAIN_RULE, MAIN_TITLE, p, 0, awtScale()) }.getOrNull()
    }

    /** (off the main thread) Save widget layout: every widget open now as it is (its place, size, workspace and monitor
     *  as Hyprland has them, its choices), and the main window and the mini player if they're open. What to tell you. */
    fun saveLayout(): String {
        val hypr = org.omarchy.music.Hyprland.on
        val widgets = LinkedHashMap<String, DesktopWidgetRules.Place>()
        for ((k, _) in DesktopWidgetRules.KINDS) {
            val p = place(k).takeIf { it.open } ?: continue
            widgets[k] = if (hypr) org.omarchy.music.Hyprland.kept(title(k), p) ?: p else p
        }
        val wins = LinkedHashMap<String, DesktopWidgetRules.Place>()
        for ((role, st) in windows.toMap()) {
            val pos = st.position
            val p = DesktopWidgetRules.Place(open = true, x = if (pos.isSpecified) pos.x.value.roundToInt() else null,
                y = if (pos.isSpecified) pos.y.value.roundToInt() else null, w = st.size.width.value.roundToInt(), h = st.size.height.value.roundToInt())
            wins[role] = if (hypr) org.omarchy.music.Hyprland.kept(MAIN_TITLE, p) ?: p else p
        }
        val l = DesktopWidgetRules.Layout(System.currentTimeMillis(), widgets, wins)
        Music.scope.launch(Dispatchers.Main) {
            val first = Prefs.text(LAYOUT).isBlank()
            Prefs.setText(LAYOUT, DesktopWidgetRules.encodeLayout(l))
            if (first) Prefs.set(LAYOUT_ON, true)
            for ((k, p) in widgets) save(k, p)
        }
        return "Saved the widget layout: ${widgets.size} widget${if (widgets.size != 1) "s" else ""}" +
            if (wins.isNotEmpty()) " and the app's window" else ""
    }

    /** Put widgets back: the saved ones opened, the others closed, each (and the app's windows) put back exactly. */
    fun putBackLayout() {
        val l = layout() ?: run { Music.toast("No widget layout saved yet"); return }
        for ((k, _) in DesktopWidgetRules.KINDS) save(k, l.widgets[k]?.copy(open = true) ?: place(k).copy(open = false))
        putBack++
    }

    /** The saved place of the main window or the mini player, when it's to be put there: at the start (with the switch
     *  on; the main window's only once a run) or after Put widgets back. */
    internal fun windowPlace(role: String, afterPutBack: Boolean): DesktopWidgetRules.Place? {
        val p = layout()?.windows?.get(role) ?: return null
        if (afterPutBack) return p
        if (!Prefs.on(LAYOUT_ON)) return null
        if (role == "main") { if (mainDone) return null; mainDone = true }
        return p
    }
    internal fun takeMainTarget() = mainTarget.also { mainTarget = null }
    internal const val MAIN_RULE_NAME = MAIN_RULE
}

/** The main window and the mini player in a saved layout: kept track of while open (Save widget layout takes their
 *  places), and put at their saved places as the app starts (with Open this layout when the app starts on) and after Put
 *  widgets back. On Hyprland through hyprctl (the main window opens straight there: see [DesktopWidgets.atAppStart]);
 *  elsewhere through the window itself. */
@Composable
fun LayoutWindow(role: String, state: androidx.compose.ui.window.WindowState) {
    androidx.compose.runtime.DisposableEffect(role, state) {
        DesktopWidgets.windows[role] = state
        onDispose { DesktopWidgets.windows.remove(role) }
    }
    val tick = DesktopWidgets.putBack
    LaunchedEffect(tick) {
        val p = DesktopWidgets.windowPlace(role, afterPutBack = tick > 0)
        if (org.omarchy.music.Hyprland.on) withContext(Dispatchers.IO) {
            val t = if (role == "main" && tick == 0) DesktopWidgets.takeMainTarget() else null
            if (t != null) org.omarchy.music.Hyprland.settle(DesktopWidgets.MAIN_RULE_NAME, DesktopWidgets.MAIN_TITLE, t, false)
            else if (p != null) org.omarchy.music.Hyprland.moveTo(DesktopWidgets.MAIN_TITLE, p, 0, DesktopWidgets.awtScale(), false)
        } else if (p != null && !p.hypr) {
            val x = p.x; val y = p.y
            if (x != null && y != null) state.position = WindowPosition(x.dp, y.dp)
            state.size = DpSize(p.w.dp, p.h.dp)
        }
    }
}

/** Windows: the window made a tool window (no taskbar button or Alt+Tab entry of its own; it stays where the other
 *  windows put it, in front only while it's the one used, unless Always on top is on). Elsewhere: nothing. */
internal fun winToolWindow(w: java.awt.Window) {
    if (!System.getProperty("os.name").lowercase().contains("win")) return
    runCatching {
        val u = com.sun.jna.platform.win32.User32.INSTANCE
        val hwnd = com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Native.getWindowPointer(w) ?: return)
        val gwl = com.sun.jna.platform.win32.WinUser.GWL_EXSTYLE
        val ex = u.GetWindowLong(hwnd, gwl)
        val want = (ex or 0x00000080 /* WS_EX_TOOLWINDOW */) and 0x00040000.inv() /* WS_EX_APPWINDOW */
        if (want == ex) return
        // The taskbar only looks again when the window is shown: hidden and shown again (without taking the focus)
        u.ShowWindow(hwnd, com.sun.jna.platform.win32.WinUser.SW_HIDE)
        u.SetWindowLong(hwnd, gwl, want)
        u.ShowWindow(hwnd, 4 /* SW_SHOWNOACTIVATE */)
    }.onFailure { org.omarchy.music.logWarn("a widget as a tool window", it) }
}

/** A widget's full screen: its own place in the app's window, brought to the front. */
private fun fullScreen(kind: String) {
    when (kind) {
        "queue" -> Instance.act(listOf("show", "now"))
        "playlists", "playlist" -> Instance.act(listOf("show", "playlists"))
        "dj" -> Instance.act(listOf("show", "dj"))
        "pinned" -> Instance.act(listOf("show", "pinned"))
        "top" -> Instance.act(listOf("show", "top"))
        "wrapped" -> Instance.act(listOf("show", "wrapped"))
        "lyrics" -> Instance.act(listOf("show", "lyrics"))
        "releases" -> Instance.act(listOf("show", "new releases"))
        "visualizer" -> Instance.act(listOf("show", "visualizer"))
        "shortcuts" -> Instance.inWindow { it.openSettings("General") }
        else -> Instance.window.show()
    }
}

/** A window brought in front of every other one and given the keyboard. Windows won't let an app put itself in front
 *  while another has the keyboard (it only flashes in the taskbar), so there it's made "always on top" for a moment and
 *  asked for the front through Windows itself, then let go: it stays in front until another window is clicked. */
fun bringToFront(w: java.awt.Window) {
    runCatching {
        w.isVisible = true
        if (System.getProperty("os.name").lowercase().contains("win")) {
            val was = w.isAlwaysOnTop
            w.isAlwaysOnTop = true
            w.toFront(); w.requestFocus()
            runCatching {
                val hwnd = com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Native.getWindowPointer(w))
                com.sun.jna.platform.win32.User32.INSTANCE.SetForegroundWindow(hwnd)
            }
            w.isAlwaysOnTop = was
        } else { w.toFront(); w.requestFocus() }
    }
}

/** Windows and macOS draw a see-through window; Linux gets a plain one (see [DesktopWidgets]). */
private val seeThrough = System.getProperty("os.name").lowercase().let { "win" in it || "mac" in it }

/** The open widgets, each its own window. In the app's own composition (desktop Main), beside the main window. The
 *  cover's colour is worked out here, once a song, for all of them. */
@Composable
fun DesktopWidgetWindows(icon: Painter) {
    if (!DesktopWidgets.anyOpen) return
    val t = Music.current()
    LaunchedEffect(t?.id) { DesktopWidgets.coverRgb = t?.let { runCatching { Tint.rgbOf(artModel(it, 200)) }.getOrNull() } }
    val look = DesktopWidgets.copied
    val bg = (look?.bg?.copy(alpha = 0xD9 / 255f) ?: backdropOf(DesktopWidgets.coverRgb)).let { if (seeThrough) it else it.copy(alpha = 1f) }
    DesktopWidgetRules.KINDS.forEachIndexed { i, (kind, name) ->
        if (DesktopWidgets.isOpen(kind)) key(kind) { WidgetWindow(kind, name, i, icon, bg) }
    }
}

/** Where a widget goes the first time: down the right of the screen, each a little below and left of the one before.
 *  dpPerAwt: Java's units as dp (Linux: Java counts real pixels, the window's density scales them). */
private fun firstPlace(i: Int, w: Int, dpPerAwt: Float): WindowPosition = runCatching {
    val b = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    WindowPosition(((b.x + b.width) * dpPerAwt - w - 24 - i * 28).dp, (b.y * dpPerAwt + 24 + i * 36).dp)
}.getOrDefault(WindowPosition(Alignment.TopEnd))

@Composable
private fun WidgetWindow(kind: String, name: String, i: Int, icon: Painter, bg: Color) {
    val hypr = org.omarchy.music.Hyprland.on
    val start = remember { DesktopWidgets.place(kind) }
    val title = DesktopWidgets.title(kind)
    // Hyprland: where it goes worked out, and a window rule set for it, before it opens, so it opens straight there (at
    // its size, on its workspace) rather than where Hyprland puts a new window, then jumping
    val rule = "radon-player-widget-$kind"
    var target by remember { mutableStateOf<DesktopWidgetRules.Hypr.Target?>(null) }
    var ready by remember { mutableStateOf(!hypr) }
    if (hypr) LaunchedEffect(Unit) {
        target = withContext(Dispatchers.IO) { runCatching { org.omarchy.music.Hyprland.prepare(rule, title, start, i, DesktopWidgets.awtScale(), focus = false) }.getOrNull() }
        ready = true
    }
    if (!ready) return
    // A place kept elsewhere than here (Hyprland's own, or Java's) isn't used: it opens at its first place
    val x = start.x?.takeIf { start.hypr == hypr }; val y = start.y?.takeIf { start.hypr == hypr }
    val state = rememberWindowState(
        position = if (x != null && y != null && !hypr) WindowPosition(x.dp, y.dp) else WindowPosition(Alignment.TopEnd),
        size = DpSize(start.w.dp, start.h.dp))
    val onTop = DesktopWidgets.place(kind).onTop
    Window(onCloseRequest = { DesktopWidgets.open(kind, false) }, state = state, title = title, icon = icon,
        undecorated = true, transparent = seeThrough, resizable = true, alwaysOnTop = onTop) {
        // Hyprland: once it's on the screen, anything the rule didn't do is put right (nothing is kept before that: the
        // size and place Hyprland gives a new window aren't the widget's)
        var placed by remember { mutableStateOf(!hypr) }
        var placedSize by remember { mutableStateOf<DpSize?>(null) }
        // dp to the screen's real pixels: the window's own density (Java itself draws at 1 here and leaves the scaling to it)
        val density = androidx.compose.ui.platform.LocalDensity.current.density.toDouble()
        val dpPerAwt = ((window.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0) / density).toFloat()
        // Elsewhere, the first time: down the right of the screen (worked out once the window's density is known)
        if (!hypr && (x == null || y == null)) LaunchedEffect(Unit) { state.position = firstPlace(i, start.w, dpPerAwt) }
        if (hypr) LaunchedEffect(Unit) {
            val t = target
            withContext(Dispatchers.IO) { if (t != null) org.omarchy.music.Hyprland.settle(rule, title, t, onTop) }
            delay(300)
            placed = true
        }
        if (hypr) LaunchedEffect(onTop, placed) { if (placed) withContext(Dispatchers.IO) { org.omarchy.music.Hyprland.pin(title, onTop) } }
        // Windows: a widget is a tool window, so the taskbar and Alt+Tab show only Radon Player itself (each widget had a
        // button of its own there, and it was hard to tell which was the player)
        LaunchedEffect(Unit) { delay(150); winToolWindow(window) }
        // Put widgets back: back to its saved place now
        val putBack = DesktopWidgets.putBack
        LaunchedEffect(putBack) {
            if (putBack == 0) return@LaunchedEffect
            val p = DesktopWidgets.place(kind)
            if (hypr) withContext(Dispatchers.IO) { org.omarchy.music.Hyprland.moveTo(title, p, i, density, p.onTop) }
            else if (!p.hypr) {
                val px = p.x; val py = p.y
                if (px != null && py != null) state.position = WindowPosition(px.dp, py.dp)
                state.size = DpSize(p.w.dp, p.h.dp); placedSize = state.size
            }
        }
        // Kept where it's put and as big as it's made, half a second after it stops moving: on Hyprland exactly as
        // Hyprland has it (its place, its size in its own pixels, its workspace and monitor)
        LaunchedEffect(state.position, state.size, placed) {
            if (!placed) return@LaunchedEffect
            delay(500)
            val p = DesktopWidgets.place(kind)
            val w = state.size.width.value.roundToInt(); val h = state.size.height.value.roundToInt()
            if (hypr) {
                val kept = withContext(Dispatchers.IO) { org.omarchy.music.Hyprland.kept(title, p.copy(w = w, h = h)) }
                if (kept != null) DesktopWidgets.save(kind, DesktopWidgets.place(kind).let { now -> kept.copy(open = now.open, onTop = now.onTop, choice = now.choice, shuffled = now.shuffled) })
            } else {
                // (a size within a dp or two of the one asked for is the one asked for)
                val asked = placedSize
                val same = asked != null && kotlin.math.abs((state.size.width - asked.width).value) <= 2 && kotlin.math.abs((state.size.height - asked.height).value) <= 2
                val pos = state.position
                DesktopWidgets.save(kind, p.copy(x = if (pos.isSpecified) pos.x.value.roundToInt() else p.x,
                    y = if (pos.isSpecified) pos.y.value.roundToInt() else p.y, w = if (same) p.w else w, h = if (same) p.h else h, hypr = false))
            }
        }
        // Its corner sizes it: by where the mouse is on the screen (the corner moves with the window as it grows), the
        // mouse's moves in Java's units made dp (dpPerAwt: on Linux Java counts real pixels, twice the dp at a scale of 2)
        val grip = Modifier.pointerInput(Unit) {
            var from: java.awt.Point? = null
            var size0 = state.size
            var moved = Offset.Zero
            detectDragGestures(onDragStart = { from = java.awt.MouseInfo.getPointerInfo()?.location; size0 = state.size; moved = Offset.Zero }) { change, drag ->
                change.consume()
                moved += drag / this.density
                val now = java.awt.MouseInfo.getPointerInfo()?.location
                val f = from
                val dx = if (now != null && f != null) (now.x - f.x) * dpPerAwt else moved.x
                val dy = if (now != null && f != null) (now.y - f.y) * dpPerAwt else moved.y
                state.size = DpSize((size0.width + dx.dp).coerceAtLeast(DesktopWidgetRules.MIN_W.dp),
                    (size0.height + dy.dp).coerceAtLeast(DesktopWidgetRules.MIN_H.dp))
            }
        }
        UiSized { OmarchyTheme {
            WidgetCard(kind, bg, rounded = seeThrough, drag = { content -> WindowDraggableArea(Modifier.fillMaxSize()) { content() } }, grip = grip)
        } }
    }
}

/** The widgets' background: the cover's main colour, deep enough for white text, a little see-through (the phone's
 *  widget's; opaque where the window can't be see-through). Dark grey while nothing plays. */
fun backdropOf(rgb: Int?): Color {
    val hsl = FloatArray(3)
    Rgb.toHsl(rgb ?: Rgb.rgb(40, 40, 46), hsl)
    hsl[2] = hsl[2].coerceIn(0.18f, 0.42f)
    return Color(Rgb.fromHsl(hsl)).copy(alpha = 0xD9 / 255f)
}

/** The widgets' writing and icons: the look's writing with Widgets in the app's look on (dark on a light look), else white. */
private val W: Color get() = DesktopWidgets.copied?.fg ?: Color.White
/** What's written on [W] (a main button's word, a switch's knob): the background's colour. */
private val INK: Color get() = DesktopWidgets.copied?.bg?.copy(alpha = 1f) ?: Color(0xFF16161A)
private val wTitle: TextStyle @Composable get() = T.body.copy(color = W, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
private val wSub: TextStyle @Composable get() = T.sub.copy(color = W.copy(alpha = 0.8f))
private val wSection: TextStyle @Composable get() = T.section.copy(color = W.copy(alpha = 0.75f))

/** One widget as it's drawn: its body on the backdrop (rounded, or filling the window), ⚙ (its options) and ✕ at the
 *  top right, the sizing corner at the bottom right. drag: what makes the body move the window (nothing in a picture). */
@Composable
fun WidgetCard(kind: String, bg: Color, rounded: Boolean = true, drag: @Composable (@Composable () -> Unit) -> Unit = { it() }, grip: Modifier = Modifier) {
    var options by remember { mutableStateOf(false) }
    // In the app's look: its gradient over the background, when it has one (as see-through as the background)
    val grad = DesktopWidgets.copied?.gradient?.map { it.copy(alpha = bg.alpha) }
    Box(Modifier.fillMaxSize().then(if (rounded) Modifier.clip(RoundedCornerShape(16.dp)) else Modifier)
        .then(if (grad != null) Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(grad)) else Modifier.background(bg))) {
        drag {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                if (options) WidgetOptions(kind) { options = false } else when (kind) {
                    "queue" -> NowAndQueue()
                    "playlists" -> NowAndPlaylists()
                    "dj" -> DjWidget()
                    "playlist" -> PlaylistWidget()
                    "pinned" -> PinnedWidget()
                    "top" -> TopWidget()
                    "wrapped" -> WrappedWidget()
                    "lyrics" -> LyricsWidget()
                    "releases" -> ReleasesWidget()
                    "visualizer" -> VisualizerWidget()
                    "shortcuts" -> ShortcutsWidget()
                }
            }
        }
        // The widgets' own line (DesktopWidgets.say): for 8 seconds, over the foot of the widget
        DesktopWidgets.note?.let { (text, at) ->
            val fresh = System.currentTimeMillis() - at < 8000
            var shown by remember(at) { mutableStateOf(fresh) }
            LaunchedEffect(at) { delay(8000 - (System.currentTimeMillis() - at).coerceIn(0, 8000)); shown = false }
            if (shown) Text(text, style = T.sub.copy(color = Color.White, fontSize = 12.sp), maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 8.dp, end = 22.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 10.dp, vertical = 6.dp))
        }
        Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
            // Full screen: the widget's own place in the app, in front (Lyrics: Now playing's lyrics of the song playing)
            IconButton(Ico.FULLSCREEN, W.copy(alpha = 0.7f), 14.dp, 6.dp, desc = "Full screen in the app") { fullScreen(kind) }
            IconButton(Ico.SETTINGS, W.copy(alpha = if (options) 1f else 0.7f), 14.dp, 6.dp, desc = "Widget options") { options = !options }
            IconButton(Ico.CLOSE, W.copy(alpha = 0.7f), 14.dp, 6.dp, desc = "Close the widget") { DesktopWidgets.open(kind, false) }
        }
        Canvas(grip.align(Alignment.BottomEnd).size(18.dp).padding(5.dp)) {   // the sizing corner: three short lines
            for (k in 1..3) {
                val o = size.width * k / 3f
                drawLine(W.copy(alpha = 0.45f), Offset(size.width, size.height - o), Offset(size.width - o, size.height), 1.2f * density)
            }
        }
    }
}

// ---- the parts the widgets share ----

/** A button on a widget: white on the colour (strong: the main one, solid white). */
@Composable
private fun WPill(text: String, strong: Boolean = false, onClick: () -> Unit) {
    Text(text, style = T.tab.copy(color = if (strong) INK else W, fontWeight = FontWeight.SemiBold), maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (strong) W.copy(alpha = 0.92f) else W.copy(alpha = 0.16f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp))
}

/** One of two or more choices on a widget: filled when it's the one chosen. */
@Composable
private fun WChip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(text, style = T.tab.copy(color = if (on) W else W.copy(alpha = 0.7f), fontWeight = if (on) FontWeight.Bold else FontWeight.Normal), maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) W.copy(alpha = 0.28f) else Color.Transparent)
            .border(1.dp, W.copy(alpha = if (on) 0f else 0.3f), RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp))
}

/** A widget's first line: its name, clear of ⚙ and ✕; a click opens it in the app, when it can. */
@Composable
private fun WHeader(text: String, open: (() -> Unit)? = null) {
    Text(text.uppercase(), style = wSection, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(end = 84.dp, bottom = 6.dp).then(if (open != null) Modifier.clickable(onClick = open) else Modifier))
}

/** A song or a playlist in a widget's list: its cover, its name and what's under it. parts: what's under it as pieces,
 *  each one a click away when it has somewhere to go (the artist's page, the album's, the release's), in place of sub;
 *  onRight: a right-click (the song's options); picked: lit, as picked songs are in the app. */
@Composable
private fun WRow(art: Any?, title: String, sub: String, lead: String = "", parts: List<Pair<String, (() -> Unit)?>>? = null,
                 picked: Boolean = false, onRight: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (picked) W.copy(alpha = 0.2f) else Color.Transparent)
        .then(if (onRight != null) Modifier.rightClick(onRight) else Modifier)
        .clickable(onClick = onClick).padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (picked) Icon(Ico.CHECK, W, 14.dp, Modifier.padding(end = 4.dp))
        if (lead.isNotEmpty()) Text(lead, style = wSub.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(22.dp))
        ArtImage(art, 36.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, style = T.body.copy(color = W, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (parts != null) Row(verticalAlignment = Alignment.CenterVertically) {
                parts.filter { it.first.isNotEmpty() }.forEachIndexed { i, (text, go) ->
                    if (i > 0) Text(" · ", style = wSub.copy(fontSize = 12.sp))
                    Text(text, style = wSub.copy(fontSize = 12.sp, textDecoration = if (go != null) androidx.compose.ui.text.style.TextDecoration.Underline else null),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).then(if (go != null) Modifier.clickable(onClick = go) else Modifier))
                }
            } else if (sub.isNotEmpty()) Text(sub, style = wSub.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---- songs in the widgets: their options (right-click), picking several, and going to the artist or the album ----

/** The app's main window, with what's on its screens closed, for something to open there (an artist, an album). */
private fun inApp(block: (Ui) -> Unit) = Instance.inWindow { ui -> ui.sheet = null; block(ui) }

/** An artist's page in the app (each of a song's artists is its own link). */
private fun goArtist(name: String, source: String = "") = inApp { ui -> ui.pages += Ui.Page.Artist(name, source.ifEmpty { null }) }

/** A song's artists and album, as WRow's parts: each one opens its page in the app. */
private fun songParts(t: Track, extra: String = ""): List<Pair<String, (() -> Unit)?>> = buildList {
    if (extra.isNotEmpty()) add(extra to null)
    org.omarchy.music.splitArtists(t.artist).ifEmpty { listOf(t.artist) }.filter { it.isNotBlank() }.forEach { a -> add(a to { goArtist(a, t.source) }) }
    if (t.album.isNotBlank()) add(t.album to { inApp { ui -> goToAlbum(ui, t) } })
}

/** The songs picked in a widget (by their place in its list): ctrl-click picks one more, shift-click all between, and a
 *  right-click on one of them gives the options for all of them. Each widget's own. */
private class WPicks {
    val at = androidx.compose.runtime.mutableStateListOf<Int>()
    var from = -1
    /** A click on row i with the keys held: true if it picked (and the click does nothing else). */
    fun click(i: Int, ctrl: Boolean, shift: Boolean): Boolean {
        when {
            shift && from >= 0 -> { (minOf(from, i)..maxOf(from, i)).forEach { if (it !in at) at += it } }
            ctrl -> { if (i in at) at.remove(i) else at += i; from = i }
            at.isNotEmpty() -> { if (i in at) at.remove(i) else at += i; from = i }   // picking: a click picks or unpicks
            else -> return false
        }
        return true
    }
}

/** The options a right-click opens on a widget's song: the app's own (the same list as in the app), for the songs picked
 *  if it's one of them, else for that one. Those that open something (a dialog, a page) open it in the app's window. */
@Composable
private fun SongMenu(menu: Pair<Track?, List<Track>>?, place: RowPlace, picks: WPicks, close: () -> Unit,
                     queueAt: ((Int) -> Int)? = null, select: (() -> Unit)? = null, extra: List<Pair<String, () -> Unit>> = emptyList()) {
    val (one, picked) = menu ?: return
    val opts = remember(menu) {
        if (picked.isEmpty()) emptyList()   // (a release with no songs to be had: only the list's own, Not my artist…)
        else songOptions(Ui.current ?: Ui(), one, picked, place).filter { it.second != "Select…" &&
            !(one == null && it.second == "Remove from the queue") }   // (the app's own picking: done here instead)
    }
    androidx.compose.material3.DropdownMenu(true, close, containerColor = C.light) {
        // Its name, and ✕ at the top right to close it (a small widget is all list: nowhere else to click)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (one != null) "“${one.title}”" else if (picked.isEmpty()) "This release" else "${picked.size} songs picked", style = T.title.copy(fontSize = 13.sp), maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 14.dp, top = 6.dp, bottom = 6.dp))
            IconButton(Ico.CLOSE, C.muted, 18.dp, 6.dp, desc = "Close") { close() }
        }
        // The list's own (a release's: Not my artist…), first
        extra.forEach { (label, act) -> androidx.compose.material3.DropdownMenuItem({ Text(label, style = T.body) }, { close(); act() },
            leadingIcon = { Icon(Ico.PERSON, C.accent, 18.dp) }) }
        if (one != null) androidx.compose.material3.DropdownMenuItem({ Text("Select…", style = T.body) },
            // Picking starts in the widget itself (ticks on its rows), never in the app's window: this row first
            { close(); if (select != null) select() else { picks.at.clear(); picks.at += place.index; picks.from = place.index } },
            leadingIcon = { Icon(Ico.SELECT, C.accent, 18.dp) })
        // Several picked in the queue: those rows out of it (the app's own option goes by its own picking)
        if (one == null && queueAt != null) androidx.compose.material3.DropdownMenuItem({ Text("Remove from the queue", style = T.body) }, {
            close(); Music.unqueueMany(picks.at.map(queueAt)); picks.at.clear() }, leadingIcon = { Icon(Ico.CLOSE, C.accent, 18.dp) })
        opts.forEach { (ico, label, _) ->
            androidx.compose.material3.DropdownMenuItem({ Text(label, style = T.body.copy(color = if (label.startsWith("Delete")) C.red else C.fg)) }, {
                close()
                // Those that open something open it in the app's window, with its screens; the rest are done here
                val inWindow = label.endsWith("…") || label.startsWith("Go to") || label.startsWith("Remove download") ||
                    label.startsWith("Remove from this playlist")
                if (inWindow) inApp { ui -> songOptions(ui, one, picked, place).firstOrNull { it.second == label }?.third?.invoke() }
                else opts.firstOrNull { it.second == label }?.third?.invoke()
                if (one == null) picks.at.clear()
            }, leadingIcon = { Icon(ico, if (label.startsWith("Delete")) C.red else C.accent, 18.dp) })
        }
    }
}

/** A widget's list of songs: a click plays from there (or picks, with ctrl or shift held, or while some are picked), a
 *  right-click gives the song's options, and its artist and album are links. A bar says how many are picked. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ColumnScope.SongList(songs: List<Track>, kind: String, lead: (Int) -> String = { "" }, queueAt: ((Int) -> Int)? = null,
                                 art: @Composable (Track) -> Any? = { songArt(it) }, play: (Int) -> Unit) {
    val picks = remember(kind) { WPicks() }
    var menu by remember { mutableStateOf<Pair<Track?, List<Track>>?>(null) }
    var menuAt by remember { mutableStateOf(-1) }
    val mods = androidx.compose.ui.platform.LocalWindowInfo.current.keyboardModifiers
    LaunchedEffect(songs) { picks.at.removeAll { it >= songs.size } }
    if (picks.at.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${picks.at.size} picked", style = wSub.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        WPill("Options", strong = true) { menuAt = picks.at.first(); menu = null to picks.at.sorted().mapNotNull { songs.getOrNull(it) } }
        WPill("Clear") { picks.at.clear() }
    }
    LazyColumn(Modifier.weight(1f)) {
        items(songs.size, key = { i -> "$i:${songs[i].id}" }) { i ->
            val s = songs[i]
            Box {
                WRow(art(s), s.title, s.artist, lead = lead(i), parts = songParts(s), picked = i in picks.at,
                    onRight = {
                        menuAt = i
                        menu = if (i in picks.at && picks.at.size > 1) null to picks.at.sorted().mapNotNull { songs.getOrNull(it) } else s to listOf(s)
                    }) {
                    if (!picks.click(i, mods.isCtrlPressed || mods.isMetaPressed, mods.isShiftPressed)) {
                        // A stream takes a moment to start: said at once, so it doesn't look like nothing happened
                        if (!s.isFile && s.file == null && !Music.lib.has(s.id)) DesktopWidgets.say("Loading “${s.title}” (streaming)…")
                        play(i)
                    }
                }
                if (menuAt == i && menu != null)
                    SongMenu(menu, RowPlace(songs, i, kind, queueIndex = queueAt?.invoke(i) ?: -1), picks, { menu = null; menuAt = -1 }, queueAt)
            }
        }
    }
}


/** Now playing, as the phone's widget has it: the cover (a click opens Now playing), the song, then shuffle, previous,
 *  play, next and repeat. */
@Composable
private fun NowPlayingTop() {
    val t = Music.current()
    Row(verticalAlignment = Alignment.CenterVertically) {
        val art = remember(t?.id) { t?.let { artModel(it, 200) } }   // worked out once a song (artModel looks at files)
        Box(Modifier.clickable { Instance.act(listOf("show", "now")) }) { ArtImage(art, 56.dp) }
        Column(Modifier.weight(1f).padding(start = 10.dp, end = 52.dp)) {
            Text(t?.title ?: "Nothing playing", style = wTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t?.let { listOf(it.artist, it.album).filter { s -> s.isNotEmpty() }.joinToString(" — ") } ?: "Radon Player",
                style = wSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(Ico.SHUFFLE, W.copy(alpha = 0.85f), 18.dp, 6.dp) { Music.shuffle() }
        IconButton(Ico.PREV, W, 22.dp, 6.dp) { Music.previous() }
        IconButton(if (Music.paused || t == null) Ico.PLAY else Ico.PAUSE, W, 30.dp, 6.dp) { Music.togglePause() }
        IconButton(Ico.NEXT, W, 22.dp, 6.dp) { Music.next() }
        IconButton(if (Music.repeat == "one") Ico.REPEAT_ONE else Ico.REPEAT, if (Music.repeat == "off") W.copy(alpha = 0.5f) else W, 18.dp, 6.dp) { Music.cycleRepeat() }
    }
    WVolume()
}

/** The volume, as Now playing's: a slim bar to tap or drag, between a quiet and a loud speaker. Every widget with play
 *  and pause has it. It follows the volume however it's changed (Now playing, the keys, another widget). */
@Composable
private fun WVolume(modifier: Modifier = Modifier) {
    var v by remember { mutableStateOf(SysVolume.get()) }
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(500); if (!held) v = SysVolume.get() } }
    fun set(f: Float) { v = f; SysVolume.set(f) }
    Row(modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(Ico.SPEAKER, W.copy(alpha = 0.7f), 14.dp, 4.dp, desc = "Quieter") { set((v - 0.05f).coerceAtLeast(0f)) }
        SlimBar(v, onMove = { held = true; set(it) }, onDone = { held = false; set(it) }, modifier = Modifier.weight(1f).padding(horizontal = 6.dp), bar = W)
        IconButton(Ico.SPEAKER, W, 18.dp, 4.dp, desc = "Louder") { set((v + 0.05f).coerceAtMost(1f)) }
    }
}

private fun playlistSongs(p: Playlist): List<Track> = if (p.smart != null) org.omarchy.music.SmartLists.songs(p).orEmpty() else p.tracks
private fun playlistArt(p: Playlist): Any? = playlistSongs(p).firstOrNull { it.thumb.isNotEmpty() || it.isFile }?.let { artModel(it) }
private fun songCount(n: Int) = "%,d song%s".format(n, if (n != 1) "s" else "")

@Composable
private fun yourPlaylists(): List<Playlist> = remember(Music.musicVersion, org.omarchy.music.SmartLists.version) { Music.lists.all() + Music.lists.smart() }

/** A song's cover, worked out once (artModel looks at files on the disk: not on every redraw). */
@Composable
private fun songArt(t: Track): Any? = remember(t.id, Music.musicVersion) { artModel(t) }

/** A playlist's cover and its number of songs, worked out once for each change of the playlist. */
@Composable
private fun listLook(p: Playlist): Pair<Any?, String> = remember(p.id, p.updated, Music.musicVersion, org.omarchy.music.SmartLists.version) {
    playlistArt(p) to songCount(playlistSongs(p).size)
}

// ---- the widgets ----

/** (1) Now playing and the queue: a song in it plays from there. */
@Composable
private fun ColumnScope.NowAndQueue() {
    NowPlayingTop()
    val upNext = Music.queue.withIndex().drop(Music.index + 1).take(300)
    WHeader("Up next")
    if (upNext.isEmpty()) Text("Nothing queued after this song", style = wSub)
    val songs = remember(upNext) { upNext.map { it.value } }
    SongList(songs, "queue", queueAt = { upNext[it].index }) { Music.playIndex(upNext[it].index) }
}

/** (2) Now playing and your playlists: a playlist plays from the top; its ⤨ shuffled. */
@Composable
private fun ColumnScope.NowAndPlaylists() {
    NowPlayingTop()
    val lists = yourPlaylists()
    WHeader("Your playlists") { Instance.act(listOf("show", "playlists")) }
    if (lists.isEmpty()) Text("No playlists yet: make one in the app", style = wSub)
    LazyColumn(Modifier.weight(1f)) {
        items(lists, key = { it.id }) { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (art, count) = listLook(p)
                Box(Modifier.weight(1f)) { WRow(art, p.name, count) { Music.playList(playlistSongs(p)) } }
                IconButton(Ico.SHUFFLE, W.copy(alpha = 0.8f), 16.dp, 6.dp, desc = "Shuffle ${p.name}") { Music.playList(playlistSongs(p), shuffled = true) }
            }
        }
    }
}

/** (3) The DJ: start or stop it, what it's playing and why, and the songs it picks from: only yours, or new ones too. */
@Composable
private fun ColumnScope.DjWidget() {
    val dj = Music.dj
    val set = dj.sets[dj.shown]
    WHeader("DJ") { Instance.act(listOf("show", "dj")) }
    Text(when {
        Music.djBusy != null -> Music.djBusy!!
        dj.on && set != null -> org.omarchy.music.Dj.kindName(set.kind) ?: set.kind
        dj.on -> "On"
        else -> "Off"
    }, style = wTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (!dj.on && Music.djBusy == null) Text("It plays sets of songs and says why it picked them", style = wSub, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
    if (dj.on && set != null && set.line.isNotEmpty())
        Text("“${set.line}”", style = wSub.copy(fontStyle = FontStyle.Italic), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    Spacer(Modifier.weight(1f))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        WPill(if (dj.on) "Stop" else "Start", strong = true) { if (dj.on) Music.djStop() else Music.djStart() }
        if (dj.on) WPill("Switch it up") { Music.djSwitch() }
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WChip("Only my songs", !Music.djNew) { Music.chooseDjNew(false) }
        WChip("New songs too", Music.djNew) { Music.chooseDjNew(true) }
    }
}

/** (4) A playlist, one click away: In order or Shuffled. Which one: ⚙. */
@Composable
private fun ColumnScope.PlaylistWidget() {
    val place = DesktopWidgets.place("playlist")
    val lists = yourPlaylists()
    val p = lists.firstOrNull { it.id == place.choice }
    WHeader("Playlist shortcut")
    if (p == null) { PlaylistChoice(lists); return }
    val songs = remember(p.id, p.updated, Music.musicVersion, org.omarchy.music.SmartLists.version) { playlistSongs(p) }
    val (art, _) = listLook(p)
    fun play() { if (songs.isEmpty()) Music.toast("“${p.name}” has no songs") else Music.playList(songs, place.shuffled) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clickable { play() }) { ArtImage(art, 64.dp) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(p.name, style = wTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(songCount(songs.size), style = wSub)
        }
    }
    Spacer(Modifier.weight(1f))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        WChip("In order", !place.shuffled) { DesktopWidgets.save("playlist", place.copy(shuffled = false)) }
        WChip("Shuffled", place.shuffled) { DesktopWidgets.save("playlist", place.copy(shuffled = true)) }
        Spacer(Modifier.weight(1f))
        Box(Modifier.clip(RoundedCornerShape(50)).background(W.copy(alpha = 0.92f)).clickable { play() }.padding(8.dp)) {
            Icon(if (place.shuffled) Ico.SHUFFLE else Ico.PLAY, INK, 22.dp)
        }
    }
}

/** Playlist shortcut's choice: your playlists, the one picked marked. */
@Composable
private fun ColumnScope.PlaylistChoice(lists: List<Playlist>, done: () -> Unit = {}) {
    val place = DesktopWidgets.place("playlist")
    if (lists.isEmpty()) { Text("No playlists yet: make one in the app", style = wSub); return }
    Text("Choose the playlist:", style = wSub, modifier = Modifier.padding(bottom = 4.dp))
    LazyColumn(Modifier.weight(1f)) {
        items(lists, key = { it.id }) { p ->
            val (art, count) = listLook(p)
            WRow(art, (if (p.id == place.choice) "✓ " else "") + p.name, count) {
                DesktopWidgets.save("playlist", DesktopWidgets.place("playlist").copy(choice = p.id)); done()
            }
        }
    }
}

/** (5) Pinned: a click opens it in the app, where it lives. */
@Composable
private fun ColumnScope.PinnedWidget() {
    WHeader("Pinned") { Instance.act(listOf("show", "pinned")) }
    if (Music.pins.isEmpty()) Text("Nothing pinned yet: Pin an artist, an album or a playlist on its page", style = wSub)
    LazyColumn(Modifier.weight(1f)) {
        items(Music.pins.toList(), key = { it.key }) { p ->
            WRow(pinArt(p), p.title, listOf(PIN_WORDS[p.kind] ?: "", p.sub).filter { it.isNotEmpty() }.joinToString(" · ")) {
                Instance.inWindow { ui -> ui.sheet = null; openPin(ui, p) }
            }
        }
    }
}
private val PIN_WORDS = mapOf("artist" to "Artist", "album" to "Album", "mine" to "Playlist", "service" to "Playlist", "smart" to "Smart playlist",
    "mood" to "Mood list", "genre" to "Genre", "folder" to "Folder", "listfolder" to "Folder of playlists")

private val TOP_PERIODS = listOf("week" to "This week", "month" to "This month", "year" to "This year", "all" to "All time")

/** (6) Most played: the top songs (a click plays from there); its name opens Most played. The period: ⚙. */
@Composable
private fun ColumnScope.TopWidget() {
    val period = DesktopWidgets.place("top").choice.takeIf { c -> TOP_PERIODS.any { it.first == c } } ?: "all"
    var top by remember { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(Music.musicVersion, period) {
        Music.bg({ Music.mostPlayed(period).take(25) }) { r, _ -> top = r.orEmpty() }
    }
    WHeader("Most played · " + TOP_PERIODS.first { it.first == period }.second) { Instance.act(listOf("show", "top")) }
    // The period, as Most played in the app has it (week, month, year, all time): on the widget, a click away
    Row(Modifier.padding(bottom = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TOP_PERIODS.forEach { (k, name) -> WChip(name.removePrefix("This ").replaceFirstChar { it.uppercase() }, period == k) {
            DesktopWidgets.save("top", DesktopWidgets.place("top").copy(choice = k)) } }
    }
    val songs = top
    when {
        songs == null -> Text("Adding up your plays…", style = wSub)
        songs.isEmpty() -> Text("No plays yet for this time", style = wSub)
    }
    SongList(songs.orEmpty(), "top", lead = { "${it + 1}" }) { i -> Music.playFrom(songs.orEmpty(), i, radio = false) }
}

/** (7) Wrapped: opened in the app for this week, this month, this year or every year. */
@Composable
private fun ColumnScope.WrappedWidget() {
    WHeader("Wrapped")
    Text("Your listening, slide by slide", style = wSub, modifier = Modifier.padding(bottom = 10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WPill("This week", strong = true) { Instance.act(listOf("wrapped", "week")) }
        WPill("This month", strong = true) { Instance.act(listOf("wrapped", "month")) }
    }
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WPill("This year") { Instance.act(listOf("wrapped")) }
        WPill("All time") { Instance.act(listOf("wrapped", "all")) }
    }
}

/** (8) Lyrics: the song's lyrics, the line being sung lit up as it plays (a click on a line goes there), or the words as
 *  written when they aren't timed; Lyrics card and Open in the app. */
@Composable
private fun ColumnScope.LyricsWidget() {
    val t = Music.current()
    var got by remember { mutableStateOf<Lyrics.Got?>(null) }
    var state by remember { mutableStateOf("") }   // "", "loading", "none", "error"
    LaunchedEffect(t?.id) {
        got = null
        if (t == null) return@LaunchedEffect
        state = "loading"
        Music.bg({ Lyrics.get(Music.ctx, t) }) { g, err ->
            if (Music.current()?.id != t.id) return@bg
            got = g; state = if (g != null) "" else if (err != null) "error" else "none"
        }
    }
    WHeader(t?.let { "${it.title} · ${it.artist}" } ?: "Lyrics")
    val g = got
    val lines = remember(g) { Lyrics.parseLrc(g?.synced) }
    Box(Modifier.weight(1f).fillMaxWidth()) {
        when {
            t == null -> Text("The lyrics of the song playing show here", style = wSub)
            state == "loading" -> Text("Finding the lyrics…", style = wSub)
            state == "error" -> Text("Couldn't get the lyrics", style = wSub)
            g == null || (g.instrumental && g.plain == null && lines.isEmpty()) -> Text(if (g?.instrumental == true) "Instrumental: no words in this one" else "No lyrics found", style = wSub)
            lines.isNotEmpty() -> Synced(lines, ink = W, size = 15.sp)
            g.plain != null -> Text(g.plain.lines().filterNot { Regex("\\s*\\[[^]]+]\\s*").matches(it) }.joinToString("\n").trim(),
                style = T.body.copy(color = W, fontSize = 14.sp, lineHeight = 21.sp), modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
            else -> Text("No lyrics found", style = wSub)
        }
    }
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (g != null && (g.plain != null || g.synced != null)) WPill("Lyrics card") { Instance.lyricsCard() }
    }
}

/** (9) New releases from your artists: the newest the app has found (by its daily look, or Refresh… in New releases;
 *  nothing is looked up for the widget itself), from the last week, month or year, all of them or only albums, EPs or
 *  singles (the widget's own choice, kept). A click plays one (found on your services then); a right-click gives its
 *  songs' options (the app's own), ctrl or shift picks several; its artist opens the artist's page and its kind (Single,
 *  EP, Album) the release's own page. The name opens New releases. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ColumnScope.ReleasesWidget() {
    val nr = org.omarchy.music.NewReleases
    val place = DesktopWidgets.place("releases")
    val range = place.choice.substringBefore('|').takeIf { it in nr.RANGES } ?: "month"
    val kind = place.choice.substringAfter('|', "all").takeIf { it in setOf("all", "album", "ep", "single") } ?: "all"
    fun choose(r: String = range, k: String = kind) = DesktopWidgets.save("releases", place.copy(choice = "$r|$k"))
    val today = remember { java.time.LocalDate.now() }
    val hideDeezer = nr.hideDeezer()
    org.omarchy.music.ReleaseLookup.version   // (read: a release found only on Deezer goes as soon as that's known)
    val list = remember(nr.found, nr.told, range, kind, hideDeezer, org.omarchy.music.ReleaseLookup.version) {
        val since = today.minusDays(nr.RANGES[range] ?: 30L)
        DesktopWidgetRules.newest(nr.told + nr.found, { nr.key(it) }, { it.date }, 200)
            .filter { r -> (kind == "all" || r.type == kind) && nr.shown(r) && runCatching { !java.time.LocalDate.parse(r.date.take(10)).isBefore(since) }.getOrDefault(true) }
            .take(60)
    }
    val kinds = mapOf("single" to "Single", "ep" to "EP", "album" to "Album")
    WHeader("New releases") { Instance.act(listOf("show", "new releases")) }
    // From when, and which kind: as the app's New releases has them
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("week" to "Week", "month" to "Month", "year" to "Year").forEach { (k, l) -> WChip(l, range == k) { choose(r = k) } }
        Spacer(Modifier.width(6.dp))
        listOf("all" to "All", "album" to "Albums", "ep" to "EPs", "single" to "Singles").forEach { (k, l) -> WChip(l, kind == k) { choose(k = k) } }
        Spacer(Modifier.width(6.dp))
        // Only what you can play, or the Deezer-only ones too (the same switch as the app's New releases)
        WChip("Hide Deezer-only", hideDeezer) { nr.setHideDeezer(!hideDeezer) }
    }
    // The app last looked back less far than asked: said, as what's shown can't go further back than that
    if ((nr.RANGES[range] ?: 0L) > (nr.RANGES[nr.range] ?: 0L))
        Text("The last Refresh… looked back a ${nr.range}: refresh in New releases for the whole ${range}", style = wSub, maxLines = 2,
            modifier = Modifier.padding(top = 4.dp))
    nr.status?.let { Text(it, style = wSub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
    Spacer(Modifier.height(4.dp))
    if (list.isEmpty()) {
        Text("Nothing new found for this", style = wTitle)
        Text("Open New releases in the app and press Refresh… to look at what your artists put out lately, or switch on " +
            "New releases by themselves in Settings › Playback to have it look once a day.", style = wSub, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.weight(1f))
        Row { WPill("Open New releases", strong = true) { Instance.act(listOf("show", "new releases")) } }
        return
    }
    // A release's page in the app (an album, an EP or a single), as New releases opens it
    fun open(r: org.omarchy.music.NewReleases.Release) {
        DesktopWidgets.say("Opening “${r.title}”…")
        Music.bg({ Music.releaseFind(r) }) { found, _ ->
            if (found == null) { DesktopWidgets.say(org.omarchy.music.ReleaseLookup.whyNot(r)); return@bg }
            val (key, al, songs) = found
            inApp { ui -> ui.pages += if (al != null) Ui.Page.AlbumPage(al, key)
                else Ui.Page.AlbumPage(org.omarchy.music.Album("single-${songs[0].id}", r.title, "${r.artist} · Single", songs[0].thumb.ifEmpty { r.cover }), key, songs) }
        }
    }
    // The first few looked up ahead, quietly, so a click plays at once instead of searching first
    LaunchedEffect(nr.found, nr.told, range, kind, hideDeezer) { withContext(Dispatchers.IO) { runCatching { org.omarchy.music.ReleaseLookup.warm(if (hideDeezer) nr.told + nr.found else list, if (hideDeezer) 400 else 20) } } }
    val picks = remember { WPicks() }
    var menu by remember { mutableStateOf<Pair<Track?, List<Track>>?>(null) }
    var menuAt by remember { mutableStateOf(-1) }
    val mods = androidx.compose.ui.platform.LocalWindowInfo.current.keyboardModifiers
    // A right-click: the songs of the release (or of every release picked) looked up, then their options
    fun options(i: Int) {
        val rs = if (i in picks.at && picks.at.size > 1) picks.at.sorted().mapNotNull { list.getOrNull(it) } else listOf(list[i])
        DesktopWidgets.say("Finding the songs of " + if (rs.size == 1) "“${rs[0].title}”…" else "${rs.size} releases…")
        Music.bg({ rs.flatMap { r -> runCatching { Music.releaseTracks(r) }.getOrNull().orEmpty() } }) { songs, _ ->
            if (songs.isNullOrEmpty()) {
                DesktopWidgets.say(if (rs.size == 1) org.omarchy.music.ReleaseLookup.whyNot(rs[0]) else "None of them could be found on your services")
                // Still its own options (Not my artist…), for a release that can't be played
                if (rs.size == 1 && nr.canSayNotMine(rs[0])) { menuAt = i; menu = null to emptyList() }
                return@bg
            }
            menuAt = i; menu = (if (songs.size == 1) songs[0] else null) to songs
        }
    }
    if (picks.at.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${picks.at.size} picked", style = wSub.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        WPill("Options", strong = true) { options(picks.at.first()) }
        WPill("Clear") { picks.at.clear() }
    }
    LazyColumn(Modifier.weight(1f)) {
        items(list.size, key = { nr.key(list[it]) }) { i ->
            val r = list[i]
            org.omarchy.music.ReleaseLookup.version   // (read: the row says where it plays from as soon as that's known)
            val parts = listOfNotNull<Pair<String, (() -> Unit)?>>(
                r.artist to { goArtist(r.artist) },
                kinds[r.type]?.let { it to { open(r) } },
                DesktopWidgetRules.whenOut(r.date, today).takeIf { it.isNotEmpty() }?.let { it to null },
                // Where it plays from: Tidal first, else YouTube Music, else only on Deezer so far
                org.omarchy.music.ReleaseLookup.whereLabel(r).takeIf { it.isNotEmpty() }?.let { it to null })
            Box {
                WRow(r.cover.ifEmpty { null }, r.title, "", parts = parts, picked = i in picks.at, onRight = { options(i) }) {
                    if (picks.click(i, mods.isCtrlPressed || mods.isMetaPressed, mods.isShiftPressed)) return@WRow
                    DesktopWidgets.say("Finding “${r.title}”…")
                    Music.bg({ Music.releaseTracks(r) }) { songs, _ ->
                        // Can't be played: said, and why (not on your services, Tidal not signed in, no connection)
                        if (songs.isNullOrEmpty()) DesktopWidgets.say(org.omarchy.music.ReleaseLookup.whyNot(r))
                        else { DesktopWidgets.say("Playing “${r.title}”"); Music.playList(songs) }
                    }
                }
                val m = menu
                // (Select… picks this release in the widget's own picking: ticked here, not in the app)
                if (menuAt == i && m != null) SongMenu(m, RowPlace(m.second, 0, "releases"), picks, { menu = null; menuAt = -1 },
                    select = { picks.at.clear(); picks.at += i; picks.from = i },
                    // Someone else of the same name (not the artist you listen to): hidden for good, yours still followed
                    extra = if (picks.at.size <= 1 && nr.canSayNotMine(r)) listOf("Not my ${r.artist} (someone else of that name)" to { nr.notMine(r) }) else emptyList())
            }
        }
    }
}

/** (10) The visualizer: Now playing's visualizer (Settings › Personalization › Widgets: or the Visualizer tab's, or one
 *  of its own), changing with it; in the colours the app draws it in (the song's own when it's the look's or the cover's;
 *  the look's accent in the app's look), or the widget's own colour. It only runs while music plays: paused or with
 *  nothing playing it draws nothing and the sound isn't read (the same reading the app's visualizer uses, at the same
 *  pace). Play/pause and next show while the mouse is over it, and while it's paused. */
@Composable
private fun ColumnScope.VisualizerWidget() {
    val t = Music.current()
    val playing = t != null && !Music.paused
    val s = rememberVis(playing)
    val rgb = DesktopWidgets.coverRgb
    val base = DesktopWidgets.copied?.accent
        ?: rgb?.let { androidx.compose.ui.graphics.lerp(Color(org.omarchy.music.CoverColourRules.shown(it)), W, 0.3f) } ?: W.copy(alpha = 0.9f)
    val hover = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    WHeader(t?.let { "${it.title} · ${it.artist}" } ?: "Visualizer")
    Box(Modifier.weight(1f).fillMaxWidth().hoverable(hover)) {
        if (s != null) {
            val fg = W
            Canvas(Modifier.fillMaxSize()) {
                if (s.frame < 0) return@Canvas   // read, so it's drawn again each frame it moves
                drawWidgetVis(s, base, fg)
            }
        } else Text(if (t == null) "Nothing playing" else "Paused", style = wSub, modifier = Modifier.align(Alignment.Center))
        if (hovered || !playing) Row(Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp).clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.25f)).widthIn(max = 360.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(if (playing) Ico.PAUSE else Ico.PLAY, W, 22.dp, 6.dp) { Music.togglePause() }
            IconButton(Ico.NEXT, W, 20.dp, 6.dp) { Music.next() }
            WVolume(Modifier.weight(1f))   // the volume, with play/pause, as on every widget that has them
        }
    }
}

/** (11) Shortcuts: the one-click shortcuts you made (Settings › General › Make desktop shortcuts…), as tiles with their
 *  pictures and plain names, as many across as fit (it reflows as it's sized); a click does it. Where the desktop shows
 *  no icons (Hyprland and the like), this is how they're on the screen. */
@Composable
private fun ColumnScope.ShortcutsWidget() {
    val kept = Prefs.text(org.omarchy.music.Shortcuts.LIST)
    val list = remember(kept) { org.omarchy.music.Shortcuts.made() }
    WHeader("Shortcuts")
    if (list.isEmpty()) {
        Text("No shortcuts yet", style = wTitle)
        Text("Make them in Settings › General: Make desktop shortcuts…, and they show here as buttons, one click each.",
            style = wSub, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.weight(1f))
        Row { WPill("Make shortcuts…", strong = true) { Instance.inWindow { it.openSettings("General") } } }
        return
    }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Adaptive(78.dp), Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(list.size, key = { list[it].key }) { i -> ShortcutTile(list[i]) }
    }
}

/** One shortcut: its picture (drawn once and kept, see [ShortcutIcons]) and its name under it. */
@Composable
private fun ShortcutTile(s: DesktopWidgetRules.Shortcut) {
    var img by remember(s.key) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(s.key) {
        img = withContext(Dispatchers.IO) {
            runCatching { ShortcutIcons.png(s)?.readBytes()?.let { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() } }.getOrNull()
        }
    }
    Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { Instance.act(s.args) }.padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            img?.let { androidx.compose.foundation.Image(it, s.name, Modifier.size(48.dp)) } ?: Icon(Ico.NOTE, W.copy(alpha = 0.6f), 24.dp)
        }
        Text(s.name, style = T.sub.copy(color = W, fontSize = 11.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
    }
}

/** A widget's options (⚙): above other windows or not, its own choices (Playlist shortcut's playlist, Most played's
 *  period), and the app. ✕ closes it. */
@Composable
private fun ColumnScope.WidgetOptions(kind: String, done: () -> Unit) {
    val place = DesktopWidgets.place(kind)
    WHeader(DesktopWidgetRules.KINDS.firstOrNull { it.first == kind }?.second ?: "Widget")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Always on top", style = wTitle.copy(fontSize = 14.sp))
            Text(if (place.onTop) "Stays above your other windows" else "Sits with your other windows", style = wSub.copy(fontSize = 12.sp))
        }
        Switch(place.onTop, { DesktopWidgets.save(kind, place.copy(onTop = it)) }, colors = SwitchDefaults.colors(checkedThumbColor = INK,
            checkedTrackColor = W.copy(alpha = 0.9f), uncheckedThumbColor = W.copy(alpha = 0.8f), uncheckedTrackColor = W.copy(alpha = 0.15f),
            uncheckedBorderColor = W.copy(alpha = 0.4f)))
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("In the app's look", style = wTitle.copy(fontSize = 14.sp))
            Text(if (WidgetLook.copyLook()) "Every widget wears the app's look (no effects)" else "In the cover's colour", style = wSub.copy(fontSize = 12.sp))
        }
        Switch(WidgetLook.copyLook(), { Prefs.set(WidgetLook.LOOK, it) }, colors = SwitchDefaults.colors(checkedThumbColor = INK,
            checkedTrackColor = W.copy(alpha = 0.9f), uncheckedThumbColor = W.copy(alpha = 0.8f), uncheckedTrackColor = W.copy(alpha = 0.15f),
            uncheckedBorderColor = W.copy(alpha = 0.4f)))
    }
    Spacer(Modifier.height(8.dp))
    when (kind) {
        "playlist" -> PlaylistChoice(yourPlaylists(), done)
        "visualizer" -> {
            val choice = WidgetLook.VIS_CHOICES.firstOrNull { it.first == Prefs.text(WidgetLook.VIS) }?.second ?: "Same as Now playing"
            Text("Shows: $choice", style = wSub)
            Row(Modifier.padding(top = 6.dp)) { WPill("Change it…") { Instance.inWindow { it.openSettings("Personalization") } } }
        }
        "top" -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TOP_PERIODS.forEach { (k, name) -> WChip(name.removePrefix("This ").replaceFirstChar { it.uppercase() }, (place.choice.ifEmpty { "all" }) == k) { DesktopWidgets.save(kind, place.copy(choice = k)) } }
        }
    }
    if (kind != "playlist") Spacer(Modifier.weight(1f))
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WPill("Done", strong = true) { done() }
        WPill("Open the app") { Instance.window.show() }
    }
}
