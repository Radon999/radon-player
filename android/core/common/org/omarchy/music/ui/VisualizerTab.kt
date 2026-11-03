@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package org.omarchy.music.ui

import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
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
private val BUILT_VIS_STYLES: List<Pair<String, String>> = listOf("bars" to "Bars", "mirror" to "Mirror", "wave" to "Wave", "circle" to "Circle",
    "led" to "LED", "glow" to "Glow", "neon" to "Neon", "ring" to "Ring", "particles" to "Particles", "scope" to "Scope",
    "terrain" to "Terrain", "dots" to "Dot matrix", "spectrogram" to "Spectrogram", "pulse" to "Pulse", "blocks" to "Blocks",
    "starfield" to "Starfield", "sunburst" to "Sunburst", "galaxy" to "Galaxy", "flower" to "Flower", "fireworks" to "Fireworks",
    "towers" to "Towers", "vinyl" to "Vinyl", "lissajous" to "XY scope", "rain" to "Rain", "kaleido" to "Kaleidoscope",
    "aurora" to "Aurora", "ecg" to "Heartbeat", "bubbles" to "Bubbles", "matrix" to "Code rain", "tunnel" to "Tunnel",
    // 15 more along the bottom
    "skyline" to "City lights", "capsules" to "Capsules", "hills" to "Hills", "steps" to "Steps", "needles" to "Needles",
    "ribbon" to "Ribbon", "dotbars" to "Dot bars", "flames" to "Flames", "tides" to "Tides", "chunky" to "8-bit",
    "bounce" to "Bouncing balls", "grass" to "Meadow", "outlines" to "Outlines", "curtain" to "Curtain", "echoes" to "Echoes",
    // 15 more around the middle (out of the cover's centre on Now playing)
    "orbits" to "Orbits", "spiral" to "Spiral", "polygons" to "Polygons", "radar" to "Radar", "lightrays" to "Light rays",
    "atom" to "Atom", "mandala" to "Mandala", "ripples" to "Ripples", "nova" to "Supernova", "honeycomb" to "Honeycomb",
    "heart" to "Heart", "sunflower" to "Sunflower", "vortex" to "Vortex", "iris" to "Iris", "clockwork" to "Clockwork")
/** The built-in styles that the style maker can start from. */
val VIS_BASES: List<Pair<String, String>> get() = BUILT_VIS_STYLES
/** Every style: the built-in ones, then the ones you made (Make your own visualizer). */
val VIS_STYLES: List<Pair<String, String>> get() = BUILT_VIS_STYLES + numbered(MyVis.styles().map { it.key to it.name }).map { (k, n) -> k to "$n (yours)" }

/** Two of yours with the same name get a number after it ("My style", "My style 2"), so the menus tell them apart. */
internal fun numbered(items: List<Pair<String, String>>): List<Pair<String, String>> {
    val seen = HashMap<String, Int>()
    return items.map { (k, n) -> val c = (seen[n] ?: 0) + 1; seen[n] = c; k to (if (c == 1) n else "$n $c") }
}

/** The colours, for any style: the look's own (its accent; on Now playing the cover's), the blends, then plain ones
 *  (the colours that used to be styles of their own). The blends' keys never clash with the plain ones (Looks.NEON). */
private val BUILT_VIS_COLORS: List<Pair<String, String>> = listOf("auto" to "Look's colour", "rainbow" to "Rainbow", "twilight" to "Twilight",
    "fire" to "Fire", "ice" to "Ice", "ocean" to "Ocean", "sunset" to "Sunset", "aurora" to "Aurora", "neonpop" to "Neon pop",
    "forest" to "Forest", "magma" to "Magma", "candy" to "Candy", "goldleaf" to "Gold leaf", "synthwave" to "Synthwave",
    "mint" to "Mint", "galaxy" to "Galaxy", "pastel" to "Pastel", "bloodmoon" to "Blood moon", "acid" to "Acid",
    "silver" to "Silver") + Looks.NEON.map { (k, v) -> k to v.first } + Looks.NEON_LOOKS.map { it.first to it.second }
/** Every colour: the built-in ones, then the blends you made. */
val VIS_COLORS: List<Pair<String, String>> get() = BUILT_VIS_COLORS + numbered(MyVis.colors().map { it.key to it.name }).map { (k, n) -> k to "$n (yours)" }

/** The neon looks' colours (Neon Tokyo nights…): each look's neon, blended from its second hue (its background's,
 *  made bright) through the neon to a white-hot top on the loud parts. */
private val NEON_LOOK_GRADS: Map<String, VisGrad> by lazy {
    Looks.NEON_LOOKS.associate { (k, _, c) ->
        val (neon, shade) = c
        val hsl = FloatArray(3); colorToHsl(shade.toArgb(), hsl)
        val second = Color(hslToColor(hsl[0], 0.9f, 0.55f))
        k to VisGrad(VisGrad.LOUD, second.toArgb().toLong() and 0xFFFFFFFFL, neon.toArgb().toLong() and 0xFFFFFFFFL,
            lerp(neon, Color.White, 0.55f).toArgb().toLong() and 0xFFFFFFFFL)
    }
}

/** Now playing's own visualizer, behind the cover: the slim bars it always had, none, or any of the styles. */
val NP_VIS_STYLES: List<Pair<String, String>> get() = listOf("slim" to "Slim bars", "off" to "Off") + VIS_STYLES

/** The visualizer's settings. The style stays in Music.visPreset (saved with the app's state); the rest are Prefs. */
object Vis {
    const val COLOR = "vis_color"     // the colour, a VIS_COLORS key
    const val NOW = "np_vis"          // Now playing's visualizer, an NP_VIS_STYLES key
    const val NOW_BG = "np_bg"        // Now playing's background: "blur" (the cover's colours, the cover blurred) or "plain" (black)
    const val NOW_STRENGTH = "np_vis_strength"   // how strongly Now playing's visualizer shows, 0.1 … 1 (a number)

    /** The styles drawn around the middle of the space (the others stand on its bottom). */
    val BUILT_CENTRED = setOf("circle", "ring", "particles", "pulse", "starfield", "sunburst", "galaxy", "flower", "vinyl",
        "lissajous", "kaleido", "tunnel", "orbits", "spiral", "polygons", "radar", "lightrays", "atom", "mandala", "ripples", "nova",
        "honeycomb", "heart", "sunflower", "vortex", "iris", "clockwork")
    /** ... and the styles you made from one of them. */
    val CENTRED: Set<String> get() = BUILT_CENTRED + MyVis.styles().filter { it.base in BUILT_CENTRED || it.dual }.map { it.key }
    /** Yours with Dual on: one style in the middle (over the cover on Now playing) and one along the bottom. */
    fun dual(key: String): Boolean = key.startsWith("myvis-") && MyVis.style(key)?.dual == true
    /** On Now playing these are drawn over the big cover (around and through it), not hidden behind it; the styles that
     *  stand on the bottom (bars, wave, glow, terrain…) stay behind, along the bottom of the screen. */
    val OVER_COVER: Set<String> get() = CENTRED.filterNot { full(it) }.toSet()
    /** A style you made with Full screen on: on Now playing it fills the whole screen behind everything (at its normal
     *  size, the way the Visualizer tab draws it), instead of over the cover or along the bottom. */
    fun full(key: String): Boolean = key.startsWith("myvis-") && MyVis.style(key)?.full == true

