package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistLookupRulesTest {
    @Test fun aPairIsAlsoTriedByItsFirstName() {
        assertEquals(listOf("Simon & Garfunkel", "Simon"), ArtistLookupRules.names("Simon & Garfunkel"))
        assertEquals(listOf("Cavetown"), ArtistLookupRules.names("Cavetown"))
    }

    @Test fun theServiceAskedForComesFirstAndEachIsAskedOnce() {
        assertEquals(listOf("tidal", "ytm", "apple", "spotify"), ArtistLookupRules.order("tidal", "ytm"))
        assertEquals(listOf("apple", "ytm", "tidal", "spotify"), ArtistLookupRules.order(null, "apple"))
        assertEquals(listOf("soundcloud", "ytm", "tidal", "apple", "spotify"), ArtistLookupRules.order("soundcloud", "ytm"))
    }

    @Test fun tidalAndSpotifyOnlyWhenSignedIn() {
        assertFalse(ArtistLookupRules.asked("tidal", signedIn = false))
        assertTrue(ArtistLookupRules.asked("tidal", signedIn = true))
        assertFalse(ArtistLookupRules.asked("spotify", signedIn = false))
        assertTrue(ArtistLookupRules.asked("ytm", signedIn = false))
    }

    @Test fun eachArtistIsLookedUpOnce() {
        assertEquals(listOf("K.Flay", "Sody"), ArtistLookupRules.once(listOf("K.Flay", "k.flay", "Sody")) { it.lowercase() })
    }
}
