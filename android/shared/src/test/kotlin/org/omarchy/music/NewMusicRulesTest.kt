package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** How new music is chosen to fit you: the seeds, a set's order, whose new releases first. */
class NewMusicRulesTest {
    private fun song(title: String, artist: String) = Track(id = title, source = "file", title = title, artist = artist)
    private fun key(t: Track) = SignalRules.songKey(t.title, t.artist)
    private fun lead(a: String) = statsKey("", a).second
    private fun titles(l: List<Track>) = l.joinToString(" ") { it.title }

    // ---- seeds ----
    private fun cand(name: String, lately: Boolean, plays: Int = 10, full: Int = 0, skips: Int = 0, up: Boolean = false, fav: Boolean = false,
                     loops: Int = 0, phase: String = "") = NewMusicRules.SeedCand(name, lately, plays, full, skips, up, fav, loops, phase)

    @Test fun whatShowsYouLikeASongCounts() {
        val plain = NewMusicRules.liking(cand("plain", true))
        assertTrue("a favourite", NewMusicRules.liking(cand("x", true, fav = true)) > plain)
        assertTrue("More like this", NewMusicRules.liking(cand("x", true, up = true)) > plain)
        assertTrue("on repeat", NewMusicRules.liking(cand("x", true, loops = 6)) > plain)
        assertTrue("heard to the end", NewMusicRules.liking(cand("x", true, full = 10)) > plain)
        assertTrue("an artist you've lately fallen for", NewMusicRules.liking(cand("x", true, phase = HabitRules.NEW_LOVE)) > plain)
        assertTrue("an artist you've left behind", NewMusicRules.liking(cand("x", true, phase = HabitRules.FADED)) < plain)
        assertTrue("mostly skipped", NewMusicRules.liking(cand("x", true, full = 1, skips = 6)) < plain)
    }

    @Test fun seedsComeInTurnsFromLatelyAndFromLongTimeFavourites() {
        repeat(20) { r ->
            val got = NewMusicRules.seeds(listOf(cand("now 1", true), cand("then 1", false), cand("then 2", false), cand("now 2", true)), 4, Random(r))
            assertEquals(4, got.size)
            assertTrue(got.toString(), got[0].startsWith("now") && got[1].startsWith("then") && got[2].startsWith("now") && got[3].startsWith("then"))
        }
        assertEquals("one side runs out: the other fills in", listOf("now", "then 1", "then 2"),
            NewMusicRules.seeds(listOf(cand("now", true), cand("then 1", false, fav = true, up = true, loops = 9), cand("then 2", false, plays = 0)), 3, Random(1))
                .let { listOf(it[0]) + it.drop(1).sorted() })
        assertEquals("nothing but lately", listOf("a", "b"), NewMusicRules.seeds(listOf(cand("a", true), cand("b", true)), 5, Random(2)).sorted())
        assertTrue(NewMusicRules.seeds(emptyList<NewMusicRules.SeedCand<String>>(), 2).isEmpty())
    }

    @Test fun theSongsYouLikeMostAreLikelierSeeds() {
        val cands = listOf(cand("meh", true, plays = 1, skips = 5, full = 1)) + (1..8).map { cand("fine $it", true, plays = 5) } +
            cand("loved", true, plays = 80, full = 30, fav = true, up = true)
        val first = (0 until 300).map { NewMusicRules.seeds(cands, 1, Random(it))[0] }
        assertTrue("loved ${first.count { it == "loved" }}, meh ${first.count { it == "meh" }}", first.count { it == "loved" } > 3 * first.count { it == "meh" })
        assertTrue("not always the same one", first.toSet().size > 2)
    }

    // ---- the order of a set ----
    private val lauv = song("Paris", "Lauv"); private val keane = song("Somewhere", "Keane"); private val sody = song("Nothing", "Sody")
    private val muse = song("Uprising", "Muse")

    @Test fun eachSongFollowsTheOneBeforeTheWayYouPlayThem() {
        val h = Habits(after = mapOf(key(lauv) to listOf(key(muse) to 5)),             // you play Uprising straight after Paris
            afterArtist = mapOf(lead("Muse") to listOf(lead("Keane") to 3)))           // and Keane after Muse
        assertEquals("Paris Uprising Somewhere Nothing", titles(NewMusicRules.flow(listOf(lauv, keane, sody, muse), h)))
    }

    @Test fun aNewSongGoesByItsArtistAmongTheOnesYouPlayInTheSameSittings() {
        val newOne = song("Brand new", "Sody")
        val h = Habits(near = mapOf(lead("Lauv") to listOf(lead("Sody") to 0.8)))
        assertEquals("Paris Brand new Somewhere Uprising", titles(NewMusicRules.flow(listOf(lauv, keane, muse, newOne), h)))
    }

    @Test fun theFirstStaysFirstAndWithNothingKnownTheOrderIsKept() {
        val songs = listOf(lauv, keane, sody, muse)
        assertEquals(songs, NewMusicRules.flow(songs, Habits()))
        assertEquals(songs, NewMusicRules.flow(songs, null))
        val h = Habits(after = mapOf(key(muse) to listOf(key(lauv) to 9)))
        assertEquals("Paris stays first", lauv, NewMusicRules.flow(songs, h)[0])
    }

    @Test fun neverTheSameArtistTwiceInARowWhileAnotherIsLeft() {
        val lauv2 = song("Modern Loneliness", "Lauv")
        val h = Habits(after = mapOf(key(lauv) to listOf(key(lauv2) to 9)))
        assertEquals("Paris Somewhere Modern Loneliness Nothing", titles(NewMusicRules.flow(listOf(lauv, lauv2, keane, sody), h)))
    }

    @Test fun howWellOneFollowsAnother() {
        val h = Habits(after = mapOf(key(lauv) to listOf(key(keane) to 1)), afterArtist = mapOf(lead("Lauv") to listOf(lead("Sody") to 50)),
            near = mapOf(lead("Lauv") to listOf(lead("Sody") to 1.0)))
        val song = NewMusicRules.follows(h, key(lauv), lead("Lauv"), key(keane), lead("Keane"))
        val artist = NewMusicRules.follows(h, key(lauv), lead("Lauv"), key(sody), lead("Sody"))
        assertTrue("the song itself after it beats any artist's ($song, $artist)", song > artist && artist > 0)
        assertEquals(0.0, NewMusicRules.follows(h, key(lauv), lead("Lauv"), key(muse), lead("Muse")), 1e-9)
    }

    // ---- new releases ----
    @Test fun theArtistsYouPlayAndHaveFallenForAreLookedAtFirst() {
        val plays = mapOf("Played" to 300, "Crush" to 40, "Faded" to 400)
        val phase = mapOf("Crush" to HabitRules.NEW_LOVE, "Faded" to HabitRules.FADED)
        val got = NewMusicRules.releaseOrder(listOf("Many songs" to 120, "Played" to 5, "Crush" to 3, "Faded" to 10, "Few songs" to 2),
            { plays[it] ?: 0 }, { phase[it] })
        assertEquals(listOf("Played", "Crush", "Faded", "Many songs", "Few songs"), got)
    }
}
