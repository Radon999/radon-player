package org.omarchy.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.omarchy.music.ClipRules
import org.omarchy.music.Music
import org.omarchy.music.Prefs
import org.omarchy.music.Track

/** A song clip ([ClipRules]): a short video of a card with the song itself playing, to share anywhere. Here: the song
 *  clip's card (the cover big, the song, a seek bar that fills as it plays), its box (a song's Options › Share a clip
 *  with sound), and what both it and the Lyrics card use to make and send the video. The card comes in every lyrics
 *  card style: Your look, Now playing, the 60 ready-made ones and the ones you make. */
object SongClips {
    /** The card: 1080 × 1350, and where its seek bar is on it. */
    const val W = 1080; const val H = 1350
    private const val BAR_X = 110; private const val BAR_Y = 1150; private const val BAR_W = 860; private const val BAR_H = 14

    /** The style in use: the one picked for clips (Settings › Personalization), else the lyrics card's. */
    fun style(): String = Prefs.text(ClipRules.STYLE).takeIf { it == LyricsCards.LOOK || it == LyricsCards.NOW || (it.isNotEmpty() && LyricsCards.of(it) != null) } ?: LyricsCards.picked()
    fun length(): Int = Prefs.num(ClipRules.LENGTH, 15f).toInt().let { if (it in ClipRules.LENGTHS) it else 15 }

    /** A song's file, if you have it as one (yours, or a download). */
    fun fileOf(t: Track): String? = (t.file ?: Music.lib.items[t.id]?.file)?.takeIf { it.isNotEmpty() }

    private fun art(t: Track): ImageBitmap? {
        val artPath = Music.lib.items[t.id]?.art?.takeIf { it.isNotEmpty() }
        return artPath?.let { runCatching { decodeImage(java.io.File(it).readBytes()) }.getOrNull() }
            ?: t.file?.let { f -> runCatching { org.omarchy.music.FileTags.cover(Music.ctx, f)?.let { decodeImage(it) } }.getOrNull() }
    }

    private class Ink(val top: Int, val bottom: Int, val title: Int, val soft: Int, val brand: Int, val accent: Color, val fg: Color, val bg: Color,
                      val fx: Set<String>, val stars: Boolean, val round: Boolean)

    private fun argb(c: Color, alpha: Int) = Rgb.argb(alpha, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

    /** The card's colours: a lyrics card style's, or the look's (the cover's own with the Now playing look or style). */
    private fun ink(styleKey: String, art: ImageBitmap?): Ink {
        LyricsCards.of(styleKey)?.let { c ->
            fun col(v: Long) = Color(0xFF000000L or (v and 0xFFFFFF))
            return Ink(argb(col(c.top), 255), argb(col(c.bottom), 255), argb(col(c.title), 255), argb(col(c.soft), 255), argb(col(c.brand), 200),
                col(c.title), col(c.words), col(c.top), c.fx, c.stars, c.roundCover)
        }
        if (Music.look == Looks.NOW_PLAYING || styleKey == LyricsCards.NOW) {
            val hsv = FloatArray(3)
            if (art != null) {
                val (px, _, _) = pixelsOf(art)
                var r = 0L; var g = 0L; var b = 0L
                for (p in px) { r += Rgb.red(p); g += Rgb.green(p); b += Rgb.blue(p) }
                val n = px.size.coerceAtLeast(1)
                Rgb.toHsv(Rgb.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt()), hsv)
            } else { hsv[0] = 240f; hsv[1] = 0.25f }
            hsv[1] = hsv[1].coerceIn(0.2f, 0.7f).let { if (hsv[1] < 0.08f) hsv[1] else it }
            hsv[2] = 0.42f; val top = Rgb.fromHsv(hsv)
            hsv[2] = 0.22f; val bottom = Rgb.fromHsv(hsv)
            hsv[1] = (hsv[1] + 0.15f).coerceIn(0.25f, 0.9f); hsv[2] = 0.92f
            val accent = Color(Rgb.fromHsv(hsv))
            return Ink(top, bottom, Rgb.argb(255, 255, 255, 255), Rgb.argb(190, 255, 255, 255), Rgb.argb(140, 255, 255, 255), accent, Color.White,
                Color(bottom), C.fx, C.stars, false)
        }
        val grad = C.gradient
        return Ink((grad?.firstOrNull() ?: C.bg).toArgb(), (grad?.lastOrNull() ?: C.bg).toArgb(), argb(C.accent, 255), argb(C.muted, 230), argb(C.accent, 200),
            C.accent, C.fg, C.bg, C.fx, C.stars, false)
    }

