package org.omarchy.music

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class FolderCleanupTest {
    @Test fun onlyLeftoversMeansNoMusic() {
        assertTrue(noAudioLeft(emptyList()))
        assertTrue(noAudioLeft(listOf("cover.jpg", "Folder.JPG", "CD1/01 Song.lrc", "notes.txt", "rip.log", "album.cue",
            "album.nfo", "list.m3u8", "Thumbs.db", "desktop.ini", ".DS_Store")))
        assertFalse(noAudioLeft(listOf("cover.jpg", "CD2/01 Song.FLAC")))
        assertFalse(noAudioLeft(listOf("song.mp4")))
        assertFalse(noAudioLeft(listOf("song.Opus")))
        assertFalse(noAudioLeft(listOf("01 Song.flac.part")))   // a download on its way
        assertFalse(noAudioLeft(listOf("README")))              // anything unknown keeps the folder
        assertFalse(noAudioLeft(listOf("video.mkv")))
        assertFalse(noAudioLeft(listOf(".jpg")))
        assertTrue(isAudioName("C:\\Music\\a.AIFF")); assertFalse(isAudioName("a.jpg"))
    }

    @Test fun upToTheArtistOrTheMusicFolder() {
        val roots = listOf("C:\\Users\\x\\Music\\")
        assertEquals(listOf("C:/Users/x/Music/Artists/AJR/Neotheater/CD1", "C:/Users/x/Music/Artists/AJR/Neotheater", "C:/Users/x/Music/Artists/AJR"),
            cleanupChain("C:\\Users\\x\\Music\\Artists\\AJR\\Neotheater\\CD1", roots))
        assertEquals(listOf("C:/Users/x/Music/AJR/Singles/Talk - Single", "C:/Users/x/Music/AJR/Singles", "C:/Users/x/Music/AJR"),
            cleanupChain("C:/Users/x/Music/AJR/Singles/Talk - Single/", roots))
        assertEquals(listOf("/m/Artists/AJR/Neo/CD1"), cleanupChain("/m/Artists/AJR/Neo/CD1", listOf("/m/Artists/AJR/Neo")))
        assertEquals(2, cleanupChain("c:/users/x/music/AJR/Neo", roots).size)   // Windows: any case
        assertTrue(cleanupChain("C:/Users/x/Music", roots).isEmpty())            // the music folder itself never
        assertTrue(cleanupChain("D:/Elsewhere/AJR/Neo", roots).isEmpty())        // nor outside it
    }

    @Test fun foldersLeftAloneAndOtherRootsStay() {
        assertEquals(listOf("/m/A/B"), cleanupChain("/m/A/B", listOf("/m"), listOf("/m/A/Keep")))
        assertTrue(cleanupChain("/m/A/B", listOf("/m"), listOf("/m/A")).isEmpty())
        assertEquals(listOf("/m/A/B"), cleanupChain("/m/A/B", listOf("/m", "/m/A/Downloads")))
        assertEquals(listOf("/m/Downloads/A"), cleanupChain("/m/Downloads/A", listOf("/m", "/m/Downloads")))
    }

    @Test fun picksTheHighestFolderWithNoMusic() {
        val chain = listOf("/m/A/B", "/m/A")
        val bare = mapOf("/m/A/B" to listOf("cover.jpg"), "/m/A" to listOf("B/cover.jpg", "C/x.lrc"))
        assertEquals("/m/A", cleanupPick(chain) { bare[it] })
        val other = mapOf("/m/A/B" to listOf("cover.jpg"), "/m/A" to listOf("B/cover.jpg", "C/x.flac"))
        assertEquals("/m/A/B", cleanupPick(chain) { other[it] })
        assertNull(cleanupPick(chain) { if (it == "/m/A/B") listOf("a.Mp3") else emptyList() })
        assertNull(cleanupPick(chain) { null })   // can't be read: left alone
    }
}
