package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SongGainsTest {
    @Test fun perOutput() {
        val dir = java.nio.file.Files.createTempDirectory("gains").toFile()
        val a = File(dir, "v.json"); val b = File(dir, "vo.json")
        val t = Track("1", title = "Go Go", artist = "Someone")
        val g = SongGains(a, b)
        g.set(listOf(t), 2.0)                         // every output
        g.set(listOf(t), 6.0, "bt:WH-1000XM6")        // the headphones only
        assertEquals(6.0, g.get(t, "bt:WH-1000XM6"), 0.0)
        assertEquals(2.0, g.get(t, "car:Chevrolet"), 0.0)
        assertEquals(2.0, g.get(t), 0.0)
        val again = SongGains(a, b)                   // kept
        assertEquals(6.0, again.get(t, "bt:WH-1000XM6"), 0.0)
        assertEquals(mapOf("bt:WH-1000XM6" to 6.0), again.outputsOf(t))
        again.set(listOf(t), 0.0); again.set(listOf(t), 0.0, "bt:WH-1000XM6")
        assertFalse(again.has(t))
        again.set(listOf(t), 3.0, "speaker")
        assertTrue(again.has(t)); assertEquals(0.0, again.get(t, "wired"), 0.0)
    }
}
