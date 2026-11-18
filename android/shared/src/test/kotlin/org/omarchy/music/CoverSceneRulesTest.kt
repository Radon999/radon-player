package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Now playing preset: a scene (effects and visualizers) for whatever colour a cover is. */
class CoverSceneRulesTest {
    private fun name(rgb: Int) = CoverSceneRules.scene(rgb)!!.name

    @Test fun aSceneForEachColourRoundTheWheel() {
        assertEquals("Embers", name(0xD8201C))          // red
        assertEquals("Lantern light", name(0xE8842A))   // orange
        assertEquals("Fireflies", name(0xE6CF2E))       // yellow
        assertEquals("Forest", name(0x2E9E3C))          // green
        assertEquals("Reef", name(0x1FB8A8))            // teal
        assertEquals("Open sky", name(0x2AA0E0))        // sky blue
        assertEquals("Rain", name(0x2E4FD0))            // blue
        assertEquals("Galaxy", name(0x6A2ED0))          // violet
        assertEquals("Dream", name(0xB42ED0))           // purple
        assertEquals("Blossom", name(0xE02E90))         // pink
        assertEquals("a red just under 360 degrees is still red", "Embers", name(0xD81C3A))
    }

    @Test fun coversWithNoColourAndTheVeryDarkAndVeryPale() {
        assertEquals("Noir", name(0x141414))
        assertEquals("Silver", name(0x808080))
        assertEquals("Mist", name(0xE6E6E6))
        assertEquals("a dark cover with a colour to it", "Night", name(0x0A1030))
        assertEquals("a pale cover with a colour to it", "Daylight", name(0xFFE0EC))
        assertNull("no cover: no scene, the app as you set it", CoverSceneRules.scene(null))
    }

    @Test fun thereIsAlwaysOneAndTheSameCoverAlwaysGivesTheSame() {
        var n = 0
        for (r in 0..255 step 15) for (g in 0..255 step 15) for (b in 0..255 step 15) {
            val rgb = (r shl 16) or (g shl 8) or b
            val s = CoverSceneRules.scene(rgb)
            assertTrue(s != null && s.fx.isNotEmpty() && s.vis.isNotEmpty() && s.bottom.isNotEmpty())
            assertEquals(s, CoverSceneRules.scene(rgb)); n++
        }
        assertTrue(n > 4000)
        assertEquals("seventeen scenes, each with a name of its own", 17, CoverSceneRules.ALL.map { it.name }.toSet().size)
        // every one of the twelve colours is reached
        assertEquals(12, (0 until 360 step 5).map { h -> CoverSceneRules.scene(hslRgb(h.toFloat()))!!.name }.toSet().size)
    }

    @Test fun theBackdropKeepsTheCoversColoursDarkEnoughToReadOn() {
        // teal sky above, a red coat in the middle, wet navy road below
        val back = CoverSceneRules.backdrop(listOf(0x4FC4C8, 0xB03A48, 0x1B2A5C))!!
        assertEquals(3, back.size)
        val hues = back.map { CoverSceneRules.hsl(it).first }
        assertTrue("teal stays teal: " + hues[0], hues[0] in 170f..195f)
        assertTrue("red stays red: " + hues[1], hues[1] > 340f || hues[1] < 10f)
        assertTrue("navy stays navy: " + hues[2], hues[2] in 215f..240f)
        for (c in back) { val l = CoverSceneRules.hsl(c).third; assertTrue("white text reads on it: $l", l in 0.13f..0.38f) }
        assertEquals("white becomes a dark grey, not a colour", 0f, CoverSceneRules.hsl(CoverSceneRules.backdrop(listOf(0xFFFFFF, 0xFFFFFF))!![0]).second, 0.02f)
        assertNull(CoverSceneRules.backdrop(listOf(0x4FC4C8)))
    }

    /** A strong mid-bright colour of that hue. */
    private fun hslRgb(h: Float): Int {
        val c = 0.8f; val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1)); val m = 0.1f
        val (r, g, b) = when ((h / 60f).toInt()) { 0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x); 3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x) }
        return (((r + m) * 255).toInt() shl 16) or (((g + m) * 255).toInt() shl 8) or ((b + m) * 255).toInt()
    }
}
