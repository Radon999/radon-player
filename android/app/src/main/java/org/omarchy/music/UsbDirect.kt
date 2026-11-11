package org.omarchy.music

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.os.Build
import kotlin.math.log10
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The native half: streams to the DAC's endpoint (usbaudio.c). */
object UsbNative {
    val loaded: Boolean = runCatching { System.loadLibrary("radonusb"); true }.getOrDefault(false)
    @JvmStatic external fun start(fd: Int, ep: Int, maxPacket: Int, pktsPerSec: Int, fbEp: Int, fbMaxPacket: Int, fbScale: Int,
                                  frameBytes: Int, rate: Int, bufferMs: Int): Long
    @JvmStatic external fun write(h: Long, buf: java.nio.ByteBuffer, off: Int, len: Int): Int
    @JvmStatic external fun played(h: Long): Long
    @JvmStatic external fun pending(h: Long): Long
    @JvmStatic external fun error(h: Long): Int
    @JvmStatic external fun setPaused(h: Long, paused: Boolean)
    @JvmStatic external fun setMuted(h: Long, muted: Boolean)
    @JvmStatic external fun flush(h: Long)
    @JvmStatic external fun stop(h: Long)
    @JvmStatic external fun reattach(fd: Int, ifno: Int): Int
    @JvmStatic external fun stats(h: Long): LongArray?
    @JvmStatic external fun kick(h: Long)
    @JvmStatic external fun unsent(h: Long): ByteArray?
}

/** Bit-perfect straight to a USB DAC, for phones whose Android mixes every sound first (Samsung's and many others':
 *  no bit-perfect mode in AudioMixerAttributes). The app takes the DAC's streaming interface over from Android, the
 *  way USB Audio Player Pro does, sets it to each song's own sample rate and bit depth, and streams the song's samples
 *  to it untouched (16-bit and 24-bit music exactly as in the file, padded into the DAC's sample size). While it does,
 *  Android can't play anything else through the DAC (other apps, notifications), and the phone's volume buttons set the
 *  DAC's own volume (its hardware volume control), so the samples stay untouched. Phones with Android's own bit-perfect
 *  mode (Pixels) keep using that. */
object UsbDirect {
    private const val ACTION_PERMISSION = "org.omarchy.music.USB_PERMISSION"
    private lateinit var app: Context
    private val usb get() = app.getSystemService(Context.USB_SERVICE) as UsbManager
    private val am get() = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** One way the DAC takes music: an alternate setting of a streaming interface. */
    data class Alt(val iface: Int, val alt: Int, val ep: Int, val maxPacket: Int, val interval: Int, val fbEp: Int, val fbMax: Int,
                   val channels: Int, val subslot: Int, val bits: Int, val rates: List<Int>, val terminal: Int, val adaptive: Boolean)

    private class Dac(val dev: UsbDevice, val conn: UsbDeviceConnection, val uac2: Boolean, val ac: Int, val alts: List<Alt>,
                      val clocks: Map<Int, Int>, val fus: List<Triple<Int, Int, Int>>, val hs: Boolean) {
        val claimed = HashSet<Int>()   // the DAC's audio interfaces the app holds
        var rateNow = 0
        var altNow: Alt? = null
        var clockRanges: List<IntRange>? = null
        var fu: Pair<Int, List<Int>>? = null     // (unit, channels) with a volume control
        var volRange: IntRange? = null            // 1/256 dB
        var volSet = false                        // its own volume set to full (once)
    }

    private var dac: Dac? = null
    private var stream = 0L
    @Volatile var active = false; private set     // the DAC is ours, streaming
    @Volatile var note = ""; private set
    private var asked = false
    private var nativeFor = ""; private var nativeHas = false
    private var hwVolume = false