    /** The style; an old colour preset ("red"…) is Bars in that colour. */
    fun style(): String = Music.visPreset.let { p -> if (VIS_STYLES.any { it.first == p }) p else "bars" }
    fun color(): String = Prefs.text(COLOR).ifEmpty { Music.visPreset.takeIf { Looks.NEON.containsKey(it) } ?: "auto" }
    fun nowStyle(): String = Prefs.text(NOW, "slim").let { k -> if (NP_VIS_STYLES.any { it.first == k }) k else "slim" }
    /** Now playing's background: plain since 3.1 (Cover blur is gone): no blurred cover behind it. */
    fun plainNow(): Boolean = true
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
    private val grad = VIS_GRADS[key] ?: NEON_LOOK_GRADS[key] ?: myGrad(key)
    /** One colour all over (the look's own or a plain one), not a blend. */
    val single = neon != null || (grad == null && key !in setOf("rainbow", "twilight", "fire", "ice", "pastel", "lookglow"))

    fun at(t: Float, lv: Float): Color = when (key) {
        "rainbow" -> { val f = ((t * 0.85f + shift) % 1f + 1f) % 1f; RAINBOW[(f * RAINBOW.size).toInt().coerceIn(0, RAINBOW.size - 1)] }
        "pastel" -> { val f = ((t * 0.85f + shift) % 1f + 1f) % 1f; PASTEL[(f * PASTEL.size).toInt().coerceIn(0, PASTEL.size - 1)] }
        "twilight" -> lerp(TW_A, TW_B, t.coerceIn(0f, 1f))
        // Glow on the look's colour: the look's accent, from a deeper shade of it to a bright one
        "lookglow" -> lerp(lerp(base, Color.Black, 0.2f), lerp(base, Color.White, 0.5f), (t * 0.7f + lv * 0.3f).coerceIn(0f, 1f))
        "fire" -> lerp(FIRE_LOW, FIRE_HIGH, (lv * 0.8f + (1f - t) * 0.2f).coerceIn(0f, 1f))
        "ice" -> lerp(ICE_LOW, ICE_HIGH, (lv * 0.7f + t * 0.3f).coerceIn(0f, 1f))
        else -> grad?.at(t, lv) ?: neon ?: base
    }
    val main: Color get() = at(0.5f, 0.6f)
    /** The same, in another colour (a Dual one's bottom style in its own). */
    fun withKey(k: String) = VisPalette(k, base, shift)
    /** Left to right across the spectrum's colours. */
    fun brush(alpha: Float): Brush = Brush.horizontalGradient(List(7) { at(it / 6f, 0.6f).copy(alpha = alpha) })

    companion object {
        /** Glow on the look's colour: shades of the look's accent (it used to be a fixed blue-to-violet). */
        fun of(style: String, key: String, base: Color, phase: Float): VisPalette {
            // A style you made with a colour of its own keeps it; one made from Glow keeps Glow's look on the look's colour
            val own = MyVis.style(style)
            val k = own?.color?.takeIf { it.isNotEmpty() } ?: key
            val from = own?.base ?: style
            return VisPalette(if (from == "glow" && k == "auto") "lookglow" else k, base, phase * 0.05f)
        }
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
            if (s.step(target, if (org.omarchy.music.Remote.active) org.omarchy.music.Remote.status.paused else Music.paused, dt)) s.frame++
        }
    }
    return s
}

/** Draws a style. backdrop: behind Now playing (no solid backgrounds or faint unlit parts, everything to the bottom edge). */
/** How much farther Starfield's stars fly than its area's half diagonal: Now playing's full screen sets it so they reach
 *  every edge of the screen from the cover's middle, wherever the cover is. */
var starReach = 1f

