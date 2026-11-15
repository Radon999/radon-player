package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** What the DJ tells you between songs takes turns by subject: skips don't come up again and again. */
class FactTurnsTest {
    /** The owner's case: many things to say about skips, one each about a few other subjects. */
    private val keys = listOf("most-skipped", "never-skipped", "skips-all", "skips-week", "skipped-week:a|b", "heard-out",
        "top-genre-month", "peak-hour", "library-size", "top-artist-month", "playlists", "favorites-count", "year-top:2024", "mood-week")

    @Test fun eachFactHasItsSubject() {
        assertEquals("skips", FactTopics.of("skipped-week:some song|some artist"))
        assertEquals("skips", FactTopics.of("heard-out"))
        assertEquals("every mood fact is about moods", "moods", FactTopics.of("mood-any:Chill"))
        assertEquals("artist", FactTopics.of("artist-rank:Cavetown"))
        assertEquals("collection", FactTopics.of("library-hours"))
        assertEquals("a kind not listed is its own subject", "something-new", FactTopics.of("something-new:x"))
    }

    @Test fun aSubjectIsNotToldAgainUntilTheOthersHaveHadATurn() {
        val rng = Random(7)
        var history = emptyList<String>()
        val topics = ArrayList<String>()
        repeat(60) {
            val k = NoRepeat.pickTurn(keys, history, FactTopics::of, max = 15, rng = rng)!!
            history = NoRepeat.remember(history, k)
            topics += FactTopics.of(k)
        }
        val subjects = keys.map(FactTopics::of).distinct().size   // 9 here
        // Between two tellings about skips, every other subject has had its turn (8 others here)
        val skipAt = topics.withIndex().filter { it.value == "skips" }.map { it.index }
        assertTrue("skips do come up", skipAt.size >= 4)
        for ((a, b) in skipAt.zipWithNext()) assertTrue("skips at $a and again at $b: too soon", b - a >= subjects)
        assertTrue("about one telling in $subjects is about skips, not six in fourteen", skipAt.size <= 60 / subjects + 1)
        for (i in 1 until topics.size) assertFalse("the same subject twice running at $i", topics[i] == topics[i - 1])
    }

    @Test fun withinASubjectTheSameThingIsNotSaidAgainSoon() {
        val rng = Random(3)
        var history = emptyList<String>()
        val skipsSaid = ArrayList<String>()
        repeat(54) {
            val k = NoRepeat.pickTurn(keys, history, FactTopics::of, max = 15, rng = rng)!!
            history = NoRepeat.remember(history, k)
            if (FactTopics.of(k) == "skips") skipsSaid += k
        }
        assertEquals("six tellings about skips: each of the six things once", skipsSaid.take(6).toSet().size, minOf(6, skipsSaid.size))
    }

    @Test fun oneSubjectOrNoneStillWorks() {
        assertNull(NoRepeat.pickTurn(emptyList(), emptyList(), FactTopics::of, max = 15))
        assertEquals("only one thing to say: it is said", "heard-out", NoRepeat.pickTurn(listOf("heard-out"), listOf("heard-out"), FactTopics::of, max = 15))
    }
}
