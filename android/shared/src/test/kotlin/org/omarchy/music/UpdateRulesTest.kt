package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Looking for a newer release: the version from where GitHub's "latest release" page sends on to, and its files. */
class UpdateRulesTest {
    @Test fun theVersionIsReadFromWhereTheLatestPageSendsYou() {
        assertEquals("v4.0.4", UpdateRules.tagFromLocation("https://github.com/Radon999/radon-player/releases/tag/v4.0.4"))
        assertEquals("v4.0.4", UpdateRules.tagFromLocation("https://github.com/Radon999/radon-player/releases/tag/v4.0.4/?x=1"))
        assertNull("no release yet: it sends on to the list", UpdateRules.tagFromLocation("https://github.com/Radon999/radon-player/releases"))
        assertNull(UpdateRules.tagFromLocation(null))
        assertNull(UpdateRules.tagFromLocation(""))
    }

    @Test fun aReleasesFilesAreWhereEveryReleasesAre() {
        assertEquals("https://github.com/Radon999/radon-player/releases/download/v4.0.4/RadonPlayer-Next.apk",
            UpdateRules.assetUrl("Radon999/radon-player", "v4.0.4", "RadonPlayer-Next.apk"))
        assertEquals("Radon-Player-4.0.4.msi", UpdateRules.pcInstallerName("v4.0.4", "Windows 11"))
        assertEquals("radon-player_4.0.4_amd64.deb", UpdateRules.pcInstallerName("v4.0.4", "Linux"))
        assertNull(UpdateRules.pcInstallerName("v4.0.4", "Mac OS X"))
    }

    @Test fun newerIsByTheNumbers() {
        assertTrue(UpdateRules.newer("v4.0.4", "4.0.3"))
        assertTrue(UpdateRules.newer("4.1.0", "4.0.12"))
        assertFalse(UpdateRules.newer("4.0.3", "4.0.3"))
        assertFalse(UpdateRules.newer("4.0.2", "4.0.3"))
        assertFalse("a build with no number is never called old", UpdateRules.newer("4.0.4", "next"))
    }
}
