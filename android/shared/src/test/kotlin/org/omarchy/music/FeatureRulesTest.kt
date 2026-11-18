package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Featured on: another artist's songs that credit the artist, as songs; a release only when all of it features them. */
class FeatureRulesTest {
    private fun t(title: String, artist: String, album: String = "") = Track("id-$title-$artist", title = title, artist = artist, album = album)
    private val credits = { x: Track -> "joji" in x.artist.lowercase() || "joji" in x.title.lowercase() }

    @Test fun aSongTheyLeadIsTheirsWithOrWithoutGuests() {
        assertTrue(FeatureRules.leads("Joji", "Joji"))
        assertTrue(FeatureRules.leads("Joji, Diplo", "Joji"))
        assertTrue(FeatureRules.leads("JOJI feat. Clams Casino", "joji"))
        assertFalse(FeatureRules.leads("Rich Brian, Joji", "Joji"))
        assertFalse(FeatureRules.leads("", "Joji"))
        assertFalse(FeatureRules.leads("Joji", ""))
        assertFalse(FeatureRules.isFeature(t("Gimme Love", "Joji"), "Joji", credits))
        assertFalse(FeatureRules.isFeature(t("Daylight", "Joji, Diplo"), "Joji", credits))
        assertTrue(FeatureRules.isFeature(t("Midsummer Madness", "88rising, Joji, Rich Brian"), "Joji", credits))
        assertTrue(FeatureRules.isFeature(t("Where Does the Time Go (feat. Joji)", "Rich Brian"), "Joji", credits))
        assertFalse("another artist's song that doesn't credit them", FeatureRules.isFeature(t("History", "Rich Brian"), "Joji", credits))
    }

    @Test fun oneFeatureDoesNotBringInTheWholeAlbum() {
        val album = Album("a1", "The Sailor") to listOf(t("Yellow", "Rich Brian"), t("Where Does the Time Go", "Rich Brian, Joji"), t("Kids", "Rich Brian"))
        val f = FeatureRules.featured("Joji", listOf(album), emptyList(), credits)
        assertTrue(f.albums.isEmpty())
        assertEquals(listOf("Where Does the Time Go"), f.songs.map { it.title })
        assertEquals("with its release's name", "The Sailor", f.songs[0].album)
    }

    @Test fun aReleaseThatFeaturesThemThroughoutIsShownAsARelease() {
        val all = Album("a2", "Head in the Clouds") to listOf(t("One", "88rising, Joji"), t("Two", "88rising, Joji"))
        val own = Album("a3", "Nectar") to listOf(t("Gimme Love", "Joji"), t("Daylight", "Joji, Diplo"))
        val single = Album("a4", "One") to listOf(t("One", "88rising, Joji"))
        val f = FeatureRules.featured("Joji", listOf(all, own, single), listOf(t("One", "88rising, Joji"), t("Peach Jam", "BlocBoy JB, Joji"), t("peach jam", "BlocBoy JB, Joji"), t("Run", "Joji")), credits)
        assertEquals(listOf("a2"), f.albums.map { it.first.id })
        assertEquals("their own songs stay in their discography; a song on that release isn't listed again; each once",
            listOf("Peach Jam"), f.songs.map { it.title })
    }

    @Test fun nothingFeaturedIsNothing() {
        val f = FeatureRules.featured("Joji", emptyList(), listOf(t("Run", "Joji")), credits)
        assertTrue(f.albums.isEmpty() && f.songs.isEmpty())
    }
}
