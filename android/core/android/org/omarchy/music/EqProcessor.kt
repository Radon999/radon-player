package org.omarchy.music

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Where the sound goes: the phone's speaker, wired headphones, each Bluetooth device by its name (your car,
 *  your earbuds), USB. Each is a key ("speaker", "wired", "bt:Name", "usb:Name") with a label and a kind. */
object Outputs {
    data class Out(val key: String, val label: String, val kind: String)

    /** Android Auto is on (wired over USB or wireless): the sound goes to the car through it, not as a Bluetooth
     *  or USB audio device, so Android names no output for it. That's the Chevy, with its own EQ. */
    @Volatile var carProjecting = false
    @Volatile private var lastUsb: Out? = null   // the USB DAC last seen (for while bit-perfect has it)

    /** Whether Android Auto is connected now, wired or wireless: what Android Auto tells apps (androidx's CarConnection
     *  reads the same: 2 is projection). */
    fun projecting(ctx: android.content.Context): Boolean = runCatching {
        ctx.contentResolver.query(android.net.Uri.parse("content://androidx.car.app.connection"), arrayOf("CarConnectionState"),
            null, null, null)?.use { c -> c.moveToFirst() && c.getInt(0) == 2 } ?: false
    }.getOrDefault(false)

    fun now(ctx: android.content.Context): Out {
        val am = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val outs = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
        fun name(d: android.media.AudioDeviceInfo) = d.productName?.toString()?.takeIf { it.isNotBlank() && it != android.os.Build.MODEL }
        // Ignore Bluetooth devices is keeping the music on your headphones while the car's connected (Android Auto too): those, for the EQ
        BtRoute.routed?.let { return it }
        // Android Auto (the truck's screen, over USB or wireless after Bluetooth connects): first, as it carries the sound
        if (carProjecting) return Out("car:Chevrolet", "Chevrolet Colorado · Android Auto", "car")
        outs.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
            it.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER }?.let { d ->
            val n = name(d) ?: "Bluetooth"
            val car = Regex("(?i)car|auto|sync|uconnect|vw|toyota|ford|honda|bmw|audi|mazda|kia|hyundai|chevy|chevrolet|colorado|silverado|gmc|mylink|nissan|tesla|subaru|jeep|dodge|mercedes|lexus").containsMatchIn(n)
            return Out("bt:$n", n, if (car) "car" else "bluetooth")
        }
        outs.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET || it.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE }?.let { d ->
            val n = name(d) ?: "USB"
            return Out("usb:$n", "$n (USB)", "usb").also { lastUsb = it }
        }
        // Bit-perfect has the USB DAC (the app took it from Android, so Android no longer lists it): still the DAC, with
        // its own EQ and Boost, not the phone's speaker
        if (Music.usbDirect) lastUsb?.let { return it }
        outs.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET }?.let { return Out("headset", "Headset (jack, with mic)", "wired") }
        outs.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES }?.let { return Out("wired", "Headphones (jack)", "wired") }
        return Out("speaker", "Built-in speaker", "speaker")
    }

    fun kindOf(key: String) = when {
        key == "speaker" -> "speaker"
        key == "wired" || key == "headset" -> "wired"
        key.startsWith("usb:") -> "usb"
        key.startsWith("car:") -> "car"
        else -> "bluetooth"
    }
}

/** The EQ in the player: a cascade of biquads per channel, in double precision. New
 *  settings swap the coefficients in without resetting the filters, so there's no gap. */
class EqProcessor : BaseAudioProcessor() {
    /** Set by its player's EqSink: the song's last sound has been played out (see Engine.audioEnded). */
    @Volatile var sinkEnded = false
    /** When its player last handed the sink music (ms), and whether the song's end has been handed in: see Engine.audioPlaying. */
    @Volatile var sinkFedAt = 0L
    @Volatile var sinkEos = false
    /** When the song's end was handed to the sound output (the clock's milliseconds): all of it is on its way out then,
     *  and what's left to hear is only what the output still holds, a second or so. */
    @Volatile var sinkEosAt = 0L
    private class Stage(val c: DoubleArray, val state: Array<DoubleArray>)

