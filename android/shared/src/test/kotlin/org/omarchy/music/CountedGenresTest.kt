package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class CountedGenresTest {
    @Test fun singerAndSongwriterAreNotCounted() {
        assertEquals(listOf("Rap"), countedGenres("Rap; Singer/Songwriter"))
        assertEquals(listOf("Hip-Hop"), countedGenres("Singer, Songwriter, Hip-Hop"))
        assertEquals(emptyList<String>(), countedGenres("Singer-Songwriter"))
        assertEquals("the tag itself is left as it is", listOf("Rap", "Singer", "Songwriter"), genreParts("Rap; Singer/Songwriter"))
    }
}
