package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Backed-up moods matched to the songs on this device by title and artist, length and album. */
class MoodBackupTest {
    private val saved = listOf(
        MoodBackupSong("Song A", "Artist One", "Album X", 200.0, listOf("Chill", "Dreamy"), 0.7, full = true),
        MoodBackupSong("Song A", "Artist One", "Live At Y", 260.0, listOf("Energetic"), 0.6),
        MoodBackupSong("Song B (feat. Z)", "Artist Two, Z", "", 0.0, listOf("Sad"), 0.5),
        MoodBackupSong("Intro", "Artist One", "Album X", 60.0, listOf("Calm"), 0.5),
        MoodBackupSong("Intro", "Artist One", "Album Q", 61.0, listOf("Dark"), 0.5),
        MoodBackupSong("Old Song", "Band", "", 180.0, listOf("Happy"), 0.5),
        MoodBackupSong("Nothing", "Band", "", 100.0, emptyList()),
    )

    private val got = MoodBackup.match(saved, listOf(
        MoodSongHere("case", "song a", "ARTIST ONE", "Album X (Deluxe Edition)", 201.5),
        MoodSongHere("live", "Song A", "Artist One", "", 259.0),
        MoodSongHere("feat", "Song B", "Artist Two & Z", "", 190.0),
        MoodSongHere("album", "Intro", "Artist One", "Album Q", 60.5),
        MoodSongHere("longer", "Song A", "Artist One", "", 300.0),
        MoodSongHere("remaster", "Old Song (2011 Remaster)", "Band", "", 181.0),
        MoodSongHere("other", "Song C", "Artist One", "", 200.0),
        MoodSongHere("empty", "Nothing", "Band", "", 100.0),
    ))

    @Test fun sameSongTidiedNames() {
        assertEquals("Chill", got["case"]!!.moods[0])   // case, edition of the album, a second and a half apart
        assertEquals(true, got["case"]!!.full)
        assertEquals("Sad", got["feat"]!!.moods[0])     // "feat." and the other artists don't matter; no length there
    }

    @Test fun lengthPicksTheRecording() {
        assertEquals("Energetic", got["live"]!!.moods[0])
        assertFalse("longer" in got)                     // a minute and a half longer than both: another recording
    }

    @Test fun albumBreaksTies() = assertEquals("Dark", got["album"]!!.moods[0])

    @Test fun bareTitleOnlyWithTheSameLength() {
        assertEquals("Happy", got["remaster"]!!.moods[0])
        assertFalse("other" in got)
        assertFalse("empty" in got)                      // nothing to give
        assertFalse("x" in MoodBackup.match(saved, listOf(MoodSongHere("x", "Old Song (Live)", "Band", "", 250.0))))
    }
}
