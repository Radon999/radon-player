package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A player that plays nothing: it notes what it was told (the songs loaded, play and pause, its volume), for the
 *  tests. While it "plays", its place in the song moves on with the clock, as a real player's does, up to the song's
 *  length (it never says the song ended by itself: [end] does). Crossfade's second player is another of these. */
class FakeEngine : Engine {
    class Load(val id: String, val uri: String, val startMs: Long)
    val loads = java.util.concurrent.CopyOnWriteArrayList<Load>()
    /** The second players asked of this one (crossfade's ending song), and where each was to start. */
    val tails = java.util.concurrent.CopyOnWriteArrayList<FakeEngine>()
    @Volatile var startedAt = 0L
    @Volatile var released = false
    @Volatile var loudest = 0f   // the loudest it was ever set
    @Volatile var pauses = 0
    @Volatile override var durationMs = 0L
    @Volatile override var state = Engine.IDLE
    @Volatile private var uri: String? = null
    @Volatile private var base = 0L                       // where it was when the clock was last set
    @Volatile private var since = System.nanoTime()
    @Volatile private var playing = false
    private var sounding: (() -> Unit)? = null

    override var positionMs: Long
        get() {
            val p = if (isPlaying) base + (System.nanoTime() - since) / 1_000_000 else base
            return if (durationMs > 0) minOf(p, durationMs) else p
        }
        set(v) { base = v; since = System.nanoTime() }
    override var playWhenReady: Boolean
        get() = playing
        set(v) { val at = positionMs; playing = v; positionMs = at }   // the clock starts, or stops, from here
    @Volatile private var vol = 1f
    override var volume: Float
        get() = vol
        set(v) { vol = v; if (v > loudest) loudest = v }
    override val isPlaying get() = playing && state == Engine.READY
    override val hasItem get() = uri != null
    override val currentUri get() = uri
    override val audioRate = 0
    override val audioBits = 0
    override fun play() { playWhenReady = true; sounding?.let { s -> sounding = null; s() } }
    override fun pause() { pauses++; playWhenReady = false }
    override fun seekTo(ms: Long) { positionMs = ms }
    override fun load(item: PlayItem, startMs: Long) {
        loads += Load(item.id, item.uri, startMs); uri = item.uri; durationMs = 0; state = Engine.READY; positionMs = startMs
    }
    override fun setSpeed(speed: Float, pitch: Float) {}
    override fun tail(uri: String, startMs: Long): Engine? = FakeEngine().also {
        it.uri = uri; it.durationMs = durationMs; it.state = Engine.READY; it.positionMs = startMs; it.startedAt = startMs; tails += it
    }
    override fun onceSounding(run: () -> Unit) { sounding = run }
    override fun release() { released = true; playing = false }
    /** The song ran out, as a real player says it. */
    fun end() { state = Engine.ENDED }
}

/** How the queue and the player behave today, pinned before they're taken out of Music into a service of their own
 *  (docs/rebuild/RADON_REBUILD_PLAN.md, section 2.1 and Phase 9). Songs here are files of the test's own (empty:
 *  nothing is decoded), so nothing is looked up on a service. */
class PlaybackBehaviourTest {
    private val engine: FakeEngine by lazy { FakeEngine().also { e -> ui { Music.player = e } } }

    /** On the app's own thread, as the screens call it. */
    private fun <T> ui(block: () -> T): T = runBlocking(Dispatchers.Main) { block() }

