package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FileWholeTest {
    private fun flac(blocks: List<Pair<Int, Int>>, after: ByteArray): File {
        val f = File.createTempFile("check", ".flac")
        f.outputStream().use { o ->
            o.write("fLaC".toByteArray())
            blocks.forEachIndexed { i, (type, size) ->
                o.write(byteArrayOf(((if (i == blocks.size - 1) 0x80 else 0) or type).toByte(), (size shr 16).toByte(), (size shr 8).toByte(), size.toByte()))
                o.write(ByteArray(size))
            }
            o.write(after)
        }
        return f
    }
    private val frame = byteArrayOf(0xff.toByte(), 0xf8.toByte(), 0x5a, 0x9c.toByte(), 1, 2, 3)

    @Test fun aFlacWhoseSoundFollowsItsBlocksIsWhole() {
        assertEquals(true, FileWhole.sound(flac(listOf(0 to 34, 4 to 2749, 6 to 60000), frame)))
        assertEquals(true, FileWhole.sound(flac(listOf(0 to 34), frame)))
    }

    @Test fun aFlacWhoseBlocksDontLeadToSoundIsNot() {
        assertEquals("the sound isn't where the blocks say", false, FileWhole.sound(flac(listOf(0 to 34, 4 to 2749), byteArrayOf(0, 0, 0, 0, 0xff.toByte(), 0xf8.toByte()))))
        assertEquals("cut short", false, FileWhole.sound(flac(listOf(0 to 34, 4 to 100), ByteArray(0))))
        assertEquals("no STREAMINFO first", false, FileWhole.sound(flac(listOf(4 to 100, 0 to 34), frame)))
    }

    @Test fun readFromItsStartItSaysTheSame() {
        assertEquals(true, flac(listOf(0 to 34, 4 to 2749, 6 to 60000), frame).inputStream().use { FileWhole.flacLeadsToSound(it) })
        assertEquals(false, flac(listOf(0 to 34, 4 to 2749), byteArrayOf(0, 0, 0, 0)).inputStream().use { FileWhole.flacLeadsToSound(it) })
        assertEquals("not a FLAC: not judged", true, "ID3 something else".byteInputStream().use { FileWhole.flacLeadsToSound(it) })
    }

    private fun mp3(tag: Int, sound: Int, v1: Boolean = false): File {
        val f = File.createTempFile("check", ".mp3")
        f.outputStream().use { o ->
            if (tag >= 0) o.write(byteArrayOf(0x49, 0x44, 0x33, 3, 0, 0, (tag shr 21 and 0x7f).toByte(), (tag shr 14 and 0x7f).toByte(), (tag shr 7 and 0x7f).toByte(), (tag and 0x7f).toByte()) + ByteArray(tag))
            o.write(byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0xe0.toByte(), 0x40) + ByteArray(sound - 4))
            if (v1) o.write("TAG".toByteArray() + ByteArray(125))
        }
        return f
    }

    @Test fun howMuchSoundAFileHolds() {
        assertEquals("an MP3: what follows its tag", 500_000L, FileWhole.soundBytes(mp3(3_000_000, 500_000)))
        assertEquals("the old tag at its end isn't sound", 500_000L, FileWhole.soundBytes(mp3(3_000_000, 500_000, v1 = true)))
        assertEquals("an MP3 with no tag", 70_000L, FileWhole.soundBytes(mp3(-1, 70_000)))
        assertEquals("a FLAC: what follows its blocks", 7L, FileWhole.soundBytes(flac(listOf(0 to 34, 4 to 2749), frame)))
        assertEquals("another kind isn't told", null, FileWhole.soundBytes(File.createTempFile("check", ".m4a").apply { writeBytes(ByteArray(64) { 7 }) }))
    }

    @Test fun aChangeThatLosesSoundIsToldFromOneThatDoesNot() {
        // “New Wave”: a 14 MB cover in its tag and 6.7 MB of sound, put back with 400 KB of sound (10 seconds)
        assertEquals(false, FileWhole.kept(6_723_008, 400_000))
        assertEquals("the same sound, a bigger tag", true, FileWhole.kept(6_723_008, 6_723_008))
        assertEquals("a tag library's tidying", true, FileWhole.kept(6_723_008, 6_723_008 - 128))
        assertEquals("not told: nothing said against it", true, FileWhole.kept(null, 5))
        assertEquals(true, FileWhole.kept(5, null))
    }

    @Test fun otherFilesAreOnlyAskedNotToBeEmpty() {
        val mp3 = File.createTempFile("check", ".mp3").apply { writeBytes(byteArrayOf(0x49, 0x44, 0x33, 4, 0, 0)) }
        assertEquals(true, FileWhole.sound(mp3))
        assertEquals(false, FileWhole.sound(File.createTempFile("check", ".mp3")))
    }
}
