package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** A playlist sorts by what its songs are now, not by what they were when they were put in. */
class SortAsNowTest {
    private fun t(id: String, title: String, album: String, artist: String = "HOAX") = Track(id, title = title, artist = artist, album = album)

    // As the playlist keeps them: "the comedown" and "Western Medicine" from when they were singles
    private val kept = listOf(t("1", "trees", "b?"), t("2", "the comedown", "the comedown"), t("3", "deleted scenes", "b? Sides"),
        t("4", "Western Medicine", "Western Medicine", "Hoax"), t("5", "Barely", "words that end with wh(y)"))
    // As their tags are today: moved into the EP and the album
    private val today = mapOf("2" to t("2", "the comedown", "b? Sides"), "4" to t("4", "Western Medicine", "b?"))
    private val now = { x: Track -> today[x.id] ?: x }

    @Test fun byAlbumASongSitsWithTheAlbumItIsInNow() {
        assertEquals("as it was: the two sit apart from their releases", listOf("1", "3", "2", "4", "5"), sortSongs(kept, "album").map { it.id })
        val sorted = sortSongsAsNow(kept, "album", now = now)
        assertEquals(listOf("trees", "Western Medicine", "deleted scenes", "the comedown", "Barely"), sorted.map { it.title })
        assertSame("the playlist's own songs come back, not today's copies", kept[3], sorted[1])
    }

    @Test fun theListsOwnOrderIsLeftAlone() {
        assertSame(kept, sortSongsAsNow(kept, "order", now = now))
        assertSame(kept, sortSongsAsNow(kept, "nonsense", now = now))
        assertEquals(emptyList<Track>(), sortSongsAsNow(emptyList(), "album", now = now))
    }

    @Test fun byArtistAndByPlaysGoByTodayToo() {
        assertEquals("one artist, however it was written then", listOf("b?", "b?", "b? Sides", "b? Sides", "words that end with wh(y)"),
            sortSongsAsNow(kept, "artist", now = now).map { now(it).album })
        val plays = mapOf(songKey(t("x", "Western Medicine", "", "HOAX")) to 9)
        assertEquals("4", sortSongsAsNow(kept, "plays", plays, now).first().id)
    }
}
