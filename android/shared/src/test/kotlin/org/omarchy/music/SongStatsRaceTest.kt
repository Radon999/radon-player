package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongStatsRaceTest {
    /** The DJ and Wrapped read every song's counts off the main thread while a song that ends is counted on it: a
     *  read at that moment failed (3.2.28), and the DJ's set wasn't made. */
    @Test fun countsCanBeReadWhileSongsAreBeingCounted() {
        val dir = java.nio.file.Files.createTempDirectory("songstats").toFile()
        val stats = SongStats(java.io.File(dir, "skips.json"))
        val errors = java.util.Collections.synchronizedList(ArrayList<Throwable>())
        val writer = Thread {
            try { for (i in 0 until 400) { val t = Track("race-$i", "ytm", "Race song $i", "Race band"); if (i % 2 == 0) stats.heardOut(t) else stats.skipped(t) } }
            catch (e: Throwable) { errors += e }
        }
        val readers = (0 until 3).map {
            Thread {
                try { while (writer.isAlive) { stats.rows(); stats.rows(1); stats.habits(0.0, Double.MAX_VALUE, null); stats.get(Track("race-1", "ytm", "Race song 1", "Race band")) } }
                catch (e: Throwable) { errors += e }
            }
        }
        writer.start(); readers.forEach { it.start() }
        writer.join(60_000); readers.forEach { it.join(60_000) }
        assertTrue("no read or count failed: $errors", errors.isEmpty())
        assertEquals(400, stats.rows().size)
    }

    /** What an imported history tells of skips (Spotify's extended history) is added to what was counted here. */
    @Test fun anImportsSkipsAreAddedToTheOnesCountedHere() {
        val dir = java.nio.file.Files.createTempDirectory("songstats-import").toFile()
        val stats = SongStats(java.io.File(dir, "skips.json"))
        val here = Track("here-1", "file", "One Song", "A Band")
        stats.skipped(here)
        stats.imported = { listOf(
            SkipEntry(3, 2, 30.0, Track("sp-1", "lastfm", "One Song", "A. Band"), listOf(10L, 20L, 30L), listOf(40L, 50L)),
            SkipEntry(1, 0, 5.0, Track("sp-2", "lastfm", "Other Song", "A Band"), listOf(5L), emptyList())) }
        val rows = stats.rows().associate { it.first.title to (it.second to it.third) }
        org.junit.Assert.assertEquals("the same song once, with both counts", mapOf("One Song" to (4 to 2), "Other Song" to (1 to 0)), rows)
        org.junit.Assert.assertEquals("shown as the song is known here", "here-1", stats.rows().first { it.first.title == "One Song" }.first.id)
        org.junit.Assert.assertEquals(4 to 2, stats.get(here))
        org.junit.Assert.assertEquals("a time's own", 3 to 2, stats.rows(since = 1).first { it.first.title == "One Song" }.let { it.second to it.third }.let { (a, b) -> (a - 1) to b })
        val h = stats.habits(0.0, 100.0, null)
        org.junit.Assert.assertEquals(3 + 1, h.skips); org.junit.Assert.assertEquals(2, h.full)
    }

    /** A clean slate for skips: the ones counted here before a moment aren't counted; songs heard out, and an import's
     *  skips, are. */
    @Test fun skipsCountedHereBeforeTheCleanSlateArentCounted() {
        val dir = java.nio.file.Files.createTempDirectory("songstats-slate").toFile()
        val stats = SongStats(java.io.File(dir, "skips.json"))
        val t = Track("slate-1", "file", "Slate Song", "A Band")
        stats.skipped(t); stats.skipped(t); stats.heardOut(t)
        org.junit.Assert.assertEquals(2 to 1, stats.get(t))
        stats.skipsFrom = System.currentTimeMillis() / 1000 + 3600   // a slate an hour from now: both skips are before it
        org.junit.Assert.assertEquals("the skips go, the song heard out stays", 0 to 1, stats.get(t))
        org.junit.Assert.assertEquals(listOf(0 to 1), stats.rows().map { it.second to it.third })
        stats.imported = { listOf(SkipEntry(4, 0, 9.0, Track("sp", "lastfm", "Slate Song", "A Band"), listOf(1L, 2L, 3L, 9L), emptyList())) }
        org.junit.Assert.assertEquals("an import's skips are its own", 4 to 1, stats.get(t))
        stats.skipsFrom = 0
        org.junit.Assert.assertEquals("nothing was taken out of the file", 6 to 1, stats.get(t))
    }
}
