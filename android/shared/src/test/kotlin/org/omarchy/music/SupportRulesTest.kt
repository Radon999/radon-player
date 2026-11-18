package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Copy everything for support, sizes as they are said, and the warning when the download folder is nearly full. */
class SupportRulesTest {
    @Test fun theReportSaysWhatTheAppIsAndWhatWentWrong() {
        val text = SupportRules.report(listOf("Version" to "4.0.14", "Device" to "phone", "Paired" to "", "Look" to "Now\nplaying"), "18 Nov · 4.0.14 · something")
        assertEquals("Radon Player: details for support\nVersion: 4.0.14\nDevice: phone\nLook: Now playing\n\nProblems\n18 Nov · 4.0.14 · something\n", text)
        assertTrue(SupportRules.report(emptyList(), "  ").endsWith("Problems\nNone noted.\n"))
    }

    @Test fun sizesAsTheyAreSaid() {
        val two = "18 Nov 2026, 08:03:11 · 4.0.14 · Moods couldn't be written\njava.lang.Exception: x\n\tat a.b(c.kt:1)\n\n9 Nov 2026, 21:40:02 · 4.0.13 · The app closed (main)\njava.lang.Error\n"
        assertEquals(2, SupportRules.count(two)); assertEquals(0, SupportRules.count(""))
        assertTrue(SupportRules.holds(two).startsWith("It holds 2 problems noted")); assertEquals("No problems are noted in it now", SupportRules.holds(" "))
        assertEquals("1 KB", SupportRules.size(1))
        assertEquals("512 MB", SupportRules.size(512_000_000))
        assertEquals("3.4 GB", SupportRules.size(3_400_000_000))
        assertEquals("", SupportRules.size(-1))
    }

    @Test fun nearlyFullIsUnderAGigabyteOrThreePercent() {
        assertTrue(SupportRules.lowSpace(800_000_000, 64_000_000_000))
        assertTrue("2% of a big disk", SupportRules.lowSpace(10_000_000_000, 500_000_000_000))
        assertFalse(SupportRules.lowSpace(20_000_000_000, 64_000_000_000))
        assertFalse("not known: no warning", SupportRules.lowSpace(-1, 0))
        assertTrue(SupportRules.lowSpaceText(800_000_000).startsWith("Only 800 MB free"))
    }
}
