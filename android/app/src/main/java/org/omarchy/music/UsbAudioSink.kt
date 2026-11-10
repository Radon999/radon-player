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
                active = true; format = inputFormat; inEnc = inputFormat.pcmEncoding
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

    private fun clearState() { startUs = C.TIME_UNSET; ended = false; pending = false; outRead = 0 }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (!active) return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        val h = UsbDirect.handle
        if (h == 0L || UsbNative.error(h) != 0) {
            val err = if (h == 0L) -1 else UsbNative.error(h)
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
        repeat(samples) {
            var l: Int = when (inEnc) {   // the sample, left-justified in 32 bits
                C.ENCODING_PCM_16BIT -> b.short.toInt() shl 16
                C.ENCODING_PCM_24BIT -> ((b.get().toInt() and 0xFF) or ((b.get().toInt() and 0xFF) shl 8) or (b.get().toInt() shl 16)) shl 8
                C.ENCODING_PCM_32BIT -> b.int
                else -> (b.float * 8388608f).roundToInt().coerceIn(-8388608, 8388607) shl 8
            }
            if (g != 1f) l = (l * g.toDouble()).roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
            when (outSlot) {
                2 -> out.putShort((l shr 16).toShort())
                3 -> { val v = l shr 8; out.put(v.toByte()); out.put((v shr 8).toByte()); out.put((v shr 16).toByte()) }
                else -> out.putInt(l)
            }
        }
        out.flip()
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (!active) return super.getCurrentPositionUs(sourceEnded)
        if (startUs == C.TIME_UNSET) return AudioSink.CURRENT_POSITION_NOT_SET
        val h = UsbDirect.handle
        val played = if (h != 0L) UsbNative.played(h) else 0L
        return startUs + played * 1_000_000L / rate
    }

    override fun play() { if (active) UsbDirect.handle.takeIf { it != 0L }?.let { UsbNative.setPaused(it, false) } else super.play() }
    override fun pause() { if (active) UsbDirect.handle.takeIf { it != 0L }?.let { UsbNative.setPaused(it, true) } else super.pause() }
    override fun playToEndOfStream() { if (active) ended = true else super.playToEndOfStream() }
    override fun isEnded(): Boolean = if (active) ended && pendingFrames() == 0L else super.isEnded()
    override fun hasPendingData(): Boolean = if (active) pendingFrames() > 0 else super.hasPendingData()
    private fun pendingFrames(): Long = UsbDirect.handle.let { if (it != 0L) UsbNative.pending(it) else 0L }
    override fun handleDiscontinuity() { if (!active) super.handleDiscontinuity() }

    /** The player's own volume: bit-perfect leaves the samples alone, so only silence (0) is heard as such. */
    override fun setVolume(volume: Float) {
        super.setVolume(volume)
        UsbDirect.handle.takeIf { it != 0L }?.let { UsbNative.setMuted(it, volume <= 0.001f) }
    }

    override fun flush() {
        if (active) { UsbDirect.handle.takeIf { it != 0L }?.let { UsbNative.flush(it) }; clearState() }
        super.flush()
    }

    override fun reset() {
        if (active) { UsbDirect.handle.takeIf { it != 0L }?.let { UsbNative.flush(it) }; clearState() }
        super.reset()
    }

    override fun release() {
        if (active) { active = false; UsbDirect.closeAll() }
        super.release()
    }
}
