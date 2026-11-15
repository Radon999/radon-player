package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Refresh in a tab's header: what it reads again, tab by tab. */
class RefreshRulesTest {
    @Test fun theStatisticsAddUpYourListeningAgain() {
        for (view in listOf("top", "skips", "favorites", "unfavorited", "repeat", "recent", "dj"))
            assertEquals(view, RefreshRules.STATS, RefreshRules.kind("music", view, inside = false))
    }

    @Test fun theTabsOfYourMusicReadYourFilesAgain() {
        for (tab in listOf("albums", "eps", "singles", "artists", "genres", "moods", "folders", "unreleased", "flac24", "flac32"))
            assertEquals(tab, RefreshRules.FILES, RefreshRules.kind(tab, "", inside = false))
        assertEquals("My music", RefreshRules.FILES, RefreshRules.kind("music", "all", inside = false))
        assertEquals(RefreshRules.FILES, RefreshRules.kind("library", "", inside = false, local = true))
        assertNull("a service's library is the service's to refresh", RefreshRules.kind("library", "", inside = false, local = false))
    }

    @Test fun theOtherListsEachHaveTheirOwn() {
        assertEquals(RefreshRules.DOWNLOADS, RefreshRules.kind("downloads", "", inside = false))
        assertEquals(RefreshRules.SMART, RefreshRules.kind("music", "smartlists", inside = false))
        assertEquals(RefreshRules.MOODLISTS, RefreshRules.kind("music", "moodlists", inside = false))
        assertEquals(RefreshRules.DUPES, RefreshRules.kind("music", "dupes", inside = false))
        for (view in listOf("playlists", "pinned", "discovery")) assertEquals(view, RefreshRules.LISTS, RefreshRules.kind("music", view, inside = false))
    }

    @Test fun whereThereIsNothingToReadAgainThereIsNoButton() {
        for (tab in listOf("player", "search", "queue", "lyrics", "visualizer")) assertNull(tab, RefreshRules.kind(tab, "", inside = false))
        assertNull("New releases has its own, which asks how far back first", RefreshRules.kind("music", "newreleases", inside = false))
        assertNull("further in (an album's page has its own)", RefreshRules.kind("albums", "", inside = true))
    }

    @Test fun eachKindSaysWhatItIsDoing() {
        for (k in listOf(RefreshRules.FILES, RefreshRules.STATS, RefreshRules.SMART, RefreshRules.MOODLISTS, RefreshRules.DUPES, RefreshRules.LISTS))
            assertTrue(k, RefreshRules.text(k).isNotEmpty())
    }
}
