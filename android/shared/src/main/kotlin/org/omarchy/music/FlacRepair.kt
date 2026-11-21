package org.omarchy.music

import java.io.File
import java.io.RandomAccessFile

/** A FLAC left half-written by an interrupted tag write: its sound is all there, but the blocks in front of it (tags,
 *  cover) no longer lead to it, so no player opens it ("First frame does not start with sync code"). The repair finds
 *  where the sound really starts and puts that right in the blocks: the leading blocks that are still whole are kept,
 *  and what lies between them and the sound is marked as padding. Four bytes are written, none of the music is moved
 *  or changed, and the bytes in front of the sound are saved first, so the file can be put back exactly as it was. */
object FlacRepair {
    /** How far into the file the sound is looked for (a cover is a few megabytes at most). */
    const val LOOK = 48L * 1024 * 1024

    /** What was done: where the sound starts, how many leading blocks were kept, and whether its tags were among them. */
    data class Done(val soundAt: Long, val kept: Int, val tagsKept: Boolean)

    private val CRC8 = IntArray(256) { var c = it; repeat(8) { c = if (c and 0x80 != 0) (c shl 1) xor 0x07 else c shl 1 }; c and 0xff }

    /** Whether b[at…] is the header of the very first frame of a FLAC stream of this many bits a sample: the sync code,
     *  a frame (or sample) number of 0, the right sample size, and a header checksum that is right. */
    internal fun firstFrameAt(b: ByteArray, at: Int, end: Int, bits: Int): Boolean {
        if (at + 6 > end) return false
        if ((b[at].toInt() and 0xff) != 0xff || (b[at + 1].toInt() and 0xfe) != 0xf8) return false
        val b2 = b[at + 2].toInt() and 0xff; val b3 = b[at + 3].toInt() and 0xff
        val block = b2 shr 4; val rate = b2 and 0x0f
        if (block == 0 || rate == 15) return false
        if (b3 and 0x01 != 0 || (b3 shr 4) > 10) return false
        val size = when ((b3 shr 1) and 0x07) { 0 -> bits; 1 -> 8; 2 -> 12; 4 -> 16; 5 -> 20; 6 -> 24; 7 -> 32; else -> -1 }
        if (size != bits) return false
        if ((b[at + 4].toInt() and 0xff) != 0) return false   // frame (or sample) number 0, in one byte
        var n = at + 5
        n += when (block) { 6 -> 1; 7 -> 2; else -> 0 }
        n += when (rate) { 12 -> 1; 13, 14 -> 2; else -> 0 }
        if (n >= end) return false
        var crc = 0
        for (i in at until n) crc = CRC8[crc xor (b[i].toInt() and 0xff)]
        return crc == (b[n].toInt() and 0xff)
    }

    /** Where the sound starts in f (a FLAC), by looking for its first frame; -1 if it isn't found, or f isn't a FLAC
     *  whose first block is its STREAMINFO. */
    fun soundStart(f: File): Long = runCatching {
        RandomAccessFile(f, "r").use { r ->
            val head = ByteArray(42); r.readFully(head)
            if (String(head, 0, 4, Charsets.ISO_8859_1) != "fLaC" || (head[4].toInt() and 0x7f) != 0 || (head[7].toInt() and 0xff) != 34) return@runCatching -1L
            val bits = (((head[20].toInt() and 0x01) shl 4) or ((head[21].toInt() and 0xf0) shr 4)) + 1
            val chunk = ByteArray(1 shl 20)
            var base = 42L
            val stop = minOf(r.length(), LOOK)
            while (base < stop) {
                r.seek(base)
                val got = r.read(chunk, 0, minOf(chunk.size.toLong(), r.length() - base).toInt())
                if (got <= 16) break
                val last = if (base + got >= r.length()) got else got - 16   // (a header across two chunks is seen in the next)
                for (i in 0 until last) if (chunk[i] == 0xff.toByte() && firstFrameAt(chunk, i, got, bits)) return@runCatching base + i
                base += last
            }
            -1L
        }
    }.getOrDefault(-1L)

    /** Put f right if it is a FLAC whose blocks don't lead to its sound. backup: where the bytes in front of the sound
     *  are saved first (as they were). Null when nothing was done: f is whole, isn't a FLAC, or its sound wasn't found. */
    fun repair(f: File, backup: File): Done? {
        if (FileCheck.sound(f)) return null
        val at = soundStart(f)
        if (at < 42 + 4 && at != 42L) return null
        RandomAccessFile(f, "rw").use { r ->
            val front = ByteArray(minOf(at, Int.MAX_VALUE.toLong()).toInt()); r.seek(0); r.readFully(front)
            backup.parentFile?.mkdirs(); backup.writeBytes(front)
            // The blocks after STREAMINFO that are still whole (a known kind, ending before the sound with room for a
            // padding block's own four bytes, or exactly at it)
            var end = 42L; var kept = 1; var tags = false
            var lastHeader = 4L   // where the header of the last block kept is
            while (end + 4 <= at) {
                r.seek(end)
                val h = ByteArray(4); r.readFully(h)
                val type = h[0].toInt() and 0x7f
                val size = ((h[1].toInt() and 0xff) shl 16) or ((h[2].toInt() and 0xff) shl 8) or (h[3].toInt() and 0xff)
                val next = end + 4 + size
                if (type !in 1..6 || (next != at && next + 4 > at)) break
                if (type == 4 && !commentsWhole(r, end + 4, size)) break
                if (type == 4) tags = true
                lastHeader = end; end = next; kept++
                if (h[0].toInt() and 0x80 != 0) { r.seek(lastHeader); r.write(h[0].toInt() and 0x7f) }   // (not the last one any more, unless it ends at the sound)
            }
            r.seek(4); r.write(0)   // (STREAMINFO isn't the last block, unless it is the only one: put right just below)
            if (end == at) {   // the kept blocks end right at the sound: the last of them is marked last
                r.seek(lastHeader); val t = r.read(); r.seek(lastHeader); r.write(t or 0x80)
            } else {           // what lies between is padding, marked last
                val pad = (at - end - 4).toInt()
                r.seek(end)
                r.write(byteArrayOf(0x81.toByte(), (pad shr 16).toByte(), (pad shr 8).toByte(), pad.toByte()))
            }
            r.fd.sync()
            return if (FileCheck.sound(f)) Done(at, kept, tags) else { r.seek(0); r.write(front); null }
        }
    }

    /** A VORBIS_COMMENT block that reads to its end: the vendor's name, then each comment, their lengths adding up. */
    private fun commentsWhole(r: RandomAccessFile, at: Long, size: Int): Boolean = runCatching {
        if (size < 8 || size > 16 * 1024 * 1024) return@runCatching false
        val b = ByteArray(size); r.seek(at); r.readFully(b)
        fun le(i: Int) = (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8) or ((b[i + 2].toInt() and 0xff) shl 16) or ((b[i + 3].toInt() and 0xff) shl 24)
        var p = 0
        val vendor = le(p); p += 4
        if (vendor < 0 || p + vendor + 4 > size) return@runCatching false
        p += vendor
        val count = le(p); p += 4
        if (count < 0 || count > 100_000) return@runCatching false
        repeat(count) { if (p + 4 > size) return@runCatching false; val n = le(p); p += 4; if (n < 0 || p + n > size) return@runCatching false; p += n }
        p == size
    }.getOrDefault(false)
}
