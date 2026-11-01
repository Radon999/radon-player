package org.omarchy.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.launch
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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** The visualizer's styles: each is drawn its own way. Its colour is picked apart from it (VIS_COLORS), for any style.
 *  Bars is the one it had from the start (and the default). */
val VIS_STYLES: List<Pair<String, String>> = listOf("bars" to "Bars", "mirror" to "Mirror", "wave" to "Wave", "circle" to "Circle",
    "led" to "LED", "glow" to "Glow", "neon" to "Neon", "ring" to "Ring", "particles" to "Particles", "scope" to "Scope",
    "terrain" to "Terrain", "dots" to "Dot matrix", "spectrogram" to "Spectrogram", "pulse" to "Pulse", "blocks" to "Blocks",
    "starfield" to "Starfield", "sunburst" to "Sunburst", "galaxy" to "Galaxy", "flower" to "Flower", "fireworks" to "Fireworks",
    "towers" to "Towers", "vinyl" to "Vinyl", "lissajous" to "XY scope", "rain" to "Rain", "kaleido" to "Kaleidoscope",
    "aurora" to "Aurora", "ecg" to "Heartbeat", "bubbles" to "Bubbles", "matrix" to "Code rain", "tunnel" to "Tunnel")

/** The colours, for any style: the look's own (its accent; on Now playing the cover's), the blends, then plain ones
 *  (the colours that used to be styles of their own). The blends' keys never clash with the plain ones (Looks.NEON). */
val VIS_COLORS: List<Pair<String, String>> = listOf("auto" to "Look's colour", "rainbow" to "Rainbow", "twilight" to "Twilight",
    "fire" to "Fire", "ice" to "Ice", "ocean" to "Ocean", "sunset" to "Sunset", "aurora" to "Aurora", "neonpop" to "Neon pop",
    "forest" to "Forest", "magma" to "Magma", "candy" to "Candy", "goldleaf" to "Gold leaf", "synthwave" to "Synthwave",
    "mint" to "Mint", "galaxy" to "Galaxy", "pastel" to "Pastel", "bloodmoon" to "Blood moon", "acid" to "Acid",
    "silver" to "Silver") + Looks.NEON.map { (k, v) -> k to v.first }

/** Now playing's own visualizer, behind the cover: the slim bars it always had, none, or any of the styles. */
val NP_VIS_STYLES: List<Pair<String, String>> = listOf("slim" to "Slim bars", "off" to "Off") + VIS_STYLES

/** The visualizer's settings. The style stays in Music.visPreset (saved with the app's state); the rest are Prefs. */
object Vis {
    const val COLOR = "vis_color"     // the colour, a VIS_COLORS key
    const val NOW = "np_vis"          // Now playing's visualizer, an NP_VIS_STYLES key
    const val NOW_BG = "np_bg"        // Now playing's background: "blur" (the cover's colours, the cover blurred) or "plain" (black)
    const val NOW_STRENGTH = "np_vis_strength"   // how strongly Now playing's visualizer shows, 0.1 … 1 (a number)

    /** The styles drawn around the middle of the space (the others stand on its bottom). */
    val CENTRED = setOf("circle", "ring", "particles", "pulse", "starfield", "sunburst", "galaxy", "flower", "vinyl",
        "lissajous", "kaleido", "tunnel")
    /** On Now playing these are drawn over the big cover (around and through it), not hidden behind it; the styles that
     *  stand on the bottom (bars, wave, glow, terrain…) stay behind, along the bottom of the screen. */
    val OVER_COVER = CENTRED

