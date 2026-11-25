package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Duplicates' names (a leading track number, signs, a file with no artist) and your copy of a song whose file is gone. */
class GoneAndDupeNamesTest {
    private fun t(id: String, title: String, artist: String = "Some Band", secs: Double = 200.0) =
        Track(id, "file", title, artist, file = "/gone-test/$id.flac", duration = secs)

    @Test fun aTrackNumberWrittenAnyWayIsLeftOff() {
        for (s in listOf("11 Ruby", "11. Ruby", "11 - Ruby", "11_Ruby", "(11) Ruby", "1-11 Ruby", "01.11 Ruby", "11 – Ruby"))
            assertEquals(s, "Ruby", withoutTrackNumber(s))
        for (s in listOf("1-800-273-8255", "12-51", "10-10", "1999", "1000 Blunts", "1, 2", "#1", "2-Step"))
            assertEquals("$s stays as it is", s, withoutTrackNumber(s))
    }

    @Test fun elevenRubyWithNoArtistIsAPossibleDuplicateOfRuby() {
        val songs = listOf(t("a", "11 Ruby", "Unknown artist", 230.0), t("b", "Ruby", "twenty one pilots", 213.0), t("c", "Rubber", "twenty one pilots"))
        val groups = dupeClusters(songs)
        assertEquals(listOf(listOf("a", "b")), groups.map { g -> g.map { it.id } })
        assertFalse("a file with no artist: not marked Remove until you say so", sureCopies(groups[0]))
    }

    @Test fun aTrackNumberAloneIsNotSureEvenByOneArtist() {
        val g = dupeClusters(listOf(t("a", "24 Hours", "Juice WRLD"), t("b", "Hours", "Juice WRLD", 100.0)))
        assertEquals(1, g.size)
        assertFalse(sureCopies(g[0]))
        assertTrue(sureCopies(dupeClusters(listOf(t("a", "Ruby", "Keane"), t("b", "Ruby (Remastered)", "Keane")))[0]))
    }

    @Test fun aNameOfSignsAloneIsFoundTwiceAndOneSongTwiceInAListIsTwoPlaces() {
        val heart = t("h", "♡", "Coldplay", 189.0)
        assertEquals(listOf(listOf(0, 1)), dupeClusterIndices(listOf(heart, heart.copy(id = "h2", file = "/gone-test/old.flac"))))
        assertEquals("the very same song listed twice", listOf(listOf(0, 2)), dupeClusterIndices(listOf(heart, t("x", "Other"), heart)))
        assertTrue("other names of signs are other songs", dupeClusters(listOf(t("n1", "…"), t("n2", "!!"))).isEmpty())
    }

    @Test fun yourCopyOfAGoneFileIsFoundByItsNameWithoutItsTrackNumber() {
        val ruby = t("ruby", "Ruby", "twenty one pilots"); val heart = t("heart", "♡", "Coldplay")
        val find = MusicFolderRules.copyFinder(listOf(ruby, heart, t("x", "Other Song")))
        assertEquals("ruby", find(t("gone1", "11 Ruby", "Unknown artist"))?.id)
        assertEquals("ruby", find(t("gone2", "03 - Ruby", "Twenty One Pilots"))?.id)
        assertEquals("heart", find(t("gone3", "♡", "Coldplay"))?.id)
        assertNull("another artist's song of that name isn't yours", find(t("gone4", "Ruby", "Kaiser Chiefs")))
        val two = MusicFolderRules.copyFinder(listOf(ruby, t("kc", "Ruby", "Kaiser Chiefs")))
        assertNull("no artist, and two artists have a Ruby: can't tell", two(t("gone5", "11 Ruby", "")))
    }

    @Test fun onlyThisDevicesFilesCanBeGoneFromIt() {
        assertTrue(MusicFolderRules.thisDevices("content://media/external/audio/media/12", android = true, windows = false))
        assertFalse("the PC's file in a synced playlist", MusicFolderRules.thisDevices("/home/radon/Music/a.flac", android = true, windows = false))
        assertFalse(MusicFolderRules.thisDevices("C:\\Music\\a.flac", android = true, windows = false))
        assertTrue(MusicFolderRules.thisDevices("C:\\Music\\a.flac", android = false, windows = true))
        assertFalse("Linux's file on Windows", MusicFolderRules.thisDevices("/home/radon/Music/a.flac", android = false, windows = true))
        assertTrue(MusicFolderRules.thisDevices("/home/radon/Music/a.flac", android = false, windows = false))
        assertFalse("Windows' file on Linux", MusicFolderRules.thisDevices("D:/Music/a.flac", android = false, windows = false))
        assertFalse("the phone's on a PC", MusicFolderRules.thisDevices("content://media/external/audio/media/12", android = false, windows = false))
    }

    @Test fun manyGoneAtOnceIsAFolderNotThereForNow() {
        assertFalse(MusicFolderRules.tooManyGone(20, 30))
        assertFalse(MusicFolderRules.tooManyGone(137, 3465))
        assertTrue(MusicFolderRules.tooManyGone(900, 3465))
    }
}
