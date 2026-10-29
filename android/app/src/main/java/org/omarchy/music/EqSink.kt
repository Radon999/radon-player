package org.omarchy.music

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The EQ (and song volume, ReplayGain, crossfeed, mono, balance, clip guard) for hi-res music. With float output
 *  the player hands 24-bit and float music straight to the output, past its audio processors (they only run on 16-bit
 *  music), so the EQ never touched it. Here every buffer of float music goes through the same EqProcessor first, and
 *  stays float (32-bit) all the way to the DAC. 16-bit music still goes through the processor chain as before. */
class EqSink(sink: AudioSink, private val eq: EqProcessor) : ForwardingAudioSink(sink) {
    private var float = false
    private var out: ByteBuffer = ByteBuffer.allocateDirect(0)
    private var pending = false   // out holds a buffer the sink hasn't taken all of yet

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        float = inputFormat.pcmEncoding == C.ENCODING_PCM_FLOAT
        if (float) eq.configureFloat(inputFormat.sampleRate, inputFormat.channelCount)
        pending = false
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
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

    override fun flush() {
        pending = false
        if (float) eq.resetFloat()
        super.flush()
    }
}
