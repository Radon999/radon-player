package org.omarchy.music

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Lyrics and our own named tags (MOOD_LYRICS) written into an ID3v2 tag by hand: an MP3's, or the ID3 chunk of a WAV.
 *  The lyrics go in a USLT frame (replacing the one there), a named tag in a TXXX frame of that name (replacing only
 *  that one). Every other frame is kept as it is; the sound isn't touched. (FLACs go through [Flac.retag]; M4As
 *  through the PC's tag library.) */
object TagWrite {
    /** An ID3v2.3/2.4 tag: its version and its frames (id, the two flag bytes, the data). */
    class Id3(val major: Int, val frames: MutableList<Triple<String, ByteArray, ByteArray>>)

    private fun syncsafe(b: ByteArray, i: Int) = ((b[i].toInt() and 0x7f) shl 21) or ((b[i + 1].toInt() and 0x7f) shl 14) or
        ((b[i + 2].toInt() and 0x7f) shl 7) or (b[i + 3].toInt() and 0x7f)
    private fun be32(b: ByteArray, i: Int) = ((b[i].toInt() and 0xff) shl 24) or ((b[i + 1].toInt() and 0xff) shl 16) or
        ((b[i + 2].toInt() and 0xff) shl 8) or (b[i + 3].toInt() and 0xff)
    private fun syncsafe(n: Int) = byteArrayOf(((n shr 21) and 0x7f).toByte(), ((n shr 14) and 0x7f).toByte(), ((n shr 7) and 0x7f).toByte(), (n and 0x7f).toByte())
    private fun be32(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    private fun le32(b: ByteArray, i: Int) = (b[i].toLong() and 0xff) or ((b[i + 1].toLong() and 0xff) shl 8) or
        ((b[i + 2].toLong() and 0xff) shl 16) or ((b[i + 3].toLong() and 0xff) shl 24)
    private fun le32(n: Long) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())

    /** The ID3 tag at the start of b and its whole size (header and footer counted), or null when it's one we leave
     *  alone: ID3v2.2, unsynchronised, or with an extended header. */
    fun parseId3(b: ByteArray): Pair<Id3, Int>? {
        if (b.size < 10 || String(b, 0, 3, Charsets.ISO_8859_1) != "ID3") return null
        val major = b[3].toInt()
        val flags = b[5].toInt() and 0xff
        if (major !in 3..4 || flags and 0xC0 != 0) return null
        val size = 10 + syncsafe(b, 6) + (if (flags and 0x10 != 0) 10 else 0)
        val end = minOf(b.size, 10 + syncsafe(b, 6))
        val frames = ArrayList<Triple<String, ByteArray, ByteArray>>()
        var i = 10
        while (i + 10 <= end && b[i] != 0.toByte()) {
            val id = String(b, i, 4, Charsets.ISO_8859_1)
            val fs = if (major == 4) syncsafe(b, i + 4) else be32(b, i + 4)
            if (fs < 0 || i + 10 + fs > end) break
            frames += Triple(id, b.copyOfRange(i + 8, i + 10), b.copyOfRange(i + 10, i + 10 + fs))
            i += 10 + fs
        }
        return Id3(major, frames) to size
    }

