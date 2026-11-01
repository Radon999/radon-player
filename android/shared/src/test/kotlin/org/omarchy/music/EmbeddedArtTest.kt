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

    private fun ss(n: Int) = byteArrayOf(((n shr 21) and 0x7f).toByte(), ((n shr 14) and 0x7f).toByte(), ((n shr 7) and 0x7f).toByte(), (n and 0x7f).toByte())
    private val ffJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 0xFF.toByte(), 0, 3, 0xFF.toByte(), 0xD9.toByte())
    private fun apic(type: Int, pic: ByteArray) = ByteArrayOutputStream().apply {
        write(0); write("image/jpeg".toByteArray()); write(0); write(type); write("c".toByteArray()); write(0); write(pic)
    }.toByteArray()
    /** ID3's unsynchronisation: a 00 after each FF that a false sync (FF Ex) or a 00 follows, and after a last FF. */
    private fun unsync(b: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        for (i in b.indices) {
            o.write(b[i].toInt())
            if (b[i] == 0xFF.toByte() && (i + 1 == b.size || (b[i + 1].toInt() and 0xE0) == 0xE0 || b[i + 1].toInt() == 0)) o.write(0)
        }
        return o.toByteArray()
    }

    @Test fun readsV24FramesWithALengthFirstAndUnsynchronised() {
        val body = apic(3, ffJpeg)
        val data = ss(body.size) + unsync(body)
        val frame = "APIC".toByteArray() + ss(data.size) + byteArrayOf(0, 0x03) + data
        val tag = "ID3".toByteArray() + byteArrayOf(4, 0, 0) + ss(frame.size + 20) + frame + ByteArray(20)
        assertArrayEquals(ffJpeg, EmbeddedArt.id3Picture(tag))
        assertArrayEquals(ffJpeg, EmbeddedArt.picture(tag.inputStream()))
    }

    @Test fun readsAWholeV23TagUnsynchronised() {
        val body = apic(3, ffJpeg)
        val frames = unsync("APIC".toByteArray() + be(body.size) + byteArrayOf(0, 0) + body)
        val tag = "ID3".toByteArray() + byteArrayOf(3, 0, 0x80.toByte()) + ss(frames.size) + frames
        assertArrayEquals(ffJpeg, EmbeddedArt.id3Picture(tag))
    }

    @Test fun findsTheFrontCoverInAFlac() {
        fun pic(kind: Int, data: ByteArray) = be(kind) + be(10) + "image/jpeg".toByteArray() + be(0) + ByteArray(16) + be(data.size) + data
        val back = byteArrayOf(9, 9)
        val p1 = pic(4, back); val p2 = pic(3, ffJpeg)
        val flac = "fLaC".toByteArray() + byteArrayOf(0, 0, 0, 34) + ByteArray(34) +
            byteArrayOf(6) + be(p1.size).copyOfRange(1, 4) + p1 + byteArrayOf(0x86.toByte()) + be(p2.size).copyOfRange(1, 4) + p2 + ByteArray(100)
        assertArrayEquals(ffJpeg, EmbeddedArt.picture(flac.inputStream()))
        val onlyBack = "fLaC".toByteArray() + byteArrayOf(0x86.toByte()) + be(p1.size).copyOfRange(1, 4) + p1
        assertArrayEquals(back, EmbeddedArt.picture(onlyBack.inputStream()))
        val emptyId3 = "ID3".toByteArray() + byteArrayOf(3, 0, 0) + ss(10) + ByteArray(10)   // a tag without a picture before it
        assertArrayEquals(ffJpeg, EmbeddedArt.picture((emptyId3 + flac).inputStream()))
        assertNull(EmbeddedArt.picture("fLaC".toByteArray().inputStream()))
    }
}
