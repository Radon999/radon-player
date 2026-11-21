package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaceRulesTest {
    @Test fun theSameSongHasNothingDifferent() {
        assertEquals(emptyList<String>(), ReplaceRules.differs("Reset Me", "Nothing But Thieves", "Broken Machine (Deluxe)", 222.0,
            "Reset Me", "Nothing But Thieves", "Broken Machine (Deluxe)", 223.0))
        assertEquals("a file with no album says nothing of the album", emptyList<String>(),
            ReplaceRules.differs("Stars", "Imagine Dragons", "", 200.0, "Stars", "Imagine Dragons", "Night Visions", 200.0))
    }

    @Test fun anotherSongVersionOrAlbumShows() {
        assertTrue("another title" in ReplaceRules.differs("Prime Time (v2)", "Juice WRLD", "Sessions", 217.0, "Prime", "Juice WRLD", "Sessions", 217.0))
        assertTrue("another artist" in ReplaceRules.differs("New Wave", "Imagine Dragons", "Saints & Sinners", 168.0, "New Wave", "Quinn XCII", "Bloom", 168.0))
        val d = ReplaceRules.differs("Feel So Close (Radio Edit)", "Calvin Harris", "18 Months", 206.0, "Feel So Close (Radio Edit)", "Calvin Harris", "Feel So Close", 331.0)
        assertTrue(d.toString(), "another album" in d && "125 s longer" in d)
        assertTrue("12 s shorter" in ReplaceRules.differs("Kentucky (Demo)", "Hippo Campus", "Demos II - EP", 240.0, "Kentucky (Demo)", "Hippo Campus", "Demos II - EP", 228.0))
    }
}
