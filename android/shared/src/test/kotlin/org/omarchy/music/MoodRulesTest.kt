package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every mood that fits a song, the sound and the words weighed against each other, and the nudge from your fixes. */
class MoodRulesTest {
    private val upbeat = SoundFeatures(loud = -11.0, range = 8.0, bpm = 124.0, beat = 0.45, bright = 2700.0, onsets = 3.6, major = 0.6)
    private val slowMinor = SoundFeatures(loud = -21.0, range = 12.0, bpm = 70.0, beat = 0.15, bright = 1300.0, onsets = 1.4, major = -0.6)
    private val pop = SoundFeatures(loud = -12.0, range = 8.0, bpm = 105.0, beat = 0.4, bright = 2200.0, onsets = 3.0, major = 0.3)

    @Test fun everyMoodThatFits() {
        val j = MoodRules.judge(pop, "Pop", mapOf("dance" to 1.0, "summer" to 0.5, "love" to 0.4))
        assertTrue(j.moods.size in 2..MoodRules.MOST)
        assertEquals(j.moods.sortedByDescending { it.second }, j.moods)
        val top = j.moods.first().second
        assertTrue(j.moods.drop(1).all { it.second >= MoodRules.FITS && it.second >= MoodRules.NEAR_TOP * top })
        // Every mood has a score, the ones that fit and the ones that don't.
        assertTrue(j.scores.keys.containsAll(j.moods.map { it.first }))
        assertTrue(j.scores.values.all { it in 0.0..1.0 })
        assertEquals(j.moods, MoodRules.decide(pop, "Pop", mapOf("dance" to 1.0, "summer" to 0.5, "love" to 0.4)))
    }

    @Test fun oneClearMoodIsNotSpreadThin() {
        // Only a genre: one mood, not a handful of weak ones.
        assertEquals(listOf("Intense"), MoodRules.decide(null, "Death Metal").map { it.first }.take(1))
        assertTrue(MoodRules.decide(null, "Pop").size <= 2)
    }

    @Test fun anUpbeatSoundIsntMadeSadByAHint() {
        val hint = mapOf("Sad" to 0.4, "Happy" to 0.3)
        assertFalse("Sad" in MoodRules.decide(upbeat, "", lyrics = hint).map { it.first })
        // Plainly sad words still count: both moods.
        val plain = MoodRules.decide(upbeat, "", lyrics = mapOf("Sad" to 0.95)).map { it.first }
        assertTrue("Sad" in plain)
    }

    @Test fun theSoundAndTheWordsBothAlwaysCount() {
        // A hint of sad words in an upbeat song (Last.fm calls it sad too) counts for less, but not for nothing.
        val without = MoodRules.judge(upbeat, "", mapOf("sad" to 0.6), lyrics = mapOf("Happy" to 0.3)).scores["Sad"] ?: 0.0
        val with = MoodRules.judge(upbeat, "", mapOf("sad" to 0.6), lyrics = mapOf("Happy" to 0.3, "Sad" to 0.4)).scores["Sad"] ?: 0.0
        assertTrue(with > without)
        // Every mood weighs both: the words more for Sad, Romantic and Dark, the sound more for Energetic, Party, Chill
        // and Focus, and neither is ever 0.
        for (m in listOf("Sad", "Romantic", "Dark")) assertTrue(MoodRules.wordsWeight(m) > MoodRules.wordsWeight("Energetic") && MoodRules.soundWeight(m) < 1.0)
        for (m in listOf("Energetic", "Party", "Chill", "Focus")) assertTrue(MoodRules.soundWeight(m) > 1.0 && MoodRules.wordsWeight(m) < MoodRules.wordsWeight("Sad"))
        for (m in MoodRules.ALL) assertTrue(MoodRules.wordsWeight(m) > 0.3 && MoodRules.soundWeight(m) > 0.3)
    }

    @Test fun aSlowMinorSoundIsntMadeHappyByAHint() {
        assertFalse("Happy" in MoodRules.decide(slowMinor, "", lyrics = mapOf("Happy" to 0.4, "Sad" to 0.5)).map { it.first })
        assertEquals("Sad", MoodRules.decide(slowMinor, "", lyrics = mapOf("Happy" to 0.4, "Sad" to 0.5)).first().first)
    }

    @Test fun agreeingIsSurerThanDisagreeing() {
        val sadWords = LyricsReading.fromScores(mapOf("Sad" to 0.9, "Calm" to 0.5), -0.7, 0.25, emptyList(), "words", 0.8)
        val gladWords = LyricsReading.fromScores(mapOf("Happy" to 0.9, "Party" to 0.6), 0.8, 0.75, emptyList(), "words", 0.8)
        val agree = MoodRules.judge(slowMinor, "", words = sadWords)
        val clash = MoodRules.judge(slowMinor, "", words = gladWords)
        assertTrue(agree.confidence > clash.confidence)
        assertTrue(clash.conflict >= 0.5)
        assertTrue(agree.conflict < 0.2)
    }

    @Test fun oppositesOnlyWhenBothArePlain() {
        // Sad tags on a pop song: Sad, and not Happy from the genre too.
        val got = MoodRules.decide(null, "Pop", mapOf("sad" to 1.0, "melancholic" to 0.8)).map { it.first }
        assertEquals("Sad", got.first())
        assertFalse("Happy" in got)
    }

    @Test fun aNudgeTipsButNeverDecides() {
        val base = MoodRules.judge(pop, "Pop", mapOf("sad" to 0.4))
        val sadBefore = base.scores["Sad"] ?: 0.0
        assertTrue(sadBefore > 0)
        val nudged = MoodRules.judge(pop, "Pop", mapOf("sad" to 0.4), bias = mapOf("Sad" to MoodLearn.CAP, "Party" to -MoodLearn.CAP))
        assertEquals(sadBefore + MoodLearn.CAP, nudged.scores["Sad"] ?: 0.0, 0.011)
        // A mood the song says nothing of isn't nudged in.
        assertEquals(MoodRules.judge(pop, "Pop").scores["Dark"], MoodRules.judge(pop, "Pop", bias = mapOf("Dark" to MoodLearn.CAP)).scores["Dark"])
        // No clues of its own: a nudge alone gives no mood.
        assertTrue(MoodRules.judge(null, "", bias = mapOf("Sad" to MoodLearn.CAP)).moods.isEmpty())
        // A mood the song plainly is stays (a nudge down counts for little there).
        val metal = MoodRules.judge(null, "Death Metal", mapOf("aggressive" to 1.0), bias = mapOf("Intense" to -MoodLearn.CAP))
        assertEquals("Intense", metal.main)
        // Near the line: a nudge up brings a mood in.
        val near = MoodRules.judge(null, "", mapOf("chill" to 0.19, "happy" to 1.0))
        val chill = near.scores["Chill"] ?: 0.0
        assertTrue(chill < MoodRules.FITS && chill + MoodLearn.CAP >= MoodRules.FITS)
        assertTrue("Chill" in MoodRules.judge(null, "", mapOf("chill" to 0.19, "happy" to 1.0), bias = mapOf("Chill" to MoodLearn.CAP)).moods.map { it.first })
    }
}
