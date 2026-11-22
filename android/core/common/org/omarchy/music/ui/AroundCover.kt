package org.omarchy.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.omarchy.music.Prefs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ───────────────────────────── Around the cover ─────────────────────────────
// Settings › Personalization › Around the cover: thirty visualizers made to go round the big cover on Now playing,
// never over it: they start at the cover's edge and move outward, so the cover stays whole. Each follows the cover's
// shape as the layout gives it (square, rounded, very round, a circle like a record) and its size. With one on, the
// middle visualizers (the ones drawn over the cover) aren't drawn; the ones along the bottom still are.

/** The thirty, in the menu's order: (key, name). */
val AROUND_STYLES: List<Pair<String, String>> = listOf(
    "ar-glow" to "Glow", "ar-bars" to "Bars", "ar-fine" to "Fine lines", "ar-blocks" to "Blocks", "ar-wave" to "Wave",
    "ar-twinwave" to "Twin wave", "ar-dots" to "Dots", "ar-orbits" to "Orbits", "ar-comet" to "Comet", "ar-twin" to "Twin comets",
    "ar-ripples" to "Ripples", "ar-echo" to "Echo", "ar-neon" to "Neon frame", "ar-march" to "Marching lights", "ar-spectrum" to "Spectrum ring",
    "ar-spikes" to "Spikes", "ar-flames" to "Flames", "ar-petals" to "Petals", "ar-rays" to "Sun rays", "ar-corona" to "Corona",
    "ar-sparkle" to "Sparkles", "ar-lightning" to "Lightning", "ar-heart" to "Heartbeat", "ar-ribbon" to "Ribbon", "ar-beads" to "Beads",
    "ar-sides" to "Side bars", "ar-topbottom" to "Above and below", "ar-halo" to "Halo", "ar-shadow" to "Pulse shadow", "ar-burst" to "Particles",
)

/** The thirty, and the ones you made from them (in the visualizer maker, with Around the cover on): Around the cover's
 *  menu. Yours made from these are in no other visualizer menu. */
val AROUND_ALL: List<Pair<String, String>> get() = AROUND_STYLES +
    numbered(MyVis.styles().filter { it.base.startsWith("ar-") }.map { it.key to it.name }).map { (k, n) -> k to "$n (yours)" }

object Around {
    /** The style on ("" none): its key, kept as the Now playing visualizer's are, each device its own. */
    const val PREF = "np_vis_around"
    fun style(): String = Prefs.text(PREF).takeIf { k -> AROUND_ALL.any { it.first == k } }.orEmpty()
    fun on(): Boolean = style().isNotEmpty()
    fun set(k: String) = Prefs.setText(PREF, k)
    fun name(k: String): String = AROUND_ALL.firstOrNull { it.first == k }?.second ?: k
    /** One of the thirty, or yours made from one. */
    fun isAround(k: String): Boolean = k.startsWith("ar-") || MyVis.style(k)?.base?.startsWith("ar-") == true
    /** The visualizer maker opened from Around the cover's menu: it starts as one of these. */
    var makeNext = false
    /** How far round the cover they reach, as a share of the cover's side (each side). */
    const val REACH = 0.32f
}

/** The cover's edge: a rounded rectangle (a circle when its corners are half its side), walked round clockwise from
 *  the top's middle. at(t) puts the point at t (0 to 1 round) and the way out from it in out: x, y, nx, ny. */
