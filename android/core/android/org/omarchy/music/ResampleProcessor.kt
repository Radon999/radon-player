package org.omarchy.music

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** High-quality resampling (Equalizer › High-quality resampling, the phone): when a song's sample rate isn't the
 *  phone's own mixing rate (44.1 kHz music on a 48 kHz phone), it's converted here with a long windowed-sinc filter
 *  (48 taps, Kaiser window) instead of Android's quicker one. When the rates already match, or bit-perfect is on, it
 *  isn't in the path at all, so it never touches music it can't improve. Takes effect from the next song. */
class ResampleProcessor(private val ctx: android.content.Context) : BaseAudioProcessor() {
    companion object {
        /** The sound output refused a song (MusicService tries it again): resampling stays out of the way from then on. */
        @Volatile var plain = false
    }
    private val half = 24            // taps on each side
    private val phases = 512         // fractional positions in the table
    private var table = Array(0) { FloatArray(0) }
    private var ch = 2
    private var inFloat = false
    private var step = 1.0           // input frames per output frame
    private var pos = 0.0            // the next output frame's place in hist (frames)
    private var hist = FloatArray(0) // input frames not used up yet, interleaved
    private var frames = 0

    /** The rate Android mixes at (most phones 48 kHz). */
    private fun deviceRate(): Int = runCatching {
        val am = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        am.getProperty(android.media.AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toInt()
    }.getOrNull() ?: 48000

    override fun onConfigure(input: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val want = deviceRate()
        if (plain || !Prefs.on(Prefs.HQ_RESAMPLE) || Music.bitPerfectNow() || input.sampleRate == want || input.sampleRate <= 0 ||
            (input.encoding != C.ENCODING_PCM_16BIT && input.encoding != C.ENCODING_PCM_FLOAT)) return AudioProcessor.AudioFormat.NOT_SET
        ch = input.channelCount
        inFloat = input.encoding == C.ENCODING_PCM_FLOAT
        step = input.sampleRate.toDouble() / want
        table = kernel(minOf(1.0, want.toDouble() / input.sampleRate) * 0.96)
        reset0()
        // Out in the encoding it came in: 16-bit stays 16-bit (Android Auto's sound output, for one, won't take float)
        return AudioProcessor.AudioFormat(want, ch, input.encoding)
    }

    /** The filter at each fractional position: sinc at the cutoff (fc of the input's Nyquist), Kaiser-windowed (beta 8),
     *  each row summing to 1 so the level doesn't change. */
    private fun kernel(fc: Double): Array<FloatArray> = Array(phases + 1) { p ->
        val frac = p.toDouble() / phases
        val row = DoubleArray(2 * half) { j ->
            val x = (j - half + 1) - frac
            val sinc = if (kotlin.math.abs(x) < 1e-9) fc else sin(PI * fc * x) / (PI * x)
            val r = x / half
            sinc * if (kotlin.math.abs(r) >= 1) 0.0 else bessel0(8.0 * sqrt(1 - r * r)) / bessel0(8.0)
        }
        val sum = row.sum()
        FloatArray(row.size) { (row[it] / sum).toFloat() }
    }

    private fun bessel0(x: Double): Double {
        var s = 1.0; var t = 1.0; var k = 1
        while (k < 50) { t *= (x / (2 * k)) * (x / (2 * k)); s += t; if (t < 1e-12 * s) break; k++ }
        return s
    }

    private fun reset0() {
        hist = FloatArray(4096 * ch)
        frames = half   // half frames of silence before the music, so the first outputs have history
        pos = half.toDouble()
    }

    private fun append(src: ByteBuffer) {
        val n = src.remaining() / (if (inFloat) 4 else 2) / ch
        if ((frames + n) * ch > hist.size) hist = hist.copyOf(maxOf(hist.size * 2, (frames + n) * ch))
        val b = src.order(ByteOrder.nativeOrder())
        var w = frames * ch
        repeat(n * ch) { hist[w++] = if (inFloat) b.float else b.short / 32768f }
        frames += n
    }

    /** As many output frames as the input so far allows, then the used-up input dropped. */
    private fun produce() {
        val avail = frames - half - pos   // output needs input up to floor(pos) + half
        val count = if (avail <= 0) 0 else (avail / step).toInt() + 1
        val out = replaceOutputBuffer(count * ch * (if (inFloat) 4 else 2))
        var made = 0
        while (made < count && floor(pos).toInt() + half < frames) {
            val i0 = floor(pos).toInt()
            val coef = table[((pos - i0) * phases).toInt().coerceIn(0, phases)]
            val base = (i0 - half + 1) * ch
            for (c in 0 until ch) {
                var acc = 0f
                var at = base + c
                for (k in coef.indices) { acc += hist[at] * coef[k]; at += ch }
                if (inFloat) out.putFloat(acc) else out.putShort((acc * 32767f).let { kotlin.math.round(it) }.coerceIn(-32768f, 32767f).toInt().toShort())
            }
            pos += step; made++
        }
        out.flip()
        val drop = floor(pos).toInt() - half + 1
        if (drop > 0) {
            System.arraycopy(hist, drop * ch, hist, 0, (frames - drop) * ch)
            frames -= drop; pos -= drop
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        append(inputBuffer)
        produce()
    }

    override fun onQueueEndOfStream() {
        // the last half of the filter: silence after the music, so its end comes out too
        if ((frames + half) * ch > hist.size) hist = hist.copyOf((frames + half) * ch)
        java.util.Arrays.fill(hist, frames * ch, (frames + half) * ch, 0f)
        frames += half
        produce()
    }

    override fun onFlush() { if (table.isNotEmpty()) reset0() }

    override fun onReset() { table = Array(0) { FloatArray(0) }; hist = FloatArray(0); frames = 0 }
}
