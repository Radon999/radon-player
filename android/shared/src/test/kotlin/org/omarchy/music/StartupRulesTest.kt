package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Starting with Linux, and which of a release's files is this system's installer. */
class StartupRulesTest {
    @Test fun theAutostartFolderIsTheDesktopsOwn() {
        assertEquals("/home/me/.config/autostart", StartupRules.autostartDir(null, "/home/me"))
        assertEquals("/home/me/.config/autostart", StartupRules.autostartDir("  ", "/home/me"))
        assertEquals("/data/cfg/autostart", StartupRules.autostartDir("/data/cfg/", "/home/me"))
    }

    @Test fun theLauncherIsNotTheJavaInside() {
        assertTrue(StartupRules.launcher("/opt/radon-player/bin/radon-player"))
        assertFalse(StartupRules.launcher("/opt/radon-player/lib/runtime/bin/java"))
        assertFalse(StartupRules.launcher(""))
    }

    @Test fun theAutostartFileStartsTheAppInTheTray() {
        val text = StartupRules.desktopEntry("/opt/radon-player/bin/radon-player")
        assertTrue(text.startsWith("[Desktop Entry]\n"))
        assertTrue(text.contains("\nExec=\"/opt/radon-player/bin/radon-player\" --tray\n"))
        assertTrue(text.contains("\nType=Application\n") && text.contains("\nName=Radon Player\n"))
    }

    @Test fun anOddPathIsQuotedWhole() {
        assertTrue(StartupRules.desktopEntry("/opt/my \"apps\"/\$radon/bin/radon-player")
            .contains("Exec=\"/opt/my \\\"apps\\\"/\\\$radon/bin/radon-player\" --tray"))
    }

    @Test fun eachSystemTakesItsOwnInstaller() {
        val files = listOf("RadonPlayer-Next.apk", "Radon.Player-3.3.0.msi", "radon-player_3.3.0_amd64.deb")
        assertEquals("Radon.Player-3.3.0.msi", StartupRules.installer(files, "Windows 11"))
        assertEquals("radon-player_3.3.0_amd64.deb", StartupRules.installer(files, "Linux"))
        assertNull("no Linux package in the release", StartupRules.installer(listOf("a.apk", "b.msi"), "Linux"))
        assertNull(StartupRules.installer(files, "Mac OS X"))
    }
}
