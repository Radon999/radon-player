package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the app tells a Linux desktop about what's playing (its media keys and "now playing"). */
class MprisRulesTest {
    private val song = MprisRules.Now(id = "tidal:123", title = "One", artist = "A, B", album = "First", hasSong = true, playing = true, position = 10.0, duration = 200.0)

    @Test fun theStatusIsPlayingPausedOrStopped() {
        assertEquals("Playing", MprisRules.status(song))
        assertEquals("Paused", MprisRules.status(song.copy(playing = false)))
        assertEquals("Stopped", MprisRules.status(MprisRules.Now()))
        assertEquals("nothing there, whatever else", "Stopped", MprisRules.status(MprisRules.Now(playing = true)))
    }

    @Test fun timesGoInMillionthsOfASecond() {
        assertEquals(1_500_000L, MprisRules.micros(1.5))
        assertEquals(0L, MprisRules.micros(-3.0))
        assertEquals(2.5, MprisRules.seconds(2_500_000L), 0.0)
    }

    @Test fun aSongsPathIsOnlyLettersDigitsAndUnderscores() {
        val p = MprisRules.trackPath("tidal:12/3 é")
        assertTrue(p, p.startsWith("/org/radonplayer/track/") && Regex("[A-Za-z0-9_/]+").matches(p))
        assertEquals("/org/radonplayer/track/tidal_3a123", MprisRules.trackPath("tidal:123"))
        assertEquals("/org/mpris/MediaPlayer2/TrackList/NoTrack", MprisRules.trackPath(""))
        assertFalse("two songs, two paths", MprisRules.trackPath("a:b") == MprisRules.trackPath("a/b"))
    }

    @Test fun eachArtistIsNamed() {
        assertEquals(listOf("A", "B"), MprisRules.artists("A, B"))
        assertEquals(listOf("Solo"), MprisRules.artists("Solo"))
        assertTrue(MprisRules.artists("").isEmpty())
    }

    @Test fun aCoverIsAnAddressTheDesktopCanOpen() {
        assertEquals("https://x/y.jpg", MprisRules.artUrl("https://x/y.jpg"))
        assertEquals("file:///home/me/My%20Music/cover.jpg", MprisRules.artUrl("/home/me/My Music/cover.jpg"))
        assertEquals("", MprisRules.artUrl("content://something"))
        assertEquals("", MprisRules.artUrl(""))
    }

    @Test fun theDesktopIsToldWhatChanged() {
        assertEquals(setOf("PlaybackStatus"), MprisRules.changed(song, song.copy(playing = false)))
        assertEquals(setOf("Metadata"), MprisRules.changed(song, song.copy(id = "tidal:124", title = "Two")))
        assertEquals("its length came in", setOf("Metadata"), MprisRules.changed(song.copy(duration = 0.0), song))
        assertEquals("a song after none: what the buttons may do too", setOf("PlaybackStatus", "Metadata", "CanPlay", "CanPause", "CanSeek"),
            MprisRules.changed(MprisRules.Now(), song))
        assertTrue("time passing is not news", MprisRules.changed(song, song.copy(position = 11.0)).isEmpty())
    }

    @Test fun aSeekIsToldAndTimePassingIsNot() {
        assertFalse(MprisRules.jumped(song, song.copy(position = 10.5), elapsed = 0.5))
        assertTrue(MprisRules.jumped(song, song.copy(position = 90.0), elapsed = 0.5))
        assertTrue("paused: it shouldn't have moved", MprisRules.jumped(song.copy(playing = false), song.copy(playing = false, position = 14.0), elapsed = 4.0))
        assertFalse("another song is not a seek", MprisRules.jumped(song, song.copy(id = "x", position = 0.0), elapsed = 0.5))
    }

    @Test fun aRelativeSeekStaysInTheSong() {
        assertEquals(15.0, MprisRules.seekTo(10.0, 5_000_000L, 200.0), 0.0)
        assertEquals(0.0, MprisRules.seekTo(10.0, -30_000_000L, 200.0), 0.0)
        assertEquals(200.0, MprisRules.seekTo(190.0, 30_000_000L, 200.0), 0.0)
        assertEquals("length not known", 220.0, MprisRules.seekTo(190.0, 30_000_000L, 0.0), 0.0)
    }

    @Test fun whatTheButtonsMayDoIsToldWhenASongComesOrGoes() {
        val none = MprisRules.Now("", "", "", "", "", hasSong = false, playing = false, position = 0.0, duration = 0.0)
        val song = MprisRules.Now("tidal:1", "One", "Band", "Album", "", hasSong = true, playing = false, position = 0.0, duration = 200.0)
        val c = MprisRules.changed(none, song)
        assertTrue(c.toString(), "CanPlay" in c && "CanPause" in c && "CanSeek" in c)
        assertTrue("CanPlay" in MprisRules.changed(song, none))
        assertTrue("not with every change of song", "CanPlay" !in MprisRules.changed(song, song.copy(id = "tidal:2", title = "Two")))
    }
}
