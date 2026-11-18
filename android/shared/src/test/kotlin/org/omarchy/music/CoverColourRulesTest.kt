package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The visualizer in the album cover's colour: as it is when bright enough, lifted when too dark to see. */
class CoverColourRulesTest {
    private fun lum(c: Int) = (299 * ((c shr 16) and 0xFF) + 587 * ((c shr 8) and 0xFF) + 114 * (c and 0xFF)) / 1000

    @Test fun aBrightCoversColourIsKept() {
        assertEquals(0xFFF06048.toInt(), CoverColourRules.shown(0xF06048))
        assertEquals(0xFFF5E663.toInt(), CoverColourRules.shown(0x00F5E663))
        assertEquals("always opaque", 0xFF, CoverColourRules.shown(0x10F06048) ushr 24)
    }

    @Test fun aDarkCoversColourIsLiftedItsHueKept() {
        val navy = CoverColourRules.shown(0x101840)
        assertTrue("bright enough to see", lum(navy) >= 95)
        assertTrue("still blue", (navy and 0xFF) > ((navy shr 16) and 0xFF) && (navy and 0xFF) > ((navy shr 8) and 0xFF))
        val maroon = CoverColourRules.shown(0x400808)
        assertTrue(lum(maroon) >= 95)
        assertTrue("still red", ((maroon shr 16) and 0xFF) > (maroon and 0xFF))
    }

    @Test fun aBlackCoverIsAPlainGrey() {
        assertEquals(0xFF808080.toInt(), CoverColourRules.shown(0x000000))
        assertEquals(0xFF808080.toInt(), CoverColourRules.shown(0x050607))
    }
}
