package org.omarchy.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.omarchy.music.Music
import org.omarchy.music.Prefs
import org.omarchy.music.Remote
import org.omarchy.music.Spectrum
import org.omarchy.music.Track
import org.omarchy.music.fmtTime
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// Now playing, Apple Music's way: the window in the cover's main colour (lighter at the top, deeper at
// the bottom) with the cover itself faintly in it, slightly blurred, and bars rising and falling with the
// music along the bottom in a light shade of the same colour. The cover big, the song under it with ☆ and ⋯,
// a thin bar to seek on, the controls, the volume, and a pill of Lyrics, the output (its EQ) and the queue.
// The queue takes the cover's place: Shuffle, Repeat and Autoplay, the songs you queued, then the songs it
// plays on with (drag ≡ to move one), then your other queues.

/** The Now playing look: this screen in the cover's colours, the cover faintly blurred behind. Any other look keeps
 *  its own background and colours here (no blurred cover, no colours from the cover). */
private val coverLook: Boolean get() = Music.look == Looks.NOW_PLAYING
/** White text: in the cover's colours, or on the plain black background; else the look's own text colour. */
private val whiteText: Boolean get() = coverLook || Vis.plainNow()
private val WHITE: Color get() = if (whiteText) Color.White else C.fg
private val SOFT: Color get() = if (whiteText) Color.White.copy(alpha = 0.62f) else C.muted
private val GLASS: Color get() = if (whiteText) Color.White.copy(alpha = 0.14f) else C.fg.copy(alpha = 0.08f)
/** An icon on a button that's on (filled with WHITE): dark, or light where the look's text is dark. */
private val ON_WHITE: Color get() = if (whiteText || !C.isLight) Color(0xFF222222) else C.light

/** The backdrop's colours from a cover: the gradient's top and bottom, and the bars' light shade. */
class NowColors(val top: Color, val bottom: Color, val bars: Color)

private val GREY = NowColors(Color(0xFF4D4D55), Color(0xFF1A1A1E), Color(0xFFDADAE0))

private fun nowColors(rgb: Int): NowColors {
    val hsl = FloatArray(3)
    Rgb.toHsl(rgb, hsl)
    val s = if (hsl[1] > 0.08f) hsl[1].coerceIn(0.25f, 0.75f) else hsl[1]   // a grey cover stays grey
    fun shade(l: Float, sat: Float = s) = Color(Rgb.fromHsl(floatArrayOf(hsl[0], sat, l)))
    return NowColors(shade(0.46f), shade(0.20f), shade(0.80f, min(1f, s + 0.1f)))
}

private suspend fun loadNowColors(model: Any?): NowColors? = Tint.rgbOf(model)?.let { nowColors(it) }

/** lock: on the lock screen (LockScreenActivity): no menu or settings at the top, an arrow down to the phone's own
 *  lock screen instead, which lock() goes to. */
@Composable
fun ApplePlayer(ui: Ui, lock: (() -> Unit)? = null) {
    val n = now()
    val t = n.t
    val art = t?.let { if (n.remote) Remote.artModel(it) else artModel(it, 1200) }
    var cols by remember { mutableStateOf(GREY) }
    LaunchedEffect(t?.id, Music.savedVersion) { cols = loadNowColors(art) ?: GREY }
    // The bars behind Now playing hear the music through Android's visualizer, which needs the
    // microphone permission (nothing is recorded): asked once, the first time Now playing shows.
    var micOk by remember { mutableStateOf(hasPermission(Perm.MIC)) }
    val askMic = rememberPermission { micOk = it }
    LaunchedEffect(Unit) { if (!micOk && !Music.micAsked) { Music.micAsked = true; Music.saveState(); askMic.launch(Perm.MIC) } }
    // Settings › Look: the background (the cover's colours and the cover blurred, or plain black) and the visualizer behind.
    val plain = Vis.plainNow()
    val cover = coverLook   // only the Now playing look takes the cover's colours and blurs it behind
    val visStyle = Vis.nowStyle()
    val visPane = ui.pane == "nowvis"   // the visualizer's settings in the cover's place: it runs there for the previews
    val vis = if (micOk && (visStyle != "off" || visPane)) rememberVis(on = true) else null
    val visCol = if (cover || plain) cols.bars else C.accent
    val strength = nowStrength
    // The styles around a middle (Ring, Pulse…) go over the big cover while it shows, else they'd hide behind it.
    val coverShown = ui.pane != "queue" && ui.pane != "lyrics" && !visPane
    val onCover = vis != null && visStyle != "off" && coverShown && visStyle in Vis.OVER_COVER
    var volumeShown by remember { mutableStateOf(false) }   // the volume bar shows only after a tap on the speaker
    Box(Modifier.fillMaxSize().then(when {
        plain -> Modifier.background(Color.Black)
        cover -> Modifier.background(Brush.verticalGradient(listOf(cols.top, cols.bottom)))
        // Another look: its own background, which the app's window already draws (with its stars); the lock screen has
        // no window behind, so it's drawn here.
        lock == null -> Modifier
        else -> Modifier.background(C.bg).then(C.gradient?.let { Modifier.background(Brush.linearGradient(it)) } ?: Modifier)
    })) {
        // The cover in the colour, faintly and only a little blurred, so it's still clearly the cover: a mid-size copy
        // softened with a light blur (a tiny copy drawn big came out blocky on the PC).
        if (art != null && !plain && cover) AsyncImage(model = smallArt(art, 128), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(8.dp).graphicsLayer(alpha = 0.32f))
        if (vis != null && visStyle != "off" && !onCover) NowVis(visStyle, vis, visCol, strength, Modifier.align(Alignment.BottomCenter))
        if (!plain && cover) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (lock != null) LockTop(n, lock) else NowTop(ui, n)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (ui.pane) {
                    "queue" -> QueuePane(ui, n)
                    // The lyrics are in the look's colours: on a light look's card, so they read on any background
                    "lyrics" -> Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).clip(R.big)
                        .background(when {
                            C.isLight -> C.light.copy(alpha = 0.94f)
                            plain -> WHITE.copy(alpha = 0.07f)
                            !cover -> C.light.copy(alpha = 0.6f)
                            else -> Color.Black.copy(alpha = 0.25f)
                        })) { LyricsTab(ui) }
                    "nowvis" -> NowVisPane(ui, vis, visCol, micOk, { askMic.launch(Perm.MIC) },
                        if (lock == null) ({ ui.pane = "art"; goTo(ui, "visualizer") }) else null)   // the lock screen has no Visualizer tab
                    else -> Column(Modifier.fillMaxSize()) {
                        BigCover(ui, art, t != null, Modifier.weight(1f), if (onCover) vis else null, visStyle, visCol, strength)
                        SongLine(ui, n)
                    }
                }
            }
            Seek(n)
            Transport(n, ui.pane != "queue" && ui.pane != "lyrics", volumeShown, { volumeShown = !volumeShown }, visPane) {
                ui.pane = if (visPane) "art" else "nowvis"
            }
            if (volumeShown) VolumeLine()
            ToolPill(ui)
            Spacer(Modifier.height(14.dp))   // where the sound goes shows in the Equalizer only (1.4)
        }
    }
}

