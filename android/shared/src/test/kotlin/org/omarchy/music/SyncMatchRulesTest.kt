package org.omarchy.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMatchRulesTest {
    private fun t(title: String, artist: String, secs: Double, album: String = "") = Track("x", "file", title, artist, album, secs)

    @Test fun aNameALittleDifferentIsStillTheSong() {
        assertTrue(SyncMatchRules.same(t("Oxygen", "Dirty Heads", 225.4), t("Oxygen", "The Dirty Heads", 225.0)))
        assertTrue(SyncMatchRules.same(t("Dont Stop", "K.Flay", 200.0), t("Don't Stop", "KFlay", 200.5)))
        assertTrue("a word more", SyncMatchRules.same(t("Hymn for the Weekend", "Coldplay", 258.0), t("Hymn For The Weekend (feat. Beyoncé)", "Coldplay", 258.3)))
    }

    @Test fun anArtistNamedTwiceIsStillTheArtist() {
        // the same song's key (sync's step 2): the lead artist, punctuation aside
        assertTrue(statsKey("Blood In The Cut", "K.Flay;K.Flay") == statsKey("Blood In The Cut", "KFlay"))
        assertTrue(statsKey("Blood In The Cut", "K.Flay, K Flay") == statsKey("Blood In The Cut", "K Flay"))
        // the near match (step 4): the name twice with no mark between
        assertTrue(SyncMatchRules.same(t("Blood In The Cut", "K Flay K Flay", 210.0), t("Blood In The Cut", "K.Flay", 210.2)))
    }

    @Test fun anotherVersionOrAnotherSongIsNot() {
        assertFalse("a rough isn't the song", SyncMatchRules.same(t("Oxygen", "Imagine Dragons", 202.7), t("Oxygen (Rough)", "Imagine Dragons", 202.7)))
        assertFalse("v1 isn't v2", SyncMatchRules.same(t("Prime Time (v1)", "Juice WRLD", 217.0), t("Prime Time (v2)", "Juice WRLD", 217.0)))
        assertFalse("live", SyncMatchRules.same(t("Believer", "Imagine Dragons", 204.0), t("Believer (Live)", "Imagine Dragons", 204.0)))
        assertFalse("lengths apart", SyncMatchRules.same(t("Oxygen", "Dirty Heads", 225.0), t("Oxygen", "The Dirty Heads", 228.0)))
        assertFalse("another artist", SyncMatchRules.same(t("Oxygen", "Dirty Heads", 225.0), t("Oxygen", "Imagine Dragons", 225.0)))
        assertFalse("no length", SyncMatchRules.same(t("Oxygen", "Dirty Heads", 0.0), t("Oxygen", "Dirty Heads", 0.0)))
    }
}
