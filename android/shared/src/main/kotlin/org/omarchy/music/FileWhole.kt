package org.omarchy.music

import java.io.File
import java.io.RandomAccessFile

/** Whether a song file is whole enough to play, checked before a changed copy of it takes the place of yours. */
object FileWhole {
    /** A FLAC: "fLaC", its metadata blocks one after another to the one marked last, and right after them the first
     *  frame of sound (its sync code, 0xFFF8 or 0xFFF9). A player refuses a file where that isn't so ("First frame
     *  does not start with sync code"). Any other kind of file: only that it isn't empty. */
    fun sound(f: File): Boolean = runCatching {
        if (!f.isFile || f.length() <= 0) return@runCatching false
        RandomAccessFile(f, "r").use { r ->
            val magic = ByteArray(4); r.readFully(magic)
            if (String(magic, Charsets.ISO_8859_1) != "fLaC") return@runCatching true   // not a FLAC (or one with a tag in front): not judged here
            var blocks = 0
            while (true) {
                val h = ByteArray(4); r.readFully(h)
                val size = ((h[1].toInt() and 0xff) shl 16) or ((h[2].toInt() and 0xff) shl 8) or (h[3].toInt() and 0xff)
                if (blocks == 0 && ((h[0].toInt() and 0x7f) != 0 || size != 34)) return@runCatching false   // STREAMINFO comes first
                r.seek(r.filePointer + size)
                blocks++
                if (h[0].toInt() and 0x80 != 0) break
                if (blocks > 4096) return@runCatching false
            }
            if (r.filePointer + 2 > r.length()) return@runCatching false
            val a = r.read(); val b = r.read()
            a == 0xff && (b and 0xfe) == 0xf8
        }
    }.getOrDefault(false)

    /** How many bytes of sound the file holds, where that can be told: a FLAC's (what follows its blocks), an MP3's
     *  (what follows its ID3v2 tag, less an ID3v1 "TAG" at its end). Null when it isn't told (another kind of file, a
     *  FLAC behind an ID3 tag, one that can't be read). A tag write changes the tags and never this. */
    fun soundBytes(f: File): Long? = runCatching {
        RandomAccessFile(f, "r").use { r ->
            val len = r.length()
            if (len < 12) return@runCatching null
            val h = ByteArray(10); r.readFully(h)
            val magic = String(h, 0, 4, Charsets.ISO_8859_1)
            if (magic == "fLaC") {
                r.seek(4)
                var blocks = 0
                while (true) {
                    val b = ByteArray(4); r.readFully(b)
                    val size = ((b[1].toInt() and 0xff) shl 16) or ((b[2].toInt() and 0xff) shl 8) or (b[3].toInt() and 0xff)
                    r.seek(r.filePointer + size)
                    if (b[0].toInt() and 0x80 != 0) break
                    if (++blocks > 4096) return@runCatching null
                }
                return@runCatching (len - r.filePointer).takeIf { it >= 0 }
            }
            var from = 0L
            if (magic.startsWith("ID3")) {
                from = 10L + (((h[6].toInt() and 0x7f) shl 21) or ((h[7].toInt() and 0x7f) shl 14) or ((h[8].toInt() and 0x7f) shl 7) or (h[9].toInt() and 0x7f)) +
                    (if (h[5].toInt() and 0x10 != 0) 10 else 0)
                if (from + 4 <= len) { r.seek(from); val m = ByteArray(4); r.readFully(m); if (String(m, Charsets.ISO_8859_1) == "fLaC") return@runCatching null }
            } else if (!((h[0].toInt() and 0xff) == 0xff && (h[1].toInt() and 0xe0) == 0xe0 && (h[1].toInt() and 0x06) != 0)) return@runCatching null
            var end = len
            if (len >= from + 128) { r.seek(len - 128); val t = ByteArray(3); r.readFully(t); if (String(t, Charsets.ISO_8859_1) == "TAG") end -= 128 }
            maxOf(0L, end - from)
        }
    }.getOrNull()

    /** A tag library may tidy a few bytes around the sound (padding it took for sound, a second tag at the end). */
    const val SLACK = 8192L

    /** Whether a file that held [before] bytes of sound still holds them, now that it holds [after]: false when sound
     *  has gone. (Where either isn't told, nothing is said against it.) */
    fun kept(before: Long?, after: Long?): Boolean = before == null || after == null || after >= before - SLACK

    /** The same of a FLAC read from its start (a file the phone only gives as a stream): false only when it is a FLAC
     *  whose blocks don't lead to its sound. */
    fun flacLeadsToSound(input: java.io.InputStream): Boolean = runCatching {
        val d = java.io.DataInputStream(input.buffered())
        val magic = ByteArray(4); d.readFully(magic)
        if (String(magic, Charsets.ISO_8859_1) != "fLaC") return@runCatching true
        var blocks = 0
        while (true) {
            val h = ByteArray(4); d.readFully(h)
            val size = ((h[1].toInt() and 0xff) shl 16) or ((h[2].toInt() and 0xff) shl 8) or (h[3].toInt() and 0xff)
            if (blocks == 0 && ((h[0].toInt() and 0x7f) != 0 || size != 34)) return@runCatching false
            var left = size
            while (left > 0) { val n = d.skipBytes(left); if (n <= 0) return@runCatching false; left -= n }
            blocks++
            if (h[0].toInt() and 0x80 != 0) break
            if (blocks > 4096) return@runCatching false
        }
        val a = d.read(); val b = d.read()
        a == 0xff && (b and 0xfe) == 0xf8
    }.getOrDefault(false)
}
