package org.omarchy.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.omarchy.music.Music
import org.omarchy.music.Prefs
import org.omarchy.music.Spectrum
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** The visualizer's styles: each is drawn its own way. Its colour is picked apart from it (VIS_COLORS), for any style.
 *  Bars is the one it had from the start (and the default). */
val VIS_STYLES: List<Pair<String, String>> = listOf("bars" to "Bars", "mirror" to "Mirror", "wave" to "Wave", "circle" to "Circle",
    "led" to "LED", "glow" to "Glow", "neon" to "Neon", "ring" to "Ring", "particles" to "Particles", "scope" to "Scope",
    "terrain" to "Terrain", "dots" to "Dot matrix", "spectrogram" to "Spectrogram", "pulse" to "Pulse", "blocks" to "Blocks")

/** The colours, for any style: the look's own (its accent; on Now playing the cover's), a few blends, then plain ones
 *  (the colours that used to be styles of their own). */
val VIS_COLORS: List<Pair<String, String>> = listOf("auto" to "Look's colour", "rainbow" to "Rainbow", "twilight" to "Twilight",
    "fire" to "Fire", "ice" to "Ice") + Looks.NEON.map { (k, v) -> k to v.first }

/** Now playing's own visualizer, behind the cover: the slim bars it always had, none, or any of the styles. */
val NP_VIS_STYLES: List<Pair<String, String>> = listOf("slim" to "Slim bars", "off" to "Off") + VIS_STYLES

/** The visualizer's settings. The style stays in Music.visPreset (saved with the app's state); the rest are Prefs. */
object Vis {
    const val COLOR = "vis_color"     // the colour, a VIS_COLORS key
    const val NOW = "np_vis"          // Now playing's visualizer, an NP_VIS_STYLES key
    const val NOW_BG = "np_bg"        // Now playing's background: "blur" (the cover's colours, the cover blurred) or "plain" (black)

    /** The styles drawn around the middle of the space (the others stand on its bottom). */
    val CENTRED = setOf("circle", "ring", "particles", "pulse")

    /** The style; an old colour preset ("red"…) is Bars in that colour. */
    fun style(): String = Music.visPreset.let { p -> if (VIS_STYLES.any { it.first == p }) p else "bars" }
    fun color(): String = Prefs.text(COLOR).ifEmpty { Music.visPreset.takeIf { Looks.NEON.containsKey(it) } ?: "auto" }
    fun nowStyle(): String = Prefs.text(NOW, "slim").let { k -> if (NP_VIS_STYLES.any { it.first == k }) k else "slim" }
    fun plainNow(): Boolean = Prefs.text(NOW_BG) == "plain"

    /** Before 3.0 the colours were styles: a saved one becomes Bars with that colour. */
    fun migrate() {
        val p = Music.visPreset
        if (!Looks.NEON.containsKey(p)) return
        if (Prefs.text(COLOR).isEmpty()) Prefs.setText(COLOR, p)
        Music.chooseVisPreset("bars")
    }
}

private val RAINBOW = Array(64) { Color(hslToColor(it * 360f / 64f, 0.85f, 0.62f)) }
private val TW_A = Color(0xFF7DCFFF); private val TW_B = Color(0xFFBB9AF7)
private val FIRE_LOW = Color(0xFFE0261A); private val FIRE_HIGH = Color(0xFFFFE04A)
private val ICE_LOW = Color(0xFF2F6BFF); private val ICE_HIGH = Color(0xFFCFF8FF)

/** A colour for a spot in the picture: t is where along the spectrum (0 the bass … 1 the treble), lv how loud. */
class VisPalette(val key: String, private val base: Color, private val shift: Float) {
    private val neon = Looks.NEON[key]?.second
    /** One colour all over (the look's own or a plain one), not a blend. */
    val single = neon != null || key !in setOf("rainbow", "twilight", "fire", "ice")

