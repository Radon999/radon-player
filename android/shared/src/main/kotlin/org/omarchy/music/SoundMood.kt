package org.omarchy.music

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A stretch of a song's sound, in one channel: samples (-1..1), [rate] of them a second. */
class Pcm(val samples: FloatArray, val rate: Int)

/** Turns decoded sound (any rate, any number of channels, interleaved) into one channel at about [want] samples a
 *  second (the file's own rate when that's lower), keeping at most [secs] seconds. It averages the samples in between,
 *  which is plenty for hearing a song's mood, and keeps memory low: only the small result is held. */
class MonoDown(private val inRate: Int, private val channels: Int, want: Int, secs: Int) {
    val rate = minOf(inRate, want).coerceAtLeast(1)
    private val out = FloatArray(rate * secs)
    private var n = 0
    private var acc = 0.0; private var k = 0; private var phase = 0L
    private var frame = 0.0; private var ch = 0
    val full get() = n >= out.size

    fun feed(buf: FloatArray, count: Int) {
        val chs = channels.coerceAtLeast(1)
        for (i in 0 until count) {
            frame += buf[i]; ch++
            if (ch < chs) continue
            acc += frame / chs; k++; frame = 0.0; ch = 0
            phase += rate
            if (phase >= inRate) {
                phase -= inRate
                if (n < out.size) out[n++] = (acc / k).toFloat()
                acc = 0.0; k = 0
            }
        }
    }

    fun result() = Pcm(out.copyOf(n), rate)
}

/** What a song sounds like, from its middle half-minute.
 *  loud: average loudness (dBFS, about -8 for a loud modern master, -25 for a quiet one); range: how much louder its
 *  loud parts are than its quiet ones (dB); bpm: its tempo (0: no steady beat found); beat: how clear that beat is
 *  (0–1); bright: where its sound sits (the spectral centroid, Hz: low and warm to bright and sharp); onsets: notes
 *  and hits a second; major: a rough guess at its key, +1 major to -1 minor (0: can't tell). */
@Serializable
data class SoundFeatures(val loud: Double = -60.0, val range: Double = 0.0, val bpm: Double = 0.0, val beat: Double = 0.0,
                         val bright: Double = 0.0, val onsets: Double = 0.0, val major: Double = 0.0)

/** Hears a song's features in its sound (no Android or Compose here, so it's tested on made-up signals). */
object SoundMood {
    // Krumhansl–Kessler key profiles: how much each note of the scale belongs in a major and a minor key (C first).
    private val MAJOR = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    /** The features of [pcm], or null when there's too little of it or it's silent. */
    fun features(pcm: Pcm): SoundFeatures? {
        val x = pcm.samples; val rate = pcm.rate
        if (rate < 4000 || x.size < rate * 4) return null
        var sq = 0.0
        for (v in x) sq += v.toDouble() * v
        val loud = 10 * log10(sq / x.size + 1e-12)
        if (loud < -60) return null

        // Short frames (about 46 ms) every quarter frame: loudness, brightness, and the onset envelope (spectral flux).
        val n = when { rate >= 32000 -> 2048; rate >= 16000 -> 1024; else -> 512 }
        val hop = n / 4
        val frames = (x.size - n) / hop + 1
        val win = hann(n)
        val re = DoubleArray(n); val im = DoubleArray(n)
        val prev = DoubleArray(n / 2 + 1)
        val db = DoubleArray(frames); val flux = DoubleArray(frames)
        val binHz = rate.toDouble() / n
        var cSum = 0.0; var cW = 0.0
        for (f in 0 until frames) {
            val off = f * hop
            var e = 0.0
            for (i in 0 until n) { val v = x[off + i].toDouble(); e += v * v; re[i] = v * win[i]; im[i] = 0.0 }
            val ms = e / n
            db[f] = 10 * log10(ms + 1e-12)
            fft(re, im)
            var fl = 0.0; var num = 0.0; var den = 0.0
            for (k in 1..n / 2) {
                val mag = sqrt(re[k] * re[k] + im[k] * im[k])
                val lg = ln(1 + 100 * mag)
                if (f > 0) fl += maxOf(0.0, lg - prev[k])
                prev[k] = lg
                num += k * binHz * mag; den += mag
            }
            flux[f] = fl
            if (den > 0 && db[f] > -50) { cSum += num / den * ms; cW += ms }
        }
        val bright = if (cW > 0) cSum / cW else 0.0
        val heard = db.filter { it > -60 }.sorted()
        val range = if (heard.size >= 10) heard[(heard.size * 0.95).toInt().coerceAtMost(heard.size - 1)] - heard[(heard.size * 0.10).toInt()] else 0.0

        // Onsets: the flux above its own local average (about a quarter second either side).
        val fps = rate.toDouble() / hop
        val half = maxOf(1, (fps / 4).toInt())
        val onset = DoubleArray(frames)
        run {
            var s = 0.0; var lo = 0; var hi = -1
            for (f in 0 until frames) {
                while (hi < minOf(frames - 1, f + half)) { hi++; s += flux[hi] }
                while (lo < f - half) { s -= flux[lo]; lo++ }
                onset[f] = maxOf(0.0, flux[f] - s / (hi - lo + 1))
            }
        }
        val mean = onset.average()
        val sd = sqrt(onset.sumOf { (it - mean) * (it - mean) } / frames)
        // A peak counts as a note or hit when it stands out from the rest, and from the song's own level of change
        // (so a held note or plain hiss doesn't count as hundreds of them).
        val thr = maxOf(mean + 0.5 * sd, 0.15 * flux.average() + 1.0)
        var peaks = 0; var last = -1_000_000
        val gap = maxOf(1, (fps * 0.06).toInt())
        for (f in 1 until frames - 1) {
            if (onset[f] <= thr || f - last < gap) continue
            var top = true
            for (j in maxOf(0, f - 3)..minOf(frames - 1, f + 3)) if (onset[j] > onset[f]) { top = false; break }
            if (top) { peaks++; last = f }
        }
        val onsets = peaks / (x.size.toDouble() / rate)

        // No tempo without hits to count it from (a drone, a held chord).
        val (bpm, beat) = if (onsets < 0.3) 0.0 to 0.0 else tempo(onset, fps)
        return SoundFeatures(round(loud, 1), round(range, 1), round(bpm, 1), round(beat, 3), round(bright, 0), round(onsets, 2),
            round(key(x, rate), 3))
    }

