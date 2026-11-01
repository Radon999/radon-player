package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Lyrics and MOOD_LYRICS written into MP3s and WAVs by hand, and read back (with MOOD) from every kind of file. */
class TagWriteTest {
    private fun le(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())
    private fun be(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    private fun ss(n: Int) = byteArrayOf(((n shr 21) and 0x7f).toByte(), ((n shr 14) and 0x7f).toByte(), ((n shr 7) and 0x7f).toByte(), (n and 0x7f).toByte())
    private val ALL = setOf(MoodTag.MOOD, MoodTag.MOOD_LYRICS, MoodTag.LYRICS)
    private fun tags(b: ByteArray) = MoodTag.readTags(ByteArrayInputStream(b), ALL)

    private val txxxMood = (byteArrayOf(0) + "MOOD".toByteArray() + byteArrayOf(0) + "Sad".toByteArray()).let {
        "TXXX".toByteArray() + be(it.size) + byteArrayOf(0, 0) + it
    }

    @Test fun mp3() {
        val mp3 = "ID3".toByteArray() + byteArrayOf(3, 0, 0) + ss(txxxMood.size) + txxxMood + ByteArray(3000) { -1 }
        val w1 = TagWrite.mp3(mp3, "line one\nline two", mapOf("MOOD_LYRICS" to "Happy;0.70;by=words"))!!
        assertEquals(mapOf("MOOD" to "Sad", "MOOD_LYRICS" to "Happy;0.70;by=words", "LYRICS" to "line one\nline two"), tags(w1))
        val w2 = TagWrite.mp3(w1, null, mapOf("MOOD_LYRICS" to "Dark;0.50;by=ai"))!!
        assertEquals(mapOf("MOOD" to "Sad", "MOOD_LYRICS" to "Dark;0.50;by=ai", "LYRICS" to "line one\nline two"), tags(w2))
        assertTrue(w2.copyOfRange(w2.size - 3000, w2.size).all { it == (-1).toByte() })   // the sound as it was
        assertEquals("words", tags(TagWrite.mp3(ByteArray(2000) { -1 }, "words", emptyMap())!!)!![MoodTag.LYRICS])   // no tag: one made
    }

    @Test fun wav() {
        val f = File.createTempFile("tagwrite", ".wav")
        try {
            val fmt = byteArrayOf(1, 0, 1, 0) + le(8000) + le(16000) + byteArrayOf(2, 0, 16, 0)
            val body = "fmt ".toByteArray() + le(16) + fmt + "data".toByteArray() + le(1000) + ByteArray(1000) { 7 }
            f.writeBytes("RIFF".toByteArray() + le(body.size + 4) + "WAVE".toByteArray() + body)
            WavTags.write(f, mapOf("title" to "X", "mood" to "Dreamy"))
            assertTrue(TagWrite.wav(f, "wav words", mapOf("MOOD_LYRICS" to "Sad;0.90;by=words")))
            assertEquals(mapOf("MOOD" to "Dreamy", "MOOD_LYRICS" to "Sad;0.90;by=words", "LYRICS" to "wav words"), f.inputStream().use { MoodTag.readTags(it, ALL) })
            assertEquals("X", WavTags.read(f)!!["title"])
        } finally { f.delete() }
    }

    @Test fun flacAndM4aRead() {
        val vc = ByteArrayOutputStream()
        vc.write(le(1)); vc.write("x".toByteArray())
        val es = listOf("TITLE=a", "mood=Focus", "MOOD_LYRICS=Sad;0.80;by=words", "UNSYNCEDLYRICS=hello").map { it.toByteArray() }
        vc.write(le(es.size)); es.forEach { vc.write(le(it.size)); vc.write(it) }
        val v = vc.toByteArray()
        val flac = "fLaC".toByteArray() + byteArrayOf(0, 0, 0, 34) + ByteArray(34) + byteArrayOf(0x84.toByte(), 0, (v.size shr 8).toByte(), v.size.toByte()) + v + ByteArray(100)
        assertEquals(mapOf("MOOD" to "Focus", "MOOD_LYRICS" to "Sad;0.80;by=words", "LYRICS" to "hello"), tags(flac))
        // a FLAC behind an ID3 tag
        assertEquals("Focus", MoodTag.read(ByteArrayInputStream("ID3".toByteArray() + byteArrayOf(3, 0, 0) + ss(20) + ByteArray(20) + flac)))

        fun box(t: String, body: ByteArray) = be(body.size + 8) + t.toByteArray(Charsets.ISO_8859_1) + body
        fun freeform(name: String, value: String) = box("----", box("mean", ByteArray(4) + "com.apple.iTunes".toByteArray()) +
            box("name", ByteArray(4) + name.toByteArray()) + box("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0) + value.toByteArray()))
        val lyr = box("©lyr", box("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0) + "la la".toByteArray()))
        val meta = box("meta", ByteArray(4) + box("hdlr", ByteArray(25)) + box("ilst", freeform("MOOD", "Romantic") + lyr + freeform("MOOD_LYRICS", "Calm;0.40")))
        val m4a = box("ftyp", "M4A ".toByteArray() + ByteArray(8)) + box("mdat", ByteArray(1000)) + box("moov", box("mvhd", ByteArray(100)) + box("udta", meta))
        assertEquals(mapOf("MOOD" to "Romantic", "MOOD_LYRICS" to "Calm;0.40", "LYRICS" to "la la"), tags(m4a))
    }
}
