package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** Favorite settings: starred by name, kept in the order they were starred. */
class SettingFavRulesTest {
    @Test fun aStarPutsItInAndAnotherTakesItOut() {
        var f = SettingFavRules.toggled(emptySet(), "Auto mono")
        f = SettingFavRules.toggled(f, "Crossfade")
        assertEquals(listOf("Auto mono", "Crossfade"), f.toList())
        assertEquals(f, SettingFavRules.read(SettingFavRules.write(f)))
        assertEquals(listOf("Crossfade"), SettingFavRules.toggled(f, "Auto mono").toList())
        assertEquals(emptySet<String>(), SettingFavRules.read(""))
        assertEquals(f, SettingFavRules.toggled(f, "  "))
    }
}
