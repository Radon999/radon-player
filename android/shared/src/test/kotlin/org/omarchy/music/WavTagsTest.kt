package org.omarchy.music

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class WavTagsTest {
    private fun le(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())
    private fun chunk(o: ByteArrayOutputStream, id: String, d: ByteArray) {
        o.write(id.toByteArray()); o.write(le(d.size)); o.write(d); if (d.size % 2 == 1) o.write(0)
    }

    /** A studio WAV: a chunk of odd size before the sound's, one after, and a stray byte at the end. */
    private fun studioWav(sound: ByteArray): File {
        val o = ByteArrayOutputStream()
        chunk(o, "bext", ByteArray(5) { 7 })
        chunk(o, "fmt ", byteArrayOf(1, 0, 2, 0, 0x44, 0xac.toByte(), 0, 0, 0x10, 0xb1.toByte(), 2, 0, 4, 0, 16, 0))
        chunk(o, "data", sound)
        chunk(o, "DGDA", ByteArray(3) { 9 })
        o.write(0)   // the stray byte
        val body = o.toByteArray()
        val f = File.createTempFile("studio", ".wav")
        f.writeBytes("RIFF".toByteArray() + le(body.size + 4) + "WAVE".toByteArray() + body)
        return f
    }

    @Test fun writesAndKeepsTheSound() {
        val sound = ByteArray(4000) { (it * 31).toByte() }
        val f = studioWav(sound)
        try {
            WavTags.write(f, mapOf("title" to "Floating v1.2", "artist" to "Somebody", "album" to "Álbum", "date" to "2019"))
            val t = WavTags.read(f)!!
            assertEquals("Floating v1.2", t["title"]); assertEquals("Somebody", t["artist"])
            assertEquals("Álbum", t["album"]); assertEquals("2019", t["date"])
            val b = f.readBytes()
            val at = (0 until b.size - 4).first { String(b, it, 4, Charsets.ISO_8859_1) == "data" }
            assertArrayEquals(sound, b.copyOfRange(at + 8, at + 8 + sound.size))
            assertEquals(b.size - 8, (b[4].toInt() and 0xff) or ((b[5].toInt() and 0xff) shl 8) or ((b[6].toInt() and 0xff) shl 16))
            WavTags.write(f, mapOf("album" to ""), jpeg = byteArrayOf(-1, -40, -1, 1, 2))
            val again = WavTags.read(f)!!
            assertEquals("", again["album"]); assertEquals("Floating v1.2", again["title"])
            assertArrayEquals(byteArrayOf(-1, -40, -1, 1, 2), EmbeddedArt.picture(f.inputStream()))
        } finally { f.delete() }
    }
}
