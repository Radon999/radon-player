package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Your copy of a song is the same song whatever album it's tagged with, and is never downloaded twice. */
class CopyRulesTest {
    @Test fun theSameTitleAndArtistIsTheSameSong() {
        assertTrue(CopyRules.sameSong("I Just Wanna Shine", "Fitz and The Tantrums", "I Just Wanna Shine", "Fitz and the Tantrums"))
        assertTrue("a featuring aside", CopyRules.sameSong("Song", "A feat. B", "Song", "A"))
        assertTrue("punctuation aside", CopyRules.sameSong("Blood in the Cut", "K.Flay", "Blood In The Cut", "KFlay"))
        assertFalse(CopyRules.sameSong("I Just Wanna Shine", "Fitz and The Tantrums", "HandClap", "Fitz and The Tantrums"))
        assertFalse(CopyRules.sameSong("Shine", "Fitz and The Tantrums", "Shine", "Collective Soul"))
    }

    @Test fun anotherVersionIsAnotherSong() {
        assertFalse(CopyRules.sameSong("Song (Live)", "A", "Song", "A"))
        assertFalse(CopyRules.sameSong("Song", "A", "Song - Acoustic", "A"))
        assertEquals(setOf("live"), CopyRules.versionWords("Song (Live)"))
        assertTrue(CopyRules.versionWords("Song").isEmpty())
    }

    @Test fun whatYouHaveIsNotDownloadedAgain() {
        val asked = listOf("mine", "new", "mine too")
        val (get, have) = CopyRules.toDownload(asked, keepBoth = false) { it.startsWith("mine") }
        assertEquals(listOf("new"), get)
        assertEquals(listOf("mine", "mine too"), have)
    }

    @Test fun keepBothDownloadsThemAll() {
        val (get, have) = CopyRules.toDownload(listOf("mine", "new"), keepBoth = true) { it == "mine" }
        assertEquals(listOf("mine", "new"), get)
        assertTrue(have.isEmpty())
    }

    @Test fun itSaysWhatItLeft() {
        assertEquals("", CopyRules.haveText(emptyList()))
        assertTrue(CopyRules.haveText(listOf("One")).contains("“One”"))
        assertTrue(CopyRules.haveText(listOf("One", "Two", "Three")).startsWith("You have 3 of those already"))
    }
}
