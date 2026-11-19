package org.omarchy.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The PC app starts itself again with Java's setting only on the Linux desktops that need it. */
class WindowSystemRulesTest {
    private val R = WindowSystemRules

    @Test fun onlyOnTheDesktopsThatPlaceWindowsThemselves() {
        assertTrue(R.startAgain("Linux", mapOf("XDG_CURRENT_DESKTOP" to "Hyprland")))
        assertTrue(R.startAgain("Linux", mapOf("XDG_CURRENT_DESKTOP" to "sway:wlroots")))
        assertTrue("the desktop calls itself something else", R.startAgain("Linux", mapOf("XDG_CURRENT_DESKTOP" to "MyOS", "HYPRLAND_INSTANCE_SIGNATURE" to "abc")))
        assertTrue(R.startAgain("Linux", mapOf("DESKTOP_SESSION" to "/usr/share/xsessions/i3")))
        assertFalse(R.startAgain("Linux", mapOf("XDG_CURRENT_DESKTOP" to "GNOME")))
        assertFalse(R.startAgain("Linux", mapOf("XDG_CURRENT_DESKTOP" to "KDE", "DESKTOP_SESSION" to "plasma")))
        assertFalse("no desktop named (the build server)", R.startAgain("Linux", emptyMap()))
        assertFalse(R.startAgain("Windows 11", mapOf("XDG_CURRENT_DESKTOP" to "Hyprland")))
    }

    @Test fun notTwiceAndNotOverThePersonsOwnSetting() {
        assertFalse(R.startAgain("Linux", mapOf("XDG_CURRENT_DESKTOP" to "Hyprland", R.SETTING to "1")))
        assertFalse(R.startAgain("Linux", mapOf("HYPRLAND_INSTANCE_SIGNATURE" to "abc", R.SETTING to "0")))
    }
}
