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

    @Test fun readsHowLongItRuns() {
        val head = ByteArray(42)
        "fLaC".toByteArray().copyInto(head)
        head[4] = 0x80.toByte(); head[7] = 34
        val si = 8
        head[si + 10] = 0x17; head[si + 11] = 0x70; head[si + 12] = 0x03; head[si + 13] = 0x70   // 96 kHz
        head[si + 14] = 0x01; head[si + 15] = 0x02; head[si + 16] = 0x8B.toByte(); head[si + 17] = 0x80.toByte()   // 16,944,000 samples
        assertEquals(176.5, Flac.seconds(head.inputStream()), 0.001)
        head[si + 14] = 0; head[si + 15] = 0; head[si + 16] = 0; head[si + 17] = 0
        assertEquals("a stream written without its total", 0.0, Flac.seconds(head.inputStream()), 0.0)
        assertEquals(0.0, Flac.seconds("ID3 not a flac at all, padding padding padding".toByteArray().inputStream()), 0.0)
    }
}
