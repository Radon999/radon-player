package org.omarchy.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
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
        "eps" -> Music.topAlbums(period(kind)).filter { Music.releaseKind(it.title, it.tracks, byCount = false) == "ep" }.map { Item(it.title, it.artist, it.plays, artModel(it.tracks.first(), 600)) }
        "singles" -> Music.topAlbums(period(kind)).filter { al -> Music.releaseKind(al.title, al.tracks, byCount = false) == "single" }
            .map { Item(it.title, it.artist, it.plays, artModel(it.tracks.first(), 600)) }
        else -> Music.topArtists(period(kind)).map { Item(it.name, "", it.plays, artistArt(it.name, it.thumb)) }
    }

    /** (blocking) The collage of the first n×n items. */
    fun draw(items: List<Item>, n: Int, labels: Boolean, progress: (Int) -> Unit = {}): ImageBitmap {
        val cell = (2400 / n).coerceIn(96, 300)
        val side = cell * n
        val pt = Painter(side, side)
        pt.fill(0xFF121214.toInt())
        val want = items.take(n * n)
        // The covers, a few at a time (fetched and kept, the phone's own files too).
        val pics = java.util.concurrent.Executors.newFixedThreadPool(6).let { pool ->
            try {
                want.mapIndexed { i, it ->
                    pool.submit<ImageBitmap?> {
                        val got = runCatching { cover(it.model, cell * 2) }.getOrNull()
                        progress(i)
                        got
                    }
                }.map { runCatching { it.get() }.getOrNull() }
            } finally { pool.shutdown() }
        }
        val big = cell * 0.105f; val small = cell * 0.088f
        for ((i, it) in want.withIndex()) {
            val x = (i % n) * cell.toFloat(); val y = (i / n) * cell.toFloat()
            pt.picture(pics[i], x, y, cell.toFloat(), empty = 0xFF29292F.toInt())
            if (!labels) continue
            pt.gradient(x, y + cell * 0.45f, x + cell, y + cell, 0x00000000, 0xD9000000.toInt())
            val pad = cell * 0.06f; val w = cell - 2 * pad
            val lines = listOfNotNull(Triple(it.title, big, true), it.sub.ifEmpty { null }?.let { s -> Triple(s, small, false) },
                Triple("%,d play%s".format(it.plays, if (it.plays != 1) "s" else ""), small, false))
            var yy = y + cell - pad - lines.sumOf { (_, sz, _) -> (sz * 1.25f).toDouble() }.toFloat()
            for ((t, sz, bold) in lines) {
                yy += sz
                pt.text(t, sz, bold, if (bold) 0xFFFFFFFF.toInt() else 0xCCFFFFFF.toInt(), x + pad, yy, w)
                yy += sz * 0.25f
            }
        }
        return pt.img
    }

    internal fun cover(model: Any?, px: Int): ImageBitmap? {
        model ?: return null
        return kotlinx.coroutines.runBlocking { loadImage(model, px) }
    }
}

@Composable
fun CollageSheet(ui: Ui, kind: String) {
    var n by remember { mutableStateOf(Music.collageSize) }
    var labels by remember { mutableStateOf(true) }
    var bmp by remember { mutableStateOf<ImageBitmap?>(null) }
    var status by remember { mutableStateOf("") }
    val items = remember(kind) { runCatching { Collage.items(kind) }.getOrDefault(emptyList()) }
    val name = mapOf("songs" to "Top songs", "albums" to "Top albums", "eps" to "Top EPs", "singles" to "Top singles")[kind.removeSuffix("-all")] ?: "Top artists"
    val period = mapOf("week" to "This week", "month" to "This month", "year" to "This year")[Collage.period(kind)] ?: "All time"
    LaunchedEffect(n, labels) {
        Music.chooseCollageSize(n)
        if (items.isEmpty()) { status = "Nothing played yet to make a collage of"; return@LaunchedEffect }
        val want = minOf(items.size, n * n)
        status = "Getting $want pictures…"
        Music.bg({ Collage.draw(items, n, labels) }) { b, err ->
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
                Image(b, contentDescription = "The collage", modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val file = "${name} ${period} ${n}x$n".replace(' ', '-')
                    Pill(SHARE_PICTURE) { if (!sharePicture(b, file, null)) Music.toast("Couldn't share it") }
                    Pill("Save to Pictures", quiet = true) {
                        Music.bg({ savePicture(b, file) }) { ok, _ -> Music.toast(if (ok == true) "Saved to Pictures" else "Couldn't save it") }
                    }
                }
            }
        }
    }
}

/** Share card…: your top 15 (songs, albums or artists, or by listening time) on one wide card like Wrapped's summary
 *  (1600×1000): the period and what it is at the top, the 15 in two columns (each with its cover, name, artist and
 *  plays or time), and the total time at the foot. To share or save. */
object Card {
    data class Row(val title: String, val sub: String, val value: String, val model: Any?)

    const val W = 1600
    const val H = 1000