/** On the lock screen: the time, where it plays from, and ⌄ to the phone's lock screen. */
@Composable
private fun LockTop(n: Now, lock: () -> Unit) {
    var clock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            clock = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date())
            kotlinx.coroutines.delay(10_000)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(48.dp))
            Text(clock, style = T.big.copy(color = WHITE, fontSize = 30.sp, fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f))
            Box(Modifier.size(48.dp).clip(R.pill).background(GLASS).clickable(onClick = lock)
                .semantics { contentDescription = "To the lock screen" }, contentAlignment = Alignment.Center) {
                Text("⌄", style = T.big.copy(color = WHITE, fontSize = 26.sp))
            }
        }
        val t = n.t
        if (t != null && t.album.isNotEmpty()) Text("From the album ${t.album}", style = T.sub.copy(color = WHITE.copy(alpha = 0.85f)),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** ☰ (always there), where it plays from, and the gear. */
@Composable
private fun NowTop(ui: Ui, n: Now) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(Ico.MENU, WHITE) { ui.openDrawer() }
        val t = n.t
        val from = when {
            n.remote -> "Playing on the desktop"
            t == null -> ""
            Music.shownRadio() -> "Playing a radio"
            t.album.isNotEmpty() -> "From the album ${t.album}"
            Music.queueName.isNotEmpty() -> "Playing from ${Music.queueName}"
            else -> ""
        }
        Text(from, style = T.sub.copy(color = WHITE.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold), maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).clickable { ui.showQueues = true }.padding(vertical = 6.dp))
        // Playing a DJ set: straight to the DJ's next set (its next category), skipping the rest of this one.
        if (t != null && t.dj > 0 && Music.shownDj().on)
            IconButton(Ico.SKIP, C.accent, desc = "Next DJ set") { Music.djNextSet() }
        // The song's own EQ (Custom EQ…): always here to set one up; red while the song's is in effect here (on this
        // output), the accent when it has one for another output. Left of Mono (on the PC, where Auto mono's button was).
        if (!n.remote && t != null) {
            Music.musicVersion
            val has = Music.songEqs.has(t)
            val now = has && Music.songEqActive
            Box(Modifier.clip(R.pill).clickable { ui.songEqFor = listOf(t) }
                .semantics { contentDescription = when { now -> "Custom EQ on"; has -> "Custom EQ on another output"; else -> "Custom EQ for this song" } }
                .padding(horizontal = 6.dp, vertical = 10.dp)) {
                Text("C EQ", style = T.tab.copy(color = when { now -> MONO_RED; has -> C.accent; else -> SOFT }, fontWeight = FontWeight.Bold))
            }
        }
        if (!n.remote) {   // Mono: both sides in each ear (one earbud in); red while it's on, the cover's colour while it's off
            val auto = onPhone && org.omarchy.music.AutoMono.active   // Auto mono is the phone's only
            val mono = org.omarchy.music.Prefs.on(org.omarchy.music.Prefs.MONO) || auto
            IconButton(Ico.HEADPHONES, if (mono) MONO_RED else C.accent, desc = if (mono) "Mono on" else "Mono off") {
                if (auto) Music.toast("Auto mono has mono on while your earbuds are connected (Settings › Playback)")
                else { Music.setOpt(org.omarchy.music.Prefs.MONO, !mono); Music.toast(if (mono) "Stereo" else "Mono: both sides in each ear") }
            }
        }
        // Custom volume ("C" and a speaker): always here, straight to the playing song's own volume; red while a change of it
        // is in effect here (this output), the accent when it has one for another output.
        if (!n.remote && t != null) {
            Music.musicVersion
            val has = Music.gains.has(t)
            val now = has && Music.gainNow(t) != 0.0
            val col = when { now -> MONO_RED; has -> C.accent; else -> SOFT }
            Row(Modifier.clip(R.pill).clickable { ui.volumeFor = listOf(t) }.padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("C", style = T.tab.copy(color = col, fontWeight = FontWeight.Bold))
                Icon(Ico.SPEAKER, col, 20.dp)
            }
        }
        // Ignore Bluetooth devices: on or off at a tap, red while on; a long-press (right-click) picks the devices (Settings)
        if (!n.remote) {
            val ign = org.omarchy.music.IgnoreBt
            val on = ign.on
            val picked = ign.ignored()
            Box(Modifier.clip(R.pill).pressable(onClick = {
                when {
                    picked.isEmpty() -> { ui.openSettings("Playback")
                        Music.toast("Pick the Bluetooth devices to ignore (your car) under Ignore Bluetooth devices") }
                    on -> { ign.setOn(false); Music.toast("Not ignoring Bluetooth devices: the music goes wherever your $DEVICE sends it") }
                    else -> { ign.setOn(true); Music.toast("Ignoring ${picked.joinToString(", ")}: the music stays where it is when " +
                        "${if (picked.size == 1) "it connects" else "they connect"}. ${LONG_PRESS.cap()} to change which") }
                }
            }, onLongClick = { ui.openSettings("Playback") })
                .semantics { contentDescription = if (on) "Ignoring Bluetooth devices" else "Not ignoring Bluetooth devices" }
                .padding(10.dp), contentAlignment = Alignment.Center) {
                Icon(Ico.BLUETOOTH_OFF, if (on) MONO_RED else SOFT, 24.dp)
            }
        }
        if (!n.remote) IconButton(Ico.CAST, if (Music.cast != null) C.accent else WHITE, desc = "Cast") { ui.castPick = true }
        IconButton(Ico.SETTINGS, WHITE) { ui.openSettings() }
    }
}