    fun init(ctx: Context) {
        app = ctx.applicationContext
        Voice.dac = { s, r, c, go -> speak(s, r, c, go) }   // the DJ heard through the DAC while the app has it
        Voice.dacOn = { (active && stream != 0L) || (Music.bitPerfect && wanted()) }
        Voice.dacReport = { lastSpeak }
        val f = IntentFilter().apply {
            addAction(ACTION_PERMISSION); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED); addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction("android.media.VOLUME_CHANGED_ACTION")
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        @Suppress("DEPRECATION") val gone = i.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        val audio = gone == null || (0 until gone.interfaceCount).any { gone.getInterface(it).interfaceClass == UsbConstants.USB_CLASS_AUDIO }
                        if (audio) disconnected(gone?.productName ?: "The USB DAC", active)
                        if (gone == null || gone.deviceName == dac?.dev?.deviceName) {
                            // Unplugged while the app had it: Android didn't see it go (it thought it was gone already, when
                            // the app took it), so it never said "becoming noisy"; the music is paused here instead
                            if (active) Music.localPlayer?.pause()
                            Music.bg({ closeAll("unplugged") })
                        }
                        asked = false
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> lastAttach = System.currentTimeMillis()
                    ACTION_PERMISSION -> if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) Music.replayForBitPerfect()
                        else { note = "Radon Player wasn't allowed to use the DAC, so bit-perfect is Android's mix"; Music.dacNote = note }
                    else -> if (active) Music.bg({ synchronized(this@UsbDirect) { applyVolume() } })
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_EXPORTED) else app.registerReceiver(r, f)
    }

    /** The DAC dropping off the phone's USB on its own (not unplugged by you) looks the same to the app as unplugging
     *  it: the music pauses. When it happens again within two minutes, Problems says so, so a loose cable or a DAC
     *  that can't get enough power from the phone can be told from the app. */
    private val drops = ArrayList<Long>()
    @Volatile private var lastAttach = 0L
    private fun disconnected(name: String, streaming: Boolean) {
        val now = System.currentTimeMillis()
        drops.removeAll { now - it > 120_000 }
        drops += now
        if (drops.size >= 2) Crashes.note("USB DAC: $name disconnected from the phone ${drops.size} times in two minutes" +
            (if (streaming) " (while playing)" else "") + (if (lastAttach > 0) ", last connected ${(now - lastAttach) / 1000} s before" else "") +
            ". The phone's USB lost it (the cable, the connector, or the DAC wanting more power than the phone gives, as in Desktop mode or at high gain): the app paused the music each time")
    }

    /** The USB DAC plugged in (a device with an audio streaming interface), or null. */
    fun device(): UsbDevice? = runCatching {
        usb.deviceList.values.firstOrNull { d -> (0 until d.interfaceCount).any { d.getInterface(it).let { i -> i.interfaceClass == UsbConstants.USB_CLASS_AUDIO && i.interfaceSubclass == 2 } } }
    }.getOrNull()

    /** Android's own bit-perfect mode for this DAC (Android 14+, the phone's audio system has it): then that's used. */
    fun androidHasBitPerfect(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        val out = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET || it.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE
        } ?: return false
        return runCatching { am.getSupportedMixerAttributes(out).any { it.mixerBehavior == android.media.AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT } }.getOrDefault(false)
    }

    /** Whether songs should go straight to the DAC: bit-perfect is on, a DAC is plugged in, and Android has no bit-perfect
     *  mode of its own for it. Asks for permission to use the DAC the first time. */
    fun wanted(): Boolean {
        if (!UsbNative.loaded || !Music.bitPerfect) return false
        if (dac != null) return true
        if (failures >= 2) return false
        val d = device() ?: return false
        if (nativeFor != d.deviceName) { nativeFor = d.deviceName; nativeHas = androidHasBitPerfect() }
        if (nativeHas) return false
        if (!usb.hasPermission(d)) {
            if (!asked) {
                asked = true
                val pi = PendingIntent.getBroadcast(app, 0, Intent(ACTION_PERMISSION).setPackage(app.packageName),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                runCatching { usb.requestPermission(d, pi) }
                note = "Allow Radon Player to use ${d.productName ?: "the DAC"} (tap OK), then bit-perfect goes straight to it"
            }
            return false
        }
        return true
    }

    // ---- the DAC's descriptors ----
    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
    private fun u16(b: ByteArray, i: Int) = u8(b, i) or (u8(b, i + 1) shl 8)
    private fun u24(b: ByteArray, i: Int) = u16(b, i) or (u8(b, i + 2) shl 16)
    private fun u32(b: ByteArray, i: Int) = u24(b, i) or (u8(b, i + 3) shl 24)

    private fun open(): Dac? {
        dac?.let { return it }
        val d = device() ?: return null
        if (!usb.hasPermission(d)) return null
        val conn = usb.openDevice(d) ?: return null
        val raw = conn.rawDescriptors ?: run { conn.close(); return null }
        var i = 0
        var ifNum = -1; var alt = 0; var cls = 0; var sub = 0; var proto = 0
        var ac = -1; var uac2 = false
        val clocks = HashMap<Int, Int>()           // terminal id -> clock source (UAC2)
        val clockIds = HashMap<Int, Int>()         // selector / multiplier -> its first source
        val fus = ArrayList<Triple<Int, Int, Int>>()   // (unit, source, channel mask with volume: bit 0 master, 1.. channels)
        val alts = ArrayList<Alt>()
        var link = 0; var channels = 2; var subslot = 0; var bits = 0; var rates = emptyList<Int>(); var pcm = true
        var eps = ArrayList<IntArray>()            // address, attributes, maxPacket, interval, synchAddress
        fun closeAlt() {
            if (cls == 1 && sub == 2 && alt > 0 && pcm && subslot > 0) {
                val out = eps.firstOrNull { (it[0] and 0x80) == 0 && (it[1] and 3) == 1 }
                if (out != null) {
                    val fb = eps.firstOrNull { (it[0] and 0x80) != 0 && (it[1] and 3) == 1 && ((it[1] shr 4) and 3) == 1 }
                        ?: out[4].takeIf { it != 0 }?.let { a -> eps.firstOrNull { it[0] == a } }
                    val mp = out[2] and 0x7FF; val mult = ((out[2] shr 11) and 3) + 1
                    alts += Alt(ifNum, alt, out[0], mp * mult, out[3], fb?.get(0) ?: 0, fb?.get(2)?.and(0x7FF) ?: 0, channels, subslot, bits,
                        rates, link, ((out[1] shr 2) and 3) == 2)
                }
            }
            eps = ArrayList(); subslot = 0; bits = 0; rates = emptyList(); pcm = true
        }
        while (i + 2 <= raw.size) {
            val len = u8(raw, i); val type = u8(raw, i + 1)
            if (len < 2 || i + len > raw.size) break
            when (type) {
                4 -> { closeAlt(); ifNum = u8(raw, i + 2); alt = u8(raw, i + 3); cls = u8(raw, i + 5); sub = u8(raw, i + 6); proto = u8(raw, i + 7)
                    if (cls == 1 && sub == 1) { ac = ifNum; if (proto == 0x20) uac2 = true } }
                5 -> { eps.add(intArrayOf(u8(raw, i + 2), u8(raw, i + 3), u16(raw, i + 4), u8(raw, i + 6), if (len >= 9) u8(raw, i + 8) else 0)) }
                0x24 -> if (cls == 1 && sub == 1) {
                    val st = u8(raw, i + 2)
                    if (st == 0x01) { if (len >= 5 && u16(raw, i + 3) >= 0x0200) uac2 = true }
                    else if (st == 0x02) { if (uac2 && len >= 8) clocks[u8(raw, i + 3)] = u8(raw, i + 7) }
                    else if (st == 0x0B) { if (len >= 6) clockIds[u8(raw, i + 3)] = u8(raw, i + 5) }
                    else if (st == 0x0C) { if (len >= 5) clockIds[u8(raw, i + 3)] = u8(raw, i + 4) }
                    else if (st == 0x06) {
                        val id = u8(raw, i + 3); val src = u8(raw, i + 4); var mask = 0
                        if (uac2) { var ch = 0; var o = i + 5; while (o + 4 <= i + len - 1) { if (((u32(raw, o) shr 2) and 3) == 3) mask = mask or (1 shl ch); ch++; o += 4 } }
                        else if (len >= 7) { val cs = u8(raw, i + 5); var ch = 0; var o = i + 6
                            while (cs > 0 && o + cs <= i + len - 1) { if ((u8(raw, o) and 2) != 0) mask = mask or (1 shl ch); ch++; o += cs } }
                        if (mask != 0) fus.add(Triple(id, src, mask))
                    }
                } else if (cls == 1 && sub == 2) {
                    val st = u8(raw, i + 2)
                    if (st == 0x01) {
                        if (uac2 && len >= 11) { link = u8(raw, i + 3); pcm = (u32(raw, i + 6) and 1) != 0 && u8(raw, i + 5) == 1; channels = u8(raw, i + 10) }
                        else if (len >= 7) { link = u8(raw, i + 3); pcm = u16(raw, i + 5) == 1 }
                    } else if (st == 0x02) {
                        if (u8(raw, i + 3) != 1) { pcm = false }
                        else if (uac2 && len >= 6) { subslot = u8(raw, i + 4); bits = u8(raw, i + 5) }
                        else if (len >= 8) {
                            channels = u8(raw, i + 4); subslot = u8(raw, i + 5); bits = u8(raw, i + 6)
                            val n = u8(raw, i + 7)
                            rates = if (n == 0 && len >= 14) listOf(u24(raw, i + 8), u24(raw, i + 11))   // continuous: min, max
                                else (0 until n).mapNotNull { k -> if (i + 8 + k * 3 + 3 <= raw.size) u24(raw, i + 8 + k * 3) else null }
                        }
                    }
                }
            }
            i += len
        }
        closeAlt()
        if (ac < 0 || alts.isEmpty()) { conn.close(); note = "${d.productName} doesn't describe a format Radon Player can stream to"; return null }
        // A terminal's clock: through selectors and multipliers to the clock source
        val resolved = clocks.mapValues { (_, c) -> var x = c; repeat(4) { clockIds[x]?.let { x = it } }; x }
        val hs = uac2 || alts.any { it.interval > 1 }
        val made = Dac(d, conn, uac2, ac, alts, resolved, fus, hs)
        made.fu = fus.firstOrNull { f -> alts.any { it.terminal == f.second } }?.let { it.first to channelsOf(it.third) }
            ?: fus.firstOrNull()?.let { it.first to channelsOf(it.third) }
        dac = made
        return made
    }

    private fun channelsOf(mask: Int) = if (mask and 1 != 0) listOf(0) else (1..8).filter { mask and (1 shl it) != 0 }.take(2)

    /** UAC2: the sample rates the DAC's clock offers (GET RANGE). */
    private fun clockRanges(dc: Dac, clock: Int): List<IntRange> {
        dc.clockRanges?.let { return it }
        val buf = ByteArray(2 + 12 * 32)
        val n = dc.conn.controlTransfer(0xA1, 0x02, 0x0100, (clock shl 8) or dc.ac, buf, buf.size, 1000)
        val out = ArrayList<IntRange>()
        if (n >= 14) { val count = u16(buf, 0); for (k in 0 until count) { val o = 2 + k * 12; if (o + 12 > n) break; out += u32(buf, o)..u32(buf, o + 4) } }
        dc.clockRanges = out
        return out
    }

    private fun supports(dc: Dac, a: Alt, rate: Int): Boolean =
        if (dc.uac2) dc.clocks[a.terminal]?.let { c -> clockRanges(dc, c).let { r -> r.isEmpty() || r.any { rate in it } } } ?: true
        else a.rates.isEmpty() || rate in a.rates || (a.rates.size == 2 && a.rates[0] < a.rates[1] && rate in a.rates[0]..a.rates[1])

    /** What a stream is set up as. */
    data class Format(val rate: Int, val channels: Int, val subslot: Int, val bits: Int)

    /** (playback thread) Sets the DAC up for a song: its rate, the setting with enough bits, and starts streaming.
     *  The format, or null (then Android's mix plays it, with the reason in [note]). */
    fun begin(rate: Int, channels: Int, srcBits: Int): Format? { waitSpeech(); return begin0(rate, channels, srcBits) }

    /** (the player's thread) While the DJ's voice is going to the DAC, the player leaves the stream alone: the next
     *  song set up meanwhile (a new set, maybe at another sample rate) replaced the stream, or flushed it, and the voice
     *  was never heard. At most 40 seconds. */
    fun waitSpeech() {
        var n = 0
        while (speaking && n < 2000) { Thread.sleep(20); n++ }
    }

    private fun begin0(rate: Int, channels: Int, srcBits: Int): Format? = synchronized(this) { runCatching {
        val dc = open() ?: return null
        val fit = dc.alts.filter { it.channels == channels && supports(dc, it, rate) }
        if (fit.isEmpty()) { note = "Not bit-perfect for this song: ${dc.dev.productName} doesn't take ${"%g".format(rate / 1000.0)} kHz${if (channels != 2) ", $channels channels" else ""}"; endStream(); return null }
        // The deepest setting: 16-bit music is just as untouched in it, and the app's volume (below full) keeps every bit
        val a = fit.maxBy { it.bits * 10 + it.subslot }
        if (dc.altNow == a && dc.rateNow == rate && stream != 0L && UsbNative.error(stream) == 0) return Format(rate, channels, a.subslot, a.bits)
        endStream()
        val c = dc.conn
        if (dc.claimed.isEmpty()) {
            // Just given back to Android (bit-perfect off, then on again): its USB audio driver takes a few seconds to
            // settle on the DAC; taken back mid-way, the DAC was left set Android's way and the music came out as static.
            val wait = releasedAt + 3500 - System.currentTimeMillis()
            if (wait > 0) Thread.sleep(wait)
            // The whole DAC taken from Android, its control interface too (as USB Audio Player Pro does): with only the
            // streaming interface taken, Android's own USB audio driver kept the rest and could go on setting the DAC
            // (its rate, its format) under the app, which played the music as loud static.
            for (intf in (0 until dc.dev.interfaceCount).map { dc.dev.getInterface(it) }
                .filter { it.interfaceClass == UsbConstants.USB_CLASS_AUDIO && it.alternateSetting == 0 })
                if (c.claimInterface(intf, true)) dc.claimed += intf.id
            tookOverAt = System.currentTimeMillis()
            // Every streaming interface stopped (alternate setting 0), whatever Android had it doing
            for (id in dc.claimed) intfOf(dc, id, 0)?.takeIf { it.interfaceSubclass == 2 }?.let { c.setInterface(it) }
            Thread.sleep(150)
        }
        if (a.iface !in dc.claimed) { note = "Android wouldn't let go of ${dc.dev.productName}"; return null }
        intfOf(dc, a.iface, 0)?.let { c.setInterface(it) }
        var setRes = 0; var readBack = 0
        if (dc.uac2) dc.clocks[a.terminal]?.let { clock ->
            val b = byteArrayOf(rate.toByte(), (rate shr 8).toByte(), (rate shr 16).toByte(), (rate shr 24).toByte())
            setRes = c.controlTransfer(0x21, 0x01, 0x0100, (clock shl 8) or dc.ac, b, 4, 1000)
            // Read back: a DAC still at another rate would get the music too fast or too slow (static, crackling)
            for (k in 0 until 4) {
                val rb = ByteArray(4)
                if (c.controlTransfer(0xA1, 0x01, 0x0100, (clock shl 8) or dc.ac, rb, 4, 1000) == 4) readBack = u32(rb, 0)
                if (readBack == rate || readBack == 0) break
                Thread.sleep(60)
            }
        }
        lastSetup = "UAC${if (dc.uac2) 2 else 1}, ${if (dc.hs) "high" else "full"} speed; asked ${rate} Hz (set: $setRes, DAC says: $readBack); " +
            "picked alt ${a.alt} of interface ${a.iface}: ${a.bits}-bit in ${a.subslot} bytes, ep 0x${a.ep.toString(16)} max ${a.maxPacket} interval ${a.interval}" +
            "${if (a.adaptive) " adaptive" else ", feedback 0x${a.fbEp.toString(16)} max ${a.fbMax}"}, terminal ${a.terminal} clock ${dc.clocks[a.terminal]}; " +
            "all: " + dc.alts.joinToString(" | ") { "alt ${it.alt}/if ${it.iface} ${it.channels}ch ${it.bits}b/${it.subslot} ep 0x${it.ep.toString(16)} mp ${it.maxPacket} i ${it.interval}" } +
            "; volume unit ${dc.fu}"
        if (dc.uac2 && readBack > 0 && readBack != rate) {
            note = "${dc.dev.productName} stayed at ${khz(readBack)} kHz when asked for ${khz(rate)}: playing through Android for this song"
            Crashes.note("USB DAC: wouldn't change rate · $lastSetup")
            return null
        }
        val ai = intfOf(dc, a.iface, a.alt) ?: return null
        // Set, and checked (GET_INTERFACE): the DAC must really be in the setting whose sample size the music is packed in
        var altRead = -1
        for (k in 0 until 3) {
            if (!c.setInterface(ai)) { Thread.sleep(80); continue }
            val g = ByteArray(1)
            altRead = if (c.controlTransfer(0x81, 0x0A, 0, a.iface, g, 1, 500) == 1) g[0].toInt() and 0xFF else a.alt   // can't ask: trust it
            if (altRead == a.alt) break
            Thread.sleep(80)
        }
        if (altRead != a.alt) {
            note = "${dc.dev.productName} didn't take the format: playing through Android for this song"
            Crashes.note("USB DAC: didn't take alt ${a.alt} (it says $altRead) · ${dc.alts.size} settings")
            return null
        }
        if (!dc.uac2) {
            val b = byteArrayOf(rate.toByte(), (rate shr 8).toByte(), (rate shr 16).toByte())
            c.controlTransfer(0x22, 0x01, 0x0100, a.ep, b, 3, 1000)
        }
        val pktsPerSec = if (dc.hs) 8000 / (1 shl (a.interval.coerceIn(1, 4) - 1)) else 1000
        val fbScale = if (dc.hs) 1 shl (a.interval.coerceIn(1, 4) - 1) else 1
        val frameBytes = a.subslot * channels
        if (rate.toLong() * frameBytes / pktsPerSec + frameBytes > a.maxPacket) { note = "${dc.dev.productName}'s packets are too small for ${"%g".format(rate / 1000.0)} kHz"; return null }
        applyVolume()
        stream = UsbNative.start(c.fileDescriptor, a.ep, a.maxPacket, pktsPerSec, if (a.adaptive) 0 else a.fbEp, a.fbMax, fbScale, frameBytes, rate, 400)
        if (stream == 0L) { note = "Couldn't start streaming to ${dc.dev.productName}"; return null }
        report(stream, "$lastSetup; $pktsPerSec packets/s, $frameBytes bytes a frame")
        watch(stream)
        dc.altNow = a; dc.rateNow = rate
        active = true; Music.usbDirect = true
        val exact = srcBits <= a.bits
        note = (if (exact) "Bit-perfect, straight to ${dc.dev.productName} (around Android): " else "Straight to ${dc.dev.productName}, but it takes ${a.bits}-bit at most: ") +
            "${if (srcBits > 0) "$srcBits-bit/" else ""}${khz(rate)} kHz" +
            " · the phone's volume is the app's (bit-perfect at full volume)"
        Format(rate, channels, a.subslot, a.bits)
    }.getOrElse { e -> note = "Streaming to the DAC failed: ${e.message?.take(80)}"; Crashes.caught(e, "USB DAC: couldn't start streaming"); null } }

    private fun khz(rate: Int) = if (rate % 1000 == 0) "${rate / 1000}" else "%.1f".format(java.util.Locale.ROOT, rate / 1000.0)

    @Volatile var lastSetup = ""; private set

    /** Songs that failed while streaming straight to the DAC this time it's plugged in; after two, bit-perfect plays
     *  through Android (as far as it can) until the DAC is plugged in again or bit-perfect is turned off and on. */
    @Volatile var failures = 0
    fun failed(why: String) {
        failures++
        closeAll()
        if (failures >= 2) note = "Bit-perfect straight to the DAC stopped after two songs failed ($why). Unplug the DAC and plug it in, " +
            "or turn bit-perfect off and on, to try again; Settings › Problems has the details to send"
    }

    /** A few seconds into a stream: how it's going, into Problems (Copy sends it), for the first streams of a session:
     *  the DAC's settings as read, what was picked, the rate it took, its feedback and any packets that went wrong. */
    private fun report(h: Long, setup: String) {
        Music.scope.launch {
            kotlinx.coroutines.delay(2000)
            val early = if (stream == h) runCatching { UsbNative.stats(h) }.getOrNull() else null
            val earlyPlayed = if (stream == h) runCatching { UsbNative.played(h) }.getOrDefault(-1L) else -1L
            val earlyAt = Music.localPlayer?.positionMs ?: -1L
            val earlyPlaying = Music.localPlayer?.playWhenReady == true
            kotlinx.coroutines.delay(3000)
            // Only when something's wrong: the music not getting through while it plays (it used to be noted every time)
            if (stream != h) return@launch
            val v = runCatching { UsbNative.stats(h) }.getOrNull() ?: return@launch
            val pl = Music.localPlayer
            val played = runCatching { UsbNative.played(h) }.getOrDefault(-1L)
            val rate = dac?.rateNow ?: 0
            // Played the whole time (not paused for the DJ, say) and the song hardly moved on: then it's worth noting
            if (pl?.playWhenReady != true || !earlyPlaying || rate <= 0 || earlyPlayed < 0 || played - earlyPlayed >= rate * 2 ||
                pl.positionMs - earlyAt >= 2000) return@launch
            Crashes.note("USB DAC report (${dac?.dev?.productName}): music not getting through · $setup · $volumeInfo · at 2 s: ${early?.get(0)} transfers, $earlyPlayed frames played, " +
                "player at ${earlyAt}ms · at 5 s: $played frames played, ${runCatching { UsbNative.pending(h) }.getOrDefault(-1L)} waiting, " +
                "player at ${pl.positionMs}ms (state ${pl.state}) · ${v[0]} transfers, ${v[1]} failed, ${v[2]} with bad packets, " +
                "last transfer back ${v.getOrNull(9)} ms ago, sent again ${v.getOrNull(12)} times; " +
                "feedback ${v[3]} seen, ${v[4]} used, last 0x${v[5].toString(16)} (${v[6]} bytes); frames a packet ${"%.4f".format(java.util.Locale.ROOT, v[7] / 65536.0)} " +
                "(nominal ${"%.4f".format(java.util.Locale.ROOT, v[8] / 65536.0)})")
        }
    }

    /** Watches the stream while it plays: if the DAC stops taking music (no data back for 0.4 s), the queued transfers
     *  are sent again, and if that doesn't help the stream is set up from scratch (the player's own thread does it: see
     *  restartWanted). Quiet: it works on its own; only a stream that keeps stopping is noted (UsbAudioSink). Paused,
     *  it's left alone: the phone may sleep then, and the stream with it, which is no problem. */
    private val watching: MutableSet<Long> = java.util.Collections.synchronizedSet(HashSet())
    private fun watch(h: Long) {
        if (!watching.add(h)) return   // one watch for each stream
        Music.scope.launch {
            var kicks = 0
            var vol = -1
            var playingSince = 0L
            try {
                while (stream == h) {
                    kotlinx.coroutines.delay(100)
                    if (stream != h) break
                    // The phone's volume, followed here too (in case Android's "volume changed" never comes)
                    val now = runCatching { am.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(vol)
                    if (now != vol) { if (vol != -1) Music.bg({ synchronized(this@UsbDirect) { applyVolume() } }); vol = now }
                    // Only while it plays, and not just after it started again (the stream wakes up with it)
                    if (Music.localPlayer?.playWhenReady != true) { playingSince = 0L; kicks = 0; continue }
                    if (playingSince == 0L) playingSince = System.currentTimeMillis()
                    if (System.currentTimeMillis() - playingSince < 500) continue
                    val v = runCatching { UsbNative.stats(h) }.getOrNull() ?: break
                    // Nothing back from the DAC for 0.15 s (64 ms of music is queued): set up again at once, the shortest gap
                    // (sending the transfers again only got it going for a moment)
                    if (v.size < 14 || v[9] < 150) { if (v.size >= 14 && v[9] < 50) kicks = 0; continue }
                    if (++kicks > 40) break
                    restartWanted = h
                }
            } finally { watching.remove(h) }
        }
    }

    @Volatile private var tookOverAt = 0L
    @Volatile private var releasedAt = 0L
    /** The DAC was just taken from Android (it then thinks it was unplugged for a moment). */
    fun tookOverLately() = System.currentTimeMillis() - tookOverAt < 5000
    /** Ms since the DAC was taken from Android (a long time: never). */
    fun sinceTakeover() = System.currentTimeMillis() - tookOverAt
    /** The DAC is the app's, or was just taken or handed back: Android's "the headphones were unplugged" is about that. */
    fun ownsOrJustMoved() = active || dac != null || System.currentTimeMillis() - tookOverAt < 8000 || System.currentTimeMillis() - releasedAt < 8000

    private fun intfOf(dc: Dac, id: Int, alt: Int): UsbInterface? =
        (0 until dc.dev.interfaceCount).map { dc.dev.getInterface(it) }.firstOrNull { it.id == id && it.alternateSetting == alt }

    val handle get() = stream

    /** The watch's ask for the stream to be set up from scratch (its handle), done by the player's own thread. */
    @Volatile var restartWanted = 0L
    /** The music a stream set up again still had to send: the new one sends it first (see UsbAudioSink). */
    @Volatile var carry: ByteArray? = null
    /** (playback thread) The stream stopped and its setting forgotten, so the next begin sets the DAC up afresh. */
    fun restartStream() {
        restartWanted = 0L
        carry = if (stream != 0L) runCatching { UsbNative.unsent(stream) }.getOrNull() else null
        endStream()
        dac?.altNow = null; dac?.rateNow = 0
    }

    /** Stops the stream (the DAC keeps its setting; the next song starts it again). */
    fun endStream() {
        // Taken out first, then stopped: a stop takes a moment, and a new stream started meanwhile (another thread)
        // was then forgotten and left running beside the next one
        val h = synchronized(streamLock) { stream.also { stream = 0L } }
        if (h != 0L) runCatching { UsbNative.stop(h) }
    }
    private val streamLock = Any()

    /** Whether the player has the DAC's stream playing (see UsbAudioSink): the DJ's voice leaves it as it found it. */
    @Volatile var sinkPlaying = false
    /** The player has its volume at nothing (the DJ talking, a fade's start): the stream mutes the music then. */
    @Volatile var sinkMuted = false
    /** The DJ's voice is going to the DAC: the music player's samples wait (they'd be heard under it). */
    @Volatile var speaking = false
    /** How the DJ's last line went to the DAC, in words (for Problems). */
    @Volatile var lastSpeak = ""

    /** Something to be heard while the app has the DAC (the DJ's voice, with the music paused under it): Android can't
     *  play anything through the DAC then, so the voice went nowhere and the DJ never finished. samples are floats
     *  (-1..1, channels interleaved) at rate; they go to the DAC at its rate and sample size, at the phone's volume.
     *  Blocking; false when the DAC isn't the app's (then Android plays it as before). Stops early once go is false. */
    fun speak(samples: FloatArray, rate: Int, channels: Int, go: () -> Boolean): Boolean {
        // No stream yet (the DJ before the first song, or between songs): one set up for the voice, at the rate the
        // DAC was last at (else 48 kHz), which the next song takes over (or sets up again at its own)
        if (!(active && stream != 0L)) {
            if (!wanted()) return false
            if (begin0(dac?.rateNow?.takeIf { it > 0 } ?: 48000, 2, 24) == null) { lastSpeak = "couldn't set the DAC up: $note"; return false }
        }
        val dc = dac ?: run { lastSpeak = "no DAC"; return false }
        val a = dc.altNow ?: run { lastSpeak = "the DAC isn't set up"; return false }
        val h = stream.takeIf { it != 0L && active } ?: run { lastSpeak = "no stream"; return false }
        val outRate = dc.rateNow.takeIf { it > 0 } ?: run { lastSpeak = "no rate"; return false }
        val slot = a.subslot; val ch = maxOf(1, channels)
        val frames = samples.size / ch
        if (frames == 0) return true
        speaking = true
        UsbNative.flush(h)   // whatever of the song was waiting (it starts again from the top after the DJ)
        UsbNative.setPaused(h, false); UsbNative.setMuted(h, false)
        try {
            val step = rate.toDouble() / outRate
            val outFrames = (frames / step).toInt()
            val chunk = 1024
            val buf = java.nio.ByteBuffer.allocateDirect(chunk * 2 * slot).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            var o = 0
            while (o < outFrames && go()) {
                buf.clear()
                val n = minOf(chunk, outFrames - o)
                val g = digitalGain.toDouble()
                for (k in 0 until n) {
                    val pos = (o + k) * step
                    val i = pos.toInt().coerceAtMost(frames - 1); val f = pos - i
                    val j = (i + 1).coerceAtMost(frames - 1)
                    for (c in 0 until 2) {   // mono to both sides; stereo as it is
                        val sc = if (ch == 1) 0 else minOf(c, ch - 1)
                        val x = (samples[i * ch + sc] * (1 - f) + samples[j * ch + sc] * f) * g
                        val v = (x.coerceIn(-1.0, 1.0) * 2147483647.0).toLong().toInt()
                        when (slot) {
                            2 -> buf.putShort((v shr 16).toShort())
                            3 -> { val w = v shr 8; buf.put(w.toByte()); buf.put((w shr 8).toByte()); buf.put((w shr 16).toByte()) }
                            else -> buf.putInt(v)
                        }
                    }
                }
                buf.flip()
                var off = 0
                val len = buf.limit()
                while (off < len && go()) {
                    val w = UsbNative.write(h, buf, off, len - off)
                    if (w < 0) { lastSpeak = "the stream went away ${o * 1000L / outRate} ms in"; return true }   // the stream's gone
                    off += w
                    // (the music pausing for the DJ, and its volume going to nothing, can come in after the voice began:
                    // the stream was muted with it, and the voice went out as silence)
                    UsbNative.setPaused(h, false); UsbNative.setMuted(h, false)
                    if (off < len) Thread.sleep(10)
                }
                o += n
            }
            // What's still queued, heard to its end
            var waited = 0
            while (go() && UsbNative.pending(h) > 0 && waited < 3000) { UsbNative.setPaused(h, false); UsbNative.setMuted(h, false); Thread.sleep(20); waited += 20 }
            lastSpeak = "${outFrames * 1000L / outRate} ms of voice sent (${khz(outRate)} kHz, from ${rate} Hz ${ch} ch), " +
                "${UsbNative.played(h)} frames played, ${UsbNative.pending(h)} left${if (!go()) ", hushed" else ""}"
        } finally {
            speaking = false
            if (!go()) UsbNative.flush(h)
            UsbNative.setPaused(h, !sinkPlaying)
            UsbNative.setMuted(h, sinkMuted)
        }
        return true
    }

    /** Lets the DAC go: back to Android (bit-perfect turned off, the DAC unplugged, the app closing). */
    fun closeAll(why: String = "") = synchronized(this) {
        endStream()
        val dc = dac ?: return@synchronized
        runCatching {
            for (id in dc.claimed) {
                intfOf(dc, id, 0)?.let { dc.conn.setInterface(it); dc.conn.releaseInterface(it) }
            }
            for (id in dc.claimed) UsbNative.reattach(dc.conn.fileDescriptor, id)
            if (dc.claimed.isNotEmpty()) releasedAt = System.currentTimeMillis()
            dc.claimed.clear()
            dc.conn.close()
        }
        dac = null; active = false; Music.usbDirect = false
        if (why == "unplugged") { note = ""; failures = 0 }
    }

    // ---- volume ----
    /** The phone's media volume onto the DAC's own volume control (its feature unit), so the samples stay untouched;
     *  without one, [digitalGain] turns the music down in the app instead. */
    fun applyVolume() {
        val dc = dac ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val idx = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val frac = idx.toDouble() / max
        // As Android's own curve for USB: each step the same number of dB, -60 dB at the lowest (half the steps ≈ -30 dB).
        // The old curve (half ≈ -15 dB) played a lot louder than the same setting does through Android.
        val db = if (frac <= 0.0) -200.0 else -60.0 * (1 - frac)
        val fu = dc.fu
        if (fu == null) { hwVolume = false; digitalGain = if (frac >= 1.0) 1f else if (frac <= 0) 0f else Math.pow(10.0, db / 20).toFloat()
            volumeInfo = "volume ${idx}/$max: no volume unit, the app's"; return }
        val range = dc.volRange ?: run {
            val b = ByteArray(14)
            val ch = fu.second.first()
            val r = if (dc.uac2) {
                val n = dc.conn.controlTransfer(0xA1, 0x02, (0x02 shl 8) or ch, (fu.first shl 8) or dc.ac, b, b.size, 1000)
                if (n >= 8) b.short(2)..b.short(4) else -96 * 256..0
            } else {
                val lo = ByteArray(2); val hi = ByteArray(2)
                dc.conn.controlTransfer(0xA1, 0x82, (0x02 shl 8) or ch, (fu.first shl 8) or dc.ac, lo, 2, 1000)
                dc.conn.controlTransfer(0xA1, 0x83, (0x02 shl 8) or ch, (fu.first shl 8) or dc.ac, hi, 2, 1000)
                lo.short(0)..hi.short(0)
            }
            (if (r.first < r.last) r else -96 * 256..0).also { dc.volRange = it }
        }
        // The DAC's own volume is left at full (0 dB) and the phone's volume is the app's, on the samples, as Android
        // itself does for USB DACs: the FiiO KA17 took its volume setting and read it back but didn't change how loud it
        // was, so the volume buttons did nothing. Bit-perfect at full volume (the samples untouched there).
        val v = 0.coerceIn(range.first, range.last)
        val b = byteArrayOf(v.toByte(), (v shr 8).toByte())
        var setOk = 0
        if (!dc.volSet) {
            for (ch in fu.second) if (dc.conn.controlTransfer(0x21, 0x01, (0x02 shl 8) or ch, (fu.first shl 8) or dc.ac, b, 2, 1000) >= 0) setOk++
            dc.conn.controlTransfer(0x21, 0x01, (0x01 shl 8) or fu.second.first(), (fu.first shl 8) or dc.ac, byteArrayOf(0), 1, 1000)   // not muted
            dc.volSet = true
        }
        hwVolume = false
        digitalGain = if (frac >= 1.0) 1f else if (frac <= 0) 0f else Math.pow(10.0, db / 20).toFloat()
        volumeInfo = "volume ${idx}/$max → ${"%.1f".format(java.util.Locale.ROOT, db)} dB in the app (DAC unit ${fu.first} ch ${fu.second} range $range held at ${v / 256} dB${if (setOk > 0) ", set" else ""})"
    }

    @Volatile var volumeInfo = ""; private set

    private fun ByteArray.short(i: Int) = ((this[i].toInt() and 0xFF) or (this[i + 1].toInt() shl 8)).toShort().toInt()

    /** The app's own volume on the samples: 1 (untouched) unless the DAC has no volume control and the phone isn't at full. */
    @Volatile var digitalGain = 1f; private set
}
