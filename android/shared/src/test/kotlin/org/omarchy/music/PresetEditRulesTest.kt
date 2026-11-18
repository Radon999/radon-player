package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.omarchy.music.PresetEditRules.Edit
import org.omarchy.music.PresetVisRules.Trio

/** Ready-made presets edited (their effects and visualizers), and choices hidden from a menu. */
class PresetEditRulesTest {
    private val bottoms = setOf("slim", "wave", "bars", "skyline")
    private fun vis(base: Trio, e: Edit) = PresetEditRules.vis(base, e) { it in bottoms }

    @Test fun someEffectsGoneTheOthersStay() {
        val e = Edit("preset-1", fxOff = setOf("sun"), fxAdd = setOf("rain", "grid"))
        assertEquals("grid;twinkle@ff0000;rain", PresetEditRules.fx("grid;sun;twinkle@ff0000", e) { it.substringBefore('@') })
        assertEquals("as it came, with no edit", "grid;sun", PresetEditRules.fx("grid;sun", Edit("x")) { it })
    }

    @Test fun theMiddleVisualizerTakenAwayTheBottomOneKept() {
        val base = Trio("sunburst", true, "skyline")
        assertEquals(Trio("skyline", false, "skyline"), vis(base, Edit("p", middle = "off")))
        assertEquals("only the middle one left", Trio("sunburst", false, "skyline"), vis(base, Edit("p", bottom = "off")))
        assertEquals("another middle one over its own bottom one", Trio("ring", true, "skyline"), vis(base, Edit("p", middle = "ring")))
        assertEquals("neither", "off", vis(base, Edit("p", middle = "off", bottom = "off")).now)
        assertEquals(base, vis(base, Edit("p")))
        assertEquals("a bottom one added to a preset with only a middle one", Trio("ring", true, "wave"), vis(Trio("ring", false, ""), Edit("p", bottom = "wave")))
    }

    @Test fun editsAreKeptALineEachAndAnEmptyOneIsNotKept() {
        val edits = mapOf("preset-1" to Edit("preset-1", setOf("sun"), setOf("rain"), "off", null), "preset-2" to Edit("preset-2"))
        val text = PresetEditRules.write(edits)
        assertEquals("preset-1|sun|rain|off|-", text)
        assertEquals(mapOf("preset-1" to edits.getValue("preset-1")), PresetEditRules.decode(text))
        assertTrue(PresetEditRules.decode("").isEmpty())
    }

    @Test fun hiddenChoicesLeaveTheMenuButNeverTheOneInUse() {
        val options = listOf("a" to "A", "b" to "B", "c" to "C")
        val hidden = PresetEditRules.hide(emptySet(), listOf("a", "b", ""), inUse = setOf("b"))
        assertEquals("the one in use isn't hidden", setOf("a"), hidden)
        assertEquals(listOf("b" to "B", "c" to "C"), PresetEditRules.shown(options, hidden, setOf("b")))
        assertEquals("one hidden earlier and put on since still shows", options, PresetEditRules.shown(options, setOf("a"), setOf("a")))
        assertEquals(setOf("a", "c"), PresetEditRules.hidden(PresetEditRules.hiddenText(setOf("a", "c"))))
    }
}