    @Volatile var settings = EqSettings(); private set
    @Volatile private var stages: List<Stage> = emptyList()
    @Volatile private var gain = 1.0
    @Volatile private var boostGain = 1.0   // Boost (Equalizer): always with the limiter
    @Volatile var songGain = 1.0   // the playing song's own volume (Volume…), on top
    @Volatile var rgGain = 1.0     // ReplayGain, on top of that
    /** Keep playing: how loud the music is while another app has the sound for a moment (1: full), eased to a buffer at
     *  a time so it doesn't click. */
    @Volatile var focusTarget = 1.0
    @Volatile private var focusNow = 1.0
    @Volatile var bypass = false   // bit-perfect: every sample passes exactly as it came
    /** Headphone crossfeed (Equalizer › Crossfeed): a little of each side, its highs softened, into the other, as you'd
     *  hear speakers across a room. The desktop's ffmpeg crossfeed, its strength 0.3. */
    @Volatile var crossfeed = false
    /** Mono (Now playing's Mono button, or Auto mono): left and right mixed into both ears, for one earbud in. */
    @Volatile var mono = false
    /** Clip guard (Settings › Sound): a peak limiter instead of hard clipping when a song's volume, ReplayGain or the
     *  EQ would go over full scale. Only ever acts on a peak that would clip. */
    @Volatile var limiter = false
    /** Dither (Settings › Sound): TPDF dither when the result is cut to 16-bit, so quiet passages fade smoothly. */
    @Volatile var dither = false
    /** Smooth distortion on loud songs (Settings › Sound quality): [PeakRepairProcessor], first in the chain, rebuilds
     *  peaks clipped in the master and takes 3 dB off; here, at the end, a soft knee instead of hard clipping (when the
     *  limiter isn't on: it already keeps everything under full scale). */
    @Volatile var smooth = false
    /** Balance: -1 (all left) … 0 … 1 (all right); the other side is turned down, never up. */
    @Volatile var balance = 0.0
    /** Crossfeed's strength: 0.2 light, 0.3 medium, 0.45 strong. */
    @Volatile var crossfeedK = 0.3
    /** Loudness compensation: how far (dB) the volume is turned down; bass and treble lifted to match (0: off). */
    @Volatile var loudnessDb = 0.0
        set(v) { if (kotlin.math.abs(v - field) >= 0.5) { field = v; rebuild() } }
    /** Tilt (Settings › Sound), dB: brighter (> 0) turns everything below 1 kHz down, warmer (< 0) everything above.
     *  Only ever down, so it can't clip. */
    @Volatile var tilt = 0.0
        set(v) { if (v != field) { field = v; rebuild() } }
    /** Detail (Settings › Sound): the sides (ambience, background parts) a touch louder, presence and air lifted a little. */
    @Volatile var detail = false
        set(v) { if (v != field) { field = v; rebuild() } }
    private var env = 0.0   // the limiter's envelope
    // Watching for the sound going dead: numbers that ran away (not a number, or without end), and music coming in
    // while nothing goes out. Either is set right at once and noted in Settings › Problems (at most once a minute).
    private var deadFrames = 0L
    private var lostFrames = 0L
    /** When the decoder last gave real sound (the clock's milliseconds): set by EqSink for hi-res music, 0 otherwise. */
    @Volatile var decoderLoudAt = 0L
    /** Called (from the sound's own thread) when a song has gone silent though it should be sounding: where the sound
     *  stopped. MusicService notes it and starts the song again from the same place, which brings the sound back. */
    @Volatile var onDead: ((String) -> Unit)? = null
    /** When real sound last left the processors for the sound output (the clock's milliseconds; 0: not yet). If a song
     *  is silent to you while this keeps moving, the music was handed to Android whole and went quiet after the app. */
    @Volatile var outLoudAt = 0L

