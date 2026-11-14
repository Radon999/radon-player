package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A player that plays nothing: it notes what it was told (the songs loaded, play and pause), for the tests. */
class FakeEngine : Engine {
    class Load(val id: String, val uri: String, val startMs: Long)
    val loads = java.util.concurrent.CopyOnWriteArrayList<Load>()
    @Volatile var pauses = 0
    @Volatile override var positionMs = 0L
    @Volatile override var durationMs = 0L
    @Volatile override var state = Engine.IDLE
    @Volatile override var volume = 1f
    @Volatile override var playWhenReady = false
    @Volatile private var uri: String? = null
    override val isPlaying get() = playWhenReady && state == Engine.READY
    override val hasItem get() = uri != null
    override val currentUri get() = uri
    override val audioRate = 0
    override val audioBits = 0
    override fun play() { playWhenReady = true }
    override fun pause() { pauses++; playWhenReady = false }
    override fun seekTo(ms: Long) { positionMs = ms }
    override fun load(item: PlayItem, startMs: Long) { loads += Load(item.id, item.uri, startMs); uri = item.uri; positionMs = startMs; state = Engine.READY }
    override fun setSpeed(speed: Float, pitch: Float) {}
    override fun tail(uri: String, startMs: Long): Engine? = null
    override fun onceSounding(run: () -> Unit) {}
    override fun release() {}
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

    private fun until(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < end) { if (ok()) return; Thread.sleep(25) }
        throw AssertionError("waited 8 s for: $what (loaded: ${engine.loads.map { it.id }}, index ${Music.index})")
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
