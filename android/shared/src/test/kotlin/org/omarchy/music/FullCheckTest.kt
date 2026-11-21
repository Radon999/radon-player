package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class FullCheckTest {
    private fun check(b: ByteArray) = FullCheck.check(b.inputStream())
    private val rnd = java.util.Random(7)
    private fun noise(n: Int) = ByteArray(n).also { rnd.nextBytes(it) }

    // ---- MP3: frames of 417 bytes (MPEG 1 layer III, 128 kbit/s, 44.1 kHz), 26 ms each ----
    private fun mp3Frames(n: Int): ByteArray = ByteArrayOutputStream().also { o ->
        repeat(n) { o.write(byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte(), 0x00)); o.write(ByteArray(413) { 0x55 }) }
    }.toByteArray()
    private fun id3(size: Int, body: ByteArray = ByteArray(size)) =
        byteArrayOf(0x49, 0x44, 0x33, 3, 0, 0, (size shr 21 and 0x7f).toByte(), (size shr 14 and 0x7f).toByte(), (size shr 7 and 0x7f).toByte(), (size and 0x7f).toByte()) + body

    @Test fun aWholeMp3SaysNothing() {
        assertNull(check(mp3Frames(400)))
        assertNull("a tag in front, with a picture's bytes in it", check(id3(50_000, noise(50_000)) + mp3Frames(400)))
        assertNull("the old tag at its end", check(mp3Frames(400) + "TAG".toByteArray() + ByteArray(125)))
        assertNull("its last frame cut off by a few bytes", check(mp3Frames(400).copyOf(400 * 417 - 30)))
    }

    @Test fun anMp3WithAStretchThatIsNotMusicIsDamaged() {
        // “New Wave”: its size as it should be, ten seconds of sound, then nothing where sound was, then the rest
        val said = check(id3(50_000) + mp3Frames(383) + ByteArray(300_000) + mp3Frames(2000))
        assertTrue(said, said!!.contains("inside it isn't music") && said.contains("from about 0:10 in"))
        assertTrue("a picture's bytes where sound was", check(mp3Frames(383) + noise(300_000) + mp3Frames(2000))!!.contains("inside it isn't music"))
        assertTrue("nothing but zeros after ten seconds", check(mp3Frames(383) + ByteArray(3 shl 20))!!.startsWith("its last 3"))
        assertEquals("no MP3 sound was found in it", check(id3(1000) + noise(90_000).also { for (i in it.indices) if (it[i] == 0xff.toByte()) it[i] = 0 }))
    }

    // ---- FLAC: frames with their two checksums, as an encoder writes them ----
    private fun crc8(b: ByteArray): Int { var c = 0; for (x in b) { c = c xor (x.toInt() and 0xff); repeat(8) { c = if (c and 0x80 != 0) ((c shl 1) xor 0x07) and 0xff else (c shl 1) and 0xff } }; return c }
    private fun crc16(b: ByteArray): Int { var c = 0; for (x in b) { c = c xor ((x.toInt() and 0xff) shl 8); repeat(8) { c = if (c and 0x8000 != 0) ((c shl 1) xor 0x8005) and 0xffff else (c shl 1) and 0xffff } }; return c }
    private fun flacFrame(number: Int, body: Int = 300): ByteArray {
        val num = if (number < 0x80) byteArrayOf(number.toByte()) else byteArrayOf((0xc0 or (number shr 6)).toByte(), (0x80 or (number and 0x3f)).toByte())
        val head = byteArrayOf(0xff.toByte(), 0xf8.toByte(), 0xc9.toByte(), 0x08) + num   // 4096 samples, 44.1 kHz, one channel, 16 bits
        val most = head + byteArrayOf(crc8(head).toByte()) + noise(body)
        val c = crc16(most)
        return most + byteArrayOf((c shr 8).toByte(), c.toByte())
    }
    private fun flac(frames: Int, said: Int = frames, pad: Int = 3000): ByteArray {
        val total = said * 4096L
        val info = ByteArray(34).also {
            it[0] = 0x10; it[2] = 0x10                                   // block sizes: 4096
            it[10] = 0x0a; it[11] = 0xc4.toByte(); it[12] = 0x40          // 44100 Hz, 1 channel
            it[13] = (0xf0 or ((total shr 32).toInt() and 0x0f)).toByte() // 16 bits, and the samples it holds
            it[14] = (total shr 24).toByte(); it[15] = (total shr 16).toByte(); it[16] = (total shr 8).toByte(); it[17] = total.toByte()
        }
        val o = ByteArrayOutputStream()
        o.write("fLaC".toByteArray()); o.write(byteArrayOf(0, 0, 0, 34)); o.write(info)
        o.write(byteArrayOf(0x81.toByte(), (pad shr 16).toByte(), (pad shr 8).toByte(), pad.toByte())); o.write(ByteArray(pad))
        repeat(frames) { o.write(flacFrame(it)) }
        return o.toByteArray()
    }

    @Test fun aWholeFlacSaysNothing() {
        assertNull(check(flac(400)))
        assertNull("an ID3 tag stuck in front of it", check(id3(2000) + flac(400)))
        assertNull("an old tag at its end", check(flac(400) + "TAG".toByteArray() + ByteArray(125)))
    }

    @Test fun aFlacDamagedAnywhereInsideIsFound() {
        val whole = flac(400)
        val one = whole.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x10).toByte() }   // one bit, half way in
        assertTrue(check(one), check(one)!!.contains("damaged"))
        val hole = whole.copyOf().also { java.util.Arrays.fill(it, 40_000, 70_000, 0) }
        assertTrue(check(hole), check(hole)!!.contains("damaged"))
        val cut = check(whole.copyOf(whole.size - 20_000))
        assertTrue(cut, cut!!.contains("cut short") || cut.contains("missing"))
        assertTrue("whole frames, fewer than it says it has", check(flac(300, said = 400))!!.contains("the rest is missing"))
        val lost = flac(400).let { b -> b.copyOfRange(0, 4 + 4 + 34 + 4 + 1000) + b.copyOfRange(4 + 4 + 34 + 4 + 3000, b.size) }   // its tags cut: they don't lead to its music
        assertTrue(check(lost), check(lost) != null)
    }

    // ---- Ogg: pages with their checksum ----
    private fun oggCrc(b: ByteArray): Int { var c = 0; for (x in b) { c = c xor ((x.toInt() and 0xff) shl 24); repeat(8) { c = if (c < 0) (c shl 1) xor 0x04c11db7 else c shl 1 } }; return c }
    private fun oggPage(seq: Int): ByteArray {
        val body = noise(200)
        val p = "OggS".toByteArray() + ByteArray(10) + byteArrayOf(9, 0, 0, 0) + byteArrayOf(seq.toByte(), (seq shr 8).toByte(), 0, 0) + ByteArray(4) + byteArrayOf(1, 200.toByte()) + body
        val c = oggCrc(p)
        p[22] = c.toByte(); p[23] = (c shr 8).toByte(); p[24] = (c shr 16).toByte(); p[25] = (c shr 24).toByte()
        return p
    }
    private fun ogg(pages: IntRange) = pages.map { oggPage(it) }.reduce { a, b -> a + b }

    @Test fun anOggIsCheckedPageByPage() {
        assertNull(check(ogg(0..299)))
        val bad = ogg(0..299).also { it[30_000] = (it[30_000].toInt() xor 1).toByte() }
        assertTrue(check(bad), check(bad)!!.contains("doesn't check out"))
        assertTrue(check(ogg(0..99) + ogg(120..299))!!.contains("part of it is missing"))
        assertTrue(check(ogg(0..299).copyOf(40_000))!!.contains("cut short"))
    }

    // ---- WAV and M4A: their parts must fit the file ----
    private fun le(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())
    private fun be(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())

    @Test fun aWavAndAnM4aMustHoldAllTheySay() {
        val wav = "RIFF".toByteArray() + le(0) + "WAVEfmt ".toByteArray() + le(16) + ByteArray(16) + "data".toByteArray() + le(100_000) + ByteArray(100_000)
        assertNull(check(wav))
        assertNull("a tag part after the sound", check(wav + "id3 ".toByteArray() + le(10) + ByteArray(10)))
        assertTrue(check(wav.copyOf(wav.size - 60_000))!!.startsWith("it is cut short"))
        val m4a = be(16) + "ftypM4A ".toByteArray() + ByteArray(4) + be(108) + "moov".toByteArray() + ByteArray(100) + be(50_008) + "mdat".toByteArray() + ByteArray(50_000)
        assertNull(check(m4a))
        assertTrue(check(m4a.copyOf(m4a.size - 20_000))!!.startsWith("it is cut short"))
        assertEquals("it isn't a music file the app knows", check("<!DOCTYPE html><html>".toByteArray()))
    }
}
