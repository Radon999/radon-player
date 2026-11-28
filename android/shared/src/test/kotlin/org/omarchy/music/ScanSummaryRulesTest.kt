package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanSummaryRulesTest {
    private val R = ScanSummaryRules

    @Test fun newChangedAndGoneAreCounted() {
        val before = mapOf("a" to "A|x|", "b" to "B|x|", "c" to "C|x|")
        val after = mapOf("a" to "A|x|", "b" to "B|y|", "d" to "D|x|", "e" to "E|x|")
        assertEquals(R.Found(new = 2, changed = 1, gone = 1), R.found(before, after))
    }

    @Test fun itSaysWhatItFound() {
        assertEquals("Your music is read: 12 new, 3 changed (4,512 songs now)", R.text(R.Found(12, 3, 0), 4512))
        assertEquals("Your music is read: nothing new (1 song)", R.text(R.Found(0, 0, 0), 1))
    }

    @Test fun foldersNotConnectedAreNamed() {
        assertEquals("Your music is read: 1 gone (9 songs now). 1 folder isn't connected (E:\\Music): its songs are left as they are",
            R.text(R.Found(0, 0, 1), 9, listOf("E:\\Music")))
        assertEquals("Your music is read: nothing new (9 songs). 3 folders aren't connected (a, b…): their songs are left as they are",
            R.text(R.Found(0, 0, 0), 9, listOf("a", "b", "c")))
    }
}
