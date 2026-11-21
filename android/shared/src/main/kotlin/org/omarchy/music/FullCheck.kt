package org.omarchy.music

import java.io.InputStream

/** The full check of one song file (Storage and damaged files › Full check): every byte of it read, start to end, and
 *  what it is made of followed all the way through, so damage anywhere inside shows, not only at its start.
 *  - a FLAC: every frame of sound has a checksum, and each is worked out and compared; the samples are counted
 *    against what the file says it holds;
 *  - an MP3: its frames are followed one after another; a stretch that isn't music (zeros, part of a picture) shows;
 *  - an Ogg or Opus: every page's checksum is worked out, and the pages counted;
 *  - a WAV, an M4A: their parts must fit the file exactly (their sound has no checksums, so it can't be checked itself).
 *  Nothing is changed. Null: nothing wrong found. */
object FullCheck {
    /** A window that moves along the file. */
    private class Win(val s: InputStream) {
        val b = ByteArray(1 shl 18); var at = 0; var end = 0; var base = 0L; var eof = false
        val pos get() = base + at
        /** At least n bytes from [at] made ready (fewer only at the file's end); how many there are. */
        fun need(n: Int): Int {
            if (end - at >= n || eof) return end - at
            System.arraycopy(b, at, b, 0, end - at); base += at; end -= at; at = 0
            while (end < b.size) { val r = s.read(b, end, b.size - end); if (r < 0) { eof = true; break }; end += r }
            return end - at
        }
        /** n bytes passed over (read, every one); how many there were. */
        fun skip(n: Long): Long {
            var left = n
            while (left > 0) { val have = need(1); if (have == 0) break; val k = minOf(left, have.toLong()).toInt(); at += k; left -= k }
            return n - left
        }
        fun u(i: Int) = b[at + i].toInt() and 0xff
        fun str(i: Int, n: Int) = String(b, at + i, n, Charsets.ISO_8859_1)
        fun be32(i: Int) = (u(i).toLong() shl 24) or (u(i + 1).toLong() shl 16) or (u(i + 2).toLong() shl 8) or u(i + 3).toLong()
        fun le32(i: Int) = u(i).toLong() or (u(i + 1).toLong() shl 8) or (u(i + 2).toLong() shl 16) or (u(i + 3).toLong() shl 24)
    }

    /** stop: asked now and then; when it says yes the check ends with nothing said. */
    fun check(input: InputStream, stop: () -> Boolean = { false }, spots: MutableList<Double>? = null): String? {
        val all = ArrayList<Double>()
        val said = look(input, stop, all)
        if (said != null) spots?.addAll(all)
        return said
    }

    /** "about 0:20 in", or of several: "in 3 places (about 0:20, 1:31 and 3:28 in)"; at most eight are named. */
    internal fun places(spots: List<Double>): String = when {
        spots.size <= 1 -> "about ${fmtTime(spots.firstOrNull() ?: 0.0)} in"
        else -> spots.take(8).map { fmtTime(it) }.let { t ->
            "in ${spots.size} places (about " + (if (spots.size > 8) t.joinToString(", ") + "…" else t.dropLast(1).joinToString(", ") + " and " + t.last()) + " in)" }
    }

    /** A damaged spot noted: one for each stretch (two within two seconds of each other are one). */
    private fun note(spots: MutableList<Double>, at: Double) { if (spots.isEmpty() || at - spots.last() > 2.0) spots += at }

    private fun look(input: InputStream, stop: () -> Boolean, spots: MutableList<Double>): String? {
        val w = Win(input)
        if (w.need(12) < 12) return "it is too small to be a song"
        // ID3 tags in front (an MP3's; some taggers put one in front of a FLAC)
        var tagged = false
        while (w.need(10) >= 10 && w.str(0, 3) == "ID3") {
            val size = 10L + ((w.u(6) and 0x7f) shl 21 or ((w.u(7) and 0x7f) shl 14) or ((w.u(8) and 0x7f) shl 7) or (w.u(9) and 0x7f)) + (if (w.u(5) and 0x10 != 0) 10 else 0)
            if (w.skip(size) < size) return "its tags run past the end of the file: it is cut short"
            tagged = true
        }
        if (w.need(12) < 4) return "it has tags and no music after them"
        return when {
            w.str(0, 4) == "fLaC" -> flac(w, stop, spots)
            tagged -> mp3(w, stop, spots)
            w.str(0, 4) == "RIFF" && w.need(12) >= 12 && w.str(8, 4) == "WAVE" -> wav(w)
            w.str(0, 4) == "OggS" -> ogg(w, stop)
            w.need(12) >= 8 && w.str(4, 4) == "ftyp" -> mp4(w)
            w.u(0) == 0xff && (w.u(1) and 0xe0) == 0xe0 -> mp3(w, stop, spots)
            else -> "it isn't a music file the app knows"
        }
    }

