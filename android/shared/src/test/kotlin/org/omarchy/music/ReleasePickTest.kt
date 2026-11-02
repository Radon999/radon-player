package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleasePickTest {
    private fun r(type: String?, title: String, artist: String = "Trevor Daniel", hires: Boolean = false, single: Boolean = false) =
        ReleasePick.Release(type, artist, title, hires, single)

    @Test fun albumOverCompilationEpAndSingle() {
        val all = listOf(r("COMPILATION", "Game Music", "Various Artists", hires = true), r("SINGLE", "Falling"),
            r("EP", "Homesick"), r("ALBUM", "Nicotine"))
        assertEquals(3, ReleasePick.best(all, "Game Music"))
        assertEquals(2, ReleasePick.best(all.take(3)))   // no album: the EP
        assertEquals(1, ReleasePick.best(all.take(2)))   // no EP: the single
        assertEquals(0, ReleasePick.best(all.take(1)))   // only the compilation
    }

    @Test fun deluxeFirstThenHiRes() {
        val all = listOf(r("ALBUM", "Nicotine", hires = true), r("ALBUM", "Nicotine (Deluxe Edition)"), r("ALBUM", "Other Album", "Various Artists"))
        assertEquals(1, ReleasePick.best(all))
        assertEquals(0, ReleasePick.best(listOf(r("ALBUM", "Nicotine", hires = true), r("ALBUM", "Nicotine"))))
    }
}
