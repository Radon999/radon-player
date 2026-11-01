package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading a song's mood from its words (made-up lines, not real lyrics), and the MOOD_LYRICS line it's kept as. */
class LyricsMoodTest {
    private val sad = "I cried all night, the tears won't stop\nYou left me alone and broken\nGoodbye my love, I miss you\n" +
        "[Chorus]\nAlone, alone, so lonely\nAlone, alone, so lonely\nAlone, alone, so lonely"

    @Test fun sadWords() {
        val r = LyricsMood.read(sad)
        assertEquals("Sad", r.mood)
        assertTrue(r.valence < 0)
        assertTrue(r.confidence > 0.5)
        assertTrue("heartbreak" in r.themes)
    }

    @Test fun partyWords() {
        val r = LyricsMood.read("Tonight we dance, the club is lit\nDJ turn it up, shots on the floor\nParty all weekend, dancing till the morning")
        assertEquals("Party", r.mood)
        assertTrue(r.energy > 0.6)
    }

    @Test fun romanticWords() =
        assertEquals("Romantic", LyricsMood.read("Kiss me darling, hold me close\nI love you, my lover, forever yours\nYour lips, your eyes, my sweetheart").mood)

    @Test fun negationTurnsWordsAround() {
        assertNotEquals("Happy", LyricsMood.read("I'm not happy, I'm not happy\nI am never smiling now, no sunshine").mood)
        assertEquals("Party", LyricsMood.read("Can't stop dancing, can't stop the party tonight\nDance dance dance").mood)
    }

    @Test fun aChorusCountsOnceIsh() {
        // One happy line sung ten times doesn't outweigh three different sad ones.
        val text = "I cried alone, broken and lonely\nTears and goodbye\nI miss you, the pain\n" + "smile smile happy\n".repeat(10)
        assertEquals("Sad", LyricsMood.read(text).mood)
    }

    @Test fun instrumentalAndUnclear() {
        assertTrue(LyricsMood.read("").instrumental)
        assertTrue(LyricsMood.read("[Instrumental]").instrumental)
        assertTrue(LyricsMood.read(null).instrumental)
        assertEquals("", LyricsMood.read("the cat sat on the mat\nand the table was brown").mood)
    }

    @Test fun tagRoundTrip() {
        val r = LyricsMood.read(sad)
        assertEquals(r, LyricsReading.parse(r.tag()))
        val p = LyricsReading.parse("Sad;0.82;valence=-0.6;energy=0.3;themes=heartbreak,night;by=words")!!
        assertEquals("Sad", p.mood); assertEquals(0.82, p.confidence, 1e-9); assertEquals(listOf("heartbreak", "night"), p.themes)
        assertTrue(LyricsReading.parse("Instrumental;1.00;by=words")!!.instrumental)
        assertEquals("", LyricsReading.parse("None;0.10;by=words")!!.mood)
        assertNull(LyricsReading.parse("blah"))
        assertEquals("Instrumental;1.00;by=ai", LyricsReading(instrumental = true, by = "ai").tag())
    }

    @Test fun wordsCountInTheMood() {
        val r = LyricsMood.read(sad)
        assertEquals("Sad", MoodRules.decide(null, "", lyrics = r.points()).first().first)
        // Instrumental: nothing from the words, the rest decides alone.
        assertEquals(emptyMap<String, Double>(), LyricsReading(instrumental = true).points())
        assertEquals(MoodRules.decide(null, "House"), MoodRules.decide(null, "House", lyrics = emptyMap()))
    }
}
