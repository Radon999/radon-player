package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a DJ's break is put together from its parts, and what fits in it. */
class ScriptRulesTest {
    private fun words(n: Int) = (1..n).joinToString(" ") { "music" } + "."

    @Test fun wordsAreCountedAsSaid() {
        assertEquals(0, ScriptRules.wordsSaid(""))
        assertEquals(0, ScriptRules.wordsSaid("   "))
        assertEquals(3, ScriptRules.wordsSaid("Hello there, friend."))
        assertEquals(45, ScriptRules.wordsSaid(words(45)))
        assertTrue("a number is said as words", ScriptRules.wordsSaid("1,250 plays") >= 5)
    }

    @Test fun aWordAboutTheDayGoesBeforeTheLineWhenThereIsRoom() {
        assertEquals("Happy Friday." to "Happy Friday. ${words(43)}", ScriptRules.opened("Happy Friday.", words(43), first = false))
        assertEquals("" to words(10), ScriptRules.opened("", words(10), first = false))
    }

    @Test fun overTheMostTheWordAboutTheDayGoesButAHelloStays() {
        assertEquals("" to words(44), ScriptRules.opened("Happy Friday.", words(44), first = false))
        assertEquals("Good evening." to "Good evening. ${words(44)}", ScriptRules.opened("Good evening.", words(44), first = true))
    }

    @Test fun aFactOnlyWhenThereIsRoomForIt() {
        assertTrue(ScriptRules.factFits(words(40), words(5)))
        assertFalse(ScriptRules.factFits(words(40), words(6)))
        assertTrue("nothing to say always fits", ScriptRules.factFits(words(60), ""))
    }

    @Test fun theThreadFromTheSongBeforeOnlyWhenThereIsOneAndRoom() {
        assertTrue(ScriptRules.threadFits("Staying with Muse.", words(30), words(12)))
        assertFalse(ScriptRules.threadFits("Staying with Muse.", words(30), words(13)))
        assertFalse(ScriptRules.threadFits("", words(10), ""))
    }

    @Test fun aBreakMadeAheadIsSaidForThePartOfTheDayItIsHeardIn() {
        val line = "Good morning. Here's a set."
        assertNull("still the same part of the day", ScriptRules.asOfNow(line, "Good morning.", true, "SATURDAY morning", "SATURDAY morning") { "x" })
        assertNull("no opener: nothing to change", ScriptRules.asOfNow("Here's a set.", "", false, "SATURDAY morning", "SATURDAY evening") { "x" })
        assertNull("made before parts of the day were kept", ScriptRules.asOfNow(line, "Good morning.", true, "", "SATURDAY evening") { "x" })
        assertEquals("the first set gets a new hello", "Good evening. Here's a set." to "Good evening.",
            ScriptRules.asOfNow(line, "Good morning.", true, "SATURDAY morning", "SATURDAY evening") { "Good evening." })
        assertEquals("another set loses its word about the day", "Here's a set." to "",
            ScriptRules.asOfNow("Easy Sunday morning. Here's a set.", "Easy Sunday morning.", false, "SUNDAY morning", "SUNDAY afternoon") { error("no hello for it") })
    }
}
