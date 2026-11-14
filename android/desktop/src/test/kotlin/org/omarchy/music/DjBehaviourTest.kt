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
        Pictures.init(c)
        Dj.withNew = false   // only the songs given: no kind of set goes looking on a service (no network in a test)
        c
    }

    /** On the app's own thread, as the screens call it. */
    fun <T> ui(block: () -> T): T = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Main) { block() }

    /** Waits (8 s at most) for something the app does a moment later, on its own threads. */
    fun until(what: String, ok: () -> Boolean) {
        val end = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < end) { if (ok()) return; Thread.sleep(25) }
        throw AssertionError("waited 8 s for: $what (index ${Music.index} of ${Music.queue.size}, DJ ${Music.dj.on}, busy ${Music.djBusy})")
    }

    /** Song files of the test's own (empty: nothing is decoded), each by its own artist: nothing is looked up. */
    fun files(tag: String, n: Int): List<Track> {
        val dir = java.io.File(ctx.filesDir, "songs-$tag").apply { mkdirs() }
        return (1..n).map { i ->
            val f = java.io.File(dir, "$tag-$i.flac").apply { writeBytes(ByteArray(0)) }
            Track("file-$tag-$i", "file", "Filed $tag $i", "Band $tag $i", file = f.path)
        }
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

    /** Every kind of set belongs to one of the four types of set, and only one; no kind was added or taken away. */
    @Test fun everyKindOfSetHasItsType() {
        assertEquals(Dj.SetType.TIME_AND_MOOD, Dj.typeOf("hour"))
        assertEquals(Dj.SetType.TIME_AND_MOOD, Dj.typeOf("repeat"))
        assertEquals(Dj.SetType.TIME_AND_MOOD, Dj.typeOf("mood:Sad"))
        // Only the high-energy moods share a set: every other mood is a set of its own (the owner, 2026-11-14)
        assertEquals(listOf("mood:Energetic+Party+Hype", "mood:Happy", "mood:Chill", "mood:Calm", "mood:Dreamy", "mood:Focus",
            "mood:Romantic", "mood:Sad", "mood:Dark", "mood:Intense"), Dj.MOOD_KINDS)
        assertEquals(Dj.SetType.NOSTALGIA_PULL, Dj.typeOf("throwback"))
        assertEquals(Dj.SetType.NOSTALGIA_PULL, Dj.typeOf("rediscover"))
        assertEquals(Dj.SetType.NOSTALGIA_PULL, Dj.typeOf("album"))
        assertEquals(Dj.SetType.SONIC_BRIDGE, Dj.typeOf("like"))
        assertEquals(Dj.SetType.SONIC_BRIDGE, Dj.typeOf("genre"))
        assertEquals(Dj.SetType.FRESH_DROP, Dj.typeOf("fresh"))
        assertEquals(Dj.SetType.FRESH_DROP, Dj.typeOf("unplayed"))
        val kinds = Dj.ORDER + Dj.FROM_ORDER + Dj.FROM_NEW_ORDER + "ask"
        // 29 as in 3.2.28 (26 and 3), and three more since the mood sets were split (7 mood sets then, 10 now)
        assertEquals("the kinds there are", 26 + 3 + 3, (Dj.ORDER + listOf("unplayed", "artist", "mix")).distinct().size)
        val byType = kinds.distinct().groupBy { Dj.typeOf(it) }
        assertEquals("all four types have kinds", 4, byType.size)
    }

    /** A set queued behind a song by the same artist as its first song says so before its line (the thread between
     *  them); with nothing known to link the two songs, and for the first set, its line is as it was. Only what the
     *  songs' tags share is ever said. */
    @Test fun theThreadFromTheSongBeforeIsSaidOnlyWhenThereIsOne() {
        Harness.ctx
        Dj.playFrom("the test songs", Harness.songs(40, from = 600))
        Dj.clock = { java.time.LocalDateTime.of(2026, 11, 11, 14, 0) }   // a Wednesday afternoon: no word about the day
        val b = try { Dj.build(kind = "mix")!! } finally { Dj.clock = { java.time.LocalDateTime.now() } }
        assertEquals("", b.opener)
        val first = b.songs[0]
        val sameArtist = Track("before-1", "ytm", "The song before", first.artist)
        val linked = Dj.bridged(b, sameArtist)
        assertTrue(linked.line, linked.line.endsWith(b.line) && linked.line.length > b.line.length)
        assertTrue(linked.line, linked.line.removeSuffix(b.line).contains(first.artist))
        assertEquals(b.songs, linked.songs)
        val stranger = Track("before-2", "ytm", "Another song before", "Somebody Else Entirely")
        assertEquals("nothing known links them: nothing is claimed", b.line, Dj.bridged(b, stranger).line)
        assertEquals("no song before it", b.line, Dj.bridged(b, null).line)
        val opening = Dj.build(kind = "mix", first = true)!!
        assertEquals("the first set has its hello", opening.line, Dj.bridged(opening, Track("before-3", "ytm", "x", opening.songs[0].artist)).line)
        Dj.discard(b.id); Dj.discard(opening.id)
    }

    /** Stays: songs the DJ played aren't picked again while they're among its last ones (the 300-song gap, fewer
     *  when it plays from few songs), and they're kept across restarts (Prefs, dj_recent_songs). */
    @Test fun songsTheDjPlayedDontComeBackYet() {
        Harness.ctx
        val pool = Harness.songs(60, from = 200)
        Dj.playFrom("the test songs", pool)
        val before = Prefs.text("dj_recent_songs").split('\n').count { it.isNotBlank() }
        val played = pool.take(20)
        Dj.played(played)
        assertEquals(before + 20, Prefs.text("dj_recent_songs").split('\n').count { it.isNotBlank() })
        val keys = played.map { songKey(it) }.toSet()
        assertTrue(Dj.used.containsAll(keys))
        var built = 0
        repeat(30) {
            val b = Dj.build(kind = "mix") ?: return@repeat
            built++
            assertTrue(b.songs.none { songKey(it) in keys })
        }
        assertTrue("sets were made ($built)", built > 5)
    }

    /** Stays: every kind of set gets its turn before one comes back. Left to pick for itself, the DJ never makes a
     *  kind again until each other kind it can make from these songs has been made since. */
    @Test fun kindsOfSetTakeTurns() {
        Harness.ctx
        Dj.playFrom("the test songs", Harness.songs(60, from = 300))
        val kinds = ArrayList<String>()
        repeat(18) { Dj.build()?.let { kinds += it.kind } }
        assertTrue("sets were made (${kinds.size})", kinds.size >= 12)
        val n = kinds.distinct().size
        assertTrue("more than one kind can be made from them ($kinds)", n >= 2)
        val last = kinds.takeLast(2 * n)
        for (i in 0..last.size - n) assertEquals("each kind once in any $n sets in a row: $kinds", n, last.subList(i, i + n).distinct().size)
    }

    /** The DJ never names itself (it was: the first set's hello always said "It's your DJ" or "Your DJ here"; the
     *  hellos are rewritten), and a break is 45 words at most, as said out loud. */
    @Test fun theFirstLineNeverNamesTheDjAndABreakIs45WordsAtMost() {
        Harness.ctx
        Dj.playFrom("the test songs", Harness.songs(40, from = 400))
        fun words(b: Dj.Built) = Spoken.humanize(b.line + " " + b.fact, filler = 0.0).split(Regex("\\s+")).count { it.isNotBlank() }
        repeat(40) {
            val b = Dj.build(kind = "mix", first = true)
            assertNotNull(b)
            assertFalse(b!!.line, Regex("\\bDJ\\b").containsMatchIn(b.line))
            assertTrue("${words(b)} words: ${b.line} ${b.fact}", words(b) <= Dj.MOST_WORDS)
            Dj.discard(b.id)
        }
        for (kind in listOf("mix", "onehit", "unplayed")) repeat(30) {
            val b = Dj.build(kind = kind) ?: return@repeat
            assertTrue("${words(b)} words: ${b.line} ${b.fact}", words(b) <= Dj.MOST_WORDS)
            Dj.discard(b.id)
        }
    }

    /** C-1, fixed (it was: the line was written, naming the set's first song, and only then were the songs still to
     *  come taken out of the set, so the DJ could name a song that wasn't in the set it introduced; this test found
     *  such a line within a few sets). Now every song a line names is in its set, the first one named is the set's
     *  first song, and with half the songs still to come every set is still made, whole. */
    @Test fun aLineOnlyNamesSongsItsSetHas() {
        Harness.ctx
        val pool = Harness.songs(60)
        Dj.playFrom("the test songs", pool)
        val coming = pool.take(30).map { songKey(it) }.toSet()
        var built = 0
        var naming = 0
        for (i in 0 until 400) {
            val b = Dj.build(kind = "onehit", coming = coming) ?: continue
            built++
            val titles = b.songs.map { it.title }
            val said = named.findAll(b.line).map { it.groupValues[1] }.toList()
            assertTrue("the line names a song its set doesn't have: ${b.line} · $titles", said.all { it in titles })
            if (said.isNotEmpty()) { naming++; assertEquals(b.line, titles[0], said[0]) }
            assertEquals("a whole set, though half the songs are still to come", Dj.SET, b.songs.size)
        }
        assertEquals("every set was made", 400, built)
        assertTrue("lines that name a song were among them ($naming)", naming > 50)
    }

    /** C-4, fixed (it was: a set that was only made, never queued or played, had already used up its kind's turn: the
     *  kind was written to the history when the set was built). Now it's only held: written when the set is played,
     *  forgotten when the set is let go. */
    @Test fun aSetsKindHasItsTurnOnlyWhenTheSetIsPlayed() {
        Harness.ctx
        Dj.playFrom("the test songs", Harness.songs(40, from = 500))
        fun times() = NoRepeat.decode(Prefs.text("dj_kind_history")).count { it == "mix" }
        Thread.sleep(1500)   // what the other tests wrote, settled (it's kept on the app's own thread, a moment later)
        val before = times()
        val letGo = Dj.build(kind = "mix")
        val played = Dj.build(kind = "mix")
        assertNotNull(letGo); assertNotNull(played)
        assertTrue("each set has a number of its own", letGo!!.id != played!!.id && letGo.id > 0 && played.id > 0)
        Thread.sleep(1500)
        assertEquals("made, not played: nothing written", before, times())
        Dj.discard(letGo.id)
        Dj.commit(played.id)
        var kept = false
        for (i in 0 until 50) { if (times() == before + 1) { kept = true; break }; Thread.sleep(100) }
        assertTrue("the set played has had its kind's turn (${times()} against $before)", kept)
        Dj.commit(played.id); Dj.commit(letGo.id)   // again, or one let go: nothing more
        Thread.sleep(1500)
        assertEquals(before + 1, times())
    }
}
