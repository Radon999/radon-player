package org.omarchy.music

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class ArtistsFolderTest {
    @Test fun kindsFromFolders() {
        assertEquals("album", folderKind("Music/Artists/AJR/Neotheater/"))
        assertEquals("ep", folderKind("Music/Artists/Imagine Dragons/Imagine Dragons EP/"))
        assertEquals("ep", folderKind("C:\\Users\\x\\Music\\Artists\\Wallows\\Spring - EP\\"))
        assertEquals("single", folderKind("Music/Artists/Lauv/Paris in the Rain - Single/"))
        assertEquals("album", folderKind("Music/Artists/Wallows/Singles Collection 2017-2020/"))
        assertEquals("album", folderKind("Music/Artists/Coldplay/Parachutes/CD1/"))
        assertEquals(null, folderKind("Music/All music/released/AJR/Neotheater/"))
        assertEquals(null, folderKind("Music/Artists/AJR/"))
    }

    @Test fun downloadsAndReleaseNames() {
        assertEquals("Music/Mine/AJR/Neotheater/" to "03 100 Bad Days.flac",
            downloadPlace("Music/Mine/", "AJR", "Neotheater", "ALBUM", 3, "100 Bad Days"))
        assertEquals("Music/Wallows/Spring - EP/", downloadPlace("Music/", "Wallows", "Spring", "EP", 1, "Pictures of Girls").first)
        assertEquals("Music/Lauv/Paris in the Rain - Single/", downloadPlace("Music", "Lauv", "Paris in the Rain", "SINGLE", 1, "Paris in the Rain").first)
        assertEquals("Music/Wallows/Singles Collection 2017-2020/", downloadPlace("Music", "Wallows", "Singles Collection 2017-2020", null, null, "Ice Cold Pool").first)
        assertTrue(isSingleRelease("I Won't - Single")); assertTrue(isSingleRelease("RUN (Single)"))
        assertFalse(isSingleRelease("Singles Collection 2017-2020")); assertFalse(isSingleRelease("All 4 Nothing"))
        assertEquals("Spring - EP", releaseName("Spring - EP", "EP")); assertEquals("Talk - Single", releaseName("Talk", "SINGLE"))
        assertEquals("Blonde", releaseName("Blonde", "ALBUM"))
    }
}
