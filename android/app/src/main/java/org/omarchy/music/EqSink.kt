package org.omarchy.music

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** What hi-res music (24-bit, 32-bit and float: your FLACs, Tidal's downloads) is handed to Android as.
 *
 *  Normally as 16-bit: the 24 or 32 bits come down to 16 here (with dither, so quiet passages fade smoothly) and the
 *  music then goes through the usual processor chain like any other song (EQ, song volume, ReplayGain, crossfeed,
 *  mono, balance, clip guard, resampling). Before, hi-res music went to Android as 32-bit float, and some sound
 *  outputs refuse float: the phone's own and Android Auto's on the S26 Ultra stopped those songs with
 *  "AUDIO_TRACK_INIT_FAILED" while 16-bit songs (MP3s, CD-quality FLACs) played.
 *
 *  Bit-perfect with a USB DAC is the one exception: then the music goes to the DAC as it is (float, all its bits). */
class EqSink(sink: AudioSink, private val eq: EqProcessor) : ForwardingAudioSink(sink) {
    private var float = false       // bit-perfect: float as it is (the EQ is bypassed then anyway)
    private var toShort = 0         // the bytes of each hi-res sample being brought down to 16-bit (3, 4), else 0
    private var inEncoding = C.ENCODING_INVALID
    private var out: ByteBuffer = ByteBuffer.allocateDirect(0)
    private var pending = false     // out holds a buffer the sink hasn't taken all of yet
    private val rng = java.util.Random()

    private fun hiRes(e: Int) = e == C.ENCODING_PCM_24BIT || e == C.ENCODING_PCM_32BIT || e == C.ENCODING_PCM_FLOAT

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        pending = false
        inEncoding = inputFormat.pcmEncoding
        if (inputFormat.sampleMimeType == androidx.media3.common.MimeTypes.AUDIO_RAW && hiRes(inEncoding) && !Music.bitPerfectNow()) {
            toShort = if (inEncoding == C.ENCODING_PCM_24BIT) 3 else 4
            float = false
            super.configure(inputFormat.buildUpon().setPcmEncoding(C.ENCODING_PCM_16BIT).build(), specifiedBufferSize, outputChannels)
            return
        }
        toShort = 0
        float = inEncoding == C.ENCODING_PCM_FLOAT
        if (float) eq.configureFloat(inputFormat.sampleRate, inputFormat.channelCount)
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (toShort > 0) return handleShort(buffer, presentationTimeUs, encodedAccessUnitCount)
        if (!float || (!pending && eq.idle())) return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        if (!pending) {   // a new buffer: through the EQ once, into out
            val n = buffer.remaining()
            if (out.capacity() < n) out = ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder())
            out.clear()
            eq.process(buffer.duplicate(), out, true)
            out.flip()
            pending = true
        }
        val done = super.handleBuffer(out, presentationTimeUs, encodedAccessUnitCount)
        buffer.position(buffer.limit() - out.remaining())   // the caller sees as much taken as the sink took
        if (done) pending = false
        return done
    }

    /** A hi-res buffer as 16-bit: converted once into out, then handed on (the sink may take it in parts). */
    private fun handleShort(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (!pending) {
            val samples = buffer.remaining() / toShort
            if (out.capacity() < samples * 2) out = ByteBuffer.allocateDirect(samples * 2).order(ByteOrder.nativeOrder())
            out.clear()
            val b = buffer.duplicate().order(ByteOrder.nativeOrder())
            repeat(samples) {
                val x: Float = when (inEncoding) {
                    C.ENCODING_PCM_24BIT -> {   // three bytes, low first
                        val v = (b.get().toInt() and 0xFF) or ((b.get().toInt() and 0xFF) shl 8) or (b.get().toInt() shl 16)
                        v / 8388608f
                    }
                    C.ENCODING_PCM_32BIT -> b.int / 2147483648f
                    else -> b.float
                }
                val d = rng.nextFloat() - rng.nextFloat()   // TPDF dither, one 16-bit step
                out.putShort(Math.round(x * 32767f + d).coerceIn(-32768, 32767).toShort())
            }
            out.flip()
            pending = true
        }
        val done = super.handleBuffer(out, presentationTimeUs, encodedAccessUnitCount)
        buffer.position(buffer.limit() - out.remaining() / 2 * toShort)   // as much of the input taken as of out
        if (done) pending = false
        return done
    }

    override fun flush() {
        pending = false
        if (float) eq.resetFloat()
        super.flush()
    }
}
