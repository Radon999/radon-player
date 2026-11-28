package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class SongExtraRulesTest {
    private val R = SongExtraRules

    @Test fun aTracksNumberIsReadHoweverItIsWritten() {
        assertEquals(3, R.number("3")); assertEquals(3, R.number("03/12")); assertEquals(3, R.number(" 3 "))
        assertEquals(0, R.number("")); assertEquals(0, R.number(null)); assertEquals(0, R.number("A1"))
    }

    @Test fun androidsOneNumberIsTrackAndDisc() {
        assertEquals(7 to 0, R.fromAndroid(7))
        assertEquals(7 to 2, R.fromAndroid(2007))
        assertEquals(0 to 0, R.fromAndroid(0))
    }

    @Test fun aCompilationIsOneAlbumUnderItsAlbumArtist() {
        assertEquals("Various Artists", R.albumOwner("Various Artists", "Dua Lipa"))
        assertEquals("Various Artists", R.albumOwner(" Various Artists ", "The Weeknd, Daft Punk"))
        assertEquals("The Weeknd", R.albumOwner("", "The Weeknd, Daft Punk"))
        assertEquals("The Weeknd", R.albumOwner(null, "The Weeknd"))
    }
}
