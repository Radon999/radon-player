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
class EqSink(sink: AudioSink, private val eq: EqProcessor, private val core: AudioSink? = null) : ForwardingAudioSink(sink) {
    private var float = false       // bit-perfect: float as it is (the EQ is bypassed then anyway)
    private var toShort = 0         // the bytes of each hi-res sample being brought down to 16-bit (3, 4), else 0
    private var inEncoding = C.ENCODING_INVALID
    private var raw16 = false       // plain 16-bit samples from the decoder (not hi-res, not a format passed on unopened)
    // Watching what the decoder gives: music, then nothing but silence for seconds, is a decoder that has stopped
    // (the song goes on counting with no sound until it is moved). Told to the app, which starts it again in place.
    private var inRate = 44100
    private var inChannels = 2
    private var wasLoud = false       // this song has sounded since it was last (re)started
    private var silentFrames = 0L
    private var out: ByteBuffer = ByteBuffer.allocateDirect(0)
    private var pending = false     // out holds a buffer the sink hasn't taken all of yet
    private val rng = java.util.Random()

    private fun hiRes(e: Int) = e == C.ENCODING_PCM_24BIT || e == C.ENCODING_PCM_32BIT || e == C.ENCODING_PCM_FLOAT

    /** The decoders are asked for float only for bit-perfect to a USB DAC. Otherwise they give plain 16-bit (or 24-bit)
     *  samples: the phone's FLAC decoder, asked for float, gave 16-bit FLACs unbearably loud and distorted (MP3s, whose
     *  decoder gives 16-bit anyway, were fine). */
    override fun getFormatSupport(format: Format): Int =
        if (format.sampleMimeType == androidx.media3.common.MimeTypes.AUDIO_RAW && format.pcmEncoding == C.ENCODING_PCM_FLOAT &&
            !asItIs()) AudioSink.SINK_FORMAT_UNSUPPORTED
        else super.getFormatSupport(format)

    /** The music goes on with all its bits: bit-perfect in effect, or about to go straight to the DAC (SinkRules.asItIs:
     *  asked before the DAC's stream is set up, when "in effect" could still be false and a 24-bit song became 16-bit). */
    private fun asItIs() = SinkRules.asItIs(Music.bitPerfectNow(), runCatching { UsbDirect.wanted() }.getOrDefault(false))

