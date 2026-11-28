package org.omarchy.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverSyncRulesTest {
    private val R = CoverSyncRules

    @Test fun theDeviceWhereSyncWasTappedDecides() {
        assertTrue("tapped on the phone, its switch on", R.carries(pcAsked = false, pcSwitch = false, phoneSwitch = true))
        assertFalse("tapped on the phone, its switch off", R.carries(pcAsked = false, pcSwitch = true, phoneSwitch = false))
        assertFalse("tapped on the PC, its switch off", R.carries(pcAsked = true, pcSwitch = false, phoneSwitch = true))
        assertTrue("tapped on the PC, its switch on", R.carries(pcAsked = true, pcSwitch = true, phoneSwitch = false))
    }

    @Test fun aPcAppWithNoSwitchYetCarriesThemAsItAlwaysDid() {
        assertTrue(R.carries(pcAsked = true, pcSwitch = null, phoneSwitch = false))
    }
}
