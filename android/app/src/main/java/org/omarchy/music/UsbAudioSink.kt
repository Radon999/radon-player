package org.omarchy.music

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** The player's sound output: Android's (the inner sink) as always, or, with bit-perfect on and a USB DAC that Android
 *  can't play bit-perfect, straight to the DAC ([UsbDirect]). Then each song's samples go to the DAC as they are, only
 *  padded into the DAC's sample size (16-bit and 24-bit music, and the decoders' float, which holds 24 bits exactly). */
class UsbAudioSink(inner: AudioSink) : ForwardingAudioSink(inner) {
    private var active = false
    private var format: Format? = null
    private var inEnc = C.ENCODING_INVALID
    private var channels = 2
    private var rate = 44100
    private var outSlot = 3
    private var startUs = C.TIME_UNSET
    private var ended = false
    private var out: ByteBuffer = ByteBuffer.allocateDirect(0)
    private var pending = false
    private var outRead = 0      // bytes of out already handed to the DAC
    private var inPerOut = 1.0   // input bytes per output byte
    private var mine = 0L        // the DAC stream this sink started
    private var muted = false
    /** Whether the player has this sink playing (play/pause), kept here: the DAC's stream can be (re)started while the
     *  player is paused or playing, and it must follow that. Before, a stream left paused (an error and its retry, the
     *  app coming back after a crash) stayed paused while the player played, and the music went on and off until you
     *  pressed Pause and Play. */
    private var playing = false
    private var restarts = 0
    private val tones = Tones()

