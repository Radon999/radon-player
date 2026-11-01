package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/** Counting plays by mood, for the DJ's mood facts and the Mood mix. */
class MoodTallyTest {
    private val hour = 3600L
    private val plays = (0 until 100).map { MoodPlay(it * hour, if (it % 4 == 0) "Sad" else "Chill", "s${it % 3}") }

    @Test fun countsInASpan() {
        assertEquals(mapOf("Chill" to 37, "Sad" to 13), MoodTally.counts(plays, 0, 50 * hour))
        assertEquals("Chill" to 75, MoodTally.top(MoodTally.counts(plays, 0, Long.MAX_VALUE)))
        assertNull(MoodTally.top(mapOf("Chill" to 3), minPlays = 10))
        assertEquals(25, MoodTally.percent(mapOf("Sad" to 1, "Chill" to 3), "Sad"))
    }

    @Test fun shiftFindsAMoodThatGrew() {
        assertEquals(Triple("Sad", 20, 50), MoodTally.shift(mapOf("Chill" to 50, "Sad" to 50), mapOf("Chill" to 80, "Sad" to 20)))
        assertNull(MoodTally.shift(mapOf("Chill" to 80, "Sad" to 20), mapOf("Chill" to 80, "Sad" to 20)))
        assertNull(MoodTally.shift(mapOf("Sad" to 5), mapOf("Chill" to 5)))   // too few plays
    }

    @Test fun neglectedMood() {
        assertEquals("Romantic", MoodTally.neglected(mapOf("Romantic" to 100, "Chill" to 300), mapOf("Chill" to 60, "Romantic" to 1)))
        assertNull(MoodTally.neglected(mapOf("Romantic" to 100, "Chill" to 300), mapOf("Chill" to 45, "Romantic" to 15)))
    }

    @Test fun topSongAndLeanings() {
        assertEquals("s0" to 9, MoodTally.topSong(plays, "Sad"))
        val night = (0 until 200).map { MoodPlay(it * hour, if (it % 24 in 0..3) "Dreamy" else "Happy") }
        val lean = MoodTally.leanings(night, { ((it / hour) % 24).toInt() })
        assertEquals("late night", lean["Dreamy"]?.first)
        assertNull(lean["Happy"])
    }

    @Test fun moodMixRotatesWithoutBackToBack() {
        val moods = listOf("Chill", "Sad", "Happy", "Hype")
        var history = listOf<String>()
        val r = Random(3)
        repeat(20) {
            val m = MoodTally.moodOrder(moods, history, r).first()
            assertNotEquals(history.lastOrNull(), m)
            history = history + m
        }
        // Each four in a row are all four moods
        assertEquals(moods.toSet(), history.takeLast(4).toSet())
    }
}