    /** The tempo of an onset envelope: the lag (60–200 BPM) where it best repeats itself, tempos near 120 a little
     *  likelier (so a song isn't heard at half or double its speed), and how strongly it repeats (0–1). */
    fun tempo(onset: DoubleArray, fps: Double): Pair<Double, Double> {
        val frames = onset.size
        val minL = maxOf(1, (fps * 60 / 200).toInt())
        val maxL = (fps * 60 / 60).toInt() + 1
        val limit = minOf(frames / 2, 2 * maxL + 1)
        if (limit <= minL + 2) return 0.0 to 0.0
        // Smoothed a little first, so a beat that falls between two frames still lines up with itself.
        val sm = DoubleArray(frames) { i -> 0.25 * onset[maxOf(0, i - 1)] + 0.5 * onset[i] + 0.25 * onset[minOf(frames - 1, i + 1)] }
        val m = sm.average()
        val c = DoubleArray(frames) { sm[it] - m }
        val ac = DoubleArray(limit + 1)
        for (l in 0..limit) {
            var s = 0.0
            for (i in 0 until frames - l) s += c[i] * c[i + l]
            ac[l] = s / (frames - l)
        }
        if (ac[0] <= 1e-12) return 0.0 to 0.0
        var best = -1; var bestScore = 0.0
        for (l in minL..minOf(maxL, limit - 1)) {
            val bpm = 60 * fps / l
            val prior = exp(-0.5 * (log2(bpm / 120) / 0.9).let { it * it })
            val twice = if (2 * l <= limit) ac[2 * l] else 0.0
            val score = (ac[l] + 0.5 * twice) * prior
            if (score > bestScore) { bestScore = score; best = l }
        }
        if (best < 0 || ac[best] <= 0) return 0.0 to 0.0
        // A finer lag between frames: the peak of a parabola through the best lag and its neighbours.
        var lag = best.toDouble()
        if (best > 0 && best < limit) {
            val a = ac[best - 1]; val b = ac[best]; val cc = ac[best + 1]
            val den = a - 2 * b + cc
            if (den < 0) lag += (0.5 * (a - cc) / den).coerceIn(-0.5, 0.5)
        }
        val strength = (ac[best] / ac[0]).coerceIn(0.0, 1.0)
        return (if (strength < 0.08) 0.0 else 60 * fps / lag) to strength   // too weak to call it a beat: no tempo
    }

    /** A rough key guess: how much of each note (chroma) the sound has, compared with major and minor keys:
     *  +1 clearly major, -1 clearly minor, 0 can't tell. */
    fun key(x: FloatArray, rate: Int): Double {
        val n = when { rate >= 32000 -> 8192; rate >= 16000 -> 4096; else -> 2048 }   // fine enough to tell notes apart
        if (x.size < n) return 0.0
        val hop = n / 2
        val win = hann(n)
        val re = DoubleArray(n); val im = DoubleArray(n)
        val chroma = DoubleArray(12)
        val binHz = rate.toDouble() / n
        val pc = IntArray(n / 2 + 1) { k ->
            val f = k * binHz
            if (f < 110 || f > 2000) -1 else Math.floorMod((12 * log2(f / 440)).roundToInt() + 9, 12)   // C = 0, A = 9
        }
        val frame = DoubleArray(12)
        var f = 0
        while (f + n <= x.size) {
            for (i in 0 until n) { re[i] = x[f + i] * win[i]; im[i] = 0.0 }
            fft(re, im)
            frame.fill(0.0)
            for (k in 1..n / 2) if (pc[k] >= 0) frame[pc[k]] += re[k] * re[k] + im[k] * im[k]
            val s = frame.sum()
            if (s > 1e-9) for (i in 0 until 12) chroma[i] += frame[i] / s   // every frame counts the same
            f += hop
        }
        if (chroma.sum() <= 0) return 0.0
        var maj = -1.0; var min = -1.0
        for (k in 0 until 12) {
            maj = maxOf(maj, corr(chroma, MAJOR, k))
            min = maxOf(min, corr(chroma, MINOR, k))
        }
        return ((maj - min) * 3).coerceIn(-1.0, 1.0)
    }

    /** How well chroma fits a key profile started on note [key] (Pearson's r). */
    private fun corr(chroma: DoubleArray, profile: DoubleArray, key: Int): Double {
        val ma = chroma.average(); val mb = profile.average()
        var sab = 0.0; var saa = 0.0; var sbb = 0.0
        for (i in 0 until 12) {
            val a = chroma[i] - ma; val b = profile[Math.floorMod(i - key, 12)] - mb
            sab += a * b; saa += a * a; sbb += b * b
        }
        return if (saa <= 0 || sbb <= 0) 0.0 else sab / sqrt(saa * sbb)
    }

    private fun hann(n: Int) = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / n) }

    private fun round(v: Double, places: Int): Double { val p = Math.pow(10.0, places.toDouble()); return Math.round(v * p) / p }

    /** In-place radix-2 FFT (n a power of two). */
    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val tr = re[b] * cr - im[b] * ci; val ti = re[b] * ci + im[b] * cr
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                    val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

}
