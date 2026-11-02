package org.omarchy.music

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tanh

/** Smooth distortion on loud songs (Settings › Sound quality): the sound side, plain Kotlin so it can be tested here.
 *  Some masters are made so loud their peaks are cut flat at full scale (clipped); those flat tops are what sounds
 *  grainy. [Declipper] rebuilds them, [SoftClip] rounds off anything that would still go over, [HEADROOM] makes room. */
object Smooth {
    /** The level drop (-3 dB) that makes room for the rebuilt peaks, EQ boosts and resampling's in-between peaks. */
    const val HEADROOM = 0.7079457843841379
}

/** A gentle knee instead of hard clipping: exactly the same below -1 dBFS, then bending smoothly (tanh) so the result
 *  never goes past full scale. Same slope at the knee, so there's no corner to hear. */
object SoftClip {
    const val KNEE = 0.8912509381337456   // -1 dBFS

    fun apply(x: Double): Double {
        val a = abs(x)
        if (a <= KNEE) return x
        val r = 1.0 - KNEE
        val y = KNEE + r * tanh((a - KNEE) / r)
        return if (x < 0) -y else y
    }
}

/** Rebuilds peaks that were clipped in the master: a run of 2 or more samples in a row pinned at (or within 0.5% of) full
 *  scale, all on one side, is replaced by a smooth curve (a cubic Hermite through the last sample before and the first
 *  after, matching their slopes), so the peak rises and falls the way it would have. A sample is only ever moved further
 *  out than it was, never pulled in, and at most to [cap] (+2 dB: with [Smooth.HEADROOM] taken off, still under
 *  [SoftClip]'s knee).
 *
 *  It streams: samples go in interleaved and come out [delay] frames later (the lookahead a run needs to be seen whole;
 *  5 ms). Anything that isn't a clipped run comes out exactly as it went in. [maxRun] is the longest run rebuilt
 *  (longer ones are left: those are deliberate, or too far gone to guess). One per player; not thread-safe. */
class Declipper(val channels: Int, sampleRate: Int, val threshold: Double = 0.995, val cap: Double = 1.25) {
    val maxRun = (sampleRate * 0.005).toInt().coerceIn(16, 1000)
    val delay = maxRun + 2
    private val size = Integer.highestOneBit(maxRun + 8) * 2
    private val m = (size - 1).toLong()
    private val ring = Array(channels) { DoubleArray(size) }
    private val start = LongArray(channels) { -1 }     // where the run now being read began (-1: none)
    private val side = IntArray(channels)              // its sign
    // A run that has just ended waits one more sample (the second neighbour on its right), then is rebuilt
    private val endAt = LongArray(channels) { NONE }
    private val endFrom = LongArray(channels)
    private val endSide = IntArray(channels)
    private var n = 0L                                 // frames taken in
    private var ch = 0
    /** Off: samples just pass through (still [delay] frames later, so switching it never jumps in time). */
    var active = true
    /** Runs rebuilt so far (for the tests, and to see it working). */
    var rebuilt = 0L
        private set

    fun reset() {
        ring.forEach { it.fill(0.0) }
        start.fill(-1); endAt.fill(NONE)
        n = 0; ch = 0
    }

    /** One sample in (channels interleaved, in order); out comes the one [delay] frames earlier on this channel
     *  (silence for the first [delay] frames). */
    fun next(x: Double): Double {
        val c = ch
        val r = ring[c]
        val i = n
        r[(i and m).toInt()] = x
        if (!active) { start[c] = -1; endAt[c] = NONE }
        else {
            if (endAt[c] == i - 1) { repair(r, endFrom[c], endAt[c], endSide[c]); endAt[c] = NONE }
            val clipped = abs(x) >= threshold
            val s = if (x < 0) -1 else 1
            if (start[c] >= 0 && (!clipped || s != side[c])) {   // the run ends here: rebuild it if it's one we can
                val len = i - start[c]
                if (len in 2..maxRun && start[c] >= 2) { endAt[c] = i; endFrom[c] = start[c]; endSide[c] = side[c] }
                start[c] = -1
            }
            if (clipped && start[c] < 0) { start[c] = i; side[c] = s }
        }
        val out = if (i >= delay) r[((i - delay) and m).toInt()] else 0.0
        if (++ch == channels) { ch = 0; n++ }
        return out
    }

    /** The run of samples [s, e) on side sg: the cubic from s-1 to e, each end's slope from its neighbour outside. */
    private fun repair(r: DoubleArray, s: Long, e: Long, sg: Int) {
        fun at(k: Long) = r[(k and m).toInt()] * sg   // as if the run were on the positive side
        val a = s - 1
        val pa = at(a); val pb = at(e)
        // Each end's slope from its outside neighbour, a little under it: the step from the neighbour is steeper than the
        // curve at the run's edge (it flattens towards the peak), and with noise or cymbals on top it overshoots. Tried on
        // clipped sines and on noisy music, 0.7 of it came closest to the real peaks. A slope the wrong way: flat.
        val ma = max(0.0, pa - at(a - 1)) * SLOPE
        val mb = min(0.0, at(e + 1) - pb) * SLOPE
        val h = (e - a).toDouble()
        var k = s
        while (k < e) {
            val t = (k - a) / h
            val t2 = t * t; val t3 = t2 * t
            val v = (2 * t3 - 3 * t2 + 1) * pa + (t3 - 2 * t2 + t) * h * ma + (-2 * t3 + 3 * t2) * pb + (t3 - t2) * h * mb
            val idx = (k and m).toInt()
            val old = r[idx] * sg
            r[idx] = sg * min(cap, max(old, v))
            k++
        }
        rebuilt++
    }

    private companion object { const val NONE = Long.MIN_VALUE; const val SLOPE = 0.7 }
}
