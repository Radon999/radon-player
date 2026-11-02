package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverTrustTest {
    private val scratch = "file:///C:/Users/me/AppData/Local/omarchy-music/cache/single-cover.jpg"

    @Test fun oneScratchFileForDifferentArtistsAlbumsIsNotBelieved() {
        // The old PC app's picks: every album's cover the same file (the last one picked: AJR's).
        val covers = mapOf("sigrid|there s always more that i could say" to scratch, "ajr|the maybe man" to scratch,
            "passion pit|manners" to "https://images.genius.com/x.jpg", "mxmtoon|dawn" to "https://images.genius.com/x.jpg")
        assertEquals(setOf("sigrid|there s always more that i could say", "ajr|the maybe man"), untrustedCovers(covers))
    }

    @Test fun theSameFileWrittenTwoWaysIsOneFile() {
        val covers = mapOf("a||one" to "C:\\Users\\me\\AppData\\Local\\omarchy-music\\cache\\single-cover.jpg", "b|two" to scratch)
        assertEquals(covers.keys, untrustedCovers(covers))
        assertEquals("c:/users/me/x y.jpg", coverFileKey("file:///C:/Users/me/x%20y.jpg"))
        assertEquals("/home/me/x.jpg", coverFileKey("file:///home/me/x.jpg"))
        assertNull(coverFileKey("https://example.com/x.jpg"))
    }

    @Test fun oneArtistsAlbumsOrOneCompilationMayShareAFile() {
        val wp = "file:///C:/Users/me/Desktop/wp.jpg"
        assertTrue(untrustedCovers(mapOf("juice wrld|wav" to wp, "juice wrld|stem edits" to wp)).isEmpty())
        val comp = "/data/covers/abc.jpg"
        assertTrue(untrustedCovers(mapOf("a||the soundtrack" to comp, "b||the soundtrack" to comp)).isEmpty())
        assertTrue(untrustedCovers(mapOf("a||one" to "/x/one.jpg", "b||two" to "/x/two.jpg")).isEmpty())
    }

    @Test fun sameArtistCredits() {
        assertTrue(sameArtist("Sigrid", "Sigrid, Bring Me The Horizon"))
        assertTrue(sameArtist("K.Flay", "K Flay"))
        assertTrue(sameArtist("Sigrid", "SIGRID (NO)"))
        assertFalse(sameArtist("Sigrid", "AJR"))
        assertFalse(sameArtist("Sigrid", ""))
    }
}