fun DrawScope.drawVis(style: String, s: VisState, pal: VisPalette, fg: Color, backdrop: Boolean) {
    MyVis.style(style)?.let { drawMyVis(it, s, pal, fg, backdrop); return }
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
            val reach = hypot(w, h) / 2 * starReach   // Now playing's full screen: out to the screen's farthest corner
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
        // ───── 15 more along the bottom ─────
        "skyline" -> {   // a city at night: buildings as tall as the bands, their windows lit by the music
            val groups = 24; val per = max(1, n / groups); val gw = w / groups
            for (g in 0 until groups) {
                var v = 0f; for (b in g * per until min(n, (g + 1) * per)) v = max(v, lv[b])
                val bh = max(base * 0.06f, v * base * 0.9f); val x = g * gw + gw * 0.08f; val bwid = gw * 0.84f
                drawRect(pal.at(g / (groups - 1f), v).copy(alpha = 0.25f + 0.35f * v), Offset(x, base - bh), Size(bwid, bh))
                var y = base - bh + 6f * u
                while (y < base - 6f * u) {
                    var xx = x + 4f * u
                    while (xx < x + bwid - 6f * u) {
                        if (((xx * 13 + y * 7).toInt() % 3) < (1 + (v * 2).roundToInt())) drawRect(Color(0xFFFFE08A).copy(alpha = 0.35f + 0.6f * v), Offset(xx, y), Size(3f * u, 3f * u))
                        xx += 7f * u
                    }
                    y += 8f * u
                }
            }
        }
        "capsules" -> {   // round-ended pills standing on the floor, a dot of light on each peak
            for (b in 0 until n step 2) {
                val v = max(lv[b], lv[min(n - 1, b + 1)]); val bh = max(bw * 2, v * base * 0.92f)
                val x = b * (bw + gap); val wid = bw * 2 + gap * 0.5f
                drawRoundRect(pal.at(t(b), v).copy(alpha = 0.4f + 0.6f * v), Offset(x, base - bh), Size(wid, bh), CornerRadius(wid / 2))
                drawCircle(Color.White.copy(alpha = 0.5f * s.peaks[b]), wid * 0.32f, Offset(x + wid / 2, base - s.peaks[b] * base * 0.92f - wid * 0.6f))
            }
        }
        "hills" -> {   // rolling hills, three layers from the bass to the treble, the nearest the loudest
            for (layer in 0 until 3) {
                val path = Path(); path.moveTo(0f, base)
                val steps = 48
                for (i in 0..steps) {
                    val b = (i * (n - 1) / steps).coerceIn(0, n - 1)
                    val v = lv[(b + layer * n / 6) % n]
                    val y = base - (0.12f + 0.25f * layer + 0.55f * v) * base * (1f - layer * 0.22f)
                    path.lineTo(i * w / steps, y)
                }
                path.lineTo(w, base); path.close()
                drawPath(path, pal.at(layer / 2f, 0.6f).copy(alpha = 0.22f + 0.2f * (2 - layer)))
            }
        }
        "steps" -> {   // a staircase traced along the spectrum, each step a band, glowing on its edge
            val path = Path(); path.moveTo(0f, base)
            for (b in 0 until n) { val y = base - lv[b] * base * 0.9f; val x0 = b * w / n; path.lineTo(x0, y); path.lineTo(x0 + w / n, y) }
            path.lineTo(w, base)
            drawPath(path, pal.brush(0.9f), style = Stroke(width = 3f * u, join = StrokeJoin.Miter))
            path.close(); drawPath(path, pal.brush(0.18f))
        }
        "needles" -> {   // fine needles, a bright bead riding on each tip
            for (b in 0 until n) {
                val v = lv[b]; val x = b * (bw + gap) + bw / 2; val y = base - v * base * 0.92f
                drawLine(pal.at(t(b), v).copy(alpha = 0.3f + 0.6f * v), Offset(x, base), Offset(x, y), strokeWidth = 1.6f * u)
                drawCircle(lerp(pal.at(t(b), v), Color.White, 0.4f).copy(alpha = 0.5f + 0.5f * v), (2f + 3f * v) * u, Offset(x, y))
            }
        }
        "ribbon" -> {   // a ribbon of the spectrum mirrored above and below a line, softly twisting
            val mid = if (backdrop) base * 0.7f else h / 2
            val top = Path(); val bot = Path()
            for (i in 0 until n) {
                val x = i * w / (n - 1); val a = lv[i] * mid * 0.8f * (0.75f + 0.25f * sin(s.phase * 1.3f + i * 0.2f))
                if (i == 0) { top.moveTo(x, mid - a); bot.moveTo(x, mid + a) } else { top.lineTo(x, mid - a); bot.lineTo(x, mid + a) }
            }
            val shape = Path().apply { addPath(top); for (i in n - 1 downTo 0) { val x = i * w / (n - 1); val a = lv[i] * mid * 0.8f * (0.75f + 0.25f * sin(s.phase * 1.3f + i * 0.2f)); lineTo(x, mid + a) }; close() }
            drawPath(shape, pal.brush(0.35f))
            drawPath(top, pal.brush(1f), style = Stroke(width = 2.5f * u)); drawPath(bot, pal.brush(0.7f), style = Stroke(width = 1.5f * u))
        }
        "dotbars" -> {   // columns of round dots, lit from the floor up
            val rows = 16; val cell = base / rows
            for (b in 0 until n step 2) {
                val lit = (lv[b] * rows).roundToInt(); val x = b * (bw + gap) + bw
                for (r in 0 until lit) drawCircle(pal.at(t(b), r / (rows - 1f)).copy(alpha = 0.5f + 0.5f * r / rows), cell * 0.36f, Offset(x, base - (r + 0.5f) * cell))
            }
        }
        "flames" -> {   // flames licking up from the floor, taller and brighter where it's loud, flickering
            for (b in 0 until n step 2) {
                val v = lv[b]; val x = b * (bw + gap) + bw; val fh = (0.08f + v * 0.85f) * base * (0.9f + 0.1f * sin(s.phase * 9f + b))
                val wid = bw * 2.6f
                val p = Path().apply {
                    moveTo(x - wid / 2, base)
                    cubicTo(x - wid / 2, base - fh * 0.5f, x - wid * 0.1f, base - fh * 0.7f, x + wid * 0.08f * sin(s.phase * 6f + b), base - fh)
                    cubicTo(x + wid * 0.1f, base - fh * 0.7f, x + wid / 2, base - fh * 0.5f, x + wid / 2, base); close()
                }
                drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFF3B0).copy(alpha = 0.9f), pal.at(t(b), v).copy(alpha = 0.8f), Color(0xFFB81F3D).copy(alpha = 0.2f)), base - fh, base))
            }
        }
        "tides" -> {   // three tides rolling in, the music of the last moment in each
            for (k in 2 downTo 0) {
                val row = s.row(k * 8); val path = Path(); path.moveTo(0f, base)
                for (i in 0 until n) { val x = i * w / (n - 1); val y = base - (0.15f + 0.22f * k + row[i] * 0.55f) * base * 0.8f + sin(s.phase * 1.5f + i * 0.3f + k) * 6f * u; path.lineTo(x, y) }
                path.lineTo(w, base); path.close()
                drawPath(path, pal.at(k / 2f, 0.6f).copy(alpha = 0.18f + 0.14f * (2 - k)))
            }
        }
        "chunky" -> {   // 8-bit: big square pixels stacked like an old game's meter
            val cols = 16; val per = max(1, n / cols); val cw = w / cols; val px = cw * 0.86f; val rows = (base / cw).toInt().coerceAtLeast(4)
            for (g in 0 until cols) {
                var v = 0f; for (b in g * per until min(n, (g + 1) * per)) v = max(v, lv[b])
                val lit = (v * rows).roundToInt()
                for (r in 0 until lit) drawRect(pal.at(g / (cols - 1f), r / (rows - 1f)), Offset(g * cw + (cw - px) / 2, base - (r + 1) * cw + (cw - px) / 2), Size(px, px))
            }
        }
        "bounce" -> {   // balls bouncing on the floor, each as high as its part of the spectrum
            val balls = 20; val per = max(1, n / balls); val gw = w / balls
            for (g in 0 until balls) {
                var v = 0f; for (b in g * per until min(n, (g + 1) * per)) v = max(v, lv[b])
                val r = gw * 0.3f; val y = base - r - v * (base - 2 * r) * 0.9f
                drawCircle(pal.at(g / (balls - 1f), v).copy(alpha = 0.6f + 0.4f * v), r, Offset(g * gw + gw / 2, y))
                drawOval(Color.Black.copy(alpha = 0.25f * (1f - v)), Offset(g * gw + gw / 2 - r, base - r * 0.2f), Size(r * 2, r * 0.4f))
            }
        }
        "grass" -> {   // a meadow: blades of grass growing with the music, swaying in its breeze
            val blades = 90
            for (i in 0 until blades) {
                val b = (i * n / blades).coerceIn(0, n - 1); val v = lv[b]; val x = i * w / blades + (i % 3) * 2f
                val hgt = (0.1f + v * 0.8f) * base * (0.7f + 0.3f * ((i * 37) % 10) / 10f)
                val sway = sin(s.phase * 2f + i * 0.5f) * 10f * u * (0.3f + v)
                val p = Path().apply { moveTo(x - 2f * u, base); quadraticBezierTo(x + sway * 0.5f, base - hgt * 0.6f, x + sway, base - hgt); quadraticBezierTo(x + sway * 0.5f + 1f, base - hgt * 0.6f, x + 2f * u, base); close() }
                drawPath(p, pal.at(t(b), v).copy(alpha = 0.5f + 0.5f * v))
            }
        }
        "outlines" -> {   // bars drawn as outlines, a solid cap held at each peak
            for (b in 0 until n) {
                val v = lv[b]; val bh = max(2f, v * base * 0.92f); val x = b * (bw + gap); val col = pal.at(t(b), v)
                drawRect(col.copy(alpha = 0.4f + 0.6f * v), Offset(x, base - bh), Size(bw, bh), style = Stroke(width = 1.4f * u))
                drawRect(col, Offset(x, base - s.peaks[b] * base * 0.92f - 3f * u), Size(bw, 3f * u))
            }
        }
        "curtain" -> {   // a curtain of light: smooth columns with no gaps, fading up from the floor
            val cw = w / n
            for (b in 0 until n) {
                val v = lv[b]; val top = base - v * base * 0.95f
                drawRect(Brush.verticalGradient(listOf(pal.at(t(b), v).copy(alpha = 0f), pal.at(t(b), v).copy(alpha = 0.85f)), top, base), Offset(b * cw, top), Size(cw + 1f, base - top))
            }
        }
        "echoes" -> {   // the last moments as lines rising up and fading, the newest at the floor
            for (k in 0 until 14) {
                val row = s.row(k * 3); val path = Path()
                val lift = k * base * 0.045f
                for (i in 0 until n) { val x = i * w / (n - 1); val y = base - lift - row[i] * base * 0.35f; if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                drawPath(path, pal.at(k / 13f, 0.7f).copy(alpha = (1f - k / 14f) * 0.9f), style = Stroke(width = (2.5f - k * 0.12f).coerceAtLeast(0.6f) * u))
            }
        }
        // ───── 15 more around the middle (on Now playing, out of the cover's centre) ─────
        "orbits" -> {   // planets on rings round a sun, each ring a part of the spectrum, swinging out when it's loud
            drawCircle(pal.at(0f, s.bass).copy(alpha = 0.6f + 0.4f * s.bass), half * (0.08f + 0.06f * s.bass), c)
            for (k in 0 until 7) {
                val v = band(k * n / 7, (k + 1) * n / 7); val r = half * (0.2f + k * 0.11f) * (1f + 0.08f * v)
                drawCircle(pal.at(k / 6f, 0.4f).copy(alpha = 0.15f), r, c, style = Stroke(width = 1f * u))
                val a = s.phase * (0.9f - k * 0.09f) + k * 1.7f
                drawCircle(pal.at(k / 6f, v).copy(alpha = 0.5f + 0.5f * v), (2.5f + 7f * v) * u, c + Offset(cos(a), sin(a)) * r)
            }
        }
        "spiral" -> {   // a spiral of dots unwinding from the middle, each dot a band, turning
            for (i in 0 until n * 2) {
                val b = i % n; val v = lv[b]; val f = i / (n * 2f)
                val a = f * 6f * PI.toFloat() + s.phase * 0.6f; val r = half * (0.06f + 0.9f * f)
                drawCircle(pal.at(t(b), v).copy(alpha = 0.3f + 0.7f * v), (1.5f + 5f * v) * u, c + Offset(cos(a), sin(a)) * r)
            }
        }
        "polygons" -> {   // nested polygons turning against each other, pushed out by the music
            for (k in 0 until 6) {
                val sides = 3 + k; val v = band(k * n / 6, (k + 1) * n / 6)
                val r = half * (0.18f + k * 0.13f) * (1f + 0.15f * v); val rot = s.phase * (if (k % 2 == 0) 0.4f else -0.3f)
                val p = Path()
                for (j in 0..sides) { val a = rot + j * 2f * PI.toFloat() / sides; val q = c + Offset(cos(a), sin(a)) * r; if (j == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y) }
                drawPath(p, pal.at(k / 5f, v).copy(alpha = 0.35f + 0.65f * v), style = Stroke(width = (1.5f + 3f * v) * u, join = StrokeJoin.Round))
            }
        }
        "radar" -> {   // a radar sweeping round, blips where the spectrum is loud
            for (k in 1..4) drawCircle(pal.main.copy(alpha = 0.15f), half * 0.24f * k, c, style = Stroke(width = 1f * u))
            val sweep = s.phase * 1.6f
            drawArc(Brush.sweepGradient(listOf(Color.Transparent, pal.main.copy(alpha = 0.35f)), c), sweep * 180f / PI.toFloat() - 60f, 60f, true, c - Offset(half * 0.96f, half * 0.96f), Size(half * 1.92f, half * 1.92f))
            for (b in 0 until n step 2) {
                val v = lv[b]; if (v < 0.2f) continue
                val a = b * 2f * PI.toFloat() / n; val r = half * (0.2f + 0.75f * (1f - v))
                drawCircle(pal.at(t(b), v).copy(alpha = v), (2f + 4f * v) * u, c + Offset(cos(a), sin(a)) * r)
            }
        }
        "lightrays" -> {   // beams of light fanning out from the middle, long and bright where it's loud
            for (b in 0 until n) {
                val v = lv[b]; val a = b * 2f * PI.toFloat() / n + s.phase * 0.15f
                val dir = Offset(cos(a), sin(a)); val r0 = half * 0.12f; val r1 = r0 + half * (0.15f + 0.85f * v)
                drawLine(Brush.linearGradient(listOf(pal.at(t(b), v).copy(alpha = 0.8f * v + 0.1f), Color.Transparent), c + dir * r0, c + dir * r1), c + dir * r0, c + dir * r1, strokeWidth = (2f + 4f * v) * u, cap = StrokeCap.Round)
            }
        }
        "atom" -> {   // an atom: three tilted orbits, electrons racing round with the music, a nucleus beating with the bass
            drawCircle(pal.at(0f, s.bass), half * (0.07f + 0.07f * s.bass), c)
            for (k in 0 until 3) {
                val v = band(k * n / 3, (k + 1) * n / 3); val tilt = k * 60f + s.phase * 8f
                val rx = half * 0.85f; val ry = half * (0.25f + 0.1f * v)
                rotateIf(tilt, c) {
                    drawOval(pal.at(k / 2f, 0.5f).copy(alpha = 0.35f), c - Offset(rx, ry), Size(rx * 2, ry * 2), style = Stroke(width = (1.2f + 2f * v) * u))
                    val a = s.phase * (2f + k) + k
                    drawCircle(pal.at(k / 2f, v), (3f + 6f * v) * u, c + Offset(cos(a) * rx, sin(a) * ry))
                }
            }
        }
        "mandala" -> {   // a mandala: petals in rings, mirrored all round, opening with the music
            for (ringI in 0 until 3) {
                val petals = 8 + ringI * 4; val v = band(ringI * n / 3, (ringI + 1) * n / 3)
                val r = half * (0.25f + ringI * 0.25f); val len = half * (0.12f + 0.2f * v)
                for (j in 0 until petals) {
                    val a = j * 2f * PI.toFloat() / petals + s.phase * 0.1f * (if (ringI % 2 == 0) 1 else -1)
                    val dir = Offset(cos(a), sin(a)); val side = Offset(-dir.y, dir.x)
                    val p = Path().apply { val b0 = c + dir * (r - len); moveTo(b0.x, b0.y); val m = c + dir * r; val t0 = c + dir * (r + len)
                        quadraticBezierTo(m.x + side.x * len * 0.5f, m.y + side.y * len * 0.5f, t0.x, t0.y); quadraticBezierTo(m.x - side.x * len * 0.5f, m.y - side.y * len * 0.5f, b0.x, b0.y); close() }
                    drawPath(p, pal.at(ringI / 2f, v).copy(alpha = 0.3f + 0.6f * v))
                }
            }
        }
        "ripples" -> {   // rings on water, each one bent by its part of the spectrum
            for (k in 0 until 8) {
                val p = Path(); val base0 = half * (0.12f + k * 0.11f) * (1f + 0.05f * ((s.phase * 0.5f + k * 0.13f) % 1f))
                val steps = 72
                for (j in 0..steps) {
                    val a = j * 2f * PI.toFloat() / steps; val b = (j * n / steps).coerceIn(0, n - 1)
                    val r = base0 + lv[(b + k * 4) % n] * half * 0.06f
                    val q = c + Offset(cos(a), sin(a)) * r; if (j == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y)
                }
                drawPath(p, pal.at(k / 7f, 0.6f).copy(alpha = 0.6f - k * 0.06f), style = Stroke(width = 1.6f * u))
            }
        }
        "nova" -> {   // a supernova: each moment's spectrum flung out as a ring, the newest in the middle
            for (k in 0 until 16) {
                val row = s.row(k * 2); val r = half * (0.08f + k * 0.058f)
                for (b in 0 until n step 3) {
                    val v = row[b]; if (v < 0.15f) continue
                    val a = b * 2f * PI.toFloat() / n
                    drawCircle(pal.at(t(b), v).copy(alpha = v * (1f - k / 16f)), (1f + 3f * v) * u, c + Offset(cos(a), sin(a)) * r)
                }
            }
        }
        "honeycomb" -> {   // a honeycomb round the middle, its cells lighting up with the bands
            val cell = half * 0.12f; val hh = cell * sqrt3
            var i = 0
            for (q in -4..4) for (r in -4..4) {
                if (abs(q + r) > 4) continue
                val x = c.x + cell * 1.5f * q; val y = c.y + hh * (r + q / 2f)
                if (hypot(x - c.x, y - c.y) > half * 0.98f) continue
                val v = lv[(i * 7) % n]; i++
                val p = Path(); for (j in 0..6) { val a = j * PI.toFloat() / 3; val px = x + cell * 0.92f * cos(a); val py = y + cell * 0.92f * sin(a); if (j == 0) p.moveTo(px, py) else p.lineTo(px, py) }
                drawPath(p, pal.at(t((i * 7) % n), v).copy(alpha = 0.08f + 0.8f * v))
            }
        }
        "heart" -> {   // a heart beating with the bass, an outline per part of the spectrum
            for (k in 0 until 5) {
                val v = if (k == 0) s.bass else band(k * n / 5, (k + 1) * n / 5)
                val sc = half * (0.18f + k * 0.14f) * (1f + 0.12f * v)
                val p = Path()
                for (j in 0..80) {
                    val tt = j * 2f * PI.toFloat() / 80
                    val x = 16f * sin(tt).pow(3); val y = -(13f * cos(tt) - 5f * cos(2 * tt) - 2f * cos(3 * tt) - cos(4 * tt))
                    val q = c + Offset(x, y) * (sc / 17f); if (j == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y)
                }
                if (k == 0) drawPath(p, pal.at(0f, v).copy(alpha = 0.5f + 0.5f * v)) else drawPath(p, pal.at(k / 4f, v).copy(alpha = 0.25f + 0.6f * v), style = Stroke(width = (1.5f + 2f * v) * u))
            }
        }
        "sunflower" -> {   // seeds of a sunflower on the golden angle, each glowing with a band
            val seeds = 220
            for (i in 0 until seeds) {
                val a = i * 2.39996f + s.phase * 0.05f; val r = half * 0.93f * kotlin.math.sqrt(i / seeds.toFloat())
                val b = (i * n / seeds).coerceIn(0, n - 1); val v = lv[b]
                drawCircle(pal.at(t(b), v).copy(alpha = 0.25f + 0.75f * v), (1.5f + 4f * v) * u, c + Offset(cos(a), sin(a)) * r)
            }
        }
        "vortex" -> {   // arms swirling into the middle, longer and brighter where it's loud
            val arms = 12
            for (k in 0 until arms) {
                val v = band(k * n / arms, (k + 1) * n / arms); val p = Path()
                for (j in 0..30) {
                    val f = j / 30f; val a = k * 2f * PI.toFloat() / arms + f * 3f + s.phase * 0.8f
                    val r = half * (0.05f + 0.9f * f * (0.5f + 0.5f * v))
                    val q = c + Offset(cos(a), sin(a)) * r; if (j == 0) p.moveTo(q.x, q.y) else p.lineTo(q.x, q.y)
                }
                drawPath(p, pal.at(k / (arms - 1f), v).copy(alpha = 0.3f + 0.7f * v), style = Stroke(width = (1.2f + 3f * v) * u, cap = StrokeCap.Round))
            }
        }
        "iris" -> {   // an eye's iris: fine fibres round a pupil that opens and closes with the bass
            val pupil = half * (0.12f + 0.08f * (1f - s.bass)); val outer = half * 0.9f
            for (b in 0 until n * 2) {
                val bb = b % n; val v = lv[bb]; val a = b * PI.toFloat() / n
                val dir = Offset(cos(a), sin(a))
                drawLine(pal.at(t(bb), v).copy(alpha = 0.3f + 0.6f * v), c + dir * (pupil * 1.15f), c + dir * (pupil * 1.15f + (outer - pupil) * (0.5f + 0.5f * v)), strokeWidth = 1.5f * u)
            }
            drawCircle(Color.Black.copy(alpha = 0.85f), pupil, c)
            drawCircle(pal.main.copy(alpha = 0.5f), outer, c, style = Stroke(width = 2f * u))
        }
        "clockwork" -> {   // gears of ticks turning at different speeds, each ring a part of the spectrum
            for (k in 0 until 5) {
                val ticks = 24 + k * 12; val v = band(k * n / 5, (k + 1) * n / 5)
                val r = half * (0.2f + k * 0.17f); val rot = s.phase * (0.6f - k * 0.1f) * (if (k % 2 == 0) 1 else -1)
                for (j in 0 until ticks) {
                    val a = rot + j * 2f * PI.toFloat() / ticks; val dir = Offset(cos(a), sin(a))
                    val len = half * (0.03f + 0.06f * v * (if (j % 4 == 0) 1.6f else 1f))
                    drawLine(pal.at(k / 4f, v).copy(alpha = 0.3f + 0.7f * v), c + dir * r, c + dir * (r + len), strokeWidth = 1.6f * u)
                }
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
               swatch: ((String) -> Pair<Color, Brush>?)? = null,
               describe: ((String) -> String)? = null,
               preview: (@Composable (String) -> Unit)? = null,
               thumb: (@Composable (String) -> Unit)? = null,
               chosen: Set<String>? = null,   // several at once (ticked; the menu stays open): these are on
               later: Boolean = false,        // the phone: put on when the menu closes (a layout rebuilds the screen, menu and all)
               menu: ((String, String) -> List<Pair<String, () -> Unit>>)? = null,   // right-click (or hold) on one: these
               pick: (String) -> Unit) {
    Box {
        var open by remember { mutableStateOf(false) }
        // Right-click (the PC) or a long press (the phone) on one: its little menu (Favourite, Delete…); that press doesn't pick it
        var ctx by remember { mutableStateOf<String?>(null) }
        var skip by remember { mutableStateOf<String?>(null) }
        // Delete asked about right in the menu (Yes / Cancel), so the menu stays open after it
        var sure by remember { mutableStateOf<String?>(null) }
        // The one the pointer is over (the PC), else the one in use: its picture and description at the top of the menu
        var over by remember { mutableStateOf<String?>(null) }
        // Where each item is in the menu (its distance from the menu's left and right edges): its picture goes beside the menu
        var menuRow by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
        val spots = remember { androidx.compose.runtime.mutableStateMapOf<String, Pair<Int, Int>>() }
        val name = if (chosen != null) options.filter { it.first in chosen }.let { on ->
                when (on.size) { 0 -> "None"; 1, 2 -> on.joinToString(", ") { it.second }; else -> "${on.size} on" } }
            else options.firstOrNull { it.first == current }?.second ?: options.firstOrNull()?.second.orEmpty()
        // The phone: the one last tapped in the menu, its picture shown under it
        var tapped by remember { mutableStateOf<String?>(null) }
        val stays = onPhone && preview != null
        Pill("$prefix$name \u25BE", quiet = true) { open = true; over = null; tapped = if (stays && chosen == null) current else null }
        val dismiss = {
            open = false
            if (later && stays) tapped?.let { if (it != current) pick(it) }
            Unit
        }
        val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
            // On the PC each one's picture pokes out beside it as the pointer goes over it. On the phone (no pointer) a tap
            // puts that one on and shows its picture right under it, and the menu stays open to try others: Back or a tap
            // outside closes it.
            // Too many to fit the screen's height (the PC): more columns, growing to the left, a line between them
            val item: @Composable (String, String) -> Unit = { k, label ->
                val text: @Composable () -> Unit = {
                    Column {
                        val on = if (chosen != null) k in chosen else k == (tapped ?: current)
                        Text((if (chosen != null) (if (on) "✓ " else "    ") else "") + label,
                            style = T.body.copy(color = if (on) C.accent else C.fg,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal))
                        // On a phone there's no pointer to hover with: each one's description under its name
                        if (onPhone) describe?.invoke(k)?.takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = T.sub.copy(fontSize = 11.sp), maxLines = 2) }
                    }
                }
                val sw = swatch?.invoke(k)
                val hover = Modifier.pointerInput(k) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Enter ||
                                e.type == androidx.compose.ui.input.pointer.PointerEventType.Move) over = k
                            if (menu != null && e.type == androidx.compose.ui.input.pointer.PointerEventType.Press && e.buttons.isSecondaryPressed) {
                                skip = k; ctx = k; e.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                val lead: (@Composable () -> Unit)? = when {
                    thumb != null -> ({ thumb(k) })
                    sw != null -> ({ VisColorDot(sw.first, sw.second) })
                    else -> null
                }
                Column(Modifier.onGloballyPositioned { c -> menuRow?.let { r -> if (r.isAttached && c.isAttached)
                        spots[k] = r.localPositionOf(c, androidx.compose.ui.geometry.Offset.Zero).x.toInt() to
                            (r.size.width - r.localPositionOf(c, androidx.compose.ui.geometry.Offset.Zero).x.toInt() - c.size.width) } }) {
                    val tap = {
                        if (skip == k) skip = null   // that was a right-click or a long press
                        // Several at once (Effects) on the phone: the first tap only shows its picture, a tap on it again
                        // puts it on or off
                        else if (stays && chosen != null && tapped != k) tapped = k
                        else { if (chosen == null && !stays) open = false; tapped = k; if (!(later && stays)) pick(k) }
                        Unit
                    }
                    if (onPhone && menu != null) {
                        // The phone: a tap picks it, holding a finger on it opens its Edit / Favourite / Delete right under it
                        Row(Modifier.fillMaxWidth().combinedClickable(onClick = tap, onLongClick = { ctx = if (ctx == k) null else k })
                            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            lead?.invoke()
                            Box(Modifier.weight(1f)) { text() }
                            // ⋯ opens the same choices with a plain tap, for when a hold isn't caught
                            Text("\u22EF", style = T.title.copy(color = if (ctx == k) C.accent else C.muted),
                                modifier = Modifier.clip(R.pill).clickable { ctx = if (ctx == k) null else k }.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                        if (ctx == k) Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (sure == k) {
                                Text("Delete it?", style = T.body.copy(color = C.red), modifier = Modifier.align(Alignment.CenterVertically))
                                Pill("Yes, delete") { sure = null; ctx = null
                                    menu(k, label).firstOrNull { it.first == "Delete" }?.second?.let { Favs.direct = true; try { it() } finally { Favs.direct = false } } }
                                Pill("Cancel", quiet = true) { sure = null }
                            } else {
                                menu(k, label).forEach { (what, act) ->
                                    Pill(what, quiet = what != "Edit") { if (what == "Delete") sure = k else { ctx = null; if (what == "Edit") open = false; act() } }
                                }
                                Pill("Cancel", quiet = true) { ctx = null }
                            }
                        }
                    } else DropdownMenuItem(text = text, onClick = tap, leadingIcon = lead, modifier = hover)
                    if (menu != null && ctx == k && !onPhone) DropdownMenu(true, { ctx = null; skip = null; sure = null }, containerColor = C.light) {
                        Text(label.removePrefix("★ "), style = T.sub, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                        if (sure == k) {
                            Text("Delete it?", style = T.body.copy(color = C.red), modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                            DropdownMenuItem(text = { Text("Yes, delete", style = T.body.copy(color = C.red)) }, onClick = {
                                sure = null; ctx = null; skip = null
                                menu(k, label).firstOrNull { it.first == "Delete" }?.second?.let { Favs.direct = true; try { it() } finally { Favs.direct = false } } })
                            DropdownMenuItem(text = { Text("Cancel", style = T.body) }, onClick = { sure = null })
                        } else menu(k, label).forEach { (what, act) ->
                            DropdownMenuItem(text = { Text(what, style = T.body.copy(color = if (what == "Delete") C.red else C.fg)) },
                                onClick = { if (what == "Delete") sure = k else { ctx = null; skip = null; if (what == "Edit") open = false; act() } })
                        }
                    }
                    if (stays && tapped == k) Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp).widthIn(max = 320.dp)) {
                        preview?.invoke(k)
                    }
                    if (!onPhone && over == k && (preview != null || describe != null)) {
                        val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.roundToPx() }
                        androidx.compose.ui.window.Popup(popupPositionProvider = remember(gap, spots[k]) { BesideMenu(gap, spots[k]?.first ?: 0, spots[k]?.second ?: 0) }) {
                            Column(Modifier.shadow(12.dp, R.big).clip(R.big).background(C.light).border(1.dp, C.line, R.big)
                                .padding(10.dp).widthIn(max = 380.dp)) {
                                preview?.invoke(k)
                                Text(label, style = T.title.copy(fontSize = 14.sp),
                                    modifier = Modifier.padding(top = if (preview != null) 8.dp else 0.dp))
                                describe?.invoke(k)?.takeIf { it.isNotEmpty() }?.let { Text(it, style = T.sub) }
                            }
                        }
                    }
                }
            }
            val perCol = if (onPhone) options.size else ((screenHeightDp() - 72) / 48).coerceAtLeast(6)
            // Each one keyed by what it is, so after a favourite moves to the top its taps and holds still go to it
            if (options.size <= perCol) options.forEach { (k, label) -> androidx.compose.runtime.key(k) { item(k, label) } }
            else {
                val cols = (options.size + perCol - 1) / perCol
                val chunks = options.chunked((options.size + cols - 1) / cols)
                Row(Modifier.onGloballyPositioned { menuRow = it }) {
                    chunks.reversed().forEachIndexed { i, chunk ->
                        if (i > 0) Box(Modifier.padding(vertical = 6.dp).width(1.dp).height((chunks.first().size * 48).dp).background(C.line))
                        Column(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
                            chunk.forEach { (k, label) -> androidx.compose.runtime.key(k) { item(k, label) } }
                        }
                    }
                }
            }
        }
        // The phone: the menu in a box of its own that always ends above the phone's buttons (Back, Home), so the last
        // ones can be tapped; it scrolls inside that. The PC: a dropdown under the pill.
        if (onPhone) {
            if (open) androidx.compose.ui.window.Dialog(onDismissRequest = dismiss) {
                Column(Modifier.fillMaxWidth().heightIn(max = (screenHeightDp() * 0.82f).dp).clip(R.big).background(C.light)
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(vertical = 8.dp), content = body)
            }
        } else DropdownMenu(open, dismiss, containerColor = C.light, content = body)
    }
}

