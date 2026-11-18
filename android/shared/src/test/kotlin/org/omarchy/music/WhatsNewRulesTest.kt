package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The What's new card after an update: once a version, with that version's headlines. */
class WhatsNewRulesTest {
    private val log = """
# Changelog

## 4.0.14 · November 2026
- **Undo for the queue:** cleared the queue by accident? An Undo button puts it back.
- **Follows the cover, a preset that changes with every song:** each cover picks a scene.
- A line with no headline.

## 4.0.13 · November 2026
- **Moods into files that kept refusing them (phone):** fixed.
""".trimIndent()

    @Test fun onceAfterAnUpdateNeverOnAFreshInstall() {
        assertTrue(WhatsNewRules.show("4.0.13", "4.0.14"))
        assertFalse("seen already", WhatsNewRules.show("4.0.14", "4.0.14"))
        assertFalse("a fresh install", WhatsNewRules.show("", "4.0.14"))
    }

    @Test fun thisVersionsHeadlines() {
        assertEquals(listOf("Undo for the queue", "Follows the cover, a preset that changes with every song"), WhatsNewRules.headlines(log, "4.0.14"))
        assertEquals(listOf("Moods into files that kept refusing them (phone)"), WhatsNewRules.headlines(log, "4.0.13"))
        assertTrue("a version the changelog doesn't have", WhatsNewRules.headlines(log, "4.0.2").isEmpty())
        assertEquals(listOf("Undo for the queue"), WhatsNewRules.headlines(log, "4.0.14", most = 1))
        assertEquals(1, WhatsNewRules.more(log, "4.0.14", 1))
    }
}
