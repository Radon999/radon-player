package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** What your listening adds up to: the plays it's given are counted, the picture made is kept and given again, and
 *  a new one is made once it's cleared. */
class TasteServiceTest {
    @Test fun thePlaysAreCountedAndThePictureIsKept() {
        Harness.ctx
        val nowS = System.currentTimeMillis() / 1000
        TasteService.historyStandIn = {
            listOf(Heard(nowS - 100, "Taste Test Artist", "Taste Test Song"), Heard(nowS - 50, "Taste Test Artist", "Taste Test Song"),
                Heard(nowS - 30, "Taste Test Other", "Another Taste Test Song"))
        }
        TasteService.clear()
        try {
            val k = Stats.statsKey("Taste Test Song", "Taste Test Artist")
            val first = TasteService.get()
            assertEquals(2, first.facts.count[k])
            assertEquals(3, first.facts.total)
            assertEquals(nowS - 50, TasteService.lastHeard()[k])
            assertSame("asked again: the one that was made", first, TasteService.get())
            assertSame(first, TasteService.current)
            TasteService.clear()
            assertNotSame("cleared: made again", first, TasteService.get())
        } finally {
            TasteService.historyStandIn = null; TasteService.clear()
        }
    }
}
