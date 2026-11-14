package org.omarchy.music

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// The DJ's voice made ready to be heard next to music (docs/rebuild/RADON_REBUILD_PLAN.md, 4.3): brought to the rate
// the music is playing at, its rumble taken out, a little warmth and presence put in, and its peaks held under a
// ceiling. Pure arithmetic on samples, done once per line before it's said, never while sound is being played.

/** One second-order filter (the "Audio EQ Cookbook" kinds), run a sample at a time. Its state is kept in doubles: at
 *  96 kHz and above an 80 Hz filter's numbers are too close together for floats. */
class Biquad private constructor(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
    private val b0 = b0 / a0; private val b1 = b1 / a0; private val b2 = b2 / a0
    private val a1 = a1 / a0; private val a2 = a2 / a0
    private var z1 = 0.0; private var z2 = 0.0

    fun run(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    companion object {
        /** Takes out what's below f0 (rumble, a thump at the start of a word). */
        fun highPass(fs: Int, f0: Double, q: Double): Biquad {
            val w = 2 * PI * f0 / fs; val c = cos(w); val alpha = sin(w) / (2 * q)
            return Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }

        /** Lifts (or lowers) what's below f0 by db. */
        fun lowShelf(fs: Int, f0: Double, db: Double, q: Double): Biquad {
            val a = 10.0.pow(db / 40); val w = 2 * PI * f0 / fs; val c = cos(w); val alpha = sin(w) / (2 * q)
            val beta = 2 * sqrt(a) * alpha
            return Biquad(a * ((a + 1) - (a - 1) * c + beta), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - beta),
                (a + 1) + (a - 1) * c + beta, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - beta)
        }

        /** Lifts (or lowers) what's around f0 by db; q is how narrow. */
        fun peak(fs: Int, f0: Double, db: Double, q: Double): Biquad {
            val a = 10.0.pow(db / 40); val w = 2 * PI * f0 / fs; val c = cos(w); val alpha = sin(w) / (2 * q)
            return Biquad(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
        }
    }
}

/** Sound from one sample rate to another, properly (a windowed-sinc filter in as many phases as the ratio needs:
 *  24 to 48 kHz is 2, to 44.1 kHz is 147), where it was a straight line between samples before. The result is in step
 *  with what went in: no delay, and as long as it should be. */
object Resampler {
    private const val TAPS = 32        // per phase
    private const val BETA = 9.0       // the Kaiser window's
    private const val CUTOFF = 0.45    // of the lower of the two rates

    private class Table(val up: Int, val down: Int, val centre: Int, val phases: Array<FloatArray>)
    private val tables = java.util.concurrent.ConcurrentHashMap<Long, Table>()

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private fun i0(x: Double): Double {
        var sum = 1.0; var term = 1.0; var k = 1
        while (term > sum * 1e-12) { term *= (x / (2 * k)) * (x / (2 * k)); sum += term; k++ }
        return sum
    }

    private fun table(from: Int, to: Int): Table = tables.getOrPut(from.toLong() shl 32 or to.toLong()) {
        val g = gcd(from, to); val up = to / g; val down = from / g
        // Going down in rate the filter is longer by as much, to keep as sharp
        val taps = if (down > up) ceil(TAPS.toDouble() * down / up).toInt() else TAPS
        val n = taps * up; val centre = n / 2
        val fc = CUTOFF * minOf(1.0, up.toDouble() / down) / up   // cycles per sample, at the rate in times up
        val phases = Array(up) { FloatArray(taps) }
        for (p in 0 until up) {
            val h = DoubleArray(taps); var sum = 0.0
            for (j in 0 until taps) {
                val t = (p + j * up - centre).toDouble()
                val sinc = if (t == 0.0) 1.0 else sin(2 * PI * fc * t) / (2 * PI * fc * t)
                val r = t / centre
                val window = if (abs(r) >= 1) 0.0 else i0(BETA * sqrt(1 - r * r)) / i0(BETA)
                h[j] = sinc * window; sum += h[j]
            }
            for (j in 0 until taps) phases[p][j] = (h[j] / sum).toFloat()   // every phase passes a steady level as it is
        }
        Table(up, down, centre, phases)
    }

    /** x (one channel, at from Hz) at to Hz. */
    fun resample(x: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || x.isEmpty() || from <= 0 || to <= 0) return x.copyOf()
        val t = table(from, to)
        val out = FloatArray(((x.size.toLong() * t.up + t.down - 1) / t.down).toInt())
        for (k in out.indices) {
            val u = k.toLong() * t.down + t.centre
            val h = t.phases[(u % t.up).toInt()]
            val at = (u / t.up).toInt()
            var sum = 0f
            val first = maxOf(0, at - (x.size - 1)); val last = minOf(h.size - 1, at)
            for (j in first..last) sum += h[j] * x[at - j]
            out[k] = sum
        }
        return out
    }
}

