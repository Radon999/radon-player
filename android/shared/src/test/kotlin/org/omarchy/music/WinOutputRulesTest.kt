package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which output is Windows' default, and a song's real bit depth. */
class WinOutputRulesTest {
    @Test fun aDevicesIdGivesItsRegistryKey() {
        assertEquals("{6f2c1a3b-0000-4a2b-9c1d-aabbccddeeff}", WinOutputRules.guidOf("{0.0.0.00000000}.{6F2C1A3B-0000-4A2B-9C1D-AABBCCDDEEFF}"))
        assertNull(WinOutputRules.guidOf("not an id"))
        assertNull(WinOutputRules.guidOf(""))
    }

    @Test fun theDeviceWindowsNamesWinsOverTheOneLastPickedByHand() {
        val active = listOf("{monitor}" to "2026-11-01", "{ka17}" to "", "{realtek}" to "2025-01-01")
        assertEquals("the DAC Windows chose as it was plugged in", "{ka17}", WinOutputRules.pick(active, "{KA17}"))
        assertEquals("Windows couldn't be asked: as before", "{monitor}", WinOutputRules.pick(active, null))
        assertEquals("it names one that isn't active: as before", "{monitor}", WinOutputRules.pick(active, "{gone}"))
        assertNull(WinOutputRules.pick(emptyList(), "{ka17}"))
    }

    @Test fun aSongsDepthIsItsFilesNotTheDecoders() {
        assertEquals("a 24-bit FLAC, decoded into 32-bit numbers", 24, WinOutputRules.shownBits("s32", 24))
        assertEquals("a stream: no file to ask", 24, WinOutputRules.shownBits("s32", null))
        assertEquals("a real 32-bit file says so itself", 32, WinOutputRules.shownBits("s32", 32))
        assertEquals(16, WinOutputRules.shownBits("s16", 0))
        assertEquals(16, WinOutputRules.shownBits("s16p", null))
        assertEquals("MP3 and AAC have no bit depth", 0, WinOutputRules.shownBits("floatp", null))
        assertEquals("a WAV's own depth, whatever it was decoded into", 32, WinOutputRules.shownBits("float", 32))
    }
}