private class Rim(val cx: Float, val cy: Float, val h: Float, rc0: Float) {
    val rc = rc0.coerceIn(0f, h)
    private val flat = 2f * (h - rc)
    private val arc = (PI / 2 * rc).toFloat()
    val total = 4 * flat + 4 * arc
    // Corner, side, corner, side…: top right, right, bottom right, bottom, bottom left, left, top left, and the top's
    // left half. A corner: its middle and the angle it starts at; a side: where it starts, which way it runs, and out.
    private val corners = arrayOf(floatArrayOf(cx + h - rc, cy - h + rc, -90f), floatArrayOf(cx + h - rc, cy + h - rc, 0f),
        floatArrayOf(cx - h + rc, cy + h - rc, 90f), floatArrayOf(cx - h + rc, cy - h + rc, 180f))
    private val sides = arrayOf(floatArrayOf(cx + h, cy - h + rc, 0f, 1f, 1f, 0f), floatArrayOf(cx + h - rc, cy + h, -1f, 0f, 0f, 1f),
        floatArrayOf(cx - h, cy + h - rc, 0f, -1f, -1f, 0f), floatArrayOf(cx - h + rc, cy - h, 1f, 0f, 0f, -1f))
    /** How far round it has turned (a style of yours with Spin): everything drawn along it moves round with it. */
    var turn = 0f
    fun at(t: Float, out: FloatArray) {
        var d = ((((t + turn) % 1f) + 1f) % 1f) * total
        // the top's right half
        val half = flat / 2f
        if (d <= half) { set(out, cx + d, cy - h, 0f, -1f); return }; d -= half
        for (k in 0 until 4) {
            if (d <= arc && arc > 0f) {
                val a = Math.toRadians((corners[k][2] + 90f * (d / arc)).toDouble())
                val nx = cos(a).toFloat(); val ny = sin(a).toFloat()
                set(out, corners[k][0] + nx * rc, corners[k][1] + ny * rc, nx, ny); return
            }
            d -= arc
            val len = if (k == 3) half else flat
            if (d <= len) { val sd = sides[k]; set(out, sd[0] + sd[2] * d, sd[1] + sd[3] * d, sd[4], sd[5]); return }
            d -= len
        }
        set(out, cx, cy - h, 0f, -1f)
    }
    private fun set(o: FloatArray, x: Float, y: Float, nx: Float, ny: Float) { o[0] = x; o[1] = y; o[2] = nx; o[3] = ny }
    /** The edge moved out by off (and its corners rounded with it). */
    fun outline(off: Float): Path = Path().apply {
        addRoundRect(RoundRect(cx - h - off, cy - h - off, cx + h + off, cy + h + off, CornerRadius(rc + off)))
    }
}

/** A level for a point round the cover: the bass at the top's middle, the treble at the bottom, the same both sides. */
private fun levelAt(s: VisState, t: Float): Float {
    val f = ((t % 1f) + 1f) % 1f
    val m = if (f < 0.5f) f * 2f else (1f - f) * 2f
    val n = s.levels.size
    return s.levels[(m * (n - 1)).toInt().coerceIn(0, n - 1)].coerceIn(0f, 1f)
}

private fun hash(i: Int): Float { var x = i * 374761393 + 668265263; x = (x xor (x ushr 13)) * 1274126177; return ((x xor (x ushr 16)) and 0xFFFF) / 65535f }

/** Draws [style] (one of the thirty, or yours made from one) round a cover of [side] px across (corner radius [corner]
 *  px; side / 2 for a circle) at the middle of this space. alpha: how strongly it shows. */
private fun DrawScope.drawAround(style: String, s: VisState, pal: VisPalette, side: Float, corner: Float, alpha: Float) {
    val mine = MyVis.style(style)
    drawAroundBase(mine?.base ?: style, mine?.amp ?: 1f, mine?.spin ?: 0f, s, pal, side, corner, alpha)
}

/** The visualizer maker's preview of one of these you're making: a cover in the layout's shape ([shape], the Cover's
 *  shape setting) in the middle, filled with [cover], and [base] round it with your Strength and Spin. */
internal fun DrawScope.drawAroundMaking(base: String, amp: Float, spin: Float, s: VisState, pal: VisPalette, shape: String, cover: Brush) {
    val side = min(size.width, size.height) / (1f + 2f * Around.REACH) * 0.92f
    val corner = when (shape) { "square" -> 0f; "soft" -> side * 0.18f; "circle" -> side / 2f; else -> side * 0.07f }
    drawAroundBase(base, amp, spin, s, pal, side, corner, 0.95f)
    drawRoundRect(cover, Offset(size.width / 2f - side / 2f, size.height / 2f - side / 2f), Size(side, side), CornerRadius(corner))
}

private fun DrawScope.drawAroundBase(style: String, amp: Float, spin: Float, s: VisState, pal: VisPalette, side: Float, corner: Float, alpha: Float) {
    // Strength: the levels made bigger or smaller while it's drawn, then put back
    val keptLevels = if (amp != 1f) s.levels.copyOf() else null
    if (keptLevels != null) { for (i in s.levels.indices) s.levels[i] = (s.levels[i] * amp).coerceIn(0f, 1f) }
    try { drawAroundStyle(style, spin, amp, s, pal, side, corner, alpha) }
    finally { keptLevels?.copyInto(s.levels) }
}

