package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** Wrapped's Least skipped: the songs you hardly ever skip, the never-skipped first, then the ones skipped now and then. */
class LeastSkippedTest {
    @Test fun neverSkippedFirstThenTheHardlySkipped() {
        val dir = java.nio.file.Files.createTempDirectory("least-skipped").toFile()
        val stats = SongStats(java.io.File(dir, "skips.json"))
        fun e(title: String, skips: Int, full: Int) = SkipEntry(skips, full, 0.0, Track("t-$title", "lastfm", title, "Band"),
            List(skips) { 10L + it }, List(full) { 50L + it })
        stats.imported = { listOf(e("Always", 0, 9), e("Never twice", 0, 4), e("Hardly", 1, 20), e("Often", 6, 6), e("Once only", 0, 1)) }
        val h = stats.habits(0.0, 100.0, null)
        assertEquals("never skipped first (most heard out first), then hardly skipped; heard out under 3 times left out",
            listOf("Always", "Never twice", "Hardly", "Often"), h.least.map { it.first.title })
        assertEquals(1 to 20, h.least[2].second to h.least[2].third)
    }
}
