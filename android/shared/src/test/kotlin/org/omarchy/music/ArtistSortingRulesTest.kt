package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Two bands a service mixed under one name: told apart by Genius's list of an artist's songs, and by what you hide
 *  from the page or put back on it yourself. */
class ArtistSortingRulesTest {
    private fun h(title: String, artist: String = "HOAX") = GeniusRules.Hit(title, artist)

    @Test fun geniusNamesSameNamedArtistsApartWithAWordInBrackets() {
        assertTrue(GeniusRules.sameNamed("HOAX", "HOAX"))
        assertTrue(GeniusRules.sameNamed("HOAX", "Hoax (UK)"))
        assertTrue(GeniusRules.sameNamed("hoax", "HOAX (Techno)"))
        assertFalse(GeniusRules.sameNamed("HOAX", "Hoaxed"))
        assertFalse(GeniusRules.sameNamed("HOAX", "Poni Hoax"))
        assertFalse(GeniusRules.sameNamed("", ""))
    }

    @Test fun theAlbumsTheSinglesAndTheEpsVersionOfASongAreOneSong() {
        val songs = GeniusRules.artistSongs(listOf(h("TV"), h("TV (Single Version)"), h("TV - EP Version"), h("TV (Album Version)"),
            h("TV (Live)"), h("Drew"), h("drew"), h("TV (English Translation)", "Genius English Translations")))
        assertEquals(listOf("TV", "TV (Live)", "Drew"), songs.map { it.title })
    }

    @Test fun aSongIsTheirsWhenGeniusListsItHoweverTheServiceWritesIt() {
        val theirs = GeniusRules.judge(listOf(h("drew"), h("Moon Moon Baby"), h("b?")))
        assertTrue(theirs("Drew"))
        assertTrue(theirs("Drew (Live)"))
        assertTrue(theirs("Moon Moon Baby - Single Version"))
        assertTrue(theirs("B?"))
        assertFalse(theirs("Trap Mental Rampage"))
        assertFalse("with no list, nothing is theirs (the page then doesn't ask)", GeniusRules.judge(emptyList())("Drew"))
    }

    @Test fun aReleaseIsTheirsWhenHalfItsSongsAre() {
        val theirs = GeniusRules.judge(listOf(h("Drew"), h("Soju")))
        assertTrue(GeniusRules.releaseIsTheirs(listOf("Drew", "A Remix Of Something"), theirs))
        assertTrue(GeniusRules.releaseIsTheirs(listOf("Soju"), theirs))
        assertFalse(GeniusRules.releaseIsTheirs(listOf("Terror", "City", "Drew"), theirs))
        assertFalse(GeniusRules.releaseIsTheirs(listOf("Terror City"), theirs))
        assertTrue("one whose songs aren't known is left in", GeniusRules.releaseIsTheirs(emptyList(), theirs))
    }

    @Test fun theSongsOnlyGeniusListsAreAddedOnce() {
        val missing = GeniusRules.missing(listOf(h("drew"), h("TV"), h("soju")), listOf("Drew - Single", "TV"))
        assertEquals(listOf("soju"), missing.map { it.title })
        assertEquals(3, GeniusRules.missing(listOf(h("drew"), h("TV"), h("soju")), emptyList()).size)
    }

    @Test fun whatYouSaidAboutASongWins() {
        assertTrue(ArtistShownRules.shown(null, theirs = true))
        assertFalse(ArtistShownRules.shown(null, theirs = false))
        assertFalse("hidden by you, though Genius lists it", ArtistShownRules.shown(ArtistShownRules.HIDE, theirs = true))
        assertTrue("put back by you, though Genius doesn't list it", ArtistShownRules.shown(ArtistShownRules.KEEP, theirs = false))
    }

    @Test fun whatYouHidIsKeptUnderTheArtistAndTheSong() {
        assertEquals(ArtistShownRules.prefKey("HOAX"), ArtistShownRules.prefKey("Hoax"))
        assertEquals(ArtistShownRules.songKey("Drew"), ArtistShownRules.songKey("drew (Live)"))
        assertEquals(ArtistShownRules.albumKey("Nectar"), ArtistShownRules.albumKey("Nectar (Deluxe Edition)"))
        assertFalse(ArtistShownRules.songKey("Drew") == ArtistShownRules.albumKey("Drew"))
        var marks = ArtistShownRules.marked(emptyMap(), listOf(ArtistShownRules.songKey("Terror"), ArtistShownRules.songKey("City")), ArtistShownRules.HIDE)
        marks = ArtistShownRules.marked(marks, listOf(ArtistShownRules.songKey("City")), ArtistShownRules.KEEP)
        assertEquals(marks, ArtistShownRules.read(ArtistShownRules.write(marks)))
        assertEquals(ArtistShownRules.HIDE, marks[ArtistShownRules.songKey("terror")])
        assertEquals(ArtistShownRules.KEEP, marks[ArtistShownRules.songKey("City")])
        assertTrue(ArtistShownRules.marked(marks, marks.keys.toList(), null).isEmpty())
        assertEquals(mapOf("s:x" to 'h'), ArtistShownRules.read("garbage\n\nh\ts:x\nz\ts:y"))
        assertTrue(ArtistShownRules.read(null).isEmpty())
    }
}
