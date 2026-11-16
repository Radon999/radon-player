package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The small parts split out of the app's centre last: what a cast device is told about a song's file, a song's
 *  speed in bit-perfect, and what Speed… says. */
class PlayLeftoversTest {
    @Test fun aCastDeviceIsToldTheSongsOwnKindOfFile() {
        Harness.ctx
        val mp3 = Track("leftovers-test-1", title = "One", artist = "Leftovers Test", file = "/music/one.mp3")
        assertEquals("the file's kind, whatever the address says", "audio/mpeg", Casting.mime(mp3, "http://192.168.1.2:8080/stream.flac"))
        val stream = Track("leftovers-test-2", title = "Two", artist = "Leftovers Test")
        assertEquals("audio/mp4", Casting.mime(stream, "http://192.168.1.2:8080/two.m4a?t=1"))
    }

    @Test fun bitPerfectPlaysEverySongAtItsOwnSpeed() {
        Harness.ctx
        val t = Track("leftovers-test-3", title = "Three", artist = "Leftovers Test")
        val had = Music.bitPerfect
        try {
            Harness.ui { Music.bitPerfect = true; assertEquals(1f to 1f, SongMarks.playback(t)) }
        } finally {
            Harness.ui { Music.bitPerfect = had }
        }
    }

    @Test fun speedSaysWhatItDid() {
        val one = listOf(Track("leftovers-test-4", title = "Four", artist = "Leftovers Test"))
        assertTrue(SongMarks.speedText(one, 1.0).endsWith("back to normal speed"))
        assertTrue(SongMarks.speedText(one, 1.5).contains("Four") && SongMarks.speedText(one, 1.5).endsWith("at 1.5×"))
        assertTrue(SongMarks.speedText(one + one, 2.0).startsWith("2 songs at 2×"))
    }
}