/** The cover, big: swipe it for the next or the last song, up for the queue; tap for the lyrics, double-tap to pause.
 *  over: the visualizer drawn over it (a style around a middle, Vis.OVER_COVER), in visStyle, moving with the cover's swipe. */
@Composable
private fun BigCover(ui: Ui, art: Any?, playing: Boolean, modifier: Modifier, over: VisState? = null, visStyle: String = "",
                     visCol: Color = Color.White, strength: Float = 0.5f) {
    var dx by remember { mutableFloatStateOf(0f) }
    Box(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            val side = if (maxWidth < maxHeight) maxWidth else maxHeight
            Box(Modifier.size(side)
                .graphicsLayer(translationX = dx * 0.6f, rotationZ = dx / 90f, alpha = 1f - (abs(dx) / 900f).coerceIn(0f, 0.5f))
                .shadow(26.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(14.dp)).background(GLASS)
                .pointerInput(playing) {
                    detectHorizontalDragGestures(onDragEnd = {
                        if (playing) { if (dx < -160f) Music.next() else if (dx > 160f) Music.previous() }
                        dx = 0f
                    }, onDragCancel = { dx = 0f }) { _, d -> dx += d }
                }
                .pointerInput(Unit) {
                    var dy = 0f
                    detectVerticalDragGestures(onDragStart = { dy = 0f }, onDragEnd = { if (dy < -80f) ui.pane = "queue" }) { _, d -> dy += d }
                }
                .pointerInput(Unit) { detectTapGestures(onTap = { ui.pane = "lyrics" }, onDoubleTap = { Music.togglePause() }) },
                contentAlignment = Alignment.Center) {
                Icon(Ico.NOTE, WHITE.copy(alpha = 0.4f), side * 0.3f)
                if (art != null) AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        // On top of the cover, a little past its edges, see-through; it takes no taps, so the cover's swipes and taps still work.
        if (over != null) NowVisOver(visStyle, over, visCol, strength, Modifier.matchParentSize().graphicsLayer(translationX = dx * 0.6f))
    }
}

/** The song, the artist (— the album), and ☆ and ⋯ in glass circles. */
@Composable
private fun SongLine(ui: Ui, n: Now) {
    val t = n.t
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t?.title ?: "Nothing playing", style = T.big.copy(color = WHITE, fontSize = 24.sp, fontWeight = FontWeight.Medium),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (t != null) ArtistLinks(t.artist, ui, t.source, style = T.body.copy(color = SOFT, fontSize = 17.sp),
                suffix = if (t.album.isNotEmpty()) "  —  ${t.album}" else "")
            else Text("Open ☰ and pick a song to start", style = T.body.copy(color = SOFT))
        }
        if (t != null && !n.remote) {
            Music.musicVersion
            val fav = Music.favorites.has(t)
            Glass(if (fav) Ico.STAR else Ico.STAR_LINE, "Favorite") { Music.setFavorite(listOf(t)) }
            Spacer(Modifier.width(10.dp))
            Box {
                Glass(Ico.MORE, "More") { menu = true }
                TrackMenu(t, RowPlace(listOf(t), 0), ui, menu) { menu = false }
            }
        }
    }
}