    private fun raw(f: Format) = f.sampleMimeType == MimeTypes.AUDIO_RAW &&
        f.pcmEncoding in setOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT)

    override fun getFormatSupport(format: Format): Int =
        if (raw(format) && format.channelCount <= 2 && UsbDirect.wanted()) AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        else super.getFormatSupport(format)

    override fun supportsFormat(format: Format): Boolean = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        clearState()
        if (raw(inputFormat) && UsbDirect.wanted()) {
            val src = when (inputFormat.pcmEncoding) { C.ENCODING_PCM_16BIT -> 16; C.ENCODING_PCM_32BIT -> 32; else -> 24 }
            val f = UsbDirect.begin(inputFormat.sampleRate, inputFormat.channelCount, src)
            Music.dacNote = UsbDirect.note
            if (f != null) {
                active = true; mine = UsbDirect.handle; UsbNative.setMuted(mine, muted); UsbNative.setPaused(mine, !playing); format = inputFormat; inEnc = inputFormat.pcmEncoding
                channels = inputFormat.channelCount; rate = inputFormat.sampleRate; outSlot = f.subslot
                val inBytes = when (inEnc) { C.ENCODING_PCM_16BIT -> 2; C.ENCODING_PCM_24BIT -> 3; else -> 4 }
                inPerOut = inBytes.toDouble() / outSlot
                return
            }
            UsbDirect.closeAll()   // this song can't go straight there: the DAC back to Android for it
        }
        if (active) { active = false; UsbDirect.closeAll() }
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    private fun clearState() { startUs = C.TIME_UNSET; ended = false; pending = false; outRead = 0; restarts = 0 }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (!active) return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        if (UsbDirect.speaking) return false   // the DJ's voice has the DAC for a moment (see UsbDirect.speak)
        // The DAC stopped taking music and sending the transfers again didn't help: set up again from scratch here
        if (UsbDirect.restartWanted != 0L && UsbDirect.restartWanted == mine) UsbDirect.restartStream()
        var h = UsbDirect.handle
        // The stream stopped on its own (its transfers refused: "AudioTrack write failed: 32") while the DAC's still
        // plugged in: set up again and carried on, not a failed song
        if (h != 0L && h == mine && UsbNative.error(h).let { it != 0 && it != 19 } && UsbDirect.device() != null) {
            UsbDirect.restartStream(); h = UsbDirect.handle
        }
        // The stream isn't this player's any more (another player, a preview, took the DAC, or it was stopped): set up
        // again for this song and carried on where it was, rather than failing ("AudioTrack write failed: -1")
        if (h != mine && UsbDirect.wanted()) {
            val f = format
            val again = f?.let { UsbDirect.begin(rate, channels, when (inEnc) { C.ENCODING_PCM_16BIT -> 16; C.ENCODING_PCM_32BIT -> 32; else -> 24 }) }
            if (again != null && again.subslot == outSlot && UsbDirect.handle != 0L) {
                h = UsbDirect.handle; mine = h
                UsbNative.setPaused(h, !playing); UsbNative.setMuted(h, muted)
                // What the old stream hadn't sent yet goes first, so nothing is skipped
                val left = UsbDirect.carry; UsbDirect.carry = null
                var carried = 0L
                if (left != null && left.isNotEmpty()) {
                    val b = ByteBuffer.allocateDirect(left.size); b.put(left); b.flip()
                    val n = UsbNative.write(h, b, 0, left.size)
                    if (n > 0) carried = n.toLong() / (outSlot * channels)
                }
                startUs = presentationTimeUs - (UsbNative.played(h) + carried) * 1_000_000L / rate
                // Set up again quietly (it just carries on); one song needing it again and again is noted, once
                if (++restarts == 4) Crashes.note("USB DAC: the stream kept stopping on this song and was set up again ${restarts} times · ${UsbDirect.lastSetup}")
            }
        }
        if (h == 0L || h != mine || UsbNative.error(h) != 0) {
            val err = if (h == 0L || h != mine) -1 else UsbNative.error(h)
            active = false; Music.usbDirect = false
            throw AudioSink.WriteException(err, format ?: Format.Builder().build(), false)
        }
        if (startUs == C.TIME_UNSET) startUs = presentationTimeUs
        if (!pending) {
            convert(buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN))
            pending = true; outRead = 0
        }
        val n = UsbNative.write(h, out, outRead, out.limit() - outRead)
        if (n < 0) throw AudioSink.WriteException(UsbNative.error(h), format ?: Format.Builder().build(), false)
        outRead += n
        val left = out.limit() - outRead
        buffer.position(buffer.limit() - (left * inPerOut).roundToInt().coerceIn(0, buffer.remaining()))
        if (left == 0) { pending = false; buffer.position(buffer.limit()); return true }
        return false
    }

    /** The buffer's samples into the DAC's sample size (little-endian), the app's own volume only if the DAC has none. */
    private fun convert(b: ByteBuffer) {
        val inBytes = when (inEnc) { C.ENCODING_PCM_16BIT -> 2; C.ENCODING_PCM_24BIT -> 3; else -> 4 }
        val samples = b.remaining() / inBytes
        if (out.capacity() < samples * outSlot) out = ByteBuffer.allocateDirect(samples * outSlot + 64).order(ByteOrder.LITTLE_ENDIAN)
        out.clear()
        val g = UsbDirect.digitalGain
        val tone = tones.ready(rate, channels)
        var ch = 0
        var mono = 0f
        repeat(samples) {
            var l: Int = when (inEnc) {   // the sample, left-justified in 32 bits
                C.ENCODING_PCM_16BIT -> b.short.toInt() shl 16
                C.ENCODING_PCM_24BIT -> ((b.get().toInt() and 0xFF) or ((b.get().toInt() and 0xFF) shl 8) or (b.get().toInt() shl 16)) shl 8
                C.ENCODING_PCM_32BIT -> b.int
                else -> (b.float.toDouble() * 2147483648.0).roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()   // all of float's precision (24-bit music exactly as it was)
            }
            if (tone) l = tones.run(ch, l, g.toDouble())
            else if (g != 1f) l = (l * g.toDouble()).roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
            mono += l / 2147483648f
            if (ch + 1 >= channels) AudioTap.push(mono / channels).also { mono = 0f }   // for the visualizers
            ch = if (ch + 1 >= channels) 0 else ch + 1
            when (outSlot) {
                2 -> out.putShort((l shr 16).toShort())
                3 -> { val v = l shr 8; out.put(v.toByte()); out.put((v shr 8).toByte()); out.put((v shr 16).toByte()) }
                else -> out.putInt(l)
            }
        }
        out.flip()
        AudioTap.lag = { pendingFrames() }
        AudioTap.pushed(rate)
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (!active) return super.getCurrentPositionUs(sourceEnded)
        if (startUs == C.TIME_UNSET) return AudioSink.CURRENT_POSITION_NOT_SET
        val h = mine
        val played = if (h != 0L && h == UsbDirect.handle) UsbNative.played(h) else 0L
        return startUs + played * 1_000_000L / rate
    }

    override fun play() { playing = true; UsbDirect.sinkPlaying = true; if (active) own()?.let { UsbNative.setPaused(it, false) } else super.play() }
    override fun pause() { playing = false; UsbDirect.sinkPlaying = false; if (active) own()?.let { UsbNative.setPaused(it, true) } else super.pause() }
    override fun playToEndOfStream() { if (active) ended = true else super.playToEndOfStream() }
    override fun isEnded(): Boolean = if (active) ended && pendingFrames() == 0L else super.isEnded()
    override fun hasPendingData(): Boolean = if (active) pendingFrames() > 0 else super.hasPendingData()
    private fun pendingFrames(): Long = own()?.let { UsbNative.pending(it) } ?: 0L
    /** The DAC stream, if it's still this player's. */
    private fun own(): Long? = mine.takeIf { it != 0L && it == UsbDirect.handle }
    override fun handleDiscontinuity() { if (!active) super.handleDiscontinuity() }

    /** The player's own volume: bit-perfect leaves the samples alone, so only silence (0) is heard as such. */
    override fun setVolume(volume: Float) {
        super.setVolume(volume)
        muted = volume <= 0.001f
        UsbDirect.sinkMuted = muted
        if (UsbDirect.speaking) return   // the DJ's voice has the stream: it isn't muted under her
        own()?.let { UsbNative.setMuted(it, volume <= 0.001f) }
    }

    override fun flush() {
        if (active) UsbDirect.waitSpeech()
        if (active) { own()?.let { UsbNative.flush(it) }; clearState() }
        super.flush()
    }

    override fun reset() {
        if (active) UsbDirect.waitSpeech()
        playing = false
        if (active) { own()?.let { UsbNative.flush(it) }; clearState() }
        super.reset()
    }

    override fun release() {
        // Only the stream this player started: a preview player let go never takes the DAC from the main one
        playing = false
        if (active) { active = false; if (UsbDirect.handle == mine) UsbDirect.closeAll() }
        super.release()
    }
}

