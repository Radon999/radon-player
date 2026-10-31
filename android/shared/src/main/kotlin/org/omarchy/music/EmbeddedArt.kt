package org.omarchy.music

import java.io.DataInputStream
import java.io.InputStream

/** Covers inside files that the phone's and the PC's own readers can miss: a WAV's (its "id3 " chunk, as taggers
 *  write it), and an ID3 tag's picture (APIC, or PIC in old tags). */
object EmbeddedArt {
    private const val MAX = 30 shl 20   // a picture chunk bigger than 30 MB isn't one

    /** The cover a file's start carries: a WAV's ID3 chunk, or an ID3 tag at the very start (raw AAC (.aac) files, MP3s),
     *  or null. */
    fun picture(input: InputStream): ByteArray? = runCatching {
        val b = if (input.markSupported()) input else java.io.BufferedInputStream(input)
        b.mark(16)
        val head = ByteArray(10)
        var n = 0
        while (n < 10) { val r = b.read(head, n, 10 - n); if (r <= 0) break; n += r }
        b.reset()
        when {
            n >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "RIFF" -> wavPicture(b)
            n == 10 && String(head, 0, 3, Charsets.ISO_8859_1) == "ID3" -> {
                val size = 10 + syncsafe(head, 6)
                if (size > MAX) null else id3Picture(ByteArray(size).also { DataInputStream(b).readFully(it) })
            }
            else -> null
        }
    }.getOrNull()

    /** The cover in a WAV file's ID3 chunk, or null (not a WAV, or no cover). */
    fun wavPicture(input: InputStream): ByteArray? = runCatching { wav(input) }.getOrNull()

    private fun wav(input: InputStream): ByteArray? {
        val d = DataInputStream(input)
        val head = ByteArray(12)
        d.readFully(head)
        if (String(head, 0, 4, Charsets.ISO_8859_1) != "RIFF" || String(head, 8, 4, Charsets.ISO_8859_1) != "WAVE") return null
        val id = ByteArray(4)
        while (true) {
            d.readFully(id)
            val size = Integer.reverseBytes(d.readInt()).toLong() and 0xffffffffL
            if (String(id, Charsets.ISO_8859_1).equals("id3 ", true)) {
                if (size > MAX) return null
                return id3Picture(ByteArray(size.toInt()).also { d.readFully(it) })
            }
            var left = size + (size and 1)   // chunks are padded to an even length
            while (left > 0) { val n = d.skip(left); if (n <= 0) return null; left -= n }
        }
    }

    /** The picture in an ID3v2 tag (the front cover if there are several), or null. */
    fun id3Picture(tag: ByteArray): ByteArray? {
        if (tag.size < 10 || String(tag, 0, 3, Charsets.ISO_8859_1) != "ID3") return null
        val major = tag[3].toInt()
        val end = minOf(tag.size, 10 + syncsafe(tag, 6))
        var p = 10
        if (tag[5].toInt() and 0x40 != 0 && major >= 3) p += if (major == 4) syncsafe(tag, p) else 4 + int(tag, p)   // extended header
        var first: ByteArray? = null
        while (p + (if (major == 2) 6 else 10) <= end) {
            val idLen = if (major == 2) 3 else 4
            val frame = String(tag, p, idLen, Charsets.ISO_8859_1)
            if (frame[0] == '\u0000') break
            val size = when (major) { 2 -> ((tag[p + 3].toInt() and 0xff) shl 16) or ((tag[p + 4].toInt() and 0xff) shl 8) or (tag[p + 5].toInt() and 0xff)
                4 -> syncsafe(tag, p + 4); else -> int(tag, p + 4) }
            val body = p + (if (major == 2) 6 else 10)
            if (size <= 0 || body + size > end) break
            if (frame == "APIC" || frame == "PIC") {
                picture(tag, body, body + size, old = frame == "PIC")?.let { (type, data) ->
                    if (type == 3) return data   // the front cover
                    if (first == null) first = data
                }
            }
            p = body + size
        }
        return first
    }

    /** (picture type, image) of an APIC/PIC frame's body. */
    private fun picture(b: ByteArray, from: Int, to: Int, old: Boolean): Pair<Int, ByteArray>? {
        var p = from
        val enc = b[p++].toInt()
        if (old) p += 3 else { while (p < to && b[p].toInt() != 0) p++; p++ }   // PIC: "JPG"; APIC: the mime type
        if (p >= to) return null
        val type = b[p++].toInt() and 0xff
        val wide = enc == 1 || enc == 2   // UTF-16: the description ends with two zero bytes
        if (wide) { while (p + 1 < to && !(b[p].toInt() == 0 && b[p + 1].toInt() == 0)) p += 2; p += 2 }
        else { while (p < to && b[p].toInt() != 0) p++; p++ }
        if (p >= to) return null
        return type to b.copyOfRange(p, to)
    }

    private fun syncsafe(b: ByteArray, i: Int) = ((b[i].toInt() and 0x7f) shl 21) or ((b[i + 1].toInt() and 0x7f) shl 14) or
        ((b[i + 2].toInt() and 0x7f) shl 7) or (b[i + 3].toInt() and 0x7f)

    private fun int(b: ByteArray, i: Int) = ((b[i].toInt() and 0xff) shl 24) or ((b[i + 1].toInt() and 0xff) shl 16) or
        ((b[i + 2].toInt() and 0xff) shl 8) or (b[i + 3].toInt() and 0xff)
}
