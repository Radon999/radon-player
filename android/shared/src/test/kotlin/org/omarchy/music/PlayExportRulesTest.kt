package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayExportRulesTest {
    private fun c(count: Int, vararg t: Long) = PlayExportRules.Counted(count, t.toList())

    @Test fun twoDevicesPlaysAreAddedUp() {
        assertEquals(c(5, 1, 2, 3, 4, 5), PlayExportRules.merge(c(3, 1, 2, 3), c(2, 4, 5), 500))
    }

    @Test fun aPlayBothHaveIsOnePlayAndTheSameFileTwiceAddsNothing() {
        val once = PlayExportRules.merge(c(3, 1, 2, 3), c(3, 2, 3, 4), 500)
        assertEquals(c(4, 1, 2, 3, 4), once)
        assertEquals("the same file again", once, PlayExportRules.merge(once, c(3, 2, 3, 4), 500))
    }

    @Test fun playsWithNoTimeAreTheLargerLotNotBoth() {
        // here: 10 plays, 2 with times; theirs: 7 plays, 1 with a time
        val m = PlayExportRules.merge(c(10, 1, 2), c(7, 9), 500)
        assertEquals("3 times, and the larger lot of 8 without", 11, m.count)
        assertEquals("again: nothing more", m, PlayExportRules.merge(m, c(7, 9), 500))
    }

    @Test fun theCountNeverGoesDown() {
        assertEquals(40, PlayExportRules.merge(c(40), c(3, 1, 2, 3), 2).count.coerceAtMost(40))
        assertEquals(listOf(2L, 3L), PlayExportRules.merge(c(0), c(3, 1, 2, 3), 2).times)
    }

    @Test fun itSaysWhatItAdded() {
        assertEquals("Nothing new in it: this device had all of it already", PlayExportRules.done(0, 0, 0, 0))
        assertEquals("Added 12 plays of 3 songs, 2 skips and songs heard out, and 1 stretch of listening", PlayExportRules.done(3, 12, 2, 1))
    }
}
