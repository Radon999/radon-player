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

    @Test fun aSongSaysWhetherItIsOnThisDevice() = withSaved(saved("here", "Here Song", "Here Band", "Here Record", "flac")) {
        assertTrue("a history's song you have: on this device", org.omarchy.music.ui.onThisDevice(Music.localOf(Track("repeat:h", title = "Here Song", artist = "Here Band"))))
        assertTrue("a history's song you haven't got: a stream", !org.omarchy.music.ui.onThisDevice(Music.localOf(Track("repeat:s", title = "Nowhere Song", artist = "Here Band"))))
    }

    @Test fun aSongNamedByEveryoneOnItIsYourFileTaggedWithOneOfThem() = withSaved(
        saved("ott", "Credit Song", "Second Singer", "Credit Record", "flac"), saved("duo", "Duo Song", "First Half & Other Half", "Duo Record", "flac")) {
        assertEquals("your file names only the second artist", "copies-ott", Music.mineOf(Track("repeat:x", title = "Credit Song", artist = "First Band & Second Singer")).id)
        assertEquals("and with dots in the name", "copies-ott", Music.mineOf(Track("repeat:y", title = "Credit Song", artist = "First Band, Second.Singer")).id)
        assertEquals("your file names both, the history only the second", "copies-duo", Music.mineOf(Track("repeat:z", title = "Duo Song", artist = "Other Half")).id)
        assertEquals("another artist's song of the same name stays as it is", "repeat:w", Music.mineOf(Track("repeat:w", title = "Credit Song", artist = "Somebody Else & Another")).id)
    }

    @Test fun aSongNamedUnderSomeoneElseIsYourOnlyFileOfThatNameAndLength() = withSaved(
        saved("unrel", "Vault Song", "Real Singer", "Sessions", "flac").copy(duration = 120.12),
        saved("twin1", "Twin Song", "Twin One", "A", "flac").copy(duration = 200.0), saved("twin2", "Twin Song", "Twin Two", "B", "flac").copy(duration = 200.4)) {
        assertEquals("the same name and length", "copies-unrel", Music.mineOf(Track("repeat:u", title = "Vault Song", artist = "Some Uploader", duration = 120.0)).id)
        assertEquals("another length: another song of that name", "repeat:v", Music.mineOf(Track("repeat:v", title = "Vault Song", artist = "Some Uploader", duration = 187.0)).id)
        assertEquals("no length known: left alone", "repeat:n", Music.mineOf(Track("repeat:n", title = "Vault Song", artist = "Some Uploader")).id)
        assertEquals("two of yours fit: neither", "repeat:t", Music.mineOf(Track("repeat:t", title = "Twin Song", artist = "Some Uploader", duration = 200.2)).id)
        assertEquals("under its own name already: nothing to move", null, YourCopies.namedOtherwise("Vault Song", "Real Singer"))
        assertEquals("not yours: nothing to move", null, YourCopies.namedOtherwise("Vault Song", "Some Uploader"))   // (no length known for it)
        val play = SpotifyHistory.Play(1, "Some Uploader", "Vault Song", "", 120_000)
        val (songs, artists) = Stats.combine(emptyList(), emptyList(), emptyList(), apple = listOf(play, play),
            asYours = { t, a -> if (a == "Some Uploader") "Vault Song" to "Real Singer" else null })
        assertEquals("its plays are your file's artist's", listOf("Real Singer" to 2), songs.map { it.artist to it.plays })
        assertEquals(listOf("Real Singer"), artists.map { it.name })
        assertEquals("what plays is never picked by length alone", "svc-u", Music.localOf(Track("svc-u", "ytm", "Vault Song", "Some Uploader", duration = 120.0)).id)
    }

    @Test fun aSongYouSayIsYoursIsYourFileUntilYouUndoIt() = withSaved(
        saved("said", "Real Name", "Real Singer", "Sessions", "wav"), saved("bare", "No One's Song", "Unknown artist", "x", "wav")) {
        val other = Track("repeat:s", title = "Another Name Entirely", artist = "Some Uploader")
        val mine = Music.mySongs().first { it.id == "copies-said" }
        try {
            assertEquals(true, Harness.ui { YourCopies.link(other, mine) })
            assertEquals("shown as your file", "copies-said", Music.mineOf(other).id)
            assertEquals("its plays are your file's artist's", "Real Name" to "Real Singer", YourCopies.renamer()("Another Name Entirely", "Some Uploader"))
            assertEquals(1, YourCopies.linkedTo(mine).size)
            assertEquals("a service's song is never swapped by it", "svc-s", Music.localOf(Track("svc-s", "ytm", "Another Name Entirely", "Some Uploader")).id)
            Harness.ui { YourCopies.unlink(mine) }
            assertEquals("undone", "repeat:s", Music.mineOf(other).id)
            assertEquals("a file with no artist can't take plays", false, Harness.ui { YourCopies.link(other, Music.mySongs().first { it.id == "copies-bare" }) })
        } finally { Harness.ui { YourCopies.unlink(mine) } }
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
