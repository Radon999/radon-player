package org.omarchy.music

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A song file's own MOOD tag, read straight from its start (no tag library, so it works the same on the phone and
 *  the PC, for any file the app can open): a FLAC's or Ogg's MOOD comment, an MP3's ID3 TMOO (or TXXX "MOOD"), the
 *  same in a WAV's or AIFF's ID3 chunk, an M4A's iTunes "MOOD" item. Only the tag is read; the sound is skipped over. */
object MoodTag {
    private const val MOST = 64 shl 20   // a tag bigger than this (64 MB) isn't read

    /** The mood a MOOD tag's words mean: one of [MoodRules.ALL], or null. "Chill", "chill; dreamy" (the first that
     *  says one), "Melancholic" (Sad); "Not happy" says nothing. */
    fun pick(text: String?): String? {
        val parts = text?.split(Regex("[;,/|\\u0000]"))?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return null
        for (p in parts) {
            if (p.lowercase().startsWith("not ")) continue   // AcousticBrainz-style "Not aggressive"
            MoodRules.ALL.firstOrNull { it.equals(p, true) }?.let { return it }
            MoodRules.decide(null, "", mapOf(p to 1.0)).firstOrNull()?.let { return it.first }
        }
        return null
    }

    /** Every mood a MOOD tag holds, ours, in its order ("Happy; Party" -> Happy, Party), as one text "Happy;Party"
     *  ("" none). A song can have several moods of its own (set in the Tagger). */
    fun pickAll(text: String?): String {
        val parts = text?.split(Regex("[;,/|\\u0000]"))?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return ""
        val out = LinkedHashSet<String>()
        for (p in parts) {
            if (p.lowercase().startsWith("not ")) continue
            (MoodRules.ALL.firstOrNull { it.equals(p, true) } ?: MoodRules.decide(null, "", mapOf(p to 1.0)).firstOrNull()?.first)
                ?.let { out += it }
        }
        return out.joinToString(";")
    }

    const val MOOD = "MOOD"
    /** The lyrics' mood (see [LyricsMood]): its own tag, read as MOOD is. */
    const val MOOD_LYRICS = "MOOD_LYRICS"
    /** The lyrics: a Vorbis LYRICS (or UNSYNCEDLYRICS) comment, an ID3 USLT frame, an M4A's ©lyr item. */
    const val LYRICS = "LYRICS"

    /** The text of the file's MOOD tag ("" when it has none), from its first bytes onward; null if it can't be read. */
    fun read(input: InputStream): String? = readTags(input, setOf(MOOD))?.let { it[MOOD] ?: "" }

    /** Several of the file's tags at once, by name (MOOD, MOOD_LYRICS, LYRICS, or any other: a Vorbis comment, an ID3
     *  TXXX frame, an M4A "----" item of that name): the ones the file has (blank ones left out); null if it can't be
     *  read. Only the tags are read; the sound is skipped over. */
    fun readTags(input: InputStream, names: Set<String>): Map<String, String>? = runCatching {
        val want = names.map { it.uppercase() }.toSet()
        val out = HashMap<String, String>()
        val s = Bytes(input)
        val head = s.bytes(4)
        when (String(head, Charsets.ISO_8859_1)) {
            "fLaC" -> flac(head, input, want, out)
            "OggS" -> ogg(s, head, want, out)
            "RIFF" -> { s.bytes(4); if (String(s.bytes(4), Charsets.ISO_8859_1) == "WAVE") chunks(s, little = true, want, out) }
            "FORM" -> { s.bytes(4); if (String(s.bytes(4), Charsets.ISO_8859_1) in setOf("AIFF", "AIFC")) chunks(s, little = false, want, out) }
            else -> if (String(head, 0, 3, Charsets.ISO_8859_1) == "ID3") {
                id3(s, head, want, out, toEnd = true)
                // A FLAC with an ID3 tag in front of it (some taggers do that): its own comments after it.
                if (want.any { it !in out }) {
                    val next = try { s.bytes(4) } catch (_: EOFException) { null }
                    if (next != null && String(next, Charsets.ISO_8859_1) == "fLaC") flac(next, input, want, out)
                }
            } else mp4(s, head, want, out)
        }
        out.filterValues { it.isNotBlank() }
    }.getOrNull()

