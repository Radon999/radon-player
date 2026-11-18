package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** New releases found by themselves, and the wake-up timer. */
class NewReleaseRulesTest {
    @Test fun onlyTheOnesNotToldAboutBefore() {
        val found = listOf("a" to ("Persona" to "half•alive"), "b" to ("ARMS" to "HOAX"), "a" to ("Persona" to "half•alive"), "c" to ("Nectar" to "Joji"))
        assertEquals(listOf("b", "c"), NewReleaseRules.fresh(found, setOf("a")).map { it.first })
        assertEquals(listOf("a", "b", "c"), NewReleaseRules.fresh(found, emptySet()).map { it.first })
    }

    @Test fun whatIsSaid() {
        assertEquals("A new release", NewReleaseRules.title(1)); assertEquals("5 new releases", NewReleaseRules.title(5))
        assertEquals("Persona by half•alive", NewReleaseRules.said(listOf("Persona" to "half•alive")))
        assertEquals("A by X; B by Y; C by Z; and 2 more", NewReleaseRules.said(listOf("A" to "X", "B" to "Y", "C" to "Z", "D" to "W", "E" to "V")))
    }

    @Test fun theWakeUpTimerGoesOffOnceInItsMinutes() {
        assertEquals(450, WakeUpRules.minutes("7:30")); assertEquals(450, WakeUpRules.minutes(" 07.30 "))
        assertNull(WakeUpRules.minutes("25:00")); assertNull(WakeUpRules.minutes("seven"))
        assertTrue(WakeUpRules.due(true, 450, 450, 322, 321))
        assertTrue("the app was busy for two minutes", WakeUpRules.due(true, 450, 452, 322, 321))
        assertFalse("already went off today", WakeUpRules.due(true, 450, 451, 322, 322))
        assertFalse("too early", WakeUpRules.due(true, 450, 449, 322, 321))
        assertFalse("the app was opened long after: not then", WakeUpRules.due(true, 450, 470, 322, 321))
        assertFalse(WakeUpRules.due(false, 450, 450, 322, 321))
        assertEquals("7:05", WakeUpRules.shown(425))
    }
}
