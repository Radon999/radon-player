package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A song clip: where it starts and ends, and what the PC's ffmpeg is told. */
class ClipRulesTest {
    @Test fun aClipStaysInsideTheSongAndBetweenTheShortestAndLongest() {
        assertEquals(30.0 to 45.0, ClipRules.bounds(30.0, 45.0, 240.0))
        assertEquals("no longer than a minute", 30.0 to 90.0, ClipRules.bounds(30.0, 200.0, 240.0))
        assertEquals("no shorter than three seconds", 30.0 to 33.0, ClipRules.bounds(30.0, 31.0, 240.0))
        assertEquals("not past the song's end", 237.0 to 240.0, ClipRules.bounds(239.0, 300.0, 240.0))
        assertEquals("a song whose length isn't known", 10.0 to 25.0, ClipRules.bounds(10.0, 25.0, 0.0))
    }

    @Test fun aLyricsClipRunsFromTheFirstLineTickedToWhereTheLastStops() {
        val times = listOf(12.0, 15.5, 19.0, 24.0, 30.0)
        assertEquals("the last one ticked stops where the next line begins", 15.5 to 24.0, ClipRules.fromLyrics(times, listOf(2, 1), 200.0))
        assertEquals("the song's last line: six seconds", 30.0 to 36.0, ClipRules.fromLyrics(times, listOf(4), 200.0))
        assertEquals("or to the song's end", 30.0 to 33.0, ClipRules.fromLyrics(times, listOf(4), 33.0))
        assertEquals("one short line still makes a clip", 12.0 to 15.5, ClipRules.fromLyrics(times, listOf(0), 200.0))
        assertNull(ClipRules.fromLyrics(times, emptyList(), 200.0))
        assertEquals("far apart: a minute and a half at most", 0.0 to 90.0, ClipRules.fromLyrics(listOf(0.0, 100.0, 200.0), listOf(0, 1), 300.0))
    }

    @Test fun theBarAtTheVideosSizeAndTheVideosHeight() {
        assertEquals(ClipRules.Bar(72, 752, 572, 8, 0xFF8800), ClipRules.Bar(110, 1130, 860, 14, 0xFF8800).scaled(1080, 720))
        assertEquals(900, ClipRules.height(1080, 1350)); assertEquals(1280, ClipRules.height(1080, 1920))
        assertEquals(286, ClipRules.filled(5.0, 10.0, 572)); assertEquals(572, ClipRules.filled(11.0, 10.0, 572))
    }

    @Test fun whatThePcsFfmpegIsTold() {
        val a = ClipRules.ffmpegArgs("ffmpeg", "card.png", "song.flac", "out.mp4", 42.5, 15.0, ClipRules.Bar(72, 752, 572, 8, 0xFF8800), 720, 900, "libx264")
        val line = a.joinToString(" ")
        assertTrue("the song from where the clip starts, for as long as it is", "-ss 42.500 -t 15.000 -i song.flac" in line)
        assertTrue("the bar's colour as a strip", "color=c=0xFF8800:s=572x8:r=12" in line)
        assertTrue("slid in from the left as it plays", "overlay=x='72-572+572*min(t/15.000,1)':y=752" in line)
        assertTrue("under the card's own left edge", "crop=72:8:0:752[left]" in line)
        assertEquals("out.mp4", a.last())
        val plain = ClipRules.ffmpegArgs("ffmpeg", "card.png", "song.flac", "out.mp4", 0.0, 8.0, null, 720, 1280, "mpeg4").joinToString(" ")
        assertTrue("a lyrics clip has no bar", "lavfi" !in plain && "[0:v]scale=720:1280,format=yuv420p[v]" in plain)
        assertEquals("1:05", ClipRules.clock(65.4)); assertEquals("ACDC - Thunder clip", ClipRules.fileName("AC/DC — Thunder"))
    }
}