/** Bit-perfect with the equalizer's Bass and Treble kept (Equalizer › Bass and Treble with bit-perfect): the two
 *  shelves (100 Hz and 8 kHz, as the equalizer's), turned down first by what they boost so nothing clips, and the
 *  phone's volume, in double precision on the samples as they go to the DAC. Only while either isn't 0 dB. */
private class Tones {
    private var key = ""
    private var c = emptyArray<DoubleArray>()          // each shelf's b0, b1, b2, a1, a2
    private var st = Array(2) { DoubleArray(4) }       // each channel's x1, x2, y1, y2 for each shelf (2 shelves)
    private var st2 = Array(2) { DoubleArray(4) }
    private var pre = 1.0

    /** Whether the tones are on, set up for this rate and these channels (and the current Bass and Treble). */
    fun ready(rate: Int, channels: Int): Boolean {
        if (!Music.bpTonesNow()) { if (key.isNotEmpty()) key = ""; return false }
        val s = Music.eqProcessor.settings
        val k = "$rate/$channels/${s.bass}/${s.treble}"
        if (k != key) {
            val fs = listOf(Filter("ls", 100.0, s.bass, 0.71), Filter("hs", 8000.0, s.treble, 0.71))
            c = fs.map { f -> Eq.coeffs(f, rate.toDouble()).let { a -> doubleArrayOf(a[0] / a[3], a[1] / a[3], a[2] / a[3], a[4] / a[3], a[5] / a[3]) } }.toTypedArray()
            if (st.size != channels || key.substringBefore('/') != rate.toString()) {
                st = Array(channels) { DoubleArray(4) }; st2 = Array(channels) { DoubleArray(4) }
            }
            pre = Math.pow(10.0, Eq.preamp(fs.filter { it.gain != 0.0 }) / 20)
            key = k
        }
        return true
    }

    private fun biquad(k: DoubleArray, s: DoubleArray, x: Double): Double {
        val y = k[0] * x + k[1] * s[0] + k[2] * s[1] - k[3] * s[2] - k[4] * s[3]
        s[1] = s[0]; s[0] = x; s[3] = s[2]; s[2] = y
        return y
    }

    /** One sample (left-justified in 32 bits) on channel ch, through both shelves and the gain. */
    fun run(ch: Int, l: Int, gain: Double): Int {
        val i = ch.coerceAtMost(st.size - 1)
        var x = l / 2147483648.0 * pre
        x = biquad(c[0], st[i], x)
        x = biquad(c[1], st2[i], x)
        x *= gain
        return (x * 2147483648.0).roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }
}
