package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveSearchTest {
    @Test fun whenToSearch() {
        assertNull(liveSearchWait("", "tidal"))
        assertNull(liveSearchWait("a ", "tidal"))   // one letter: not yet
        // The waits as they are now (120 and 300 ms; the test still had the first ones, 150 and 450)
        assertEquals(120L, liveSearchWait("jo", "local"))   // your own songs: right away
        assertEquals(300L, liveSearchWait("joji", "tidal"))   // a service: once you pause
        assertEquals(50L, liveSearchWait("https://tidal.com/browse/track/1", "local"))   // a pasted link: at once
    }
}