/** Holding a finger on it (the phone): onLong, seen before the item's own tap (which would otherwise take the press),
 *  and that press then doesn't tap it. Moving the finger (scrolling) or lifting it early is an ordinary touch. */
fun Modifier.holdForMenu(onLong: () -> Unit): Modifier = pointerInput(onLong) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) break
            }
            true
        }
        if (ended == null) {   // held long enough without moving: the menu, and the rest of this press is swallowed
            onLong()
            while (true) {
                val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                e.changes.forEach { it.consume() }
                if (e.changes.none { it.pressed }) break
            }
        }
    }
}

/** A chip with a little menu on right-click (the PC) or a long press (the phone). */
@Composable
fun MenuChip(label: String, on: Boolean, menu: () -> List<Pair<String, () -> Unit>>, pick: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.pointerInput(label) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Press && e.buttons.isSecondaryPressed) {
                    open = true; e.changes.forEach { it.consume() }
                }
            }
        }
    }) {
        if (onPhone) Text(label, style = T.tab.copy(color = if (on) C.accent else C.muted),   // the phone: hold it for its menu
            modifier = Modifier.clip(R.pill).background(if (on) C.accent.copy(alpha = 0.16f) else C.fg.copy(alpha = 0.05f))
                .border(1.dp, if (on) C.accent.copy(alpha = 0.5f) else Color.Transparent, R.pill)
                .combinedClickable(onClick = pick, onLongClick = { open = true }).padding(horizontal = 12.dp, vertical = 6.dp))
        else Chip(label, on) { pick() }
        DropdownMenu(open, { open = false }, containerColor = C.light) {
            Text(label.removePrefix("★ "), style = T.sub, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            menu().forEach { (what, act) ->
                DropdownMenuItem(text = { Text(what, style = T.body.copy(color = if (what == "Delete") C.red else C.fg)) },
                    onClick = { open = false; act() })
            }
        }
    }
}

