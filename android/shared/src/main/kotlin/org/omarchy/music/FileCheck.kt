package org.omarchy.music

import java.io.File
import java.io.RandomAccessFile

/** Whether a song file is whole enough to play, checked before a changed copy of it takes the place of yours. */
object FileCheck {
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
}
