package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the app remembers, checked as it's read: a choice it no longer knows becomes the usual one. */
class RememberedRulesTest {
    @Test fun aTimeTheAppKnowsIsKept() {
        assertEquals("month", RememberedRules.period("month"))
        assertEquals("all", RememberedRules.period("decade"))
        assertEquals("all", RememberedRules.period(""))
    }

    @Test fun theOldListByTimeIsTheArtistsNow() {
        assertEquals("time_artists", RememberedRules.topKind("time"))
        assertEquals("time_albums", RememberedRules.topKind("time_albums"))
        assertEquals("songs", RememberedRules.topKind("genres"))
    }

    @Test fun aCollageSizeTheAppDoesNotOfferIsTheFullOne() {
        assertEquals(10, RememberedRules.collage(10))
        assertEquals(25, RememberedRules.collage(12))
        assertEquals(25, RememberedRules.collage(0))
    }

    @Test fun unknownChoicesBecomeTheUsualOnes() {
        assertEquals("heard", RememberedRules.skipOrder("heard"))
        assertEquals("most", RememberedRules.skipOrder("newest"))
        assertEquals("replace", RememberedRules.dupeAction("replace"))
        assertEquals("ask", RememberedRules.dupeAction("delete"))
    }

    @Test fun onlyTheTabsThereAreAreKept() {
        assertEquals(mapOf("a.flac" to "ep", "b.flac" to "single"),
            RememberedRules.tabPicks(mapOf("a.flac" to "ep", "b.flac" to "single", "c.flac" to "mixtape")))
    }

    @Test fun musicItselfIsToldFromAFolderInIt() {
        assertTrue(RememberedRules.musicItself("Music/"))
        assertTrue(RememberedRules.musicItself(" music\\ "))
        assertFalse(RememberedRules.musicItself("Music/Mine/"))
        assertFalse(RememberedRules.musicItself(""))
    }
}
