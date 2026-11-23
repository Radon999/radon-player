package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.omarchy.music.ui.Looks
import org.omarchy.music.ui.LookFx
import org.omarchy.music.ui.LyricsCards
import org.omarchy.music.ui.MenuGroups
import org.omarchy.music.ui.MyPresets
import org.omarchy.music.ui.NP_VIS_STYLES
import org.omarchy.music.ui.Themes
import org.omarchy.music.ui.UiLayouts
import org.omarchy.music.ui.VIS_COLORS
import org.omarchy.music.ui.VIS_STYLES
import org.omarchy.music.ui.WRAPS_BUILT
import org.omarchy.music.ui.WS

/** Personalization's ready-made choices: thirty more of each kind came in 4.0.14. Every one has a name and a key of its
 *  own, and every preset only names things that are there (a preset naming a look that isn't would put on nothing). */
class PersonalizationTest {
    private fun unique(what: String, keys: List<String>) = assertEquals("$what: every key once", keys.size, keys.toSet().size)

    @Test fun thirtyMoreOfEachAndNoKeyTwice() {
        Harness.ctx
        val looks = Looks.ALL.map { it.first }
        unique("looks", looks); unique("themes", Themes.MENU.map { it.first }); unique("layouts", UiLayouts.BUILT_IN.map { it.key })
        unique("effects", LookFx.ALL.map { it.key }); unique("visualizers", VIS_STYLES.map { it.first }); unique("visualizer colours", VIS_COLORS.map { it.first })
        unique("Wrapped styles", WS.STYLES.map { it.first }); unique("lyrics cards", LyricsCards.PRESETS.map { it.key }); unique("presets", MyPresets.BUILT.map { it.key })
        assertTrue("the thirty new looks are in the Look menu", looks.containsAll(MenuGroups.NEW))
        assertEquals(30, MenuGroups.NEW.size)
        assertEquals(45, Themes.ALL.size); assertEquals(45, UiLayouts.BUILT_IN.size)
        assertEquals("121: thirty more that move to the music", 121, LookFx.ALL.size); assertEquals(30, LookFx.ALL.count { it.music }); assertEquals("thirty Music scenes", 30, looks.count { it.startsWith("scene-") }); assertEquals(60, LyricsCards.PRESETS.size); assertEquals("sixty, and Follows the cover and Glass cover", 62, MyPresets.BUILT.size)
        assertEquals(30, WRAPS_BUILT.size)
        assertEquals("ninety visualizers", 90, VIS_STYLES.count { !it.first.startsWith("myvis-") })
        for (k in MenuGroups.NEW) assertTrue("a colour blend for $k", VIS_COLORS.any { it.first == k })
        // names don't repeat within a menu either (two things called the same can't be told apart)
        for ((what, names) in listOf("looks" to Looks.ALL.map { it.second }, "themes" to Themes.MENU.map { it.second }, "layouts" to UiLayouts.BUILT_IN.map { it.name },
            "effects" to LookFx.ALL.map { it.name }, "presets" to MyPresets.BUILT.map { it.name }))
            assertEquals("$what: every name once: " + names.groupBy { it }.filter { it.value.size > 1 }.keys, names.size, names.toSet().size)
    }

    @Test fun everyPresetNamesThingsThatAreThere() {
        Harness.ctx
        val fx = LookFx.ALL.map { it.key }.toSet() + "stars"
        val vis = NP_VIS_STYLES.map { it.first }.toSet()
        val wraps = WS.STYLES.map { it.first }.toSet()
        val colours = VIS_COLORS.map { it.first }.toSet()
        for (p in MyPresets.BUILT) {
            assertTrue("${p.name}: its look ${p.look}", Looks.known(p.look))
            assertTrue("${p.name}: its theme ${p.theme}", Themes.known(p.theme))
            assertTrue("${p.name}: its layout ${p.layout}", UiLayouts.BUILT_IN.any { it.key == p.layout })
            for (e in p.fx.split(';').filter { it.isNotBlank() }) assertTrue("${p.name}: its effect $e", LookFx.keyOf(e) in fx)
            assertTrue("${p.name}: its visualizer ${p.npVis}", p.npVis in vis)
            if (p.npBottom.isNotEmpty()) assertTrue("${p.name}: its bottom visualizer ${p.npBottom}", p.npBottom in vis)
            if (p.wrapped.isNotEmpty()) assertTrue("${p.name}: its Wrapped style ${p.wrapped}", p.wrapped in wraps)
            if (p.card.isNotEmpty()) assertTrue("${p.name}: its lyrics card ${p.card}", p.card == LyricsCards.LOOK || p.card == LyricsCards.NOW || LyricsCards.of(p.card) != null)
            if (p.visColor.isNotEmpty()) assertTrue("${p.name}: its visualizer colour ${p.visColor}", p.visColor in colours)
        }
        // each new look's own effect is one that can be drawn
        for (k in MenuGroups.NEW) assertTrue("a section for $k", MenuGroups.look(k).startsWith("Materials and places"))
    }

    @Test fun theMenusFoldIntoAFewSections() {
        Harness.ctx
        fun sections(what: String, keys: List<String>, of: (String) -> String, most: Int) {
            val by = keys.groupBy(of)
            assertTrue("$what in ${by.size} sections: ${by.mapValues { it.value.size }}", by.size in 3..7)
            assertTrue("$what: no section longer than $most: ${by.mapValues { it.value.size }}", by.values.all { it.size <= most })
        }
        sections("Looks", Looks.ALL.map { it.first }, MenuGroups::look, 40)
        sections("Themes", Themes.MENU.map { it.first }, MenuGroups::theme, 30)
        sections("Layouts", UiLayouts.BUILT_IN.map { it.key }, MenuGroups::layout, 30)
        sections("Effects", LookFx.MENU.map { it.first }, MenuGroups::effect, 40)
        sections("Visualizers", NP_VIS_STYLES.map { it.first }, MenuGroups::vis, 36)
    }

    @Test fun everySceneFromACoverCanBeDrawn() {
        Harness.ctx
        val fx = LookFx.ALL.map { it.key }.toSet()
        val middle = org.omarchy.music.ui.Vis.BUILT_CENTRED
        val all = VIS_STYLES.map { it.first }.toSet()
        for (s in CoverSceneRules.ALL) {
            for (e in s.fx) assertTrue("${s.name}: its effect $e", e in fx)
            assertTrue("${s.name}: ${s.vis} goes round the cover", s.vis in middle)
            assertTrue("${s.name}: ${s.bottom} stands along the bottom", s.bottom in all && s.bottom !in middle)
        }
    }
}
