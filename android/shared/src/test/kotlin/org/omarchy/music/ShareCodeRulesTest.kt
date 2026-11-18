package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.omarchy.music.ShareCodeRules.Made
import org.omarchy.music.ShareCodeRules.Parts

/** Share codes: what one holds, a preset whole with what it uses, and how it is added to yours. */
class ShareCodeRulesTest {
    private val look = Made("look", "mylook-a1|Dusk|ff8800|101010|202020|ffffff|303030|true|false|rain,snow")
    private val blend = Made("viscolor", "mycol-c3|Embers|0|false|ff0000,ffaa00")
    private val vis = Made("vis", "myvis-b2|Twin rings|ring|true|false|false|0.5|1.0|false|mycol-c3")
    private val preset = Made("preset", "mypreset-p9|My night|mylook-a1|glass|classic|0|rain;twinkle|apple-dark|myvis-b2|1|wave|look|card-3|mycol-c3")
    private val store = listOf(look, blend, vis, preset).associateBy { ShareCodeRules.keyOf(it.line) }

    @Test fun aCodeIsOneLineAndComesBackAsItWent() {
        val parts = Parts(listOf(look), listOf("fav_themes" to "glass"), "0|rain;snow")
        val code = ShareCodeRules.encode(parts)
        assertTrue(code.startsWith("RadonShare:") && '\n' !in code && ' ' !in code)
        assertEquals(parts, ShareCodeRules.decode("  $code \n"))
        assertNull(ShareCodeRules.decode("hello")); assertNull(ShareCodeRules.decode("RadonShare:!!!"))
    }

    @Test fun aPresetGoesWholeWithTheThingsMadeThatItUses() {
        val all = ShareCodeRules.withUsed(listOf(preset)) { store[it] }
        assertEquals("the preset, its look, its visualizer, its blend (which the visualizer uses too)",
            setOf("mypreset-p9", "mylook-a1", "myvis-b2", "mycol-c3"), all.map { ShareCodeRules.keyOf(it.line) }.toSet())
        assertEquals("only the preset is what was shared", listOf("mypreset-p9"), all.filter { it.main }.map { ShareCodeRules.keyOf(it.line) })
        assertEquals("a ready-made look or theme is not carried: everyone has those", emptySet<String>(), ShareCodeRules.mentions("mypreset-x|P|nord|glass|classic|0||apple-dark|ring|1|wave"))
    }

    @Test fun addedUnderNewKeysWithEveryMentionPointedAtThem() {
        var n = 0
        val got = ShareCodeRules.rekeyed(ShareCodeRules.withUsed(listOf(preset)) { store[it] }, { s -> ShareCodeRules.STORES.getValue(s) + "new" + (++n) }, { s -> if (s == "look") setOf("Dusk") else emptySet() })
        val by = got.associateBy { it.store }
        val newLook = ShareCodeRules.keyOf(by.getValue("look").line); val newVis = ShareCodeRules.keyOf(by.getValue("vis").line); val newBlend = ShareCodeRules.keyOf(by.getValue("viscolor").line)
        assertTrue(newLook.startsWith("mylook-new") && newVis.startsWith("myvis-new") && newBlend.startsWith("mycol-new"))
        val p = by.getValue("preset").line.split("|")
        assertEquals("the preset names the new look, visualizer and blend", listOf(newLook, newVis, newBlend), listOf(p[2], p[8], p[13]))
        assertEquals("its ready-made theme and effects are as they were", listOf("glass", "rain;twinkle"), listOf(p[3], p[6]))
        assertEquals("the visualizer names the new blend", newBlend, by.getValue("vis").line.split("|").last())
        assertEquals("a name you have already gets (2)", "Dusk (2)", ShareCodeRules.nameOf(by.getValue("look").line))
        assertEquals("the look's own effects are untouched", "rain,snow", by.getValue("look").line.split("|").last())
    }

    @Test fun whatPastingDidInWords() {
        assertEquals("Added “Dusk” to yours (each is in its menu, under Yours); put on 2 ready-made choices", ShareCodeRules.added(listOf("Dusk"), 2, false))
        assertEquals("Put on its effects", ShareCodeRules.added(emptyList(), 0, true))
    }
}
