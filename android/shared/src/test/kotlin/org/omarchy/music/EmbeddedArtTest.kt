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
