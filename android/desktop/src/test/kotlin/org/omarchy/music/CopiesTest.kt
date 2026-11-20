package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Copies of a song: the Duplicates list, and a song going away for another in your playlists. The playlist is the
 *  test's own, made for the test and deleted after it; no file is touched. */
class CopiesTest {
    private fun song(id: String, title: String, artist: String = "Copies Test Band", secs: Double = 200.0) =
        Track("copy-$id", "file", title, artist, file = "/copies-test/$id.flac").copy(duration = secs)

    @Test fun oneSongInTwoFilesIsADuplicateWithItsBestCopyFirst() {
        val songs = listOf(
            song("a1", "Salt and Stone", "The Copies Test Band", secs = 180.0), song("a2", "Salt And Stone (Remastered)", secs = 240.0),
            song("b1", "Paper Boats"), song("b2", "Paper Boats", "Another Band", secs = 231.0),      // another artist's, another length: not a copy
            song("c1", "Zebra"), song("c2", "Zebra"),
            song("d1", "Alone"))
        val groups = Copies.groupsOf(songs, { it.file }, { it.duration }) { false }
        assertEquals("two groups, by name; in each the better copy (here: the longer) first",
            listOf(listOf("copy-a2", "copy-a1"), listOf("copy-c1", "copy-c2")), groups.map { g -> g.map { it.id } })
    }

    @Test fun oneSongTaggedUnderTwoOfItsArtistsIsADuplicate() {
        // What a phone makes of one song in two files: it reads the first artist only of a file that credits two
        val songs = listOf(
            song("n1", "Console", "First Rapper", secs = 165.8), song("n2", "Console", "Second Rapper", secs = 165.8),   // the very same length
            song("n3", "Console", "Somebody Else", secs = 155.0),                                                        // another song of that name
            song("y1", "Get It", "KFlay", secs = 176.49), song("y2", "Get It", "Arkells;K.Flay", secs = 190.0),          // an artist they share
            song("s1", "Skit", "One", secs = 12.0), song("s2", "Skit", "Other", secs = 12.0))                           // too short to tell by length
        val groups = Copies.groupsOf(songs, { it.file }, { 0.0 }) { false }
        assertEquals(setOf(setOf("copy-n1", "copy-n2"), setOf("copy-y1", "copy-y2")), groups.map { g -> g.map { it.id }.toSet() }.toSet())
    }

    @Test fun oneFileListedTwiceIsOneCopyAndGroupsYouKeptAreLeftOut() {
        val twice = listOf(song("a1", "Salt and Stone"), song("a1-again", "Salt and Stone").copy(file = "/copies-test/a1.flac"))
        assertTrue("the same file under two ids is no duplicate", Copies.groupsOf(twice, { it.file }, { 0.0 }) { false }.isEmpty())
        val two = listOf(song("c1", "Zebra"), song("c2", "Zebra"))
        assertEquals(1, Copies.groupsOf(two, { it.file }, { 0.0 }) { false }.size)
        assertTrue("marked Not a duplicate: not shown again", Copies.groupsOf(two, { it.file }, { 0.0 }) { true }.isEmpty())
        assertTrue("songs with no name are never grouped", Copies.groupsOf(listOf(song("n1", "…"), song("n2", "!!")), { it.file }, { 0.0 }) { false }.isEmpty())
    }

    @Test fun theCopyKeptTakesThePlaceOfTheOnesGoingOnce() {
        val a = song("a", "Alpha"); val old1 = song("old1", "Beta"); val old2 = song("old2", "Beta"); val keep = song("keep", "Beta"); val z = song("z", "Zeta")
        fun ids(l: List<Track>) = l.map { it.id }
        assertEquals("at the first one's place, once", listOf("copy-a", "copy-keep", "copy-z"),
            ids(Copies.swapped(listOf(a, old1, z, old2), setOf("copy-old1", "copy-old2"), keep)))
        assertEquals("already in it earlier: not twice", listOf("copy-keep", "copy-a"),
            ids(Copies.swapped(listOf(keep, a, old1), setOf("copy-old1"), keep)))
        assertEquals("none of them in it: as it was", listOf("copy-a", "copy-z"), ids(Copies.swapped(listOf(a, z), setOf("copy-old1"), keep)))
    }

    @Test fun aSongGoingAwayForAnotherIsSwappedInYourPlaylists() {
        Harness.ctx
        val name = "Copies test playlist"
        val a = song("a", "Alpha"); val old = song("old", "Beta"); val keep = song("keep", "Beta")
        try {
            Harness.ui {
                val id = Music.lists.create(name).id
                Music.lists.add(id, listOf(a, old))
                Copies.retarget(listOf("copy-old"), keep)
                assertEquals(listOf("copy-a", "copy-keep"), Music.lists.get(id)!!.tracks.map { it.id })
                Copies.retarget(listOf("copy-keep"), keep)   // going away for itself: nothing happens
                assertEquals(listOf("copy-a", "copy-keep"), Music.lists.get(id)!!.tracks.map { it.id })
            }
        } finally {
            Harness.ui { Music.lists.all().filter { it.name == name }.forEach { Music.lists.delete(it.id) }; Music.musicVersion++ }
        }
    }
}
