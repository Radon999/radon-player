package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Learning from your fixes: the songs that sound like the fixed one, and the artist's others, get a small, capped nudge. */
class MoodLearnTest {
    private fun sound(bpm: Double, loud: Double, major: Double, bright: Double = 2200.0) =
        SoundFeatures(loud = loud, range = 8.0, bpm = bpm, beat = 0.4, bright = bright, onsets = 3.0, major = major)

    private val fixed = LearnSong("a", "zed", sound(120.0, -11.0, 0.6))
    private val twin = LearnSong("b", "other", sound(121.0, -11.2, 0.55))
    private val farAway = LearnSong("c", "other", sound(70.0, -24.0, -0.7, 1200.0))
    private val sameArtist = LearnSong("d", "zed", sound(70.0, -24.0, -0.7, 1200.0))
    private val unheard = LearnSong("e", "nobody", null)

    @Test fun aFixFromWhatChanged() {
        val f = MoodLearn.fix(fixed, listOf("Happy", "Party"), listOf("Sad", "Happy"))
        assertEquals(setOf("Party"), f.added)
        assertEquals(setOf("Sad"), f.removed)
    }

    @Test fun nearestAndSameArtistNudged() {
        val fix = MoodLearn.fix(fixed, listOf("Happy"), listOf("Sad"))
        val b = MoodLearn.biases(listOf(fixed, twin, farAway, sameArtist, unheard), listOf(fix))
        // The one that sounds just like it: nearly a full step.
        assertTrue((b["b"]?.get("Happy") ?: 0.0) > 0.05)
        assertTrue((b["b"]?.get("Sad") ?: 0.0) < -0.05)
        // Sounds nothing like it, another artist, not heard: nothing.
        assertEquals(null, b["c"]); assertEquals(null, b["e"])
        // The same artist's other song: a little.
        assertEquals(MoodLearn.ARTIST_STEP, b["d"]?.get("Happy") ?: 0.0, 1e-9)
        // The fixed song itself: none (yours stands as it is).
        assertEquals(null, b["a"])
    }

    @Test fun nudgesAreCapped() {
        val many = (1..10).map { i -> MoodLearn.fix(LearnSong("f$i", "zed", sound(120.0 + i * 0.1, -11.0, 0.6)), listOf("Happy"), emptyList()) }
        val b = MoodLearn.biases(listOf(twin, sameArtist) + many.map { it.song }, many)
        assertEquals(MoodLearn.CAP, b["b"]?.get("Happy") ?: 0.0, 1e-9)
        assertEquals(MoodLearn.CAP, b["d"]?.get("Happy") ?: 0.0, 1e-9)
        assertTrue(b.values.all { m -> m.values.all { it in -MoodLearn.CAP..MoodLearn.CAP } })
    }

    @Test fun nothingChangedNothingLearnt() {
        val same = MoodLearn.fix(fixed, listOf("Happy"), listOf("Happy"))
        assertTrue(MoodLearn.biases(listOf(fixed, twin), listOf(same)).isEmpty())
        assertTrue(MoodLearn.biases(listOf(fixed, twin), emptyList()).isEmpty())
    }

    @Test fun onlyTheNearestFew() {
        val crowd = (1..20).map { LearnSong("n$it", "x$it", sound(120.0 + it * 0.05, -11.0, 0.6)) }
        val b = MoodLearn.biases(crowd + fixed, listOf(MoodLearn.fix(fixed, listOf("Party"), emptyList())))
        assertEquals(MoodLearn.K, b.size)
    }
}
