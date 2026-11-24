package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricSyncRulesTest {
    private fun heard(vararg ws: Pair<String, Double>) = ws.map { LyricSyncRules.Heard(LyricSyncRules.norm(it.first), it.second) }

    @Test fun recogniserPiecesBecomeWords() {
        val w = LyricSyncRules.words(listOf("\u2581C", "od", "e", "\u2581Mon", "key", " get"), listOf(0.7, 0.9, 1.0, 1.2, 1.3, 1.7), 20.0)
        assertEquals(listOf("code" to 20.7, "monkey" to 21.2, "get" to 21.7), w.map { it.word to it.at })
    }

    @Test fun overlappingWindowsKeepEachWordOnce() {
        val a = heard("one" to 18.5, "two" to 19.5)
        assertEquals(listOf("one"), LyricSyncRules.keep(a, 0.0, 100.0).map { it.word })   // the first window keeps to 19 s
        assertEquals(listOf("two"), LyricSyncRules.keep(a, 18.0, 100.0).map { it.word })  // the next from 19 s
    }

    @Test fun linesAreTimedByTheirFirstWordHeard() {
        val plain = "[Verse 1]\nHello there friend\n\nGoodbye my love\nSee you soon"
        val h = heard("hello" to 10.0, "there" to 10.4, "friend" to 10.8, "yeah" to 12.0,   // an ad-lib in between
            "goodbye" to 20.0, "my" to 20.4, "love" to 20.8, "see" to 30.0, "you" to 30.3, "soon" to 30.6)
        val t = LyricSyncRules.timeLines(plain, h, 60.0)!!
        assertEquals(listOf("Hello there friend", "Goodbye my love", "See you soon"), t.map { it.second })   // the label left out
        assertEquals(10.0 - LyricSyncRules.LEAD, t[0].first, 0.01)
        assertEquals(20.0 - LyricSyncRules.LEAD, t[1].first, 0.01)
        assertEquals(30.0 - LyricSyncRules.LEAD, t[2].first, 0.01)
    }

    @Test fun aLineNotHeardIsPlacedBetweenItsNeighbours() {
        val plain = "one two three\nfour five six\nseven eight nine"
        // the middle line mumbled: none of its words heard
        val h = heard("one" to 10.0, "two" to 10.5, "three" to 11.0, "seven" to 22.0, "eight" to 22.5, "nine" to 23.0)
        val t = LyricSyncRules.timeLines(plain, h, 60.0)!!
        assertEquals(16.0 - LyricSyncRules.LEAD, t[1].first, 0.01)   // halfway by words
        assertTrue(t[0].first < t[1].first && t[1].first < t[2].first)
    }

    @Test fun aMisheardFirstWordStillTimesItsLine() {
        val plain = "alpha bravo charlie\ndelta echo foxtrot"
        val h = heard("alpha" to 5.0, "bravo" to 5.4, "charlie" to 5.8, "zzz" to 14.6, "echo" to 15.0, "foxtrot" to 15.4)
        val t = LyricSyncRules.timeLines(plain, h, 60.0)!!
        assertEquals(15.0 - LyricSyncRules.WORD_SECS - LyricSyncRules.LEAD, t[1].first, 0.01)   // from its second word
    }

    @Test fun tooLittleHeardIsNotTrusted() {
        val plain = "a long line of words here\nanother long line of words\nand a third one too"
        assertNull(LyricSyncRules.timeLines(plain, heard("long" to 3.0, "line" to 3.4), 60.0))
        assertNull(LyricSyncRules.timeLines(plain, emptyList(), 60.0))
    }

    @Test fun lrcLines() {
        assertEquals("[00:05.25]Hello\n[01:02.00]World", LyricSyncRules.lrc(listOf(5.25 to "Hello", 62.0 to "World")))
    }

    @Test fun sungWordsCompareLoosely() {
        assertEquals("you", LyricSyncRules.norm("youuuuuuu"))
        assertEquals("dont", LyricSyncRules.norm("Don't"))
        assertTrue(LyricSyncRules.alike("dilligent", "diligent") >= 0.6)
        assertTrue(LyricSyncRules.alike("monkey", "coffee") < 0.6)
    }
}
