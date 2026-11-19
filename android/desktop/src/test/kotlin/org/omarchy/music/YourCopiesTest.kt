package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Your copy of a song: a song named by Last.fm, an import or a service is your own file of it when you have one.
 *  The saved songs are the test's own, put in for the test and taken out after it. */
class YourCopiesTest {
    private fun saved(id: String, title: String, artist: String, album: String, format: String) = LibEntry(id = "copies-$id",
        source = "ytm", title = title, artist = artist, album = album, file = "/music/test/copies-$id.$format", format = format, added = 1.0)

    private fun <T> withSaved(vararg entries: LibEntry, block: () -> T): T {
        Harness.ctx
        Harness.ui { for (e in entries) Music.lib.items[e.id] = e; Music.musicVersion++ }
        try { return block() } finally { Harness.ui { for (e in entries) Music.lib.items.remove(e.id); Music.musicVersion++ } }
    }

    @Test fun ofTwoCopiesTheOneOnTheSameAlbumComesFirstThenTheFlac() = withSaved(
        saved("mp3", "Copy Song", "Copy Band", "First Record", "mp3"), saved("flac", "Copy Song", "Copy Band", "Second Record", "flac")) {
        val mine = Music.songIndex()
        assertEquals("the one on the album named", "copies-mp3", Music.fmSong("Copy Song", "Copy Band", "", mine, album = "First Record").id)
        assertEquals("no album named: the FLAC", "copies-flac", Music.fmSong("Copy Song", "Copy Band", "", mine).id)
        assertEquals("an album you don't have it on: the FLAC", "copies-flac", Music.fmSong("Copy Song", "Copy Band", "", mine, album = "Some Other Record").id)
    }

    @Test fun aSongNamedByEveryoneOnItIsYourFileTaggedWithOneOfThem() = withSaved(
        saved("ott", "Credit Song", "Second Singer", "Credit Record", "flac"), saved("duo", "Duo Song", "First Half & Other Half", "Duo Record", "flac")) {
        assertEquals("your file names only the second artist", "copies-ott", Music.mineOf(Track("repeat:x", title = "Credit Song", artist = "First Band & Second Singer")).id)
        assertEquals("and with dots in the name", "copies-ott", Music.mineOf(Track("repeat:y", title = "Credit Song", artist = "First Band, Second.Singer")).id)
        assertEquals("your file names both, the history only the second", "copies-duo", Music.mineOf(Track("repeat:z", title = "Duo Song", artist = "Other Half")).id)
        assertEquals("another artist's song of the same name stays as it is", "repeat:w", Music.mineOf(Track("repeat:w", title = "Credit Song", artist = "Somebody Else & Another")).id)
    }

    @Test fun aSongYouDontHaveIsLeftToBeFoundOnAService() {
        Harness.ctx
        val t = Music.fmSong("Nobody Has This One", "No One At All", "", Music.songIndex())
        assertEquals("lastfm", t.source)
        assertTrue(t.id, t.id.startsWith("lfm-"))
        assertEquals("the same song gets the same name each time", t.id, Music.fmSong("Nobody Has This One", "No One At All", "", Music.songIndex()).id)
    }

    @Test fun aServicesSongPlaysFromYourCopyButALiveVersionIsNotYourStudioCopy() = withSaved(
        saved("stage", "Stage Song", "Stage Band", "Stage Record", "flac")) {
        assertEquals("copies-stage", Music.localOf(Track("svc-studio", "ytm", "Stage Song", "Stage Band")).id)
        assertEquals("svc-live", Music.localOf(Track("svc-live", "ytm", "Stage Song (Live)", "Stage Band")).id)
        assertEquals("another artist's song of the same name", "svc-other", Music.localOf(Track("svc-other", "ytm", "Stage Song", "Someone Else")).id)
    }
}
