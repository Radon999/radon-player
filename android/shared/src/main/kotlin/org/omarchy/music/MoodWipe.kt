package org.omarchy.music

import java.io.File
import java.io.RandomAccessFile

/** Takes named tags (MOOD, MOOD_LYRICS) out of a song file in place, for when the usual writer couldn't: their values
 *  are blanked where they are, so the file keeps exactly its size and every other byte (the sound, the title, the
 *  artist, the album, the cover). It finds them the way [MoodTag] reads them, so whatever [MoodTag] reads as a mood is
 *  what goes: a FLAC's comments (also behind an ID3 tag), an ID3v2.3/2.4 tag's TMOO and TXXX frames (an MP3's, or the
 *  ID3 chunk of a WAV or AIFF; one with an extended header too), an M4A's "----" items whatever their "mean". A blank
 *  value is no tag to [MoodTag] (or to players), and the next mood written replaces it. Ogg files are left to the tag
 *  library (their comments sit inside checksummed pages). */
object MoodWipe {
    private const val MOST = 64L shl 20

    /** How many values were blanked in the file (0: none found, or a format it doesn't change). */
    fun wipe(file: File, names: Set<String>): Int {
        val want = names.map { it.uppercase() }.toSet()
        return RandomAccessFile(file, "rw").use { f ->
            if (f.length() < 12) return@use 0
            val head = ByteArray(12).also { f.seek(0); f.readFully(it) }
            val magic = String(head, 0, 4, Charsets.ISO_8859_1)
            when {
                magic == "fLaC" -> flac(f, 0, want)
                magic.startsWith("ID3") -> {
                    val n = id3(f, 0, want)
                    val after = id3End(f, 0)
                    n + (if (after != null && after + 4 <= f.length() && str(f, after, 4) == "fLaC") flac(f, after, want) else 0)
                }
                magic == "RIFF" && String(head, 8, 4, Charsets.ISO_8859_1) == "WAVE" -> chunks(f, true, want)
                magic == "FORM" && String(head, 8, 4, Charsets.ISO_8859_1).let { it == "AIFF" || it == "AIFC" } -> chunks(f, false, want)
                String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> mp4(f, want)
                else -> 0
            }
        }
    }

    private fun str(f: RandomAccessFile, at: Long, n: Int): String {
        f.seek(at); val b = ByteArray(n); f.readFully(b); return String(b, Charsets.ISO_8859_1)
    }
    private fun bytes(f: RandomAccessFile, at: Long, n: Int): ByteArray { f.seek(at); return ByteArray(n).also { f.readFully(it) } }
    private fun be32(b: ByteArray, i: Int) = ((b[i].toLong() and 0xff) shl 24) or ((b[i + 1].toLong() and 0xff) shl 16) or
        ((b[i + 2].toLong() and 0xff) shl 8) or (b[i + 3].toLong() and 0xff)
    private fun le32(b: ByteArray, i: Int) = (b[i].toLong() and 0xff) or ((b[i + 1].toLong() and 0xff) shl 8) or
        ((b[i + 2].toLong() and 0xff) shl 16) or ((b[i + 3].toLong() and 0xff) shl 24)
    private fun syncsafe(b: ByteArray, i: Int) = ((b[i].toLong() and 0x7f) shl 21) or ((b[i + 1].toLong() and 0x7f) shl 14) or
        ((b[i + 2].toLong() and 0x7f) shl 7) or (b[i + 3].toLong() and 0x7f)

    // ---- FLAC: the Vorbis comment block's entries "KEY=value" ----

    private fun flac(f: RandomAccessFile, start: Long, want: Set<String>): Int {
        var at = start + 4
        var n = 0
        while (at + 4 <= f.length()) {
            val h = bytes(f, at, 4)
            val last = h[0].toInt() and 0x80 != 0
            val type = h[0].toInt() and 0x7f
            val size = ((h[1].toLong() and 0xff) shl 16) or ((h[2].toLong() and 0xff) shl 8) or (h[3].toLong() and 0xff)
            val body = at + 4
            if (body + size > f.length()) break
            if (type == 4) n += comments(f, body, size, want)
            if (last) break
            at = body + size
        }
        return n
    }