@Composable
private fun Glass(ico: Ico, desc: String, on: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(R.pill).background(if (on) WHITE.copy(alpha = 0.85f) else GLASS).clickable(onClick = onClick)
        .semantics { contentDescription = desc }
        .padding(10.dp), contentAlignment = Alignment.Center) { Icon(ico, if (on) ON_WHITE else WHITE, 20.dp) }
}

/** A thin bar to seek on; the time gone, the quality, the time left. */
@Composable
private fun Seek(n: Now) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val frac = drag ?: if (n.dur > 0) (n.pos / n.dur).toFloat().coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        SlimBar(frac, onMove = { drag = it }, onDone = { f -> if (n.dur > 0) Music.seek(f * n.dur); drag = null })
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            val pos = drag?.let { it * n.dur } ?: n.pos
            Text(fmtTime(pos), style = T.time.copy(color = SOFT))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (!n.remote) QualityMark()
                else if (Remote.status.quality.isNotEmpty()) Text(Remote.status.quality, style = T.time.copy(color = SOFT, fontWeight = FontWeight.SemiBold), maxLines = 1)
            }
            Text("-" + fmtTime(maxOf(0.0, n.dur - pos)), style = T.time.copy(color = SOFT))
        }
    }
}

/** A slim bar (the seek bar, the volume): tap or drag along it. */
@Composable
private fun SlimBar(frac: Float, onMove: (Float) -> Unit, onDone: (Float) -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf(false) }
    Canvas(modifier.fillMaxWidth().height(24.dp)
        .pointerInput(Unit) { detectTapGestures { o -> onDone((o.x / size.width).coerceIn(0f, 1f)) } }
        .pointerInput(Unit) {
            var last = 0f
            detectHorizontalDragGestures(
                onDragStart = { o -> dragging = true; last = (o.x / size.width).coerceIn(0f, 1f); onMove(last) },
                onDragEnd = { dragging = false; onDone(last) },
                onDragCancel = { dragging = false; onDone(last) },
            ) { change, _ -> last = (change.position.x / size.width).coerceIn(0f, 1f); onMove(last) }
        }) {
        val h = (if (dragging) 9f else 6f) * density / 2.6f * 2.6f
        val y = (size.height - h) / 2
        drawRoundRect(WHITE.copy(alpha = 0.22f), Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
        drawRoundRect(WHITE.copy(alpha = 0.9f), Offset(0f, y), Size(size.width * frac, h), CornerRadius(h / 2))
    }
}

/** FLAC, the stream's quality, or ⤓ FLAC to save it. */
@Composable
private fun QualityMark() {
    Music.savedVersion
    val t = Music.current() ?: return
    val saved = Music.lib.items[t.id]
    val saving = Music.dl.busy(t.id) || Music.downloads.any { it.id == t.id }
    val stream = saved == null && !t.isFile
    val lossy = if (stream) Music.dl.streamLossy(t.id) else null
    val heard = Music.nowFormat.takeIf { Music.nowFormatOf == t.id && it.isNotEmpty() }
    val label = when {
        saving -> "Saving…"
        heard != null -> if (stream && lossy == null) "⤓ $heard" else heard   // the bit depth and kHz the player has
        saved?.format == "flac" -> "FLAC" + (saved.quality.takeIf { it.isNotEmpty() }?.let { " $it" } ?: "")
        saved != null -> "Offline"
        saving -> "Saving…"
        lossy != null -> Music.dl.streamQuality(t.id).ifEmpty { "Streaming" }
        stream -> "⤓ FLAC"
        t.isFile -> t.file?.substringAfterLast('.', "")?.uppercase().orEmpty()
        else -> ""
    }
    if (label.isEmpty()) return
    Text(label, style = T.time.copy(color = SOFT, fontWeight = FontWeight.SemiBold), maxLines = 1,
        modifier = Modifier.clip(R.small).clickable(enabled = stream && !saving && lossy == null) { Music.saveCurrent() }
            .padding(horizontal = 6.dp, vertical = 2.dp))
}

/** Previous, play/pause, next together in the middle, plain and white; the volume at the far left (it shows or hides the
 *  volume bar) and the visualizer's settings at the far right (they open in the cover's place; visOpen: they show).
 *  modes (the big cover showing): Shuffle left of Previous and Repeat right of Next too, all a little smaller so they fit a phone. */
@Composable
private fun Transport(n: Now, modes: Boolean, volumeShown: Boolean, onVolume: () -> Unit, visOpen: Boolean, onVisualizer: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = if (modes) 6.dp else 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(Ico.SPEAKER, if (volumeShown) WHITE else SOFT, 24.dp, 12.dp, desc = if (volumeShown) "Hide the volume" else "Volume") { onVolume() }
        if (modes) BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            // The five take 244dp at full size; on a narrower phone they all shrink a little so none is cut off.
            val k = (maxWidth / 244.dp).coerceIn(0.6f, 1f)
            val rep = if (n.remote) Remote.status.repeat else Music.repeat
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp * k), verticalAlignment = Alignment.CenterVertically) {
                ModeButton(Ico.SHUFFLE, Music.shuffled, "Shuffle", k) { Music.shuffle() }
                IconButton(Ico.PREV, WHITE, 30.dp * k, 8.dp * k) { Music.previous() }
                IconButton(if (n.paused) Ico.PLAY else Ico.PAUSE, WHITE, 44.dp * k, 10.dp * k) { Music.togglePause() }
                IconButton(Ico.NEXT, WHITE, 30.dp * k, 8.dp * k) { Music.next() }
                ModeButton(if (rep == "one") Ico.REPEAT_ONE else Ico.REPEAT, rep != "off",
                    when (rep) { "one" -> "Repeat one"; "all" -> "Repeat all"; else -> "Repeat off" }, k) { Music.cycleRepeat() }
            }
        } else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(Ico.PREV, WHITE, 34.dp, 10.dp) { Music.previous() }
            IconButton(if (n.paused) Ico.PLAY else Ico.PAUSE, WHITE, 48.dp, 12.dp) { Music.togglePause() }
            IconButton(Ico.NEXT, WHITE, 34.dp, 10.dp) { Music.next() }
        }
        IconButton(Ico.VIS, if (visOpen) WHITE else SOFT, 24.dp, 12.dp,
            desc = if (visOpen) "Close the visualizer settings" else "Visualizer settings") { onVisualizer() }
    }
}