    /** The end of the chain, every buffer: the decoder is giving music, yet silence arrives here: lost on the way
     *  (peak repair, resampling). Three seconds of that and the app starts the song again in place. */
    private fun watchEnd(inPeak: Double, outPeak: Double, frames: Int) {
        val now = System.currentTimeMillis()
        if (outPeak > 0.02) { outLoudAt = now; SoundLog.outLoudAt = now }
        if (inPeak < 0.0005 && decoderLoudAt > 0 && now - decoderLoudAt < 1500) {
            lostFrames += frames
            if (lostFrames > 3 * rate) { lostFrames = 0; onDead?.invoke("between the decoder and the equalizer (peak repair or resampling)") }
        } else lostFrames = 0
    }

    /** Set right again by itself: a hiccup (Settings › Problems hears of it only if the sound keeps cutting out). */
    private fun note(what: String) = SoundLog.hiccup(what)
    private fun heal() {
        stages.forEach { s -> s.state.forEach { it.fill(0.0) } }
        lpL = 0.0; lpR = 0.0; env = 0.0
    }
    private val rnd = java.util.Random()
    private var lpL = 0.0
    private var lpR = 0.0
    private var rate = 48000.0
    private var channels = 2

    /** Another processor's settings and levels (crossfade's second player sounds the same as the first). */
    fun copyFrom(o: EqProcessor) {
        songGain = o.songGain; rgGain = o.rgGain; bypass = o.bypass; crossfeed = o.crossfeed; mono = o.mono; limiter = o.limiter
        smooth = o.smooth; dither = o.dither; balance = o.balance; crossfeedK = o.crossfeedK; loudnessDb = o.loudnessDb; tilt = o.tilt; detail = o.detail
        update(o.settings)
    }

    fun update(s: EqSettings) {
        settings = s
        rebuild()
    }

    /** The loudness compensation's shelves for loudnessDb (ISO 226's lift at low levels, roughly): bass up to +10 dB,
     *  treble up to +4 dB. */
    private fun loudnessFilters(): List<Filter> {
        val d = loudnessDb
        if (d < 0.5) return emptyList()
        return listOf(Filter("ls", 90.0, min(10.0, d * 0.3), 0.71), Filter("hs", 9000.0, min(4.0, d * 0.12), 0.71))
    }

    private fun rebuild() {
        val s = settings
        boostGain = 10.0.pow(s.boost.coerceIn(0.0, Eq.BOOST_MAX) / 20)
        val t = tilt
        val extra = loudnessFilters() + when {
            t > 0 -> listOf(Filter("ls", 1000.0, -t, 0.5))
            t < 0 -> listOf(Filter("hs", 1000.0, t, 0.5))
            else -> emptyList()
        } + if (detail) listOf(Filter("pk", 3000.0, 1.5, 1.0), Filter("hs", 10000.0, 1.5, 0.7)) else emptyList()
        if (!s.on && extra.isEmpty()) { stages = emptyList(); gain = 1.0; return }
        val fs = (if (s.on) Eq.filters(s) else emptyList()) + extra
        val old = stages
        stages = fs.mapIndexed { i, f ->
            val c = Eq.coeffs(f, rate).let { k -> DoubleArray(5) { j -> (if (j < 3) k[j] else k[j + 1]) / k[3] } }
            Stage(c, old.getOrNull(i)?.state?.takeIf { it.size == channels } ?: Array(channels) { DoubleArray(2) })
        }
        gain = 10.0.pow(Eq.preamp(fs) / 20)
    }

    override fun onConfigure(input: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (input.encoding != C.ENCODING_PCM_16BIT && input.encoding != C.ENCODING_PCM_FLOAT)
            throw AudioProcessor.UnhandledAudioFormatException(input)
        rate = input.sampleRate.toDouble()
        channels = input.channelCount
        stages = emptyList()
        rebuild()
        return input
    }

    override fun queueInput(input: ByteBuffer) {
        val n = input.remaining()
        if (n == 0) return
        val out = replaceOutputBuffer(n)
        process(input, out, inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT)
        out.flip()
    }

    /** Nothing to do: every sample would pass exactly as it came. */
    fun idle() = bypass || (stages.isEmpty() && songGain == 1.0 && rgGain == 1.0 && !crossfeed && !mono && !detail && balance == 0.0 && boostGain == 1.0 &&
        focusNow == 1.0 && focusTarget == 1.0)