    private fun comments(f: RandomAccessFile, body: Long, size: Long, want: Set<String>): Int {
        if (size > MOST) return 0
        val b = bytes(f, body, size.toInt())
        var i = 0
        fun int(): Long { if (i + 4 > b.size) return -1; val v = le32(b, i); i += 4; return v }
        val vendor = int(); if (vendor < 0 || i + vendor > b.size) return 0
        i += vendor.toInt()
        val count = int(); if (count < 0) return 0
        var n = 0
        repeat(count.toInt()) {
            val len = int(); if (len < 0 || i + len > b.size) return n
            val eq = (i until i + len.toInt()).firstOrNull { b[it] == '='.code.toByte() }
            if (eq != null && String(b, i, eq - i, Charsets.UTF_8).uppercase() in want && (eq + 1 until i + len.toInt()).any { b[it] != ' '.code.toByte() }) {
                f.seek(body + eq + 1); f.write(ByteArray(i + len.toInt() - eq - 1) { ' '.code.toByte() })
                n++
            }
            i += len.toInt()
        }
        return n
    }

    // ---- ID3v2.3 / 2.4: TMOO (MOOD) and TXXX frames by name ----

    /** Where the ID3 tag at `at` ends (header, frames and footer), or null. */
    private fun id3End(f: RandomAccessFile, at: Long): Long? {
        if (at + 10 > f.length()) return null
        val h = bytes(f, at, 10)
        if (String(h, 0, 3, Charsets.ISO_8859_1) != "ID3") return null
        return at + 10 + syncsafe(h, 6) + (if (h[5].toInt() and 0x10 != 0) 10 else 0)
    }

    private fun id3(f: RandomAccessFile, at: Long, want: Set<String>): Int {
        if (at + 10 > f.length()) return 0
        val h = bytes(f, at, 10)
        val major = h[3].toInt()
        val flags = h[5].toInt() and 0xff
        if (String(h, 0, 3, Charsets.ISO_8859_1) != "ID3" || major !in 3..4 || flags and 0x80 != 0) return 0   // (unsynchronised: left alone)
        val size = syncsafe(h, 6)
        if (size > MOST || at + 10 + size > f.length()) return 0
        val b = bytes(f, at + 10, size.toInt())
        var from = 0
        if (flags and 0x40 != 0 && b.size >= 4) from = (if (major == 4) syncsafe(b, 0) else be32(b, 0) + 4).toInt().coerceIn(0, b.size)
        // ID3v2.4 frame sizes are syncsafe, but some taggers wrote them plain: whichever walks the frames further.
        val ways = if (major == 4) listOf(true, false) else listOf(false)
        val best = ways.map { walk(b, from, it) }.maxByOrNull { it.second } ?: return 0
        var n = 0
        for ((id, frameAt, len, flag2) in best.first) {
            if (id != "TMOO" && id != "TXXX") continue
            if (major == 4 && flag2 and 0x0C != 0) continue   // compressed or encrypted
            if (major == 3 && flag2 and 0xC0 != 0) continue
            var d = frameAt; var dl = len
            if (major == 4 && flag2 and 0x01 != 0) { d += 4; dl -= 4 }   // a data length first
            if (dl < 2) continue
            val enc = b[d].toInt()
            var s = d + 1
            val end = d + dl
            if (id == "TMOO") { if ("MOOD" !in want) continue }
            else {
                // The TXXX's name, then its value
                val nameEnd = if (enc == 1 || enc == 2) { var k = s; while (k + 1 < end && !(b[k] == 0.toByte() && b[k + 1] == 0.toByte())) k += 2; k }
                    else { var k = s; while (k < end && b[k] != 0.toByte()) k++; k }
                val raw = b.copyOfRange(s, nameEnd)
                val name = when (enc) { 1 -> String(raw, Charsets.UTF_16); 2 -> String(raw, Charsets.UTF_16BE); 3 -> String(raw, Charsets.UTF_8)
                    else -> String(raw, Charsets.ISO_8859_1) }.removePrefix("﻿").trim().uppercase()
                if (name !in want) continue
                s = nameEnd + (if (enc == 1 || enc == 2) 2 else 1)
            }
            if (s >= end) continue
            if (blank(b, s, end, enc)) { f.seek(at + 10 + s); f.write(b, s, end - s); n++ }
        }
        return n
    }

    private data class Frame(val id: String, val at: Int, val len: Int, val flag2: Int)

    /** The frames from b[from], and how far they ran. */
    private fun walk(b: ByteArray, from: Int, syncsafeSizes: Boolean): Pair<List<Frame>, Int> {
        val out = ArrayList<Frame>()
        var i = from
        while (i + 10 <= b.size && b[i] != 0.toByte()) {
            val id = String(b, i, 4, Charsets.ISO_8859_1)
            if (id.any { !(it in 'A'..'Z' || it in '0'..'9') }) break
            val fs = (if (syncsafeSizes) syncsafe(b, i + 4) else be32(b, i + 4))
            if (fs < 0 || i + 10 + fs > b.size) break
            out += Frame(id, i + 10, fs.toInt(), b[i + 9].toInt() and 0xff)
            i += 10 + fs.toInt()
        }
        return out to i
    }