    /** The style; an old colour preset ("red"…) is Bars in that colour. */
    fun style(): String = Music.visPreset.let { p -> if (VIS_STYLES.any { it.first == p }) p else "bars" }
    fun color(): String = Prefs.text(COLOR).ifEmpty { Music.visPreset.takeIf { Looks.NEON.containsKey(it) } ?: "auto" }
    fun nowStyle(): String = Prefs.text(NOW, "slim").let { k -> if (NP_VIS_STYLES.any { it.first == k }) k else "slim" }
    fun plainNow(): Boolean = Prefs.text(NOW_BG) == "plain"
    /** Now playing visualizer's strength (its opacity): 0.5 is how faint it always was. */
    fun nowStrength(): Float = Prefs.num(NOW_STRENGTH, 0.5f).coerceIn(0.1f, 1f)

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
private val PASTEL = Array(64) { Color(hslToColor(it * 360f / 64f, 0.75f, 0.8f)) }

/** A blend of a few colours. mode: ALONG (bass to treble), LOUD (mostly by how loud, a little along), STRIPES (hard
 *  bands of each colour along the spectrum, no blending). */
private class VisGrad(val mode: Int, vararg stops: Long) {
    private val c = Array(stops.size) { Color(stops[it]) }
    fun at(t: Float, lv: Float): Color {
        if (mode == STRIPES) return c[(((t.coerceIn(0f, 1f) * 9.99f).toInt()) % c.size)]
        val x = (if (mode == LOUD) lv * 0.65f + t * 0.35f else t).coerceIn(0f, 1f) * (c.size - 1)
        val i = min(x.toInt(), c.size - 2)
        return lerp(c[i], c[i + 1], x - i)
    }
    companion object { const val ALONG = 0; const val LOUD = 1; const val STRIPES = 2 }
}

/** The blends added in 3.0 (Rainbow, Twilight, Fire, Ice and Pastel are worked out in VisPalette itself). */
private val VIS_GRADS: Map<String, VisGrad> = mapOf(
    "ocean" to VisGrad(VisGrad.LOUD, 0xFF0B3D91, 0xFF0096C7, 0xFF48CAE4, 0xFFCAF0F8),
    "sunset" to VisGrad(VisGrad.ALONG, 0xFF6A2C91, 0xFFF0547A, 0xFFFF9F43, 0xFFFFE066),
    "aurora" to VisGrad(VisGrad.ALONG, 0xFF2BFF88, 0xFF1FD1C1, 0xFF4F7BFF, 0xFF9B5CFF),
    "neonpop" to VisGrad(VisGrad.ALONG, 0xFFFF2FD6, 0xFFB45CFF, 0xFF19F0FF),
    "forest" to VisGrad(VisGrad.LOUD, 0xFF1B5E20, 0xFF43A047, 0xFF9CCC65, 0xFFE6EE9C),
    "magma" to VisGrad(VisGrad.LOUD, 0xFF3B0F70, 0xFFB63679, 0xFFFB8861, 0xFFFCFDBF),
    "candy" to VisGrad(VisGrad.STRIPES, 0xFFFF8FC8, 0xFF8FFFD6, 0xFFFFF38F, 0xFFC59BFF),
    "goldleaf" to VisGrad(VisGrad.LOUD, 0xFF7A4E00, 0xFFD4A017, 0xFFFFD45A, 0xFFFFF6D0),
    "synthwave" to VisGrad(VisGrad.ALONG, 0xFFFFC857, 0xFFFF2A6D, 0xFFB026FF, 0xFF05D9E8),
    "mint" to VisGrad(VisGrad.LOUD, 0xFF0F9D8A, 0xFF3EE0B0, 0xFFB5FFE4, 0xFFF0FFF9),
    "galaxy" to VisGrad(VisGrad.LOUD, 0xFF2B1B6B, 0xFF7C4DFF, 0xFFFF6EC7, 0xFFFFF0FA),
    "bloodmoon" to VisGrad(VisGrad.LOUD, 0xFF3A0008, 0xFFB3001B, 0xFFFF5A36, 0xFFFFE3D3),
    "acid" to VisGrad(VisGrad.ALONG, 0xFF9B00FF, 0xFF00C2FF, 0xFF39FF14, 0xFFEFFF00),
    "silver" to VisGrad(VisGrad.LOUD, 0xFF4B515C, 0xFF8E95A1, 0xFFC8CDD6, 0xFFFFFFFF))

/** A colour for a spot in the picture: t is where along the spectrum (0 the bass … 1 the treble), lv how loud. */
class VisPalette(val key: String, private val base: Color, private val shift: Float) {
    private val neon = Looks.NEON[key]?.second
    private val grad = VIS_GRADS[key]
    /** One colour all over (the look's own or a plain one), not a blend. */
    val single = neon != null || (grad == null && key !in setOf("rainbow", "twilight", "fire", "ice", "pastel"))

