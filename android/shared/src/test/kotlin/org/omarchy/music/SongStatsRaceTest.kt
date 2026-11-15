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
}
