package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class MoodWipeTest {
    private val names = setOf(MoodTag.MOOD, MoodTag.MOOD_LYRICS)
    private fun read(f: File) = f.inputStream().buffered().use { MoodTag.readTags(it, names + "TITLE") }.orEmpty()
    private fun le(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())

    private fun flac(vararg comments: String): ByteArray {
        val vc = ByteArrayOutputStream()
        val vendor = "test".toByteArray(); vc.write(le(vendor.size)); vc.write(vendor); vc.write(le(comments.size))
        for (c in comments) { val b = c.toByteArray(); vc.write(le(b.size)); vc.write(b) }
        val v = vc.toByteArray()
        val o = ByteArrayOutputStream()
        o.write("fLaC".toByteArray())
        o.write(byteArrayOf(0, 0, 0, 34)); o.write(ByteArray(34))   // STREAMINFO
        o.write(byteArrayOf(0x84.toByte(), (v.size shr 16).toByte(), (v.size shr 8).toByte(), v.size.toByte())); o.write(v)
        o.write(ByteArray(64) { 0x55 })   // "the sound"
        return o.toByteArray()
    }

    @Test fun flacMoodsBlankedOtherTagsKept() {
        val f = File.createTempFile("wipe", ".flac").apply { writeBytes(flac("TITLE=Keep me", "mood=Happy;Party", "MOOD_LYRICS=sad")) }
        val size = f.length()
        assertEquals(2, MoodWipe.wipe(f, names))
        assertEquals(mapOf("TITLE" to "Keep me"), read(f))
        assertEquals(size, f.length())
        assertEquals(0, MoodWipe.wipe(f, names))   // nothing left the second time
        f.delete()
    }

    @Test fun mp3MoodsBlanked() {
        val sound = ByteArray(64).also { it[0] = 0xFF.toByte(); it[1] = 0xFB.toByte() }
        val withMood = TagWrite.mp3(sound, null, mapOf(MoodTag.MOOD_LYRICS to "x"), "Chill")!!
        val f = File.createTempFile("wipe", ".mp3").apply { writeBytes(withMood) }
        assertEquals("Chill", read(f)[MoodTag.MOOD])
        assertTrue(MoodWipe.wipe(f, names) >= 2)
        assertEquals(emptyMap<String, String>(), read(f))
        assertEquals(withMood.size.toLong(), f.length())
        // The usual writer still takes the blanked tag and gives it a new mood
        val again = TagWrite.mp3(f.readBytes(), null, emptyMap(), "Happy")!!
        f.writeBytes(again)
        assertEquals("Happy", read(f)[MoodTag.MOOD])
        f.delete()
    }
}
