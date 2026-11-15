package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The picture beside a song that is its cover, when the file holds none. The folders are the owner's, as found. */
class FolderCoverRulesTest {
    @Test fun thePictureNamedAsACoverIs() {
        assertEquals("cover.jpg", FolderCoverRules.pick(listOf("Another_Round.mp3", "You_Say.mp3", "cover.jpg")))
        assertEquals("any case", "Folder.JPG", FolderCoverRules.pick(listOf("a.mp3", "Folder.JPG")))
        assertEquals("cover before folder, whatever the order in the folder", "cover.png",
            FolderCoverRules.pick(listOf("folder.jpg", "back.jpg", "cover.png", "a.flac")))
    }

    @Test fun theOnlyPictureInTheFolderIsWhateverItsName() {
        assertEquals("https___images.genius.com_e65375d9af4b7795521dbe82965d47c5.640x640x1.jpg",
            FolderCoverRules.pick(listOf("0_03 Danny's Den.m4a", "1_02 Rosie.m4a", "https___images.genius.com_e65375d9af4b7795521dbe82965d47c5.640x640x1.jpg")))
    }

    @Test fun severalPicturesWithNoSuchNameAreNotGuessedAt() {
        assertNull(FolderCoverRules.pick(listOf("a.mp3", "scan1.jpg", "scan2.png")))
        assertNull(FolderCoverRules.pick(listOf("a.mp3", "b.flac", "notes.txt")))
        assertNull(FolderCoverRules.pick(emptyList()))
    }
}