    /** The tag as bytes, with some padding (for tags added later). */
    fun buildId3(t: Id3, padding: Int = 1024): ByteArray {
        val body = ByteArrayOutputStream()
        for ((id, flags, data) in t.frames) {
            body.write(id.toByteArray(Charsets.ISO_8859_1))
            body.write(if (t.major == 4) syncsafe(data.size) else be32(data.size))
            body.write(flags); body.write(data)
        }
        body.write(ByteArray(padding))
        val b = body.toByteArray()
        return byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), t.major.toByte(), 0, 0) + syncsafe(b.size) + b
    }

    /** A string in the frame's encoding (UTF-8 in ID3v2.4, UTF-16 with its mark in 2.3), and its end. */
    private fun str(major: Int, s: String, end: Boolean): ByteArray =
        if (major == 4) s.toByteArray(Charsets.UTF_8) + (if (end) byteArrayOf(0) else ByteArray(0))
        else byteArrayOf(0xff.toByte(), 0xfe.toByte()) + s.toByteArray(Charsets.UTF_16LE) + (if (end) byteArrayOf(0, 0) else ByteArray(0))

    private fun enc(major: Int): Byte = if (major == 4) 3 else 1

    /** A TXXX frame's name (its description). */
    private fun txxxName(d: ByteArray): String {
        if (d.isEmpty()) return ""
        val body = d.copyOfRange(1, d.size)
        val s = when (d[0].toInt()) {
            1 -> String(body, Charsets.UTF_16)
            2 -> String(body, Charsets.UTF_16BE)
            3 -> String(body, Charsets.UTF_8)
            else -> String(body, Charsets.ISO_8859_1)
        }
        return s.substringBefore('\u0000').removePrefix("﻿").trim()
    }

    /** Set the lyrics (null: leave them; "": take them off) and named tags ("" takes one off) in the tag. */
    fun set(t: Id3, lyrics: String?, named: Map<String, String>) {
        if (lyrics != null) {
            val at = t.frames.indexOfFirst { it.first == "USLT" }.takeIf { it >= 0 } ?: t.frames.size
            t.frames.removeAll { it.first == "USLT" }
            if (lyrics.isNotBlank()) {
                val d = byteArrayOf(enc(t.major)) + "eng".toByteArray(Charsets.ISO_8859_1) + str(t.major, "", true) + str(t.major, lyrics, false)
                t.frames.add(minOf(at, t.frames.size), Triple("USLT", byteArrayOf(0, 0), d))
            }
        }
        for ((k, v) in named) {
            val at = t.frames.indexOfFirst { it.first == "TXXX" && txxxName(it.third).equals(k, true) }.takeIf { it >= 0 } ?: t.frames.size
            t.frames.removeAll { it.first == "TXXX" && txxxName(it.third).equals(k, true) }
            if (v.isNotBlank()) {
                val d = byteArrayOf(enc(t.major)) + str(t.major, k, true) + str(t.major, v.trim(), false)
                t.frames.add(minOf(at, t.frames.size), Triple("TXXX", byteArrayOf(0, 0), d))
            }
        }
    }

    /** An MP3's bytes with the lyrics and named tags set (a new ID3v2.3 tag in front when it has none), or null when its
     *  tag is one we leave alone. */
    fun mp3(all: ByteArray, lyrics: String?, named: Map<String, String>): ByteArray? {
        val (tag, from) = if (all.size >= 3 && String(all, 0, 3, Charsets.ISO_8859_1) == "ID3") (parseId3(all) ?: return null)
            else Id3(3, ArrayList()) to 0
        if (from > all.size) return null
        set(tag, lyrics, named)
        return buildId3(tag) + all.copyOfRange(from, all.size)
    }

    /** A WAV with the lyrics and named tags set in its ID3 chunk (made if it has none), written through a copy put in
     *  its place; every other chunk kept as it is. False if it isn't a WAV we can read, or its tag is one we leave alone. */
    fun wav(file: File, lyrics: String?, named: Map<String, String>): Boolean {
        val tmp = File(file.parentFile, ".${file.name}.retag")
        try {
        RandomAccessFile(file, "r").use { f ->
            val len = f.length()
            if (len < 12) return false
            val head = ByteArray(12).also { f.readFully(it) }
            if (String(head, 0, 4, Charsets.ISO_8859_1) != "RIFF" || String(head, 8, 4, Charsets.ISO_8859_1) != "WAVE") return false
            // The chunks: (id, where its data starts, its size); a stray byte at the end, or a last chunk cut short, is let be.
            val chunks = ArrayList<Triple<String, Long, Long>>()
            var pos = 12L
            while (pos + 8 <= len) {
                val h = ByteArray(8); f.seek(pos); f.readFully(h)
                val id = String(h, 0, 4, Charsets.ISO_8859_1)
                if (id.any { it.code < 32 || it.code > 126 }) break
                val size = minOf(le32(h, 4), len - pos - 8)
                chunks += Triple(id, pos + 8, size)
                pos += 8 + size + (size and 1)
            }
            if (chunks.none { it.first == "fmt " }) return false
            val old = chunks.firstOrNull { it.first.equals("id3 ", true) }
            val tag = if (old == null) Id3(3, ArrayList()) else {
                val b = ByteArray(old.third.toInt()); f.seek(old.second); f.readFully(b)
                parseId3(b)?.first ?: return false
            }
            set(tag, lyrics, named)
            val id3 = buildId3(tag, padding = 0)
            RandomAccessFile(tmp, "rw").use { o ->
                o.setLength(0)
                o.write("RIFF".toByteArray(Charsets.ISO_8859_1)); o.write(le32(0)); o.write("WAVE".toByteArray(Charsets.ISO_8859_1))
                for ((id, at, size) in chunks) {
                    if (id.equals("id3 ", true)) continue
                    o.write(id.toByteArray(Charsets.ISO_8859_1)); o.write(le32(size))
                    var done = 0L
                    while (done < size) {
                        val n = f.channel.transferTo(at + done, size - done, o.channel)
                        if (n <= 0) break
                        done += n
                    }
                    o.seek(o.length())
                    if (size % 2 == 1L) o.write(0)
                }
                if (tag.frames.isNotEmpty()) {
                    o.write((old?.first ?: "id3 ").toByteArray(Charsets.ISO_8859_1)); o.write(le32(id3.size.toLong())); o.write(id3)
                    if (id3.size % 2 == 1) o.write(0)
                }
                val total = o.length()
                o.seek(4); o.write(le32(total - 8))
            }
        }
        } catch (e: Exception) { tmp.delete(); throw e }
        try { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: Exception) { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        finally { tmp.delete() }
        return true
    }
}
