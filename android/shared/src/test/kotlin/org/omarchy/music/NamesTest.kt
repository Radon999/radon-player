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

    @Test fun movieNames() {
        assertEquals("What Was I Made For?", movieName("What Was I Made For? [From The Motion Picture \"Barbie\"]"))
        assertEquals("Song", movieName("Song (From the Original Motion Picture Soundtrack)"))
        assertEquals("Song", movieName("Song - From the Netflix Series \"Arcane\""))
        assertEquals("Song", movieName("Song (Original Motion Picture Soundtrack)"))
        assertEquals("Song (Live)", movieName("Song (Live) [From “Wicked”]"))
        assertEquals(null, movieName("From Me to You"))
        assertEquals(null, movieName("Song (From Me to You)"))
        assertEquals(null, movieName("Soundtrack 2 My Life"))
    }

    @Test fun fileRenames() {
        assertEquals("C:/M/03 - New Song.flac", renamedFile("C:/M/03 - Old Song.flac", "Old Song", "New Song"))
        assertEquals("C:\\M\\Artist - What_.mp3", renamedFile("C:\\M\\Artist - Who.mp3", "Who", "What?"))
        assertEquals("/m/07 New.flac", renamedFile("/m/07 track7.flac", "Something", "New"))
        assertEquals(null, renamedFile("/m/Same.flac", "Same", "Same"))
    }

    @Test fun matching() {
        assertEquals(norm("Beyoncé"), norm("Beyonce"))
        assertTrue(similar("heavydirtysoul", "heavy dirty soul") > 0.85)
        assertEquals("The Original Motion Picture Soundtrack", albumWithoutPart("The Original Motion Picture Soundtrack: Pt. 2"))
    }

    @Test fun sameSongAnywhere() {
        assertEquals(statsKey("Heavydirtysoul", "Twenty One Pilots"), statsKey("Heavydirtysoul (Remastered)", "twenty one pilots feat. X"))
        assertEquals("kflay", statsKey("Song", "K.Flay").second)
        assertEquals("0:42", fmtTime(42.0))
    }
}
