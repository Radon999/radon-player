package org.omarchy.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import org.omarchy.music.Lyrics
import org.omarchy.music.Music
import org.omarchy.music.Track

/** A lyrics card, as the desktop draws it: the lines you tick, big, on the cover's colour, with the cover,
 *  title and artist; 1080×1350, to share or save. */
fun lyricsCard(lines: List<String>, t: Track): ImageBitmap {
    val w = 1080; val h = 1350; val pad = 96f
    val pt = Painter(w, h)
    val artPath = Music.lib.items[t.id]?.art?.takeIf { it.isNotEmpty() }
    val art = artPath?.let { runCatching { decodeImage(java.io.File(it).readBytes()) }.getOrNull() }
    // The cover's colour: its average, made darker for white words to read on.
    val hsv = FloatArray(3)
    if (art != null) {
        val (px, _, _) = pixelsOf(art)
        var r = 0L; var g = 0L; var b = 0L
        for (p in px) { r += Rgb.red(p); g += Rgb.green(p); b += Rgb.blue(p) }
        val n = px.size.coerceAtLeast(1)
        Rgb.toHsv(Rgb.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt()), hsv)
    } else { hsv[0] = 240f; hsv[1] = 0.25f }
    hsv[1] = hsv[1].coerceIn(0.2f, 0.7f).let { if (hsv[1] < 0.08f) hsv[1] else it }
    hsv[2] = 0.42f
    val top = Rgb.fromHsv(hsv)
    hsv[2] = 0.22f
    val bottom = Rgb.fromHsv(hsv)
    pt.gradient(0f, 0f, w.toFloat(), h.toFloat(), top, bottom)
    var left = pad
    if (art != null) {
        pt.picture(art, pad, pad, 132f, 14f)
        left = pad + 132f + 32f
    }
    fun white(alpha: Int) = Rgb.argb(alpha, 255, 255, 255)
    val th = pt.block(t.title, 44f, true, white(255), left, pad + 18f, w - left - pad, maxLines = 4)
    pt.block(t.artist, 36f, false, white(190), left, pad + 26f + th, w - left - pad, maxLines = 4)
    val body = lines.joinToString("\n")
    val size = (if (body.length < 120) 76f else if (body.length < 220) 64f else if (body.length < 380) 52f else if (body.length < 520) 42f else 36f).let { if (lines.size > 12) minOf(it, 44f) else if (lines.size > 9) minOf(it, 56f) else it }
    val bh = pt.blockHeight(body, size, true, w - 2 * pad, spacing = 10f)
    val y = maxOf(pad + 132f + 80f, (h - bh) / 2f + 40f)
    pt.block(body, size, true, white(255), pad, y, w - 2 * pad, spacing = 10f)
    pt.text("Radon Player", 30f, true, white(140), pad, h - pad + 20f, w - 2 * pad)
    return pt.img
}

/** Lyrics card…: tick up to 15 lines, see the card, then share or save it. */
@Composable
fun LyricsCardDialog(ui: Ui, t: Track, got: Lyrics.Got) {
    val words = remember(got) {
        (got.plain ?: Lyrics.parseLrc(got.synced).joinToString("\n") { it.second })
            .lines().filter { it.isNotBlank() && !(it.startsWith("[") && it.endsWith("]")) }
    }
    val picked = remember(got) { mutableStateListOf<String>() }
    val lines = picked.ifEmpty { words.take(1) }
    val bmp = remember(lines.toList(), t.id) { lyricsCard(lines.toList(), t) }
    val box = CheckboxDefaults.colors(checkedColor = C.accent, checkmarkColor = C.darker, uncheckedColor = C.muted)
    AlertDialog(onDismissRequest = { ui.lyricsCard = null }, containerColor = C.light,
        title = { Text("Lyrics card", style = T.head) },
        text = {
            Column {
                Image(bmp, "The card", Modifier.size(208.dp, 260.dp).align(Alignment.CenterHorizontally))
                Text("Tick the lines for the card (up to 15):", style = T.sub, modifier = Modifier.padding(vertical = 6.dp))
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(words.size) { i ->
                        val wd = words[i]
                        val on = wd in picked
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, { if (it) { if (picked.size < 15) picked += wd } else picked -= wd },
                                enabled = on || picked.size < 15, colors = box)
                            Text(wd, style = T.body)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Row {
                    TextButton({ if (!sharePicture(bmp, "${t.title} lyrics", "com.instagram.android")) Music.toast("Instagram isn't on this $DEVICE") }) {
                        Text("Instagram", color = C.accent) }
                    TextButton({ if (!sharePicture(bmp, "${t.title} lyrics", "com.facebook.katana")) Music.toast("Facebook isn't on this $DEVICE") }) {
                        Text("Facebook", color = C.accent) }
                }
                Row {
                    TextButton({ Music.toast(if (savePicture(bmp, "${t.title} lyrics")) "Saved to Pictures" else "Couldn't save it") }) {
                        Text("Save", color = C.muted) }
                    TextButton({ sharePicture(bmp, "${t.title} lyrics", null) }) { Text("Messages & more…", color = C.accent) }
                }
            }
        },
        dismissButton = { TextButton({ ui.lyricsCard = null }) { Text("Close", color = C.muted) } })
}
