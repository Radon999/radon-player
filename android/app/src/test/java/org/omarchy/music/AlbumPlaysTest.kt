package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0: Plays on singles and albums: a song's plays on its single, an EP and its album, onto the album. */
class AlbumPlaysTest {
    @Test fun kinds() {
        assertEquals(0, albumKind("Songs", "Songs"))
        assertEquals(1, albumKind("South - EP"))
        assertEquals(2, albumKind("Persona"))
        assertEquals(3, albumKind("Persona (Extended)"))
        assertEquals(2, albumKind("Epic Album"))
    }

    @Test fun plan() {
        val h = "half•alive"
        val plays = listOf(AlbumPlay("Songs", h, "Songs", 5), AlbumPlay("Songs", h, "Persona", 7), AlbumPlay("Hot Mess", h, "Persona", 3),
            AlbumPlay("Ruby", "Bad Suns", "Ruby EP", 4), AlbumPlay("Ruby", "Bad Suns", "Ruby - Single", 1))
        val rows = albumPlaysPlan(plays, emptyList(), emptyMap()).associateBy { it.title }
        assertEquals("Persona", rows["Songs"]!!.to)
        assertEquals("Ruby EP", rows["Ruby"]!!.to)
        assertTrue("Hot Mess" !in rows)
        val picks = mapOf(albumPlayKey("Songs", h, "Songs") to "Persona")
        assertTrue(albumPlaysPlan(plays, emptyList(), picks).none { it.title == "Songs" })
    }

    @Test fun duplicates() {
        val songs = listOf(Stats.Song("N.P.D", "Last Dinosaurs", plays = 52), Stats.Song("NPD", "Last Dinosaurs", plays = 2),
            Stats.Song("creature", "half•alive", plays = 84), Stats.Song("Creature - Acoustic", "half•alive", plays = 106))
        val rows = duplicateSongsPlan(songs, emptyMap()).associateBy { it.title }
        assertEquals("N.P.D", rows["NPD"]!!.to.first)
        assertEquals("creature", rows["Creature - Acoustic"]!!.to.first)
        assertTrue(rows["Creature - Acoustic"]!!.version)
        assertEquals("The Original Motion Picture Soundtrack", albumWithoutPart("The Original Motion Picture Soundtrack: Pt. 2"))
        assertEquals("Part of Me", albumWithoutPart("Part of Me"))
    }
}
