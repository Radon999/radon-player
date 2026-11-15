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
}
