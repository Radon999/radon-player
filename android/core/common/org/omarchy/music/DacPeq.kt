package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The DAC's own equalizer (the PEQ the FiiO Control app sets up, kept inside the DAC): which of its presets is on, and
 *  switching to another (or to Off, its bypass) from the app, on the phone and the PC. Only a FiiO KA17 for now: it's
 *  told over its USB HID interface, as FiiO Control does (the commands as DevicePEQ worked them out). The presets'
 *  settings themselves stay in the DAC: the app only picks which one is used. */
object DacPeq {
    /** The platform's half: finding the DAC and sending it a command. */
    interface Link {
        /** The DAC's name (its USB product name) if one with its own EQ is plugged in, else null. */
        fun dac(): String?
        /** The app may talk to it (the phone asks you once); false: [ask]. */
        fun allowed(): Boolean = true
        fun ask() {}
        /** Sends packet as an output report (with its report ID), and if reply, gives back what the DAC sends in the
         *  next moment (its input reports run together); null if it couldn't be sent. */
        fun send(packet: ByteArray, reportId: Int, reply: Boolean): ByteArray?
    }

    @Volatile var link: Link? = null

    /** The KA17's presets as FiiO numbers them (Off is the bypass). */
    val PRESETS = listOf(4 to "USER1", 8 to "USER2", 9 to "USER3", 0 to "Jazz", 1 to "Pop", 2 to "Rock", 3 to "Dance",
        5 to "R&B", 6 to "Classic", 7 to "Hip-hop")
    const val OFF = 10
    private const val LAST = "dac_peq_last"      // the preset Off goes back to

    var dac by mutableStateOf<String?>(null); private set
    var preset by mutableStateOf<Int?>(null); private set     // null: not read (yet)
    var allowed by mutableStateOf(true); private set
    var busy by mutableStateOf(false); private set
    var note by mutableStateOf(""); private set

    fun name(id: Int?) = when (id) { null -> "?"; OFF -> "Off"; else -> PRESETS.firstOrNull { it.first == id }?.second ?: "Preset $id" }
    /** The preset turning it on goes to: the last one used here, else USER1. */
    fun last(): Int = Prefs.num(LAST, 4f).toInt().takeIf { it != OFF } ?: 4

    private fun supported(name: String) = name.contains("KA17", ignoreCase = true)
    private fun reportId(name: String) = 1   // the KA17's (the FiiO DAPs and older dongles use 7)

    // ---- the commands ----
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    fun setPacket(id: Int) = bytes(0xAA, 0x0A, 0, 0, 0x16, 1, id, 0, 0xEE)
    val GET_PACKET = bytes(0xBB, 0x0A, 0, 0, 0x16, 0, 0, 0xEE)
    /** The preset in the DAC's reply (BB 0B .. .. 16 .. id), or null. */
    fun presetIn(b: ByteArray): Int? {
        for (i in 0..b.size - 7)
            if ((b[i].toInt() and 0xFF) == 0xBB && b[i + 1].toInt() == 0x0B && b[i + 4].toInt() == 0x16) return b[i + 6].toInt() and 0xFF
        return null
    }

    /** From a HID report descriptor: whether it numbers its reports, and how many bytes the output report id has
     *  (without the ID byte); null if it has none. */
    fun outputBytes(desc: ByteArray, id: Int): Pair<Boolean, Int>? {
        var i = 0; var size = 0; var count = 0; var rid = 0; var ids = false
        val bits = HashMap<Int, Int>()
        while (i < desc.size) {
            val b = desc[i].toInt() and 0xFF
            if (b == 0xFE) { i += 3 + (if (i + 1 < desc.size) desc[i + 1].toInt() and 0xFF else 0); continue }   // a long item
            val n = when (b and 3) { 0 -> 0; 1 -> 1; 2 -> 2; else -> 4 }
            if (i + n >= desc.size) break
            var v = 0
            for (k in 0 until n) v = v or ((desc[i + 1 + k].toInt() and 0xFF) shl (8 * k))
            when (b and 0xFC) {
                0x74 -> size = v
                0x94 -> count = v
                0x84 -> { rid = v; ids = true }
                0x90 -> bits[rid] = (bits[rid] ?: 0) + size * count
            }
            i += 1 + n
        }
        val got = bits[if (ids) id else 0] ?: return null
        return ids to (got + 7) / 8
    }

    // ---- what the app does ----
    /** Looks again: is the DAC there, and which preset is it on. */
    fun refresh() {
        val l = link ?: return
        Music.bg({
            val d = l.dac()?.takeIf { supported(it) }
            val ok = d != null && l.allowed()
            Triple(d, ok, if (ok) read(l, d!!) else null)
        }) { r, _ ->
            dac = r?.first; allowed = r?.second ?: true; preset = r?.third
            if (dac == null) note = ""
        }
    }

    private fun read(l: Link, d: String): Int? {
        repeat(2) { l.send(GET_PACKET, reportId(d), true)?.let { r -> presetIn(r)?.let { return it } } }
        return null
    }

    /** Switches the DAC to preset id (OFF: no EQ). */
    fun choose(id: Int) {
        val l = link ?: return
        val d = dac ?: return
        if (busy) return
        busy = true; note = ""
        if (id != OFF) Prefs.setNum(LAST, id.toFloat())
        Music.bg({
            val sent = l.send(setPacket(id), reportId(d), false) != null
            Thread.sleep(150)
            sent to read(l, d)
        }) { r, e ->
            busy = false
            val now = r?.second
            if (now != null) preset = now
            note = when {
                e != null || r?.first != true -> "Couldn't reach the ${d}'s EQ: unplug it and plug it in again, then try once more"
                now == null -> "Sent to the $d, but it didn't say which preset it's on now"
                now != id -> "The $d stayed on ${name(now)}"
                else -> ""
            }
            if (r?.first != true || now != id) Crashes.note("DAC EQ: asked the $d for ${name(id)} (${id}); " +
                (if (r?.first != true) "couldn't send it" else "it's on ${name(now)} (${now})"))
        }
    }

    fun turn(on: Boolean) = choose(if (on) last() else OFF)
    fun ask() { link?.ask() }
}
