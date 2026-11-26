package org.omarchy.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 *  What a widget does in the main window goes through the same commands a second launch sends ([Instance]). */
object DesktopWidgets {
    private const val PREF = "pc_widget."   // pc_: kept on this PC, never synced to the phone
    private const val TOP_OFF = "pc_widget_top_off"   // Always on top switched off once for all (it was on by itself)
    private val places by lazy {
        val firstTime = !Prefs.on(TOP_OFF)
        mutableStateMapOf<String, DesktopWidgetRules.Place>().apply {
            for ((k, _) in DesktopWidgetRules.KINDS) {
                val p = DesktopWidgetRules.decode(k, Prefs.text(PREF + k))
                put(k, if (firstTime && p.onTop) p.copy(onTop = false).also { Prefs.setText(PREF + k, DesktopWidgetRules.encode(it)) } else p)
            }
            if (firstTime) Prefs.set(TOP_OFF, true)
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

    /** The cover playing's main colour (null: none), worked out once a song for every widget (the host keeps it). */
    var coverRgb by mutableStateOf<Int?>(null)
        internal set
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
    val bg = backdropOf(DesktopWidgets.coverRgb).let { if (seeThrough) it else it.copy(alpha = 1f) }
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
    // A place kept elsewhere than here (Hyprland's own, or Java's) isn't used: it opens at its first place
    val x = start.x?.takeIf { start.hypr == hypr }; val y = start.y?.takeIf { start.hypr == hypr }
    val state = rememberWindowState(
        position = if (x != null && y != null && !hypr) WindowPosition(x.dp, y.dp) else WindowPosition(Alignment.TopEnd),
        size = DpSize(start.w.dp, start.h.dp))
    val title = "Radon Player · $name"
    val onTop = DesktopWidgets.place(kind).onTop
    Window(onCloseRequest = { DesktopWidgets.open(kind, false) }, state = state, title = title, icon = icon,
        undecorated = true, transparent = seeThrough, resizable = true, alwaysOnTop = onTop) {
        // Hyprland: put where it was kept, at its size, once it's on the screen (nothing is kept before that: the size
        // and place Hyprland gives a new window aren't the widget's)
        var placed by remember { mutableStateOf(!hypr) }
        var placedSize by remember { mutableStateOf<DpSize?>(null) }
        // dp to the screen's real pixels: the window's own density (Java itself draws at 1 here and leaves the scaling to it)
        val density = androidx.compose.ui.platform.LocalDensity.current.density.toDouble()
        val dpPerAwt = ((window.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0) / density).toFloat()
        // Elsewhere, the first time: down the right of the screen (worked out once the window's density is known)
        if (!hypr && (x == null || y == null)) LaunchedEffect(Unit) { state.position = firstPlace(i, start.w, dpPerAwt) }
        if (hypr) LaunchedEffect(Unit) {
            val scale = density
            withContext(Dispatchers.IO) { org.omarchy.music.Hyprland.place(title, x, y, start.w, start.h, i, scale, onTop) }
            delay(400)
            placedSize = state.size; placed = true
        }
        if (hypr) LaunchedEffect(onTop, placed) { if (placed) withContext(Dispatchers.IO) { org.omarchy.music.Hyprland.pin(title, onTop) } }
        // Kept where it's put and as big as it's made, half a second after it stops moving
        LaunchedEffect(state.position, state.size, placed) {
            if (!placed) return@LaunchedEffect
            delay(500)
            val p = DesktopWidgets.place(kind)
            // (a size within a dp or two of the one asked for is the one asked for: Hyprland counts in its own pixels)
            val asked = placedSize
            val same = asked != null && kotlin.math.abs((state.size.width - asked.width).value) <= 2 && kotlin.math.abs((state.size.height - asked.height).value) <= 2
            val w = if (same) p.w else state.size.width.value.roundToInt()
            val h = if (same) p.h else state.size.height.value.roundToInt()
            if (hypr) {
                val at = withContext(Dispatchers.IO) { org.omarchy.music.Hyprland.where(title) }
                DesktopWidgets.save(kind, DesktopWidgets.place(kind).copy(x = at?.first ?: p.x, y = at?.second ?: p.y, w = w, h = h, hypr = true))
            } else {
                val pos = state.position
                DesktopWidgets.save(kind, p.copy(x = if (pos.isSpecified) pos.x.value.roundToInt() else p.x,
                    y = if (pos.isSpecified) pos.y.value.roundToInt() else p.y, w = w, h = h, hypr = false))
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

private val W = Color.White
private val wTitle: TextStyle @Composable get() = T.body.copy(color = W, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
private val wSub: TextStyle @Composable get() = T.sub.copy(color = W.copy(alpha = 0.8f))
private val wSection: TextStyle @Composable get() = T.section.copy(color = W.copy(alpha = 0.75f))

/** One widget as it's drawn: its body on the backdrop (rounded, or filling the window), ⚙ (its options) and ✕ at the
 *  top right, the sizing corner at the bottom right. drag: what makes the body move the window (nothing in a picture). */
@Composable
fun WidgetCard(kind: String, bg: Color, rounded: Boolean = true, drag: @Composable (@Composable () -> Unit) -> Unit = { it() }, grip: Modifier = Modifier) {
    var options by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().then(if (rounded) Modifier.clip(RoundedCornerShape(16.dp)) else Modifier).background(bg)) {
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
        Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
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
    Text(text, style = T.tab.copy(color = if (strong) Color(0xFF16161A) else W, fontWeight = FontWeight.SemiBold), maxLines = 1,
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
        modifier = Modifier.padding(end = 56.dp, bottom = 6.dp).then(if (open != null) Modifier.clickable(onClick = open) else Modifier))
}

/** A song or a playlist in a widget's list: its cover, its name and what's under it. */
@Composable
private fun WRow(art: Any?, title: String, sub: String, lead: String = "", onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (lead.isNotEmpty()) Text(lead, style = wSub.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(22.dp))
        ArtImage(art, 36.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, style = T.body.copy(color = W, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, style = wSub.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    LazyColumn(Modifier.weight(1f)) {
        items(upNext, key = { it.index }) { (i, s) -> WRow(songArt(s), s.title, s.artist) { Music.playIndex(i) } }
    }
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
            Icon(if (place.shuffled) Ico.SHUFFLE else Ico.PLAY, Color(0xFF16161A), 22.dp)
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
    val songs = top
    when {
        songs == null -> Text("Adding up your plays…", style = wSub)
        songs.isEmpty() -> Text("No plays yet for this time", style = wSub)
    }
    LazyColumn(Modifier.weight(1f)) {
        itemsIndexed(songs.orEmpty()) { i, s -> WRow(songArt(s), s.title, s.artist, lead = "${i + 1}") { Music.playFrom(songs.orEmpty(), i, radio = false) } }
    }
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
        WPill("Open in the app") { Instance.act(listOf("lyrics")) }
    }
}

/** (9) New releases from your artists: the newest the app has found (by its daily look, or Refresh… in New releases;
 *  nothing is looked up for the widget itself). A click plays one (found on your services then); the name opens New
 *  releases. */
@Composable
private fun ColumnScope.ReleasesWidget() {
    val nr = org.omarchy.music.NewReleases
    val list = remember(nr.found, nr.told) {
        DesktopWidgetRules.newest(nr.told + nr.found, { nr.key(it) }, { it.date }, 30)
    }
    val today = remember { java.time.LocalDate.now() }
    val kinds = mapOf("single" to "Single", "ep" to "EP")
    WHeader("New releases") { Instance.act(listOf("show", "new releases")) }
    nr.status?.let { Text(it, style = wSub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 4.dp)) }
    if (list.isEmpty()) {
        Text("Nothing new found yet", style = wTitle)
        Text("Open New releases in the app and press Refresh… to look at what your artists put out lately, or switch on " +
            "New releases by themselves in Settings › Playback to have it look once a day.", style = wSub, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.weight(1f))
        Row { WPill("Open New releases", strong = true) { Instance.act(listOf("show", "new releases")) } }
        return
    }
    LazyColumn(Modifier.weight(1f)) {
        items(list, key = { nr.key(it) }) { r ->
            val sub = listOfNotNull(r.artist, kinds[r.type], DesktopWidgetRules.whenOut(r.date, today).takeIf { it.isNotEmpty() }).joinToString(" · ")
            WRow(r.cover.ifEmpty { null }, r.title, sub) {
                Music.toast("Finding “${r.title}”…")
                Music.bg({ Music.releaseTracks(r) }) { songs, err ->
                    if (songs.isNullOrEmpty()) Music.toast("Couldn't find “${r.title}” on your services" + (err?.message?.let { ": ${it.take(80)}" } ?: ""))
                    else Music.playList(songs)
                }
            }
        }
    }
}

/** (10) The visualizer: the style picked in the app's Visualizer, in the colours picked there (the song's own when it's
 *  the look's or the cover's), on the song's colour. It only runs while music plays: paused or with nothing playing it
 *  draws nothing and the sound isn't read (the same reading the app's visualizer uses, at the same pace). Play/pause
 *  and next show while the mouse is over it, and while it's paused. */
@Composable
private fun ColumnScope.VisualizerWidget() {
    val t = Music.current()
    val playing = t != null && !Music.paused
    val s = rememberVis(playing)
    val rgb = DesktopWidgets.coverRgb
    val base = rgb?.let { androidx.compose.ui.graphics.lerp(Color(org.omarchy.music.CoverColourRules.shown(it)), W, 0.3f) } ?: W.copy(alpha = 0.9f)
    val hover = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    WHeader(t?.let { "${it.title} · ${it.artist}" } ?: "Visualizer")
    Box(Modifier.weight(1f).fillMaxWidth().hoverable(hover)) {
        if (s != null) {
            val style = Vis.style()
            val key = Vis.color().let { if (it == "auto" || it == "cover") "auto" else it }
            Canvas(Modifier.fillMaxSize()) {
                if (s.frame < 0) return@Canvas   // read, so it's drawn again each frame it moves
                clipRect { drawVis(style, s, VisPalette.of(style, key, base, s.phase), W, backdrop = true) }
            }
        } else Text(if (t == null) "Nothing playing" else "Paused", style = wSub, modifier = Modifier.align(Alignment.Center))
        if (hovered || !playing) Row(Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.25f)),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(if (playing) Ico.PAUSE else Ico.PLAY, W, 22.dp, 6.dp) { Music.togglePause() }
            IconButton(Ico.NEXT, W, 20.dp, 6.dp) { Music.next() }
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
        Switch(place.onTop, { DesktopWidgets.save(kind, place.copy(onTop = it)) }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF16161A),
            checkedTrackColor = W.copy(alpha = 0.9f), uncheckedThumbColor = W.copy(alpha = 0.8f), uncheckedTrackColor = W.copy(alpha = 0.15f),
            uncheckedBorderColor = W.copy(alpha = 0.4f)))
    }
    Spacer(Modifier.height(8.dp))
    when (kind) {
        "playlist" -> PlaylistChoice(yourPlaylists(), done)
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
