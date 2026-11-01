package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The DJ's lines as they're said out loud: no symbols, numbers in words, contractions, short sentences. */
class SpokenTest {
    private fun say(s: String) = Spoken.humanize(s, filler = 0.0)

    @Test fun numbersInWords() {
        assertEquals("forty-two", Spoken.words(42))
        assertEquals("fifteen hundred", Spoken.words(1500))
        assertEquals("twelve hundred thirty-four", Spoken.words(1234))
        assertEquals("a thousand", Spoken.words(1000))
        assertEquals("two thousand three hundred forty-five", Spoken.words(2345))
        assertEquals("a million", Spoken.words(1_000_000))
    }

    @Test fun yearsAsSaid() {
        assertEquals("twenty fifteen", Spoken.year(2015))
        assertEquals("two thousand eight", Spoken.year(2008))
        assertEquals("nineteen ninety-nine", Spoken.year(1999))
        assertEquals("nineteen oh five", Spoken.year(1905))
    }

    @Test fun symbolsAndFeaturing() {
        val s = say("Up next, “Get Lucky (feat. Pharrell Williams) - 2013 Remaster” by Daft Punk & friends.")
        assertTrue(s, "featuring Pharrell Williams" in s)
        assertFalse(s, "feat." in s || "&" in s || "“" in s || "Remaster" in s)
        assertTrue(s, "Daft Punk and friends" in s)
        assertEquals("About twenty-three percent of it.", say("About 23% of it."))
    }

    @Test fun numbersYearsAndTimes() {
        assertEquals("You played it twelve hundred thirty-four times in twenty fifteen.", say("You played it 1,234 times in 2015."))
        assertEquals("Around ten at night.", say("Around 10 PM."))
        assertEquals("Four and a half hours.", say("4.5 hours."))
        assertEquals("Back to the eighties.", say("Back to the 80s."))
        assertEquals("Two thousand fifteen plays.", say("2015 plays"))
    }

    @Test fun contractionsAndShortSentences() {
        assertEquals("It's late, but you've been up all night.", say("It is late but you have been up all night."))
        assertEquals("Here's the plan. Three songs.", say("Here is the plan: three songs."))
        assertEquals("Let's go.", say("Let us go"))
    }

    @Test fun fillerOnlySometimesAndNeverOnGreetings() {
        val r = Random(7)
        val got = (0 until 40).map { Spoken.humanize("Here's something new.", 0.5, r) }
        assertTrue(got.any { it == "Here's something new." })
        assertTrue(got.any { it != "Here's something new." && it.endsWith("here's something new.") })
        assertEquals("Good evening, friend.", Spoken.humanize("Good evening, friend.", 1.0, Random(1)))
        // A name keeps its capital after a filler
        assertTrue(Spoken.humanize("Daft Punk, coming up.", 1.0, Random(2)).endsWith("Daft Punk, coming up."))
    }
}