    fun at(t: Float, lv: Float): Color = when (key) {
        "rainbow" -> { val f = ((t * 0.85f + shift) % 1f + 1f) % 1f; RAINBOW[(f * RAINBOW.size).toInt().coerceIn(0, RAINBOW.size - 1)] }
        "twilight" -> lerp(TW_A, TW_B, t.coerceIn(0f, 1f))
        "fire" -> lerp(FIRE_LOW, FIRE_HIGH, (lv * 0.8f + (1f - t) * 0.2f).coerceIn(0f, 1f))
        "ice" -> lerp(ICE_LOW, ICE_HIGH, (lv * 0.7f + t * 0.3f).coerceIn(0f, 1f))
        else -> neon ?: base
    }
    val main: Color get() = at(0.5f, 0.6f)
    /** Left to right across the spectrum's colours. */
    fun brush(alpha: Float): Brush = Brush.horizontalGradient(List(7) { at(it / 6f, 0.6f).copy(alpha = alpha) })

    companion object {
        /** Glow on the look's colour keeps the blue-to-violet it always had. */
        fun of(style: String, key: String, base: Color, phase: Float) =
            VisPalette(if (style == "glow" && key == "auto") "twilight" else key, base, phase * 0.05f)
    }
}

/** What the drawing works from: the levels eased frame to frame, the peaks, a short history (Terrain and Spectrogram),
 *  and the sparks and rings that the bass sets off (Particles, Pulse). Moved on by step() once a frame. */
class VisState {
    val levels = FloatArray(Spectrum.BANDS)
    val peaks = FloatArray(Spectrum.BANDS)
    val hist = Array(HIST) { FloatArray(Spectrum.BANDS) }
    private var head = 0
    private var histAcc = 0f
    var phase = 0f                              // seconds since it started, for things that drift
        private set
    var bass = 0f                               // the bass's level now
        private set
    private var bassSlow = 0f
    private var cool = 0f
    private var sinceLoud = 99f
    private var ambient = 0f
    // The sparks, around the middle, in units of half the smaller side.
    val px = FloatArray(MAXP); val py = FloatArray(MAXP); val vx = FloatArray(MAXP); val vy = FloatArray(MAXP)
    val life = FloatArray(MAXP); val pr = FloatArray(MAXP); val ph = FloatArray(MAXP)
    private var nextP = 0
    // The rings, as a part of half the smaller side, and how strong each still is.
    val ringR = FloatArray(MAXR); val ringA = FloatArray(MAXR)
    private var nextR = 0
    private val rnd = kotlin.random.Random(7)
    /** Ticks each frame something moved: the drawing reads it, so it's drawn again. */
    var frame by mutableIntStateOf(0)

    /** The levels age frames ago (0: the newest kept). */
    fun row(age: Int): FloatArray = hist[((head - age) % HIST + HIST) % HIST]
    /** How far towards the next history row we are (0 … 1), for a smooth scroll. */
    val histFrac: Float get() = (histAcc / HIST_STEP).coerceIn(0f, 1f)

    private fun spark(speed: Float) {
        val i = nextP; nextP = (nextP + 1) % MAXP
        val a = rnd.nextFloat() * 2f * PI.toFloat()
        val v = speed * (0.45f + rnd.nextFloat() * 0.75f)
        px[i] = cos(a) * 0.12f; py[i] = sin(a) * 0.12f
        vx[i] = cos(a) * v; vy[i] = sin(a) * v
        life[i] = 1f; pr[i] = rnd.nextFloat(); ph[i] = rnd.nextFloat()
    }