private fun DrawScope.drawAroundStyle(style: String, spin: Float, amp: Float, s: VisState, pal: VisPalette, side: Float, corner: Float, alpha: Float) {
    val cx = size.width / 2f; val cy = size.height / 2f
    val rim = Rim(cx, cy, side / 2f, corner)
    rim.turn = s.phase * spin * 0.08f
    val reach = side * Around.REACH
    val p = FloatArray(4); val q = FloatArray(4)
    val ph = s.phase
    val bass = (s.bass * amp).coerceIn(0f, 1f)   // (the bass is the visualizer's own: scaled here, not changed)
    fun col(t: Float, lv: Float, a: Float = 1f) = pal.at(((t % 1f) + 1f) % 1f, lv).copy(alpha = (a * alpha).coerceIn(0f, 1f))
    fun line(n: Int, len: (Float, Float) -> Float, w: Float, start: Float = 2f) {
        for (i in 0 until n) { val t = i / n.toFloat(); rim.at(t, p); val lv = levelAt(s, t); val l = len(t, lv)
            if (l < 0.5f) continue
            drawLine(col(t, lv), Offset(p[0] + p[2] * start, p[1] + p[3] * start), Offset(p[0] + p[2] * (start + l), p[1] + p[3] * (start + l)), w, StrokeCap.Round) }
    }
    fun wave(n: Int, off: (Float, Float) -> Float): Path { val path = Path()
        for (i in 0..n) { val t = i / n.toFloat(); rim.at(t, p); val o = off(t, levelAt(s, t))
            if (i == 0) path.moveTo(p[0] + p[2] * o, p[1] + p[3] * o) else path.lineTo(p[0] + p[2] * o, p[1] + p[3] * o) }
        path.close(); return path }
    when (style) {
        "ar-glow" -> for (k in 6 downTo 1) drawPath(rim.outline(k * reach * 0.05f * (0.4f + bass)), col(0.2f, bass, 0.10f + 0.05f * (6 - k)), style = Stroke(reach * 0.07f * k))
        "ar-bars" -> line(96, { _, lv -> lv * reach }, side * 0.012f)
        "ar-fine" -> line(220, { _, lv -> lv * reach * 0.9f }, side * 0.004f)
        "ar-blocks" -> for (i in 0 until 64) { val t = i / 64f; rim.at(t, p); val lv = levelAt(s, t); val n = (lv * 6).toInt(); val b = side * 0.022f
            for (j in 0 until n) { val d = 4f + j * b * 1.35f; drawCircle(col(t, j / 6f), b * 0.45f, Offset(p[0] + p[2] * (d + b / 2), p[1] + p[3] * (d + b / 2))) } }
        "ar-wave" -> drawPath(wave(240) { _, lv -> 4f + lv * reach * 0.8f }, col(ph * 0.05f, bass), style = Stroke(side * 0.012f))
        "ar-twinwave" -> { drawPath(wave(240) { t, lv -> 4f + lv * reach * (0.5f + 0.3f * sin(t * 20f + ph * 2f)) }, col(0f, bass), style = Stroke(side * 0.009f))
            drawPath(wave(240) { t, lv -> 4f + lv * reach * (0.5f + 0.3f * cos(t * 20f - ph * 2f)) }, col(0.5f, bass, 0.7f), style = Stroke(side * 0.009f)) }
        "ar-dots" -> for (i in 0 until 72) { val t = i / 72f; rim.at(t, p); val lv = levelAt(s, t); val d = reach * 0.18f
            drawCircle(col(t, lv), side * (0.004f + lv * 0.014f), Offset(p[0] + p[2] * d, p[1] + p[3] * d)) }
        "ar-orbits" -> for (k in 0 until 6) { val sp = 0.04f + k * 0.017f; val t = (ph * sp + k / 6f) % 1f; val d = reach * (0.15f + 0.12f * k)
            for (j in 0 until 12) { rim.at(t - j * 0.006f, p); drawCircle(col(t, bass, 1f - j / 12f), side * (0.006f + bass * 0.01f) * (1f - j / 14f), Offset(p[0] + p[2] * d, p[1] + p[3] * d)) } }
        "ar-comet", "ar-twin" -> for (k in 0 until (if (style == "ar-twin") 2 else 1)) { val head = (ph * (0.12f + bass * 0.1f) + k * 0.5f) % 1f
            for (j in 0 until 50) { val t = head - j * 0.004f; rim.at(t, p); val d = reach * 0.12f
                drawCircle(col(t, bass, (1f - j / 50f)), side * 0.012f * (1f - j / 60f), Offset(p[0] + p[2] * d, p[1] + p[3] * d)) } }
        "ar-ripples" -> for (k in 0 until 5) { val f = ((ph * (0.25f + bass * 0.3f)) + k / 5f) % 1f
            drawPath(rim.outline(4f + f * reach), col(k / 5f, bass, (1f - f) * 0.8f), style = Stroke(side * 0.008f)) }
        "ar-echo" -> for (k in 1..4) drawPath(rim.outline(k * reach * 0.2f * (0.3f + bass)), col(k / 4f, bass, 1f - k / 5f), style = Stroke(side * 0.006f))
        "ar-neon" -> { drawPath(rim.outline(reach * 0.08f), col(0.3f, bass, 0.25f), style = Stroke(side * 0.05f * (0.5f + bass)))
            drawPath(rim.outline(reach * 0.08f), col(0.3f, bass), style = Stroke(side * 0.01f + bass * side * 0.02f)) }
        "ar-march" -> drawPath(rim.outline(reach * 0.15f), col(ph * 0.05f, bass), style = Stroke(side * (0.01f + bass * 0.012f), cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(side * 0.03f, side * 0.04f), -ph * side * 0.15f)))
        "ar-spectrum" -> for (i in 0 until 48) { val t = i / 48f; val lv = levelAt(s, t + 0.5f / 48f); val path = Path()
            for (j in 0..6) { rim.at(t + j / (48f * 6f), p); val d = 6f + lv * reach * 0.25f
                if (j == 0) path.moveTo(p[0] + p[2] * d, p[1] + p[3] * d) else path.lineTo(p[0] + p[2] * d, p[1] + p[3] * d) }
            drawPath(path, col(t, lv), style = Stroke(side * (0.01f + lv * 0.04f))) }
        "ar-spikes" -> for (i in 0 until 60) { val t = i / 60f; rim.at(t - 0.006f, p); rim.at(t + 0.006f, q); val lv = levelAt(s, t)
            val tip = FloatArray(4).also { rim.at(t, it) }; val l = 3f + lv * reach
            drawPath(Path().apply { moveTo(p[0], p[1]); lineTo(tip[0] + tip[2] * l, tip[1] + tip[3] * l); lineTo(q[0], q[1]); close() }, col(t, lv)) }
        "ar-flames" -> for (i in 0 until 70) { val t = i / 70f; rim.at(t - 0.007f, p); rim.at(t + 0.007f, q); val lv = levelAt(s, t)
            val tip = FloatArray(4).also { rim.at(t, it) }; val l = (4f + lv * reach) * (0.75f + 0.35f * sin(ph * 9f + i * 1.7f))
            val bend = sin(ph * 4f + i) * l * 0.25f
            drawPath(Path().apply { moveTo(p[0], p[1]); quadraticBezierTo(tip[0] + tip[2] * l * 0.6f - tip[3] * bend, tip[1] + tip[3] * l * 0.6f + tip[2] * bend, tip[0] + tip[2] * l, tip[1] + tip[3] * l)
                quadraticBezierTo(tip[0] + tip[2] * l * 0.4f, tip[1] + tip[3] * l * 0.4f, q[0], q[1]); close() }, col(t, lv, 0.85f)) }
        "ar-petals" -> for (i in 0 until 24) { val t = i / 24f; rim.at(t, p); val lv = levelAt(s, t); val l = side * 0.04f + lv * reach * 0.9f; val w = side * 0.05f
            val bx = p[0] + p[2] * (l / 2 + 3f); val by = p[1] + p[3] * (l / 2 + 3f)
            drawPath(Path().apply { moveTo(p[0], p[1]); quadraticBezierTo(bx - p[3] * w, by + p[2] * w, p[0] + p[2] * l, p[1] + p[3] * l)
                quadraticBezierTo(bx + p[3] * w, by - p[2] * w, p[0], p[1]); close() }, col(t, lv, 0.8f)) }
        "ar-rays" -> for (i in 0 until 48) { val t = i / 48f + ph * 0.004f; rim.at(t, p); val lv = levelAt(s, t); val l = reach * (0.3f + lv * 0.7f)
            drawLine(Brush.linearGradient(listOf(col(t, lv), col(t, lv, 0f)), Offset(p[0], p[1]), Offset(p[0] + p[2] * l, p[1] + p[3] * l)),
                Offset(p[0], p[1]), Offset(p[0] + p[2] * l, p[1] + p[3] * l), side * 0.008f) }
        "ar-corona" -> for (k in 12 downTo 1) drawPath(rim.outline(k * reach * 0.07f * (0.5f + bass)), col(0.4f, bass, 0.05f), style = Stroke(reach * 0.08f))
        "ar-sparkle" -> for (i in 0 until 60) { val t = hash(i); rim.at(t, p); val d = reach * (0.05f + 0.9f * hash(i + 999)); val tw = 0.5f + 0.5f * sin(ph * (2f + hash(i + 7) * 4f) + i)
            val lv = levelAt(s, t); val r = side * 0.006f * (0.4f + tw + lv); val c = Offset(p[0] + p[2] * d, p[1] + p[3] * d)
            drawLine(col(t, lv, tw), c - Offset(r * 2, 0f), c + Offset(r * 2, 0f), r * 0.5f); drawLine(col(t, lv, tw), c - Offset(0f, r * 2), c + Offset(0f, r * 2), r * 0.5f) }
        "ar-lightning" -> { val hit = (1f - (s.sinceBeat / 0.4f)).coerceIn(0f, 1f) * (0.4f + bass); if (hit > 0.02f) {
            val seed = (ph * 2f).toInt(); val start = hash(seed); val path = Path()
            for (j in 0..40) { val t = start + j * 0.012f; rim.at(t, p); val d = 6f + reach * 0.5f * hash(seed * 31 + j) * hit
                if (j == 0) path.moveTo(p[0] + p[2] * d, p[1] + p[3] * d) else path.lineTo(p[0] + p[2] * d, p[1] + p[3] * d) }
            drawPath(path, col(start, hit, hit), style = Stroke(side * 0.008f)); drawPath(path, Color.White.copy(alpha = hit * alpha * 0.6f), style = Stroke(side * 0.003f)) } }
        "ar-heart" -> { val beat = (1f - s.sinceBeat / 0.35f).coerceIn(0f, 1f)
            drawPath(rim.outline(4f + beat * reach * 0.35f), col(0.1f, beat), style = Stroke(side * (0.008f + beat * 0.02f)))
            drawPath(rim.outline(4f + beat * reach * 0.7f), col(0.1f, beat, beat * 0.5f), style = Stroke(side * 0.005f)) }
        "ar-ribbon" -> drawPath(wave(300) { t, lv -> 4f + reach * 0.45f * (0.5f + 0.5f * sin(t * 14f * PI.toFloat() + ph * 2f)) * (0.4f + lv) }, col(ph * 0.03f, bass), style = Stroke(side * 0.014f, cap = StrokeCap.Round))
        "ar-beads" -> for (i in 0 until 40) { val t = i / 40f + ph * 0.01f; rim.at(t, p); val lv = levelAt(s, t); val d = reach * 0.22f
            drawCircle(col(t, lv), side * (0.01f + 0.018f * lv) * (if (i % 2 == 0) 1f else 0.6f), Offset(p[0] + p[2] * d, p[1] + p[3] * d)) }
        "ar-sides", "ar-topbottom" -> { val n = 24; val horizontal = style == "ar-sides"
            for (side2 in listOf(-1f, 1f)) for (i in 0 until n) { val f = (i + 0.5f) / n; val lv = s.levels[(f * (s.levels.size - 1)).toInt()].coerceIn(0f, 1f)
                val along = -rim.h + rim.rc + f * 2f * (rim.h - rim.rc); val l = 4f + lv * reach * 0.9f; val c = col(f, lv)
                if (horizontal) { val x = cx + side2 * (rim.h + 4f); drawLine(c, Offset(x, cy + along), Offset(x + side2 * l, cy + along), side * 0.016f, StrokeCap.Round) }
                else { val y = cy + side2 * (rim.h + 4f); drawLine(c, Offset(cx + along, y), Offset(cx + along, y + side2 * l), side * 0.016f, StrokeCap.Round) } } }
        "ar-halo" -> for (k in 0 until 8) { val lv = s.levels[(k * (s.levels.size - 1) / 7)].coerceIn(0f, 1f)
            drawPath(rim.outline(5f + k * reach * 0.11f), col(k / 8f, lv, 0.2f + lv * 0.8f), style = Stroke(side * 0.004f * (1f + lv * 2f))) }
        "ar-shadow" -> for (k in 10 downTo 1) { val o = k * reach * 0.06f * (0.4f + bass * 1.2f)
            drawRoundRect(col(0.5f, bass, 0.06f), Offset(cx - rim.h - o, cy - rim.h - o + reach * 0.15f), Size(2 * (rim.h + o), 2 * (rim.h + o)), CornerRadius(rim.rc + o)) }
        "ar-burst" -> for (k in 0 until 90) { val t = hash(k); val life = ((ph * (0.3f + 0.4f * hash(k + 50)) + hash(k + 99)) % 1f); rim.at(t, p)
            val lv = levelAt(s, t); val d = 4f + life * reach * (0.6f + lv)
            drawCircle(col(t, lv, (1f - life)), side * 0.006f * (0.5f + lv), Offset(p[0] + p[2] * d, p[1] + p[3] * d)) }
    }
}