    /** b[s, end) (text in the frame's encoding) made spaces, its ends and byte-order marks kept. Whether anything changed. */
    private fun blank(b: ByteArray, s: Int, end: Int, enc: Int): Boolean {
        var changed = false
        if (enc == 1 || enc == 2) {
            var little = enc == 1
            var k = s
            while (k + 1 < end) {
                val a = b[k].toInt() and 0xff; val c = b[k + 1].toInt() and 0xff
                when {
                    a == 0xFF && c == 0xFE -> little = true
                    a == 0xFE && c == 0xFF -> little = false
                    a == 0 && c == 0 -> {}
                    else -> {
                        val (x, y) = if (little) 0x20 to 0 else 0 to 0x20
                        if (a != x || c != y) { b[k] = x.toByte(); b[k + 1] = y.toByte(); changed = true }
                    }
                }
                k += 2
            }
        } else for (k in s until end) if (b[k] != 0.toByte() && b[k] != ' '.code.toByte()) { b[k] = ' '.code.toByte(); changed = true }
        return changed
    }

    // ---- WAV and AIFF: the ID3 chunk ----

    private fun chunks(f: RandomAccessFile, little: Boolean, want: Set<String>): Int {
        var at = 12L
        var n = 0
        while (at + 8 <= f.length()) {
            val h = bytes(f, at, 8)
            val id = String(h, 0, 4, Charsets.ISO_8859_1)
            if (id.any { it.code < 32 || it.code > 126 }) break
            val size = if (little) le32(h, 4) else be32(h, 4)
            if (id.equals("id3 ", true) && size >= 10) n += id3(f, at + 8, want)
            at += 8 + size + (size and 1)
        }
        return n
    }

    // ---- MP4 / M4A: moov › udta › meta › ilst (or moov › meta) › "----" items by name ----

    private fun mp4(f: RandomAccessFile, want: Set<String>): Int {
        var n = 0
        for ((type, body, end) in boxes(f, 0, f.length())) if (type == "moov") {
            for ((t2, b2, e2) in boxes(f, body, end)) when (t2) {
                "udta" -> for ((t3, b3, e3) in boxes(f, b2, e2)) if (t3 == "meta") n += meta(f, b3, e3, want)
                "meta" -> n += meta(f, b2, e2, want)
            }
        }
        return n
    }

    private fun meta(f: RandomAccessFile, body: Long, end: Long, want: Set<String>): Int {
        // An iTunes meta box starts with a version and flags; a QuickTime one goes straight to its children.
        val from = if (end - body >= 8 && str(f, body + 4, 4) == "hdlr") body else body + 4
        var n = 0
        for ((t, b, e) in boxes(f, from, end)) if (t == "ilst") {
            for ((t4, ib, ie) in boxes(f, b, e)) {
                if (t4 != "----") continue
                val kids = boxes(f, ib, ie)
                val nb = kids.firstOrNull { k -> k.first == "name" } ?: continue
                if (nb.third - nb.second - 4 !in 0..4096) continue
                val name = String(bytes(f, nb.second + 4, (nb.third - nb.second - 4).toInt()), Charsets.UTF_8).uppercase()
                if (name !in want) continue
                for ((dt, db, de) in kids) if (dt == "data" && de - db > 8 && de - db - 8 <= MOST) {
                    val v = bytes(f, db + 8, (de - db - 8).toInt())
                    if (v.all { x -> x == ' '.code.toByte() }) continue
                    f.seek(db + 8); f.write(ByteArray(v.size) { ' '.code.toByte() }); n++
                }
            }
        }
        return n
    }

    /** The boxes in [from, to): (type, where its body starts, where it ends). */
    private fun boxes(f: RandomAccessFile, from: Long, to: Long): List<Triple<String, Long, Long>> {
        val out = ArrayList<Triple<String, Long, Long>>()
        var i = from
        while (i + 8 <= to) {
            val h = bytes(f, i, 8)
            var size = be32(h, 0); var head = 8L
            if (size == 1L) { if (i + 16 > to) break; size = java.nio.ByteBuffer.wrap(bytes(f, i + 8, 8)).long; head = 16 }
            else if (size == 0L) size = to - i
            if (size < head || i + size > to) break
            out += Triple(String(h, 4, 4, Charsets.ISO_8859_1), i + head, i + size)
            i += size
        }
        return out
    }
}
