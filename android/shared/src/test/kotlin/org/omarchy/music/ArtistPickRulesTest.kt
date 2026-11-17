package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Two bands with one name, every song of an artist, and what a Genius search shows. */
class ArtistPickRulesTest {
    private fun t(title: String, artist: String, album: String = "") = Track("id-$title-$artist", title = title, artist = artist, album = album)

    @Test fun thePickIsKeptUnderTheNameHoweverItIsWritten() {
        assertEquals(ArtistLookupRules.pickKey("HOAX"), ArtistLookupRules.pickKey("Hoax"))
        assertEquals("tidal" to "12345", ArtistLookupRules.readPick(ArtistLookupRules.pick("tidal", "12345")))
        assertNull(ArtistLookupRules.readPick(""))
        assertNull(ArtistLookupRules.readPick(null))
        assertNull(ArtistLookupRules.readPick("tidal:"))
    }

    @Test fun oneOfThemIsToldByASongItIsKnownFor() {
        assertEquals("known for “Drew”", ArtistLookupRules.knownFor("Drew"))
        assertEquals("", ArtistLookupRules.knownFor(null))
        assertEquals("", ArtistLookupRules.knownFor(" "))
    }

    @Test fun everySongIsListedOnce() {
        val single = Album("1", "TV - Single"); val album = Album("2", "b?"); val comp = Album("3", "Indie Now")
        val all = ArtistLookupRules.everySong(listOf(
            single to listOf(t("TV", "HOAX")),
            album to listOf(t("TV", "HOAX", "b?"), t("Drew", "HOAX", "b?"), t("Drew (Live)", "HOAX", "b?")),
            comp to listOf(t("Someone Else's Song", "Other Band"), t("Soju", "HOAX")))) { it.artist == "HOAX" }
        assertEquals(listOf("TV", "Drew", "Drew (Live)", "Soju"), all.map { it.title })
        assertEquals("a song with no album named takes its release's", "TV - Single", all[0].album)
        assertEquals("Indie Now", all[3].album)
        assertTrue(ArtistLookupRules.everySong(emptyList()) { true }.isEmpty())
    }

    @Test fun geniusShowsSongsAndNotItsOwnPages() {
        val hits = listOf(GeniusRules.Hit("TV", "HOAX", "https://images.genius.com/x.jpg"),
            GeniusRules.Hit("TV (English Translation)", "Genius English Translations"),
            GeniusRules.Hit("TV", "Hoax", "https://assets.genius.com/images/default_cover_image.png"),
            GeniusRules.Hit("", "HOAX"), GeniusRules.Hit("Another", "Someone", "https://assets.genius.com/images/default_cover_image.png"))
        val songs = GeniusRules.songs(hits)
        assertEquals(listOf("TV", "Another"), songs.map { it.title })
        assertEquals("https://images.genius.com/x.jpg", songs[0].art)
        assertEquals("Genius's placeholder is no cover", "", songs[1].art)
        assertFalse(GeniusRules.isSong(GeniusRules.Hit("X", "genius romanizations")))
    }
}
