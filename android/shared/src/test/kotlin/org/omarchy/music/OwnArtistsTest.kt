package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnArtistsTest {
    @Test fun bandNamesStayWhole() {
        assertEquals(listOf("Of Monsters and Men"), ownArtists("Of Monsters and Men"))
        assertEquals(listOf("Fitz and The Tantrums"), ownArtists("Fitz and The Tantrums"))
        assertEquals(listOf("Esquivel and His Orchestra"), ownArtists("Esquivel and His Orchestra"))
        assertEquals(listOf("Mumford & Sons"), ownArtists("Mumford & Sons"))
        assertEquals(listOf("Tyler, The Creator"), ownArtists("Tyler, The Creator"))
    }

    @Test fun collaborationsSplit() {
        assertEquals(listOf("Lil Nas X", "Jack Harlow"), ownArtists("Lil Nas X & Jack Harlow"))
        assertEquals(listOf("Quinn XCII", "Chelsea Cutler"), ownArtists("Quinn XCII & Chelsea Cutler"))
        assertEquals(listOf("Juice WRLD", "Halsey"), ownArtists("Juice WRLD feat. Halsey"))
        assertEquals(listOf("Of Monsters and Men", "Someone"), ownArtists("Of Monsters and Men & Someone"))
    }
}