    /** Moves everything on by dt seconds towards the spectrum heard; true while anything still moves. */
    fun step(target: FloatArray, paused: Boolean, dt: Float): Boolean {
        val k = dt * 60f   // the easing was tuned at 60 frames a second
        val up = 1f - 0.3f.pow(k)
        val down = 1f - 0.7f.pow(k)
        var moving = false
        var loud = 0f
        val n = min(levels.size, target.size)
        for (b in 0 until n) {
            val want = if (paused) 0f else target[b]
            val have = levels[b]
            // Android hands over ~20 spectra a second: rise most of the way at once, fall quickly, eased between.
            val new = if (want > have) have + (want - have) * up else have - (have - want) * down
            if (abs(new - have) > 0.002f) moving = true
            levels[b] = new
            loud = max(loud, new)
            peaks[b] = if (new >= peaks[b]) new else max(0f, peaks[b] - 0.45f * dt)
        }
        phase += dt
        sinceLoud = if (loud > 0.01f) 0f else sinceLoud + dt
        histAcc += dt
        if (histAcc >= HIST_STEP) {
            histAcc = if (histAcc > HIST_STEP * 3) 0f else histAcc - HIST_STEP
            head = (head + 1) % HIST
            levels.copyInto(hist[head])
        }
        // The bass: a kick is a jump above its recent average.
        bass = (levels[0] + levels[1] + levels[2] + levels[3]) / 4f
        bassSlow += (bass - bassSlow) * (1f - 0.92f.pow(k))
        cool -= dt
        if (bass > bassSlow + 0.07f && bass > 0.22f && cool <= 0f) {
            cool = 0.14f
            repeat(8 + (24 * bass).toInt()) { spark(0.9f + bass * 1.4f) }
            ringR[nextR] = 0.12f; ringA[nextR] = 0.6f + 0.4f * bass; nextR = (nextR + 1) % MAXR
        }
        ambient += loud * dt * 22f
        while (ambient >= 1f) { ambient -= 1f; spark(0.25f) }
        var alive = false
        val drag = 0.975f.pow(k)
        for (i in 0 until MAXP) if (life[i] > 0f) {
            px[i] += vx[i] * dt; py[i] += vy[i] * dt
            vx[i] *= drag; vy[i] *= drag
            life[i] -= dt * 0.8f
            alive = true
        }
        for (i in 0 until MAXR) if (ringA[i] > 0f) {
            ringR[i] += dt * 0.55f; ringA[i] -= dt * 0.7f
            alive = true
        }
        return moving || alive || sinceLoud < HIST * HIST_STEP + 0.5f
    }

    companion object {
        const val HIST = 64              // rows of history kept
        const val HIST_STEP = 1f / 30f   // one row every 1/30 s: about 2 seconds of history
        const val MAXP = 160             // sparks at once at most
        const val MAXR = 8               // rings at once at most
    }
}

/** The spectrum, eased frame by frame into a VisState for drawing (null: the visualizer isn't allowed, or isn't there). */
@Composable
fun rememberVis(on: Boolean, failed: (String) -> Unit = {}): VisState? {
    val target = rememberSpectrum(on, failed) ?: return null
    val s = remember { VisState() }
    LaunchedEffect(target) {
        var last = 0L
        while (true) withFrameNanos { now ->
            val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(0.001f, 0.1f)
            last = now
            if (s.step(target, Music.paused, dt)) s.frame++
        }
    }
    return s
}