/** Holds peaks under a ceiling without the crunch of clipping: it looks 2 ms ahead, so the sound is already turned
 *  down when a peak arrives, and lets go over 50 ms after. Below the ceiling by more than the knee, nothing is touched. */
object Limiter {
    const val CEILING_DB = -1.5
    val CEILING = 10.0.pow(CEILING_DB / 20)   // 0.8414
    private const val KNEE_DB = 3.0           // where it starts to act, below the ceiling
    private const val AHEAD = 0.002           // seconds
    private const val RELEASE = 0.050         // seconds
    private val UNTOUCHED = 10.0.pow((CEILING_DB - KNEE_DB) / 20)

    /** How much a peak this high is turned down (1: not at all). What comes out is never over the ceiling. */
    fun gainFor(peak: Double): Double {
        if (peak <= UNTOUCHED) return 1.0
        val x = 20 * log10(peak)
        val over = x - (CEILING_DB - KNEE_DB)
        val y = if (x >= CEILING_DB + KNEE_DB) CEILING_DB else x - over * over / (4 * KNEE_DB)
        return 10.0.pow((y - x) / 20)
    }

    /** How many samples went over the ceiling after the limiter in the last [limit] and had to be cut (none, ever:
     *  the tests hold it to that). */
    @Volatile var cut = 0
        private set

    /** x (one channel at fs Hz), limited. The same length and in step with x. */
    fun limit(x: FloatArray, fs: Int): FloatArray {
        val n = x.size
        if (n == 0) return x
        val ahead = ceil(AHEAD * fs).toInt()
        // The loudest of each sample and the 2 ms after it, as the gain that asks for
        val want = DoubleArray(n)
        val q = IntArray(n); var head = 0; var tail = 0   // indices, loudest first
        var next = 0
        for (i in 0 until n) {
            while (next < n && next <= i + ahead) {
                val a = abs(x[next])
                while (tail > head && abs(x[q[tail - 1]]) <= a) tail--
                q[tail++] = next++
            }
            while (q[head] < i) head++
            want[i] = gainFor(abs(x[q[head]]).toDouble())
        }
        // Down at once, back up slowly
        val keep = exp(-1.0 / (RELEASE * fs))
        for (i in 1 until n) if (want[i] > want[i - 1]) want[i] = keep * want[i - 1] + (1 - keep) * want[i]
        // The turn down spread over the 2 ms before the peak (a ramp, not a step): the mean of the last 2 ms of gains,
        // each of which already allowed for this sample
        val out = FloatArray(n)
        var sum = want[0] * (ahead + 1)
        var over = 0
        for (i in 0 until n) {
            sum += want[i] - (if (i - ahead - 1 >= 0) want[i - ahead - 1] else want[0])
            var v = x[i] * (sum / (ahead + 1))
            if (abs(v) > CEILING * 1.000001) { over++; v = if (v > 0) CEILING else -CEILING }
            out[i] = v.toFloat()
        }
        cut = over
        return out
    }
}

/** The whole of it, in order: to the music's rate, the level for two speakers (x 0.7071), rumble out (80 Hz), warmth
 *  in (+2.5 dB under 180 Hz), presence in (+2 dB around 3.5 kHz), peaks held under -1.5 dB, and 5 ms of fade at each
 *  end so it never clicks. One channel comes out, the same for both speakers ([stereo] makes the two of it). */
object DjVoiceProcessor {
    const val UPMIX = 0.70710678
    private const val FADE = 0.005   // seconds

    /** voice (one channel at rate Hz, -1 to 1) ready to be heard at outRate Hz. */
    fun process(voice: FloatArray, rate: Int, outRate: Int): FloatArray {
        if (voice.isEmpty() || rate <= 0 || outRate <= 0) return FloatArray(0)
        val x = Resampler.resample(voice, rate, outRate)
        val highPass = Biquad.highPass(outRate, 80.0, 0.70710678)
        val shelf = Biquad.lowShelf(outRate, 180.0, 2.5, 0.70710678)
        val peak = Biquad.peak(outRate, 3500.0, 2.0, 1.2)
        for (i in x.indices) {
            val v = x[i].toDouble()
            x[i] = peak.run(shelf.run(highPass.run(if (v.isNaN() || v.isInfinite()) 0.0 else v * UPMIX))).toFloat()
        }
        val y = Limiter.limit(x, outRate)
        val fade = minOf((FADE * outRate).toInt(), y.size / 2)
        for (i in 0 until fade) { val g = i.toFloat() / fade; y[i] *= g; y[y.size - 1 - i] *= g }
        return y
    }

    /** One channel as two, the same in each (left, right, left, right...). */
    fun stereo(mono: FloatArray): FloatArray = FloatArray(mono.size * 2) { mono[it / 2] }
}
