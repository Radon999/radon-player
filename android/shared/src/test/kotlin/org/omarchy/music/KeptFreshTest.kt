package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Something slow to make, kept and made again in the background as it gets old (what your listening adds up to). */
class KeptFreshTest {
    private var t = 1000.0
    private val waiting = ArrayList<() -> Unit>()
    private var made = 0
    private var fails = false
    private val kept = KeptFresh(600.0, 1800.0, { t }, { waiting += it }) { if (fails) error("can't be made") else "no. ${++made}" }

    @Test fun theFirstAskerWaitsAndThenItIsKept() {
        assertNull(kept.current)
        assertEquals("no. 1", kept.get())
        t += 599
        assertEquals("no. 1", kept.get())
        assertEquals(1, made)
        assertEquals(0, waiting.size)
    }

    @Test fun anOldOneIsGivenAtOnceWhileANewOneIsMadeBehind() {
        kept.get()
        t += 600
        assertEquals("the old one, without waiting", "no. 1", kept.get())
        assertEquals("no. 1", kept.get())
        assertEquals("one at a time", 1, waiting.size)
        assertEquals(1, made)
        waiting.removeAt(0)()
        assertEquals("no. 2", kept.get())
        assertEquals("no. 2", kept.current)
        assertEquals(0, waiting.size)
        t += 600
        kept.get()
        assertEquals("and again when that one is old", 1, waiting.size)
    }

    @Test fun oneTooOldIsWaitedFor() {
        kept.get()
        t += 1800
        assertEquals("no. 2", kept.get())
        assertEquals(0, waiting.size)
    }

    @Test fun aNewOneThatCantBeMadeLeavesTheOldOneAndIsTriedAgain() {
        kept.get()
        t += 600
        fails = true
        kept.get()
        waiting.removeAt(0)()
        assertEquals("no. 1", kept.get())
        assertEquals("tried again at the next asking", 1, waiting.size)
        fails = false
        waiting.removeAt(0)()
        assertEquals("no. 2", kept.get())
    }

    @Test fun workThatCantBeStartedIsTriedAgainToo() {
        var refuse = true
        val started = ArrayList<() -> Unit>()
        val k = KeptFresh(600.0, 1800.0, { t }, { if (refuse) error("no thread"); started.add(it) }) { "x" }
        k.get()
        t += 600
        assertEquals("x", k.get())
        refuse = false
        k.get()
        assertEquals(1, started.size)
    }

    @Test fun manyAskingAtOnceForTheFirstMakeOne() {
        val n = AtomicInteger()
        val k = KeptFresh(600.0, 1800.0, { 1000.0 }, { it() }) { Thread.sleep(50); "no. ${n.incrementAndGet()}" }
        val go = CountDownLatch(1); val done = CountDownLatch(8)
        val got = java.util.Collections.synchronizedList(ArrayList<String>())
        repeat(8) { Thread { go.await(); got += k.get(); done.countDown() }.start() }
        go.countDown()
        done.await(10, TimeUnit.SECONDS)
        assertEquals(1, n.get())
        assertEquals(List(8) { "no. 1" }, got.toList())
        assertSame(k.current, k.get())
    }
}
