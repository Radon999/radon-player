package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FileCheckTest {
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
        assertEquals(true, FileCheck.sound(flac(listOf(0 to 34, 4 to 2749, 6 to 60000), frame)))
        assertEquals(true, FileCheck.sound(flac(listOf(0 to 34), frame)))
    }

    @Test fun aFlacWhoseBlocksDontLeadToSoundIsNot() {
        assertEquals("the sound isn't where the blocks say", false, FileCheck.sound(flac(listOf(0 to 34, 4 to 2749), byteArrayOf(0, 0, 0, 0, 0xff.toByte(), 0xf8.toByte()))))
        assertEquals("cut short", false, FileCheck.sound(flac(listOf(0 to 34, 4 to 100), ByteArray(0))))
        assertEquals("no STREAMINFO first", false, FileCheck.sound(flac(listOf(4 to 100, 0 to 34), frame)))
    }

    @Test fun otherFilesAreOnlyAskedNotToBeEmpty() {
        val mp3 = File.createTempFile("check", ".mp3").apply { writeBytes(byteArrayOf(0x49, 0x44, 0x33, 4, 0, 0)) }
        assertEquals(true, FileCheck.sound(mp3))
        assertEquals(false, FileCheck.sound(File.createTempFile("check", ".mp3")))
    }
}