    /** For trying the check on real files, from a terminal: every music file under the folders (or files) given, and
     *  what is said of each that has something wrong. */
    @JvmStatic fun main(args: Array<String>) {
        val kinds = setOf("flac", "mp3", "wav", "m4a", "mp4", "ogg", "opus", "oga")
        var n = 0; var bad = 0; var bytes = 0L
        val t0 = System.currentTimeMillis()
        for (a in args) java.io.File(a).walkTopDown().filter { it.isFile && it.extension.lowercase() in kinds }.forEach { f ->
            n++; bytes += f.length()
            val said = runCatching { f.inputStream().use { check(it) } }.getOrElse { "couldn't be read: $it" }
            if (said != null) { bad++; println("${f.path}: $said") }
        }
        println("$n files, ${bytes shr 20} MB, $bad with something said, ${(System.currentTimeMillis() - t0) / 1000} s")
    }

    // ---- MP3 ----

    private val BITRATES = arrayOf(
        intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448),   // MPEG 1, layer I
        intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384),      // MPEG 1, layer II
        intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320),       // MPEG 1, layer III
        intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256),      // MPEG 2, layer I
        intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160))           // MPEG 2, layers II and III
    private val RATES = arrayOf(intArrayOf(11025, 12000, 8000), intArrayOf(), intArrayOf(22050, 24000, 16000), intArrayOf(44100, 48000, 32000))

    /** An MPEG audio frame header at b[i]: (its length in bytes, its kind (version, layer, sample rate), samples, sample
     *  rate), or null when it isn't one. */
    internal fun mp3Frame(b: ByteArray, i: Int): IntArray? {
        val b0 = b[i].toInt() and 0xff; val b1 = b[i + 1].toInt() and 0xff; val b2 = b[i + 2].toInt() and 0xff
        if (b0 != 0xff || (b1 and 0xe0) != 0xe0) return null
        val ver = (b1 shr 3) and 3; val layer = (b1 shr 1) and 3   // ver 3: MPEG 1, 2: MPEG 2, 0: 2.5; layer 3: I, 2: II, 1: III
        val br = b2 shr 4; val sr = (b2 shr 2) and 3; val pad = (b2 shr 1) and 1
        if (ver == 1 || layer == 0 || br == 0 || br == 15 || sr == 3) return null
        val rate = RATES[ver][sr]
        val kbps = BITRATES[if (ver == 3) 3 - layer else if (layer == 3) 3 else 4][br]
        val (len, samples) = when {
            layer == 3 -> (12 * kbps * 1000 / rate + pad) * 4 to 384
            layer == 2 || ver == 3 -> 144 * kbps * 1000 / rate + pad to 1152
            else -> 72 * kbps * 1000 / rate + pad to 576
        }
        if (len < 24) return null
        return intArrayOf(len, ver * 100 + layer * 10 + sr, samples, rate)
    }

    private const val MP3_MIDDLE = 2048L          // this much that isn't music, with music after it: damage
    private const val MP3_STRETCH = 64L           // one stretch of it this long is a place to listen to
    private const val MP3_END = 2L shl 20         // a tag at the end (an APE one with a cover) can be big; more than this isn't one

    private fun mp3(w: Win, stop: () -> Boolean, spots: MutableList<Double>): String? {
        var frames = 0L; var kind = -1; var secs = 0.0
        var run = 0L        // bytes that aren't music since the last frame
        var middle = 0L
        var n = 0
        while (true) {
            if (++n and 0xfff == 0 && stop()) return null
            if (w.need(4) < 4) { run += w.need(1); break }
            val cand = mp3Frame(w.b, w.at)?.takeIf { kind < 0 || it[1] == kind }
            // The first frame, and the first after a stretch that isn't music: the one after it must be a frame too
            // (two bytes of a picture can look like a frame's start)
            val f = if (cand != null && (frames == 0L || run > 0)) {
                val have = w.need(cand[0] + 4)
                if (have >= cand[0] + 4 && mp3Frame(w.b, w.at + cand[0])?.takeIf { it[1] == cand[1] } == null) null else cand
            } else cand
            if (f == null) { w.at++; run++; continue }
            if (run > 0) { if (frames > 0) { middle += run; if (run >= MP3_STRETCH) note(spots, secs) }; run = 0 }
            kind = f[1]; frames++; secs += f[2].toDouble() / f[3]
            w.skip(f[0].toLong())
        }
        return when {
            frames == 0L -> "no MP3 sound was found in it"
            middle > MP3_MIDDLE -> "${SupportRules.size(middle)} inside it isn't music" + (if (spots.isNotEmpty()) " (${places(spots)})" else "") + ": the sound there was lost or overwritten"
            run > MP3_END -> { note(spots, secs); "its last ${SupportRules.size(run)} isn't music (after ${fmtTime(secs)} of sound): the sound there was lost or overwritten" }
            else -> null
        }
    }

    // ---- FLAC ----

    private val CRC8 = IntArray(256) { var c = it; repeat(8) { c = if (c and 0x80 != 0) (c shl 1) xor 0x07 else c shl 1 }; c and 0xff }
    private val CRC16 = IntArray(256) { var c = it shl 8; repeat(8) { c = if (c and 0x8000 != 0) (c shl 1) xor 0x8005 else c shl 1 }; c and 0xffff }

    /** A FLAC frame header at b[i] (end: where b's bytes stop): its block size in samples, or -1 when it isn't one (the
     *  sync code, every field one a frame can have, and its own checksum right). */
    internal fun flacFrame(b: ByteArray, i: Int, end: Int): Int {
        if (i + 6 > end) return -1
        if ((b[i].toInt() and 0xff) != 0xff || (b[i + 1].toInt() and 0xfe) != 0xf8) return -1
        val b2 = b[i + 2].toInt() and 0xff; val b3 = b[i + 3].toInt() and 0xff
        val block = b2 shr 4; val rate = b2 and 0x0f
        if (block == 0 || rate == 15) return -1
        if (b3 and 0x01 != 0 || (b3 shr 4) > 10 || ((b3 shr 1) and 7) == 3) return -1
        val first = b[i + 4].toInt() and 0xff   // the frame's (or sample's) number, written as UTF-8 is
        val more = when { first < 0x80 -> 0; first < 0xc0 -> return -1; first < 0xe0 -> 1; first < 0xf0 -> 2; first < 0xf8 -> 3; first < 0xfc -> 4; first < 0xfe -> 5; first == 0xfe -> 6; else -> return -1 }
        var n = i + 5 + more
        val extra = (if (block == 6) 1 else if (block == 7) 2 else 0) + (if (rate == 12) 1 else if (rate >= 13) 2 else 0)
        if (n + extra + 1 > end) return -1
        for (k in i + 5 until n) if ((b[k].toInt() and 0xc0) != 0x80) return -1
        val size = when (block) {
            1 -> 192
            in 2..5 -> 576 shl (block - 2)
            6 -> { val v = (b[n].toInt() and 0xff) + 1; n += 1; v }
            7 -> { val v = (((b[n].toInt() and 0xff) shl 8) or (b[n + 1].toInt() and 0xff)) + 1; n += 2; v }
            else -> 256 shl (block - 8)
        }
        n += when (rate) { 12 -> 1; 13, 14 -> 2; else -> 0 }
        if (n >= end) return -1
        var crc = 0
        for (k in i until n) crc = CRC8[crc xor (b[k].toInt() and 0xff)]
        return if (crc == (b[n].toInt() and 0xff)) size else -1
    }

    /** Where in the song the frame whose header is at b[i] starts, in samples: its number, counted in frames of
     *  [block] samples in a file whose frames are all one size, in samples in one whose frames vary. */
    private fun flacAt(b: ByteArray, i: Int, block: Int): Long {
        val first = b[i + 4].toInt() and 0xff
        val more = when { first < 0x80 -> 0; first < 0xe0 -> 1; first < 0xf0 -> 2; first < 0xf8 -> 3; first < 0xfc -> 4; first < 0xfe -> 5; else -> 6 }
        var n = (if (more == 0) first else (first and (0x3f shr more))).toLong()
        for (k in 1..more) n = (n shl 6) or (b[i + 4 + k].toLong() and 0x3f)
        return if (b[i + 1].toInt() and 1 != 0) n else n * block
    }

    private fun flac(w: Win, stop: () -> Boolean, spots: MutableList<Double>): String? {
        if (w.need(42) < 42 || (w.u(4) and 0x7f) != 0 || w.be32(4) and 0xffffff != 34L) return "it is a FLAC without its first block (what says how its sound is stored)"
        val maxBlock = (w.u(10) shl 8) or w.u(11)
        val fixed = (w.u(8) shl 8) or w.u(9)   // (the size of every frame, in a file whose frames are all one size)
        val maxFrame = (w.u(15) shl 16) or (w.u(16) shl 8) or w.u(17)
        val rate = (w.u(18) shl 12) or (w.u(19) shl 4) or (w.u(20) shr 4)
        val ch = ((w.u(20) shr 1) and 7) + 1
        val bits = (((w.u(20) and 1) shl 4) or (w.u(21) shr 4)) + 1
        val total = ((w.u(21) and 0x0f).toLong() shl 32) or w.be32(22)
        w.skip(4)
        var blocks = 0
        while (true) {   // its blocks (tags, cover, seek table…) one after another to the one marked last
            if (w.need(4) < 4) return "its tags run past the end of the file: it is cut short"
            val last = w.u(0) and 0x80 != 0; val size = w.be32(0) and 0xffffff
            if (w.skip(4 + size) < 4 + size) return "its tags run past the end of the file: it is cut short"
            if (last) break
            if (++blocks > 4096) return "its tags can't be followed to its music"
        }
        if (w.need(16) < 6 || flacFrame(w.b, w.at, w.end) < 0) return "its tags and cover no longer lead to its music (a tag write that was cut short)"
        // Frames: each ends with a checksum of itself, so a sum run over a whole frame comes to 0. A new frame's header
        // where the sum is 0 is where the last one ended; a frame that goes on past the biggest one can be is damaged.
        val limit = maxOf(maxFrame, maxOf(maxBlock, 4096) * ch * ((bits + 7) / 8)) + 16384L
        fun at(samples: Long) = if (rate > 0) fmtTime(samples.toDouble() / rate) else "?"
        var good = 0L                                   // samples in the frames that checked out
        var size = flacFrame(w.b, w.at, w.end)
        var from = flacAt(w.b, w.at, fixed)             // where the frame being read starts, in samples
        var crc = 0; var len = 0L
        var zeroAt = -1L   // how far into the last frame its sum was last 0 (what follows a whole last frame is a tag)
        var lost = false   // after a frame that didn't check out: looking for where the next one starts
        var sound = true   // the frame before this one checked out (or this is the first): a fault here is a new place
        var endBad = false
        var n = 0
        while (true) {
            if (++n and 0x3f == 0 && stop()) return null
            val have = w.need(17)   // (16: the longest a frame's header can be, looked at from any byte)
            if (have == 0) break
            val lim = if (w.eof) w.end else w.end - 16
            var i = w.at
            val b = w.b
            while (i < lim) {
                val v = b[i].toInt() and 0xff
                if (lost) {
                    val next = if (v == 0xff) flacFrame(b, i, w.end) else -1
                    if (next < 0) { i++; continue }
                    lost = false; size = next; from = flacAt(b, i, fixed); crc = 0; len = 0; zeroAt = -1
                } else if (crc == 0 && len >= 10) {
                    zeroAt = len
                    if (v == 0xff) {
                        val next = flacFrame(b, i, w.end)
                        if (next >= 0) { good += size; sound = true; size = next; from = flacAt(b, i, fixed); len = 0; zeroAt = -1 }
                    }
                }
                crc = CRC16[((crc shr 8) xor v) and 0xff] xor ((crc shl 8) and 0xffff)
                len++; i++
                if (len > limit) {   // a frame that goes on past the biggest one can be: damaged
                    if (sound && rate > 0) note(spots, from.toDouble() / rate)
                    sound = false; lost = true; len = 0; crc = 0
                }
            }
            w.at = i
        }
        if (!lost) {
            if (crc == 0 && len >= 10) good += size                      // the last frame, whole
            else if (zeroAt >= 10 && len - zeroAt <= 4096) good += size   // …with a small tag after it
            else { endBad = true; if (sound && rate > 0) note(spots, from.toDouble() / rate) }
        }
        return when {
            endBad && spots.size <= 1 -> "its sound is damaged or cut short at its end (about ${at(good)} in)"
            spots.isNotEmpty() -> "its sound is damaged ${places(spots)} (${if (spots.size == 1) "a frame there doesn't" else "frames there don't"} check out)"
            lost || endBad -> "its sound is damaged (a frame doesn't check out)"
            total > 0 && good < total -> "it holds ${at(good)} of the ${at(total)} it says it has: the rest is missing"
            else -> null
        }
    }

    // ---- WAV ----

    private fun wav(w: Win): String? {
        w.skip(12)
        var fmt = false; var data = false
        while (true) {
            val have = w.need(8)
            if (have == 0) break
            if (have < 8) { if (data) break; return "it ends in the middle of one of its parts" }
            val id = w.str(0, 4); val size = w.le32(4)
            if (id.any { it.code < 32 || it.code > 126 }) return if (data && fmt) null else "it can't be followed to its sound (${w.pos} bytes in)"
            w.skip(8)
            if (id == "fmt ") fmt = true
            if (id == "data") {
                data = true
                if (size == 0L || size == 0xffffffffL) { while (w.need(1) > 0) w.skip(w.need(1).toLong()); break }   // a recording never closed: to the end
                val got = w.skip(size)
                if (got < size) return "it is cut short: ${SupportRules.size(size - got)} of its sound is missing"
                if (size and 1L == 1L) w.skip(1)
                continue
            }
            val all = size + (size and 1)
            if (w.skip(all) < size) return "it is cut short (its “${id.trim()}” part runs past the end of the file)"
        }
        return when { !fmt -> "it is a WAV that doesn't say how its sound is stored"; !data -> "it is a WAV with no sound in it"; else -> null }
    }

    // ---- Ogg (Vorbis, Opus) ----

    private val CRC32 = IntArray(256) { var c = it shl 24; repeat(8) { c = if (c < 0) (c shl 1) xor 0x04c11db7 else c shl 1 }; c }

    private fun ogg(w: Win, stop: () -> Boolean): String? {
        var pages = 0L; var junk = 0L
        val seqs = HashMap<Long, Long>()
        while (true) {
            if (pages and 0x3f == 0L && stop()) return null
            val have = w.need(27)
            if (have == 0) break
            if (have < 27) { junk += have; break }
            if (w.str(0, 4) != "OggS" || w.u(4) != 0) { w.at++; junk++; if (junk > 65536) return "it can't be followed after its page $pages (what follows isn't music)"; continue }
            if (junk > 0 && pages > 0) return "${SupportRules.size(junk)} inside it isn't music (after its page $pages)"
            val segs = w.u(26)
            if (w.need(27 + segs) < 27 + segs) return "it is cut short (in its page ${pages + 1})"
            var body = 0
            for (k in 0 until segs) body += w.u(27 + k)
            val all = 27 + segs + body
            if (w.need(all) < all) return "it is cut short (in its page ${pages + 1})"
            var crc = 0
            for (k in 0 until all) { val v = if (k in 22..25) 0 else w.u(k); crc = (crc shl 8) xor CRC32[((crc ushr 24) xor v) and 0xff] }
            if (crc.toLong() and 0xffffffffL != w.le32(22)) return "its sound is damaged (its page ${pages + 1} doesn't check out)"
            val serial = w.le32(14); val seq = w.le32(18)
            seqs[serial]?.let { if (seq != it + 1) return "part of it is missing (after its page $pages)" }
            seqs[serial] = seq
            pages++
            w.skip(all.toLong())
        }
        return if (pages == 0L) "no sound was found in it" else null
    }

    // ---- M4A / MP4 ----

    private fun mp4(w: Win): String? {
        var moov = false; var mdat = false
        while (true) {
            val have = w.need(16)
            if (have == 0) break
            if (have < 8) return if (moov && mdat) null else "it ends in the middle of one of its parts"
            var size = w.be32(0); val type = w.str(4, 4); var head = 8L
            if (type.any { it.code < 32 || it.code > 126 }) return if (moov && mdat) null else "it can't be followed to its sound (${w.pos} bytes in)"
            if (size == 1L) { if (have < 16) return "it ends in the middle of one of its parts"; size = (w.be32(8) shl 32) or w.be32(12); head = 16 }
            if (type == "moov") moov = true
            if (type == "mdat") mdat = true
            if (size == 0L) { while (w.need(1) > 0) w.skip(w.need(1).toLong()); break }   // to the end of the file
            if (size < head) return "it can't be followed to its sound (${w.pos} bytes in)"
            val got = w.skip(size)
            if (got < size) return "it is cut short: ${SupportRules.size(size - got)} of its “$type” part is missing"
        }
        return when { !mdat -> "it is an M4A with no sound in it"; !moov -> "it is an M4A without the part that says how to play it"; else -> null }
    }
}
