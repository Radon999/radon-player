package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spotify's extended history says how each play ended; the app keeps that for its skip counts. */
class SpotifySkipsTest {
    @Test fun theEndingIsReadFromTheExtendedHistory() {
        assertEquals("marked skipped, but ended another way: not a skip", 0, SpotifyHistory.ending(true, "endplay"))
        assertEquals("going back is not a skip", 0, SpotifyHistory.ending(true, "backbtn"))
        assertEquals("only 'skipped' to go by", 2, SpotifyHistory.ending(true, null))
        assertEquals("the forward button", 2, SpotifyHistory.ending(null, "fwdbtn"))
        assertEquals("played to its end", 1, SpotifyHistory.ending(false, "trackdone"))
        assertEquals("the past year's history says nothing", 0, SpotifyHistory.ending(null, null))
        assertEquals("stopped, not skipped", 0, SpotifyHistory.ending(false, "logout"))
    }

    @Test fun aSkipBeforeThirtySecondsIsKeptAsASkipNotAsAPlay() {
        val json = """[
            {"ts":"2024-03-01T10:03:20Z","ms_played":200000,"master_metadata_track_name":"Long Song","master_metadata_album_artist_name":"A Band","reason_end":"trackdone","skipped":false},
            {"ts":"2024-03-01T10:04:00Z","ms_played":8000,"master_metadata_track_name":"Skipped Song","master_metadata_album_artist_name":"A Band","reason_end":"fwdbtn","skipped":true},
            {"ts":"2024-03-01T10:05:00Z","ms_played":45000,"master_metadata_track_name":"Half Song","master_metadata_album_artist_name":"A Band","reason_end":"fwdbtn","skipped":true},
            {"ts":"2024-03-01T10:06:00Z","ms_played":9000,"master_metadata_track_name":"Went Back","master_metadata_album_artist_name":"A Band","reason_end":"backbtn","skipped":true},
            {"ts":"2024-03-01T10:05:01Z","ms_played":300,"master_metadata_track_name":"Mis Tap","master_metadata_album_artist_name":"A Band","reason_end":"fwdbtn","skipped":true}
        ]"""
        val plays = ArrayList<SpotifyHistory.Play>(); val skips = ArrayList<SpotifyHistory.Play>()
        SpotifyHistory.parse(json.byteInputStream(), plays, skips)
        assertEquals(listOf("Long Song" to 1, "Half Song" to 2), plays.map { it.title to it.how })
        assertEquals("under thirty seconds: a skip, not a play; a tap of a third of a second is neither", listOf("Skipped Song"), skips.map { it.title })
    }
}
