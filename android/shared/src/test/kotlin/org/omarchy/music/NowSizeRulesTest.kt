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
}