    override fun supportsFormat(format: Format): Boolean = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        pending = false; eos = false; eq.sinkEos = false; eq.sinkEnded = false
        lastPts = C.TIME_UNSET; lastDone = true
        inEncoding = inputFormat.pcmEncoding
        inRate = inputFormat.sampleRate.takeIf { it > 0 } ?: 44100
        inChannels = inputFormat.channelCount.takeIf { it > 0 } ?: 2
        wasLoud = false; silentFrames = 0; seenBuf = null
        eq.decoderLoudAt = 0L
        raw16 = inputFormat.sampleMimeType == androidx.media3.common.MimeTypes.AUDIO_RAW && inEncoding == C.ENCODING_PCM_16BIT
        val straight = asItIs()
        SoundLog.note("the output set up for a song (samples $inEncoding, $inRate Hz${if (straight) ", as it is" else ""})")
        if (straight) eq.bypass = true   // every sample as it came (set again from the output once the stream runs)
        // Bit-perfect is on, yet a hi-res song is about to be brought down to 16 bits: said in Settings › Problems
        // (only with a DAC plugged in: without one, bit-perfect left on has nothing to send to, and that is no fault)
        if (Music.bitPerfect && !straight && inputFormat.sampleMimeType == androidx.media3.common.MimeTypes.AUDIO_RAW && hiRes(inEncoding) &&
            runCatching { UsbDirect.device() != null }.getOrDefault(false))
            Crashes.note("Bit-perfect is on and a DAC is plugged in, but this hi-res song is going out as 16-bit through Android (${UsbDirect.note})")
        if (inputFormat.sampleMimeType == androidx.media3.common.MimeTypes.AUDIO_RAW && hiRes(inEncoding) && !straight) {
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

    // "Fed" only when the output really took some of it. The player hands the same buffer in again and again while the
    // output takes nothing (seen over Bluetooth with the DJ on): that counted as music still coming out, so a song that
    // was over was waited on for a whole minute before the next one played.
    // The piece of music last handed in: its time in the song, whether the output finished it, and how much was left.
    private var lastPts = C.TIME_UNSET
    private var lastDone = true
    private var lastLeft = 0

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        // The player has moved on to the next piece while the output was still part-way through the last one. That
        // must never happen (the player is meant to hand the same piece in until it is taken whole), and it is what
        // both faults on the owner's phone come down to (21 Nov 2026): when the new piece is another buffer the output
        // refuses it and the player stops (“Wake Up Call”); when it is the same buffer used again the output takes it
        // for the old one, loses count, and reports a jump in the song's clock (“Only Friend”). Caught here, before
        // the output sees it: the output lets go of the old piece (a few thousandths of a second of music), and it is
        // noted with how far the player jumped, to find what makes the player move on.
        if (lastPts != C.TIME_UNSET && !lastDone && presentationTimeUs != lastPts) {
            val jump = (presentationTimeUs - lastPts) / 1000
            pending = false
            val ok = forgetInput()
            SoundLog.hiccup("the player moved on to the next piece of “${Music.current()?.title}” ($jump ms on) before the sound output had finished the last " +
                "($lastLeft bytes of it left; hi-res to 16-bit: ${toShort > 0}): " + if (ok) "the old piece let go" else "it couldn't be let go")
        }
        val before = buffer.remaining()
        val done = try { feed(buffer, presentationTimeUs, encodedAccessUnitCount) }
        catch (e: IllegalArgumentException) {
            // Android's output was still holding a buffer from before and was handed another: it refuses that
            // ("FAILED_RUNTIME_CHECK"), the player stops, and the song went on counting with no sound, again each time
            // it was loaded (seen on “Summer”, 20 Nov 2026). What it holds is let go, and the buffer is handed in again.
            val how = "hi-res to 16-bit: ${toShort > 0}, float: $float, a buffer waiting here: $pending, the song's samples: $inEncoding"
            pending = false
            lastPts = C.TIME_UNSET; lastDone = true
            val cleared = forget()
            Crashes.note("The sound output was holding an old buffer while “${Music.current()?.title}” played ($how): " +
                (if (cleared) "let go, and the song carried on" else "it couldn't be let go"))
            SoundLog.note("the sound output held an old buffer ($how): ${if (cleared) "let go" else "not let go"}")
            if (!cleared) throw e
            // (what comes next isn't where the old buffer left off: told so, so the output lines its clock up quietly
            // and doesn't report the jump as a fault)
            runCatching { super.handleDiscontinuity() }
            feed(buffer, presentationTimeUs, encodedAccessUnitCount)
        }
        if (done || buffer.remaining() != before) eq.sinkFedAt = System.currentTimeMillis()
        lastPts = presentationTimeUs; lastDone = done; lastLeft = buffer.remaining()
        return done
    }

    /** Only the piece the output was part-way through is let go (not what it is already writing out, and its sound
     *  track stays): the lightest way to put it right, with nothing to hear. */
    private fun forgetInput(): Boolean = runCatching {
        val sink = core ?: return@runCatching false
        var c: Class<*>? = sink.javaClass
        while (c != null) {
            val f = runCatching { c!!.getDeclaredField("inputBuffer") }.getOrNull()
            if (f != null) { f.isAccessible = true; f.set(sink, null); return@runCatching true }
            c = c.superclass
        }
        false
    }.getOrDefault(false)

    /** Android's output made to forget the buffer it was part-way through (and the one it was writing): its own flush
     *  only does that while it has a sound track open, and it can be holding one with none. By its fields' names in
     *  the player library (1.4.1; the app isn't shrunk, so they are as written); false if they can't be reached. */
    private fun forget(): Boolean = runCatching {
        runCatching { super.flush() }
        val sink = core ?: return@runCatching false
        var c: Class<*>? = sink.javaClass
        var done = 0
        while (c != null && done < 2) {
            for (name in listOf("inputBuffer", "outputBuffer")) runCatching {
                c!!.getDeclaredField(name).apply { isAccessible = true }.set(sink, null); done++
            }
            c = c.superclass
        }
        done >= 1
    }.getOrDefault(false)

