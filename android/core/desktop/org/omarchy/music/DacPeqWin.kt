package org.omarchy.music

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

/** The PC's half of [DacPeq], on Windows: a FiiO DAC's HID interface (Windows shares it with FiiO Control), found
 *  through Windows' HID device list, written and read with overlapped I/O so a silent DAC never holds the app up. */
object DacPeqWin : DacPeq.Link {
    private interface Hid : Library {
        fun HidD_GetHidGuid(guid: Memory)
        fun HidD_GetPreparsedData(h: Pointer, pp: PointerByReference): Byte
        fun HidD_FreePreparsedData(pp: Pointer): Byte
        fun HidP_GetCaps(pp: Pointer, caps: Memory): Int
        fun HidD_GetProductString(h: Pointer, buf: Memory, len: Int): Byte
    }
    private interface Cfg : Library {
        fun CM_Get_Device_Interface_List_SizeW(len: IntByReference, guid: Memory, dev: WString?, flags: Int): Int
        fun CM_Get_Device_Interface_ListW(guid: Memory, dev: WString?, buf: Memory, len: Int, flags: Int): Int
    }
    private interface K32 : Library {
        fun CreateFileW(name: WString, access: Int, share: Int, sec: Pointer?, disp: Int, flags: Int, tmpl: Pointer?): Pointer?
        fun CloseHandle(h: Pointer): Int
        fun WriteFile(h: Pointer, buf: Memory, n: Int, done: IntByReference?, ov: Memory): Int
        fun ReadFile(h: Pointer, buf: Memory, n: Int, done: IntByReference?, ov: Memory): Int
        fun CreateEventW(sec: Pointer?, manual: Int, initial: Int, name: WString?): Pointer?
        fun WaitForSingleObject(h: Pointer, ms: Int): Int
        fun GetOverlappedResult(h: Pointer, ov: Memory, done: IntByReference, wait: Int): Int
        fun CancelIoEx(h: Pointer, ov: Memory?): Int
    }

    private val windows = System.getProperty("os.name").lowercase().contains("win")
    private val hid by lazy { Native.load("hid", Hid::class.java) }
    private val cfg by lazy { Native.load("cfgmgr32", Cfg::class.java) }
    private val k32 by lazy { Native.load("kernel32", K32::class.java) }

    fun start() {
        if (!windows) return
        DacPeq.link = this
        DacPeq.refresh()
    }

    private class Found(val path: String, val name: String, val inLen: Int, val outLen: Int)
    @Volatile private var cache: Found? = null
    @Volatile private var cacheAt = 0L

    private fun open(path: String, overlapped: Boolean): Pointer? {
        val h = k32.CreateFileW(WString(path), (0x80000000L or 0x40000000L).toInt() /* read, write */, 3 /* share both */,
            null, 3 /* OPEN_EXISTING */, if (overlapped) 0x40000000 else 0, null) ?: return null
        return if (Pointer.nativeValue(h) == -1L || Pointer.nativeValue(h) == 0L) null else h
    }

    /** The FiiO DAC's vendor HID collection (one that takes output reports), looked for again every few seconds. */
    private fun find(): Found? {
        val now = System.currentTimeMillis()
        if (now - cacheAt < 3000) return cache
        cacheAt = now
        cache = runCatching { look() }.getOrNull()
        return cache
    }

    private fun look(): Found? {
        val guid = Memory(16).apply { clear() }
        hid.HidD_GetHidGuid(guid)
        val len = IntByReference()
        if (cfg.CM_Get_Device_Interface_List_SizeW(len, guid, null, 0) != 0 || len.value <= 1) return null
        val buf = Memory(len.value * 2L).apply { clear() }
        if (cfg.CM_Get_Device_Interface_ListW(guid, null, buf, len.value, 0) != 0) return null
        val paths = String(buf.getCharArray(0, len.value)).split('\u0000').filter { it.lowercase().contains("vid_2972") }
        var best: Found? = null
        for (p in paths) {
            val h = open(p, false) ?: continue
            try {
                val pp = PointerByReference()
                if (hid.HidD_GetPreparsedData(h, pp).toInt() == 0) continue
                val caps = Memory(64).apply { clear() }
                val st = hid.HidP_GetCaps(pp.value, caps)
                hid.HidD_FreePreparsedData(pp.value)
                if (st != 0x00110000) continue
                val page = caps.getShort(2).toInt() and 0xFFFF
                val inLen = caps.getShort(4).toInt() and 0xFFFF
                val outLen = caps.getShort(6).toInt() and 0xFFFF
                if (outLen <= 1) continue
                val nm = Memory(512).apply { clear() }
                val name = if (hid.HidD_GetProductString(h, nm, 512).toInt() != 0) nm.getWideString(0).trim() else ""
                val f = Found(p, name.ifEmpty { "FiiO DAC" }, inLen, outLen)
                if (page >= 0xFF00) return f
                if (best == null) best = f
            } finally { k32.CloseHandle(h) }
        }
        return best
    }

    override fun dac(): String? = if (windows) find()?.name else null

    /** One overlapped write or read, waited for at most ms; the number of bytes, or -1. */
    private fun io(h: Pointer, buf: Memory, n: Int, write: Boolean, ms: Int): Int {
        val ev = k32.CreateEventW(null, 1, 0, null) ?: return -1
        try {
            val ov = Memory(32).apply { clear(); setPointer(if (Native.POINTER_SIZE == 8) 24L else 16L, ev) }
            val done = IntByReference()
            if (write) k32.WriteFile(h, buf, n, null, ov) else k32.ReadFile(h, buf, n, null, ov)
            if (k32.WaitForSingleObject(ev, ms) != 0) {
                k32.CancelIoEx(h, ov)
                k32.GetOverlappedResult(h, ov, done, 1)   // the cancel finished before ov and buf go
                return -1
            }
            return if (k32.GetOverlappedResult(h, ov, done, 0) != 0) done.value else -1
        } finally { k32.CloseHandle(ev) }
    }

    override fun send(packet: ByteArray, reportId: Int, reply: Boolean): ByteArray? = synchronized(this) {
        val f = find() ?: return null
        val h = open(f.path, true) ?: run { cacheAt = 0; return null }
        try {
            val out = Memory(f.outLen.toLong()).apply { clear() }
            out.setByte(0, reportId.toByte())
            out.write(1, packet, 0, minOf(packet.size, f.outLen - 1))
            if (io(h, out, f.outLen, true, 1000) < 0) return null
            if (!reply || f.inLen <= 1) return ByteArray(0)
            val got = java.io.ByteArrayOutputStream()
            val inb = Memory(f.inLen.toLong())
            val until = System.currentTimeMillis() + 900
            while (System.currentTimeMillis() < until) {
                inb.clear()
                val n = io(h, inb, f.inLen, false, 300)
                if (n > 0) { got.write(inb.getByteArray(0, n)); if (DacPeq.presetIn(got.toByteArray()) != null) break }
            }
            got.toByteArray()
        } catch (e: Throwable) { null } finally { k32.CloseHandle(h) }
    }
}
