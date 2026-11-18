package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Back up by itself: when one is due, and which old ones go. */
class BackupScheduleRulesTest {
    @Test fun whenOneIsDue() {
        val day = 86_400L
        assertTrue("never yet", BackupScheduleRules.due("daily", 0, 1_800_000_000))
        assertFalse(BackupScheduleRules.due("daily", 1000, 1000 + day - 1))
        assertTrue(BackupScheduleRules.due("daily", 1000, 1000 + day))
        assertFalse(BackupScheduleRules.due("weekly", 1000, 1000 + 6 * day))
        assertTrue(BackupScheduleRules.due("weekly", 1000, 1000 + 7 * day))
        assertFalse(BackupScheduleRules.due("off", 0, 1_800_000_000))
    }

    @Test fun theNewestFiveAreKept() {
        val names = (10..17).map { BackupScheduleRules.name("2026-11-$it") } + "notes.txt"
        assertEquals(listOf("Radon Player backup 2026-11-12.zip", "Radon Player backup 2026-11-11.zip", "Radon Player backup 2026-11-10.zip"), BackupScheduleRules.old(names))
        assertTrue(BackupScheduleRules.old(names.take(3)).isEmpty())
    }
}
