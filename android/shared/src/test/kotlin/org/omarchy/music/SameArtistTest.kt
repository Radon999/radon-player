package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One artist under two names, and an album's songs in its own order. */
class SameArtistTest {
    @Test fun theNameYouGaveAnArtistIsTheOneTheServicesCredit() {
        assertTrue("the owner's case", SameArtist.alias("Forrest", "Forrest Frank, Connor Price"))
        assertTrue(SameArtist.alias("Forrest Frank", "Forrest"))
        assertTrue(SameArtist.alias("Forrest Frank feat. Connor Price", "Forrest"))
        assertTrue("an artist they share", SameArtist.alias("Sigrid", "Sigrid, Bring Me The Horizon"))
        assertTrue("written another way", SameArtist.alias("K.Flay", "K Flay"))
    }

    @Test fun otherArtistsStayOthers() {
        assertFalse(SameArtist.alias("Forrest", "Connor Price"))
        assertFalse("the last word isn't the first", SameArtist.alias("Frank", "Forrest Frank"))
        assertFalse("a part of a word isn't a word", SameArtist.alias("Forr", "Forrest Frank"))
        assertFalse("too short to tell", SameArtist.alias("The", "The Weeknd"))
        assertFalse(SameArtist.alias("", "Forrest Frank"))
    }

    @Test fun aSongsNameLeavesOutItsVersionAndGuests() {
        assertEquals(SameArtist.title("SOS"), SameArtist.title("SOS (feat. Connor Price)"))
        assertEquals(SameArtist.title("Don't Look Down"), SameArtist.title("Don't Look Down - Remastered 2011"))
    }

    @Test fun anAlbumsSongsAreInTrackOrderWithTheUnnumberedLast() {
        fun song(id: String) = Track(id, "file", "Song $id", "Order Band")
        val nos = mapOf("c" to 3, "a" to 1, "new" to 2, "none" to 9999, "zero" to 0, "d" to 4)
        val came = listOf("new", "c", "none", "a", "zero", "d").map(::song)   // a download just in comes first
        assertEquals(listOf("a", "new", "c", "d", "none", "zero"), AlbumOrder.sorted(came) { nos.getValue(it.id) }.map { it.id })
        assertEquals("none numbered: as they came", came.map { it.id }, AlbumOrder.sorted(came) { 0 }.map { it.id })
    }

    @Test fun aTwoDiscAlbumIsDiscOneThenDiscTwo() {
        fun song(id: String) = Track(id, "file", "Song $id", "Order Band")
        val place = mapOf("d2t1" to (2 to 1), "d1t2" to (1 to 2), "d2t2" to (2 to 2), "d1t1" to (1 to 1), "nodisc-t3" to (0 to 3), "none" to (2 to 0))
        val came = listOf("d2t1", "d1t2", "d2t2", "d1t1", "nodisc-t3", "none").map(::song)
        assertEquals("a song that says no disc is on the first; one with no number is last",
            listOf("d1t1", "d1t2", "nodisc-t3", "d2t1", "d2t2", "none"),
            AlbumOrder.sorted(came, disc = { place.getValue(it.id).first }) { place.getValue(it.id).second }.map { it.id })
    }

    @Test fun theUnreleasedTabHoldsFoldersNamedSoAndTheOnesYouChoose() {
        assertTrue(UnreleasedRules.has("C:\\Users\\me\\Music\\Artists\\UNRELEASED\\Coldplay"))
        assertTrue("on the phone, any case", UnreleasedRules.has("Music/Artists/Unreleased/k.flay West Ghost/"))
        assertTrue("songs right in it", UnreleasedRules.has("Music/unreleased"))
        assertFalse(UnreleasedRules.has("Music/Artists/Coldplay/Parachutes"))
        assertFalse("part of a name isn't the name", UnreleasedRules.has("Music/Artists/Unreleased demos club/A"))
        val mine = listOf("Music/Leaks", "D:\\Rips\\Demos\\")
        assertTrue(UnreleasedRules.has("Music/Leaks/Somebody/Tape", mine))
        assertTrue(UnreleasedRules.has("d:/rips/demos", mine))
        assertFalse("beside it, not in it", UnreleasedRules.has("Music/Leaks 2/Somebody", mine))
        assertFalse(UnreleasedRules.has("Music/Artists/Coldplay", mine))
    }

    @Test fun aFolderInTheUnreleasedTabIsNamedFromBelowIt() {
        assertEquals("Coldplay", UnreleasedRules.nameIn("C:\\Users\\me\\Music\\Artists\\UNRELEASED\\Coldplay"))
        assertEquals("castle comer / lone survivor", UnreleasedRules.nameIn("Music/Artists/UNRELEASED/castle comer/lone survivor/"))
        assertEquals("songs right in it: its own name", "UNRELEASED", UnreleasedRules.nameIn("Music/Artists/UNRELEASED"))
        assertEquals("Somebody / Tape", UnreleasedRules.nameIn("Music/Leaks/Somebody/Tape", listOf("Music/Leaks")))
        assertEquals("Leaks", UnreleasedRules.nameIn("Music/Leaks", listOf("Music/Leaks")))
    }
}
