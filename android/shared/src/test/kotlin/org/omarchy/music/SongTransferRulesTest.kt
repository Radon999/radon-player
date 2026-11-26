package org.omarchy.music

import org.omarchy.music.SongTransferRules.Way as W
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Other device's songs: always the file copied from the other device, never a download from streaming. */
class SongTransferRulesTest {
    private val R = SongTransferRules

    @Test fun aSongIsAlwaysCopiedNeverDownloaded() {
        assertEquals("the other device has the file: it's copied", W.COPY, R.way(hasFile = true))
        assertEquals("no file over there: nothing is downloaded instead", W.NO_FILE, R.way(hasFile = false))
        assertEquals("only two ways, neither of them streaming", listOf(W.COPY, W.NO_FILE), W.values().toList())
    }

    @Test fun whatEachRowSays() {
        assertEquals("⇄ from your PC", R.label(W.COPY, "your PC"))
        assertEquals("⇄ your phone didn't say where its file is: Sync again", R.label(W.NO_FILE, "your phone"))
        assertEquals("On your phone", R.badge("your phone"))
    }

    @Test fun songsWithoutAFileAreSaidSo() {
        assertEquals("1 song couldn't be copied: your PC didn't say where its file is. Sync again, then get it", R.noFile(1, "your PC"))
        assertEquals("3 songs couldn't be copied: your phone didn't say where their files are. Sync again, then get them", R.noFile(3, "your phone"))
    }

    @Test fun onlyFilesOfYourOwnMusicAreHandedOut() {
        val listed = setOf("/home/me/Music/Artists/HOAX/Drew/01 Drew.flac", "C:\\Users\\me\\Music\\Song.mp3")
        assertTrue(R.servable("/home/me/Music/Artists/HOAX/Drew/01 Drew.flac", listed))
        assertTrue(R.servable("C:\\Users\\me\\Music\\Song.mp3", listed))
        assertFalse("a file that isn't one of your songs", R.servable("/home/me/.local/share/radon-player/remote-tokens.json", listed))
        assertFalse("climbing out with ..", R.servable("/home/me/Music/Artists/HOAX/Drew/../../../../.ssh/id_rsa", listed + "/home/me/Music/Artists/HOAX/Drew/../../../../.ssh/id_rsa"))
        assertFalse("…the Windows way", R.servable("C:\\Users\\me\\Music\\..\\secret.mp3", setOf("C:\\Users\\me\\Music\\..\\secret.mp3")))
        assertFalse("a NUL", R.servable("/home/me/Music/a.flac\u0000.txt", setOf("/home/me/Music/a.flac\u0000.txt")))
        assertFalse("nothing", R.servable("", setOf("")))
        assertTrue("a name with dots in it is fine", R.plainRef("/Music/A..B/x...flac"))
    }

    @Test fun aCopiedFileIsSavedAsDownloadsAre() {
        assertEquals("Music/Artists/HOAX/Drew/" to "01 Drew.flac", R.savePlace("Music/Artists/", "HOAX, Someone", "Drew", "Drew", "01 - Drew.flac"))
        assertEquals("an EP in EPs, an MP3 stays an MP3", "Music/Artists/X/EPs/Spring - EP/03 Song.mp3".let { it.substringBeforeLast('/') + "/" to it.substringAfterLast('/') },
            R.savePlace("Music/Artists/", "X", "Spring - EP", "Song", "3. whatever.MP3"))
        assertEquals("no album: a single, no number", "Music/Artists/X/Singles/Song - Single/" to "Song.m4a", R.savePlace("Music/Artists/", "X", "", "Song", "Song.m4a"))
        assertEquals("names made safe", "/data/Music/A_B/C_D/Wh_t.flac".let { it.substringBeforeLast('/') + "/" to it.substringAfterLast('/') },
            R.savePlace("/data/Music", "A/B", "C:D", "Wh?t", "x.flac"))
        assertEquals("no artist", "Music/Artists/Unknown artist/Singles/Song - Single/" to "Song.flac", R.savePlace("Music/Artists/", "", "", "Song", "Song.flac"))
        assertNull("not a song file", R.savePlace("Music/Artists/", "X", "Y", "Z", "notes.txt"))
        assertNull("no type at all", R.savePlace("Music/Artists/", "X", "Y", "Z", "flac"))
    }

    @Test fun trackNumbersFromFileNames() {
        assertEquals(7L, R.numberOf("07 Song.flac"))
        assertEquals(7L, R.numberOf("7. Song.flac"))
        assertEquals(12L, R.numberOf("12 - Song.flac"))
        assertNull(R.numberOf("Song.flac"))
        assertNull("a song whose name is a number", R.numberOf("1999.flac"))
        assertNull(R.numberOf("00 Intro.flac"))
    }

    @Test fun aNameThatIsTakenGetsANumber() {
        assertEquals("Song.flac", R.nameTry("Song.flac", 1))
        assertEquals("Song (2).flac", R.nameTry("Song.flac", 2))
        assertEquals("01 A.B (3).mp3", R.nameTry("01 A.B.mp3", 3))
        assertEquals("Song (2)", R.nameTry("Song", 2))
    }

    @Test fun whereAFetchStartsAgain() {
        assertEquals(0L, R.rangeStart(null, 100))
        assertEquals(40L, R.rangeStart("bytes=40-", 100))
        assertNull("past the end", R.rangeStart("bytes=100-", 100))
        assertNull("a part in the middle isn't asked for", R.rangeStart("bytes=0-10", 100))
        assertNull(R.rangeStart("lines=1-", 100))
    }

    @Test fun checksumsAndLibrarySongs() {
        val sha = "a".repeat(64)
        assertTrue(R.validSha(sha))
        assertFalse(R.validSha("A".repeat(64)))
        assertFalse(R.validSha("../" + "a".repeat(61)))
        assertFalse(R.validSha(null))
        assertEquals("the very download the other made" , "123" to "tidal", R.libraryId("123", "tidal", sha))
        assertEquals("the song shown, found by name", "lfm-x" to "lastfm", R.libraryId("lfm-x", "lastfm", sha))
        assertEquals("no song given: named after the file", "copy-aaaaaaaaaaaaaaaa" to "lastfm", R.libraryId("", "", sha))
        assertEquals("never kept as a file of yours (it has no file there)", "copy-aaaaaaaaaaaaaaaa" to "lastfm", R.libraryId("f123", "file", sha))
    }

    @Test fun whatIsSaid() {
        assertEquals("Copying from your PC · 1 of 3 · 40%", R.progress("Copying from your PC", 0, 3, 40))
        assertEquals("Copying from your PC · 3 of 3", R.progress("Copying from your PC", 5, 3, null))
        assertEquals("Couldn't reach your PC. Open Radon Player on your PC and make sure both are on the same Wi-Fi or Tailscale", R.unreachable("your PC"))
        assertEquals("Copied 2 songs from your PC. 1 was here already. 1 couldn't be copied: it changed", R.summary(2, 1, 1, "it changed", "your PC"))
        assertEquals("Nothing to copy", R.summary(0, 0, 0, "", "your PC"))
        assertTrue(R.asked(3, "Pixel").startsWith("Asked Pixel for 3 songs"))
    }
}
