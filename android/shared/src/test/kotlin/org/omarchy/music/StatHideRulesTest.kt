package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StatHideRulesTest {
    @Test fun anEntryIsTheSameHoweverItsNameIsWritten() {
        assertEquals(StatHideRules.song("Blood in the Cut", "K.Flay"), StatHideRules.song("Blood In The Cut", "KFlay"))
        assertEquals(StatHideRules.artist("K.Flay"), StatHideRules.artist("kflay"))
        assertEquals(StatHideRules.album("Every Where Is Some Where", "K.Flay"), StatHideRules.album("every where is some where", "KFlay"))
    }

    @Test fun aSongAnAlbumAndAnArtistOfOneNameAreApart() {
        val keys = setOf(StatHideRules.song("Loom", "Loom"), StatHideRules.album("Loom", "Loom"), StatHideRules.artist("Loom"))
        assertEquals(3, keys.size)
        assertNotEquals(StatHideRules.song("Stay", "One"), StatHideRules.song("Stay", "Another"))
        assertEquals(listOf("Song", "Album", "Artist"), listOf(StatHideRules.song("a", "b"), StatHideRules.album("a", "b"), StatHideRules.artist("b")).map { StatHideRules.kind(it) })
    }

    @Test fun settingsSaysHowMany() {
        assertEquals("Nothing is hidden", StatHideRules.count(0))
        assertEquals("3 hidden", StatHideRules.count(3))
    }
}