    private fun until(what: String, ms: Long = 8000, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (ok()) return; Thread.sleep(25) }
        throw AssertionError("waited ${ms / 1000} s for: $what (loaded: ${engine.loads.map { it.id }}, index ${Music.index}, at ${engine.positionMs} ms)")
    }

    private fun files(tag: String, n: Int = 3): List<Track> {
        val dir = java.io.File(Harness.ctx.filesDir, "songs-$tag").apply { mkdirs() }
        return (1..n).map { i ->
            val f = java.io.File(dir, "$tag-$i.flac").apply { writeBytes(ByteArray(0)) }
            Track("file-$tag-$i", "file", "Played $tag $i", "Band $tag", file = f.path)
        }
    }

    private fun start(tag: String, at: Int = 0): List<Track> {
        Harness.ctx
        engine   // the player, once
        val songs = files(tag)
        ui {
            Music.lyricsToFiles = false   // no lyrics looked up for a test's songs
            Music.autoplay = false        // nor a radio at the end of the queue
            Music.repeat = "off"
            Music.playFrom(songs, at, radio = false)
        }
        until("the song at $at loaded") { engine.loads.lastOrNull()?.id == songs[at].id }
        return songs
    }

    @Test fun playingASongLoadsItAndPlays() {
        val songs = start("first")
        assertEquals(0, Music.index)
        assertEquals(songs.map { it.id }, Music.queue.map { it.id })
        assertEquals(songs[0].file, engine.loads.last().uri)
        assertEquals(0L, engine.loads.last().startMs)
        assertTrue(engine.playWhenReady)
    }

    @Test fun nextPlaysTheNextSong() {
        val songs = start("next")
        ui { Music.next() }
        until("the second song loaded") { engine.loads.lastOrNull()?.id == songs[1].id }
        assertEquals(1, Music.index)
    }

    /** Skipping on while a song is still being got: the one skipped past is never loaded into the player. */
    @Test fun aSongSkippedWhileItLoadsIsNeverLoaded() {
        val songs = start("skip")
        ui { Music.next(); Music.next() }   // both before the second song's load came back
        until("the third song loaded") { engine.loads.lastOrNull()?.id == songs[2].id }
        assertEquals(2, Music.index)
        assertFalse(engine.loads.any { it.id == songs[1].id })
    }

    /** The last song ends, with no Repeat, no Autoplay and no other queue: the player is paused and stays on it. */
    @Test fun theQueueEndingStopsThePlayer() {
        val songs = start("end", at = 2)
        val pausesBefore = engine.pauses
        engine.end()
        ui { Music.engineEnded() }
        until("the player paused") { engine.pauses > pausesBefore }
        assertEquals(2, Music.index)
        assertEquals(songs[2].id, engine.loads.last().id)
        assertFalse(engine.playWhenReady)
    }

    /** Previous, more than 10 seconds into a song: the song from its start again, not the one before. */
    @Test fun previousWellIntoASongStartsItAgain() {
        start("back-late", at = 1)
        val loads = engine.loads.size
        engine.positionMs = 30_000
        ui { Music.position = 30.0; Music.previous() }
        assertTrue("back at its start (${engine.positionMs} ms)", engine.positionMs < 2000)
        assertEquals(1, Music.index)
        assertEquals("nothing new is loaded", loads, engine.loads.size)
    }

    /** Crossfade: a few seconds before a song's end a second player gets the ending ready; at the fade it takes the
     *  ending over (so there's no gap), the next song comes in in the main player, the ending fades out in its own and
     *  is let go, and the next song ends up at full volume. */
    @Test fun crossfadeFadesTheEndingOutInAPlayerOfItsOwnWhileTheNextComesIn() {
        val songs = start("fade")
        try {
            ui { Prefs.set(Prefs.CROSSFADE, true); Prefs.setNum(Prefs.CROSSFADE_SECS, 2f) }
            engine.durationMs = 60_000
            engine.positionMs = 56_500   // a second and a half before the fade (the last 2 seconds)
            until("a second player got the ending ready") { engine.tails.size == 1 }
            val ending = engine.tails[0]
            assertEquals(songs[0].file, ending.currentUri)
            assertEquals("ready where the fade begins", 58_000L, ending.startedAt)
            until("the next song loaded", 20_000) { engine.loads.lastOrNull()?.id == songs[1].id }
            assertEquals(1, Music.index)
            until("the ending's player was let go", 20_000) { ending.released }
            assertTrue("the ending was heard in its own player (loudest ${ending.loudest})", ending.loudest > 0.5f)
            until("the next song at full volume") { engine.volume == 1f }
            assertEquals("one second player, no more", 1, engine.tails.size)
        } finally { ui { Prefs.set(Prefs.CROSSFADE, false) } }
    }

    /** No crossfade on Repeat one, nor with bit-perfect on: no second player is ever asked for. */
    @Test fun noCrossfadeOnRepeatOneOrWithBitPerfect() {
        start("nofade")
        try {
            ui { Prefs.set(Prefs.CROSSFADE, true); Prefs.setNum(Prefs.CROSSFADE_SECS, 2f); Music.repeat = "one" }
            engine.durationMs = 60_000
            engine.positionMs = 56_500
            Thread.sleep(1500)
            assertTrue("Repeat one", engine.tails.isEmpty())
            ui { Music.repeat = "off"; Music.bitPerfect = true }
            engine.positionMs = 56_500
            Thread.sleep(1500)
            assertTrue("bit-perfect", engine.tails.isEmpty())
        } finally { ui { Music.bitPerfect = false; Music.repeat = "off"; Prefs.set(Prefs.CROSSFADE, false) } }
    }

    /** Previous, in a song's first 10 seconds: the song before it. */
    @Test fun previousAtASongsStartGoesToTheSongBefore() {
        val songs = start("back-early", at = 1)
        engine.positionMs = 2_000
        ui { Music.position = 2.0; Music.previous() }
        until("the song before loaded") { engine.loads.lastOrNull()?.id == songs[0].id }
        assertEquals(0, Music.index)
    }

    /** Repeat all: after the last song, the first again. */
    @Test fun repeatAllGoesRoundToTheFirstSong() {
        val songs = start("round", at = 2)
        ui { Music.repeat = "all" }
        engine.end()
        ui { Music.engineEnded() }
        until("the first song loaded") { engine.loads.lastOrNull()?.id == songs[0].id }
        assertEquals(0, Music.index)
        ui { Music.repeat = "off" }
    }

    /** A song that reaches its end but whose player never says so (it sits there, "playing", not moving): after a
     *  few seconds the app moves on by itself. */
    @Test fun aSongStuckAtItsEndMovesOnByItself() {
        val songs = start("stuck")
        engine.durationMs = 200_000
        engine.positionMs = 199_800   // in its last second and a half, and it stays there
        until("the app moved on to the next song") { engine.loads.lastOrNull()?.id == songs[1].id }
        assertEquals(1, Music.index)
    }

    /** A song that plays to its end is counted as heard to the end, not as skipped. */
    @Test fun aSongThatEndsIsCountedAsHeardOut() {
        val songs = start("heard", at = 2)
        engine.end()
        ui { Music.engineEnded() }
        until("it was counted") { Music.songStats.rows().any { it.first.title == songs[2].title && it.second == 0 && it.third == 1 } }
    }

    /** Repeat one: the song that ended is loaded again, from its very start. */
    @Test fun repeatOnePlaysTheSongAgainFromItsStart() {
        val songs = start("again", at = 1)
        val loadsBefore = engine.loads.size
        ui { Music.repeat = "one" }
        engine.positionMs = 180_000
        engine.end()
        ui { Music.engineEnded() }
        until("the song loaded again") { engine.loads.size > loadsBefore }
        assertEquals(1, Music.index)
        assertEquals(songs[1].id, engine.loads.last().id)
        assertEquals(0L, engine.loads.last().startMs)
        ui { Music.repeat = "off" }
    }
}