    fun at(t: Float, lv: Float): Color = when (key) {
        "rainbow" -> { val f = ((t * 0.85f + shift) % 1f + 1f) % 1f; RAINBOW[(f * RAINBOW.size).toInt().coerceIn(0, RAINBOW.size - 1)] }
        "pastel" -> { val f = ((t * 0.85f + shift) % 1f + 1f) % 1f; PASTEL[(f * PASTEL.size).toInt().coerceIn(0, PASTEL.size - 1)] }
        "twilight" -> lerp(TW_A, TW_B, t.coerceIn(0f, 1f))
        "fire" -> lerp(FIRE_LOW, FIRE_HIGH, (lv * 0.8f + (1f - t) * 0.2f).coerceIn(0f, 1f))
        "ice" -> lerp(ICE_LOW, ICE_HIGH, (lv * 0.7f + t * 0.3f).coerceIn(0f, 1f))
        else -> grad?.at(t, lv) ?: neon ?: base
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

    // The beat: how long since the bass last kicked, and how hard (Fireworks).
    var sinceBeat = 99f
        private set
    /** Turns, faster with the bass (Galaxy, Vinyl, Kaleidoscope, Flower, Tunnel, XY scope); radians. */
    var spin = 0f
        private set
    /** How far the Tunnel has flown (its rings use the part after the point). */
    var travel = 0f
        private set
    /** How fast the stars fly now (Starfield). */
    var warp = 0f
        private set
    // Starfield's stars: their direction, distance out (0 the middle … 1.5 past the corners) and colour.
    val sa = FloatArray(STARS); val sd = FloatArray(STARS); val st = FloatArray(STARS)
    // Fireworks' sparks: their burst's middle (0 … 1 across and down), how far out from it and how fast (in the
    // smaller side), how much life is left, their colour.
    val fox = FloatArray(MAXF); val foy = FloatArray(MAXF); val fdx = FloatArray(MAXF); val fdy = FloatArray(MAXF)
    val fvx = FloatArray(MAXF); val fvy = FloatArray(MAXF); val fl = FloatArray(MAXF); val fc = FloatArray(MAXF)
    private var nextF = 0
    private var fwQuiet = 0f
    // Rain's drops: across (0 … 1), down (0 … 1 the ground), speed, colour, alive (> 0), splash time left (> 0: splashing).
    val rx = FloatArray(MAXD); val ry = FloatArray(MAXD); val rv = FloatArray(MAXD); val rt = FloatArray(MAXD)
    val rl = FloatArray(MAXD); val rs = FloatArray(MAXD)
    private var nextD = 0
    // Bubbles: across, down (1 the bottom), rising speed, size (0 … 1), colour, life (1; less while popping).
    val bx = FloatArray(MAXB); val by = FloatArray(MAXB); val bv = FloatArray(MAXB); val br = FloatArray(MAXB)
    val bt = FloatArray(MAXB); val bl = FloatArray(MAXB)
    private var nextB = 0
    /** Code rain: each column's falling head (0 the top … 1 the bottom). */
    val mHead = FloatArray(MCOLS)
    /** Heartbeat's trace, a ring of samples (ecgHead the newest), -0.4 … 1. */
    val ecg = FloatArray(ECG)
    var ecgHead = 0
        private set
    private var ecgAcc = 0f
    private var ecgAge = 9f
    private var ecgAmp = 0f

    init {
        for (i in 0 until STARS) { sa[i] = rnd.nextFloat() * VIS_TAU; sd[i] = 0.02f + rnd.nextFloat() * 1.4f; st[i] = rnd.nextFloat() }
        for (i in 0 until MCOLS) mHead[i] = rnd.nextFloat() * 1.5f
    }

    /** A burst of fireworks somewhere in the top half; power 0 … 1. */
    private fun burst(power: Float) {
        val cx = 0.15f + rnd.nextFloat() * 0.7f
        val cy = 0.15f + rnd.nextFloat() * 0.4f
        val hue = rnd.nextFloat()
        repeat(14 + (power * 22).toInt()) {
            val i = nextF; nextF = (nextF + 1) % MAXF
            val a = rnd.nextFloat() * VIS_TAU
            val v = (0.25f + 0.25f * power) * (0.55f + rnd.nextFloat() * 0.45f)
            fox[i] = cx; foy[i] = cy; fdx[i] = 0f; fdy[i] = 0f
            fvx[i] = cos(a) * v; fvy[i] = sin(a) * v
            fl[i] = 1f; fc[i] = (hue + rnd.nextFloat() * 0.15f) % 1f
        }
    }

    /** The heart monitor's shape a seconds after a beat: a small dip, the tall spike, a dip under, then a low bump. */
    private fun heart(a: Float): Float = when {
        a < 0.02f -> -0.12f * a / 0.02f
        a < 0.05f -> -0.12f + 1.12f * (a - 0.02f) / 0.03f
        a < 0.09f -> 1f - 1.35f * (a - 0.05f) / 0.04f
        a < 0.12f -> -0.35f + 0.35f * (a - 0.09f) / 0.03f
        a < 0.22f -> 0f
        a < 0.38f -> 0.25f * sin((a - 0.22f) / 0.16f * PI.toFloat())
        else -> 0f
    }

    /** Moves on the newer styles' own things: stars, fireworks, rain, bubbles, code rain, the heart trace. True while any still moves. */
    private fun stepMore(dt: Float, k: Float, loud: Float, beat: Boolean): Boolean {
        spin += dt * (0.25f + bass * 1.8f)
        if (spin > 1000f * VIS_TAU) spin -= 1000f * VIS_TAU   // kept small (by whole turns) so it stays precise
        travel += dt * (0.12f + bass * 0.9f + loud * 0.15f)
        if (travel > 1000f) travel -= 1000f
        warp = 0.06f + bass * 1.4f + loud * 0.25f
        for (i in 0 until STARS) {
            sd[i] += dt * warp * (0.08f + sd[i]) * 1.6f
            if (sd[i] > 1.5f) { sd[i] = 0.01f + rnd.nextFloat() * 0.12f; sa[i] = rnd.nextFloat() * VIS_TAU; st[i] = rnd.nextFloat() }
        }
        // Fireworks: a burst on each beat, and now and then while it's loud with no clear beat.
        fwQuiet += dt
        if (beat) { fwQuiet = 0f; burst(bass) }
        else if (fwQuiet > 1.4f && loud > 0.25f) { fwQuiet = 0f; burst(loud * 0.5f) }
        var alive = false
        val drag = 0.97f.pow(k)
        for (i in 0 until MAXF) if (fl[i] > 0f) {
            fvy[i] += 0.3f * dt
            fvx[i] *= drag; fvy[i] *= drag
            fdx[i] += fvx[i] * dt; fdy[i] += fvy[i] * dt
            fl[i] -= dt * 0.75f
            alive = true
        }
        // Rain: each pair of bands drops more often the louder it is.
        val n = levels.size
        if (!Music.paused) for (g in 0 until n / 2) {
            val v = max(levels[g * 2], levels[g * 2 + 1])
            if (rnd.nextFloat() < v * v * dt * 6f) {
                val i = nextD; nextD = (nextD + 1) % MAXD
                rx[i] = (g + 0.2f + rnd.nextFloat() * 0.6f) / (n / 2); ry[i] = -0.1f
                rv[i] = 0.9f + v * 0.9f + rnd.nextFloat() * 0.3f; rt[i] = g / (n / 2 - 1f)
                rl[i] = 1f; rs[i] = 0f
            }
        }
        for (i in 0 until MAXD) if (rl[i] > 0f) {
            alive = true
            if (rs[i] > 0f) { rs[i] -= dt; if (rs[i] <= 0f) rl[i] = 0f }
            else { ry[i] += rv[i] * dt; if (ry[i] >= 1f) { ry[i] = 1f; rs[i] = SPLASH } }
        }
        // Bubbles: from twelve spots along the bottom, more and bigger the louder their bands.
        if (!Music.paused) for (g in 0 until 12) {
            val per = n / 12
            var v = 0f
            for (b in g * per until (g + 1) * per) v += levels[b]
            v /= per
            if (rnd.nextFloat() < v * v * dt * 2.2f) {
                val i = nextB; nextB = (nextB + 1) % MAXB
                bx[i] = (g + 0.2f + rnd.nextFloat() * 0.6f) / 12f; by[i] = 1.05f
                bv[i] = 0.12f + 0.25f * v + rnd.nextFloat() * 0.08f; br[i] = 0.25f + 0.75f * v; bt[i] = g / 11f; bl[i] = 1f
            }
        }
        for (i in 0 until MAXB) if (bl[i] > 0f) {
            by[i] -= bv[i] * dt
            if (by[i] < 0.1f) bl[i] -= dt * 4f   // pops at the top
            alive = true
        }
        // Code rain: each column's head falls faster the louder its bands, and starts again at the top.
        for (cI in 0 until MCOLS) {
            val per = n / MCOLS
            var v = 0f
            for (b in cI * per until (cI + 1) * per) v = max(v, levels[b])
            mHead[cI] += dt * (0.2f + v * 1.6f)
            if (mHead[cI] > 1.9f) mHead[cI] = -rnd.nextFloat() * 0.3f
        }
        // Heartbeat: a beat for each kick; a calm one about each second while the music has no clear kick.
        ecgAge += dt
        if (beat) { ecgAge = 0f; ecgAmp = 0.55f + 0.45f * bass }
        else if (ecgAge > 1.1f && loud > 0.03f) { ecgAge = 0f; ecgAmp = 0.3f + 0.4f * loud }
        ecgAcc += dt
        while (ecgAcc >= ECG_STEP) {
            ecgAcc -= ECG_STEP
            ecgHead = (ecgHead + 1) % ECG
            ecg[ecgHead] = heart((ecgAge - ecgAcc).coerceAtLeast(0f)) * ecgAmp + (rnd.nextFloat() - 0.5f) * 0.05f * loud
        }
        return alive
    }

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
        sinceBeat += dt
        val beat = bass > bassSlow + 0.07f && bass > 0.22f && cool <= 0f
        if (beat) {
            cool = 0.14f
            sinceBeat = 0f
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
        if (stepMore(dt, k, loud, beat)) alive = true
        return moving || alive || sinceLoud < HIST * HIST_STEP + 0.5f
    }

    companion object {
        const val HIST = 64              // rows of history kept
        const val HIST_STEP = 1f / 30f   // one row every 1/30 s: about 2 seconds of history
        const val MAXP = 160             // sparks at once at most
        const val MAXR = 8               // rings at once at most
        const val STARS = 110            // Starfield's stars
        const val MAXF = 200             // fireworks' sparks at once at most
        const val MAXD = 140             // raindrops at once at most
        const val MAXB = 56              // bubbles at once at most
        const val MCOLS = 24             // Code rain's columns
        const val ECG = 160              // Heartbeat's samples across
        const val ECG_STEP = 1f / 70f    // one sample every 1/70 s: about 2.3 seconds across
        const val SPLASH = 0.35f         // how long a raindrop's splash lasts, seconds
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
        "starfield" -> {   // stars rushing out from the middle, faster on each beat of the bass
            val reach = hypot(w, h) / 2
            val streak = (0.03f + s.warp * 0.1f).coerceAtMost(0.3f)
            for (i in 0 until VisState.STARS) {
                val d = s.sd[i]
                val dir = Offset(cos(s.sa[i]), sin(s.sa[i]))
                val r0 = max(0f, d - streak * (0.15f + d)) * reach
                val bright = (d * 1.6f).coerceIn(0f, 1f)
                drawLine(pal.at(s.st[i], 0.4f + 0.6f * bright).copy(alpha = bright), c + dir * r0, c + dir * (d * reach),
                    strokeWidth = (0.6f + 2.2f * d) * u, cap = StrokeCap.Round)
            }
            if (!backdrop) drawCircle(pal.main.copy(alpha = 0.06f + 0.2f * s.bass), half * (0.05f + 0.1f * s.bass), c)
        }
        "sunburst" -> {   // a ring of rays made of arcs, a round equaliser turning slowly, a cap on each peak
            val sectors = 24; val segs = 8; val per = max(1, n / sectors)
            val r0 = half * (0.2f + 0.05f * s.bass); val r1 = half * 0.96f
            val th = (r1 - r0) / segs
            val seg = Stroke(width = th * 0.72f)
            val capStroke = Stroke(width = max(1.5f * u, th * 0.22f))
            val sweep = 360f / sectors
            val rot = s.spin * 10f
            for (sI in 0 until sectors) {
                var v = 0f; var pk = 0f
                for (b in sI * per until min(n, (sI + 1) * per)) { v = max(v, lv[b]); pk = max(pk, s.peaks[b]) }
                val lit = (v * segs).roundToInt()
                val start = rot + sI * sweep - 90f + sweep * 0.1f
                val tg = sI / (sectors - 1f)
                for (g in 0 until segs) {
                    val rad = r0 + (g + 0.5f) * th
                    val f = (g + 1f) / segs
                    val col = when {
                        g < lit -> pal.at(tg, f).copy(alpha = 0.5f + 0.5f * f)
                        backdrop -> continue
                        else -> fg.copy(alpha = 0.05f)
                    }
                    drawArc(col, start, sweep * 0.8f, false, Offset(c.x - rad, c.y - rad), Size(rad * 2, rad * 2), style = seg)
                }
                if (pk * segs > 0.3f) {
                    val rad = r0 + pk.coerceAtMost(1f) * (r1 - r0)
                    drawArc(lerp(pal.at(tg, 1f), Color.White, 0.5f), start, sweep * 0.8f, false, Offset(c.x - rad, c.y - rad),
                        Size(rad * 2, rad * 2), style = capStroke)
                }
            }
        }
        "galaxy" -> {   // three spiral arms of stars turning round a bright core, each star a band, brighter the louder
            val arms = 3
            drawCircle(pal.main.copy(alpha = 0.05f + 0.1f * s.bass), half * (0.22f + 0.15f * s.bass), c)
            drawCircle(pal.at(0f, s.bass).copy(alpha = 0.18f + 0.45f * s.bass), half * (0.07f + 0.08f * s.bass), c)
            for (a in 0 until arms) for (b in 0 until n) {
                val tt = t(b); val v = lv[b]
                val jit = sin(b * 12.9898f + a * 78.233f)   // a fixed scatter for each star
                val ang = s.spin * 0.6f + a * VIS_TAU / arms + tt * 3.4f + jit * 0.18f
                val r = half * (0.1f + 0.82f * tt) * (1f + 0.06f * jit) + v * half * 0.06f
                drawCircle(pal.at(tt, v).copy(alpha = 0.25f + 0.75f * v), (0.8f + 3.2f * v) * u * (0.7f + 0.5f * (1f - tt)),
                    Offset(c.x + cos(ang) * r, c.y + sin(ang) * r))
            }
        }
        "flower" -> {   // a flower: eight outer petals from the low half of the spectrum, six inner ones from the high half
            val pts = 144
            for (layer in 0..1) {
                val petals = if (layer == 0) 8 else 6
                val from = if (layer == 0) 0 else n / 2
                val span = n / 2
                val rIn = half * (if (layer == 0) 0.2f else 0.1f)
                val amp = half * (if (layer == 0) 0.74f else 0.42f)
                val rot = if (layer == 0) s.spin * 0.3f else -s.spin * 0.45f
                val path = Path()
                var sum = 0f
                for (i in 0 until pts) {
                    val th = i * VIS_TAU / pts
                    val pf = i * petals / pts.toFloat()
                    val p = min(pf.toInt(), petals - 1)
                    val lo = from + p * span / petals
                    val v = band(lo, max(lo + 1, from + (p + 1) * span / petals))
                    sum += v
                    val sh = sin((pf - p) * PI.toFloat())   // 0 between petals, 1 at a tip
                    val r = rIn + amp * (0.15f + 0.85f * v) * sh * sh
                    val x = c.x + cos(th + rot) * r; val y = c.y + sin(th + rot) * r
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                val avg = sum / pts
                val col = pal.at(if (layer == 0) 0.2f else 0.8f, avg)
                drawPath(path, col.copy(alpha = 0.18f + 0.3f * avg))
                drawPath(path, col.copy(alpha = 0.6f + 0.4f * avg), style = Stroke(width = 1.8f * u, join = StrokeJoin.Round))
            }
            drawCircle(pal.at(1f, s.bass).copy(alpha = 0.5f + 0.5f * s.bass), half * (0.05f + 0.04f * s.bass), c)
        }
        "fireworks" -> {   // a burst of sparks for each kick of the bass, falling and fading
            val area = if (backdrop) h else h * 0.95f
            val unit = min(w, area)
            for (i in 0 until VisState.MAXF) {
                val l = s.fl[i]
                if (l <= 0f) continue
                val p = Offset(s.fox[i] * w + s.fdx[i] * unit, s.foy[i] * area + s.fdy[i] * unit)
                val col = pal.at(s.fc[i], l)
                val a = l.coerceIn(0f, 1f)
                drawLine(col.copy(alpha = a * 0.45f), p - Offset(s.fvx[i], s.fvy[i]) * (unit * 0.07f), p,
                    strokeWidth = 1.4f * u, cap = StrokeCap.Round)
                drawCircle(lerp(col, Color.White, 0.3f * a).copy(alpha = a), (0.8f + 1.6f * l) * u, p)
            }
        }
        "towers" -> {   // blocks in 3D standing on a floor, lit on top, shaded on the side, mirrored under the floor
            val groups = 12; val per = n / groups
            val floor = if (backdrop) h else h * 0.72f
            val gw = w / groups; val fw = gw * 0.6f; val dx = gw * 0.24f; val dy = min(gw * 0.18f, h * 0.08f)
            val room = floor - dy - h * 0.03f
            for (g in 0 until groups) {
                var v = 0f
                for (b in g * per until (g + 1) * per) v = max(v, lv[b])
                val th = max(2f * u, v * room)
                val x = g * gw + (gw - fw - dx) / 2
                val y = floor - th
                val col = pal.at(g / (groups - 1f), v)
                if (!backdrop) drawRect(Brush.verticalGradient(listOf(col.copy(alpha = 0.25f * (0.4f + v)), Color.Transparent),
                    startY = floor, endY = floor + th * 0.5f), Offset(x, floor), Size(fw, th * 0.5f))
                val side = Path()
                side.moveTo(x + fw, y); side.lineTo(x + fw + dx, y - dy); side.lineTo(x + fw + dx, floor - dy); side.lineTo(x + fw, floor); side.close()
                drawPath(side, lerp(col, Color.Black, 0.45f))
                val top = Path()
                top.moveTo(x, y); top.lineTo(x + dx, y - dy); top.lineTo(x + fw + dx, y - dy); top.lineTo(x + fw, y); top.close()
                drawPath(top, lerp(col, Color.White, 0.35f))
                drawRect(Brush.verticalGradient(listOf(col, lerp(col, Color.Black, 0.3f)), startY = y, endY = floor), Offset(x, y), Size(fw, th))
            }
            if (!backdrop) drawLine(fg.copy(alpha = 0.12f), Offset(0f, floor), Offset(w, floor), 1f)
        }
        "vinyl" -> {   // a record turning, its grooves lit by the music (the bass outside), the label beating with the bass
            val rr = half * 0.94f
            val rot = s.spin * 2.2f
            drawCircle(Color(0xFF0D0D10).copy(alpha = if (backdrop) 0.45f else 1f), rr, c)
            val grooves = 16; val per = n / grooves
            val dull = Color(0xFF34343C)
            for (g in 0 until grooves) {
                val v = band(g * per, (g + 1) * per)
                val rad = rr * (0.38f + 0.58f * (grooves - 1 - g) / (grooves - 1f))
                drawCircle(lerp(dull, pal.at(g / (grooves - 1f), v), (v * 1.4f).coerceAtMost(1f)).copy(alpha = 0.45f + 0.55f * v), rad, c,
                    style = Stroke(width = (0.8f + 2.2f * v) * u))
            }
            val deg = rot * 180f / PI.toFloat()
            for (k in 0..1) drawArc(Color.White.copy(alpha = 0.07f), deg + k * 180f, 28f, true, Offset(c.x - rr, c.y - rr), Size(rr * 2, rr * 2))
            drawCircle(pal.main.copy(alpha = 0.9f), rr * (0.3f + 0.03f * s.bass), c)
            drawCircle(lerp(pal.main, Color.White, 0.6f), rr * 0.035f, c + Offset(cos(rot), sin(rot)) * (rr * 0.2f))
            drawCircle(Color.Black, rr * 0.025f, c)
            if (!backdrop) {   // the tone arm
                val pivot = Offset(c.x + rr * 1.02f, c.y - rr * 0.9f)
                val head = Offset(c.x + rr * 0.5f, c.y - rr * 0.12f)
                drawLine(fg.copy(alpha = 0.55f), pivot, head, strokeWidth = 3f * u, cap = StrokeCap.Round)
                drawCircle(fg.copy(alpha = 0.7f), 6f * u, pivot)
            }
        }
        "lissajous" -> {   // an XY scope: a figure traced by two waves, the bass, mids and highs bending it
            val lo = band(0, max(1, n / 6)); val mi = band(n / 6, n / 2); val hi = band(n / 2, n)
            val rad = half * 0.82f
            if (!backdrop) {
                val grid = fg.copy(alpha = 0.06f)
                drawCircle(grid, rad, c, style = Stroke(width = 1f))
                drawLine(grid, Offset(c.x - rad, c.y), Offset(c.x + rad, c.y), 1f)
                drawLine(grid, Offset(c.x, c.y - rad), Offset(c.x, c.y + rad), 1f)
            }
            val pts = 220
            val path = Path()
            for (i in 0..pts) {
                val a = i * VIS_TAU / pts
                val x = sin(3f * a + s.spin) * (0.45f + 0.45f * lo) + 0.22f * mi * sin(7f * a + s.phase * 2f)
                val y = sin(2f * a) * (0.45f + 0.45f * mi) + 0.22f * hi * cos(11f * a - s.phase * 3f)
                val px = c.x + x.coerceIn(-1f, 1f) * rad; val py = c.y + y.coerceIn(-1f, 1f) * rad
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, pal.brush(0.15f), style = Stroke(width = 9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, pal.brush(0.35f), style = Stroke(width = 4.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, pal.brush(1f), style = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        "rain" -> {   // drops falling where the spectrum is loud, splashing on the ground
            val ground = if (backdrop) h - 2f * u else h * 0.85f
            if (!backdrop) drawLine(pal.brush(0.35f), Offset(0f, ground), Offset(w, ground), 1.5f * u)
            val len = ground * 0.05f
            val splash = Stroke(width = 1.2f * u)
            for (i in 0 until VisState.MAXD) {
                if (s.rl[i] <= 0f) continue
                val x = s.rx[i] * w
                val col = pal.at(s.rt[i], 0.7f)
                if (s.rs[i] > 0f) {
                    val k = 1f - s.rs[i] / VisState.SPLASH
                    val rw = min(w, h) * 0.035f * (0.3f + k) + 2f * u
                    drawOval(col.copy(alpha = (1f - k) * 0.8f), Offset(x - rw, ground - rw * 0.3f), Size(rw * 2, rw * 0.6f), style = splash)
                } else {
                    val y = s.ry[i] * ground
                    drawLine(col.copy(alpha = 0.85f), Offset(x, y - len * (0.5f + s.rv[i] * 0.5f)), Offset(x, y),
                        strokeWidth = 1.6f * u, cap = StrokeCap.Round)
                }
            }
        }
        "kaleido" -> {   // a kaleidoscope: a slice of the spectrum mirrored twelve times round the middle, in three layers
            val wedges = 12; val k = 12; val wa = VIS_TAU / wedges
            val inner = floatArrayOf(0.35f, 0.22f, 0.1f); val reach = floatArrayOf(0.6f, 0.4f, 0.25f)
            for (layer in 0..2) {
                val from = layer * n / 3; val span = n / 3
                val rot = s.spin * (if (layer == 1) -0.5f else 0.35f) + layer * wa / 2
                val path = Path()
                var sum = 0f
                for (wI in 0 until wedges) for (j in 0 until k) {
                    val m = if (wI % 2 == 0) j else k - j   // every other slice mirrored, so they meet edge to edge
                    val v = lv[from + m * (span - 1) / k]
                    sum += v
                    val ang = rot + wI * wa + j * wa / k
                    val r = half * (inner[layer] + reach[layer] * v)
                    val x = c.x + cos(ang) * r; val y = c.y + sin(ang) * r
                    if (wI == 0 && j == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                val avg = sum / (wedges * k)
                val col = pal.at(layer / 2f, avg)
                drawPath(path, col.copy(alpha = 0.16f + 0.3f * avg))
                drawPath(path, col.copy(alpha = 0.55f + 0.45f * avg), style = Stroke(width = 1.5f * u, join = StrokeJoin.Round))
            }
        }
        "aurora" -> {   // soft curtains of light waving across the sky, each a part of the spectrum, taller the louder
            val ribbons = 4; val per = n / ribbons; val pts = 32
            val bottom = if (backdrop) h else h * 0.92f
            for (r in 0 until ribbons) {
                val v = band(r * per, (r + 1) * per)
                val edgeY = bottom * (0.55f + 0.11f * r)
                val curtain = bottom * (0.15f + 0.4f * v)
                val sway = bottom * 0.06f * (0.5f + v)
                val col = pal.at(r / (ribbons - 1f), 0.4f + 0.6f * v)
                fun edge(i: Int): Float {
                    val fx = i / pts.toFloat()
                    return edgeY + sin(fx * 5f + s.phase * (0.6f + 0.25f * r) + r * 1.7f) * sway +
                        sin(fx * 11f - s.phase * 0.9f + r) * sway * 0.35f
                }
                val line = Path(); val glow = Path()
                for (i in 0..pts) {
                    val x = w * i / pts; val y = edge(i)
                    if (i == 0) { line.moveTo(x, y); glow.moveTo(x, y) } else { line.lineTo(x, y); glow.lineTo(x, y) }
                }
                for (i in pts downTo 0) glow.lineTo(w * i / pts, edge(i) - curtain * (0.75f + 0.25f * sin(i * 0.9f + s.phase * 2f)))
                glow.close()
                drawPath(glow, Brush.verticalGradient(0f to Color.Transparent, 0.7f to col.copy(alpha = 0.3f * (0.35f + 0.65f * v)),
                    1f to col.copy(alpha = 0.55f * (0.35f + 0.65f * v)), startY = edgeY - sway - curtain, endY = edgeY + sway))
                drawPath(line, col.copy(alpha = 0.45f + 0.45f * v), style = Stroke(width = 1.5f * u, join = StrokeJoin.Round))
            }
        }
        "ecg" -> {   // a heart monitor: a beat for each kick of the bass, the trace sweeping across
            val mid = if (backdrop) h * 0.6f else h * 0.5f
            val amp = if (backdrop) h * 0.5f else h * 0.38f
            if (!backdrop) {
                val grid = fg.copy(alpha = 0.05f)
                val step = max(8f * u, h / 8f)
                var gy = mid % step
                while (gy < h) { drawLine(grid, Offset(0f, gy), Offset(w, gy), 1f); gy += step }
                var gx = 0f
                while (gx < w) { drawLine(grid, Offset(gx, 0f), Offset(gx, h), 1f); gx += step }
            }
            val m = VisState.ECG
            val dxs = w / (m - 1)
            val head = s.ecgHead
            for (i in 1 until m) {
                val age = ((head - i) % m + m) % m   // how many samples ago this one was written
                if (age > m - 8) continue             // the gap just ahead of the sweep
                val a = 1f - age / m.toFloat()
                val col = pal.at(i / (m - 1f), 0.5f + 0.5f * abs(s.ecg[i]))
                drawLine(col.copy(alpha = a), Offset((i - 1) * dxs, mid - s.ecg[i - 1] * amp), Offset(i * dxs, mid - s.ecg[i] * amp),
                    strokeWidth = 2f * u, cap = StrokeCap.Round)
            }
            val hp = Offset(head * dxs, mid - s.ecg[head] * amp)
            val hc = pal.at(head / (m - 1f), 1f)
            drawCircle(hc.copy(alpha = 0.25f), 7f * u, hp)
            drawCircle(lerp(hc, Color.White, 0.5f), 3f * u, hp)
        }
        "bubbles" -> {   // bubbles rising from the bottom, more and bigger where the spectrum is loud, popping at the top
            val area = if (backdrop) h else h * 0.92f
            val unit = min(w, h)
            val ring = Stroke(width = 1.3f * u)
            for (i in 0 until VisState.MAXB) {
                val l = s.bl[i]
                if (l <= 0f) continue
                val r = (2f * u + s.br[i] * unit * 0.06f) * (1f + (1f - l) * 0.6f)
                val p = Offset(s.bx[i] * w + sin(s.phase * 2.2f + i * 1.7f) * r * 0.7f, s.by[i] * area)
                val col = pal.at(s.bt[i], 0.5f + 0.5f * s.br[i])
                drawCircle(col.copy(alpha = 0.1f * l), r, p)
                drawCircle(col.copy(alpha = 0.75f * l), r, p, style = ring)
                drawCircle(Color.White.copy(alpha = 0.55f * l), r * 0.22f, p + Offset(-r * 0.35f, -r * 0.35f))
            }
        }
        "matrix" -> {   // columns of little glyphs raining down, each column a band, faster and longer the louder
            val cols = VisState.MCOLS; val per = n / cols
            val cw = w / cols
            val area = if (backdrop) h else h * 0.95f
            val rows = max(4, (area / cw).toInt())
            val ch = area / rows
            val tick = (s.phase * 8f).toInt()
            for (cI in 0 until cols) {
                var v = 0f
                for (b in cI * per until (cI + 1) * per) v = max(v, lv[b])
                val trail = 0.12f + 0.75f * v
                val headY = s.mHead[cI]
                val col = pal.at(cI / (cols - 1f), v)
                for (r in 0 until rows) {
                    val d = headY - (r + 0.5f) / rows
                    if (d < 0f || d > trail) continue
                    val fade = 1f - d / trail
                    // A glyph: two strokes picked by a hash, a few of them changing as it runs.
                    val hsh = ((cI * 73856093) xor (r * 19349663) xor (if ((cI + r) % 4 == 0) tick * 83492791 else 0)) and 0x7fffffff
                    val x = cI * cw + cw * 0.2f; val y = r * ch + ch * 0.12f
                    val gw = cw * 0.6f; val gh = ch * 0.76f
                    val c0 = if (d < 1f / rows) lerp(col, Color.White, 0.75f) else col.copy(alpha = 0.15f + 0.85f * fade)
                    val vx = x + gw * ((hsh and 3) / 3f) * 0.8f
                    drawRect(c0, Offset(vx, y), Size(max(1f, gw * 0.2f), gh * (0.5f + 0.5f * ((hsh shr 2) and 1))))
                    val hy = y + gh * (((hsh shr 3) and 3) / 3f) * 0.8f
                    drawRect(c0, Offset(x, hy), Size(gw * (0.5f + 0.5f * ((hsh shr 5) and 1)), max(1f, gh * 0.16f)))
                }
            }
        }
        "tunnel" -> {   // squares flying out of the middle, twisting as they come, faster with the bass, bent by the bands
            val rings = 12; val perEdge = 6
            val far = hypot(w, h) / 2 * 1.1f
            for (i in 0 until rings) {
                val z = (s.travel + i / rings.toFloat()) % 1f
                val rad = far * (0.02f + z * z)
                val rot = s.spin * 0.4f + z * 1.6f
                val fade = min(1f, z * 3f) * (if (z > 0.85f) (1f - z) / 0.15f else 1f)
                val path = Path()
                var sum = 0f
                for (e in 0 until 4) {
                    val a0 = rot + e * VIS_TAU / 4; val a1 = a0 + VIS_TAU / 4
                    val p0 = Offset(cos(a0), sin(a0)); val p1 = Offset(cos(a1), sin(a1))
                    for (q in 0 until perEdge) {
                        val j = e * perEdge + q
                        val v = lv[(j * n / (4 * perEdge)).coerceAtMost(n - 1)]
                        sum += v
                        val f = q / perEdge.toFloat()
                        val p = c + (p0 + (p1 - p0) * f) * (rad * (1f + 0.18f * v))
                        if (j == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                }
                path.close()
                val avg = sum / (4 * perEdge)
                drawPath(path, pal.at(z, avg).copy(alpha = (fade * (0.35f + 0.65f * avg)).coerceIn(0f, 1f)),
                    style = Stroke(width = (0.8f + 3f * z * z) * u, join = StrokeJoin.Round))
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

/** A whole turn, in radians. */
private val VIS_TAU = (2 * PI).toFloat()

private val NEON_HALO = listOf(7f to 0.06f, 4f to 0.10f, 2f to 0.18f)

/** A visualizer colour's little sample for the menu. */
fun visSwatch(key: String, accent: Color): Pair<Color, Brush> = Color(0xFF101014) to
    if (key == "auto") SolidColor(accent)
    // Quiet to loud as well as bass to treble, so the blends that go by loudness show their range too.
    else VisPalette(key, accent, 0f).let { p -> Brush.horizontalGradient(List(5) { p.at(it / 4f, 0.3f + 0.6f * it / 4f) }) }

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
                if (sw != null) DropdownMenuItem(text = text, onClick = { open = false; pick(k) }, leadingIcon = { VisColorDot(sw.first, sw.second) })
                else DropdownMenuItem(text = text, onClick = { open = false; pick(k) })
            }
        }
    }
}

/** A colour's little sample: a dot of it on a dark circle (the colour menus; Now playing's visualizer pane too). */
@Composable
fun VisColorDot(bg: Color, dot: Brush) {
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
            // The styles in a row that scrolls: ‹ and › step through it (on the PC a mouse can't swipe it; Shift + wheel scrolls it too)
            val styleScroll = rememberScrollState()
            val scrollScope = androidx.compose.runtime.rememberCoroutineScope()
            fun step(by: Float) { scrollScope.launch { styleScroll.animateScrollBy(by) } }
            if (styleScroll.canScrollBackward) Chip("‹", false) { step(-420f) }
            Row(Modifier.weight(1f).horizontalScroll(styleScroll)
                .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VIS_STYLES.forEach { (k, label) -> Chip(label, style == k) { Music.chooseVisPreset(k) } }
            }
            if (styleScroll.canScrollForward) Chip("›", false) { step(420f) }
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
