package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** Sending a playlist to the other device says what went across and names what could not. */
class PlaylistSendRulesTest {
    @Test fun whatItSays() {
        assertEquals("“Gym” sent to the PC: 40 songs added",
            PlaylistSendRules.said(true, "Gym", "the PC", "the PC", 40, 0, emptyList()))
        assertEquals("“Gym” came from the phone: 1 song added, 3 in it already",
            PlaylistSendRules.said(false, "Gym", "the phone", "this PC", 1, 3, emptyList()))
        assertEquals("“Gym” sent to the PC: all 12 songs were in it already",
            PlaylistSendRules.said(true, "Gym", "the PC", "the PC", 0, 12, emptyList()))
    }

    @Test fun theSongsLeftOutAreNamed() {
        assertEquals("“Gym” sent to the PC: 38 songs added. 2 songs left out, not on the PC: Drew – HOAX; demo 3",
            PlaylistSendRules.said(true, "Gym", "the PC", "the PC", 38, 0, listOf("Drew – HOAX", "demo 3")))
        assertEquals("“Gym” came from the phone: no songs added. 5 songs left out, not on this PC: a; b; c; and 2 more",
            PlaylistSendRules.said(false, "Gym", "the phone", "this PC", 0, 0, listOf("a", "b", "c", "d", "e")))
        assertEquals("Drew – HOAX", PlaylistSendRules.songName("Drew", "HOAX"))
        assertEquals("demo 3", PlaylistSendRules.songName("demo 3", " "))
        assertEquals("a\nb", PlaylistSendRules.missingList(listOf("a", "b")))
    }
}
