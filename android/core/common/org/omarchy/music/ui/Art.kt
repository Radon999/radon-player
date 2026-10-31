package org.omarchy.music.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import org.omarchy.music.Music
import org.omarchy.music.Track
import org.omarchy.music.sizedThumb
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** The cover inside a song file (on the phone, or Android's thumbnail of it; on the PC, the file's own picture, else its
 *  folder's cover.jpg); fallback (a picture's path or address) when it has none. */
data class FileArt(val uri: String, val fallback: String = "")

/** What to draw for a song's art: the saved cover, the service's picture, or the file's own. */
fun artModel(t: Track, px: Int = 226): Any? {
    Music.lib.items[t.id]?.art?.takeIf { it.isNotEmpty() && File(it).exists() }?.let { return File(it) }
    fun pic(it: String): Any = if (it.startsWith("/") || (it.length > 2 && it[1] == ':')) File(it) else it
    val name = t.album.ifEmpty { t.title }
    org.omarchy.music.Pictures.cover(t.artist, name).takeIf { it.isNotEmpty() }?.let { return pic(it) }   // one you picked
    // One picked before 2.1.2 (its album's name may only look like this one's): after the song's own cover.
    val old = org.omarchy.music.Pictures.oldCover(t.artist, name)
    if (old.isNotEmpty() && t.file == null) return pic(old)
    if (t.thumb.isNotEmpty()) return sizedThumb(t.thumb, px)
    return t.file?.let { FileArt(it, old) }
}

/** Colour sums without Android's helpers (the same maths as android.graphics.Color and ColorUtils). */
object Rgb {
    fun red(c: Int) = (c shr 16) and 0xFF
    fun green(c: Int) = (c shr 8) and 0xFF
    fun blue(c: Int) = c and 0xFF
    fun alpha(c: Int) = (c ushr 24) and 0xFF
    fun rgb(r: Int, g: Int, b: Int) = argb(255, r, g, b)
    fun argb(a: Int, r: Int, g: Int, b: Int) = (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** hue 0..360, saturation and value 0..1. */
    fun toHsv(c: Int, out: FloatArray) {
        val r = red(c) / 255f; val g = green(c) / 255f; val b = blue(c) / 255f
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val d = mx - mn
        var h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        if (h < 0) h += 360f
        out[0] = h; out[1] = if (mx == 0f) 0f else d / mx; out[2] = mx
    }

    fun fromHsv(hsv: FloatArray, alpha: Int = 255): Int {
        val h = ((hsv[0] % 360f) + 360f) % 360f; val s = hsv[1].coerceIn(0f, 1f); val v = hsv[2].coerceIn(0f, 1f)
        val c = v * s; val x = c * (1 - kotlin.math.abs((h / 60f) % 2f - 1)); val m = v - c
        val (r, g, b) = when ((h / 60f).toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        return argb(alpha, ((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
    }

    /** hue 0..360, saturation and lightness 0..1 (as ColorUtils.colorToHSL). */
    fun toHsl(c: Int, out: FloatArray) {
        val r = red(c) / 255f; val g = green(c) / 255f; val b = blue(c) / 255f
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val d = mx - mn
        val l = (mx + mn) / 2f
        val h: Float; val s: Float
        if (mx == mn) { h = 0f; s = 0f } else {
            h = when (mx) { r -> ((g - b) / d) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f }
            s = d / (1f - kotlin.math.abs(2f * l - 1f))
        }
        out[0] = ((h * 60f) % 360f).let { if (it < 0) it + 360f else it }; out[1] = s; out[2] = l
    }

    fun fromHsl(hsl: FloatArray): Int {
        val h = hsl[0]; val s = hsl[1]; val l = hsl[2]
        val c = (1f - kotlin.math.abs(2 * l - 1f)) * s; val m = l - 0.5f * c
        val x = c * (1f - kotlin.math.abs((h / 60f % 2f) - 1f))
        val (r, g, b) = when ((h.toInt() / 60)) {
            0 -> Triple(c + m, x + m, m); 1 -> Triple(x + m, c + m, m); 2 -> Triple(m, c + m, x + m)
            3 -> Triple(m, x + m, c + m); 4 -> Triple(x + m, m, c + m); 5, 6 -> Triple(c + m, m, x + m)
            else -> Triple(0f, 0f, 0f)
        }
        return rgb((r * 255f).roundToInt(), (g * 255f).roundToInt(), (b * 255f).roundToInt())
    }
}

object Tint {
    /** The colour a cover mostly is: its most common hue, vivid pixels counting more than grey ones. */
    fun artColor(img: ImageBitmap): Int {
        val (px, w, h) = pixelsOf(img)
        val buckets = HashMap<Int, DoubleArray>()
        val hsv = FloatArray(3)
        for (y in 0 until 32) for (x in 0 until 32) {
            val p = px[(y * h / 32).coerceAtMost(h - 1) * w + (x * w / 32).coerceAtMost(w - 1)]
            val r = Rgb.red(p); val g = Rgb.green(p); val b = Rgb.blue(p)
            Rgb.toHsv(p, hsv)
            val hh = hsv[0] / 360f; val s = hsv[1]; val v = hsv[2]
            if (v < 0.1) continue   // black borders and shadows say nothing about the cover
            val wt = 0.2 + s * v
            val key = (((hh * 12).toInt() % 12) * 100) + (min(2, (s * 3).toInt()) * 10) + min(2, (v * 3).toInt())
            val acc = buckets.getOrPut(key) { DoubleArray(4) }
            acc[0] += r * wt; acc[1] += g * wt; acc[2] += b * wt; acc[3] += wt
        }
        val best = buckets.values.maxByOrNull { it[3] } ?: return Rgb.rgb(24, 24, 24)
        return Rgb.rgb((best[0] / best[3]).roundToInt(), (best[1] / best[3]).roundToInt(), (best[2] / best[3]).roundToInt())
    }

    /** The window and the player bar in the cover's colour, dark enough for the text. */
    fun shades(rgb: Int): Pair<Color, Color> {
        val hsl = FloatArray(3)
        Rgb.toHsl(rgb, hsl)
        hsl[1] = min(hsl[1], 0.6f)
        fun shade(l: Float) = Color(Rgb.fromHsl(floatArrayOf(hsl[0], hsl[1], l)))
        return shade(0.15f) to shade(0.09f)
    }

    /** A cover's main colour (null: no picture). */
    suspend fun rgbOf(model: Any?): Int? = loadImage(model, 64)?.let { artColor(it) }

    suspend fun of(model: Any?): Pair<Color, Color>? = rgbOf(model)?.let { shades(it) }
}
