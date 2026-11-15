package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Upgrade and Downloads' Upgrade and Refresh: what they say at the end, and the asks that end before anything is
 *  fetched. Nothing is downloaded here. */
class UpgradesTest {
    @Test fun whatUpgradeSaysAtItsEnd() {
        assertEquals("Upgraded 1 song to FLAC", Upgrades.doneText(1, 0, 0, 0))
        assertEquals("Upgraded 12 songs to FLAC · 3 not on Tidal · 2 as good already · 1 couldn't", Upgrades.doneText(12, 3, 2, 1))
        assertEquals("Upgraded 0 songs to FLAC · 4 as good already", Upgrades.doneText(0, 0, 4, 0))
    }

    @Test fun whatDownloadsUpgradeAndRefreshSay() {
        assertEquals("5 upgraded (FLAC from Tidal, else AAC)", Upgrades.flacText(5, 0))
        assertEquals("2 upgraded (FLAC from Tidal, else AAC) · 3 left as they are", Upgrades.flacText(2, 3))
        assertEquals("Downloads are up to date", Upgrades.refreshedText(0))
        assertEquals("Refreshed: 1 more download", Upgrades.refreshedText(1))
        assertEquals("Refreshed: 4 more downloads", Upgrades.refreshedText(4))
    }

    @Test fun upgradeWithoutTidalOrWithNothingToUpgradeStartsNothing() {
        Harness.ctx
        Harness.ui {
            Upgrades.songs(listOf(Track("up-stream", "ytm", "A Stream", "Upgrade Test Band")))   // not a file of yours (or Tidal isn't connected)
            assertNull("no run began", Music.upgrading)
            Upgrades.songs(emptyList())
            assertNull(Music.upgrading)
        }
    }
}
