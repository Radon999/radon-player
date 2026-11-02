package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class GenreDupesTest {
    @Test fun keys() {
        assertEquals(genreKey("Hip-Hop/Rap"), genreKey("Hip-Hop-Rap"))
        assertEquals(genreKey("Hip-Hop/Rap"), genreKey("hip hop & rap"))
        assertEquals(genreKey("R&B"), genreKey("R and B"))
        assertEquals("pop", genreKey("Pop"))
        assert(genreKey("Rock") != genreKey("Pop Rock"))
    }

    @Test fun groups() {
        val d = genreDupes(mapOf("Hip-Hop/Rap" to 287, "Hip-Hop-Rap" to 295, "Pop" to 641, "pop" to 3, "Rock" to 64, "Unknown genre" to 9))
        assertEquals(listOf("Pop" to listOf("pop"), "Hip-Hop-Rap" to listOf("Hip-Hop/Rap")), d)
    }
}
