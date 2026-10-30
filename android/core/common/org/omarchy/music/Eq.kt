package org.omarchy.music

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// The equalizer: a correction for your headphones, then a 10-band EQ to taste, then bass
// and treble shelves; the same filters, presets and pre-gain as the desktop app.

/** A filter: "pk" a peak or dip, "ls" / "hs" a low / high shelf; Hz, dB, Q. */
data class Filter(val kind: String, val hz: Double, val gain: Double, val q: Double)

object Eq {
    // Corrections for your headphones come from AutoEq (github.com/jaakkopasanen/AutoEq): Add
    // headphones… finds yours and brings it to the Harman target, the sound most listeners prefer.
    val HEADPHONES: LinkedHashMap<String, Pair<String, List<Filter>>> = linkedMapOf(
        "off" to ("None" to emptyList()),
        // Built in (AutoEq, to the Harman in-ear target): Xenns Mangird Top Pro, measured by two reviewers
        "mangird-top-pro" to ("Xenns Mangird Top Pro · Super Review" to listOf(
            Filter("ls", 105.0, -2.6, 0.70), Filter("pk", 310.0, -2.4, 0.80), Filter("pk", 5665.0, 4.0, 3.54),
            Filter("pk", 875.0, 1.7, 1.09), Filter("pk", 3144.0, 1.6, 2.45), Filter("hs", 10000.0, -7.6, 0.70),
            Filter("pk", 71.0, 0.8, 2.41), Filter("pk", 8299.0, 2.0, 1.91), Filter("pk", 146.0, -0.3, 1.98),
            Filter("pk", 1763.0, 0.2, 3.00))),
        "mangird-top-pro-j" to ("Xenns Mangird Top Pro · Jaytiss" to listOf(
            Filter("ls", 105.0, -1.8, 0.70), Filter("pk", 5757.0, 6.1, 2.20), Filter("pk", 304.0, -2.0, 1.02),
            Filter("pk", 1818.0, -1.1, 1.84), Filter("pk", 803.0, 1.3, 2.30), Filter("hs", 10000.0, -7.8, 0.70),
            Filter("pk", 74.0, 0.9, 2.19), Filter("pk", 8502.0, 2.4, 2.28), Filter("pk", 3346.0, 0.9, 3.79),
            Filter("pk", 173.0, -0.2, 1.53))),
        // Sony WH-1000XM6 over Bluetooth and on its cable (AutoEq, Super Review), and the WF-1000XM6 earbuds: no full
        // measurement of those yet, so a gentle approximation from reviews
        "wh1000xm6-bt" to ("Sony WH-1000XM6 headphones · Bluetooth" to listOf(
            Filter("ls", 105.0, -8.3, 0.70), Filter("pk", 171.0, -4.9, 0.99), Filter("pk", 648.0, 2.5, 0.63),
            Filter("pk", 2499.0, -5.2, 1.50), Filter("pk", 5689.0, 4.1, 0.40), Filter("hs", 10000.0, 3.3, 0.70),
            Filter("pk", 4137.0, 1.3, 3.44), Filter("pk", 6747.0, -2.2, 3.84), Filter("pk", 1496.0, -1.5, 3.88),
            Filter("pk", 1151.0, 1.4, 4.07))),
        "wh1000xm6-cable" to ("Sony WH-1000XM6 headphones · cable" to listOf(
            Filter("ls", 105.0, -1.5, 0.70), Filter("pk", 161.0, -7.5, 0.69), Filter("pk", 2143.0, 4.3, 3.66),
            Filter("pk", 1019.0, -5.3, 1.74), Filter("pk", 8308.0, 5.3, 0.21), Filter("hs", 10000.0, -1.5, 0.70),
            Filter("pk", 57.0, -2.0, 2.27), Filter("pk", 463.0, 1.5, 2.87), Filter("pk", 103.0, 1.7, 2.95),
            Filter("pk", 4339.0, 0.9, 3.49))),
        "wf1000xm6" to ("Sony WF-1000XM6 earbuds · approximate" to listOf(
            Filter("ls", 250.0, -2.5, 0.70), Filter("pk", 6000.0, -1.5, 1.0), Filter("hs", 10000.0, 1.0, 0.70))),
        // The Galaxy S26 Ultra's own speakers: an approximation. Bass they can't play is eased (headroom, less rattle),
        // the honky upper mids and the sharp 3 kHz tamed, a little air added
        "s26ultra-speaker" to ("Samsung Galaxy S26 Ultra speaker · approximate" to listOf(
            Filter("ls", 180.0, -3.0, 0.70), Filter("pk", 900.0, -1.5, 1.2), Filter("pk", 3200.0, -2.5, 1.6),
            Filter("hs", 9000.0, 1.5, 0.70))),
    )
    val BANDS = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    const val Q = 1.41        // an octave wide, so neighbouring bands blend smoothly
    /** How many bands the EQ can have, and where they sit: 3, 5, 10, 15 (2/3 octave) or 31 (1/3 octave). */
    val COUNTS = listOf(3, 5, 10, 15, 31)
    fun bandsFor(n: Int): List<Int> = when (n) {
        3 -> listOf(100, 1000, 10000)
        5 -> listOf(60, 230, 910, 3600, 14000)
        15 -> listOf(25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000, 16000)
        31 -> listOf(20, 25, 31, 40, 50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600,
            2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000, 12500, 16000, 20000)
        else -> BANDS
    }
    /** Each band's width: narrower the more bands there are, so they still blend. */
    fun qFor(n: Int) = when (n) { 3 -> 0.45; 5 -> 0.75; 15 -> 2.15; 31 -> 4.32; else -> Q }
    fun label(hz: Int) = if (hz >= 1000) (if (hz % 1000 == 0) "${hz / 1000}k" else "%.1fk".format(hz / 1000.0)) else "$hz"

