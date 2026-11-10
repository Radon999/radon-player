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
        var claimed = -1
        var rateNow = 0
        var altNow: Alt? = null
        var clockRanges: List<IntRange>? = null
        var fu: Pair<Int, List<Int>>? = null     // (unit, channels) with a volume control
        var volRange: IntRange? = null            // 1/256 dB
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
        val f = IntentFilter().apply {
            addAction(ACTION_PERMISSION); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction("android.media.VOLUME_CHANGED_ACTION")
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        @Suppress("DEPRECATION") val gone = i.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        if (gone == null || gone.deviceName == dac?.dev?.deviceName) Music.bg({ closeAll("unplugged") })
                        asked = false
                    }
                    ACTION_PERMISSION -> if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) Music.replayForBitPerfect()
                        else { note = "Radon Player wasn't allowed to use the DAC, so bit-perfect is Android's mix"; Music.dacNote = note }
                    else -> if (active) Music.bg({ synchronized(this@UsbDirect) { applyVolume() } })
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_EXPORTED) else app.registerReceiver(r, f)
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
    fun begin(rate: Int, channels: Int, srcBits: Int): Format? = runCatching {
        val dc = open() ?: return null
        val fit = dc.alts.filter { it.channels == channels && supports(dc, it, rate) }
        if (fit.isEmpty()) { note = "Not bit-perfect for this song: ${dc.dev.productName} doesn't take ${"%g".format(rate / 1000.0)} kHz${if (channels != 2) ", $channels channels" else ""}"; endStream(); return null }
        val a = fit.filter { it.bits >= srcBits }.minByOrNull { it.bits * 10 + it.subslot } ?: fit.maxBy { it.bits }
        if (dc.altNow == a && dc.rateNow == rate && stream != 0L && UsbNative.error(stream) == 0) return Format(rate, channels, a.subslot, a.bits)
        endStream()
        val c = dc.conn
        if (dc.claimed != a.iface) {
            val intf = intfOf(dc, a.iface, 0) ?: return null
            if (!c.claimInterface(intf, true)) { note = "Android wouldn't let go of ${dc.dev.productName}"; return null }
            dc.claimed = a.iface
        }
        intfOf(dc, a.iface, 0)?.let { c.setInterface(it) }
        if (dc.uac2) dc.clocks[a.terminal]?.let { clock ->
            val b = byteArrayOf(rate.toByte(), (rate shr 8).toByte(), (rate shr 16).toByte(), (rate shr 24).toByte())
            c.controlTransfer(0x21, 0x01, 0x0100, (clock shl 8) or dc.ac, b, 4, 1000)
        }
        val ai = intfOf(dc, a.iface, a.alt) ?: return null
        if (!c.setInterface(ai)) { note = "${dc.dev.productName} didn't take the format"; return null }
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
        dc.altNow = a; dc.rateNow = rate
        active = true; Music.usbDirect = true
        val exact = srcBits <= a.bits
        note = (if (exact) "Bit-perfect, straight to ${dc.dev.productName} (around Android): " else "Straight to ${dc.dev.productName}, but it takes ${a.bits}-bit at most: ") +
            "${if (srcBits > 0) "$srcBits-bit/" else ""}${"%g".format(rate / 1000.0)} kHz" +
            if (!hwVolume) " · the DAC has no volume control of its own, so below full volume the app turns it down (bit-perfect at full volume)" else ""
        Format(rate, channels, a.subslot, a.bits)
    }.getOrElse { e -> note = "Streaming to the DAC failed: ${e.message?.take(80)}"; Crashes.caught(e, "USB DAC: couldn't start streaming"); null }

    private fun intfOf(dc: Dac, id: Int, alt: Int): UsbInterface? =
        (0 until dc.dev.interfaceCount).map { dc.dev.getInterface(it) }.firstOrNull { it.id == id && it.alternateSetting == alt }

    val handle get() = stream

    /** Stops the stream (the DAC keeps its setting; the next song starts it again). */
    fun endStream() {
        if (stream != 0L) { runCatching { UsbNative.stop(stream) }; stream = 0L }
    }

    /** Lets the DAC go: back to Android (bit-perfect turned off, the DAC unplugged, the app closing). */
    fun closeAll(why: String = "") = synchronized(this) {
        endStream()
        val dc = dac ?: return@synchronized
        runCatching {
            if (dc.claimed >= 0) {
                intfOf(dc, dc.claimed, 0)?.let { dc.conn.setInterface(it); dc.conn.releaseInterface(it) }
                UsbNative.reattach(dc.conn.fileDescriptor, dc.claimed)
            }
            dc.conn.close()
        }
        dac = null; active = false; Music.usbDirect = false
        if (why == "unplugged") note = ""
    }

    // ---- volume ----
    /** The phone's media volume onto the DAC's own volume control (its feature unit), so the samples stay untouched;
     *  without one, [digitalGain] turns the music down in the app instead. */
    fun applyVolume() {
        val dc = dac ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val idx = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val frac = idx.toDouble() / max
        val db = if (frac <= 0.0) -200.0 else 50.0 * log10(frac)   // half the steps ≈ -15 dB, a tenth ≈ -50 dB
        val fu = dc.fu
        if (fu == null) { hwVolume = false; digitalGain = if (frac >= 1.0) 1f else if (frac <= 0) 0f else Math.pow(10.0, db / 20).toFloat(); return }
        hwVolume = true; digitalGain = 1f
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
        val v = (db * 256).roundToInt().coerceIn(range.first, range.last)
        val b = byteArrayOf(v.toByte(), (v shr 8).toByte())
        for (ch in fu.second) dc.conn.controlTransfer(0x21, 0x01, (0x02 shl 8) or ch, (fu.first shl 8) or dc.ac, b, 2, 1000)
        // Muted at zero (the mute control, where the DAC has one)
        val m = byteArrayOf(if (idx == 0) 1 else 0)
        dc.conn.controlTransfer(0x21, 0x01, (0x01 shl 8) or fu.second.first(), (fu.first shl 8) or dc.ac, m, 1, 1000)
    }

    private fun ByteArray.short(i: Int) = ((this[i].toInt() and 0xFF) or (this[i + 1].toInt() shl 8)).toShort().toInt()

    /** The app's own volume on the samples: 1 (untouched) unless the DAC has no volume control and the phone isn't at full. */
    @Volatile var digitalGain = 1f; private set
}
