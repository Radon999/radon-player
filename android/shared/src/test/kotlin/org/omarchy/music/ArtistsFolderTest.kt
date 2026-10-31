package org.omarchy.music

import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test fun downloadsGoUnderArtists() {
        assertEquals("Music/Artists/AJR/Neotheater/" to "03 100 Bad Days.flac",
            downloadPlace("Music/Artists/", "AJR", "Neotheater", "ALBUM", 3, "100 Bad Days"))
        assertEquals("Music/Artists/Wallows/Spring - EP/", downloadPlace("Music/", "Wallows", "Spring - EP", "EP", 1, "Pictures of Girls").first)
        assertEquals("Music/Artists/Lauv/Paris in the Rain - Single/", downloadPlace("Music/Artists", "Lauv", "Paris in the Rain", "SINGLE", 1, "Paris in the Rain").first)
        assertEquals("Music/Artists/Lauv/Paris in the Rain - Single/", downloadPlace("Music/Artists", "Lauv", "Paris in the Rain", null, null, "Paris in the Rain").first)
        assertEquals("Music/Artists/Wallows/Singles Collection 2017-2020/", downloadPlace("Music/Artists", "Wallows", "Singles Collection 2017-2020", null, null, "Ice Cold Pool").first)
    }
}