    /** gains (at the frequencies from) moved onto n bands: read off the curve, in log frequency. */
    fun resample(gains: List<Double>, from: List<Int>, n: Int): List<Double> {
        if (gains.size == n && from == bandsFor(n)) return gains
        val xs = from.map { kotlin.math.ln(it.toDouble()) }
        return bandsFor(n).map { hz ->
            val x = kotlin.math.ln(hz.toDouble())
            when {
                x <= xs.first() -> gains.first()
                x >= xs.last() -> gains.last()
                else -> {
                    val i = xs.indexOfLast { it <= x }.coerceAtMost(xs.size - 2)
                    val t = (x - xs[i]) / (xs[i + 1] - xs[i])
                    clamp(gains[i] + (gains[i + 1] - gains[i]) * t)
                }
            }
        }
    }

    /** A built-in preset's gains on n bands (the presets are written for 10). */
    fun preset(key: String, n: Int): List<Double>? = PRESETS[key]?.second?.let { resample(it, BANDS, n) }

    /** Every Sound preset there is to pick: the built-in ones, the ones you saved or imported ("my:…"), then Custom. */
    fun presets(s: EqSettings): Map<String, Pair<String, List<Double>?>> =
        PRESETS.filterKeys { it != "custom" } + s.myPresets.map { (k, p) -> "my:$k" to (p.name to p.bands) } + ("custom" to PRESETS["custom"]!!)

    /** Any preset's gains (yours too) on n bands. */
    fun presetOf(s: EqSettings, key: String, n: Int): List<Double>? = presets(s)[key]?.second?.let { resample(it, BANDS, n) }

    /** What filters do at the ten bands: a correction or a parametric file as a Sound preset. */
    fun curveToBands(filters: List<Filter>): List<Double> = BANDS.map { hz -> clamp(filters.sumOf { gainAt(it, hz.toDouble()) }) }

    /** gains at freqs as a GraphicEQ line (Wavelet, Equalizer APO, AutoEq read it). */
    fun graphicEqText(freqs: List<Int>, gains: List<Double>): String =
        "GraphicEQ: " + freqs.zip(gains).joinToString("; ") { (f, g) -> "$f ${"%.1f".format(java.util.Locale.US, g)}" } + "\n"

    /** A correction as a ParametricEQ file (AutoEq's, Equalizer APO's format), its pre-gain first. */
    fun parametricText(filters: List<Filter>): String = buildString {
        append("Preamp: ${"%.1f".format(java.util.Locale.US, preamp(filters))} dB\n")
        filters.forEachIndexed { i, f ->
            val k = when (f.kind) { "ls" -> "LSC"; "hs" -> "HSC"; else -> "PK" }
            append("Filter ${i + 1}: ON $k Fc ${f.hz.toInt()} Hz Gain ${"%.1f".format(java.util.Locale.US, f.gain)} dB Q ${"%.2f".format(java.util.Locale.US, f.q)}\n")
        }
    }

