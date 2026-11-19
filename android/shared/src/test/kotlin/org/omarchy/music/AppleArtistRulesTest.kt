package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** A play from Apple's Play Activity file that came without its artist finds it by the song's title. */
class AppleArtistRulesTest {
    @Test fun theArtistATitleWasPlayedMostBy() {
        val by = AppleArtistRules.byTitle(listOf(
            Triple("Golden", "Hippo Campus", 40), Triple("Golden", "Harry Styles", 3), Triple("Golden", "Harry Styles", 2),
            Triple("Separator", "Radiohead", 12), Triple("", "Nobody", 5), Triple("Untitled", "", 5)))
        assertEquals("Hippo Campus", AppleArtistRules.artist(by, "Golden"))
        assertEquals("Radiohead", AppleArtistRules.artist(by, "Separator (Remastered)"))
        assertEquals("Radiohead", AppleArtistRules.artist(by, "SEPARATOR"))
        assertEquals("", AppleArtistRules.artist(by, "Untitled"))
        assertEquals("", AppleArtistRules.artist(by, "Never heard of it"))
    }
}