    /** The song clip's card and its seek bar. fill: how much of the bar is drawn filled, 0 to 1 (0 for the video, which
     *  fills it itself as it plays; some for the picture you see while choosing). */
    fun card(t: Track, styleKey: String, startSecs: Double, endSecs: Double, fill: Float): Pair<ImageBitmap, ClipRules.Bar> {
        val pt = Painter(W, H)
        val art = art(t)
        val k = ink(styleKey, art)
        pt.gradient(0f, 0f, W.toFloat(), H.toFloat(), k.top, k.bottom)
        val seeds = java.util.Random(11).let { r -> List(200) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
        val fx = LookFx.keys(k.fx); val tints = LookFx.colors(k.fx)
        pt.draw {
            if (k.stars) for (i in 0 until 70) { val p = seeds[i]
                drawCircle(k.fg.copy(alpha = 0.25f + p[3] * 0.5f), (1f + p[2] * 2.2f), Offset(p[0] * size.width, p[1] * size.height)) }
            for (key in LookFx.ALL.map { it.key }.filter { it in fx }) drawFx(key, 3f, seeds, k.accent, k.fg, k.bg, tints[key])
        }
        val side = 780f; val left = (W - side) / 2
        pt.picture(art, left, 110f, side, if (k.round) side / 2 else 30f, empty = argb(k.fg, 40))
        val th = pt.block(t.title, 54f, true, k.title, 110f, 936f, 860f, maxLines = 2)
        pt.block(cardArtist(t), 38f, false, k.soft, 110f, 946f + th, 860f, maxLines = 1)
        val bar = ClipRules.Bar(BAR_X, BAR_Y, BAR_W, BAR_H, k.accent.toArgb() and 0xFFFFFF)
        pt.draw {
            drawRect(k.fg.copy(alpha = 0.22f), Offset(BAR_X.toFloat(), BAR_Y.toFloat()), Size(BAR_W.toFloat(), BAR_H.toFloat()))
            if (fill > 0f) drawRect(k.accent, Offset(BAR_X.toFloat(), BAR_Y.toFloat()), Size(BAR_W * fill.coerceIn(0f, 1f), BAR_H.toFloat()))
        }
        pt.text(ClipRules.clock(startSecs), 30f, false, k.soft, BAR_X.toFloat(), BAR_Y + 62f, 400f)
        pt.text(ClipRules.clock(endSecs), 30f, false, k.soft, BAR_X + BAR_W - 400f, BAR_Y + 62f, 400f, right = true)
        pt.text("Radon Player", 30f, true, k.brand, 96f, H - 60f, W - 192f)
        return pt.img to bar
    }
}

/** A card's line under the song's name: the artist and, with Plays on cards on (Settings › Personalization), how often
 *  you have played the song ("Saint Motel · 132 plays"): on lyrics cards, picture cards and song clips. */
internal fun cardArtist(t: Track): String {
    if (!Prefs.on(ClipRules.CARD_PLAYS, true)) return t.artist
    val now = System.currentTimeMillis()
    val counts = cardPlays?.takeIf { now - cardPlaysAt < 5_000 } ?: runCatching { Music.playCounts() }.getOrDefault(emptyMap()).also { cardPlays = it; cardPlaysAt = now }
    val said = org.omarchy.music.RepeatLoopRules.plays(counts[org.omarchy.music.songKey(t)] ?: t.plays)
    return if (said.isEmpty()) t.artist else t.artist + "  ·  " + said
}
@Volatile private var cardPlays: Map<Pair<String, String>, Int>? = null
@Volatile private var cardPlaysAt = 0L

/** Makes a clip away from the screen, then hands it on (to save, to share); says why if it couldn't be made. */
@Composable
internal fun rememberClipMaker(): Pair<Boolean, (ImageBitmap, ClipRules.Bar?, String, Double, Double, String, (java.io.File) -> Unit) -> Unit> {
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    return busy to { card, bar, file, start, end, name, then ->
        if (!busy) {
            busy = true
            scope.launch {
                val made = withContext(Dispatchers.IO) { runCatching { makeClip(card, bar, file, start, end - start, name) } }
                busy = false
                made.onSuccess(then).onFailure { Music.toast("Couldn't make the clip: " + (it.message ?: "something went wrong")) }
            }
        }
    }
}

/** A song's Options › Share a clip with sound: the card, where the clip starts and ends, its style, and Save or Share. */
@Composable
fun SongClipDialog(ui: Ui, t: Track) {
    val file = remember(t.id) { SongClips.fileOf(t) }
    val playingIt = Music.current()?.id == t.id
    val song = if (t.duration > 0) t.duration else if (playingIt) Music.duration else 0.0
    var range by remember(t.id) {
        val from = if (playingIt) Music.position else 0.0
        mutableStateOf(ClipRules.bounds(from, from + SongClips.length(), song))
    }
    val (start, end) = range
    var styleKey by remember { mutableStateOf(SongClips.style()) }
    val mine = LyricsCards.mine()
    val shown = remember(t.id, start.toInt(), end.toInt(), Music.look, C.bg, C.fg, C.accent, styleKey, mine) { SongClips.card(t, styleKey, start, end, 0.4f).first }
    val (busy, make) = rememberClipMaker()
    val name = ClipRules.fileName(t.title)
    fun go(then: (java.io.File) -> Unit) {
        val f = file ?: return
        val (base, bar) = SongClips.card(t, styleKey, start, end, 0f)
        make(base, bar, f, start, end, name, then)
    }
    val sliders = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.fg.copy(alpha = 0.15f))
    val top = if (song > 0) song.toFloat() else 600f
    AlertDialog(onDismissRequest = { if (!busy) ui.songClip = null }, containerColor = C.light,
        title = { Text("Clip with sound", style = T.head) },
        text = {
            Column {
                Image(shown, "The clip's card", Modifier.size(192.dp, 240.dp).align(Alignment.CenterHorizontally))
                if (file == null) Text(ClipRules.whyNot(false), style = T.sub, modifier = Modifier.padding(top = 8.dp))
                else {
                    Text("A short video of this card with the song playing, its bar filling as it goes. ${ClipRules.clock(start)} to ${ClipRules.clock(end)}: " +
                        "${(end - start).toInt()} seconds (up to ${ClipRules.MAX.toInt()})", style = T.sub, modifier = Modifier.padding(vertical = 6.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Starts", style = T.body, modifier = Modifier.width(56.dp))
                        Slider(start.toFloat(), { v -> range = ClipRules.bounds(v.toDouble(), v + (end - start), song) }, valueRange = 0f..top,
                            modifier = Modifier.weight(1f), colors = sliders)
                        Text(ClipRules.clock(start), style = T.time, modifier = Modifier.width(44.dp).padding(start = 6.dp))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Ends", style = T.body, modifier = Modifier.width(56.dp))
                        Slider(end.toFloat(), { v -> range = ClipRules.bounds(start, v.toDouble(), song) }, valueRange = 0f..top,
                            modifier = Modifier.weight(1f), colors = sliders)
                        Text(ClipRules.clock(end), style = T.time, modifier = Modifier.width(44.dp).padding(start = 6.dp))
                    }
                    Row {
                        if (playingIt) TextButton({ val at = Music.position; range = ClipRules.bounds(at, at + (end - start), song) }) { Text("Start where it's playing", color = C.accent) }
                        if (playingIt) TextButton({ Music.seek(start) }) { Text("Hear it from there", color = C.accent) }
                    }
                    if (busy) Text("Making the clip… a few seconds", style = T.sub.copy(color = C.accent))
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Text("Style", style = T.sub, modifier = Modifier.padding(end = 8.dp))
                    ChoicePill(Favs.sorted(Favs.CARDS, LyricsCards.menu()), styleKey, group = { MenuGroups.card(it) }, describe = { LyricsCards.describe(it) },
                        preview = { k -> val b = remember(k, t.id, mine) { SongClips.card(t, k, start, end, 0.4f).first }
                            Image(b, null, Modifier.size(144.dp, 180.dp)) },
                        kind = Favs.CARDS, menu = { k, l -> Favs.menu(Favs.CARDS, k, l) }) { styleKey = it; Prefs.setText(ClipRules.STYLE, it) }
                }
                if (file != null && !busy) {
                    if (onPhone) Row {
                        TextButton({ go { f -> if (!shareClip(f, name, "com.instagram.android")) Music.toast("Instagram isn't on this $DEVICE") } }) { Text("Instagram", color = C.accent) }
                        TextButton({ go { f -> if (!shareClip(f, name, "com.facebook.katana")) Music.toast("Facebook isn't on this $DEVICE") } }) { Text("Facebook", color = C.accent) }
                    }
                    Row {
                        TextButton({ go { f -> Music.toast(if (saveClip(f)) savedClipNote() else "Couldn't save it") } }) { Text("Save", color = C.muted) }
                        TextButton({ go { f -> if (!shareClip(f, name, null)) Music.toast("Couldn't share it") } }) { Text(if (onPhone) "Messages & more…" else SHARE_PICTURE, color = C.accent) }
                    }
                }
            }
        },
        dismissButton = { TextButton({ if (!busy) ui.songClip = null }) { Text("Close", color = C.muted) } })
}
