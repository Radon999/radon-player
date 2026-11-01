package org.omarchy.music

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** A WAV's tags read and written by hand: its "ID3 " chunk (as players read it) and its LIST INFO chunk (as Windows
 *  reads it). For the WAVs the tag library won't take: studio files with chunks of their own (bext, regn, DGDA…) or
 *  a stray byte at the end. Every other chunk is kept as it is; the sound isn't touched. */
object WavTags {
    private val FRAMES = mapOf("title" to "TIT2", "artist" to "TPE1", "albumartist" to "TPE2", "album" to "TALB",
        "tracknumber" to "TRCK", "genre" to "TCON")
    private val INFO = mapOf("title" to "INAM", "artist" to "IART", "album" to "IPRD", "genre" to "IGNR", "date" to "ICRD",
        "tracknumber" to "ITRK")

    private class Chunk(val id: String, val at: Long, val size: Long)   // at: where its data starts

    private fun le32(b: ByteArray, i: Int) = (b[i].toLong() and 0xff) or ((b[i + 1].toLong() and 0xff) shl 8) or
        ((b[i + 2].toLong() and 0xff) shl 16) or ((b[i + 3].toLong() and 0xff) shl 24)
    private fun le32(n: Long) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())

    private fun chunks(f: RandomAccessFile): List<Chunk> {
        val head = ByteArray(12)
        f.seek(0); f.readFully(head)
        if (String(head, 0, 4, Charsets.ISO_8859_1) != "RIFF" || String(head, 8, 4, Charsets.ISO_8859_1) != "WAVE") error("not a WAV file")
        val out = ArrayList<Chunk>()
        var i = 12L
        val len = f.length()
        val h = ByteArray(8)
        while (i + 8 <= len) {
            f.seek(i); f.readFully(h)
            val id = String(h, 0, 4, Charsets.ISO_8859_1)
            if (id.any { it.code < 32 || it.code > 126 }) break   // past the last chunk (a stray byte or two)
            val size = minOf(le32(h, 4), len - i - 8)
            out += Chunk(id, i + 8, size)
            i += 8 + size + (size and 1)
        }
        return out
    }

    private fun bytes(f: RandomAccessFile, c: Chunk): ByteArray = ByteArray(c.size.toInt()).also { f.seek(c.at); f.readFully(it) }

    // ---- ID3 ----

    private class Id3(val major: Int, val frames: MutableList<Triple<String, ByteArray, ByteArray>>)   // id, flags, data

    private fun syncsafe(b: ByteArray, i: Int) = ((b[i].toInt() and 0x7f) shl 21) or ((b[i + 1].toInt() and 0x7f) shl 14) or
        ((b[i + 2].toInt() and 0x7f) shl 7) or (b[i + 3].toInt() and 0x7f)
    private fun be32(b: ByteArray, i: Int) = ((b[i].toInt() and 0xff) shl 24) or ((b[i + 1].toInt() and 0xff) shl 16) or
        ((b[i + 2].toInt() and 0xff) shl 8) or (b[i + 3].toInt() and 0xff)
    private fun syncsafe(n: Int) = byteArrayOf(((n shr 21) and 0x7f).toByte(), ((n shr 14) and 0x7f).toByte(), ((n shr 7) and 0x7f).toByte(), (n and 0x7f).toByte())
    private fun be32(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())

    private fun parseId3(b: ByteArray): Id3? {
        if (b.size < 10 || String(b, 0, 3, Charsets.ISO_8859_1) != "ID3") return null
        val major = b[3].toInt()
        if (major !in 3..4 || b[5].toInt() and 0x80 != 0) return null   // v2.2, or unsynchronised: left alone
        val end = minOf(b.size, 10 + syncsafe(b, 6))
        var i = 10
        if (b[5].toInt() and 0x40 != 0) i += if (major == 4) syncsafe(b, 10) else be32(b, 10) + 4   // extended header
        val frames = ArrayList<Triple<String, ByteArray, ByteArray>>()
        while (i + 10 <= end && b[i] != 0.toByte()) {
            val id = String(b, i, 4, Charsets.ISO_8859_1)
            val size = if (major == 4) syncsafe(b, i + 4) else be32(b, i + 4)
            if (size < 0 || i + 10 + size > end) break
            frames += Triple(id, b.copyOfRange(i + 8, i + 10), b.copyOfRange(i + 10, i + 10 + size))
            i += 10 + size
        }
        return Id3(major, frames)
    }

    private fun text(d: ByteArray): String {
        if (d.isEmpty()) return ""
        val body = d.copyOfRange(1, d.size)
        val s = when (d[0].toInt()) {
            1 -> String(body, Charsets.UTF_16)
            2 -> String(body, Charsets.UTF_16BE)
            3 -> String(body, Charsets.UTF_8)
            else -> String(body, Charsets.ISO_8859_1)
        }
        return s.split('\u0000').map { it.trim().removePrefix("﻿") }.filter { it.isNotEmpty() }.joinToString(", ")
    }

    private fun textFrame(major: Int, s: String): ByteArray =
        if (major == 4) byteArrayOf(3) + s.toByteArray(Charsets.UTF_8)
        else byteArrayOf(1, 0xff.toByte(), 0xfe.toByte()) + s.toByteArray(Charsets.UTF_16LE)

    private fun buildId3(t: Id3): ByteArray {
        val body = java.io.ByteArrayOutputStream()
        for ((id, flags, data) in t.frames) {
            body.write(id.toByteArray(Charsets.ISO_8859_1))
            body.write(if (t.major == 4) syncsafe(data.size) else be32(data.size))
            body.write(flags); body.write(data)
        }
        val b = body.toByteArray()
        return byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), t.major.toByte(), 0, 0) + syncsafe(b.size) + b
    }

    private fun picture(jpeg: ByteArray) = byteArrayOf(0) + "image/jpeg".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 3, 0) + jpeg

    // ---- LIST INFO ----

    private fun parseInfo(b: ByteArray): MutableList<Pair<String, ByteArray>>? {
        if (b.size < 4 || String(b, 0, 4, Charsets.ISO_8859_1) != "INFO") return null
        val out = ArrayList<Pair<String, ByteArray>>()
        var i = 4
        while (i + 8 <= b.size) {
            val id = String(b, i, 4, Charsets.ISO_8859_1)
            val size = le32(b, i + 4).toInt()
            if (size < 0 || i + 8 + size > b.size) break
            out += id to b.copyOfRange(i + 8, i + 8 + size)
            i += 8 + size + (size and 1)
        }
        return out
    }

    private fun infoText(d: ByteArray) = String(d, Charsets.UTF_8).trimEnd('\u0000').trim()

    private fun buildInfo(items: List<Pair<String, ByteArray>>): ByteArray {
        val o = java.io.ByteArrayOutputStream()
        o.write("INFO".toByteArray(Charsets.ISO_8859_1))
        for ((id, d) in items) {
            o.write(id.toByteArray(Charsets.ISO_8859_1)); o.write(le32(d.size.toLong())); o.write(d)
            if (d.size % 2 == 1) o.write(0)
        }
        return o.toByteArray()
    }

    // ---- reading and writing ----

    /** The WAV's tags (title, artist, albumartist, album, tracknumber, date, genre): the ID3 chunk's, else LIST INFO's. */
    fun read(file: File): Map<String, String>? = runCatching {
        RandomAccessFile(file, "r").use { f ->
            val cs = chunks(f)
            val id3 = cs.firstOrNull { it.id.equals("id3 ", true) }?.let { parseId3(bytes(f, it)) }
            val info = cs.filter { it.id == "LIST" }.firstNotNullOfOrNull { parseInfo(bytes(f, it)) }
            val out = LinkedHashMap<String, String>()
            for (k in listOf("title", "artist", "albumartist", "album", "tracknumber", "date", "genre")) {
                val fromId3 = id3?.let { t ->
                    val ids = if (k == "date") listOf("TDRC", "TYER") else listOfNotNull(FRAMES[k])
                    ids.firstNotNullOfOrNull { id -> t.frames.firstOrNull { it.first == id }?.let { text(it.third) }?.ifEmpty { null } }
                }
                val fromInfo = INFO[k]?.let { id -> info?.firstOrNull { it.first == id }?.let { infoText(it.second) }?.ifEmpty { null } }
                out[k] = fromId3 ?: fromInfo ?: ""
            }
            out
        }
    }.getOrNull()

    /** Write changes ("" clears a field) and, if given, a new cover into the WAV, through a copy put in its place. */
    fun write(file: File, changes: Map<String, String>, jpeg: ByteArray? = null) {
        val tmp = File(file.parentFile, ".${file.name}.retag")
        RandomAccessFile(file, "r").use { f ->
            val cs = chunks(f)
            val id3Id = cs.firstOrNull { it.id.equals("id3 ", true) }?.id ?: "id3 "
            val id3 = cs.firstOrNull { it.id.equals("id3 ", true) }?.let { parseId3(bytes(f, it)) } ?: Id3(3, ArrayList())
            val infoChunk = cs.firstOrNull { it.id == "LIST" && parseInfo(bytes(f, it)) != null }
            val info = infoChunk?.let { parseInfo(bytes(f, it)) } ?: ArrayList()
            for ((k, v0) in changes) {
                val v = v0.trim()
                val ids = if (k == "date") listOf("TDRC", "TYER") else listOfNotNull(FRAMES[k])
                if (ids.isNotEmpty()) {
                    val at = id3.frames.indexOfFirst { it.first in ids }.takeIf { it >= 0 } ?: id3.frames.size
                    id3.frames.removeAll { it.first in ids }
                    if (v.isNotEmpty()) id3.frames.add(minOf(at, id3.frames.size),
                        Triple(if (k == "date") (if (id3.major == 4) "TDRC" else "TYER") else ids[0], byteArrayOf(0, 0), textFrame(id3.major, v)))
                }
                INFO[k]?.let { id ->
                    val at = info.indexOfFirst { it.first == id }.takeIf { it >= 0 } ?: info.size
                    info.removeAll { it.first == id }
                    if (v.isNotEmpty()) info.add(minOf(at, info.size), id to (v.toByteArray(Charsets.UTF_8) + 0))
                }
            }
            if (jpeg != null) { id3.frames.removeAll { it.first == "APIC" }; id3.frames.add(0, Triple("APIC", byteArrayOf(0, 0), picture(jpeg))) }
            RandomAccessFile(tmp, "rw").use { o ->
                o.setLength(0)
                o.write("RIFF".toByteArray(Charsets.ISO_8859_1)); o.write(le32(0)); o.write("WAVE".toByteArray(Charsets.ISO_8859_1))
                fun put(id: String, data: ByteArray) {
                    o.write(id.toByteArray(Charsets.ISO_8859_1)); o.write(le32(data.size.toLong())); o.write(data)
                    if (data.size % 2 == 1) o.write(0)
                }
                for (c in cs) {
                    if (c.id.equals("id3 ", true) || c === infoChunk) continue
                    o.write(c.id.toByteArray(Charsets.ISO_8859_1)); o.write(le32(c.size))
                    var done = 0L
                    while (done < c.size) done += f.channel.transferTo(c.at + done, c.size - done, o.channel)
                    o.seek(o.length())
                    if (c.size % 2 == 1L) o.write(0)
                }
                if (info.isNotEmpty()) put("LIST", buildInfo(info))
                if (id3.frames.isNotEmpty()) put(id3Id, buildId3(id3))
                val total = o.length()
                o.seek(4); o.write(le32(total - 8))
            }
        }
        try { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: Exception) { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        finally { tmp.delete() }
    }
}
