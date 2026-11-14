package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The DJ in the queue, as it is today: starting it, its next set queued before this one runs out, the next set
 *  introduced when its first song starts, and what stops it. Pinned before the DJ's part of Music becomes a service
 *  of its own (docs/rebuild/RADON_REBUILD_PLAN.md, Phases 8 and 9). The library is a playlist of the test's own song
 *  files; the DJ is kept to them (Only my songs) and its voice is off, so nothing is looked up or said out loud.
 *
 *  These run after DjBehaviourTest (by name): once there's a library, the app makes the DJ's first set ahead by
 *  itself, which changes what the one DJ plays from under any test that had given it other songs (the plan's C-5). */
class DjFlowTest {
    private val engine: FakeEngine by lazy { FakeEngine().also { e -> Harness.ui { Music.player = e } } }

    private fun loaded() = engine.loads.lastOrNull()?.id == Music.current()?.id

    /** The DJ started on a library of 80 songs; back once its first set's first song is in the player. */
    private fun startDj(before: () -> Unit = {}) {
        Harness.ctx
        engine
        val songs = Harness.files("library", 80)
        Harness.ui {
            Music.lyricsToFiles = false
            Music.autoplay = false
            Music.repeat = "off"
            val pl = Music.lists.named("Test library") ?: Music.lists.create("Test library")
            Music.lists.replaceTracks(pl.id, songs)
            Music.chooseDjVoice(false)
            Music.djStop()
            Music.chooseDjNew(false)
        }
        Harness.until("the DJ free to start") { Music.djBusy == null }
        val loads = engine.loads.size
        Harness.ui { before(); Music.djStart() }
        Harness.until("the DJ's first set playing") { Music.dj.on && engine.loads.size > loads && Music.current()?.dj == 1 && loaded() }
    }

    @Test fun startingTheDjPlaysAFirstSetFromTheLibrary() {
        startDj(before = { Music.repeat = "all" })
        val set = Music.queue.filter { it.dj == 1 }
        assertTrue("a set is 3 to 6 songs (${set.size})", set.size in 3..Dj.SET)
        assertEquals("the first set starts at the front of the queue", 1, Music.queue[0].dj)
        assertEquals(0, Music.index)
        assertTrue(set.all { it.id.startsWith("file-library-") })
        assertEquals(set.size, set.map { it.id }.distinct().size)
        val shown = Music.shownDj()
        assertTrue(shown.on)
        assertTrue(shown.kind.isNotEmpty())
        assertTrue(shown.line.isNotEmpty())
        assertEquals("Repeat all means nothing to the DJ: starting it turns it off", "off", Music.repeat)
        assertTrue(engine.playWhenReady)
    }

    @Test fun theNextSetIsQueuedBeforeThisOneRunsOutAndIntroducedWhenItStarts() {
        startDj()
        val first = Music.queue.count { it.dj == 1 }
        // On to the set's last but one song: by then the next set is asked for
        while (Music.index < first - 2) {
            Harness.ui { Music.next() }
            Harness.until("the next song loaded") { loaded() }
        }
        Harness.until("the second set queued") { Music.queue.any { it.dj == 2 } }
        assertEquals("no song twice in the queue", Music.queue.size, Music.queue.map { it.id }.distinct().size)
        assertEquals("1", Music.dj.shown)
        // Into the second set: its line is the one shown from its first song on
        while (Music.current()?.dj == 1) {
            Harness.ui { Music.next() }
            Harness.until("the next song loaded") { loaded() }
        }
        assertEquals(2, Music.current()?.dj)
        Harness.until("the second set introduced") { Music.dj.shown == "2" }
        assertTrue(Music.dj.on)
    }

    /** Switch it up: a new set now, in place of the rest of this one; the song that was playing stays behind it. */
    @Test fun switchItUpPutsANewSetInPlaceOfTheRestOfThisOne() {
        startDj()
        val was = Music.current()?.id
        Harness.ui { Music.djSwitch() }
        Harness.until("the new set playing") { Music.current()?.dj == 2 && loaded() }
        assertTrue(Music.dj.on)
        assertEquals(1, Music.index)
        assertEquals(was, Music.queue[0].id)
        assertEquals("the rest of the first set is gone", 1, Music.queue.count { it.dj == 1 })
        assertTrue(Music.queue.count { it.dj == 2 } in 3..Dj.SET)
        assertEquals(Music.queue.size, Music.queue.map { it.id }.distinct().size)
    }

    /** With the DJ, Repeat is only Repeat one or off (Repeat all means nothing to it), and shuffle isn't used. */
    @Test fun withTheDjRepeatIsOneOrOffAndShuffleDoesNothing() {
        startDj()
        Harness.ui { Music.cycleRepeat() }
        assertEquals("one", Music.repeat)
        Harness.ui { Music.cycleRepeat() }
        assertEquals("off", Music.repeat)
        val order = Music.queue.map { it.id }
        Harness.ui { Music.shuffle() }
        assertEquals(order, Music.queue.map { it.id })
    }

    /** Stays: with the DJ off, its first set is made ahead, so starting it is instant.
     *  C-3, fixed (it was: that set's hello was written when the set was made, so it greeted with the time it was made
     *  at: made in the morning, started in the afternoon, it still said good morning). Now it greets with the time
     *  it's started at. (A Wednesday, which has no hello of its own, so the hello is the hour's.) */
    @Test fun aFirstSetMadeAheadGreetsWithTheTimeItIsStartedAt() {
        val morning = java.time.LocalDateTime.of(2026, 11, 11, 9, 0)
        Dj.clock = { morning }
        try {
            startDj()
            Harness.ui { Music.djStop() }   // three seconds later the next first set is made ahead
            Thread.sleep(5000)
            Dj.clock = { morning.withHour(15) }
            var atOnce = false
            Harness.ui { Music.djStart(); atOnce = Music.dj.on }
            assertTrue("the first set made ahead starts the DJ at once", atOnce)
            val line = Music.shownDj().line
            assertFalse("at three in the afternoon it greeted with the morning: $line",
                Regex("morning|rise and shine", RegexOption.IGNORE_CASE).containsMatchIn(line))
            assertTrue("it greets with the afternoon: $line", line.contains("afternoon", ignoreCase = true))
            assertTrue("and still introduces its set: $line", line.length > 60)
        } finally { Dj.clock = { java.time.LocalDateTime.now() } }
    }

    /** A set that plays has had its kind's turn: starting the DJ writes its first set's kind to the history. */
    @Test fun aSetThatPlaysTakesItsKindsTurn() {
        Harness.ctx
        fun times() = NoRepeat.decode(Prefs.text("dj_kind_history")).groupingBy { it }.eachCount()
        Thread.sleep(1500)   // what the tests before wrote, settled
        val before = times()
        startDj()
        val kind = Music.shownDj().kind
        assertTrue(kind.isNotEmpty())
        Harness.until("its kind written") { (times()[kind] ?: 0) == (before[kind] ?: 0) + 1 }
    }

    @Test fun pickingOtherSongsStopsTheDj() {
        startDj()
        val other = Harness.files("other", 3)
        Harness.ui { Music.playFrom(other, 0, radio = false) }
        Harness.until("the song picked loaded") { engine.loads.lastOrNull()?.id == other[0].id }
        assertFalse(Music.dj.on)
        assertEquals(other.map { it.id }, Music.queue.map { it.id })
    }

    @Test fun stoppingTheDjLeavesTheMusicAsItIs() {
        startDj()
        val playing = Music.current()?.id
        val queue = Music.queue.map { it.id }
        Harness.ui { Music.djStop() }
        assertFalse(Music.dj.on)
        assertEquals(playing, Music.current()?.id)
        assertEquals(queue, Music.queue.map { it.id })
        assertTrue(engine.playWhenReady)
    }
}
