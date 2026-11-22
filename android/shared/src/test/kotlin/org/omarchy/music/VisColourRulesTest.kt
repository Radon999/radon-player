package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** A visualizer's colour set in Colours, changed or taken off, without touching the others'. */
class VisColourRulesTest {
    @Test fun setChangedAndTakenOff() {
        var t = ""
        t = VisColourRules.set(t, "blocks", "red")
        t = VisColourRules.set(t, "myvis-3#bottom", "ice")
        assertEquals("red", VisColourRules.get(t, "blocks"))
        assertEquals("ice", VisColourRules.get(t, "myvis-3#bottom"))
        assertEquals("", VisColourRules.get(t, "myvis-3"))   // its middle part comes as it was made
        t = VisColourRules.set(t, "blocks", "rainbow")
        assertEquals("rainbow", VisColourRules.get(t, "blocks"))
        assertEquals("ice", VisColourRules.get(t, "myvis-3#bottom"))
        t = VisColourRules.set(t, "blocks", "")
        assertEquals("", VisColourRules.get(t, "blocks"))
        assertEquals("myvis-3#bottom=ice", t)
    }

    @Test fun nothingSetIsNothing() {
        assertEquals("", VisColourRules.get("", "bars"))
        assertEquals("", VisColourRules.set("", "bars", ""))
    }
}
