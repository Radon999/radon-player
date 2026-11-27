package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** Two artists of one name: the one sharing titles with your songs is yours; a stranger never is. */
class ArtistMatchRulesTest {
    private val R = ArtistMatchRules
    private val lofi = 11L to setOf("nostalgia pack", "warm", "tonight", "effortless", "across the room")
    private val other = 22L to setOf("standn still", "in my head")

    @Test fun yoursIsTheOneSharingTitles() {
        assertEquals(11L, R.pick(listOf(other, lofi), setOf("warm", "tonight", "1234", "back in")))
    }

    @Test fun aStrangerOfTheSameNameIsNeverYours() {
        assertEquals("only the other Forrest on Deezer: none of yours, so nothing", 0L,
            R.pick(listOf(other), setOf("warm", "tonight", "1234", "back in")))
    }

    @Test fun tooLittleToTellGoesByTheName() {
        assertEquals("followed without songs of theirs", 22L, R.pick(listOf(other, lofi), emptySet()))
        assertEquals("one song of theirs, not on Deezer", 22L, R.pick(listOf(other, lofi), setOf("a demo")))
        assertEquals(0L, R.pick(emptyList(), setOf("warm")))
    }
}