    /** A FLAC's comments (its first four bytes already read). */
    private fun flac(first4: ByteArray, input: InputStream, want: Set<String>, out: MutableMap<String, String>) {
        val c = Flac.comments(Flac.readHeader(java.io.SequenceInputStream(ByteArrayInputStream(first4), input)))
        fromComments(c, want, out)
    }

    /** The wanted ones of a set of Vorbis comments (keys upper case). */
    private fun fromComments(c: Map<String, List<String>>, want: Set<String>, out: MutableMap<String, String>) {
        for (n in want) {
            val v = (if (n == LYRICS) c[LYRICS] ?: c["UNSYNCEDLYRICS"] else c[n])?.firstOrNull { it.isNotBlank() } ?: continue
            out.putIfAbsent(n, v)
        }
    }

    /** Reads and skips through a stream, never back. */
    private class Bytes(val s: InputStream) {
        fun bytes(n: Int): ByteArray {
            val b = ByteArray(n); var o = 0
            while (o < n) { val r = s.read(b, o, n - o); if (r < 0) throw EOFException(); o += r }
            return b
        }
        fun skip(n: Long) {
            var left = n
            while (left > 0) {
                val k = s.skip(left)
                if (k > 0) left -= k else { if (s.read() < 0) throw EOFException(); left-- }
            }
        }
    }

    private fun be32(b: ByteArray, i: Int) = ((b[i].toLong() and 0xff) shl 24) or ((b[i + 1].toLong() and 0xff) shl 16) or
        ((b[i + 2].toLong() and 0xff) shl 8) or (b[i + 3].toLong() and 0xff)
    private fun le32(b: ByteArray, i: Int) = (b[i].toLong() and 0xff) or ((b[i + 1].toLong() and 0xff) shl 8) or
        ((b[i + 2].toLong() and 0xff) shl 16) or ((b[i + 3].toLong() and 0xff) shl 24)
    private fun syncsafe(b: ByteArray, i: Int) = ((b[i].toInt() and 0x7f) shl 21) or ((b[i + 1].toInt() and 0x7f) shl 14) or
        ((b[i + 2].toInt() and 0x7f) shl 7) or (b[i + 3].toInt() and 0x7f)

    // ---- ID3v2 (MP3, and the ID3 chunk of a WAV or AIFF) ----

    /** An ID3v2.3/2.4 tag (its first four bytes already read): MOOD from its TMOO (else a TXXX "MOOD"), LYRICS from
     *  its USLT, any other name from a TXXX of that name. toEnd: read on to the tag's end (what follows it is read next). */
    private fun id3(s: Bytes, first4: ByteArray, want: Set<String>, out: MutableMap<String, String>, toEnd: Boolean = false) {
        val h = first4 + s.bytes(6)
        val major = h[3].toInt()
        if (String(h, 0, 3, Charsets.ISO_8859_1) != "ID3" || major !in 3..4) return
        val end = syncsafe(h, 6).toLong()
        var at = 0L
        if (h[5].toInt() and 0x40 != 0) {   // an extended header: skipped
            val x = s.bytes(4); at += 4
            val size = if (major == 4) syncsafe(x, 0).toLong() - 4 else be32(x, 0) + 2
            if (size > 0) { s.skip(size); at += size }
        }
        var tmoo: String? = null
        while (at + 10 <= end) {
            val f = s.bytes(10); at += 10
            if (f[0] == 0.toByte()) break   // padding
            val id = String(f, 0, 4, Charsets.ISO_8859_1)
            val size = if (major == 4) syncsafe(f, 4).toLong() else be32(f, 4)
            if (size < 0 || at + size > end) break
            val wanted = when (id) {
                "TMOO" -> MOOD in want && size < 65536
                "TXXX" -> size < 65536
                "USLT" -> LYRICS in want && size < (8 shl 20)
                else -> false
            }
            if (wanted) {
                var d = s.bytes(size.toInt())
                if (major == 4 && f[9].toInt() and 0x01 != 0 && d.size >= 4) d = d.copyOfRange(4, d.size)   // a data length first
                when (id) {
                    "TMOO" -> if (tmoo == null) tmoo = id3Texts(d).firstOrNull()
                    "USLT" -> if (LYRICS !in out) uslt(d)?.let { out[LYRICS] = it }
                    else -> {
                        val texts = id3Texts(d)
                        val name = texts.firstOrNull()?.uppercase()
                        if (texts.size >= 2 && name != null && name in want && name !in out) out[name] = texts.drop(1).joinToString("; ")
                    }
                }
            } else s.skip(size)
            at += size
        }
        tmoo?.let { out[MOOD] = it }   // TMOO over a TXXX "MOOD"
        if (toEnd && at < end) s.skip(end - at)
        if (toEnd && h[5].toInt() and 0x10 != 0) s.skip(10)   // a footer
    }

