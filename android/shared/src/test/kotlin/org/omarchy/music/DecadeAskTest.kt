package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class DecadeAskTest {
    @Test fun aDecadeAskedForIsItsFirstYear() {
        assertEquals(1980, DecadeAsk.of("80s")); assertEquals(1980, DecadeAsk.of("play the 80s")); assertEquals(1980, DecadeAsk.of("1980s"))
        assertEquals(2000, DecadeAsk.of("00s")); assertEquals(2010, DecadeAsk.of("2010s")); assertEquals(2010, DecadeAsk.of("the 10s"))
        assertEquals(1990, DecadeAsk.of("some 90's music"))
    }

    @Test fun moreThanADecadeIsNotOne() {
        assertEquals(null, DecadeAsk.of("80s rock")); assertEquals(null, DecadeAsk.of("Blink 182")); assertEquals(null, DecadeAsk.of(""))
    }
}
