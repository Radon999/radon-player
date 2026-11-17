package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A Genius search shows releases; a song shows by itself only when it was released by itself. */
class GeniusSearchRulesTest {
    private fun h(title: String, artist: String = "HOAX") = GeniusRules.Hit(title, artist)
    private fun r(id: String, title: String) = GeniusRules.Release(id, title, "HOAX")

    @Test fun aReleaseThatIsOnlyTheSongIsASingle() {
        assertTrue(GeniusRules.isSingleOf("Grow", "Grow - Single"))
        assertTrue(GeniusRules.isSingleOf("Grow", "Grow"))
        assertTrue(GeniusRules.isSingleOf("Grow (feat. Someone)", "grow"))
        assertTrue(GeniusRules.isSingleOf("Grow", "Something Else (Single)"))
        assertFalse(GeniusRules.isSingleOf("Grow", "b?"))
        assertFalse(GeniusRules.isSingleOf("into the blackhole", "b? Sides"))
    }

    @Test fun aSongOnAReleaseShowsAsThatRelease() {
        val b = r("1", "b?"); val sides = r("2", "b? Sides"); val single = r("3", "Grow - Single"); val named = r("9", "Hoax")
        val (alone, releases) = GeniusRules.fold(listOf(
            h("more than you know") to b, h("into the blackhole") to sides, h("Grow") to single,
            h("Moon Moon Baby") to null, h("Beach House") to b), listOf(named, b))
        assertEquals("only the ones released by themselves", listOf("Grow", "Moon Moon Baby"), alone.map { it.title })
        assertEquals("the releases found by name first, each once", listOf("9", "1", "2"), releases.map { it.id })
    }

    @Test fun theReleasesOnlyGeniusListsAreAddedToTheDiscography() {
        val genius = listOf("b?", "words that end with wh(y)", "The Truth and Other Lies - EP", "b? Sides", "The Truth and Other Lies (Deluxe Edition)")
        val have = listOf("b?", "words that end with wh(y)", "funny business", "b? Sides")
        assertEquals(listOf("The Truth and Other Lies - EP"), GeniusRules.unlisted(genius, have) { it })
        assertTrue(GeniusRules.unlisted(emptyList<String>(), have) { it }.isEmpty())
        assertTrue("the same artist: they share a release", GeniusRules.sharesARelease(genius, have))
        assertFalse("someone else of that name", GeniusRules.sharesARelease(listOf("Trap Mental Rampage", "Terror City"), have))
        assertFalse(GeniusRules.sharesARelease(emptyList(), have))
    }

    @Test fun nothingFoundIsNothing() {
        val (alone, releases) = GeniusRules.fold(emptyList(), emptyList())
        assertTrue(alone.isEmpty() && releases.isEmpty())
    }
}
