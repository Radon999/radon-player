package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverMatchTest {
    @Test fun theSameSongByTheSameArtistFits() {
        assertEquals(2, coverFit("All Girls Are the Same", "Juice WRLD", "All Girls Are The Same", "Juice WRLD"))
        assertEquals(2, coverFit("Little Grace", "Hippo Campus", "Little Grace", "Hippo Campus, Someone"))
        assertEquals(2, coverFit("Bad Dream Baby", "Hippo Campus feat. X", "Bad Dream Baby", "Hippo Campus"))
    }

    @Test fun aDemoFitsItsSongLess() {
        assertEquals(1, coverFit("Chapstick - Demo", "Hippo Campus", "Chapstick", "Hippo Campus"))
        assertTrue(coverFit("Lucid Dreams", "Juice WRLD", "Lucid Dreams (Forget Me)", "Juice WRLD") >= 1)
    }

    @Test fun someoneElsesSongDoesNotFit() {
        assertEquals(0, coverFit("Boys", "Hippo Campus", "Boys", "Charli XCX"))
        assertEquals(0, coverFit("Boys", "Hippo Campus", null, "Hippo Campus"))
    }

    @Test fun theSameAlbumFirstThenTheSameName() {
        val found = listOf(listOf("Boys", "Charli XCX", "Boys", "c1"), listOf("Boys - Demo", "Hippo Campus", "Demos", "c2"),
            listOf("Boys", "Hippo Campus", "Bambi", "c3"), listOf("Boys", "Hippo Campus", "EP", "c4"))
        assertEquals("c4", bestCover("Boys", "Hippo Campus", "EP", found))
        assertEquals("c3", bestCover("Boys", "Hippo Campus", "", found))
        assertEquals("", bestCover("Nope", "Hippo Campus", "", found))
    }

    @Test fun itunesCoversAreMadeBig() {
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music/x/600x600bb.jpg",
            itunesBig("https://is1-ssl.mzstatic.com/image/thumb/Music/x/100x100bb.jpg"))
    }
}
