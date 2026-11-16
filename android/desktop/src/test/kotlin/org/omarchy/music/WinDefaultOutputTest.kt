package org.omarchy.music

import org.junit.Assert.assertTrue
import org.junit.Test

/** Asking Windows for its default output: it answers with a device's key or with nothing (no sound device, or not
 *  Windows), and never brings the app down. On Windows this really talks to Windows' sound devices. */
class WinDefaultOutputTest {
    @Test fun windowsIsAskedAndAnswersWithAKeyOrNothing() {
        repeat(3) {
            val g = WinDefaultOutput.guid()
            println("Windows' default output: ${g ?: "none (or not Windows)"}")
            assertTrue("$g", g == null || (g.startsWith("{") && g.endsWith("}") && g == g.lowercase()))
        }
    }
}
