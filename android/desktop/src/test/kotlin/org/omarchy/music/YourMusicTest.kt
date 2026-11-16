package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Your music as the tabs show it: a song in your folders is one of your songs, its album tag says which tab it is
 *  in, and a tab picked by hand (Tagger > Tab) wins until it is taken back. No file is touched: the songs are the
 *  test's own. */
class YourMusicTest {
    private fun mine(id: String, album: String) =
        Track("ym-$id", "file", "Song $id", "Your Music Test Band", file = "/your-music-test/$id.flac").copy(album = album)

    @Test fun theTabASongIsIn() {
        Harness.ctx
        val had = Music.local.files
        val ep = mine("ep", "Your Music Test - EP"); val plain = mine("plain", "Your Music Test Album")
        try {
            Harness.ui {
                Music.local.files = had + listOf(ep, plain); Music.musicVersion++
                assertTrue("a song in your folders is one of your songs", YourMusic.songs().any { it.id == ep.id })
                assertEquals("ep", YourMusic.tabOf(ep))
                assertEquals("album", YourMusic.tabOf(plain))
                assertEquals("ep", YourMusic.releaseKind(ep.album, listOf(ep)))
                assertNull("a stream is not yours: in no tab", YourMusic.tabOf(Track("ym-stream", "tidal", "Stream", "Your Music Test Band")))

                YourMusic.setTab(listOf(plain), "single")
                assertEquals("put there by hand", "single", YourMusic.tabOf(plain))
                assertEquals("single", YourMusic.releaseKind(plain.album, listOf(plain)))
                YourMusic.setTab(listOf(plain), null)
                assertEquals("taken back: where its tags put it", "album", YourMusic.tabOf(plain))
            }
        } finally {
            Harness.ui {
                Music.tabPicks.remove(YourMusic.tabKey(ep)); Music.tabPicks.remove(YourMusic.tabKey(plain))
                Music.local.files = had; Music.musicVersion++; Music.saveState()
            }
        }
    }

    @Test fun yourSongsByAlbumAndByFolderHoldEverySong() {
        Harness.ctx
        val had = Music.local.files
        val a = mine("a", "Your Music Test Album"); val b = mine("b", "Your Music Test Album")
        try {
            Harness.ui {
                Music.local.files = had + listOf(a, b); Music.musicVersion++
                val album = YourMusic.albums().first { (_, songs) -> songs.any { it.id == a.id } }
                assertEquals("Your Music Test Album", album.first.title)
                assertEquals("Your Music Test Band", album.first.artist)
                assertTrue(album.second.any { it.id == b.id })
                assertEquals("every song is in one folder", YourMusic.songs().size, YourMusic.folderSongs().sumOf { it.second.size })
                assertEquals("and under one genre", YourMusic.songs().size, YourMusic.genres().sumOf { it.second.size })
            }
        } finally {
            Harness.ui { Music.local.files = had; Music.musicVersion++ }
        }
    }
}