    // The 16-bit music (MP3s, streams, CD-quality FLACs) is watched as the hi-res music is: before, a decoder that
    // stopped giving sound on one of those went unnoticed, and the song played on silent until it was moved.
    private var seenBuf: ByteBuffer? = null
    private var seenLeft = -1
    private fun watch16(buffer: ByteBuffer) {
        if (!raw16 || (buffer === seenBuf && buffer.remaining() == seenLeft)) return   // (the same buffer handed in again)
        seenBuf = buffer; seenLeft = buffer.remaining()
        val b = buffer.duplicate().order(ByteOrder.nativeOrder())
        var peak = 0
        var i = b.position()
        val hop = 2 * inChannels * 4
        while (i + 2 <= b.limit()) { val v = b.getShort(i).toInt(); if (v > peak) peak = v else if (-v > peak) peak = -v; i += hop }
        heard(peak / 32768f, buffer.remaining() / 2 / inChannels)
    }

    /** What the decoder just gave: music (noted, for the equalizer's own watch), or nothing after music for four
     *  seconds with the song not at its end: the decoder has stopped, and the app starts the song again in place. */
    private fun heard(peak: Float, frames: Int) {
        if (peak > 0.02f) { wasLoud = true; silentFrames = 0; eq.decoderLoudAt = System.currentTimeMillis() }
        else if (peak < 0.0002f && wasLoud && !eos) {
            silentFrames += frames
            if (silentFrames > 4L * inRate) { silentFrames = 0; wasLoud = false; eq.onDead?.invoke("at the decoder (it gave four seconds of nothing after music)") }
        } else silentFrames = 0
    }

    private fun feed(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (toShort > 0) return handleShort(buffer, presentationTimeUs, encodedAccessUnitCount)
        watch16(buffer)
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
            var peak = 0f
            repeat(samples) {
                val x: Float = when (inEncoding) {
                    C.ENCODING_PCM_24BIT -> {   // three bytes, low first
                        val v = (b.get().toInt() and 0xFF) or ((b.get().toInt() and 0xFF) shl 8) or (b.get().toInt() shl 16)
                        v / 8388608f
                    }
                    C.ENCODING_PCM_32BIT -> b.int / 2147483648f
                    else -> b.float
                }
                if (x > peak) peak = x else if (-x > peak) peak = -x
                val d = rng.nextFloat() - rng.nextFloat()   // TPDF dither, one 16-bit step
                out.putShort(Math.round(x * 32767f + d).coerceIn(-32768, 32767).toShort())
            }
            out.flip()
            pending = true
            heard(peak, samples / inChannels)
        }
        val done = super.handleBuffer(out, presentationTimeUs, encodedAccessUnitCount)
        buffer.position(buffer.limit() - out.remaining() / 2 * toShort)   // as much of the input taken as of out
        if (done) pending = false
        return done
    }

    // The song's end handed in (playToEndOfStream) and all of it played out: told to the app (Engine.audioEnded), so it
    // moves on the moment the music's over even when the player is slow to say it ended.
    private var eos = false
    override fun playToEndOfStream() {
        if (!eos) eq.sinkEosAt = System.currentTimeMillis()   // (the player calls this over and over until it's played out)
        eos = true; eq.sinkEos = true; super.playToEndOfStream()
    }
    override fun isEnded(): Boolean { val e = super.isEnded(); eq.sinkEnded = eos && e; return e }
    override fun reset() { eos = false; eq.sinkEos = false; eq.sinkEnded = false; lastPts = C.TIME_UNSET; lastDone = true; super.reset() }

    override fun flush() {
        eos = false; eq.sinkEos = false; eq.sinkEnded = false
        pending = false
        lastPts = C.TIME_UNSET; lastDone = true
        wasLoud = false; silentFrames = 0; seenBuf = null
        if (float) eq.resetFloat()
        super.flush()
    }
}
