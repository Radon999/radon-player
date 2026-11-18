package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A censored word in a title is read as the word it stands for, when songs are compared. */
class CensoredTest {
    @Test fun aCensoredWordIsTheWordItStandsFor() {
        assertEquals("fuck", Censored.word("f**k"))
        assertEquals("fuck", Censored.word("f*ck"))
        assertEquals("fuck", Censored.word("f***"))
        assertEquals("fuck", Censored.word("f*k"))
        assertEquals("fuck", Censored.word("f--k"))
        assertEquals("shit", Censored.word("sh*t"))
        assertEquals("shit", Censored.word("sh!t"))
        assertEquals("the likelier of two that fit", "shit", Censored.word("s**t"))
        assertEquals("bitch", Censored.word("b*tch"))
        assertEquals("nigga", Censored.word("n***a"))
        assertEquals("ass", Censored.word("a**"))
        assertEquals("fucking", Censored.word("f***ing"))
        assertEquals("motherfucker", Censored.word("motherf**ker"))
    }

    @Test fun ordinaryWordsWithSignsAreLeftAlone() {
        assertNull(Censored.word("p!nk"))
        assertNull(Censored.word("t-shirt"))
        assertNull(Censored.word("hip-hop"))
        assertNull(Censored.word("fuck"))
        assertNull("the letters there have to fit", Censored.word("d**n't"))
        assertEquals("p!nk and the t-shirt", Censored.plain("p!nk and the t-shirt"))
        assertEquals("n.p.d", Censored.plain("n.p.d"))
        assertEquals("a - b", Censored.plain("a - b"))
    }

    @Test fun aTitleIsReadWithItsCensoredWordsWrittenOut() {
        assertEquals("just don't give a fuck", Censored.plain("just don't give a f**k"))
        assertEquals("letters left out with a gap", "just don't give a fuck", Censored.plain("just don't give a f  k"))
        assertEquals("bad bitch (remix)", Censored.plain("bad b*tch (remix)"))
        assertEquals("holy shit!", Censored.plain("holy sh*t!"))
    }

    @Test fun yourCensoredCopyIsTheSameSong() {
        assertEquals(statsKey("Just Don't Give A Fuck", "Eminem"), statsKey("Just Don't Give a F**k", "Eminem"))
        assertEquals(statsKey("Just Don't Give A Fuck", "Eminem"), statsKey("Just Don't Give a F  k", "Eminem"))
        assertEquals(statsKey("Bad Bitch", "X"), statsKey("Bad B*tch (feat. Someone)", "X"))
        assertEquals(looseTitle("Still Don't Give A Fuck"), looseTitle("Still Don't Give a F*ck"))
        assertTrue(CopyRules.sameSong("Shit Show", "X", "Sh*t Show", "X"))
        assertNotEquals("another song is another song", statsKey("Just Don't Give A Fuck", "Eminem"), statsKey("Still Don't Give A Fuck", "Eminem"))
    }
}
