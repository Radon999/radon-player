package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** A file's own MOOD tag read from each kind of file, and what its words mean. */
class MoodTagTest {
    private fun le(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())
    private fun be(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    private fun ss(n: Int) = byteArrayOf(((n shr 21) and 0x7f).toByte(), ((n shr 14) and 0x7f).toByte(), ((n shr 7) and 0x7f).toByte(), (n and 0x7f).toByte())
    private fun read(b: ByteArray) = MoodTag.read(ByteArrayInputStream(b))

    @Test fun pick() {
        assertEquals("Chill", MoodTag.pick("Chill"))
        assertEquals("Chill", MoodTag.pick("chill; dreamy"))
        assertEquals("Sad", MoodTag.pick("Melancholic"))
        assertEquals("Party", MoodTag.pick("Not happy, Party"))
        assertNull(MoodTag.pick(""))
        assertNull(MoodTag.pick("xyzzy"))
    }

    @Test fun mp3() {
        val apic = "APIC".toByteArray() + ss(5000) + byteArrayOf(0, 0) + ByteArray(5000)
        val tm = byteArrayOf(3) + "Happy".toByteArray()
        val tag = apic + "TMOO".toByteArray() + ss(tm.size) + byteArrayOf(0, 0) + tm + ByteArray(100)
        assertEquals("Happy", read("ID3".toByteArray() + byteArrayOf(4, 0, 0) + ss(tag.size) + tag + ByteArray(3000) { -1 }))
        val tx = byteArrayOf(0) + "MOOD".toByteArray() + byteArrayOf(0) + "Sad".toByteArray()
        val f = "TXXX".toByteArray() + be(tx.size) + byteArrayOf(0, 0) + tx
        assertEquals("Sad", read("ID3".toByteArray() + byteArrayOf(3, 0, 0) + ss(f.size) + f + ByteArray(100)))
        assertEquals("", read("ID3".toByteArray() + byteArrayOf(3, 0, 0) + ss(0) + ByteArray(50)))
    }

    @Test fun wavWrittenAndRead() {
        val f = File.createTempFile("moodtag", ".wav")
        try {
            val fmt = byteArrayOf(1, 0, 1, 0) + le(8000) + le(16000) + byteArrayOf(2, 0, 16, 0)
            val body = "fmt ".toByteArray() + le(16) + fmt + "data".toByteArray() + le(1000) + ByteArray(1000)
            f.writeBytes("RIFF".toByteArray() + le(body.size + 4) + "WAVE".toByteArray() + body)
            WavTags.write(f, mapOf("title" to "X", "mood" to "Dreamy"))
            assertEquals("Dreamy", WavTags.read(f)!!["mood"])
            assertEquals("Dreamy", f.inputStream().use { MoodTag.read(it) })
            WavTags.write(f, mapOf("mood" to ""))
            assertEquals("", f.inputStream().use { MoodTag.read(it) })
        } finally { f.delete() }
    }

    @Test fun m4a() {
        fun box(t: String, body: ByteArray) = be(body.size + 8) + t.toByteArray() + body
        val item = box("----", box("mean", ByteArray(4) + "com.apple.iTunes".toByteArray()) + box("name", ByteArray(4) + "MOOD".toByteArray()) +
            box("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0) + "Romantic".toByteArray()))
        val meta = box("meta", ByteArray(4) + box("hdlr", ByteArray(25)) + box("ilst", box("covr", ByteArray(300)) + item))
        assertEquals("Romantic", read(box("ftyp", "M4A ".toByteArray() + ByteArray(8)) + box("mdat", ByteArray(10000)) +
            box("moov", box("mvhd", ByteArray(100)) + box("trak", ByteArray(500)) + box("udta", meta))))
        assertEquals("", read(box("ftyp", ByteArray(12)) + box("moov", box("mvhd", ByteArray(100))) + box("mdat", ByteArray(100))))
    }

    @Test fun flac() {
        val vc = ByteArrayOutputStream()
        vc.write(le(1)); vc.write("x".toByteArray())
        val es = listOf("TITLE=a", "mood=Focus").map { it.toByteArray() }
        vc.write(le(es.size)); es.forEach { vc.write(le(it.size)); vc.write(it) }
        val v = vc.toByteArray()
        assertEquals("Focus", read("fLaC".toByteArray() + byteArrayOf(0, 0, 0, 34) + ByteArray(34) +
            byteArrayOf(0x84.toByte(), 0, (v.size shr 8).toByte(), v.size.toByte()) + v + ByteArray(100)))
    }

    @Test fun ogg() {
        val id = byteArrayOf(1) + "vorbis".toByteArray() + ByteArray(23)
        val cm = ByteArrayOutputStream()
        cm.write(byteArrayOf(3) + "vorbis".toByteArray()); cm.write(le(1)); cm.write("v".toByteArray())
        val es = listOf("METADATA_BLOCK_PICTURE=" + "A".repeat(600), "MOOD=Calm").map { it.toByteArray() }
        cm.write(le(es.size)); es.forEach { cm.write(le(it.size)); cm.write(it) }; cm.write(1)
        val c = cm.toByteArray()
        fun lacing(n: Int): ByteArray { val l = ArrayList<Byte>(); var k = n; while (k >= 255) { l += 255.toByte(); k -= 255 }; l += k.toByte(); return l.toByteArray() }
        fun page(data: ByteArray) = lacing(data.size).let { "OggS".toByteArray() + ByteArray(10) + le(7) + ByteArray(8) + byteArrayOf(it.size.toByte()) + it + data }
        assertEquals("Calm", read(page(id) + page(c) + page(ByteArray(10))))
    }
}
