package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Update installs the newest release out as you tap it, not the one an older look found (4.0.37 went in after 4.0.38
 *  was out, and the app then asked to update again). */
class UpdateToInstallTest {
    private val v37 = "4.0.37" to "https://github.com/x/releases/download/v4.0.37/RadonPlayer-Next.apk"
    private val v38 = "4.0.38" to "https://github.com/x/releases/download/v4.0.38/RadonPlayer-Next.apk"
    private val v450 = "4.5.0" to "https://github.com/x/releases/download/v4.5.0/RadonPlayer-Next.apk"

    @Test fun aNewerReleaseSinceTheLastLookIsTheOneInstalled() {
        assertEquals(v38, UpdateRules.toInstall(v37, v38))
        assertEquals(v450, UpdateRules.toInstall(v37, v450))   // 4.5.0 is newer than 4.0.38 and 4.0.37
    }

    @Test fun theSameOrNoAnswerKeepsWhatWasFound() {
        assertEquals(v38, UpdateRules.toInstall(v38, v38))
        assertEquals(v38, UpdateRules.toInstall(v38, null))      // GitHub didn't answer just now
        assertEquals(v38, UpdateRules.toInstall(v38, v37))       // never an older one
        assertEquals(v38, UpdateRules.toInstall(null, v38))
        assertNull(UpdateRules.toInstall(null, null))
    }
}
