package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** Size of everything: what is kept, and what it comes to. */
class UiSizeRulesTest {
    @Test fun theUsualSizeUnlessOneIsKept() {
        assertEquals(100, UiSizeRules.percent("")); assertEquals(100, UiSizeRules.percent("big"))
        assertEquals(150, UiSizeRules.percent("150")); assertEquals(1.5f, UiSizeRules.factor("150"), 0.0001f)
        assertEquals(1f, UiSizeRules.factor(""), 0.0001f)
    }

    @Test fun aNewInstallIsAtTheUsualSizeAndABackupFromTheOtherDeviceLeavesItThere() {
        assertEquals("nothing kept yet: 100%", 100, UiSizeRules.percent(""))
        val backup = """{"ui_size":"150","crossfade":true}"""
        assertEquals("on the other device the size isn't taken", setOf("crossfade"), BackupMerge.switchesToRestore(backup, crossDevice = true).keys)
        assertEquals("on the same device it comes back", "150", BackupMerge.switchesToRestore(backup, crossDevice = false)["ui_size"])
    }

    @Test fun neverTooSmallToReadOrTooBigToUse() {
        assertEquals(80, UiSizeRules.percent("5")); assertEquals(250, UiSizeRules.percent("900"))
    }

    @Test fun theMenu() {
        assertEquals("100% (usual)", UiSizeRules.label(100)); assertEquals("125%", UiSizeRules.label(125))
        assertEquals("150" to "150%", UiSizeRules.OPTIONS.first { it.first == "150" })
    }
}
