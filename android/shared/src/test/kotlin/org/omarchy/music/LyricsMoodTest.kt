package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ---- the line and phrase reader (version 2) ----

    /** An upbeat beach song about leaving your troubles behind (our own lines): relief, not sadness, though "blues" is in it. */
    private val beach = """
        Kicked off my boots and drove on down to the coast
        Left all my worries back at home in the city
        Sand between my toes and a cold one in my hand
        Let the tide roll in and wash my blues away
        [Chorus]
        Got no worries, just the sunshine and the waves
        Let the ocean wash my blues away
        Nothing on my mind but the sun on the shore
        Let the ocean wash my blues away
    """.trimIndent()

    /** A quiet, lonely song (our own lines): sad, and calm with it. */
    private val sadCalm = """
        Quiet rain against the window, I sit here alone
        The candle's burning low and the house is so still
        I miss the sound of your voice in the morning
        Softly I whisper goodbye to the empty room
        I miss the sound of your voice in the morning
        The tears fall slow and the night is long
    """.trimIndent()

    @Test fun washingTheBluesAwayIsNotSad() {
        val r = LyricsMood.read(beach)
        assertTrue("Sad: ${r.scores}", (r.scores["Sad"] ?: 0.0) < 0.2)
        assertTrue(r.mood == "Happy" || r.mood == "Chill")
        assertTrue((r.scores["Happy"] ?: 0.0) >= 0.5)
        assertTrue((r.scores["Chill"] ?: 0.0) >= 0.5)
        assertTrue(r.valence > 0.3)
        // Upbeat, country-ish sound with these words: not in Sad at all.
        val upbeat = SoundFeatures(loud = -11.0, range = 8.0, bpm = 118.0, beat = 0.4, bright = 2600.0, onsets = 3.5, major = 0.6)
        val got = MoodRules.judge(upbeat, "Country", words = r).moods.map { it.first }
        assertEquals("Happy", got.first())
        assertFalse("Sad" in got)
    }

    @Test fun bluesOnTheirOwnAreStillSad() {
        val r = LyricsMood.read("Got the blues tonight, can't shake this feeling\nI'm feeling blue and so alone\nCold and lonely in the rain")
        assertEquals("Sad", r.mood)
    }

    @Test fun sadButCalm() {
        val r = LyricsMood.read(sadCalm)
        assertEquals("Sad", r.mood)
        assertTrue((r.scores["Calm"] ?: 0.0) >= 0.4)
        assertTrue(r.energy < 0.4)
        assertTrue(r.valence < 0)
        // A slow, quiet, minor sound agrees: Sad and Calm both, and surer than the sound alone.
        val slow = SoundFeatures(loud = -20.0, range = 12.0, bpm = 72.0, beat = 0.15, bright = 1400.0, onsets = 1.5, major = -0.5)
        val j = MoodRules.judge(slow, "", words = r)
        assertEquals("Sad", j.main)
        assertTrue("Calm" in j.moods.map { it.first })
        assertTrue(j.confidence > MoodRules.judge(slow, "").confidence)
        assertTrue(j.conflict < 0.2)
    }

    @Test fun phrasesNotWords() {
        assertEquals("Happy", LyricsMood.read("I can't stop smiling since you came\nCan't stop smiling, I feel so good").mood)
        assertEquals("Sad", LyricsMood.read("You broke my heart and walked out on me\nNow I cry myself to sleep").mood)
        val relief = LyricsMood.read("No more tears, no more crying in the rain\nI dried my eyes and I feel alive again")
        assertNotEquals("Sad", relief.mood)
        assertTrue((relief.scores["Sad"] ?: 0.0) < (relief.scores["Happy"] ?: 0.0))
        // "Nothing to lose" is drive, not loss.
        assertNotEquals("Sad", LyricsMood.read("We got nothing to lose tonight\nNothing to lose, so let's ride").mood)
    }

    @Test fun negationStopsAtAComma() {
        // "not" turns "sad" around but doesn't reach past the comma to "happy".
        val r = LyricsMood.read("I'm not sad, happy and free\nNot lonely, smiling all day")
        assertEquals("Happy", r.mood)
        assertTrue((r.scores["Sad"] ?: 0.0) < 0.2)
    }

    @Test fun nowCountsMoreThanUsedTo() {
        val r = LyricsMood.read("I used to cry myself to sleep every night\nI used to feel so lonely and so blue\n" +
            "But now I smile, the sun is shining on my face\nNo more tears, I feel alive\nBut now I smile, the sun is shining on my face")
        assertEquals("Happy", r.mood)
    }

    @Test fun aChorusCountsMoreThanAPassingLine() {
        val r = LyricsMood.read("I miss you sometimes\n" + "Dance with me, dance all night, the party never ends\n".repeat(4))
        assertEquals("Party", r.mood)
        assertTrue((r.scores["Party"] ?: 0.0) > (r.scores["Sad"] ?: 0.0))
    }

    @Test fun aStoryIsReadLessSurely() {
        val mine = "I cried alone all night\nI miss you, my heart is broken\nI'm so lonely without you\nTears on my pillow, I can't let go\n" +
            "I say goodbye but I still miss you\nMy empty room, my lonely bed"
        val theirs = "She cried alone all night\nShe missed him, her heart was broken\nHe was so lonely without her\n" +
            "Tears on her pillow, she couldn't let go\nThey said goodbye but they still missed them\nHer empty room, his lonely bed"
        assertTrue(LyricsMood.read(theirs).confidence < LyricsMood.read(mine).confidence)
    }

    @Test fun everyMoodScoredAndKeptInTheTag() {
        val r = LyricsMood.read(beach)
        assertEquals(LyricsMood.VERSION, r.version)
        assertTrue(r.scores.size >= 2)
        assertTrue(r.scores.keys.all { it in MoodRules.ALL } && r.scores.values.all { it in 0.0..1.0 })
        assertTrue("v=${LyricsMood.VERSION}" in r.tag() && "scores=" in r.tag())
        assertEquals(r, LyricsReading.parse(r.tag()))
        // The points for the rules: every mood the words say, not only the top one.
        assertTrue(r.points().size >= 2)
    }

    @Test fun oldReadingsAreReadAgainButNotTheAIs() {
        val old = LyricsReading.parse("Sad;0.31;second=Happy;valence=-0.10;energy=0.40;by=words")!!
        assertEquals(1, old.version)
        assertTrue(old.outdated)
        assertEquals(mapOf("Sad" to 0.31, "Happy" to 0.6 * 0.31), old.points())
        assertFalse(LyricsMood.read(sad).outdated)
        assertFalse(LyricsReading.parse("Sad;0.80;by=ai")!!.outdated)
        assertFalse(LyricsReading.parse("Instrumental;1.00;by=words")!!.outdated)
        assertTrue(LyricsReading.parse("Sad;0.80;by=ai")!!.rank > LyricsMood.read(sad).rank)
        assertTrue(LyricsMood.read(sad).rank > old.rank)
    }

    @Test fun aiScoresBecomeAReading() {
        val r = LyricsReading.fromScores(mapOf("Happy" to 0.8, "Chill" to 0.6, "Sad" to 0.1, "Party" to 0.02, "Bogus" to 1.0), 0.7, 0.5,
            listOf("summer"), "ai", 0.9, "A getaway to the beach; leaving it all behind")
        assertEquals("Happy", r.mood); assertEquals("Chill", r.second)
        assertEquals(setOf("Happy", "Chill", "Sad"), r.scores.keys)
        assertFalse(';' in r.meaning)
        val back = LyricsReading.parse(r.tag())!!
        assertEquals(r, back)
        assertEquals("ai", back.by)
        // Nothing clear: no mood, but the scores still count a little.
        assertEquals("", LyricsReading.fromScores(mapOf("Happy" to 0.2), 0.0, 0.5, emptyList(), "ai", 0.5).mood)
    }
}
