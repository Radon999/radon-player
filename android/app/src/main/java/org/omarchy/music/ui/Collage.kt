package org.omarchy.music.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.omarchy.music.Music
import org.omarchy.music.Stats

/** Collage…: your top songs, albums or artists as covers in a square grid (5×5 up to 25×25), each with
 *  its name and plays, to save to your pictures or share. The desktop's collage_png, drawn the same way. */
object Collage {
    val SIZES = listOf(5, 10, 15, 20, 25)

    data class Item(val title: String, val sub: String, val plays: Int, val model: Any?)

    /** kind: "songs", "albums" or "artists" (Most played's period), "albums-all" or "artists-all" (all time). */
    fun period(kind: String) = if (kind.endsWith("-all")) "all" else Music.topPeriod

    fun items(kind: String): List<Item> = when (kind.removeSuffix("-all")) {
        "songs" -> Music.mostPlayed(period(kind)).map { Item(it.title, it.artist, it.plays, artModel(it, 600)) }
        "albums" -> Music.topAlbums(period(kind)).map { Item(it.title, it.artist, it.plays, artModel(it.tracks.first(), 600)) }
        else -> Music.topArtists(period(kind)).map { Item(it.name, "", it.plays, artistArt(it.name, it.thumb)) }
    }

    /** (blocking) The collage of the first n×n items. */
    fun draw(ctx: Context, items: List<Item>, n: Int, labels: Boolean, progress: (Int) -> Unit = {}): Bitmap {
        val cell = (2400 / n).coerceIn(96, 300)
        val side = cell * n
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF121214.toInt())
        val want = items.take(n * n)
        // The covers, a few at a time (Coil fetches and caches them, the phone's own files too).
        val pics = java.util.concurrent.Executors.newFixedThreadPool(6).let { pool ->
            try {
                want.mapIndexed { i, it ->
                    pool.submit<Bitmap?> {
                        val got = runCatching { cover(ctx, it.model, cell) }.getOrNull()
                        progress(i)
                        got
                    }
                }.map { runCatching { it.get() }.getOrNull() }
            } finally { pool.shutdown() }
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val big = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = cell * 0.105f; typeface = Typeface.DEFAULT_BOLD }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textSize = cell * 0.088f }
        for ((i, it) in want.withIndex()) {
            val x = (i % n) * cell.toFloat(); val y = (i / n) * cell.toFloat()
            val dst = RectF(x, y, x + cell, y + cell)
            val pic = pics[i]
            if (pic != null) {   // filled, the middle kept (a cover that isn't square is cut, not squashed)
                val s = minOf(pic.width, pic.height)
                val src = Rect((pic.width - s) / 2, (pic.height - s) / 2, (pic.width + s) / 2, (pic.height + s) / 2)
                c.drawBitmap(pic, src, dst, paint)
            } else {
                paint.color = 0xFF29292F.toInt(); c.drawRect(dst, paint)
            }
            if (!labels) continue
            val shade = Paint().apply {
                shader = LinearGradient(0f, y + cell * 0.45f, 0f, y + cell, 0x00000000, 0xD9000000.toInt(), Shader.TileMode.CLAMP)
            }
            c.drawRect(x, y + cell * 0.45f, x + cell, y + cell, shade)
            val pad = cell * 0.06f; val w = cell - 2 * pad
            val lines = listOfNotNull(it.title to big, it.sub.ifEmpty { null }?.let { s -> s to small },
                "%,d play%s".format(it.plays, if (it.plays != 1) "s" else "") to small)
            var yy = y + cell - pad - lines.sumOf { (_, p) -> (p.textSize * 1.25f).toDouble() }.toFloat()
            for ((t, p) in lines) {
                yy += p.textSize
                c.drawText(TextUtils.ellipsize(t, p, w, TextUtils.TruncateAt.END).toString(), x + pad, yy, p)
                yy += p.textSize * 0.25f
            }
        }
        return bmp
    }

    private fun cover(ctx: Context, model: Any?, px: Int): Bitmap? {
        model ?: return null
        val req = coil.request.ImageRequest.Builder(ctx).data(model).size(px * 2).allowHardware(false).build()
        val res = kotlinx.coroutines.runBlocking { coil.Coil.imageLoader(ctx).execute(req) }
        return (res as? coil.request.SuccessResult)?.drawable?.toBitmap()
    }
}

@Composable
fun CollageSheet(ui: Ui, kind: String) {
    val ctx = LocalContext.current
    var n by remember { mutableStateOf(Music.collageSize) }
    var labels by remember { mutableStateOf(true) }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("") }
    val items = remember(kind) { runCatching { Collage.items(kind) }.getOrDefault(emptyList()) }
    val name = mapOf("songs" to "Top songs", "albums" to "Top albums")[kind.removeSuffix("-all")] ?: "Top artists"
    val period = mapOf("week" to "This week", "month" to "This month", "year" to "This year")[Collage.period(kind)] ?: "All time"
    LaunchedEffect(n, labels) {
        Music.chooseCollageSize(n)
        if (items.isEmpty()) { status = "Nothing played yet to make a collage of"; return@LaunchedEffect }
        val want = minOf(items.size, n * n)
        status = "Getting $want pictures…"
        Music.bg({ Collage.draw(ctx, items, n, labels) }) { b, err ->
            if (b != null) {
                bmp = b
                status = "$name · $period · $want of ${items.size}" + if (items.size < n * n) " (only ${items.size}: the rest of the grid is empty)" else ""
            } else status = "Couldn't make it: ${err?.message?.take(100)}"
        }
    }
    FullScreen("Collage · $name", { ui.collageFor = null }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Collage.SIZES.forEach { k -> Chip("$k×$k", n == k) { n = k } }
                Chip("Names and plays", labels) { labels = !labels }
            }
            Text(status, style = T.sub)
            bmp?.let { b ->
                Image(b.asImageBitmap(), contentDescription = "The collage", modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val file = "${name} ${period} ${n}x$n".replace(' ', '-')
                    Pill("Share…") { if (!sharePicture(ctx, b, file, null)) Music.toast("Couldn't share it") }
                    Pill("Save to Pictures", quiet = true) {
                        Music.bg({ savePicture(b, file) }) { ok, _ -> Music.toast(if (ok == true) "Saved to Pictures" else "Couldn't save it") }
                    }
                }
            }
        }
    }
}
