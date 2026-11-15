package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A favorite and a song's own volume. The songs are the test's own, and what it sets is taken off again. */
class SongMarksTest {
    private fun song(id: String, title: String, source: String = "ytm") = Track("mark-$id", source, title, "Marks Test Band")

    @Test fun whatStarringSays() {
        val one = listOf(song("a", "Alpha")); val three = listOf(song("a", "Alpha"), song("b", "Beta"), song("c", "Gamma"))
        assertEquals("“Alpha” in your favorites", SongMarks.favoriteText(one, true))
        assertEquals("“Alpha” out of your favorites", SongMarks.favoriteText(one, false))
        assertEquals("3 songs in your favorites", SongMarks.favoriteText(three, true))
    }

    @Test fun whatVolumeSays() {
        val one = listOf(song("a", "Alpha")); val two = listOf(song("a", "Alpha"), song("b", "Beta"))
        assertEquals("“Alpha” at +3.0 dB on Headphones (jack)", SongMarks.gainText(one, 3.0, "Headphones (jack)"))
        assertEquals("on every output: no place named", "2 songs at -4.5 dB", SongMarks.gainText(two, -4.5, ""))
        assertEquals("“Alpha” back to normal volume on Headphones (jack)", SongMarks.gainText(one, 0.0, "Headphones (jack)"))
    }

    @Test fun aStarGoesOnAndOffAndSoundCloudSongsStayOut() {
        Harness.ctx
        val a = song("a", "Alpha"); val b = song("b", "Beta"); val sc = song("sc", "From SoundCloud", SoundCloud.KEY)
        try {
            Harness.ui {
                SongMarks.favorite(listOf(a))
                assertTrue("a tap stars it", Music.favorites.has(a))
                SongMarks.favorite(listOf(a))
                assertFalse("another takes the star off", Music.favorites.has(a))
                SongMarks.favorite(listOf(a, b, sc), on = true)
                assertTrue(Music.favorites.has(a)); assertTrue(Music.favorites.has(b))
                assertFalse("SoundCloud's song isn't among your favorites", Music.favorites.has(sc))
                SongMarks.favorite(listOf(a, b))   // the other way from the first one's: off for both
                assertFalse(Music.favorites.has(a)); assertFalse(Music.favorites.has(b))
            }
        } finally { Harness.ui { Music.favorites.set(listOf(a, b, sc), false); Music.musicVersion++ } }
    }

    @Test fun aSongsOwnVolumeIsForOneOutputOrForAll() {
        Harness.ctx
        val a = song("a", "Alpha")
        try {
            Harness.ui {
                val here = Music.outputNow.key
                assertEquals(0.0, SongMarks.gainNow(a), 0.0)
                SongMarks.gain(listOf(a), 2.0, null)   // every output
                assertEquals(2.0, SongMarks.gainNow(a), 0.001)
                SongMarks.gain(listOf(a), -3.0, here)   // this output: its own comes first
                assertEquals(-3.0, SongMarks.gainNow(a), 0.001)
                assertEquals("-3 dB is about 0.708 of the sound", 0.708, SongMarks.gainOf(a), 0.001)
                SongMarks.gain(listOf(a), 0.0, here)
                assertEquals("this output's taken off: the every-output one again", 2.0, SongMarks.gainNow(a), 0.001)
            }
        } finally { Harness.ui { Music.gains.set(listOf(a), 0.0, Music.outputNow.key); Music.gains.set(listOf(a), 0.0, null); Music.musicVersion++ } }
    }
}
