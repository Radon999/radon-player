package org.omarchy.music

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Mood and lyrics writes change only the mood and lyrics: a song's title, artist, album and album artist (what Fix
 *  albums and artists put there) and every other tag stay exactly as they were. */
class TagKeepTest {
    private fun le(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())
    private fun be(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    private fun ss(n: Int) = byteArrayOf(((n shr 21) and 0x7f).toByte(), ((n shr 14) and 0x7f).toByte(), ((n shr 7) and 0x7f).toByte(), (n and 0x7f).toByte())

    /** An ID3 text frame (ISO-8859-1), its size written syncsafe (v2.4), plain (v2.3), or plain in a v2.4 tag as old iTunes did. */
    private fun text(id: String, s: String, size: (Int) -> ByteArray) = (byteArrayOf(0) + s.toByteArray(Charsets.ISO_8859_1)).let {
        id.toByteArray() + size(it.size) + byteArrayOf(0, 0) + it
    }
    private fun id3(major: Int, frames: ByteArray, padding: Int = 0) =
        "ID3".toByteArray() + byteArrayOf(major.toByte(), 0, 0) + ss(frames.size + padding) + frames + ByteArray(padding)

    /** The text of the frame id in the tag at the start of b ("" none). */
    private fun frame(b: ByteArray, id: String): String {
        val f = TagWrite.parseId3(b)!!.first.frames.firstOrNull { it.first == id } ?: return ""
        val cs = when (f.third[0].toInt()) { 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1 }
        return String(f.third, 1, f.third.size - 1, cs).trimEnd('\u0000')
    }

    private val sound = ByteArray(3000) { -1 }
    private val core = listOf("TIT2" to "Song", "TPE1" to "Quinn XCII", "TPE2" to "Quinn XCII", "TALB" to "A Letter to My Younger Self", "TRCK" to "4")

    @Test fun mp3KeepsEveryOtherFrame() {
        for (major in 3..4) {
            val frames = core.map { (id, s) -> text(id, s, if (major == 4) ::ss else ::be) }.reduce(ByteArray::plus) +
                text("APIC", "x".repeat(300), if (major == 4) ::ss else ::be)   // a big frame (sizes over 127 bytes)
            val mp3 = id3(major, frames, padding = 50) + sound
            val w = TagWrite.mp3(mp3, "the words", mapOf(MoodTag.MOOD_LYRICS to "Sad;0.80;by=words"), mood = "Chill")!!
            for ((id, s) in core) assertEquals(s, frame(w, id))
            val old = TagWrite.parseId3(mp3)!!.first.frames
            val now = TagWrite.parseId3(w)!!.first.frames
            for (f in old) assertTrue(now.any { it.first == f.first && it.third.contentEquals(f.third) })   // byte for byte
            val tags = MoodTag.readTags(ByteArrayInputStream(w), setOf(MoodTag.MOOD, MoodTag.MOOD_LYRICS, MoodTag.LYRICS))!!
            assertEquals("Chill", tags[MoodTag.MOOD]); assertEquals("the words", tags[MoodTag.LYRICS])
            assertTrue(w.copyOfRange(w.size - sound.size, w.size).contentEquals(sound))
            // The mood changed again: one mood frame, the new one.
            val w2 = TagWrite.mp3(w, null, emptyMap(), mood = "Party")!!
            assertEquals("Party", MoodTag.read(ByteArrayInputStream(w2)))
            assertEquals(TagWrite.parseId3(w)!!.first.frames.size, TagWrite.parseId3(w2)!!.first.frames.size)
        }
    }

    @Test fun oldItunesSizesAreReadWholeNotCut() {
        // An ID3v2.4 tag with plain (not syncsafe) frame sizes: read the syncsafe way the big frame would end early and
        // every frame after it (the artist, the album) would be lost. Read the plain way, all of it is kept.
        val frames = text("APIC", "x".repeat(300), ::be) + core.map { (id, s) -> text(id, s, ::be) }.reduce(ByteArray::plus)
        val w = TagWrite.mp3(id3(4, frames, padding = 20) + sound, "words", emptyMap())!!
        for ((id, s) in core) assertEquals(s, frame(w, id))
    }

    @Test fun aTagThatCantBeReadWholeIsLeftAlone() {
        val frames = core.map { (id, s) -> text(id, s, ::be) }.reduce(ByteArray::plus)
        val broken = frames.copyOf().also { it[20] = 0x7f }   // the second frame's size, nonsense
        assertNull(TagWrite.mp3(id3(3, broken) + sound, "words", emptyMap()))
    }

    @Test fun anMp3WithOnlyAnId3v1TagKeepsItsNames() {
        fun pad(s: String, n: Int) = s.toByteArray(Charsets.ISO_8859_1).copyOf(n)
        val v1 = "TAG".toByteArray() + pad("Song", 30) + pad("Quinn XCII", 30) + pad("The Album", 30) + pad("2019", 4) +
            ByteArray(28) + byteArrayOf(0, 7) + byteArrayOf(13)
        val w = TagWrite.mp3(sound + v1, "words", emptyMap())!!
        assertEquals("Song", frame(w, "TIT2")); assertEquals("Quinn XCII", frame(w, "TPE1")); assertEquals("The Album", frame(w, "TALB"))
        assertEquals("7", frame(w, "TRCK"))
        assertTrue(w.copyOfRange(w.size - 128, w.size).contentEquals(v1))   // the ID3v1 tag stays too
    }

    @Test fun flacChangesOnlyTheNamedComments() {
        val vc = ByteArrayOutputStream()
        val vendor = "reference libFLAC 1.4.3".toByteArray()
        vc.write(le(vendor.size)); vc.write(vendor)
        val es = listOf("TITLE=Song", "Artist=Quinn XCII", "ALBUMARTIST=Quinn XCII", "ALBUM=The Album", "MOOD=Sad", "ALBUM ARTIST=Quinn XCII")
            .map { it.toByteArray() }
        vc.write(le(es.size)); es.forEach { vc.write(le(it.size)); vc.write(it) }
        val v = vc.toByteArray()
        fun block(type: Int, d: ByteArray, last: Boolean = false) = byteArrayOf((type or if (last) 0x80 else 0).toByte(),
            (d.size shr 16).toByte(), (d.size shr 8).toByte(), d.size.toByte()) + d
        val f = File.createTempFile("keep", ".flac")
        try {
            f.writeBytes("fLaC".toByteArray() + block(0, ByteArray(34)) + block(4, v) + block(6, ByteArray(40) { 3 }) +
                block(6, ByteArray(50) { 4 }) + block(1, ByteArray(100), last = true) + "audio".toByteArray())
            val out = ByteArrayOutputStream()
            Flac.retag(f, out, mapOf("MOOD" to listOf("Chill"), "MOOD_LYRICS" to listOf("Calm;0.40")), null, keepOther = true)
            val b = out.toByteArray()
            val blocks = Flac.readHeader(ByteArrayInputStream(b))
            assertEquals(listOf(0, 4, 6, 6, 1), blocks.map { it.type })   // both pictures and the padding kept, in order
            val c = Flac.comments(blocks)
            assertEquals(listOf("Quinn XCII"), c["ARTIST"]); assertEquals(listOf("Quinn XCII"), c["ALBUMARTIST"])
            assertEquals(listOf("The Album"), c["ALBUM"]); assertEquals(listOf("Song"), c["TITLE"])
            assertEquals(listOf("Chill"), c["MOOD"]); assertEquals(listOf("Calm;0.40"), c["MOOD_LYRICS"])
            // The others byte for byte (their own spelling too), the vendor as it was.
            val raw = String(blocks[1].data, Charsets.UTF_8)
            for (e in listOf("TITLE=Song", "Artist=Quinn XCII", "ALBUMARTIST=Quinn XCII", "ALBUM=The Album", "ALBUM ARTIST=Quinn XCII", "reference libFLAC 1.4.3"))
                assertTrue(e, e in raw)
            assertEquals("audio", String(b, b.size - 5, 5))
        } finally { f.delete() }
    }

    @Test fun aWavWithOnlyListInfoKeepsItsNames() {
        val f = File.createTempFile("keep", ".wav")
        try {
            fun item(id: String, s: String) = (s.toByteArray() + 0).let { id.toByteArray() + le(it.size) + it + (if (it.size % 2 == 1) byteArrayOf(0) else ByteArray(0)) }
            val info = "INFO".toByteArray() + item("INAM", "Song") + item("IART", "Quinn XCII") + item("IPRD", "The Album")
            val fmt = byteArrayOf(1, 0, 1, 0) + le(8000) + le(16000) + byteArrayOf(2, 0, 16, 0)
            val body = "fmt ".toByteArray() + le(16) + fmt + "data".toByteArray() + le(1000) + ByteArray(1000) { 7 } +
                "LIST".toByteArray() + le(info.size) + info
            f.writeBytes("RIFF".toByteArray() + le(body.size + 4) + "WAVE".toByteArray() + body)
            assertTrue(TagWrite.wav(f, "words", emptyMap(), mood = "Dreamy"))
            val t = WavTags.read(f)!!   // the ID3 chunk's now (players read it in place of LIST INFO)
            assertEquals("Song", t["title"]); assertEquals("Quinn XCII", t["artist"]); assertEquals("The Album", t["album"])
            assertEquals("Dreamy", t["mood"])
            val b = f.readBytes()
            val at = (0 until b.size - 4).first { String(b, it, 4, Charsets.ISO_8859_1) == "data" }
            assertArrayEquals(ByteArray(1000) { 7 }, b.copyOfRange(at + 8, at + 8 + 1000))
        } finally { f.delete() }
    }
}
