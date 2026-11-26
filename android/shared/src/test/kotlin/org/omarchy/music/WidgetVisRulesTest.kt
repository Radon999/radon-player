package org.omarchy.music

import org.omarchy.music.WidgetVisRules.Pick
import org.junit.Test
import org.junit.Assert.assertEquals

/** The Visualizer widget follows Now playing's visualizer (and its Dual bottom one), or a style of its own. */
class WidgetVisRulesTest {
    private val styles = setOf("slim", "bars", "ring", "wave", "glow", "myvis-1")
    private fun pick(choice: String, now: String, second: String? = null, tab: String = "bars") =
        WidgetVisRules.pick(choice, now, second, tab) { it in styles }

    @Test fun followsNowPlayingByDefault() {
        assertEquals(Pick("ring"), pick("", "ring"))
        assertEquals(Pick("slim"), pick("", "slim"))
    }

    @Test fun changesWhenNowPlayingChanges() {
        assertEquals(Pick("wave"), pick("", "wave"))
        assertEquals(Pick("myvis-1"), pick("", "myvis-1"))
    }

    @Test fun dualBringsItsBottomStyle() {
        assertEquals(Pick("ring", "glow"), pick("", "ring", second = "glow"))
        assertEquals(Pick("ring"), pick("", "ring", second = "off"))
        assertEquals(Pick("ring"), pick("", "ring", second = "ring"))
    }

    @Test fun nowPlayingOffShowsTheVisualizerTabs() {
        assertEquals(Pick("bars"), pick("", "off"))
        assertEquals(Pick("wave"), pick("", "off", tab = "wave"))
    }

    @Test fun aStyleOfItsOwn() {
        assertEquals(Pick("wave"), pick("wave", "ring", second = "glow"))
        assertEquals(Pick("slim"), pick("slim", "ring"))
        assertEquals(Pick("bars"), pick("tab", "ring"))
    }

    @Test fun aDeletedStyleFollowsNowPlaying() {
        assertEquals(Pick("ring"), pick("myvis-9", "ring"))
        assertEquals(Pick("bars"), pick("", "myvis-9"))
        assertEquals(Pick("ring"), pick("off", "ring"))
    }
}
