package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaysApartTest {
    private fun song(album: String, secs: Double, title: String = "Yesterday") =
        Track("$album-$title", title = title, artist = "Imagine Dragons", album = album, duration = secs)

    @Test fun twoSongsOfOneNameAreToldApartByLength() {
        val split = PlaysApart.distinct(listOf(song("Evolve", 205.0), song("It's Time EP Sessions", 251.0)))
        val versions = split[statsKey("Yesterday", "Imagine Dragons")]
        assertEquals("two songs", 2, versions?.size)
        assertEquals("evolve", PlaysApart.versionOf("Evolve (Deluxe)", versions!!))
        assertEquals("a play on the sessions is theirs", PlaysApart.albumKey("It's Time EP Sessions"), PlaysApart.versionOf("It's Time EP Sessions", versions))
        assertNull("no album: not known", PlaysApart.versionOf("", versions))
    }

    @Test fun oneRecordingOnTwoAlbumsStaysOneSong() {
        // a remaster, a compilation, a single named after the song: the same song, its plays added up as before
        val same = listOf(song("Night Visions", 187.0, "Radioactive"), song("Night Visions (Deluxe)", 187.0, "Radioactive"),
            song("Greatest Hits", 188.5, "Radioactive"), song("Radioactive", 170.0, "Radioactive"))
        assertTrue(PlaysApart.distinct(same).isEmpty())
    }

    @Test fun theMainOneKeepsItsKeyAndTheOtherIsMarked() {
        val versions = listOf(listOf(PlaysApart.albumKey("Evolve")), listOf(PlaysApart.albumKey("It's Time EP Sessions")))
        val split = mapOf(statsKey("Yesterday", "Imagine Dragons") to versions)
        val base = songKey(song("Evolve", 205.0))
        assertEquals(base, PlaysApart.key(base, "Yesterday", "Imagine Dragons", "Evolve", split))
        assertEquals("not known: the main one", base, PlaysApart.key(base, "Yesterday", "Imagine Dragons", "", split))
        val other = PlaysApart.key(base, "Yesterday", "Imagine Dragons", "It's Time EP Sessions", split)
        assertTrue("the other one apart", other != base)
        assertEquals("another song is left alone", base, PlaysApart.key(base, "Demons", "Imagine Dragons", "Evolve", split))
    }
}
