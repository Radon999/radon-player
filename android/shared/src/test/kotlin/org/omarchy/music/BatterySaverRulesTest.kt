package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySaverRulesTest {
    private val R = BatterySaverRules

    @Test fun itIsOnByItsSwitchOrThePhonesOwnSaver() {
        assertFalse(R.on(false, false)); assertTrue(R.on(true, false)); assertTrue(R.on(false, true))
    }

    @Test fun theVisualizerWidgetDrawsHalfAsOftenWhileSaving() {
        assertEquals(30, (1000 / R.vizFrameMs(false)).toInt())
        assertEquals(15, (1000 / R.vizFrameMs(true)).toInt())
    }

    @Test fun scansWaitOnlyOnBatteryAndOnlyWhileSaving() {
        assertTrue(R.holdsScans(saving = true, pluggedIn = false))
        assertFalse(R.holdsScans(saving = true, pluggedIn = true))
        assertFalse(R.holdsScans(saving = false, pluggedIn = false))
        assertEquals("Battery saver is on: the lyrics scan waits until this phone is plugged in", R.waits("the lyrics scan", "phone"))
    }
}