/** Draws a style. backdrop: behind Now playing (no solid backgrounds or faint unlit parts, everything to the bottom edge). */
fun DrawScope.drawVis(style: String, s: VisState, pal: VisPalette, fg: Color, backdrop: Boolean) {
    val lv = s.levels
    val n = lv.size
    val w = size.width
    val h = size.height
    val u = density
    val gap = w / n * 0.25f
    val bw = w / n - gap
    val base = if (backdrop) h else h * 0.8f
    val c = Offset(w / 2, h / 2)
    val half = min(w, h) / 2
    fun t(b: Int) = b / (n - 1f)
    fun band(lo: Int, hi: Int): Float { var sum = 0f; for (b in lo until hi) sum += lv[b]; return sum / (hi - lo) }
    when (style) {
        "mirror" -> {
            val mid = h / 2
            for (b in 0 until n) {
                val v = lv[b]; val bh = max(1f, v * mid * 0.9f)
                drawRect(pal.at(t(b), v).copy(alpha = 0.3f + 0.7f * v), Offset(b * (bw + gap), mid - bh), Size(bw, bh * 2))
            }
        }
        "wave" -> {
            val path = Path()
            val xs = FloatArray(n) { it * w / (n - 1) }
            val ys = FloatArray(n) { base - lv[it] * base * 0.85f }
            path.moveTo(0f, base); path.lineTo(xs[0], ys[0])
            for (i in 1 until n) { val mx = (xs[i - 1] + xs[i]) / 2; path.cubicTo(mx, ys[i - 1], mx, ys[i], xs[i], ys[i]) }
            path.lineTo(w, base); path.close()
            if (pal.single) drawPath(path, Brush.verticalGradient(listOf(pal.main.copy(alpha = 0.75f), pal.main.copy(alpha = 0.1f)),
                startY = base * 0.15f, endY = base))
            else drawPath(path, pal.brush(0.45f))
            drawPath(path, pal.brush(1f), style = Stroke(width = 5f))
        }
        "circle" -> {
            val bass = band(0, max(1, n / 6))
            val r0 = min(w, h) * (0.16f + 0.05f * bass)
            val reach = min(w, h) * 0.3f
            for (side in intArrayOf(1, -1)) for (b in 0 until n) {
                val v = lv[b]
                val ang = (-PI / 2 + side * (b + 0.5) / n * PI).toFloat()
                val dir = Offset(cos(ang), sin(ang))
                drawLine(pal.at(t(b), v).copy(alpha = 0.35f + 0.65f * v), c + dir * r0, c + dir * (r0 + 3f + v * reach),
                    strokeWidth = max(3f, (2 * PI * r0 / (n * 2) * 0.7).toFloat()), cap = StrokeCap.Round)
            }
            drawCircle(pal.main.copy(alpha = 0.18f + 0.4f * bass), r0 * 0.85f, c)
        }
        "led" -> {
            val rows = 18; val cell = base / rows; val hot = Color(0xFFF7768E)
            for (b in 0 until n) {
                val lit = (lv[b] * rows).roundToInt(); val x = b * (bw + gap)
                for (r in 0 until rows) {
                    val col = when {
                        r < lit -> (if (r >= rows * 0.8f) hot else pal.at(t(b), r / (rows - 1f))).copy(alpha = 0.95f)
                        backdrop -> continue
                        else -> fg.copy(alpha = 0.06f)
                    }
                    drawRect(col, Offset(x, base - (r + 1) * cell + 2f), Size(bw, cell - 4f))
                }
            }
        }
        "glow" -> for (b in 0 until n) {
            val v = lv[b]; val bh = max(2f, v * base * 0.95f); val x = b * (bw + gap)
            val col = pal.at(t(b), v)
            drawRect(col.copy(alpha = 0.10f + 0.25f * v), Offset(x - gap, base - bh - 6f), Size(bw + 2 * gap, bh + 6f))
            drawRect(Brush.verticalGradient(listOf(col, col.copy(alpha = 0.25f)), startY = base - bh, endY = base), Offset(x, base - bh), Size(bw, bh))
        }
        "neon" -> {   // black, the bars glowing, a hot top on the loud ones
            if (!backdrop) {
                drawRect(Color.Black, Offset.Zero, size)
                drawRect(pal.main.copy(alpha = 0.35f), Offset(0f, base + 1f), Size(w, 1.5f))
            }
            for (b in 0 until n) {
                val v = lv[b]; val bh = max(2f, v * base * 0.92f); val x = b * (bw + gap)
                val neon = pal.at(t(b), v)
                for ((spread, a) in NEON_HALO)
                    drawRect(neon.copy(alpha = a * (0.5f + v)), Offset(x - spread, base - bh - spread), Size(bw + 2 * spread, bh + spread))
                val hotTop = lerp(neon, Color.White, 0.45f + 0.4f * v)
                drawRect(Brush.verticalGradient(0f to hotTop, 0.25f to neon, 1f to lerp(neon, Color.Black, 0.5f),
                    startY = base - bh, endY = base), Offset(x, base - bh), Size(bw, bh))
                if (!backdrop) drawRect(neon.copy(alpha = 0.16f * (0.4f + v)), Offset(x, base + 4f), Size(bw, bh * 0.22f))
            }
        }
        "ring" -> {   // a slowly turning shape around a ring, pushed out by the music, with ticks inside it
            val r0 = half * (0.42f + 0.06f * s.bass)
            val reach = half * 0.45f
            val m = n * 2
            val rot = s.phase * 0.2f
            val path = Path()
            for (i in 0 until m) {
                val idx = if (i < n) i else m - 1 - i
                val a = rot + i * 2f * PI.toFloat() / m
                val r = r0 + lv[idx] * reach
                val x = c.x + cos(a) * r; val y = c.y + sin(a) * r
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            val sweep = Brush.sweepGradient(List(8) { pal.at((it % 7) / 6f, 0.6f) }, c)
            drawPath(path, pal.main.copy(alpha = 0.16f))
            drawPath(path, sweep, style = Stroke(width = 2.5f * u, join = StrokeJoin.Round))
            drawCircle(pal.main.copy(alpha = 0.35f), r0, c, style = Stroke(width = 1.5f * u))
            for (i in 0 until m) {
                val idx = if (i < n) i else m - 1 - i
                val v = lv[idx]
                val a = rot + i * 2f * PI.toFloat() / m
                val dir = Offset(cos(a), sin(a))
                val inner = r0 * 0.9f
                drawLine(pal.at(t(idx), v).copy(alpha = 0.35f + 0.65f * v), c + dir * inner, c + dir * (inner - 2f * u - v * half * 0.2f),
                    strokeWidth = 2f * u, cap = StrokeCap.Round)
            }
        }
        "particles" -> {   // sparks thrown out from the middle on each beat of the bass
            drawCircle(pal.main.copy(alpha = 0.12f + 0.35f * s.bass), half * (0.10f + 0.12f * s.bass), c)
            drawCircle(pal.main.copy(alpha = 0.06f + 0.12f * s.bass), half * (0.22f + 0.2f * s.bass), c)
            for (i in 0 until VisState.MAXP) {
                val l = s.life[i]
                if (l <= 0f) continue
                drawCircle(pal.at(s.ph[i], l).copy(alpha = l.coerceIn(0f, 1f)), (1.5f + 3.5f * s.pr[i]) * u * (0.6f + 0.4f * l),
                    Offset(c.x + s.px[i] * half, c.y + s.py[i] * half))
            }
        }
        "scope" -> {   // an oscilloscope's glowing line, made of the bands as waves
            val mid = if (backdrop) h * 0.55f else h * 0.45f
            val amp = h * 0.38f
            if (!backdrop) {
                val grid = fg.copy(alpha = 0.06f)
                for (i in 1..3) drawLine(grid, Offset(0f, mid + (i - 2) * amp * 0.6f), Offset(w, mid + (i - 2) * amp * 0.6f), 1f)
                for (i in 1..7) drawLine(grid, Offset(w * i / 8f, mid - amp), Offset(w * i / 8f, mid + amp), 1f)
            }
            val groups = 8; val per = n / groups
            val ga = FloatArray(groups) { g -> band(g * per, (g + 1) * per) }
            val pts = 140
            val path = Path()
            for (i in 0..pts) {
                val x = i / pts.toFloat()
                var y = 0f
                for (g in 0 until groups) y += ga[g] * sin(2f * PI.toFloat() * (g + 1) * 1.6f * x + s.phase * (3f + g * 1.7f) + g)
                y = (y / 2f).coerceIn(-1f, 1f)
                val env = 0.25f + 0.75f * sin(PI.toFloat() * x)
                val py = mid - y * amp * env
                if (i == 0) path.moveTo(0f, py) else path.lineTo(x * w, py)
            }
            drawPath(path, pal.brush(0.15f), style = Stroke(width = 10f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, pal.brush(0.35f), style = Stroke(width = 5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, pal.brush(1f), style = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        "terrain" -> {   // the last moments as lines going back into the distance, like mountains
            val rows = 22; val every = 2
            val top = if (backdrop) 0f else h * 0.06f
            val depth = base - top
            for (r in rows - 1 downTo 0) {
                val z = r / (rows - 1f)
                val row = s.row(r * every)
                val y0 = base - z * depth * 0.6f
                val ws = 1f - z * 0.45f
                val lift = depth * 0.4f * (1f - z * 0.5f)
                val path = Path()
                for (i in 0 until n) {
                    val x = w / 2 + (t(i) - 0.5f) * w * ws
                    val y = y0 - row[i] * lift
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, pal.at(1f - z, 0.6f).copy(alpha = 0.12f + 0.88f * (1f - z)),
                    style = Stroke(width = (1f + 1.5f * (1f - z)) * u, join = StrokeJoin.Round))
            }
        }
        "dots" -> {   // a grid of dots lit out from the middle line, up and down
            val cols = n / 2; val rows = 15; val mid = rows / 2
            val cw = w / cols
            val ch = (if (backdrop) h else h * 0.9f) / rows
            val cy = if (backdrop) h / 2 else h * 0.45f
            val r0 = min(cw, ch) * 0.34f
            for (cI in 0 until cols) {
                val v = (lv[cI * 2] + lv[cI * 2 + 1]) / 2
                val reach = v * (mid + 0.6f)
                val col = pal.at(cI / (cols - 1f), v)
                for (r in 0 until rows) {
                    val d = abs(r - mid)
                    val at = Offset(cI * cw + cw / 2, cy + (r - mid) * ch)
                    if (d < reach) drawCircle(col.copy(alpha = 1f - 0.5f * d / (mid + 1f)), r0 * (1f - 0.25f * d / (mid + 1f)), at)
                    else if (!backdrop) drawCircle(fg.copy(alpha = 0.06f), r0 * 0.55f, at)
                }
            }
        }
        "spectrogram" -> {   // the spectrum over time, scrolling left: the bass at the bottom, louder brighter
            val rows = n / 2; val cols = VisState.HIST
            val cw = w / (cols - 1)
            val area = if (backdrop) h else h * 0.92f
            val ch = area / rows
            val shift = s.histFrac * cw
            for (j in 0 until cols) {
                val row = s.row(cols - 1 - j)
                val x = j * cw - shift
                for (f in 0 until rows) {
                    val v = max(row[f * 2], row[f * 2 + 1])
                    if (v < 0.04f) continue
                    drawRect(pal.at(f / (rows - 1f), v).copy(alpha = (0.15f + v).coerceAtMost(1f)),
                        Offset(x, area - (f + 1) * ch), Size(cw + 0.5f, ch + 0.5f))
                }
            }
        }
        "pulse" -> {   // rings sent out on the bass, around a heart that beats with it
            for (i in 0 until VisState.MAXR) {
                val a = s.ringA[i]
                if (a <= 0f) continue
                drawCircle(pal.at(i / (VisState.MAXR - 1f), a).copy(alpha = (a * 0.8f).coerceIn(0f, 1f)), half * s.ringR[i], c,
                    style = Stroke(width = (1.5f + 5f * a) * u))
            }
            val mids = band(n / 6, n / 2); val highs = band(n / 2, n)
            drawCircle(pal.at(0f, s.bass).copy(alpha = 0.25f + 0.5f * s.bass), half * (0.10f + 0.14f * s.bass), c)
            drawCircle(pal.at(0.5f, mids).copy(alpha = 0.4f + 0.6f * mids), half * (0.27f + 0.12f * mids), c, style = Stroke(width = 3f * u))
            drawCircle(pal.at(1f, highs).copy(alpha = 0.25f + 0.6f * highs), half * (0.40f + 0.10f * highs), c, style = Stroke(width = 1.5f * u))
        }
        "blocks" -> {   // a hi-fi's equaliser: wide columns of blocks, a cap that falls slowly from each peak
            val groups = 16; val per = n / groups; val segs = 12
            val gw = w / groups; val bwid = gw * 0.8f; val sh = base * 0.94f / segs
            for (g in 0 until groups) {
                var v = 0f; var pk = 0f
                for (b in g * per until (g + 1) * per) { v = max(v, lv[b]); pk = max(pk, s.peaks[b]) }
                val lit = (v * segs).roundToInt()
                val x = g * gw + (gw - bwid) / 2
                val tg = g / (groups - 1f)
                for (sg in 0 until segs) {
                    val y = base - (sg + 1) * sh
                    val f = (sg + 1f) / segs
                    if (sg < lit) drawRoundRect(pal.at(tg, f).copy(alpha = 0.55f + 0.45f * f), Offset(x, y + sh * 0.12f), Size(bwid, sh * 0.76f), CornerRadius(3f * u))
                    else if (!backdrop) drawRoundRect(fg.copy(alpha = 0.05f), Offset(x, y + sh * 0.12f), Size(bwid, sh * 0.76f), CornerRadius(3f * u))
                }
                if (pk * segs > 0.3f) drawRoundRect(lerp(pal.at(tg, 1f), Color.White, 0.5f), Offset(x, base - pk * segs * sh - sh * 0.3f),
                    Size(bwid, sh * 0.28f), CornerRadius(2f * u))
            }
        }
        else -> for (b in 0 until n) {   // Bars: the one it had from the start
            val v = lv[b]
            val bh = max(2f, v * base * 0.95f)
            val x = b * (bw + gap)
            val col = pal.at(t(b), v)
            drawRect(col.copy(alpha = 0.35f + 0.65f * v), Offset(x, base - bh), Size(bw, bh))
            if (!backdrop) drawRect(col.copy(alpha = 0.12f * (0.4f + v)), Offset(x, base + 3f), Size(bw, bh * 0.25f))
        }
    }
}

private val NEON_HALO = listOf(7f to 0.06f, 4f to 0.10f, 2f to 0.18f)

/** A visualizer colour's little sample for the menu. */
fun visSwatch(key: String, accent: Color): Pair<Color, Brush> = Color(0xFF101014) to
    if (key == "auto") SolidColor(accent) else VisPalette(key, accent, 0f).let { p -> Brush.horizontalGradient(List(5) { p.at(it / 4f, 0.7f) }) }

/** A pill showing the choice, and a menu of the others (with a colour sample beside each when swatch gives one). */
@Composable
fun ChoicePill(options: List<Pair<String, String>>, current: String, prefix: String = "",
               swatch: ((String) -> Pair<Color, Brush>?)? = null, pick: (String) -> Unit) {
    Box {
        var open by remember { mutableStateOf(false) }
        val name = options.firstOrNull { it.first == current }?.second ?: options.firstOrNull()?.second.orEmpty()
        Pill("$prefix$name ▾", quiet = true) { open = true }
        DropdownMenu(open, { open = false }, containerColor = C.light) {
            options.forEach { (k, label) ->
                val text: @Composable () -> Unit = {
                    Text(label, style = T.body.copy(color = if (k == current) C.accent else C.fg,
                        fontWeight = if (k == current) FontWeight.SemiBold else FontWeight.Normal))
                }
                val sw = swatch?.invoke(k)
                if (sw != null) DropdownMenuItem(text = text, onClick = { open = false; pick(k) }, leadingIcon = { Swatch(sw.first, sw.second) })
                else DropdownMenuItem(text = text, onClick = { open = false; pick(k) })
            }
        }
    }
}

@Composable
private fun Swatch(bg: Color, dot: Brush) {
    Box(Modifier.size(20.dp).clip(R.pill).background(bg).border(1.dp, C.line, R.pill), contentAlignment = Alignment.Center) {
        Box(Modifier.size(10.dp).clip(R.pill).background(dot))
    }
}

/** The Visualizer tab: what's playing, drawn with its sound (Android's Visualizer on the player's own audio session, so
 *  only this app's sound, never the mic; on the PC the song decoded again in step). A style and, apart from it, a colour. */
@Composable
fun VisualizerTab() {
    var allowed by remember { mutableStateOf(hasPermission(Perm.MIC)) }
    val ask = rememberPermission { allowed = it }
    if (!allowed) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(SPECTRUM_ASK, style = T.sub, textAlign = TextAlign.Center)
            Box(Modifier.padding(top = 14.dp)) { Pill("Allow it") { ask.launch(Perm.MIC) } }
        }
        return
    }
    LaunchedEffect(Unit) { Vis.migrate() }
    var failed by remember { mutableStateOf<String?>(null) }
    val s = rememberVis(true) { failed = it } ?: remember { VisState() }
    val style = Vis.style()
    val colorKey = Vis.color()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VIS_STYLES.forEach { (k, label) -> Chip(label, style == k) { Music.chooseVisPreset(k) } }
            }
            Spacer(Modifier.width(4.dp))
            val accent = C.accent
            ChoicePill(VIS_COLORS, colorKey, swatch = { visSwatch(it, accent) }) { Prefs.setText(Vis.COLOR, it) }
        }
        val accent = C.accent
        val fg = C.fg
        Canvas(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
            if (s.frame < 0) return@Canvas   // read, so it's drawn again each frame
            val pal = VisPalette.of(style, colorKey, accent, s.phase)
            clipRect { drawVis(style, s, pal, fg, backdrop = false) }
        }
        Text(failed ?: Music.current()?.title ?: "Nothing playing", style = T.sub,
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), textAlign = TextAlign.Center)
    }
}
