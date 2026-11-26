package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Size of Now playing: 100% at first, and never so small or big it can't be used. */
class NowSizeRulesTest {
    @Test fun usualSizeAtFirst() {
        assertEquals(100, NowSizeRules.percent(""))
        assertEquals(100, NowSizeRules.percent("nonsense"))
        assertEquals(1f, NowSizeRules.factor(""), 0f)
    }

    @Test fun keptWithinRange() {
        assertEquals(70, NowSizeRules.percent("70"))
        assertEquals(50, NowSizeRules.percent("10"))
        assertEquals(125, NowSizeRules.percent("400"))
        assertEquals(0.8f, NowSizeRules.factor("80"), 0.0001f)
    }

    @Test fun theMenuSaysWhichIsUsual() {
        assertEquals("100% (usual)", NowSizeRules.label(100))
        assertEquals("80%", NowSizeRules.label(80))
        assertTrue(NowSizeRules.OPTIONS.any { it.first == "100" })
    }

    @Test fun keptOnEachDevice() {
        assertTrue("never synced between the phone and the PC", !SyncRules.prefSyncs(NowSizeRules.PREF))
    }

    @Test fun theSlidersStartAtTheUsualSize() {
        assertEquals(100, NowSizeRules.visPercent(null))
        assertEquals(100, NowSizeRules.coverPercent(null))
        assertEquals(100, NowSizeRules.coverPercent(Float.NaN))
    }

    @Test fun theSlidersKeepToTheirStepsAndRange() {
        assertEquals(75, NowSizeRules.visPercent(73f))
        assertEquals(200, NowSizeRules.visPercent(900f))
        assertEquals(25, NowSizeRules.visPercent(0f))
        assertEquals("the cover never bigger than its room", 100, NowSizeRules.coverPercent(140f))
        assertEquals(30, NowSizeRules.coverPercent(5f))
        assertEquals(60, NowSizeRules.coverPercent(61f))
    }

    @Test fun theSlidersAreKeptOnEachDevice() {
        assertTrue(!SyncRules.prefSyncs(NowSizeRules.VIS_PREF))
        assertTrue(!SyncRules.prefSyncs(NowSizeRules.COVER_PREF))
    }

    @Test fun movingASliderSwitchesTheOverallSizeOff() {
        assertEquals("both sliders at 100%: the overall size is used", 0.7f, NowSizeRules.overall("70", 100, 100), 0.0001f)
        assertEquals("the cover's slider moved: off", 1f, NowSizeRules.overall("70", 100, 60), 0f)
        assertEquals("the visualizer's slider moved: off", 1f, NowSizeRules.overall("70", 150, 100), 0f)
        assertTrue("back at 100%: on again, as it was", NowSizeRules.overallOn(100, 100))
    }
}