/** Shuffle or Repeat beside the transport: filled while it's on, as the queue's buttons are (k: the size, 1 = full). */
@Composable
private fun ModeButton(ico: Ico, on: Boolean, desc: String, k: Float, onClick: () -> Unit) {
    Box(Modifier.clip(R.pill).background(if (on) WHITE.copy(alpha = 0.85f) else Color.Transparent).clickable(onClick = onClick)
        .semantics { contentDescription = desc }.padding(8.dp * k), contentAlignment = Alignment.Center) {
        Icon(ico, if (on) ON_WHITE else SOFT, 20.dp * k)
    }
}

/** The phone's media volume, as a slim bar between a quiet and a loud speaker. */
@Composable
private fun VolumeLine() {
    var v by remember { mutableFloatStateOf(SysVolume.get()) }
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {   // the volume keys change it too
        while (true) {
            kotlinx.coroutines.delay(600)
            if (!held) v = SysVolume.get()
        }
    }
    fun set(f: Float) { v = f; SysVolume.set(f) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Ico.SPEAKER, SOFT, 16.dp)
        SlimBar(v, onMove = { held = true; set(it) }, onDone = { held = false; set(it) }, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
        Icon(Ico.SPEAKER, SOFT, 22.dp)
    }
}

/** Lyrics, where the sound goes (its equalizer) and the queue, in a glass pill. */
@Composable
private fun ToolPill(ui: Ui) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.clip(R.pill).background(GLASS).border(1.dp, WHITE.copy(alpha = 0.14f), R.pill).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PillTool(Ico.LYRICS, ui.pane == "lyrics", "Lyrics") { ui.pane = if (ui.pane == "lyrics") "art" else "lyrics" }
            // The song's own EQ is in effect: the usual one greyed out (it isn't what's heard); C EQ is where to change it
            if (Music.songEqActive) PillTool(Ico.SPEAKER, false, "Output and equalizer (this song uses its own EQ)", dim = true) {
                Music.toast("This song has its own EQ on ${Music.outputNow.label}: tap C EQ at the top to change it"); ui.sheet = "eq" }
            else PillTool(Ico.SPEAKER, false, "Output and equalizer") { ui.sheet = "eq" }
            PillTool(Ico.QUEUE, ui.pane == "queue", "Queue") { ui.pane = if (ui.pane == "queue") "art" else "queue" }
        }
    }
}

@Composable
private fun PillTool(ico: Ico, on: Boolean, desc: String, dim: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(width = 52.dp, height = 40.dp).clip(R.pill).background(if (on) WHITE.copy(alpha = 0.88f) else Color.Transparent)
        .clickable(onClick = onClick).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        Icon(ico, if (on) ON_WHITE else if (dim) WHITE.copy(alpha = 0.3f) else WHITE, 22.dp)
    }
}

