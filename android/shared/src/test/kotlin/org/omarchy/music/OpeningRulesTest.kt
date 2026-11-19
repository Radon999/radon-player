package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Linux' small rules for the PC app: what opens a link, how the window is drawn, what closing the window does. */
class OpeningRulesTest {
    @Test fun aLinkGoesToTheDesktopsOwnOpenerFirst() {
        assertEquals(listOf("xdg-open"), OpeningRules.LINUX_OPENERS.first())
        assertTrue(OpeningRules.LINUX_OPENERS.all { it.isNotEmpty() })
    }

    @Test fun softwareDrawingGoesByTheSettingUnlessTheStartSaysOtherwise() {
        assertTrue(OpeningRules.softwareDrawing(null, setting = true))
        assertFalse(OpeningRules.softwareDrawing(null, setting = false))
        assertFalse(OpeningRules.softwareDrawing("", setting = false))
        assertTrue("asked for this start", OpeningRules.softwareDrawing(" Software ", setting = false))
        assertFalse("turned off for this start", OpeningRules.softwareDrawing("opengl", setting = true))
        assertTrue("a word it doesn't know: the setting", OpeningRules.softwareDrawing("fast", setting = true))
    }

    @Test fun withNoTrayClosingMinimizes() {
        assertEquals("tray", OpeningRules.onClose(hasTray = true))
        assertEquals("minimize", OpeningRules.onClose(hasTray = false))
        // no tray and no minimized windows (Hyprland…): hidden while music plays, closed when nothing does
        assertEquals("hide", OpeningRules.onClose(hasTray = false, noMinimize = true, playing = true))
        assertEquals("quit", OpeningRules.onClose(hasTray = false, noMinimize = true, playing = false))
        assertEquals("tray", OpeningRules.onClose(hasTray = true, noMinimize = true, playing = false))
    }
}
