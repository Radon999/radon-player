package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Download: which of the songs asked for are new, why the song playing can't be downloaded, and how downloads are
 *  said to come. Nothing is downloaded here: only the rules, and the asks that end before a download begins. */
class DownloadsTest {
    private fun song(id: String, source: String = "ytm") = Track("dl-$id", source, "Song $id", "Download Test Band")

    @Test fun onlyTheSongsYouDontHaveAndThatArentWaitingAreNew() {
        val asked = listOf(song("new"), song("file", "file"), song("have"), song("waiting"), song("sc", SoundCloud.KEY), song("new2", "tidal"))
        val new = Downloads.newOf(asked, has = { it == "dl-have" }, waiting = setOf("dl-waiting"))
        assertEquals("your file, the one downloaded, the one waiting and SoundCloud's are left out", listOf("dl-new", "dl-new2"), new.map { it.id })
        assertTrue(Downloads.newOf(emptyList(), { false }, emptySet()).isEmpty())
    }

    @Test fun whyTheSongPlayingCantBeDownloaded() {
        val has = { id: String -> id == "dl-have" }
        val waiting = setOf("dl-waiting")
        assertEquals("nothing is playing", Downloads.whyNot(null, has, waiting))
        assertEquals("it is your own file", Downloads.whyNot(song("file", "file"), has, waiting))
        assertEquals("it is downloaded already", Downloads.whyNot(song("have"), has, waiting))
        assertEquals("it is being downloaded", Downloads.whyNot(song("waiting"), has, waiting))
        assertEquals("SoundCloud songs only stream", Downloads.whyNot(song("sc", SoundCloud.KEY), has, waiting))
        assertEquals("a streamed song you don't have: it can", "", Downloads.whyNot(song("new"), has, waiting))
    }

    @Test fun howDownloadsAreSaidToCome() {
        assertEquals("in FLAC (hi-res where Tidal has it)", Downloads.qualityText(true, "flac24", canMp3 = true))
        assertEquals("in FLAC 16-bit", Downloads.qualityText(true, "flac16", canMp3 = true))
        assertEquals("the PC makes MP3s", "in MP3 320 kbps", Downloads.qualityText(true, "mp3", canMp3 = true))
        assertEquals("the phone can't: Tidal's own 320 kbps", "at 320 kbps (AAC)", Downloads.qualityText(true, "mp3", canMp3 = false))
        assertEquals("without Tidal, whatever is picked", "from YouTube, its best (Opus 160 kbps; Tidal isn't connected)", Downloads.qualityText(false, "flac24", canMp3 = true))
    }

    @Test fun askingForSongsThatCantBeDownloadedStartsNothing() {
        Harness.ctx
        Harness.ui {
            val before = Music.downloads
            Downloads.start(listOf(song("sc1", SoundCloud.KEY), song("sc2", SoundCloud.KEY)))
            Downloads.start(listOf(song("file", "file")))
            Downloads.start(emptyList())
            assertEquals("nothing joined the songs waiting", before, Music.downloads)
        }
    }
}
