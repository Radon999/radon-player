package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** Missing from this album: which songs of an album's tracklist aren't among yours. */
class MissingSongsTest {
    private fun listed(n: Int, title: String, secs: Int = 0, short: String = title) = MissingSong(n, title, short, secs)
    private fun mine(id: String, title: String, secs: Double = 0.0) = Track("missing-$id", "ytm", title, "Missing Band").copy(duration = secs)

    @Test fun aReleasesNameIsComparedWithoutItsKindOnTheEnd() {
        for (s in listOf("Ruby - EP", "Ruby (EP)", "Ruby [EP]", "Ruby EP", "Ruby: EP")) assertEquals(s, "Ruby", MissingSongs.releaseName(s))
        assertEquals("Talk", MissingSongs.releaseName("Talk - Single")); assertEquals("Talk", MissingSongs.releaseName("Talk (Single)"))
        assertEquals("Sleep", MissingSongs.releaseName("Sleep")); assertEquals("Singles Club", MissingSongs.releaseName("Singles Club"))
        assertEquals("a release named only for its kind stays", "EP", MissingSongs.releaseName("EP"))
    }

    @Test fun theSongsYouDontHaveAreTheMissingOnesInTheAlbumsOrder() {
        val all = listOf(listed(1, "Morning Light"), listed(2, "Paper Boats"), listed(3, "Harbour Song"), listed(4, "Last Train Home"))
        val album = listOf(mine("1", "Morning Light"), mine("3", "Harbour Song"))
        assertEquals(listOf(2, 4), MissingSongs.from(all, album, album).map { it.n })
        assertEquals("you have none of it: all of it", listOf(1, 2, 3, 4), MissingSongs.from(all, emptyList(), emptyList()).map { it.n })
        assertEquals("you have all of it: none", emptyList<Int>(), MissingSongs.from(all, emptyList(), all.map { mine("x${it.n}", it.title) }).map { it.n })
    }

    @Test fun aSongYouHaveUnderAnotherSpellingOrVersionIsNotMissing() {
        val all = listOf(listed(1, "Salt & Stone"), listed(2, "Paper Boats (feat. Some Guest)", short = "Paper Boats"),
            listed(3, "Don't Look Down"), listed(4, "Harbour Song"))
        val album = listOf(mine("1", "Salt and Stone"), mine("2", "Paper Boats"), mine("3", "Dont Look Down - Remastered 2011"))
        assertEquals(listOf(4), MissingSongs.from(all, album, album).map { it.n })
    }

    @Test fun aSongOfTheArtistsOnAnotherAlbumOfYoursIsNotMissing() {
        val all = listOf(listed(1, "Morning Light"), listed(2, "Paper Boats"))
        val album = listOf(mine("1", "Morning Light"))
        val yours = album + mine("single", "Paper Boats")   // yours as a single
        assertEquals(emptyList<Int>(), MissingSongs.from(all, album, yours).map { it.n })
    }

    @Test fun aSongOfTheAlbumUnderAnotherNameIsToldByItsLength() {
        val all = listOf(listed(1, "Morning Light", 200), listed(2, "Interlude", 61), listed(3, "Harbour Song", 240))
        val album = listOf(mine("1", "Morning Light", 200.0), mine("2", "Track 02", 62.0))
        assertEquals("the unnamed one is the interlude (within 2 s); the other is missing", listOf(3), MissingSongs.from(all, album, album).map { it.n })
        val far = listOf(mine("1", "Morning Light", 200.0), mine("2", "Track 02", 70.0))
        assertEquals(listOf(2, 3), MissingSongs.from(all, far, far).map { it.n })
    }
}
