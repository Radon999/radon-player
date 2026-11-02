package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PronounceTest {
    private val known = setOf("because", "you've", "been", "playing", "and", "juice", "see", "sin", "here", "some", "music",
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w",
        "x", "y", "z", "world", "you")

    @Test fun unknownNamesAreSaid() {
        val s = Pronounce.sayable("because you've been playing Seesyn and Juice WRLD", known)
        assertEquals("because you've been playing see sin and Juice world", s)
    }

    @Test fun knownWordsStayAndCapsAreSpelt() {
        assertEquals("here some music", Pronounce.sayable("here some music", known))
        assertEquals("U K", Pronounce.word("UK", known))
        assertTrue(Pronounce.sayable("Zzqx", known).isNotBlank())
        assertEquals("anything", Pronounce.sayable("anything", emptySet()))
    }
}
