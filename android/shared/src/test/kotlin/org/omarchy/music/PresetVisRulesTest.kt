package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test
import org.omarchy.music.PresetVisRules.Trio

/** Presets leave my middle / bottom visualizer: what a preset puts on Now playing with each switch. */
class PresetVisRulesTest {
    private val bottoms = setOf("slim", "wave", "bars", "skyline")
    private fun mix(yours: Trio, preset: Trio, m: Boolean, b: Boolean) = PresetVisRules.mix(yours, preset, m, b) { it in bottoms }
    private val preset = Trio("ring", true, "wave")

    @Test fun neitherOnThePresetsAsItIs() {
        assertEquals(preset, mix(Trio("off", false, "slim"), preset, false, false))
    }

    @Test fun justTheLooksYoursStayEvenWhenYouHaveNone() {
        assertEquals(Trio("off", false, "slim"), mix(Trio("off", false, "slim"), preset, true, true))
        assertEquals(Trio("galaxy", true, "bars"), mix(Trio("galaxy", true, "bars"), preset, true, true))
        assertEquals("yours is one along the bottom", Trio("bars", false, "bars"), mix(Trio("bars", false, "slim"), preset, true, true))
    }

    @Test fun thePresetsBottomOneButNotItsMiddleOne() {
        assertEquals("no middle one of yours: only its bottom one", Trio("wave", false, "wave"), mix(Trio("off", false, "slim"), preset, true, false))
        assertEquals("your middle one over its bottom one", Trio("galaxy", true, "wave"), mix(Trio("galaxy", false, "slim"), preset, true, false))
        assertEquals("a preset with one bottom style", Trio("galaxy", true, "skyline"), mix(Trio("galaxy", false, "slim"), Trio("skyline", false, ""), true, false))
    }

    @Test fun thePresetsMiddleOneOverYourBottomOne() {
        assertEquals(Trio("ring", true, "bars"), mix(Trio("bars", false, "slim"), preset, false, true))
        assertEquals("no bottom one of yours: only its middle one", Trio("ring", false, "slim"), mix(Trio("off", false, "slim"), preset, false, true))
        assertEquals("a preset with nothing in the middle: yours along the bottom", Trio("bars", false, "bars"), mix(Trio("galaxy", true, "bars"), Trio("skyline", false, ""), false, true))
    }
}
