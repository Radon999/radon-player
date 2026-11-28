package org.omarchy.music

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WholeArtistsTest {
    @After fun back() = WholeArtists.set(emptyList())

    @Test fun aNameWithACommaInItIsOneArtist() {
        assertEquals(listOf("Tyler, The Creator"), splitArtists("Tyler, The Creator"))
        assertEquals(listOf("Tyler, The Creator", "Frank Ocean"), splitArtists("Tyler, The Creator, Frank Ocean"))
        assertEquals(listOf("Kali Uchis", "Tyler, The Creator"), splitArtists("Kali Uchis feat. Tyler, The Creator"))
        assertEquals(listOf("Earth, Wind & Fire"), ownArtists("Earth, Wind & Fire"))
        assertEquals(listOf("Earth, Wind & Fire"), duoParts("Earth, Wind & Fire"))
    }

    @Test fun itsPlaysAreItsOwnNotItsFirstWords() {
        assertEquals(statsKey("", "tyler, the creator").second, statsKey("", "Tyler, The Creator, Frank Ocean").second)
        assertFalse(statsKey("", "Tyler, The Creator").second == statsKey("", "Tyler").second)
    }

    @Test fun twoArtistsAreStillTwo() {
        assertEquals(listOf("Post Malone", "Swae Lee"), splitArtists("Post Malone, Swae Lee"))
        assertEquals(listOf("Tyler Childers"), splitArtists("Tyler Childers"))
    }

    @Test fun yourOwnNamesStayWholeTooAndCanBeTakenBack() {
        assertEquals(listOf("Me", "My", "Friends"), splitArtists("Me, My, Friends"))
        WholeArtists.set(WholeArtists.parse(" Me, My, Friends \n\n"))
        assertTrue(WholeArtists.has("me, my, friends"))
        assertEquals(listOf("Me, My, Friends", "Halsey"), splitArtists("Me, My, Friends, Halsey"))
        assertEquals(norm("me, my, friends").replace(" ", ""), statsKey("", "Me, My, Friends").second.replace(" ", ""))
        WholeArtists.set(emptyList())
        assertEquals(listOf("Me", "My", "Friends"), splitArtists("Me, My, Friends"))
    }

    @Test fun aNameInsideALongerWordIsLeftAlone() {
        WholeArtists.set(listOf("Now, Now"))
        assertEquals(listOf("Know", "Nowhere"), splitArtists("Know, Nowhere"))
    }
}
