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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

private val WHITE = Color.White
private val SOFT = Color.White.copy(alpha = 0.62f)
private val GLASS = Color.White.copy(alpha = 0.14f)

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
    val levels = if (micOk) rememberLevels(on = true) else null
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(cols.top, cols.bottom)))) {
        // The cover in the colour, faintly, slightly blurred (a small copy drawn big is soft on every phone).
        if (art != null) AsyncImage(model = smallArt(art, 20), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer(alpha = 0.28f))
        if (levels != null) NowBars(levels.first, levels.second, cols.bars, Modifier.align(Alignment.BottomCenter))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (lock != null) LockTop(n, lock) else NowTop(ui, n)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (ui.pane) {
                    "queue" -> QueuePane(ui, n)
                    "lyrics" -> Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).clip(R.big)
                        .background(Color.Black.copy(alpha = 0.25f))) { LyricsTab(ui) }
                    else -> Column(Modifier.fillMaxSize()) {
                        BigCover(ui, art, t != null, Modifier.weight(1f))
                        SongLine(ui, n)
                    }
                }
            }
            Seek(n)
            Transport(n)
            VolumeLine()
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
        IconButton(Ico.MENU, WHITE) { ui.drawer = true }
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
        if (!n.remote) {   // Mono: both sides in each ear (one earbud in); red while it's on, the cover's colour while it's off
            val auto = org.omarchy.music.AutoMono.active
            val mono = org.omarchy.music.Prefs.on(org.omarchy.music.Prefs.MONO) || auto
            IconButton(Ico.HEADPHONES, if (mono) MONO_RED else C.accent, desc = if (mono) "Mono on" else "Mono off") {
                if (auto) Music.toast("Auto mono has mono on while your earbuds are connected (Settings › Playback)")
                else { Music.setOpt(org.omarchy.music.Prefs.MONO, !mono); Music.toast(if (mono) "Stereo" else "Mono: both sides in each ear") }
            }
        }
        // A song with its own volume: 🔊! shown, red while that change is in effect here (this output, the switch on)
        if (!n.remote && t != null && Music.musicVersion >= 0 && Music.gains.has(t)) {
            val now = Music.gainNow(t) != 0.0
            IconButton(Ico.VOLUME_ALERT, if (now) MONO_RED else C.accent,
                desc = if (now) "Volume changed here" else "Volume changed on another output") { ui.volumeFor = listOf(t) }
        }
        // A song with its own EQ (Custom EQ…): C EQ, red while it's in effect here (on this output)
        if (!n.remote && t != null && Music.musicVersion >= 0 && Music.songEqs.has(t)) {
            val now = Music.songEqActive
            Box(Modifier.clip(R.pill).clickable { ui.songEqFor = listOf(t) }
                .semantics { contentDescription = if (now) "Custom EQ on" else "Custom EQ on another output" }.padding(horizontal = 6.dp, vertical = 10.dp)) {
                Text("C EQ", style = T.tab.copy(color = if (now) MONO_RED else C.accent, fontWeight = FontWeight.Bold))
            }
        }
        if (!n.remote) IconButton(Ico.CAST, if (Music.cast != null) C.accent else WHITE, desc = "Cast") { ui.castPick = true }
        IconButton(Ico.SETTINGS, WHITE) { ui.openSettings() }
    }
}

/** The cover, big: swipe it for the next or the last song, up for the queue; tap for the lyrics, double-tap to pause. */
@Composable
private fun BigCover(ui: Ui, art: Any?, playing: Boolean, modifier: Modifier) {
    var dx by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
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
        .padding(10.dp), contentAlignment = Alignment.Center) { Icon(ico, if (on) Color(0xFF222222) else WHITE, 20.dp) }
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

/** Previous, play/pause, next: plain and white, Apple's way. */
@Composable
private fun Transport(n: Now) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(Ico.PREV, WHITE, 34.dp, 12.dp) { Music.previous() }
        IconButton(if (n.paused) Ico.PLAY else Ico.PAUSE, WHITE, 48.dp, 12.dp) { Music.togglePause() }
        IconButton(Ico.NEXT, WHITE, 34.dp, 12.dp) { Music.next() }
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
        Icon(ico, if (on) Color(0xFF222222) else if (dim) WHITE.copy(alpha = 0.3f) else WHITE, 22.dp)
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
                item("qh") { QHead("Queue", null, "Clear") { Music.clearQueued() } }
                itemsIndexed(queued, key = { _, j -> "q$j-${q[j].id}" }) { _, j -> QueueSong(q[j], j, ui, n.remote) }
            }
            item("ch") { QHead("Continue Playing", if (Music.queueName.isNotEmpty()) "From ${Music.queueName}" else "From your music", null) {} }
            if (rest.isEmpty()) item("none") {
                Text(if (Music.autoplay) "Autoplay plays songs like this one next" else "Nothing after this song",
                    style = T.sub.copy(color = SOFT), modifier = Modifier.padding(vertical = 10.dp))
            }
            itemsIndexed(rest, key = { _, j -> "n$j-${q[j].id}" }) { _, j -> QueueSong(q[j], j, ui, n.remote) }
            if (!n.remote && Music.queues.isNotEmpty()) {
                item("yh") { QHead("Your queues", null, "Remove all others") { Music.removeOtherQueues() } }
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
                        IconButton(Ico.TRASH, SOFT, 18.dp, 8.dp) { Music.deleteQueue(s.id) }
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
        Icon(ico, if (on) Color.Black.copy(alpha = 0.62f) else WHITE, 20.dp)
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
                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = WHITE, checkmarkColor = Color.Black,
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

/** Twice as many slim bars as bands (each between two is their mix), spread out along the bottom. */
@Composable
private fun NowBars(levels: FloatArray, frame: Int, col: Color, modifier: Modifier) {
    Canvas(modifier.fillMaxWidth().fillMaxHeight(0.28f)) {
        @Suppress("UNUSED_EXPRESSION") frame
        val n = levels.size
        val m = n * 2 - 1
        val slot = size.width / m
        val bw = maxOf(1.5f, slot * 0.38f)
        for (i in 0 until m) {
            val lv = if (i % 2 == 0) levels[i / 2] else (levels[i / 2] + levels[i / 2 + 1]) / 2
            val h = maxOf(2f, lv * size.height)
            drawRect(col.copy(alpha = 0.12f + 0.22f * lv), Offset(i * slot + (slot - bw) / 2, size.height - h), Size(bw, h))
        }
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
