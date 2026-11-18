package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What takes the space, and the damage that can be seen without playing a file. */
class FileCheckRulesTest {
    private fun s(title: String, artist: String, album: String, kind: String, mb: Long) = FileCheckRules.Song(title, artist, album, kind, mb * 1_000_000)

    @Test fun whatTakesTheSpace() {
        val sum = FileCheckRules.summary(listOf(s("Drew", "HOAX", "b?", "FLAC", 40), s("Soju", "HOAX, Someone", "b?", "FLAC", 35),
            s("TV", "Hoax", "", "MP3", 8), s("Slow", "Joji", "Nectar", "FLAC", 30)))
        assertEquals(113_000_000L, sum.total); assertEquals(4, sum.songs)
        assertEquals(listOf("FLAC" to 105_000_000L, "MP3" to 8_000_000L), sum.byKind)
        assertEquals("one artist however the name is written, with or without guests", listOf("HOAX" to 83_000_000L, "Joji" to 30_000_000L), sum.artists)
        assertEquals("b? · HOAX", sum.albums[0].first); assertEquals(75_000_000L, sum.albums[0].second)
        assertEquals("Drew – HOAX", sum.biggest[0].first)
    }

    @Test fun damageThatShowsWithoutPlaying() {
        val flac = "fLaC00000000".toByteArray(Charsets.ISO_8859_1)
        assertNull(FileCheckRules.damage(flac, 30_000_000, 200.0))
        assertEquals("the file is empty", FileCheckRules.damage(ByteArray(0), 0, 200.0))
        assertTrue(FileCheckRules.damage("<!DOCTYPE ht".toByteArray(), 4000, 200.0)!!.startsWith("it isn't a music file"))
        assertTrue("three minutes in 100 KB", FileCheckRules.damage(flac, 100_000, 180.0)!!.contains("cut short"))
        assertNull("a short sound, or a length not known: nothing is said", FileCheckRules.damage(flac, 20_000, 0.0))
    }

    @Test fun whatItSays() {
        assertEquals("3,280 songs take 96.4 GB. No damaged files found", FileCheckRules.said(3280, 96_400_000_000, 0, 0))
        assertEquals("10 songs take 300 MB. 1 damaged file found (2 couldn't be opened to look)", FileCheckRules.said(10, 300_000_000, 1, 2))
    }
}
