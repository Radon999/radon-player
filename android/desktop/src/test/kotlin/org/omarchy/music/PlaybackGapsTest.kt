package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** More of how the queue and the player behave today, pinned before they're taken out of Music (Phase 9 of
 *  docs/rebuild/RADON_REBUILD_PLAN.md): the parts PlaybackBehaviourTest left open that the move could break. Repeat
 *  all going round in a new order when shuffled, your own copy of a song played in place of a service's, Play next
 *  against Add to queue, the queue as it comes back after a restart, and a stream skipped while it's still being got. */
class PlaybackGapsTest {
    private val engine: FakeEngine by lazy { FakeEngine().also { e -> ui { Music.player = e } } }

    private fun <T> ui(block: () -> T): T = runBlocking(Dispatchers.Main) { block() }

    private fun until(what: String, ms: Long = 8000, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (ok()) return; Thread.sleep(25) }
        throw AssertionError("waited ${ms / 1000} s for: $what (loaded: ${engine.loads.map { it.id }}, index ${Music.index}, queue ${Music.queue.map { it.id }})")
    }

    private fun files(tag: String, n: Int = 3): List<Track> {
        val dir = java.io.File(Harness.ctx.filesDir, "songs-$tag").apply { mkdirs() }
        return (1..n).map { i ->
            val f = java.io.File(dir, "$tag-$i.flac").apply { writeBytes(ByteArray(0)) }
            Track("file-$tag-$i", "file", "Gap $tag $i", "Group $tag", file = f.path)
        }
    }

    private fun start(tag: String, at: Int = 0, n: Int = 3): List<Track> {
        Harness.ctx
        engine
        val songs = files(tag, n)
        ui {
            Music.lyricsToFiles = false
            Music.autoplay = false
            Music.repeat = "off"
            Music.playFrom(songs, at, radio = false)
        }
        until("the song at $at loaded") { engine.loads.lastOrNull()?.id == songs[at].id }
        return songs
    }

    /** Repeat all with the queue shuffled: after the last song it goes round again in a new order (never starting
     *  with the song that just ended). Not shuffled, it goes round in the same order. */
    @Test fun repeatAllGoesRoundInANewOrderWhenShuffled() {
        val songs = start("reshuffle", n = 10)
        try {
            // Not shuffled: round again, as it was
            ui { Music.repeat = "all"; Music.playIndex(9) }
            until("the last song loaded") { engine.loads.lastOrNull()?.id == songs[9].id }
            engine.end()
            ui { Music.engineEnded() }
            until("round to the first") { Music.index == 0 && engine.loads.lastOrNull()?.id == songs[0].id }
            assertEquals("the same order", songs.map { it.id }, Music.queue.map { it.id })
            assertFalse(Music.shuffled)
            // Shuffled: round again in another order
            ui { Music.shuffle() }
            assertTrue(Music.shuffled)
            assertEquals("the song playing stays first", songs[0].id, Music.queue[0].id)
            ui { Music.playIndex(9) }
            val last = ui { Music.queue[9] }
            until("the shuffled queue's last song loaded") { engine.loads.lastOrNull()?.id == last.id }
            val before = Music.queue.map { it.id }
            val loads = engine.loads.size
            engine.end()
            ui { Music.engineEnded() }
            until("round to the front") { Music.index == 0 && engine.loads.size > loads }
            val after = Music.queue.map { it.id }
            assertEquals("the same songs", before.toSet(), after.toSet())
            assertEquals(10, after.size)
            assertNotEquals("in a new order", before, after)
            assertNotEquals("not the song that just ended, twice in a row", last.id, after[0])
            assertEquals("the new first song is the one loaded", after[0], engine.loads.last().id)
            assertTrue("still shuffled", Music.shuffled)
        } finally { ui { Music.repeat = "off"; Music.shuffled = false } }
    }

    /** Your copy first: a service's song you have yourself (the same title and artist) plays from your file, both when
     *  it's picked from a list and when it sat in a queue kept from before. A song you don't have stays the service's. */
    @Test fun aSongYouHaveYourselfPlaysFromYourOwnCopy() {
        Harness.ctx; engine
        val mine = files("owned", 2)
        val others = files("unowned", 2)
        until("the first look at the music folders done") { !Music.local.scanning }
        val had = Music.local.files
        ui { Music.dl.standIn = { t -> Downloader.Ready("http://test/${t.id}") }; Music.keepPlayed = false; Music.lyricsToFiles = false; Music.autoplay = false; Music.repeat = "off" }
        try {
            ui { Music.local.files = had + mine; Music.musicVersion++ }
            val theirs0 = Track("svc-owned-1", "tidal", mine[0].title, mine[0].artist)
            val theirs1 = Track("svc-owned-2", "tidal", mine[1].title, mine[1].artist)
            val notMine = Track("svc-else-1", "tidal", "A song nobody here has", "Somebody else")
            // Picked from a list: your copy is what goes into the queue, and what plays
            ui { Music.playFrom(listOf(theirs0, notMine, others[0]), 0, radio = false) }
            until("your own file loaded") { engine.loads.lastOrNull()?.id == mine[0].id }
            assertEquals(mine[0].file, engine.loads.last().uri)
            assertEquals(listOf(mine[0].id, "svc-else-1", others[0].id), Music.queue.map { it.id })
            assertFalse("a file isn't a stream", Music.streaming)
            // The song you don't have: the service's
            ui { Music.next() }
            until("the service's song loaded") { engine.loads.lastOrNull()?.id == "svc-else-1" }
            assertEquals("http://test/svc-else-1", engine.loads.last().uri)
            assertTrue(Music.streaming)
            // In a queue from before you had it: yours takes its place when its turn comes, with its marks
            ui { Music.queue = Music.queue + theirs1.copy(queued = true); Music.playIndex(3) }
            until("your own file loaded in its place") { engine.loads.lastOrNull()?.id == mine[1].id }
            assertEquals(mine[1].file, engine.loads.last().uri)
            assertEquals(mine[1].id, Music.queue[3].id)
            assertFalse("playing now, no longer waiting in the queue", Music.queue[3].queued)
            assertFalse(engine.loads.any { it.id.startsWith("svc-owned") })
        } finally {
            ui { Music.dl.standIn = null; Music.local.files = had; Music.musicVersion++ }
        }
    }

    /** Add to queue puts songs after the ones you queued before; Play next right after the song playing, ahead of
     *  them. They're marked as queued until their turn comes. */
    @Test fun playNextGoesAheadOfWhatYouQueuedAndAddToQueueBehindIt() {
        val songs = start("enq")
        val extra = files("enq-extra", 4)
        ui { Music.enqueue(listOf(extra[0]), "queue") }
        assertEquals(listOf(songs[0].id, extra[0].id, songs[1].id, songs[2].id), Music.queue.map { it.id })
        ui { Music.enqueue(listOf(extra[1]), "queue") }
        assertEquals(listOf(songs[0].id, extra[0].id, extra[1].id, songs[1].id, songs[2].id), Music.queue.map { it.id })
        ui { Music.enqueue(listOf(extra[2]), "next") }
        assertEquals(listOf(songs[0].id, extra[2].id, extra[0].id, extra[1].id, songs[1].id, songs[2].id), Music.queue.map { it.id })
        ui { Music.enqueue(listOf(extra[3])) }   // (Add to queue is the usual way)
        assertEquals(listOf(songs[0].id, extra[2].id, extra[0].id, extra[1].id, extra[3].id, songs[1].id, songs[2].id), Music.queue.map { it.id })
        assertEquals(listOf(false, true, true, true, true, false, false), Music.queue.map { it.queued })
        assertEquals("nothing changes for the song playing", 0, Music.index)
        assertEquals(songs[0].id, engine.loads.last().id)
        // Its turn comes: it plays, and no longer counts as waiting
        ui { Music.next() }
        until("the song put next loaded") { engine.loads.lastOrNull()?.id == extra[2].id }
        assertEquals(1, Music.index)
        assertEquals(listOf(false, false, true, true, true, false, false), Music.queue.map { it.queued })
        // Picking other songs: the ones still waiting come first, after the song picked
        val other = files("enq-other", 2)
        ui { Music.playFrom(other, 0, radio = false) }
        until("the picked song loaded") { engine.loads.lastOrNull()?.id == other[0].id }
        assertEquals(listOf(other[0].id, extra[0].id, extra[1].id, extra[3].id, other[1].id), Music.queue.map { it.id })
    }

    /** The queue is written down (state.json) as it always was, and after a restart it's back: the same songs, the
     *  same place, Repeat, its name, your other queues; the song is in the player again, not playing until you say. */
    @Test fun theQueueComesBackAfterARestart() {
        Harness.ctx; engine
        val a = files("kept-a"); val b = files("kept-b")
        val withNew = Dj.withNew
        try {
            ui {
                Music.lyricsToFiles = false; Music.autoplay = false; Music.repeat = "off"; Music.multiQueue = true
                Music.listName = "Kept A"; Music.playFrom(a, 0, radio = false)
            }
            until("A's first song loaded") { engine.loads.lastOrNull()?.id == a[0].id }
            ui { Music.listName = "Kept B"; Music.playFrom(b, 1, radio = false) }
            until("B's second song loaded") { engine.loads.lastOrNull()?.id == b[1].id }
            val id = Music.queueId
            assertTrue(id.isNotEmpty())
            ui { Music.repeat = "all"; Music.shuffled = true; Music.saveStateNow() }
            // As it's written
            val file = java.io.File(Harness.ctx.filesDir, "state.json")
            val st = kotlinx.serialization.json.Json.parseToJsonElement(file.readText()).jsonObject
            assertEquals(b.map { it.id }, st["queue"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content })
            assertEquals(1, st["index"]!!.jsonPrimitive.int)
            assertEquals("all", st["repeat"]!!.jsonPrimitive.content)
            assertEquals("true", st["shuffled"]!!.jsonPrimitive.content)
            assertEquals("Kept B", st["queue_name"]!!.jsonPrimitive.content)
            assertEquals(id, st["queue_id"]!!.jsonPrimitive.content)
            val others = st["queues"]!!.jsonArray.map { it.jsonObject }
            assertTrue("the other queue is written too", others.any { it["name"]!!.jsonPrimitive.content == "Kept A" &&
                it["tracks"]!!.jsonArray.map { t -> t.jsonObject["id"]!!.jsonPrimitive.content } == a.map { t -> t.id } })
            assertFalse("no radio: nothing written for it", st.containsKey("radio_from"))
            // The app as it is after being closed: nothing in memory. Then what a start reads back
            val loads = engine.loads.size
            ui {
                Music.queue = emptyList(); Music.index = -1; Music.repeat = "off"; Music.shuffled = false
                Music.queueName = ""; Music.queueId = ""; Music.queues = emptyList()
                Music::class.java.getDeclaredMethod("loadState").apply { isAccessible = true }.invoke(Music)
            }
            assertEquals(b.map { it.id }, Music.queue.map { it.id })
            assertEquals(1, Music.index)
            assertEquals("all", Music.repeat)
            assertTrue(Music.shuffled)
            assertEquals("Kept B", Music.queueName)
            assertEquals(id, Music.queueId)
            assertEquals(listOf("Kept A"), Music.queues.map { it.name })
            assertEquals(a.map { it.id }, Music.queues[0].tracks.map { it.id })
            until("the song put back into the player") { engine.loads.size > loads }
            assertEquals(b[1].id, engine.loads.last().id)
            assertEquals(0L, engine.loads.last().startMs)
            assertFalse("not playing until you say", engine.playWhenReady)
            // …and Play goes on from there
            ui { Music.next() }
            until("Repeat all, at the last but one: on to the last") { engine.loads.lastOrNull()?.id == b[2].id }
            assertTrue(engine.playWhenReady)
        } finally {
            ui { Music.repeat = "off"; Music.shuffled = false; Music.removeOtherQueues(); Music.listName = ""; Music.lyricsToFiles = false; Music.autoplay = false }
            Dj.withNew = withNew
        }
    }

    /** A stream that's slow to come, skipped while it's still being got: when it does come it's let go (it never
     *  reaches the player), and the song skipped to plays. */
    @Test fun aStreamSkippedWhileItIsStillBeingGotNeverPlays() {
        Harness.ctx; engine
        val f = files("slow")
        val slow = Track("slow-1", "tidal", "Slow to come", "Far away")
        val gate = java.util.concurrent.CountDownLatch(1)
        ui {
            Music.dl.standIn = { t -> if (t.id == "slow-1") gate.await(6, java.util.concurrent.TimeUnit.SECONDS); Downloader.Ready("http://test/${t.id}") }
            Music.keepPlayed = false; Music.lyricsToFiles = false; Music.autoplay = false; Music.repeat = "off"
        }
        try {
            ui { Music.playFrom(listOf(f[0], slow, f[1]), 0, radio = false) }
            until("the first song loaded") { engine.loads.lastOrNull()?.id == f[0].id }
            ui { Music.next() }
            Thread.sleep(300)
            assertEquals(1, Music.index)
            assertTrue("it's a stream, being got", Music.streaming)
            assertEquals("nothing new in the player yet", f[0].id, engine.loads.last().id)
            ui { Music.next() }
            until("the song after it loaded") { engine.loads.lastOrNull()?.id == f[1].id }
            gate.countDown()   // now the stream comes, too late
            Thread.sleep(700)
            assertFalse("the skipped stream never reached the player", engine.loads.any { it.id == "slow-1" })
            assertEquals(f[1].id, engine.loads.last().id)
            assertEquals(2, Music.index)
            assertFalse(Music.streaming)
            assertTrue(engine.playWhenReady)
        } finally { gate.countDown(); ui { Music.dl.standIn = null } }
    }
}
