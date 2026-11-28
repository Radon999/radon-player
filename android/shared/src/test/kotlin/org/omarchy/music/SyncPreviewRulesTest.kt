package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPreviewRulesTest {
    private val R = SyncPreviewRules

    @Test fun itCountsWhatWouldChangeOnBoth() {
        assertEquals("A sync would change nothing", R.title(emptyList(), emptyList()))
        assertEquals("A sync would change 1 thing", R.title(listOf("a"), emptyList()))
        assertEquals("A sync would change 3 things", R.title(listOf("a"), listOf("b", "c")))
    }

    @Test fun eachDevicesChangesAreUnderItsName() {
        val b = R.body(listOf("Favorites: 2 songs added"), emptyList(), "this phone", "the PC")
        assertEquals("On this phone:\n• Favorites: 2 songs added\n\nOn the PC:\n• nothing\n\nNothing has been changed yet.", b)
    }

    @Test fun aLongListIsCutShort() {
        val b = R.body((1..50).map { "line $it" }, emptyList(), "a", "b", most = 40)
        assertTrue(b.contains("• …and 10 more")); assertTrue(!b.contains("line 41"))
    }
}