// ───────────────────────────── The queue, in the cover's place ─────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueuePane(ui: Ui, n: Now) {
    val t = n.t
    val q = Music.shownQueue()
    val idx = Music.shownIndex()
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable { ui.pane = "art" }) { ArtImage(t?.let { if (n.remote) Remote.artModel(it) else artModel(it) }, 60.dp) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t?.title ?: "Nothing playing", style = T.title.copy(color = WHITE, fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t?.artist ?: "", style = T.sub.copy(color = SOFT), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (t != null && !n.remote) {
                Music.musicVersion
                val fav = Music.favorites.has(t)
                Glass(if (fav) Ico.STAR else Ico.STAR_LINE, "Favorite") { Music.setFavorite(listOf(t)) }
                Spacer(Modifier.width(10.dp))
                Box {
                    Glass(Ico.MORE, "More") { menu = true }
                    TrackMenu(t, RowPlace(listOf(t), 0), ui, menu) { menu = false }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val rep = if (n.remote) Remote.status.repeat else Music.repeat
            Mode(Ico.SHUFFLE, Music.shuffled, "Shuffle", Modifier.weight(1f)) { Music.shuffle() }
            Mode(if (rep == "one") Ico.REPEAT_ONE else Ico.REPEAT, rep != "off", "Repeat", Modifier.weight(1f)) { Music.cycleRepeat() }
            Mode(Ico.INFINITY, Music.autoplay, "Autoplay", Modifier.weight(1f)) { Music.setAutoplay(!Music.autoplay) }
        }
        val start = (idx + 1).coerceAtLeast(0)
        val queued = (start until q.size).filter { q[it].queued }
        val rest = (start until q.size).filter { !q[it].queued }
        val list = rememberLazyListState()
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list) {
            if (queued.isNotEmpty()) {
                item("qh") { QHead("Queue", null, "Clear") { ui.confirm = "Clear the songs you queued?" to { Music.clearQueued() } } }
                itemsIndexed(queued, key = { _, j -> "q$j-${q[j].id}" }) { _, j -> QueueSong(q[j], j, ui, n.remote) }
            }
            item("ch") { QHead("Continue Playing", if (Music.queueName.isNotEmpty()) "From ${Music.queueName}" else "From your music", null) {} }
            if (rest.isEmpty()) item("none") {
                Text(if (Music.autoplay) "Autoplay plays songs like this one next" else "Nothing after this song",
                    style = T.sub.copy(color = SOFT), modifier = Modifier.padding(vertical = 10.dp))
            }
            itemsIndexed(rest, key = { _, j -> "n$j-${q[j].id}" }) { _, j -> QueueSong(q[j], j, ui, n.remote) }
            if (!n.remote && Music.queues.isNotEmpty()) {
                item("yh") { QHead("Your queues", null, "Remove all others") { ui.confirm = "Delete all your other queues? Only the one playing stays." to { Music.removeOtherQueues() } } }
                itemsIndexed(Music.queues, key = { _, s -> "s" + s.id }) { _, s ->
                    Row(Modifier.fillMaxWidth().clip(R.card).clickable { Music.switchQueue(s.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ico.QUEUE, SOFT, 20.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(s.name, style = T.title.copy(color = WHITE, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val cur = s.tracks.getOrNull(s.index)?.title ?: ""
                            Text("${s.index + 1} of ${s.tracks.size} · $cur", style = T.sub.copy(color = SOFT), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(Ico.UP, SOFT, 18.dp, 8.dp, desc = "Move up") { Music.moveQueue(s.id, -1) }
                        IconButton(Ico.DOWN, SOFT, 18.dp, 8.dp, desc = "Move down") { Music.moveQueue(s.id, 1) }
                        IconButton(Ico.EDIT, SOFT, 18.dp, 8.dp) {
                            ui.askName = Triple("Rename the queue", s.name) { nm -> Music.renameQueue(s.id, nm) }
                        }
                        IconButton(Ico.TRASH, SOFT, 18.dp, 8.dp) { ui.confirm = "Delete this queue?" to { Music.deleteQueue(s.id) } }
                    }
                }
            }
            item("end") { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun QHead(title: String, sub: String?, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.title.copy(color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold))
            if (sub != null) Text(sub, style = T.sub.copy(color = SOFT))
        }
        if (action != null) Text(action, style = T.body.copy(color = SOFT, fontWeight = FontWeight.Medium),
            modifier = Modifier.clip(R.small).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp))
    }
}

@Composable
private fun Mode(ico: Ico, on: Boolean, desc: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(if (on) WHITE.copy(alpha = 0.62f) else GLASS)
        .clickable(onClick = onClick).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        Icon(ico, if (on) ON_WHITE.copy(alpha = 0.75f) else WHITE, 20.dp)
    }
}

/** A song in the queue: tap to play it, long-press for its menu, drag ≡ up or down to move it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueSong(t: Track, j: Int, ui: Ui, remote: Boolean) {
    var menu by remember { mutableStateOf(false) }
    var dy by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { 60.dp.toPx() }
    Box(Modifier.graphicsLayer(translationY = dy).background(if (dy != 0f) Color.Black.copy(alpha = 0.2f) else Color.Transparent, R.card)) {
        val checked = t.id in ui.selected
        if (ui.selecting) androidx.compose.runtime.SideEffect {
            val q = Music.shownQueue()
            if (ui.selectPool !== q) { ui.selectPool = q; ui.selectPlace = RowPlace(q, j, kind = "queue", queueIndex = j) }
        }
        Row(Modifier.fillMaxWidth().height(60.dp).clip(R.card)
            .background(if (checked) WHITE.copy(alpha = 0.14f) else Color.Transparent)
            .pressable(onClick = { if (ui.selecting) ui.toggle(t) else Music.playIndex(j) },
                onLongClick = { if (ui.selecting) ui.optionsHere(t) else menu = true }),
            verticalAlignment = Alignment.CenterVertically) {
            if (ui.selecting) androidx.compose.material3.Checkbox(checked, { ui.toggle(t) },   // picked: a checkmark, as in the lists
                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = WHITE, checkmarkColor = ON_WHITE,
                    uncheckedColor = SOFT))
            ArtImage(if (remote) Remote.artModel(t) else artModel(t), 46.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t.title, style = T.title.copy(color = WHITE, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.artist, style = T.sub.copy(color = SOFT), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!remote) Box(Modifier.size(44.dp).pointerInput(j) {
                detectVerticalDragGestures(onDragStart = { dy = 0f },
                    onDragEnd = {
                        val steps = (dy / rowPx).roundToInt()
                        dy = 0f
                        if (steps != 0) Music.moveInQueue(j, (j + steps).coerceIn(Music.index + 1, Music.queue.size - 1))
                    },
                    onDragCancel = { dy = 0f }) { _, d -> dy += d }
            }, contentAlignment = Alignment.Center) { Icon(Ico.GRIP, WHITE.copy(alpha = 0.4f), 20.dp) }
        }
        TrackMenu(t, RowPlace(Music.shownQueue(), j, kind = "queue", queueIndex = j), ui, menu) { menu = false }
    }
}

// ───────────────────────────── The visualizer's settings, in the cover's place ─────────────────────────────

/** The strength while its bar is dragged (saved once let go, the PC writes its settings file on each change); null: not dragged. */
private var strengthDrag by mutableStateOf<Float?>(null)
/** Now playing visualizer's strength now: the one being dragged, else the saved one. */
private val nowStrength: Float get() = strengthDrag ?: Vis.nowStrength()

/** Now playing's visualizer, changed right here: every style with a small live preview, its colour, its strength. Saved to
 *  the same settings as Settings › Look › Now playing visualizer, and it shows straight away behind or over the cover.
 *  s: the music's levels (null: not allowed yet; askMic asks). openFull: to the big Visualizer tab (null: on the lock screen). */
@Composable
private fun NowVisPane(ui: Ui, s: VisState?, col: Color, micOk: Boolean, askMic: () -> Unit, openFull: (() -> Unit)?) {
    val style = Vis.nowStyle()
    val colorKey = Vis.color()
    val levels = s ?: remember { VisState() }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Visualizer", style = T.title.copy(color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Text("What moves with the music on this screen", style = T.sub.copy(color = SOFT), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Glass(Ico.CLOSE, "Close") { ui.pane = "art" }
        }
        if (!micOk) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(10.dp)).background(GLASS)
            .clickable { askMic() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("It needs to hear the music the app plays. Tap to allow it", style = T.sub.copy(color = WHITE), modifier = Modifier.weight(1f))
        }
        val rows = NP_VIS_STYLES.chunked(3)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(rows, key = { i, _ -> "r$i" }) { _, row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (k, label) -> VisTile(k, label, k == style, levels, col, colorKey, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item("color") {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text("Colour", style = T.title.copy(color = WHITE, fontWeight = FontWeight.Medium))
                        Text(if (style == "slim" || style == "off") "For the Visualizer's styles; the slim bars take the cover's colour"
                            else "Look's colour is the cover's colour here", style = T.sub.copy(color = SOFT))
                    }
                    VisColorPick(colorKey, col)
                }
            }
            item("strength") {
                Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text("Strength", style = T.title.copy(color = WHITE, fontWeight = FontWeight.Medium))
                    // The bar's 0 … 1 is a strength of 0.1 … 1 (never quite invisible: Off is for that).
                    SlimBar((nowStrength - 0.1f) / 0.9f, onMove = { strengthDrag = 0.1f + 0.9f * it },
                        onDone = { Prefs.setNum(Vis.NOW_STRENGTH, 0.1f + 0.9f * it); strengthDrag = null })
                }
            }
            if (openFull != null) item("full") {
                Text("Open the full Visualizer ›", style = T.body.copy(color = SOFT, fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(top = 10.dp, bottom = 14.dp).clip(R.small).clickable { openFull() }
                        .padding(horizontal = 6.dp, vertical = 4.dp))
            } else item("end") { Spacer(Modifier.height(12.dp)) }
        }
    }
}

