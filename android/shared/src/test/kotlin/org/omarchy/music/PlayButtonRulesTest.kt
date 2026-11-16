package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The small rules behind the play buttons, casting and a song's radio. */
class PlayButtonRulesTest {
    @Test fun nextSwitchesTheSetOnlyPausedWithTheDjOn() {
        assertTrue(PlayButtonRules.nextSwitchesSet(djOn = true, remote = false, paused = true, djTalking = false, hasSong = true))
        assertFalse("playing: Next skips a song", PlayButtonRules.nextSwitchesSet(true, false, paused = false, djTalking = false, hasSong = true))
        assertFalse("the DJ off", PlayButtonRules.nextSwitchesSet(false, false, true, false, true))
        assertFalse("the desktop's remote", PlayButtonRules.nextSwitchesSet(true, remote = true, paused = true, djTalking = false, hasSong = true))
        assertFalse("the DJ talking", PlayButtonRules.nextSwitchesSet(true, false, true, djTalking = true, hasSong = true))
        assertFalse("nothing there", PlayButtonRules.nextSwitchesSet(true, false, true, false, hasSong = false))
    }

    @Test fun aCastDeviceIsToldTheKindOfFile() {
        assertEquals("audio/mpeg", CastRules.mime("/music/Song.MP3"))
        assertEquals("audio/mp4", CastRules.mime("http://192.168.1.2:8080/a.m4a?token=x.flac"))
        assertEquals("audio/ogg", CastRules.mime("a.opus"))
        assertEquals("audio/wav", CastRules.mime("a.wav"))
        assertEquals("audio/flac", CastRules.mime("a.flac"))
        assertEquals("anything else is sent as FLAC", "audio/flac", CastRules.mime("http://host/stream"))
    }

    @Test fun aSongsRadioAsksItsOwnServiceFirst() {
        assertEquals(listOf("soundcloud", "tidal", "ytm"), RadioRules.order("soundcloud", "tidal"))
        assertEquals(listOf("ytm", "tidal"), RadioRules.order("ytm", "ytm"))
        assertEquals(listOf("local", "apple", "ytm", "tidal"), RadioRules.order("local", "apple"))
    }

    @Test fun servicesWithoutARadioAreSkipped() {
        assertTrue(RadioRules.skips("apple", signedIn = true))
        assertTrue(RadioRules.skips("spotify", signedIn = true))
        assertTrue(RadioRules.skips("tidal", signedIn = false))
        assertFalse(RadioRules.skips("tidal", signedIn = true))
        assertFalse(RadioRules.skips("ytm", signedIn = false))
    }
}