    /** The float path (EqSink, for hi-res): set up for rate and channels outside the player's processor chain. */
    fun configureFloat(sampleRate: Int, channelCount: Int) {
        rate = sampleRate.toDouble()
        channels = channelCount
        stages = emptyList()
        rebuild()
    }

    fun resetFloat() = onFlush()

    /** input's samples (16-bit or float), through gain, the EQ, balance, crossfeed, mono and the limiter (or Smooth
     *  distortion's soft knee), into out. */
    fun process(input: ByteBuffer, out: ByteBuffer, float: Boolean) {
        val n = input.remaining()
        val st = stages
        input.order(ByteOrder.nativeOrder())
        out.order(ByteOrder.nativeOrder())
        if (idle()) {
            // Nothing to do to the samples, but still watched: music the decoder gives that arrives here as silence
            // (lost in peak repair or resampling) was never noticed with the EQ off, which is how most songs play.
            val size = if (float) 4 else 2
            var peak = 0.0
            var i = input.position()
            val end = input.limit()
            val hop = size * maxOf(1, channels) * 4   // every fourth frame is plenty to tell music from nothing
            while (i + size <= end) {
                val v = kotlin.math.abs(if (float) input.getFloat(i).toDouble() else input.getShort(i) / 32768.0)
                if (v > peak) peak = v
                i += hop
            }
            watchEnd(peak, peak, n / size / maxOf(1, channels))
            out.put(input)
            return
        }
        // (towards Keep playing's level, a step a buffer: from full to turned down in about a fifth of a second)
        val ft = focusTarget
        if (focusNow != ft) focusNow = if (kotlin.math.abs(ft - focusNow) <= 0.08) ft else focusNow + (if (ft > focusNow) 0.08 else -0.08)
        val g = (if (st.isEmpty()) 1.0 else gain) * songGain * rgGain * boostGain * focusNow
        val b = balance
        val gl = if (b > 0) 1.0 - b else 1.0
        val gr = if (b < 0) 1.0 + b else 1.0
        val lim = limiter || boostGain > 1.0
        val soft = smooth && !lim
        val rel = kotlin.math.exp(-1.0 / (0.08 * rate))   // the limiter lets go over 80 ms
        val th = 0.97
        val dith = dither && !float
        var ch = 0
        var ran = false      // a sample that isn't a number any more
        var inPeak = 0.0     // the loudest sample that came in, and that went out
        var outPeak = 0.0
        while (input.hasRemaining()) {
            val raw = if (float) input.float.toDouble() else input.short / 32768.0
            if (kotlin.math.abs(raw) > inPeak) inPeak = kotlin.math.abs(raw)
            var x = raw * g
            for (s in st) {
                val z = s.state[ch]
                val c = s.c
                val y = c[0] * x + z[0]
                z[0] = c[1] * x - c[3] * y + z[1]
                z[1] = c[2] * x - c[4] * y
                x = y
            }
            if (channels == 2) x *= if (ch == 0) gl else gr
            if (lim) {
                val a = kotlin.math.abs(x)
                env = if (a > env) a else env * rel
                if (env > th) x *= th / env
            } else if (soft) x = SoftClip.apply(x)
            if (x.isNaN() || x.isInfinite()) { x = 0.0; ran = true }
            else if (kotlin.math.abs(x) > outPeak) outPeak = kotlin.math.abs(x)
            if (float) out.putFloat(x.toFloat())
            else {
                val d = if (dith) (rnd.nextDouble() - rnd.nextDouble()) else 0.0   // TPDF: ±1 LSB, triangular
                out.putShort((x * 32768.0 + d).roundToInt().coerceIn(-32768, 32767).toShort())
            }
            ch = (ch + 1) % channels
        }
        // Numbers that ran away would leave the music silent until the next song: everything is set back, so the
        // sound returns within the same moment.
        if (ran || env.isNaN() || env.isInfinite() || lpL.isNaN() || lpR.isNaN()) {
            heal()
            note("The equalizer's numbers ran away mid-song and were set back (gain %.3f: song %.3f, ReplayGain %.3f, boost %.3f; %d filters; EQ %s)"
                .format(g, songGain, rgGain, boostGain, st.size, if (settings.on) "on" else "off"))
        }
        // Music coming in and nothing going out for three seconds: said, with what turned it down
        val frames = n / (if (float) 4 else 2) / maxOf(1, channels)
        if (inPeak > 0.02 && outPeak < 0.0005) {
            deadFrames += frames
            if (deadFrames > 3 * rate) {
                deadFrames = 0
                heal()
                note("Music was coming in but silence going out (gain %.5f: song %.3f, ReplayGain %.3f, boost %.3f; balance %.2f; limiter %.3f; %d filters)"
                    .format(g, songGain, rgGain, boostGain, balance, env, st.size))
            }
        } else deadFrames = 0
        watchEnd(inPeak, outPeak, frames)
        if (detail && channels == 2) widthPass(out, n, float)
        if (crossfeed && channels == 2) crossfeedPass(out, n, float)
        if (mono && channels == 2) monoPass(out, n, float)
    }