    fun draw(title: String, whenText: String, rows: List<Row>, lead: String?): ImageBitmap {
        val w = W; val h = H
        val pt = Painter(w, h)
        val list = rows.take(15)
        val pics = list.map { runCatching { Collage.cover(it.model, 160) }.getOrNull() }
        val head = runCatching { Collage.cover(list.firstOrNull()?.model, 300) }.getOrNull()
        // Wrapped's card colours: a dark card, lit a little in the top song's colour
        val rgb = (head ?: pics.firstOrNull())?.let { runCatching { Tint.artColor(it) }.getOrNull() } ?: 0xFF5A3C8C.toInt()
        val hsl = FloatArray(3); Rgb.toHsl(rgb, hsl)
        val s = hsl[1].coerceIn(0.25f, 0.6f)
        pt.gradient(0f, 0f, w.toFloat(), h.toFloat(), Rgb.fromHsl(floatArrayOf(hsl[0], s, 0.30f)), Rgb.fromHsl(floatArrayOf(hsl[0], s * 0.6f, 0.11f)))
        fun text(t: String, size: Float, bold: Boolean, alpha: Int, x: Float, y: Float, width: Float, right: Boolean = false) =
            pt.text(t, size, bold, Rgb.argb(alpha, 255, 255, 255), x, y + size, width, right)
        // The top: the first one's picture in a circle, the period and what this is
        pt.picture(head, 60f, 50f, 120f, 60f, empty = 0x1FFFFFFF)
        text(whenText.uppercase() + " · MOST PLAYED", 30f, true, 200, 205f, 62f, w - 280f)
        text(title, 58f, true, 255, 205f, 104f, w - 280f)
        pt.rect(60f, 196f, 160f, 202f, Rgb.argb(220, 255, 255, 255))
        // The 15, in two columns (1–8 and 9–15)
        val top = 230f; val rowH = 80f; val colW = (w - 120f - 60f) / 2f
        list.forEachIndexed { i, r ->
            val col = if (i < 8) 0 else 1
            val x = 60f + col * (colW + 60f)
            val y = top + (if (col == 0) i else i - 8) * rowH
            text("${i + 1}", 30f, true, 170, x, y + 16f, 52f)
            pt.picture(pics.getOrNull(i), x + 58f, y + 4f, 62f, 8f, empty = 0x1FFFFFFF)
            val name = if (r.sub.isNotEmpty()) "${r.title} - ${r.sub}" else r.title
            text(name, 30f, true, 255, x + 136f, y + 16f, colW - 136f - 170f)
            text(r.value, 28f, true, 220, x + colW - 160f, y + 18f, 160f, right = true)
        }
        // The foot: the total listening time (for the time cards), and the app
        if (lead != null) {
            text("TOTAL", 24f, false, 170, 60f, h - 120f, 300f)
            text(lead, 44f, true, 255, 60f, h - 90f, 600f)
        }
        text("Radon Player", 26f, true, 128, w - 460f, h - 60f, 400f, right = true)
        return pt.img
    }
}

@Composable
fun CardSheet(ui: Ui, kind: String) {
    var bmp by remember { mutableStateOf<ImageBitmap?>(null) }
    var status by remember { mutableStateOf("Making it…") }
    val period = mapOf("week" to "This week", "month" to "This month", "year" to "This year")[Music.topPeriod] ?: "All time"
    val title = mapOf("songs" to "My top songs", "albums" to "My top albums", "artists" to "My top artists",
        "time_songs" to "My songs by time", "time_albums" to "My albums by time")[kind] ?: "My artists by time"
    LaunchedEffect(kind) {
        Music.bg({
            var lead: String? = null
            val rows = when (kind) {
                "time", "time_artists" -> Stats.time[Music.topPeriod]?.let { t -> lead = fmtHours(t.secs); t.artists.take(15).map { (n, v) -> Card.Row(n, "", fmtHours(v), artistArt(n, "")) } }.orEmpty()
                "time_songs", "time_albums" -> Stats.time[Music.topPeriod]?.let { t -> lead = fmtHours(t.secs)
                    timeItemsOf(kind.removePrefix("time_"), t).take(15).map { Card.Row(it.title, it.artist, fmtHours(it.secs), artModel(it.tracks.first(), 600)) } }.orEmpty()
                "songs" -> Music.mostPlayed(Music.topPeriod).take(15).map { Card.Row(it.title, it.artist, "%,d".format(it.plays), artModel(it, 600)) }
                "albums" -> Music.topAlbums(Music.topPeriod).take(15).map { Card.Row(it.title, it.artist, "%,d".format(it.plays), artModel(it.tracks.first(), 600)) }
                else -> Music.topArtists(Music.topPeriod).take(15).map { Card.Row(it.name, "", "%,d".format(it.plays), artistArt(it.name, it.thumb)) }
            }
            if (rows.isEmpty()) null else Card.draw(title, period, rows, lead)
        }) { b, err -> bmp = b; status = if (b != null) "Share it to a story or a chat, or save it" else err?.message?.take(100) ?: "Nothing played in this time yet" }
    }
    FullScreen("Share card · $title", { ui.cardFor = null }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(status, style = T.sub)
            bmp?.let { b ->
                // The whole card on screen at once: as wide as fits, never taller than the window
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val ratio = Card.W.toFloat() / Card.H
                    val wide = if (maxWidth / ratio > 460.dp) 460.dp * ratio else maxWidth
                    Image(b, contentDescription = "The card", modifier = Modifier.width(wide).aspectRatio(ratio))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val file = "$title $period".replace(' ', '-')
                    Pill(SHARE_PICTURE) { if (!sharePicture(b, file, null)) Music.toast("Couldn't share it") }
                    Pill("Save to Pictures", quiet = true) {
                        Music.bg({ savePicture(b, file) }) { ok, _ -> Music.toast(if (ok == true) "Saved to Pictures" else "Couldn't save it") }
                    }
                }
            }
        }
    }
}
