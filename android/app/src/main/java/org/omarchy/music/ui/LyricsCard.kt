package org.omarchy.music.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.omarchy.music.Lyrics
import org.omarchy.music.Music
import org.omarchy.music.Track

/** A lyrics card, as the desktop draws it: the lines you tick, big, on the cover's colour, with the cover,
 *  title and artist; 1080×1350, to share or save. */
fun lyricsCard(lines: List<String>, t: Track): Bitmap {
    val w = 1080; val h = 1350; val pad = 96f
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val artPath = Music.lib.items[t.id]?.art?.takeIf { it.isNotEmpty() }
    val art = artPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
    // The cover's colour: its average, made darker for white words to read on.
    val hsv = FloatArray(3)
    if (art != null) {
        val s = Bitmap.createScaledBitmap(art, 1, 1, true)
        android.graphics.Color.colorToHSV(s.getPixel(0, 0), hsv)
    } else { hsv[0] = 240f; hsv[1] = 0.25f }
    hsv[1] = hsv[1].coerceIn(0.2f, 0.7f).let { if (hsv[1] < 0.08f) hsv[1] else it }
    hsv[2] = 0.42f
    val top = android.graphics.Color.HSVToColor(hsv)
    hsv[2] = 0.22f
    val bottom = android.graphics.Color.HSVToColor(hsv)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP) })
    var left = pad
    if (art != null) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val r = RectF(pad, pad, pad + 132f, pad + 132f)
        c.save()
        c.clipPath(android.graphics.Path().apply { addRoundRect(r, 14f, 14f, android.graphics.Path.Direction.CW) })
        c.drawBitmap(art, null, r, p)
        c.restore()
        left = pad + 132f + 32f
    }
    fun paint(size: Float, bold: Boolean, alpha: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(alpha, 255, 255, 255); textSize = size
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    fun layout(text: String, p: TextPaint, width: Int, spacing: Float = 0f) =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(spacing, 1f).setMaxLines(4).build()
    val tl = layout(t.title, paint(44f, true, 255), (w - left - pad).toInt())
    c.save(); c.translate(left, pad + 18f); tl.draw(c); c.restore()
    val al = layout(t.artist, paint(36f, false, 190), (w - left - pad).toInt())
    c.save(); c.translate(left, pad + 26f + tl.height); al.draw(c); c.restore()
    val body = lines.joinToString("\n")
    val size = if (body.length < 120) 76f else if (body.length < 220) 64f else if (body.length < 380) 52f else 42f
    val bl = StaticLayout.Builder.obtain(body, 0, body.length, paint(size, true, 255).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }, (w - 2 * pad).toInt())
        .setLineSpacing(10f, 1f).build()
    val y = maxOf(pad + 132f + 80f, (h - bl.height) / 2f + 40f)
    c.save(); c.translate(pad, y); bl.draw(c); c.restore()
    c.drawText("Radon Player", pad, h - pad + 20f, paint(30f, true, 140))
    return bmp
}

/** Lyrics card…: tick up to a dozen lines, see the card, then share or save it. */
@Composable
fun LyricsCardDialog(ui: Ui, t: Track, got: Lyrics.Got) {
    val words = remember(got) {
        (got.plain ?: Lyrics.parseLrc(got.synced).joinToString("\n") { it.second })
            .lines().filter { it.isNotBlank() && !(it.startsWith("[") && it.endsWith("]")) }
    }
    val picked = remember(got) { mutableStateListOf<String>() }
    val lines = picked.ifEmpty { words.take(1) }
    val bmp = remember(lines.toList(), t.id) { lyricsCard(lines.toList(), t) }
    val ctx = LocalContext.current
    val box = CheckboxDefaults.colors(checkedColor = C.accent, checkmarkColor = C.darker, uncheckedColor = C.muted)
    AlertDialog(onDismissRequest = { ui.lyricsCard = null }, containerColor = C.light,
        title = { Text("Lyrics card", style = T.head) },
        text = {
            Column {
                Image(bmp.asImageBitmap(), "The card", Modifier.size(208.dp, 260.dp).align(Alignment.CenterHorizontally))
                Text("Tick the lines for the card (up to 12):", style = T.sub, modifier = Modifier.padding(vertical = 6.dp))
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(words.size) { i ->
                        val wd = words[i]
                        val on = wd in picked
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, { if (it) { if (picked.size < 12) picked += wd } else picked -= wd },
                                enabled = on || picked.size < 12, colors = box)
                            Text(wd, style = T.body)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Row {
                    TextButton({ if (!sharePicture(ctx, bmp, "${t.title} lyrics", "com.instagram.android")) Music.toast("Instagram isn't on this phone") }) {
                        Text("Instagram", color = C.accent) }
                    TextButton({ if (!sharePicture(ctx, bmp, "${t.title} lyrics", "com.facebook.katana")) Music.toast("Facebook isn't on this phone") }) {
                        Text("Facebook", color = C.accent) }
                }
                Row {
                    TextButton({ Music.toast(if (savePicture(bmp, "${t.title} lyrics")) "Saved to Pictures" else "Couldn't save it") }) {
                        Text("Save", color = C.muted) }
                    TextButton({ sharePicture(ctx, bmp, "${t.title} lyrics", null) }) { Text("Messages & more…", color = C.accent) }
                }
            }
        },
        dismissButton = { TextButton({ ui.lyricsCard = null }) { Text("Close", color = C.muted) } })
}
