package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameLogRulesTest {
    private val R = FrameLogRules

    @Test fun onlyFramesThatSkippedOneAreSlowAndATimeAwayIsNotAFrame() {
        var l = FrameLogRules.Log()
        for (ms in listOf(16, 17, 16, 40, 200, 5000, 0)) l = l.plus(ms, "Playlists")
        assertEquals(5, l.frames); assertEquals(2, l.slow); assertEquals(1, l.freezes)
        assertEquals(listOf(FrameLogRules.Slow(200, "Playlists"), FrameLogRules.Slow(40, "Playlists")), l.worst)
    }

    @Test fun onlyTheSlowestAreKept() {
        var l = FrameLogRules.Log()
        for (i in 1..100) l = l.plus(40 + i, "Music")
        assertEquals(R.KEEP, l.worst.size); assertEquals(140, l.worst.first().ms)
    }

    @Test fun itSaysWhatItRecorded() {
        assertEquals("Nothing recorded yet", R.line(FrameLogRules.Log()))
        assertEquals("1,000 frames, none slow", R.line(FrameLogRules.Log(frames = 1000)))
        val l = FrameLogRules.Log(1000, 20, 1, listOf(FrameLogRules.Slow(240, "Playlists"), FrameLogRules.Slow(50, "Music")))
        assertEquals("1,000 frames, 20 slow (2.0%), 1 freeze, the worst 240 ms on Playlists", R.line(l))
        val rep = R.report(l, "5.3.0", "PC")
        assertTrue(rep.startsWith("Radon Player 5.3.0 · PC · slow frames\n"))
        assertTrue(rep.contains("Where the slowest were: Playlists (1, up to 240 ms), Music (1, up to 50 ms)"))
        assertTrue(rep.trimEnd().endsWith("50 ms · Music"))
    }
}
