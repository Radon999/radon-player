package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlacFormatTest {
    @Test fun readsBitsAndRate() {
        val head = ByteArray(42)
        "fLaC".toByteArray().copyInto(head)
        head[4] = 0x80.toByte(); head[7] = 34   // STREAMINFO, the last block, 34 bytes
        val si = 8
        head[si + 10] = 0x17; head[si + 11] = 0x70; head[si + 12] = 0x03; head[si + 13] = 0x70   // 96 kHz, stereo, 24-bit
        assertEquals(24 to 96000, Flac.streamFormat(head.inputStream()))
        assertNull(Flac.streamFormat("ID3 not a flac at all, padding padding padding".toByteArray().inputStream()))
    }
}