    /** A USLT frame's words (after its encoding, language and description). */
    private fun uslt(d: ByteArray): String? {
        if (d.size < 5) return null
        val enc = d[0].toInt()
        var i = 4
        if (enc == 1 || enc == 2) { while (i + 1 < d.size && !(d[i] == 0.toByte() && d[i + 1] == 0.toByte())) i += 2; i += 2 }
        else { while (i < d.size && d[i] != 0.toByte()) i++; i++ }
        if (i > d.size) return null
        val body = d.copyOfRange(i, d.size)
        val s = when (enc) {
            1 -> String(body, Charsets.UTF_16)
            2 -> String(body, Charsets.UTF_16BE)
            3 -> String(body, Charsets.UTF_8)
            else -> String(body, Charsets.ISO_8859_1)
        }
        return s.trimEnd('\u0000').removePrefix("\uFEFF").trim().ifEmpty { null }
    }

    /** A text frame's strings (split where it has several, as TXXX's name and value). */
    private fun id3Texts(d: ByteArray): List<String> {
        if (d.isEmpty()) return emptyList()
        val body = d.copyOfRange(1, d.size)
        val s = when (d[0].toInt()) {
            1 -> String(body, Charsets.UTF_16)
            2 -> String(body, Charsets.UTF_16BE)
            3 -> String(body, Charsets.UTF_8)
            else -> String(body, Charsets.ISO_8859_1)
        }
        return s.split('\u0000').map { it.trim().removePrefix("﻿") }.filter { it.isNotEmpty() }
    }

    // ---- WAV and AIFF: chunks, the ID3 one read ----

    private fun chunks(s: Bytes, little: Boolean, want: Set<String>, out: MutableMap<String, String>) {
        while (true) {
            val h = try { s.bytes(8) } catch (_: EOFException) { return }
            val id = String(h, 0, 4, Charsets.ISO_8859_1)
            if (id.any { it.code < 32 || it.code > 126 }) return
            val size = if (little) le32(h, 4) else be32(h, 4)
            if (id.equals("id3 ", true) && size in 10..MOST) {
                val d = s.bytes(size.toInt())
                val b = Bytes(ByteArrayInputStream(d))
                id3(b, b.bytes(4), want, out)
                return
            }
            s.skip(size + (size and 1))
        }
    }

    // ---- MP4 / M4A: moov › udta › meta › ilst › "----" items by name, and ©lyr ----

    private fun mp4(s: Bytes, first4: ByteArray, want: Set<String>, out: MutableMap<String, String>) {
        var head = first4 + s.bytes(4)
        if (String(head, 4, 4, Charsets.ISO_8859_1) != "ftyp") return
        val path = listOf("moov", "udta", "meta")
        var depth = 0
        var left = Long.MAX_VALUE   // bytes left in the box we're in (moov, udta), headers counted
        while (true) {
            var size = be32(head, 0); var used = 8L
            if (size == 1L) { size = ByteBuffer.wrap(s.bytes(8)).long; used = 16 }
            else if (size == 0L) size = left   // to the end
            if (size < used || size > left) return
            val type = String(head, 4, 4, Charsets.ISO_8859_1)
            if (type == path[depth] || (type == "meta" && depth > 0)) {   // (some files have moov › meta)
                if (type == "meta") { if (size - used <= MOST) meta(s.bytes((size - used).toInt()), want, out); return }
                depth++; left = size - used
            } else {
                s.skip(size - used)
                left -= size
            }
            if (left < 8) return   // went through the box without finding it
            head = try { s.bytes(8) } catch (_: EOFException) { return }
        }
    }

