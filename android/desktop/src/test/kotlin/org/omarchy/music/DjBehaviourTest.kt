package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The whole app's logic started in a test, on the PC's side of it (where the app's folder is just a folder): its
 *  files in a folder made for the run, no player, no window, no account. What the rebuild's tests of the DJ and of
 *  playback stand on (docs/rebuild/RADON_REBUILD_PLAN.md, Phase 0). */
object Harness {
    val ctx: Ctx by lazy {
        val dir = java.nio.file.Files.createTempDirectory("radon-test").toFile()
        val c = Ctx(dir, java.io.File(dir, "cache").apply { mkdirs() })
        // As PcApp.start, without the PC's own parts (the player, the tray, the remote, Discord, updates)
        Crashes.init(c)
        Music.init(c)
        LastFm.init(c)
        SpotifyHistory.init(c)
        DeezerHistory.init(c)
        YouTubeHistory.init(c)
        TidalHistory.init(c)
        AppleHistory.init(c)
        Stats.init(c)
        c
    }

    /** Songs for the DJ to play from, each by its own artist. */
    fun songs(n: Int, from: Int = 1): List<Track> = (from until from + n).map { Track("test-$it", "ytm", "Song number $it", "Artist $it") }
}

/** How the DJ behaves today, pinned before it's rebuilt: what must stay, and the faults the plan names (each test of
 *  a fault says which, C-…, and is turned round when its fix goes in). */
class DjBehaviourTest {
    private val named = Regex("“([^”]+)”")

    @Test fun theAppStartsWithoutAWindowOrAPlayer() {
        Harness.ctx
        assertTrue(Music.queue.isEmpty())
        assertFalse(Music.dj.on)
    }

    /** Stays: a song still to come in the queue is never in a new set. */
    @Test fun aSetNeverHasASongStillToCome() {
        Harness.ctx
        val pool = Harness.songs(60)
        Dj.playFrom("the test songs", pool)
        val coming = pool.take(30).map { songKey(it) }.toSet()
        var built = 0
        repeat(60) {
            val b = Dj.build(kind = "onehit", coming = coming) ?: return@repeat
            built++
            assertTrue(b.songs.size >= 3)
            assertTrue(b.songs.none { songKey(it) in coming })
        }
        assertTrue("sets were made ($built)", built > 5)
    }

    /** C-1 (a fault, as it is today): the line is written, naming the set's first song, and only then are the songs
     *  still to come taken out of the set. So the DJ can name a song that isn't in the set it introduces. */
    @Test fun fault_theLineCanNameASongTheSetNoLongerHas() {
        Harness.ctx
        val pool = Harness.songs(60)
        Dj.playFrom("the test songs", pool)
        val coming = pool.take(30).map { songKey(it) }.toSet()
        var stale: String? = null
        var built = 0
        for (i in 0 until 400) {
            val b = Dj.build(kind = "onehit", coming = coming) ?: continue
            built++
            val titles = b.songs.map { it.title }
            if (named.findAll(b.line).any { it.groupValues[1] !in titles }) { stale = b.line; break }
        }
        assertTrue("sets were made ($built)", built > 0)
        assertNotNull("after $built sets no line named a song its set doesn't have: is C-1 fixed? Then turn this test round", stale)
    }

    /** C-4 (a fault, as it is today): a set that was only made (never queued, never played) has already used up its
     *  kind's turn: the kind is written to the history when the set is built. */
    @Test fun fault_aSetNeverPlayedStillUsesUpItsKindsTurn() {
        Harness.ctx
        Dj.playFrom("the test songs", Harness.songs(40, from = 100))
        assertFalse(NoRepeat.decode(Prefs.text("dj_kind_history")).contains("mix"))
        val b = Dj.build(kind = "mix")
        assertNotNull(b)
        assertEquals("mix", b!!.kind)
        var kept = false
        for (i in 0 until 50) {   // kept on the app's own thread, a moment later
            if (NoRepeat.decode(Prefs.text("dj_kind_history")).contains("mix")) { kept = true; break }
            Thread.sleep(100)
        }
        assertTrue("the kind wasn't written at build: is C-4 fixed? Then turn this test round", kept)
    }
}
