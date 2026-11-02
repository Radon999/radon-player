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

/** Splits a whole song's decoded sound (any rate, any number of channels, interleaved) into windows of [secs] seconds
 *  in one channel at about [want] samples a second, handing each to [each] as it fills (the last, shorter one on
 *  [finish]). Only one window is held at a time, so a long song never fills memory (Moods' Full scan). */
class MonoWindows(private val inRate: Int, private val channels: Int, want: Int, secs: Int, private val each: (Pcm) -> Unit) {
    val rate = minOf(inRate, want).coerceAtLeast(1)
    private val out = FloatArray(rate * secs)
    private var n = 0
    private var acc = 0.0; private var k = 0; private var phase = 0L
    private var frame = 0.0; private var ch = 0
    /** Seconds of sound handed on so far. */
    var heard = 0.0
        private set

    fun feed(buf: FloatArray, count: Int) {
        val chs = channels.coerceAtLeast(1)
        for (i in 0 until count) {
            frame += buf[i]; ch++
            if (ch < chs) continue
            acc += frame / chs; k++; frame = 0.0; ch = 0
            phase += rate
            if (phase >= inRate) {
                phase -= inRate
                out[n++] = (acc / k).toFloat()
                acc = 0.0; k = 0
                if (n == out.size) { heard += n.toDouble() / rate; each(Pcm(out.copyOf(), rate)); n = 0 }
            }
        }
    }

    fun finish() { if (n > 0) { heard += n.toDouble() / rate; each(Pcm(out.copyOf(n), rate)); n = 0 } }
}

/** What a song sounds like: from a minute of its middle (Scan moods), or the whole song (Full scan).
 *  loud: average loudness (dBFS, about -8 for a loud modern master, -25 for a quiet one); range: how much louder its
 *  loud parts are than its quiet ones (dB); bpm: its tempo (0: no steady beat found); beat: how clear that beat is
 *  (0–1); bright: where its sound sits (the spectral centroid, Hz: low and warm to bright and sharp); onsets: notes
 *  and hits a second; major: a rough guess at its key, +1 major to -1 minor (0: can't tell).
 *  Only from a whole song: vary: how much its loudness moves from part to part (dB, the spread of its parts'
 *  loudness); sections: how many parts it seems to have (where loudness, brightness or busyness changes); 0: not
 *  known (a minute of it heard). */
@Serializable
data class SoundFeatures(val loud: Double = -60.0, val range: Double = 0.0, val bpm: Double = 0.0, val beat: Double = 0.0,
                         val bright: Double = 0.0, val onsets: Double = 0.0, val major: Double = 0.0,
                         val vary: Double = 0.0, val sections: Int = 0)

/** A whole song heard window by window ([add] each, from [MonoWindows]), its features worked out at the end ([result]):
 *  averages over the song, its tempo where its beat is clearest, its loudness range over all of it, and how it
 *  changes from part to part. Only each window's few numbers are kept. */
class WholeSong {
    companion object {
        /** Seconds of each window a whole song is heard in: long enough for its tempo and key, short enough to see its parts. */
        const val WINDOW = 20
    }

    private val parts = ArrayList<Pair<Double, SoundFeatures?>>()   // (seconds, its features; null: silent)
    private val levels = IntArray(SoundMood.LEVEL_BINS)

    fun add(pcm: Pcm) {
        val secs = pcm.samples.size.toDouble() / pcm.rate
        if (secs < 4 && parts.isNotEmpty()) return   // a last scrap: too short to say anything
        parts += secs to SoundMood.features(pcm, levels)
    }

    fun result(): SoundFeatures? = SoundMood.combine(parts, levels)
}

/** Hears a song's features in its sound (no Android or Compose here, so it's tested on made-up signals). */
object SoundMood {
    // Krumhansl–Kessler key profiles: how much each note of the scale belongs in a major and a minor key (C first).
    private val MAJOR = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    /** Bins of [features]' loudness counts: half a dB each, from -60 dB to 0. */
    const val LEVEL_BINS = 120

    /** The features of [pcm], or null when there's too little of it or it's silent. [levels]: if given, each short
     *  frame's loudness is counted into it (for a whole song's loudness range, see [combine]). */
    fun features(pcm: Pcm, levels: IntArray? = null): SoundFeatures? {
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
        if (levels != null) for (d in db) if (d > -60) levels[((d + 60) * 2).toInt().coerceIn(0, LEVEL_BINS - 1)]++
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

    /** A whole song's features from its windows' [(seconds, features or null if silent)] and every frame's loudness
     *  ([levels], from [features]); null when none of it could be heard. */
    fun combine(parts: List<Pair<Double, SoundFeatures?>>, levels: IntArray): SoundFeatures? {
        val heard = parts.filter { it.second != null }.map { it.first to it.second!! }
        if (heard.isEmpty()) return null
        val total = parts.sumOf { it.first }
        // Loudness: the average power over the whole song (silent parts count as very quiet).
        var power = 0.0
        for ((s, f) in parts) power += s * Math.pow(10.0, (f?.loud ?: -70.0) / 10)
        val loud = 10 * log10(power / total + 1e-12)
        val hs = heard.sumOf { it.first }
        fun avg(get: (SoundFeatures) -> Double) = heard.sumOf { it.first * get(it.second) } / hs
        // Tempo: the middle one of the parts' tempos, each counted by its length and how clear its beat is.
        val tempos = heard.filter { it.second.bpm > 0 }.map { it.second.bpm to it.first * it.second.beat }.sortedBy { it.first }
        val bpm = if (tempos.isEmpty() || tempos.sumOf { it.second } <= 0) 0.0 else {
            val half = tempos.sumOf { it.second } / 2; var acc = 0.0
            tempos.first { acc += it.second; acc >= half }.first
        }
        // Range: the loud frames over the quiet ones across the whole song, as for a minute of it.
        val count = levels.sum()
        fun level(p: Double): Double {
            val want = (count * p).toLong().coerceAtMost(count - 1L); var seen = 0L
            for (i in levels.indices) { seen += levels[i]; if (seen > want) return -60 + (i + 0.5) / 2 }
            return 0.0
        }
        val range = if (count >= 10) level(0.95) - level(0.10) else avg { it.range }
        // Vary: the spread of the parts' loudness; sections: a new one where a part sounds clearly unlike the one before.
        val meanLoud = avg { it.loud }
        val vary = sqrt(heard.sumOf { it.first * (it.second.loud - meanLoud).let { d -> d * d } } / hs)
        var sections = 1
        for (i in 1 until parts.size) {
            val a = parts[i - 1].second; val b = parts[i].second
            val changed = if (a == null || b == null) a != b else
                kotlin.math.abs(a.loud - b.loud) >= 3 || kotlin.math.abs(a.bright - b.bright) >= 0.3 * maxOf(a.bright, b.bright, 1.0) ||
                    kotlin.math.abs(a.onsets - b.onsets) >= 0.5 * maxOf(a.onsets, b.onsets, 1.0)
            if (changed) sections++
        }
        return SoundFeatures(round(loud, 1), round(range, 1), round(bpm, 1), round(avg { it.beat }, 3), round(avg { it.bright }, 0),
            round(avg { it.onsets }, 2), round(avg { it.major }, 3), round(vary, 1), sections)
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
