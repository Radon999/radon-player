package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared names code, as the phone and the PC both use it (the desktop's own tests say the same). */
class NamesTest {
    @Test fun editions() {
        assertEquals("Persona" to "Extended", albumEdition("Persona (Extended)"))
        assertEquals("Moon Music" to "Full Moon Edition", albumEdition("Moon Music (Full Moon Edition)"))
        assertEquals("Hope (Sessions)" to "", albumEdition("Hope (Sessions)"))
    }

    @Test fun singles() {
        assertTrue(isSingle("Talk - Single", "Talk"))
        assertTrue(isSingle("The Search", "The Search"))
        assertFalse(isSingle("Perception", "Leave Me Alone"))
        assertEquals(1, albumKind("South - EP"))
        assertEquals(3, albumKind("Persona (Extended)"))
        assertTrue(isEp("Remote EP"))
    }

    @Test fun liveNames() {
        assertEquals("Song", stripLive("Song (Live)"))
        assertEquals("Song", stripLive("Song - Live at Red Rocks"))
        assertEquals("Live Forever", stripLive("Live Forever"))
        assertTrue(hasLive("Song [Live]"))
    }

    @Test fun matching() {
        assertEquals(norm("Beyoncé"), norm("Beyonce"))
        assertTrue(similar("heavydirtysoul", "heavy dirty soul") > 0.85)
        assertEquals("The Original Motion Picture Soundtrack", albumWithoutPart("The Original Motion Picture Soundtrack: Pt. 2"))
    }
}
