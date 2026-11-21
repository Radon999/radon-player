package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoderRoomTest {
    @Test fun aFlacFrameAlwaysFits() {
        // “Kentucky (Demo)”: a frame of 41,436 bytes, a decoder given 32,768
        assertTrue(DecoderRoom.flac(2, -1) > 41_436)
        assertTrue("a file that says too little", DecoderRoom.flac(2, 32_768) > 41_436)
        assertEquals("the most a stereo frame can hold", 2 * 4 * 65_535 + 65_536, DecoderRoom.flac(2, 0))
        assertEquals("a file that says more is believed", 9_000_000, DecoderRoom.flac(2, 9_000_000))
        assertTrue("channels not known", DecoderRoom.flac(-1, -1) >= 4 * 65_535)
    }
}
