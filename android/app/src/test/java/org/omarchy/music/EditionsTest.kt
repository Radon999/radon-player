package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0: an album and its deluxe, extended or anniversary edition are one; singles count for their album. */
class EditionsTest {
    @Test fun editions() {
        assertEquals("Persona" to "Extended", albumEdition("Persona (Extended)"))
        assertEquals("Night Visions" to "Deluxe", albumEdition("Night Visions (Deluxe)"))
        assertEquals("Origins" to "Deluxe Edition", albumEdition("Origins (Deluxe Edition)"))
        assertEquals("Ten" to "10th Anniversary Edition", albumEdition("Ten - 10th Anniversary Edition"))
        assertEquals("Smithereens" to "Deluxe", albumEdition("Smithereens - Deluxe"))
        for (same in listOf("Death Race For Love (Sessions)", "Death Race For Love (Bonus Track Version)", "Persona",
            "Mercury - Acts 1 & 2", "Folklore (Deluxe Sessions)", "Thing (Expanded Edition)"))
            assertEquals(same, "", albumEdition(same).second)
    }

    @Test fun singles() {
        assertTrue(isSingle("Selene", "Selene"))
        assertTrue(isSingle("Selene - Single", "Other"))
        assertFalse(isSingle("Night Visions", "Selene"))
        assertEquals("Quinn XCII", soloName(mapOf("Quinn XCII & Jeremy Zucker" to 50, "Quinn XCII" to 3)))
        assertEquals("Simon & Garfunkel", soloName(mapOf("Simon & Garfunkel" to 5)))
    }
}
