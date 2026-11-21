package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class FlacRepairTest {
    private fun streamInfo(bits: Int = 24): ByteArray = ByteArray(34).also { it[12] = (((bits - 1) shr 4) and 1).toByte(); it[13] = (((bits - 1) and 0x0f) shl 4).toByte() }
    private fun block(type: Int, data: ByteArray, last: Boolean = false) =
        byteArrayOf(((if (last) 0x80 else 0) or type).toByte(), (data.size shr 16).toByte(), (data.size shr 8).toByte(), data.size.toByte()) + data
    private fun comments(vararg c: String): ByteArray = ByteArrayOutputStream().also { o ->
        fun le(n: Int) = o.write(byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte()))
        le(4); o.write("test".toByteArray()); le(c.size); c.forEach { le(it.toByteArray().size); o.write(it.toByteArray()) }
    }.toByteArray()
    /** The first frame of a 24-bit stream: its header with a right checksum, then some sound. */
    private fun sound(): ByteArray {
        val h = byteArrayOf(0xff.toByte(), 0xf8.toByte(), 0xc9.toByte(), 0x1c, 0x00)   // 4096 samples, 44.1 kHz; stereo, 24 bits; frame 0
        var crc = 0
        for (x in h) { var c = crc xor (x.toInt() and 0xff); repeat(8) { c = if (c and 0x80 != 0) (c shl 1) xor 0x07 else c shl 1 }; crc = c and 0xff }
        return h + crc.toByte() + ByteArray(4000) { (it * 7).toByte() }
    }
    private fun file(vararg parts: ByteArray) = File.createTempFile("repair", ".flac").apply { writeBytes("fLaC".toByteArray() + parts.reduce { a, b -> a + b }) }
    private fun backup() = File.createTempFile("repair", ".front")

    @Test fun aWholeFileIsLeftAlone() {
        val f = file(block(0, streamInfo()), block(4, comments("TITLE=Whole")), block(6, ByteArray(5000), last = true), sound())
        val was = f.readBytes()
        assertNull(FlacRepair.repair(f, backup()))
        assertTrue(was.contentEquals(f.readBytes()))
    }

    @Test fun aCoverBlockCutShortIsMadePaddingAndTheTagsStay() {
        // The picture block says 90,000 bytes but only 5,000 of it were written before the app was closed: its "end" is
        // far past where the sound starts
        val cut = byteArrayOf(0x86.toByte(), 0x01, 0x5f, 0x90.toByte()) + ByteArray(5000) { 0x11 }
        val music = sound()
        val f = file(block(0, streamInfo()), block(4, comments("TITLE=Hymn", "ARTIST=A Band")), cut, music)
        assertEquals("no player opens it", false, FileCheck.sound(f))
        val b = backup(); val was = f.readBytes()
        val done = FlacRepair.repair(f, b)
        assertNotNull(done)
        assertEquals("it plays", true, FileCheck.sound(f))
        assertEquals("its tags were whole, and stay", true, done!!.tagsKept)
        assertEquals("the same length: nothing was moved", was.size.toLong(), f.length())
        assertTrue("the music is untouched", music.contentEquals(f.readBytes().copyOfRange(done.soundAt.toInt(), f.length().toInt())))
        assertTrue("what was in front of the sound is saved as it was", was.copyOfRange(0, done.soundAt.toInt()).contentEquals(b.readBytes()))
    }

    @Test fun tagsThatDontReadToTheirEndBecomePaddingToo() {
        val bad = byteArrayOf(0x04, 0x00, 0x00, 0x40) + ByteArray(64) { 0x7f }   // a comment block of nonsense
        val f = file(block(0, streamInfo()), bad, ByteArray(300) { 0x22 }, sound())
        val done = FlacRepair.repair(f, backup())
        assertNotNull(done); assertEquals(false, done!!.tagsKept); assertEquals(true, FileCheck.sound(f))
    }

    @Test fun aSyncCodeInsideACoverIsNotTakenForTheSound() {
        val trap = ByteArray(2000) { 0x33 }.also { it[100] = 0xff.toByte(); it[101] = 0xf8.toByte(); it[102] = 0xc9.toByte(); it[103] = 0x1c; it[104] = 0; it[105] = 0x55 }   // (a wrong checksum)
        val cut = byteArrayOf(0x86.toByte(), 0x7f, 0x00, 0x00) + trap
        val music = sound()
        val f = file(block(0, streamInfo()), cut, music)
        val done = FlacRepair.repair(f, backup())
        assertEquals((4 + 38 + cut.size).toLong(), done!!.soundAt)
    }

    @Test fun aFileWithNoSoundToFindIsLeftAsItWas() {
        val f = file(block(0, streamInfo()), byteArrayOf(0x86.toByte(), 0x7f, 0, 0) + ByteArray(3000) { 0x44 })
        val was = f.readBytes()
        assertNull(FlacRepair.repair(f, backup()))
        assertTrue(was.contentEquals(f.readBytes()))
    }
}
