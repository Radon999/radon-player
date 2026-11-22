package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverWordsTest {
    @Test fun aReleaseIsSearchedForWithoutItsKindOrSigns() {
        assertEquals("Lone Survivor", coverWords("Lone Survivor - EP"))
        assertEquals("Castlecomer Lone Survivor", coverWords("Castlecomer / Lone Survivor - EP"))
        assertEquals("Castlecomer Lone Survivor", coverWords("Castlecomer Lone Survivor EP"))
        assertEquals("Talk", coverWords("Talk (Single)"))
        assertEquals("Talk", coverWords("Talk - Single"))
        assertEquals("Mercury Act 1", coverWords("Mercury - Act 1"))
        assertEquals("Don't Stop & Go", coverWords("Don't Stop & Go"))
        assertEquals("Deep", coverWords("Deep"))
        assertEquals("÷", coverWords("÷"))
    }
}