/** Around the cover on Now playing: a space bigger than the cover (it reaches out round it), centred on it, drawn under
 *  it. side: the cover's size; corner: its corner radius (side / 2 for a circle). It takes no taps. */
@Composable
fun AroundTheCover(style: String, s: VisState, col: Color, strength: Float, side: Dp, corner: Dp, modifier: Modifier = Modifier) {
    val colorKey = Vis.color()
    Canvas(modifier.requiredSize(side * (1f + 2f * Around.REACH))) {
        if (s.frame < 0) return@Canvas   // read, so it's drawn again each frame
        val pal = VisPalette.of(style, colorKey, col, s.phase)
        drawAround(style, s, pal, side.toPx(), corner.toPx(), (0.55f + strength * 0.6f).coerceIn(0.5f, 1f))
    }
}

/** The cover's corner radius for the layout's Cover's shape, at this size. */
fun coverCorner(shape: String, side: Dp): Dp = when (shape) { "square" -> 0.dp; "soft" -> 32.dp; "circle" -> side / 2; else -> 14.dp }

/** One in its menu: a cover in the layout's shape with it going round it, moving. colour: a VIS_COLORS key to show it
 *  in ("" the one it would be drawn in). */
@Composable
fun AroundPreview(style: String, colour: String = "") {
    val s = rememberSampleVis()
    val shapeKey = UiLayouts.current.x("artShape")
    val cover = if (onPhone) 120.dp else 130.dp
    val corner = coverCorner(shapeKey, cover)
    Box(Modifier.size(PW, PH).clip(RoundedCornerShape(12.dp))
        .background(C.gradient?.takeIf { it.size > 1 }?.let { Brush.linearGradient(it) } ?: Brush.linearGradient(listOf(C.bg, C.bg)))
        .border(1.dp, C.line, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.requiredSize(cover * (1f + 2f * Around.REACH))) {
            if (s.frame < 0) return@Canvas
            val pal = VisPalette.of(style, if (colour.isEmpty()) Vis.color() else "!$colour", VIS_LOOK, s.phase)
            drawAround(style, s, pal, cover.toPx(), corner.toPx(), 0.9f)
        }
        Box(Modifier.size(cover).clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(C.accent, C.light, C.accent.copy(alpha = 0.5f)))))
    }
}

/** Around the cover's menu (Settings): the thirty and yours made from them, each shown round a cover in the layout's
 *  shape; yours can be changed or deleted from the menu (⋯), and Make your own opens the visualizer maker with Around
 *  the cover on. */
@Composable
fun AroundPicker(current: String, picked: (String) -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        ChoicePill(AROUND_ALL, current, group = { if (it.startsWith("myvis-")) "Yours" else "Made for it" }, preview = { AroundPreview(it) },
            menu = { k, l -> if (k.startsWith("myvis-")) Favs.menu(Favs.VIS, k, l) else emptyList() }) { Around.set(it); picked(it) }
        Pill("Make your own", quiet = true) {
            Ui.current?.let { Around.makeNext = true; it.makerEdit = null; it.sheet = "vismaker" }
        }
    }
}
