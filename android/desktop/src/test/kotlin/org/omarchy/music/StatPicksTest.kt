package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Your picks in the stats: a pair kept apart is remembered once, a joint credit's pick is set and taken back, and
 *  the cover of the song playing is kept under a name of its own. What was picked before is put back. */
class StatPicksTest {
    @Test fun aPairKeptApartIsRememberedOnce() {
        Harness.ctx
        val had = Music.similarKept
        try {
            Harness.ui {
                StatPicks.keepApart("Picks Test One", "Picks Test Two")
                StatPicks.keepApart("picks-test one", "Picks Test Two")
                assertEquals(had.size + 1, Music.similarKept.size)
                assertTrue(StatPickRules.apartKey("Picks Test One", "Picks Test Two") in Music.similarKept)
            }
        } finally {
            Harness.ui { Music.similarKept = had; Music.saveState() }
        }
    }

    @Test fun aJointCreditsPickIsSetAndTakenBack() {
        Harness.ctx
        val had = Music.artistPicks
        val credit = "Picks Test One & Picks Test Two"
        try {
            Harness.ui {
                StatPicks.setArtistPick(credit, "Picks Test One")
                assertEquals("Picks Test One", Music.artistPicks[pairKey(credit)])
                StatPicks.setArtistPick(credit, null)
                assertFalse(pairKey(credit) in Music.artistPicks)
            }
        } finally {
            Harness.ui { Music.artistPicks = had; Music.saveState() }
        }
    }

    @Test fun eachFilesCoverHasItsOwnNameInTheCache() {
        val a = NowArt.cacheName("/music/a/song.flac"); val b = NowArt.cacheName("/music/b/song.flac")
        assertNotEquals(a, b)
        assertEquals("the same file, the same name", a, NowArt.cacheName("/music/a/song.flac"))
        assertTrue(a.endsWith(".jpg") && a.length == 28)
    }
}
