package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRulesTest {
    private val now = 1_000_000_000L
    private val day = 86_400L
    private val a = SongFacts(title = "Song A", artist = "Daft Punk, Pharrell", album = "RAM", genre = "Indie Rock; Pop",
        moods = listOf("Chill", "Happy"), release = "album", format = "FLAC 24", downloaded = true, favorite = true, plays = 12,
        lastPlayed = now - 40 * day, added = (now - 5 * day).toDouble(), skips = 4, heardOut = 1, length = 400.0, folder = "Music/Rock/RAM/")
    private val b = SongFacts(title = "B side", artist = "Trap Lord", album = "Thing - EP", genre = "Trap", moods = listOf("Hype"),
        release = "ep", format = "MP3", myFile = true, added = (now - 100 * day).toDouble(), length = 150.0, folder = "Music/Rap/")

    private fun fits(r: SmartRule, f: SongFacts) = SmartRules.fits(r, f, now)

    @Test fun genre() {
        assertTrue(fits(SmartRule("genre", "contains", "rock"), a))
        assertFalse(fits(SmartRule("genre", "contains", "rap"), b))   // whole words: rap isn't in Trap
        assertTrue(fits(SmartRule("genre", "is", "pop"), a))
        assertFalse(fits(SmartRule("genre", "is", "rock"), a))
        assertTrue(fits(SmartRule("genre", "not", "rock"), b))
        assertFalse(fits(SmartRule("genre", "not", "rock"), a))
    }

    @Test fun moodsAndText() {
        assertTrue(fits(SmartRule("mood", "is", values = listOf("hype", "Energetic")), b))
        assertFalse(fits(SmartRule("mood", "is", "Sad"), a))
        assertTrue(fits(SmartRule("mood", "not", "Sad"), a))
        assertTrue(fits(SmartRule("artist", "contains", "daft"), a))
        assertTrue(fits(SmartRule("artist", "is", "Pharrell"), a))
        assertTrue(fits(SmartRule("title", "not", "song"), b))
    }

    @Test fun releaseFormatSource() {
        assertTrue(fits(SmartRule("release", "is", "ep"), b))
        assertFalse(fits(SmartRule("release", "is", "ep"), a))
        assertTrue(fits(SmartRule("format", "is", "FLAC"), a))
        assertTrue(fits(SmartRule("format", "is", "FLAC 24"), a))
        assertFalse(fits(SmartRule("format", "is", "FLAC 16"), a))
        assertFalse(fits(SmartRule("format", "is", "FLAC"), SongFacts()))   // not read yet
        assertTrue(fits(SmartRule("format", "not", "FLAC"), b))
        assertTrue(fits(SmartRule("source", "downloaded"), a))
        assertFalse(fits(SmartRule("source", "downloaded"), b))
        assertTrue(fits(SmartRule("source", "files"), b))
        assertTrue(fits(SmartRule("favorite", "yes"), a))
        assertTrue(fits(SmartRule("favorite", "no"), b))
    }

    @Test fun playsAndTimes() {
        assertTrue(fits(SmartRule("plays", "more", n = 10.0), a))
        assertTrue(fits(SmartRule("plays", "less", n = 1.0), b))
        assertTrue(fits(SmartRule("lastplayed", "not", n = 30.0), a))
        assertFalse(fits(SmartRule("lastplayed", "within", n = 30.0), a))
        assertTrue(fits(SmartRule("lastplayed", "within", n = 60.0), a))
        assertTrue(fits(SmartRule("lastplayed", "not", n = 30.0), b))   // never played: not within either
        assertTrue(fits(SmartRule("never", "yes"), b))
        assertFalse(fits(SmartRule("never", "yes"), a))
        assertTrue(fits(SmartRule("added", "within", n = 30.0), a))
        assertFalse(fits(SmartRule("added", "within", n = 30.0), b))
        assertTrue(fits(SmartRule("added", "not", n = 30.0), b))
    }

    @Test fun skipsLengthFolder() {
        assertTrue(fits(SmartRule("skips", "lot"), a))
        assertFalse(fits(SmartRule("skips", "lot"), b))
        assertFalse(fits(SmartRule("skips", "never"), b))   // never heard out either
        assertTrue(fits(SmartRule("skips", "never"), SongFacts(heardOut = 2)))
        assertTrue(fits(SmartRule("length", "longer", n = 5.0), a))
        assertTrue(fits(SmartRule("length", "shorter", n = 5.0), b))
        assertFalse(fits(SmartRule("length", "shorter", n = 5.0), SongFacts()))   // no length known
        assertTrue(fits(SmartRule("folder", "under", "Music/Rock"), a))
        assertFalse(fits(SmartRule("folder", "is", "Music/Rock"), a))
        assertTrue(fits(SmartRule("folder", "is", "music\\rock\\ram"), a))
        assertTrue(fits(SmartRule("from-a-newer-version", "x"), a))
    }

    @Test fun allOrAny() {
        val rules = listOf(SmartRule("favorite", "yes"), SmartRule("never", "yes"))
        assertFalse(SmartRules.fits(rules, false, a, now))
        assertTrue(SmartRules.fits(rules, true, a, now))
        assertTrue(SmartRules.fits(rules, true, b, now))
        assertTrue(SmartRules.fits(emptyList(), false, b, now))
    }

    @Test fun orderAndLimit() {
        val m = mapOf("a" to a, "b" to b, "c" to SongFacts(title = "C", plays = 3, added = 5.0))
        val ids = listOf("a", "b", "c")
        fun pick(order: String, limit: Int = 0) = SmartRules.pick(ids, { m.getValue(it) }, emptyList(), false, order, limit, now)
        assertEquals(listOf("a", "c", "b"), pick("plays"))
        assertEquals(listOf("a", "c"), pick("plays", 2))
        assertEquals(listOf("b", "c", "a"), pick("title"))
        assertEquals(listOf("a", "b", "c"), pick("added"))
        val n = (1..50).toList()
        val r1 = SmartRules.pick(n, { SongFacts(title = "$it") }, emptyList(), false, "random", 10, now, seed = 7)
        assertEquals(10, r1.size)
        assertEquals(r1, SmartRules.pick(n, { SongFacts(title = "$it") }, emptyList(), false, "random", 10, now, seed = 7))
    }

    @Test fun wordsAndIdeas() {
        assertEquals("Last played not within the last 30 days", SmartRules.words(SmartRule("lastplayed", "not", n = 30.0)))
        assertEquals("Never played", SmartRules.words(SmartRule("never", "yes")))
        assertEquals("Release type is EP or Single", SmartRules.words(SmartRule("release", "is", values = listOf("ep", "single"))))
        for (i in SmartRules.IDEAS) for (r in i.rules) {
            assertTrue("${i.name}: $r", r.field in SmartRules.FIELDS && SmartRules.ops(r.field).any { it.first == r.op })
        }
        for (k in SmartRules.FIELDS.keys) assertTrue(k, SmartRules.ops(k).isNotEmpty())
    }
}
