package org.omarchy.music

import java.io.DataInputStream
import java.io.InputStream

/** Covers inside files that the phone's and the PC's own readers can miss: a WAV's (its "id3 " chunk, as taggers
 *  write it), and an ID3 tag's picture (APIC, or PIC in old tags). */
object EmbeddedArt {
    private const val MAX = 30 shl 20   // a picture chunk bigger than 30 MB isn't one

    /** The cover a file carries: a WAV's or an AIFF's ID3 chunk, an M4A's (its covr atom, AAC or ALAC), a FLAC's PICTURE
     *  block, or an ID3 tag at the very start (raw AAC (.aac) files, MP3s); null if none. */
    fun picture(input: InputStream): ByteArray? = runCatching {
        val b = if (input.markSupported()) input else java.io.BufferedInputStream(input)
        b.mark(16)
        val head = ByteArray(10)
        var n = 0
        while (n < 10) { val r = b.read(head, n, 10 - n); if (r <= 0) break; n += r }
        b.reset()
        when {
            n >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "RIFF" -> wavPicture(b)
            n >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "FORM" -> aiff(b)
            n >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> mp4(b)
            n >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "fLaC" -> flac(b)
            n == 10 && String(head, 0, 3, Charsets.ISO_8859_1) == "ID3" -> {
                val size = 10 + syncsafe(head, 6) + (if (head[5].toInt() and 0x10 != 0) 10 else 0)   // a v2.4 footer
                if (size > MAX) null else {
                    val tag = ByteArray(size).also { DataInputStream(b).readFully(it) }
                    // An ID3 tag before a FLAC's own header (some taggers write one): the FLAC's picture if the tag has none
                    id3Picture(tag) ?: runCatching {
                        b.mark(8); val m = ByteArray(4); val r = b.read(m); b.reset()
                        if (r == 4 && String(m, Charsets.ISO_8859_1) == "fLaC") flac(b) else null
                    }.getOrNull()
                }
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

    /** An AIFF's cover: its "ID3 " chunk (sizes big-endian, unlike a WAV's). */
    private fun aiff(input: InputStream): ByteArray? {
        val d = DataInputStream(input)
        val head = ByteArray(12)
        d.readFully(head)
        val kind = String(head, 8, 4, Charsets.ISO_8859_1)
        if (kind != "AIFF" && kind != "AIFC") return null
        val id = ByteArray(4)
        while (true) {
            d.readFully(id)
            val size = d.readInt().toLong() and 0xffffffffL
            if (String(id, Charsets.ISO_8859_1).equals("ID3 ", true)) {
                if (size > MAX) return null
                return id3Picture(ByteArray(size.toInt()).also { d.readFully(it) })
            }
            var left = size + (size and 1)
            while (left > 0) { val n = d.skip(left); if (n <= 0) return null; left -= n }
        }
    }

    /** A FLAC's cover: its PICTURE metadata block (type 6), the front cover if there are several. */
    private fun flac(input: InputStream): ByteArray? {
        val d = DataInputStream(input)
        val magic = ByteArray(4).also { d.readFully(it) }
        if (String(magic, Charsets.ISO_8859_1) != "fLaC") return null
        var first: ByteArray? = null
        while (true) {
            val h = d.readInt()
            val last = h ushr 31 == 1
            val type = (h ushr 24) and 0x7f
            val size = h and 0xffffff
            if (type == 6 && size <= MAX) {
                val b = ByteArray(size).also { d.readFully(it) }
                val kind = int(b, 0)
                var p = 4
                p += 4 + int(b, p)   // the mime type
                p += 4 + int(b, p)   // the description
                p += 16              // width, height, colour depth, colours used
                val len = int(b, p); p += 4
                if (len > 0 && p + len <= b.size) {
                    val pic = b.copyOfRange(p, p + len)
                    if (kind == 3) return pic
                    if (first == null) first = pic
                }
            } else {
                var left = size.toLong()
                while (left > 0) { val n = d.skip(left); if (n <= 0) return first; left -= n }
            }
            if (last) return first
        }
    }

    /** An M4A's cover: moov › udta › meta › ilst › covr › data, read box by box (the moov box may come after the music). */
    private fun mp4(input: InputStream): ByteArray? {
        val d = DataInputStream(input)
        while (true) {
            var size = d.readInt().toLong() and 0xffffffffL
            val type = ByteArray(4).also { d.readFully(it) }.let { String(it, Charsets.ISO_8859_1) }
            var header = 8L
            if (size == 1L) { size = d.readLong(); header = 16 }
            if (size == 0L) return null   // runs to the end: the music, no moov after it
            if (type == "moov") {
                if (size - header > MAX * 2L) return null
                val moov = ByteArray((size - header).toInt()).also { d.readFully(it) }
                return box(moov, 0, moov.size, listOf("udta", "meta", "ilst", "covr", "data"))?.let { (from, to) ->
                    if (to - from > 8) moov.copyOfRange(from + 8, to) else null   // data: type and locale, then the picture
                }
            }
            var left = size - header
            while (left > 0) { val n = d.skip(left); if (n <= 0) return null; left -= n }
        }
    }

    /** Where the box at the end of path is inside b[from, to): (its body's start, end), or null. meta has 4 bytes of
     *  version and flags before its boxes. */
    private fun box(b: ByteArray, from: Int, to: Int, path: List<String>): Pair<Int, Int>? {
        if (path.isEmpty()) return from to to
        var p = from
        while (p + 8 <= to) {
            var size = int(b, p).toLong() and 0xffffffffL
            val type = String(b, p + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L && p + 16 <= to) { size = ((int(b, p + 8).toLong() and 0xffffffffL) shl 32) or (int(b, p + 12).toLong() and 0xffffffffL); header = 16 }
            if (size == 0L) size = (to - p).toLong()
            if (size < header || p + size > to) return null
            if (type == path[0]) {
                val body = p + header + if (type == "meta") 4 else 0
                return box(b, body, (p + size).toInt(), path.drop(1))
            }
            p += size.toInt()
        }
        return null
    }

    /** The picture in an ID3v2 tag (the front cover if there are several), or null. Reads tags written with
     *  "unsynchronisation" (the whole tag in v2.2/2.3, a frame in v2.4) and v2.4 frames that carry their length first. */
    fun id3Picture(raw: ByteArray): ByteArray? {
        if (raw.size < 10 || String(raw, 0, 3, Charsets.ISO_8859_1) != "ID3") return null
        val major = raw[3].toInt()
        val wholeUnsync = raw[5].toInt() and 0x80 != 0
        // v2.2/2.3: the whole tag's bytes after the header were unsynchronised (FF 00 for each FF): undone first
        val tag = if (wholeUnsync && major < 4) raw.copyOfRange(0, 10) + resync(raw, 10, minOf(raw.size, 10 + syncsafe(raw, 6))) else raw
        val end = if (tag === raw) minOf(tag.size, 10 + syncsafe(tag, 6)) else tag.size
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
                // The frame's flags: v2.4 0x01 its length first (4 bytes), 0x02 unsynchronised, 0x04 encrypted, 0x08
                // compressed; v2.3 0x80 compressed, 0x40 encrypted, 0x20 a group byte first
                val f = if (major == 2) 0 else tag[p + 9].toInt() and 0xff
                val skip = when {
                    major == 4 && f and 0x0c != 0 -> -1
                    major == 3 && f and 0xc0 != 0 -> -1
                    major == 4 -> (if (f and 0x40 != 0) 1 else 0) + (if (f and 0x01 != 0) 4 else 0)
                    major == 3 -> if (f and 0x20 != 0) 1 else 0
                    else -> 0
                }
                val got = if (skip < 0 || skip >= size) null
                    else if (major == 4 && (f and 0x02 != 0 || wholeUnsync)) {
                        val b = resync(tag, body + skip, body + size)
                        picture(b, 0, b.size, old = false)
                    } else picture(tag, body + skip, body + size, old = frame == "PIC")
                got?.let { (type, data) ->
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

    /** b[from, to) with ID3's unsynchronisation undone: each FF 00 back to FF. */
    private fun resync(b: ByteArray, from: Int, to: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(to - from)
        var i = from
        while (i < to) {
            out.write(b[i].toInt())
            if (b[i] == 0xFF.toByte() && i + 1 < to && b[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    private fun syncsafe(b: ByteArray, i: Int) = ((b[i].toInt() and 0x7f) shl 21) or ((b[i + 1].toInt() and 0x7f) shl 14) or
        ((b[i + 2].toInt() and 0x7f) shl 7) or (b[i + 3].toInt() and 0x7f)

    private fun int(b: ByteArray, i: Int) = ((b[i].toInt() and 0xff) shl 24) or ((b[i + 1].toInt() and 0xff) shl 16) or
        ((b[i + 2].toInt() and 0xff) shl 8) or (b[i + 3].toInt() and 0xff)
}
