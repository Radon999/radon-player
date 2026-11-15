package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Importing an .m3u8 playlist: where each line says its song is. */
class M3uPathsTest {
    @Test fun aPhonesStorageRootIsTakenOffTheFront() {
        assertEquals("Music/Artists/Sody/Let Go.flac", M3uPaths.clean("/storage/emulated/0/Music/Artists/Sody/Let Go.flac"))
        assertEquals("Music/a.mp3", M3uPaths.clean("/sdcard/Music/a.mp3"))
        assertEquals("Music/a.mp3", M3uPaths.clean("/storage/self/primary/Music/a.mp3"))
        assertEquals("another root stays", "/mnt/card/Music/a.mp3", M3uPaths.clean("/mnt/card/Music/a.mp3"))
    }

    @Test fun aFileAddressIsReadAsAPath() {
        assertEquals("Music/My Songs/a b.flac", M3uPaths.clean("file:///storage/emulated/0/Music/My%20Songs/a%20b.flac"))
        assertEquals("a plus in a name stays a plus", "/music/1+1.flac", M3uPaths.clean("file:///music/1+1.flac"))
    }

    @Test fun aWindowsPlaceIsReadWithSlashes() {
        assertEquals("C:/Users/me/Music/a.flac", M3uPaths.clean("C:\\Users\\me\\Music\\a.flac"))
        assertEquals("../Album/01.flac", M3uPaths.clean("..\\Album\\01.flac"))
    }

    @Test fun aPlaceIsWholeOrFromThePlaylistsFolder() {
        assertTrue(M3uPaths.whole("/music/a.flac"))
        assertTrue(M3uPaths.whole("C:/Users/me/Music/a.flac"))
        assertFalse(M3uPaths.whole("../Album/01.flac"))
        assertFalse(M3uPaths.whole("a.flac"))
        assertFalse("the phone's own way of naming a place counts from the storage root's folder", M3uPaths.whole("Music/a.flac"))
    }
}