    /** A new device's starting Sound preset, for every kind of music: a gentle shape for its kind (you tune it after). */
    fun deviceCurve(kind: String): List<Double> = when (kind) {
        "speaker" -> listOf(-6.0, -4.0, -1.0, 1.0, 1.5, 1.0, 0.0, -1.5, 0.5, 1.0)
        "car" -> listOf(0.0, 1.0, 0.5, -1.5, -0.5, 0.0, -0.5, -1.0, 0.5, 1.5)
        else -> listOf(1.0, 0.5, 0.0, -0.5, -0.5, 0.0, 0.5, 1.0, 1.0, 0.5)   // headphones and earbuds
    }
    const val RANGE = 12.0    // dB, either way
    val PRESETS: LinkedHashMap<String, Pair<String, List<Double>?>> = linkedMapOf(
        "flat" to ("Flat" to List(10) { 0.0 }),
        "bass" to ("Bass boost" to listOf(5.0, 4.5, 3.5, 1.5, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
        "warm" to ("Warm" to listOf(2.0, 2.0, 1.5, 1.0, 0.0, 0.0, -0.5, -1.0, -1.5, -2.0)),
        "vocal" to ("Vocal" to listOf(-2.0, -2.0, -1.0, 0.0, 1.5, 2.5, 2.5, 1.5, 0.0, -1.0)),
        "bright" to ("Bright" to listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.5, 3.5, 4.0)),
        "v" to ("V-shape" to listOf(4.0, 3.5, 2.0, 0.0, -1.0, -1.0, 0.0, 1.5, 3.0, 3.5)),
        // Cars, for every kind of music: the cab's boom around 250 Hz down, the dash's glare at 2-4 kHz softened, air back
        "colorado" to ("Chevy Colorado 2023+ (standard)" to listOf(0.0, 1.5, 1.0, -2.0, -1.0, 0.0, -1.0, -1.5, 1.0, 2.0)),
        "colorado-bose" to ("Chevy Colorado 2023+ (Bose)" to listOf(0.0, 0.0, 0.0, -1.5, -0.5, 0.0, -0.5, -1.0, 0.5, 1.5)),
        // Your headphones, earbuds and the phone's speaker, for every kind of music: on top of their correction
        // (Headphone correction), a gentle shape that suits each one. Mangird: a little more sub-bass, the low mids and
        // the 4 kHz edge eased, air.
        "mangird" to ("Xenns Mangird Top Pro" to listOf(1.5, 1.0, 0.5, -0.5, -0.5, 0.0, 0.0, -0.5, 0.5, 1.0)),
        // WH-1000XM6 over Bluetooth: its thick upper bass thinned, the treble it holds back brought forward
        "wh1000xm6-bt" to ("Sony WH-1000XM6 · Bluetooth" to listOf(0.5, 0.0, -1.0, -1.5, -0.5, 0.0, 1.0, 1.5, 1.5, 1.0)),
        // On its cable it's leaner already: the same, gentler
        "wh1000xm6-cable" to ("Sony WH-1000XM6 · cable" to listOf(0.5, 0.5, -0.5, -1.0, -0.5, 0.0, 0.5, 1.0, 1.0, 1.0)),
        // WF-1000XM6 earbuds: sub-bass kept, the warm low mids cleared, detail and a touch of air
        "wf1000xm6" to ("Sony WF-1000XM6 earbuds" to listOf(1.0, 0.5, -0.5, -1.0, 0.0, 0.0, 0.5, 1.0, 1.0, 0.5)),
        // The S26 Ultra's speaker: bass it can't play cut (louder and cleaner without it), body and voices from 250 Hz
        // to 1 kHz lifted, the shrill 4 kHz eased, a little sparkle
        "s26ultra-speaker" to ("Galaxy S26 Ultra speaker" to listOf(-6.0, -4.0, -1.0, 1.0, 1.5, 1.0, 0.0, -1.5, 0.5, 1.0)),
        "custom" to ("Custom" to null),
    )

    fun clamp(g: Double) = (max(-RANGE, min(RANGE, g)) * 2).roundToInt() / 2.0

    /** Every correction there is to pick: the built-in ones, then the AutoEq ones you added. */
    fun headphones(s: EqSettings): Map<String, Pair<String, List<Filter>>> =
        HEADPHONES + s.profiles.mapValues { (_, p) -> p.name to p.toFilters() }

    /** Every filter the settings make, in order. */
    fun filters(s: EqSettings): List<Filter> =
        (headphones(s)[s.headphones]?.second ?: emptyList()) +
            bandsFor(s.count).zip(s.bands).map { (hz, g) -> Filter("pk", hz.toDouble(), g, qFor(s.count)) } +
            listOf(Filter("ls", 100.0, s.bass, 0.71), Filter("hs", 8000.0, s.treble, 0.71))

    /** RBJ cookbook coefficients (b0, b1, b2, a0, a1, a2), as libavfilter builds them. */
    fun coeffs(f: Filter, rate: Double): DoubleArray {
        val a = 10.0.pow(f.gain / 40)
        val w0 = 2 * PI * f.hz / rate
        val cw = cos(w0)
        val alpha = sin(w0) / (2 * f.q)
        return if (f.kind == "pk") doubleArrayOf(1 + alpha * a, -2 * cw, 1 - alpha * a, 1 + alpha / a, -2 * cw, 1 - alpha / a)
        else {
            val sa = 2 * sqrt(a) * alpha
            val s = if (f.kind == "ls") 1 else -1   // the high shelf is the low one mirrored
            doubleArrayOf(a * ((a + 1) - s * (a - 1) * cw + sa), s * 2 * a * ((a - 1) - s * (a + 1) * cw),
                a * ((a + 1) - s * (a - 1) * cw - sa), (a + 1) + s * (a - 1) * cw + sa,
                -s * 2 * ((a - 1) + s * (a + 1) * cw), (a + 1) + s * (a - 1) * cw - sa)
        }
    }

    /** The gain in dB of one filter at f Hz. */
    fun gainAt(flt: Filter, f: Double, rate: Double = 48000.0): Double {
        val c = coeffs(flt, rate)
        val w = -2 * PI * f / rate
        // z = e^{jw}: numerator and denominator as complex numbers
        val (zr, zi) = cos(w) to sin(w)
        val (z2r, z2i) = cos(2 * w) to sin(2 * w)
        val nr = c[0] + c[1] * zr + c[2] * z2r
        val ni = c[1] * zi + c[2] * z2i
        val dr = c[3] + c[4] * zr + c[5] * z2r
        val di = c[4] * zi + c[5] * z2i
        return 20 * log10(hypot(nr, ni) / hypot(dr, di))
    }

    /** The cut that keeps the EQ's biggest boost from clipping: dB, 0 or below. */
    fun preamp(filters: List<Filter>): Double {
        val peak = (0 until 300).maxOf { i -> val f = 20 * 1000.0.pow(i / 299.0); filters.sumOf { gainAt(it, f) } }
        return -ceil(max(0.0, peak) * 10) / 10
    }

    const val BOOST_MAX = 12.0
    const val BOOST_FREE = 2.0   // dB of the limiter catching only the odd peak: not heard

    /** How far Boost goes before the limiter starts squashing: the EQ's pre-cut back, plus BOOST_FREE. */
    fun boostClean(s: EqSettings) = min(BOOST_MAX, -(if (s.on) preamp(filters(s)) else 0.0) + BOOST_FREE)

    fun fmtDb(g: Double) = if (g == 0.0) "0" else (if (g > 0) "+" else "−") + "%.1f".format(kotlin.math.abs(g)).removeSuffix(".0")
}

@Serializable
data class EqSettings(
    val on: Boolean = false, val headphones: String = "off", val preset: String = "flat",
    val bands: List<Double> = List(10) { 0.0 }, val custom: List<Double> = List(10) { 0.0 },
    val bass: Double = 0.0, val treble: Double = 0.0,
    val profiles: Map<String, AutoEq.Profile> = emptyMap(),   // AutoEq corrections you added
    val devices: Map<String, String> = emptyMap(),            // an output's name: the correction you used on it
    val count: Int = 10,                                      // how many bands: 3, 5, 10, 15 or 31
    val perOutput: Boolean = true,                            // a separate EQ for each output (speaker, headphones, car…)
    val outputs: Map<String, OutputEq> = emptyMap(),          // each output's own EQ, by its key (see [Outputs])
    val output: String = "",                                  // the output these settings are for now
    val boost: Double = 0.0,                                  // Boost: dB louder than normal (0-12), this output's
    val myPresets: Map<String, MyPreset> = emptyMap(),        // Sound presets you saved or imported (10 bands each)
) {
    /** These settings as one output keeps them. */
    fun toOutput(label: String) = OutputEq(label, on, headphones, preset, bands, custom, bass, treble, count, boost)
    /** These settings with an output's own EQ put in. */
    fun withOutput(key: String, o: OutputEq?) = if (o == null) copy(output = key, on = false, headphones = "off", preset = "flat",
        bands = List(count) { 0.0 }, custom = List(count) { 0.0 }, bass = 0.0, treble = 0.0, boost = 0.0)
        else copy(output = key, on = o.on, headphones = o.headphones, preset = o.preset, bands = o.bands, custom = o.custom,
            bass = o.bass, treble = o.treble, count = o.count, boost = o.boost)
}

/** A Sound preset of yours: its name and its ten bands' gains. */
@Serializable
data class MyPreset(val name: String, val bands: List<Double>)

/** One output's own EQ. */
@Serializable
data class OutputEq(val label: String = "", val on: Boolean = false, val headphones: String = "off", val preset: String = "flat",
                    val bands: List<Double> = List(10) { 0.0 }, val custom: List<Double> = List(10) { 0.0 },
                    val bass: Double = 0.0, val treble: Double = 0.0, val count: Int = 10, val boost: Double = 0.0)

