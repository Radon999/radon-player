package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A play is a song's only when its title, artist and album are the song's. */
class PlaysApartTest {
    private val k = statsKey("Yesterday", "Imagine Dragons")
    private val split = mapOf(k to listOf(listOf(PlaysApart.albumKey("Evolve")), listOf(PlaysApart.albumKey("It's Time EP Sessions"))))
    private fun key(album: String) = PlaysApart.key(k, "Yesterday", "Imagine Dragons", album, split)

    @Test fun eachAlbumItsOwn() {
        assertEquals("the main album keeps the song's key", k, key("Evolve"))
        assertEquals("its editions are the same album", k, key("Evolve (Deluxe)"))
        assertEquals("no album: the main one", k, key(""))
        val other = key("It's Time EP Sessions")
        assertTrue("the other album apart", other != k)
        assertEquals("written a little another way: the same", other, key("It's Time (EP Sessions)"))
        val never = key("Greatest Hits")
        assertTrue("an album none of its plays were on: its own (none counted)", never != k && never != other)
        assertEquals("another song is left alone", statsKey("Demons", "Imagine Dragons"),
            PlaysApart.key(statsKey("Demons", "Imagine Dragons"), "Demons", "Imagine Dragons", "Evolve", split))
    }

    @Test fun aNearNameOnlyWhenItsMostlyTheSame() {
        val vs = listOf(listOf(PlaysApart.albumKey("Night Visions Live")))
        assertNull("Night Visions isn't Night Visions Live", PlaysApart.versionOf("Night Visions", vs))
        val sessions = listOf(listOf(PlaysApart.albumKey("It's Time EP Sessions")))
        assertEquals("one short word more", sessions[0][0], PlaysApart.versionOf("It's Time Sessions", sessions))
        assertNull("not a whole other album of the same first words", PlaysApart.versionOf("It's Time", sessions))
    }

    @Test fun aSingleNamedAfterItsSongIsNoAlbumOfItsOwn() {
        org.junit.Assert.assertTrue(PlaysApart.ownSingle("Next Semester", "Next Semester"))
        org.junit.Assert.assertTrue(PlaysApart.ownSingle("Next Semester", "Next Semester - Single"))
        org.junit.Assert.assertTrue(PlaysApart.ownSingle("Sunflower (feat. Swae Lee)", "Sunflower"))
        org.junit.Assert.assertFalse(PlaysApart.ownSingle("Next Semester", "Clancy"))
        org.junit.Assert.assertFalse("two songs of one name stay apart by their albums", PlaysApart.ownSingle("Yesterday", "Evolve"))
        org.junit.Assert.assertFalse(PlaysApart.ownSingle("Yesterday", ""))
    }
}