    /** The meta box's body: its ilst's "----" items by name, and its ©lyr (the lyrics). */
    private fun meta(b: ByteArray, want: Set<String>, out: MutableMap<String, String>) {
        // An iTunes meta box starts with a version and flags; a QuickTime one goes straight to its children.
        val from = if (b.size >= 8 && String(b, 4, 4, Charsets.ISO_8859_1) == "hdlr") 0 else 4
        val ilst = boxes(b, from, b.size).firstOrNull { it.first == "ilst" } ?: return
        fun text(data: Triple<String, Int, Int>) = String(b, data.second + 8, maxOf(0, data.third - data.second - 8), Charsets.UTF_8).trim()
        for ((type, at, end) in boxes(b, ilst.second, ilst.third)) {
            if (type == "\u00A9lyr" && LYRICS in want && LYRICS !in out) {
                boxes(b, at, end).firstOrNull { it.first == "data" }?.let { out[LYRICS] = text(it) }
                continue
            }
            if (type != "----") continue
            val kids = boxes(b, at, end)
            val name = kids.firstOrNull { it.first == "name" }?.let { String(b, it.second + 4, it.third - it.second - 4, Charsets.UTF_8) }?.uppercase() ?: continue
            if (name !in want || name in out) continue
            val data = kids.firstOrNull { it.first == "data" } ?: continue
            out[name] = text(data)
        }
    }

    /** The boxes in b[from, to): (type, where its body starts, where it ends). */
    private fun boxes(b: ByteArray, from: Int, to: Int): List<Triple<String, Int, Int>> {
        val out = ArrayList<Triple<String, Int, Int>>()
        var i = from
        while (i + 8 <= to) {
            val size = be32(b, i).toInt()
            if (size < 8 || i + size > to) break
            out += Triple(String(b, i + 4, 4, Charsets.ISO_8859_1), i + 8, i + size)
            i += size
        }
        return out
    }

    // ---- Ogg (Vorbis, Opus): the second packet, its comments ----

    private fun ogg(s: Bytes, first4: ByteArray, want: Set<String>, out: MutableMap<String, String>) {
        var page = first4
        val packet = java.io.ByteArrayOutputStream()
        var packets = 0
        var serial: Long? = null
        while (true) {
            val h = page + s.bytes(23)   // the rest of the page header
            val segs = h[26].toInt() and 0xff
            val table = s.bytes(segs)
            val ser = le32(h, 14)
            val mine = serial == null || serial == ser
            if (serial == null) serial = ser
            for (lace in table) {
                val n = lace.toInt() and 0xff
                if (!mine) { s.skip(n.toLong()); continue }
                val d = s.bytes(n)
                if (packets == 1) { packet.write(d); if (packet.size() > MOST) return }
                if (n < 255) { packets++; if (packets == 2) { comments(packet.toByteArray())?.let { fromComments(it, want, out) }; return } }
            }
            page = s.bytes(4)
            if (String(page, Charsets.ISO_8859_1) != "OggS") return
        }
    }

    /** A Vorbis or Opus comment packet's comments (keys upper case). */
    private fun comments(p: ByteArray): Map<String, List<String>>? {
        val skip = when {
            p.size > 7 && p[0] == 3.toByte() && String(p, 1, 6, Charsets.ISO_8859_1) == "vorbis" -> 7
            p.size > 8 && String(p, 0, 8, Charsets.ISO_8859_1) == "OpusTags" -> 8
            else -> return null
        }
        val bb = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(skip)
        val vendor = bb.int
        bb.position(bb.position() + vendor)
        val out = LinkedHashMap<String, MutableList<String>>()
        repeat(bb.int) {
            val len = bb.int
            val kv = String(p, bb.position(), len, Charsets.UTF_8)
            bb.position(bb.position() + len)
            out.getOrPut(kv.substringBefore('=').uppercase()) { ArrayList() } += kv.substringAfter('=')
        }
        return out
    }
}
