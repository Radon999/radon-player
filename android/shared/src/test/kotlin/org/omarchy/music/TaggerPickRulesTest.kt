package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tagging a selection with streaming songs in it: those are skipped, and it says so. */
class TaggerPickRulesTest {
    @Test fun streamingSongsAreSkippedAndSaid() {
        assertEquals("", TaggerPickRules.skipped(0, 5))
        assertEquals("1 streaming song was skipped: download it to be able to tag it", TaggerPickRules.skipped(1, 4))
        assertEquals("3 streaming songs were skipped: download them to be able to tag them", TaggerPickRules.skipped(3, 2))
        assertEquals("This is a streaming song: download it to be able to tag it", TaggerPickRules.skipped(1, 0))
        assertEquals("These 12 are streaming songs: download them to be able to tag them", TaggerPickRules.skipped(12, 0))
    }
}