/** A style to pick: its small live preview (Off: a ✕) and its name; the one picked is lit. */
@Composable
private fun VisTile(key: String, label: String, on: Boolean, s: VisState, col: Color, colorKey: String, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val fg = WHITE
    Column(modifier.clip(shape).background(if (on) WHITE.copy(alpha = 0.22f) else GLASS)
        .border(1.dp, if (on) WHITE.copy(alpha = 0.75f) else Color.Transparent, shape)
        .clickable { Prefs.setText(Vis.NOW, key) }.semantics { contentDescription = label }.padding(6.dp)) {
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center) {
            if (key == "off") Icon(Ico.CLOSE, SOFT, 18.dp)
            else Canvas(Modifier.fillMaxSize()) {
                if (s.frame < 0) return@Canvas   // read, so it's drawn again each frame
                clipRect {
                    if (key == "slim") drawSlim(s.levels, col, 2.5f)
                    else drawVis(key, s, VisPalette.of(key, colorKey, col, s.phase), fg, backdrop = true)
                }
            }
        }
        Text(label, style = T.sub.copy(color = if (on) WHITE else SOFT, fontSize = 12.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

/** The Visualizer's colour (the same setting as the Visualizer tab's), as a glass pill and its menu. col: Look's colour here. */
@Composable
private fun VisColorPick(current: String, col: Color) {
    Box {
        var open by remember { mutableStateOf(false) }
        val name = VIS_COLORS.firstOrNull { it.first == current }?.second ?: VIS_COLORS.first().second
        Row(Modifier.clip(R.pill).background(GLASS).clickable { open = true }.semantics { contentDescription = "Colour: $name" }
            .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val (bg, dot) = visSwatch(current, col)
            VisColorDot(bg, dot)
            Text("$name ▾", style = T.body.copy(color = WHITE, fontWeight = FontWeight.Medium), maxLines = 1,
                modifier = Modifier.padding(start = 6.dp))
        }
        DropdownMenu(open, { open = false }, containerColor = C.light) {
            VIS_COLORS.forEach { (k, label) ->
                DropdownMenuItem(text = {
                    Text(label, style = T.body.copy(color = if (k == current) C.accent else C.fg,
                        fontWeight = if (k == current) FontWeight.SemiBold else FontWeight.Normal))
                }, onClick = { open = false; Prefs.setText(Vis.COLOR, k) }, leadingIcon = {
                    val (bg, dot) = visSwatch(k, col)
                    VisColorDot(bg, dot)
                })
            }
        }
    }
}

// ───────────────────────────── The bars ─────────────────────────────

/** The bands' levels from the music's spectrum (rememberSpectrum), and a frame count that ticks
 *  while they move (null: the visualizer isn't allowed, or isn't there). */
@Composable
fun rememberLevels(on: Boolean): Pair<FloatArray, Int>? {
    val target = rememberSpectrum(on) ?: return null
    val levels = remember { FloatArray(Spectrum.BANDS) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now ->
            var moving = false
            for (b in levels.indices) {
                val want = if (Music.paused) 0f else target[b]
                val have = levels[b]
                // Android hands over ~20 spectra a second: rise most of the way at once, fall quickly, eased between.
                val new = if (want > have) have + (want - have) * 0.7f else have - (have - want) * 0.3f
                if (abs(new - have) > 0.002f) moving = true
                levels[b] = new
            }
            if (moving && now - last > 16_000_000L) { last = now; frame++ }   // every screen frame: it keeps up with the beat
        }
    }
    return levels to frame
}

/** Now playing's visualizer, faint behind everything: the slim bars, or a Visualizer style (in the Visualizer's colour;
 *  its Look's colour here is the cover's light shade). The styles around a middle get the whole screen (while the cover
 *  doesn't show; over it, NowVisOver), the others the bottom. strength: its opacity (Vis.nowStrength). */
@Composable
private fun NowVis(style: String, s: VisState, bars: Color, strength: Float, modifier: Modifier) {
    if (style == "slim") { NowBars(s.levels, s.frame, bars, modifier, strength / 0.5f); return }
    val colorKey = Vis.color()
    Canvas(modifier.fillMaxWidth().then(if (style in Vis.CENTRED) Modifier.fillMaxHeight() else Modifier.fillMaxHeight(0.32f))
        .graphicsLayer(alpha = strength)) {
        if (s.frame < 0) return@Canvas   // read, so it's drawn again each frame
        val pal = VisPalette.of(style, colorKey, bars, s.phase)
        clipRect { drawVis(style, s, pal, WHITE, backdrop = true) }
    }
}

/** A style around a middle (Vis.OVER_COVER) over the big cover: brighter than behind, still see-through so the cover shows.
 *  A plain Canvas takes no taps, so they reach the cover under it. */
@Composable
private fun NowVisOver(style: String, s: VisState, col: Color, strength: Float, modifier: Modifier) {
    val colorKey = Vis.color()
    val fg = WHITE
    Canvas(modifier.graphicsLayer(alpha = (strength * 1.3f).coerceIn(0.1f, 1f))) {
        if (s.frame < 0) return@Canvas   // read, so it's drawn again each frame
        drawVis(style, s, VisPalette.of(style, colorKey, col, s.phase), fg, backdrop = true)
    }
}

/** Twice as many slim bars as bands (each between two is their mix), spread out along the bottom (k: brighter or fainter). */
@Composable
private fun NowBars(levels: FloatArray, frame: Int, col: Color, modifier: Modifier, k: Float = 1f) {
    Canvas(modifier.fillMaxWidth().fillMaxHeight(0.28f)) {
        @Suppress("UNUSED_EXPRESSION") frame
        drawSlim(levels, col, k)
    }
}

private fun DrawScope.drawSlim(levels: FloatArray, col: Color, k: Float) {
    val n = levels.size
    val m = n * 2 - 1
    val slot = size.width / m
    val bw = maxOf(1.5f, slot * 0.38f)
    for (i in 0 until m) {
        val lv = if (i % 2 == 0) levels[i / 2] else (levels[i / 2] + levels[i / 2 + 1]) / 2
        val h = maxOf(2f, lv * size.height)
        drawRect(col.copy(alpha = ((0.12f + 0.22f * lv) * k).coerceIn(0f, 1f)), Offset(i * slot + (slot - bw) / 2, size.height - h), Size(bw, h))
    }
}

/** The mini player (Android's picture in picture): the cover, the song, its bars in the cover's colour. Its
 *  buttons are Android's own, under your finger: previous, play or pause, next. */
@Composable
fun MiniPlayer() {
    val n = now()
    val t = n.t
    val act = currentActivity()
    val art = t?.let { if (n.remote) Remote.artModel(it) else artModel(it, 600) }
    var cols by remember { mutableStateOf(GREY) }
    LaunchedEffect(t?.id) { cols = loadNowColors(art) ?: GREY }
    LaunchedEffect(Music.paused) { updateMiniPlayer(act) }
    val levels = rememberLevels(on = true)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(cols.top, cols.bottom)))) {
        if (art != null) AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer(alpha = 0.85f))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
        if (levels != null) NowBars(levels.first, levels.second, cols.bars, Modifier.align(Alignment.BottomCenter).fillMaxHeight(0.3f))
        Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            Text(t?.title ?: "Nothing playing", style = T.title.copy(color = Color.White, fontSize = 13.sp), maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(t?.artist ?: "", style = T.sub.copy(color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp), maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Mono's colour while it is on (Now playing's button, the queue's badge). */
val MONO_RED = androidx.compose.ui.graphics.Color(0xFFE5484D)
