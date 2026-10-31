package org.omarchy.music

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class EmbeddedArtTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

    private fun id3(): ByteArray {
        val frame = ByteArrayOutputStream().apply {
            write(0); write("image/jpeg".toByteArray()); write(0); write(3); write("cover".toByteArray()); write(0); write(jpeg)
        }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray()); out.write(3); out.write(0); out.write(0)
        val size = 10 + frame.size
        out.write(byteArrayOf(0, 0, (size shr 7).toByte(), (size and 0x7f).toByte()))
        out.write("APIC".toByteArray())
        out.write(byteArrayOf(0, 0, (frame.size shr 8).toByte(), frame.size.toByte(), 0, 0))
        out.write(frame)
        return out.toByteArray()
    }

    private fun le(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())

    private fun be(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    private fun atom(type: String, vararg body: ByteArray): ByteArray {
        val all = body.fold(ByteArray(0)) { a, x -> a + x }
        return be(8 + all.size) + type.toByteArray() + all
    }

    @Test fun findsTheCoverInAnM4aAndAnAiff() {
        val data = atom("data", be(13), be(0), jpeg)
        val moov = atom("moov", atom("mvhd", ByteArray(20)), atom("udta", atom("meta", ByteArray(4), atom("hdlr", ByteArray(25)),
            atom("ilst", atom("Xnam", atom("data", be(1), be(0), "Song".toByteArray())), atom("covr", data)))))
        val m4a = atom("ftyp", "M4A ".toByteArray(), be(0)) + atom("mdat", ByteArray(300)) + moov
        assertArrayEquals(jpeg, EmbeddedArt.picture(m4a.inputStream()))
        val tag = id3()
        val aiff = "FORM".toByteArray() + be(0) + "AIFF".toByteArray() + "SSND".toByteArray() + be(5) + ByteArray(6) +
            "ID3 ".toByteArray() + be(tag.size) + tag
        assertArrayEquals(jpeg, EmbeddedArt.picture(aiff.inputStream()))
    }

    @Test fun findsTheCoverInAWav() {
        val tag = id3()
        val data = ByteArray(101)   // an odd-sized chunk, padded
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray()); out.write(le(0)); out.write("WAVE".toByteArray())
        out.write("data".toByteArray()); out.write(le(data.size)); out.write(data); out.write(0)
        out.write("id3 ".toByteArray()); out.write(le(tag.size)); out.write(tag)
        assertArrayEquals(jpeg, EmbeddedArt.wavPicture(out.toByteArray().inputStream()))
        assertArrayEquals(jpeg, EmbeddedArt.id3Picture(tag))
        assertArrayEquals(jpeg, EmbeddedArt.picture(out.toByteArray().inputStream()))
        assertArrayEquals(jpeg, EmbeddedArt.picture((tag + ByteArray(50)).inputStream()))   // a raw .aac with its ID3 tag first
        assertNull(EmbeddedArt.wavPicture("fLaC and nothing else at all".toByteArray().inputStream()))
    }
}