    /** Mono over the frames just written to out (n bytes): each side the two sides' average. */
    private fun monoPass(out: ByteBuffer, n: Int, float: Boolean) {
        val size = if (float) 4 else 2
        var i = out.position() - n
        while (i + 2 * size <= out.position()) {
            if (float) { val m = (out.getFloat(i) + out.getFloat(i + size)) / 2f; out.putFloat(i, m); out.putFloat(i + size, m) }
            else { val m = ((out.getShort(i) + out.getShort(i + size)) / 2).toShort(); out.putShort(i, m); out.putShort(i + size, m) }
            i += 2 * size
        }
    }

    /** Detail's wider stereo over the frames just written to out (n bytes): the sides 25% louder, both 10% lower. */
    private fun widthPass(out: ByteBuffer, n: Int, float: Boolean) {
        val size = if (float) 4 else 2
        var i = out.position() - n
        while (i + 2 * size <= out.position()) {
            val l = if (float) out.getFloat(i).toDouble() else out.getShort(i) / 32768.0
            val r = if (float) out.getFloat(i + size).toDouble() else out.getShort(i + size) / 32768.0
            val m = (l + r) / 2; val s = (l - r) / 2 * 1.25
            val nl = (m + s) * 0.9; val nr = (m - s) * 0.9
            if (float) { out.putFloat(i, nl.toFloat()); out.putFloat(i + size, nr.toFloat()) }
            else {
                out.putShort(i, (nl * 32768.0).roundToInt().coerceIn(-32768, 32767).toShort())
                out.putShort(i + size, (nr * 32768.0).roundToInt().coerceIn(-32768, 32767).toShort())
            }
            i += 2 * size
        }
    }

    /** Crossfeed over the frames just written to out (n bytes). */
    private fun crossfeedPass(out: ByteBuffer, n: Int, float: Boolean) {
        val a = 1.0 - kotlin.math.exp(-2.0 * Math.PI * 700.0 / rate)   // the other side's highs softened above 700 Hz
        val k = crossfeedK
        val size = if (float) 4 else 2
        var i = out.position() - n
        while (i + 2 * size <= out.position()) {
            val l = if (float) out.getFloat(i).toDouble() else out.getShort(i) / 32768.0
            val r = if (float) out.getFloat(i + size).toDouble() else out.getShort(i + size) / 32768.0
            lpL += a * (l - lpL); lpR += a * (r - lpR)
            val nl = (l + k * lpR) / (1 + k); val nr = (r + k * lpL) / (1 + k)
            if (float) { out.putFloat(i, nl.toFloat()); out.putFloat(i + size, nr.toFloat()) }
            else {
                out.putShort(i, (nl * 32768.0).roundToInt().coerceIn(-32768, 32767).toShort())
                out.putShort(i + size, (nr * 32768.0).roundToInt().coerceIn(-32768, 32767).toShort())
            }
            i += 2 * size
        }
    }

    override fun onFlush() {
        stages.forEach { s -> s.state.forEach { it.fill(0.0) } }
        lpL = 0.0; lpR = 0.0; env = 0.0
    }
}