/** Where a menu item's picture goes: to the left of the menu, level with the item (kept on screen); to the right when
 *  there's no room on the left. */
private class BesideMenu(val gap: Int, val fromLeft: Int = 0, val fromRight: Int = 0) : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize,
                                   layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                                   popupContentSize: androidx.compose.ui.unit.IntSize): androidx.compose.ui.unit.IntOffset {
        val w = popupContentSize.width; val h = popupContentSize.height
        val menuLeft = anchorBounds.left - fromLeft; val menuRight = anchorBounds.right + fromRight
        fun level() = (anchorBounds.top + anchorBounds.height / 2 - h / 2).coerceIn(0, (windowSize.height - h).coerceAtLeast(0))
        // Beside the menu (left first, then right), never over it: over it, it would sit under the pointer
        if (menuLeft - gap - w >= 0) return androidx.compose.ui.unit.IntOffset(menuLeft - gap - w, level())
        if (menuRight + gap + w <= windowSize.width) return androidx.compose.ui.unit.IntOffset(menuRight + gap, level())
        // A small window: no room beside it. Under the item pointed at (or above it), clear of that item's row
        val x = ((anchorBounds.left + anchorBounds.right) / 2 - w / 2).coerceIn(0, (windowSize.width - w).coerceAtLeast(0))
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - h
        val y = when {
            below + h <= windowSize.height -> below
            above >= 0 -> above
            windowSize.height - anchorBounds.bottom >= anchorBounds.top -> below
            else -> above.coerceAtLeast(0)
        }
        return androidx.compose.ui.unit.IntOffset(x, y)
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
    if (!allowed && !org.omarchy.music.Remote.active) {   // the PC's remote: the PC's sound, no permission needed
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
                // Right-click (or hold) one: ★ Favourite (to the front), or Delete one you made
                Favs.sorted(Favs.VIS, VIS_STYLES).forEach { (k, label) -> androidx.compose.runtime.key(k) { MenuChip(label, style == k, { Favs.menu(Favs.VIS, k, label) }) { Music.chooseVisPreset(k) } } }
                // Your own style or colour blend (and the one picked, if it's yours, to change it)
                Chip(if (style.startsWith("myvis-")) "✎ Change yours" else "+ Make your own", false) {
                    Ui.current?.let { it.makerEdit = style.takeIf { s -> s.startsWith("myvis-") }; it.sheet = "vismaker" }
                }
            }
            if (styleScroll.canScrollForward) Chip("›", false) { step(420f) }
            Spacer(Modifier.width(4.dp))
            val accent = C.accent
            ChoicePill(Favs.sorted(Favs.VIS_COLOURS, VIS_COLORS), colorKey, swatch = { visSwatch(it, accent) },
                menu = { k, l -> Favs.menu(Favs.VIS_COLOURS, k, l) }) { Prefs.setText(Vis.COLOR, it) }
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


// ───────────────────────────── Visualizers you made ─────────────────────────────

/** A style you made: one of the built-in styles (base) drawn your way. mirror: its left half mirrored onto the right;
 *  flip: upside down; reflect: a faint reflection under it; spin: turns (0 none … 1 fast); amp: how strongly it moves
 *  (0.5 calm … 2 wild); echo: a soft glow around it; color: a colour of its own ("" the one picked in the Visualizer). */
data class MyVisStyle(val key: String, val name: String, val base: String, val mirror: Boolean = false, val flip: Boolean = false,
                      val reflect: Boolean = false, val spin: Float = 0f, val amp: Float = 1f, val echo: Boolean = false, val color: String = "",
                      val full: Boolean = false, val dual: Boolean = false, val base2: String = "bars",
                      // Dual: the bottom one's own shape and colour ("" = the same colour as the middle one)
                      val mirror2: Boolean = false, val flip2: Boolean = false, val reflect2: Boolean = false, val spin2: Float = 0f,
                      val amp2: Float = 1f, val echo2: Boolean = false, val color2: String = "") {
    fun encode() = listOf(key, name.replace('|', '/'), base, mirror, flip, reflect, spin, amp, echo, color, full, dual, base2,
        mirror2, flip2, reflect2, spin2, amp2, echo2, color2).joinToString("|")
    /** A Dual one's bottom style on its own, shaped its own way. */
    fun bottom(): MyVisStyle = copy(dual = false, full = false, base = base2.takeIf { !it.startsWith("myvis-") && it !in Vis.BUILT_CENTRED } ?: "bars",
        mirror = mirror2, flip = flip2, reflect = reflect2, spin = spin2, amp = amp2, echo = echo2, color = color2)
}

/** A colour blend you made: 2 to 5 colours (0xAARRGGBB), how they spread (ALONG bass to treble, LOUD by loudness, STRIPES
 *  hard bands), and glow: the loud parts go white-hot, like the neon colours. */
data class MyVisColor(val key: String, val name: String, val stops: List<Long>, val mode: Int = 0, val glow: Boolean = false) {
    fun encode() = listOf(key, name.replace('|', '/'), mode, glow, stops.joinToString(",") { it.toString(16) }).joinToString("|")
}

/** The visualizers you made, kept with the app's settings (so backups and Sync carry them). */
object MyVis {
    const val STYLES = "my_vis_styles"
    const val COLORS = "my_vis_colors"
    const val SPREAD_ALONG = 0; const val SPREAD_LOUD = 1; const val SPREAD_STRIPES = 2
    /** Which part of a Dual one to draw next: ALL (the middle one, the bottom one in the lower part), MIDDLE or BOTTOM. */
    const val PART_ALL = 0; const val PART_MIDDLE = 1; const val PART_BOTTOM = 2
    var part = PART_ALL
    /** Draws a Dual one's part p (Now playing: the middle over the cover, the bottom along the bottom). */
    inline fun <T> drawingPart(p: Int, block: () -> T): T { val was = part; part = p; try { return block() } finally { part = was } }

    private var stylesFrom = "\u0000"; private var stylesCache: List<MyVisStyle> = emptyList()
    private var colorsFrom = "\u0000"; private var colorsCache: List<MyVisColor> = emptyList()

    fun styles(): List<MyVisStyle> {
        val text = Prefs.text(STYLES)
        if (text != stylesFrom) {
            stylesCache = text.lines().mapNotNull { l -> runCatching { val p = l.split("|")
                MyVisStyle(p[0], p[1], p[2], p[3].toBoolean(), p[4].toBoolean(), p[5].toBoolean(), p[6].toFloat(), p[7].toFloat(),
                    p[8].toBoolean(), p.getOrElse(9) { "" }, p.getOrElse(10) { "false" }.toBoolean(),
                    p.getOrElse(11) { "false" }.toBoolean(), p.getOrElse(12) { "bars" },
                    // Before the bottom one had its own shape it took the middle one's (without the spin)
                    p.getOrElse(13) { p[3] }.toBoolean(), p.getOrElse(14) { p[4] }.toBoolean(), p.getOrElse(15) { p[5] }.toBoolean(),
                    p.getOrElse(16) { "0" }.toFloat(), p.getOrElse(17) { p[7] }.toFloat(), p.getOrElse(18) { p[8] }.toBoolean(),
                    p.getOrElse(19) { "" }) }.getOrNull() }
            stylesFrom = text
        }
        return stylesCache
    }
    fun colors(): List<MyVisColor> {
        val text = Prefs.text(COLORS)
        if (text != colorsFrom) {
            colorsCache = text.lines().mapNotNull { l -> runCatching { val p = l.split("|")
                MyVisColor(p[0], p[1], p[4].split(",").filter { it.isNotBlank() }.map { it.toLong(16) }, p[2].toInt(), p[3].toBoolean())
                    .takeIf { it.stops.size >= 2 } }.getOrNull() }
            colorsFrom = text
        }
        return colorsCache
    }
    fun style(key: String): MyVisStyle? = when {
        key == "myvis-preview" -> PreviewVis.style   // the maker's, not saved yet
        key.startsWith("myvis-") -> styles().firstOrNull { it.key == key }
        else -> null
    }
    fun color(key: String): MyVisColor? = when {
        key == "mycol-preview" -> PreviewVis.color
        key.startsWith("mycol-") -> colors().firstOrNull { it.key == key }
        else -> null
    }

    fun saveStyle(v: MyVisStyle): MyVisStyle {
        val keyed = if (v.key.startsWith("myvis-")) v else v.copy(key = "myvis-" + System.currentTimeMillis().toString(36))
        Prefs.setText(STYLES, (styles().filter { it.key != keyed.key } + keyed).joinToString("\n") { it.encode() })
        return keyed
    }
    fun deleteStyle(key: String) {
        MadeSync.forget(key)
        Prefs.setText(STYLES, styles().filter { it.key != key }.joinToString("\n") { it.encode() })
        if (Music.visPreset == key) Music.chooseVisPreset("bars")
        if (Prefs.text(Vis.NOW) == key) Prefs.setText(Vis.NOW, "slim")
    }
    fun saveColor(c: MyVisColor): MyVisColor {
        val keyed = if (c.key.startsWith("mycol-")) c else c.copy(key = "mycol-" + System.currentTimeMillis().toString(36))
        Prefs.setText(COLORS, (colors().filter { it.key != keyed.key } + keyed).joinToString("\n") { it.encode() })
        return keyed
    }
    fun deleteColor(key: String) {
        MadeSync.forget(key)
        Prefs.setText(COLORS, colors().filter { it.key != key }.joinToString("\n") { it.encode() })
        if (Prefs.text(Vis.COLOR) == key) Prefs.setText(Vis.COLOR, "auto")
    }
}

/** A blend you made, as the built-in blends are drawn (glow: a white-hot top on the loud parts). */
private fun myGrad(key: String): VisGrad? = MyVis.color(key)?.let { c ->
    val stops = if (c.glow) c.stops + (lerp(Color(c.stops.last()), Color.White, 0.6f).toArgb().toLong() and 0xFFFFFFFFL) else c.stops
    VisGrad(if (c.glow) VisGrad.LOUD else c.mode, *stops.toLongArray())
}

/** A style you made: its base style drawn with your changes. The levels are scaled for amp while it's drawn, then put back. */
private fun DrawScope.drawMyVis(d: MyVisStyle, s: VisState, pal: VisPalette, fg: Color, backdrop: Boolean) {
    if (d.base.startsWith("myvis-")) return   // made from one of yours: not allowed (it could go round in circles)
    if (d.dual) {
        // Two at once, each shaped and coloured its own way: the bottom one (in its own colour, if it has one) and the middle one
        val b = d.bottom()
        val pal2 = if (d.color2.isNotEmpty()) pal.withKey(d.color2) else pal
        val middle = d.copy(dual = false)
        when (MyVis.part) {
            MyVis.PART_MIDDLE -> drawMyVis(middle, s, pal, fg, backdrop)
            MyVis.PART_BOTTOM -> drawMyVis(b, s, pal2, fg, backdrop)
            else -> {
                val h = if (b.base == "rain" || b.base == "matrix") size.height else size.height * 0.4f   // rain falls all the way down
                translate(top = size.height - h) { clipRect(0f, 0f, size.width, h) { withSize(size.width, h) { drawMyVis(b, s, pal2, fg, backdrop) } } }
                drawMyVis(middle, s, pal, fg, backdrop)
            }
        }
        return
    }
    val lv = s.levels
    val keep = lv.copyOf()
    if (d.amp != 1f) for (i in lv.indices) lv[i] = (lv[i] * d.amp).coerceIn(0f, 1f)
    val mid = Offset(size.width / 2, size.height / 2)
    fun faint(alpha: Float, block: DrawScope.() -> Unit) {
        val paint = androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha }
        drawContext.canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), paint)
        block()
        drawContext.canvas.restore()
    }
    fun DrawScope.plain(b: String, turns: Boolean) {
        val m = Offset(size.width / 2, size.height / 2)
        val turn = if (turns && d.spin > 0f) s.phase * 90f * d.spin else 0f
        rotateIf(turn, m) { if (d.flip) scaleY(-1f, m) { drawVis(b, s, pal, fg, backdrop) } else drawVis(b, s, pal, fg, backdrop) }
    }
    fun DrawScope.shaped(b: String, turns: Boolean) {
        val m = Offset(size.width / 2, size.height / 2)
        if (d.mirror) {
            clipRect(right = size.width / 2) { plain(b, turns) }
            scaleX(-1f, m) { clipRect(right = size.width / 2) { plain(b, turns) } }
        } else plain(b, turns)
    }
    fun DrawScope.whole(b: String, turns: Boolean) {
        val m = Offset(size.width / 2, size.height / 2)
        if (d.echo) faint(0.35f) { scaleBoth(1.05f, m) { shaped(b, turns) } }
        shaped(b, turns)
        if (d.reflect) faint(0.28f) { scaleY(-1f, m) { shaped(b, turns) } }
    }
    try {
        whole(d.base, true)
    } finally {
        keep.copyInto(lv)
    }
}

private val sqrt3 = kotlin.math.sqrt(3f)

private inline fun DrawScope.rotateIf(deg: Float, pivot: Offset, block: DrawScope.() -> Unit) {
    if (deg == 0f) block() else { drawContext.transform.rotate(deg, pivot); block(); drawContext.transform.rotate(-deg, pivot) }
}
private inline fun DrawScope.scaleY(k: Float, pivot: Offset, block: DrawScope.() -> Unit) {
    drawContext.transform.scale(1f, k, pivot); block(); drawContext.transform.scale(1f, 1f / k, pivot)
}
private inline fun DrawScope.scaleX(k: Float, pivot: Offset, block: DrawScope.() -> Unit) {
    drawContext.transform.scale(k, 1f, pivot); block(); drawContext.transform.scale(1f / k, 1f, pivot)
}
private inline fun DrawScope.scaleBoth(k: Float, pivot: Offset, block: DrawScope.() -> Unit) {
    drawContext.transform.scale(k, k, pivot); block(); drawContext.transform.scale(1f / k, 1f / k, pivot)
}
