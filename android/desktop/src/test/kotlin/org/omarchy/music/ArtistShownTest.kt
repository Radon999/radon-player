package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hiding songs and releases from an artist's page, as the app does it (kept in its settings, read back by the page). */
class ArtistShownTest {
    @Test fun whatYouHideIsKeptReadBackAndSeenAtOnce() {
        Harness.ctx
        val artist = "Hide Test Band " + System.nanoTime()
        try {
            val before = ArtistShown.version
            Harness.ui {
                ArtistShown.songs(artist, listOf(Track("hide-x", "ytm", "Vision", "Someone Else, $artist")), hide = true)
                ArtistShown.albums(artist, listOf("Ubuntu (Deluxe Edition)", "ARMS"), hide = true)
            }
            assertTrue("an open page is told to redraw", ArtistShown.version > before)
            val m = ArtistShown.marks(artist)
            assertEquals(ArtistShownRules.HIDE, m[ArtistShownRules.songKey("vision")])
            assertEquals(ArtistShownRules.HIDE, m[ArtistShownRules.albumKey("Ubuntu")])
            assertEquals(ArtistShownRules.HIDE, m[ArtistShownRules.albumKey("ARMS")])
            assertFalse("hidden: off the page", ArtistShownRules.shown(m[ArtistShownRules.songKey("Vision (Live)")], theirs = true))
            assertTrue("another song stays", ArtistShownRules.shown(m[ArtistShownRules.songKey("Moon Moon Baby")], theirs = true))
            assertEquals("under the artist however the name is written", m, ArtistShown.marks(artist.uppercase()))
            Harness.ui { ArtistShown.albums(artist, listOf("Ubuntu"), hide = false) }
            assertEquals(ArtistShownRules.KEEP, ArtistShown.marks(artist)[ArtistShownRules.albumKey("Ubuntu")])
            assertEquals("the others stay hidden", ArtistShownRules.HIDE, ArtistShown.marks(artist)[ArtistShownRules.albumKey("ARMS")])
        } finally { Prefs.setText(ArtistShownRules.prefKey(artist), "") }
    }
}