/** Smooth distortion on loud songs (Settings › Sound quality), the first step in the phone's chain (before resampling
 *  and the EQ). Some songs are mastered so loud their peaks are cut flat (clipped), which sounds grainy, and anything
 *  that adds level afterwards (EQ boosts, a song's volume, resampling's in-between peaks) clips them again. Here:
 *  the clipped runs are rebuilt as smooth peaks ([Declipper]), then everything is turned down 3 dB ([Smooth.HEADROOM])
 *  so the rebuilt peaks and later boosts have room, with a soft knee ([SoftClip]) just in case. Nothing is made up
 *  afterwards: the music is simply 3 dB quieter while it's on.
 *
 *  Off, every sample passes exactly as it came (5 ms later: the lookahead a clipped run needs). Turned on or off while
 *  a song plays, the level glides over 50 ms instead of jumping. Not in the chain at all in bit-perfect. */
class PeakRepairProcessor(private val eq: EqProcessor) : BaseAudioProcessor() {
    private var dc: Declipper? = null
    private var float = false
    private var gain = 1.0            // glides between 1 and the headroom as the switch changes
    private var glide = 0.0           // how much per sample
    private val rnd = java.util.Random()

    override fun onConfigure(input: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (Music.bitPerfectNow() || input.sampleRate <= 0 || input.channelCount <= 0 ||
            (input.encoding != C.ENCODING_PCM_16BIT && input.encoding != C.ENCODING_PCM_FLOAT)) return AudioProcessor.AudioFormat.NOT_SET
        return input
    }

    /** The new format takes effect here (not in onConfigure: the last song may still be draining then). */
    override fun onFlush() {
        tailDone = false
        val f = inputAudioFormat
        if (f == AudioProcessor.AudioFormat.NOT_SET) { dc = null; return }
        float = f.encoding == C.ENCODING_PCM_FLOAT
        dc = Declipper(f.channelCount, f.sampleRate)
        gain = if (on()) Smooth.HEADROOM else 1.0
        glide = (1.0 - Smooth.HEADROOM) / (0.05 * f.sampleRate * f.channelCount)
    }

    override fun onReset() { dc = null; tailDone = false }

    private fun on() = eq.smooth && !eq.bypass

    override fun queueInput(input: ByteBuffer) {
        val n = input.remaining()
        if (n == 0) return
        val out = replaceOutputBuffer(n)
        val d = dc
        if (d == null) { out.put(input); out.flip(); return }
        input.order(ByteOrder.nativeOrder())
        while (input.hasRemaining()) put(out, d, if (float) input.float.toDouble() else input.short / 32768.0)
        out.flip()
    }

    /** The end of the music: out with the last 5 ms still held for the lookahead. Once a stream: the player library
     *  says "ended" again on every pass while it drains, and a tail made each time never let the drain finish (see
     *  ResampleProcessor.onQueueEndOfStream: the cause of music going silent while it played on). */
    private var tailDone = false
    override fun onQueueEndOfStream() {
        if (tailDone) return
        tailDone = true
        val d = dc ?: return
        val samples = d.delay * d.channels
        val out = replaceOutputBuffer(samples * if (float) 4 else 2)
        repeat(samples) { put(out, d, 0.0) }
        out.flip()
    }

    private fun put(out: ByteBuffer, d: Declipper, x: Double) {
        val on = on()
        d.active = on
        var y = d.next(x)
        val target = if (on) Smooth.HEADROOM else 1.0
        if (gain != target) gain = if (gain > target) maxOf(target, gain - glide) else minOf(target, gain + glide)
        if (gain != 1.0) y = SoftClip.apply(y * gain)
        if (float) out.putFloat(y.toFloat())
        else {   // when off (gain 1, nothing rebuilt) y is exactly the sample that came in, so this gives it back as it was
            val dth = if (gain != 1.0 && eq.dither) rnd.nextDouble() - rnd.nextDouble() else 0.0
            out.putShort((y * 32768.0 + dth).roundToInt().coerceIn(-32768, 32767).toShort())
        }
    }
}
