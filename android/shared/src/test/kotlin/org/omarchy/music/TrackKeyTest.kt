package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A song's two keys together: the same answers as asking each key by hand, as the code did before. */
class TrackKeyTest {
    private fun t(title: String, artist: String) = Track("", title = title, artist = artist)

    @Test fun itHoldsBothKeysAsTheyWere() {
        val song = t("Blood In The Cut", "K.Flay, Someone")
        val k = TrackKey.of(song)
        assertEquals(songKey(song), k.song)
        assertEquals(statsKey(song.title, song.artist), k.stats)
        assertEquals(k, TrackKey.of("Blood In The Cut", "K.Flay, Someone"))
    }

    @Test fun eitherKeyFindsTheSong() {
        val song = t("Blood In The Cut", "K.Flay")
        val k = TrackKey.of(song)
        assertTrue(k inAny setOf(k.song))
        assertTrue(k inAny setOf(k.stats))
        assertFalse(k inAny setOf("another song" to "kflay"))
        assertFalse(k inAny emptySet())
    }

    @Test fun theSameAnswerAsAskingEachKeyByHand() {
        val songs = listOf(t("One", "A & B"), t("Two (Live)", "C feat. D"), t("Three", "E, F"), t("Four", "K.Flay"))
        val kept = setOf(statsKey("One", "A & B"), songKey(songs[2]))
        for (s in songs) {
            val byHand = songKey(s) in kept || statsKey(s.title, s.artist) in kept
            assertEquals(s.title, byHand, TrackKey.of(s) inAny kept)
        }
    }

    @Test fun aSetOfSongsKnowsThemUnderEitherName() {
        val set = KeySet.of(listOf(t("One", "A & B")))
        assertTrue(set.has(t("One", "A & B")))
        assertTrue("as Last.fm names it", set.has(t("One", "A")))
        assertFalse(set.has(t("Two", "A & B")))
        assertTrue(KeySet(listOf(statsKey("Four", "K.Flay"))).has(t("Four", "KFlay")))
    }

    @Test fun aPlayedSongIsKeptWithBothKeys() {
        val k = TrackKey.of("Four", "K.Flay")
        assertEquals(listOf(k.song, k.stats), SetRules.recentKeys(k.entry()))
    }
}
