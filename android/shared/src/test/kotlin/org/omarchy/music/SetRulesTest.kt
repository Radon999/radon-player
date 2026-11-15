package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random

/** The DJ's rules for a set that need nothing but the songs. */
class SetRulesTest {
    private fun song(title: String, artist: String = "Artist $title") = Track(id = title, source = "file", title = title, artist = artist)
    private fun titles(l: List<Track>?) = l?.joinToString(" ") { it.title }

    // ---- the mood arc ----
    private val moods = mapOf("a" to "Calm", "b" to "Hype", "c" to "Sad", "d" to "Happy", "e" to "Party", "f" to "Chill")
    private fun arc(vararg t: String) = titles(SetRules.arc(t.map { song(it) }) { moods[it.title] })

    @Test fun fromACalmFirstSongTheSetBuildsUp() = assertEquals("a c f d e b", arc("a", "b", "c", "d", "e", "f"))
    @Test fun fromALoudFirstSongItEasesDown() = assertEquals("b e d f c a", arc("b", "a", "c", "d", "e", "f"))
    @Test fun aSongWithNoMoodGoesInTheMiddle() = assertEquals("a c x d b", arc("a", "b", "x", "c", "d"))
    @Test fun withFewMoodsKnownTheOrderIsLeft() {
        assertEquals("a x y b z", arc("a", "x", "y", "b", "z"))
        assertEquals("b a", arc("b", "a"))
    }

    // ---- no artist twice in a row ----
    private fun spread(cap: Boolean, vararg s: Pair<String, String>) = titles(SetRules.spread(s.map { song(it.first, it.second) }, cap))

    @Test fun noArtistTwiceInARow() =
        assertEquals("1 3 2 4", spread(false, "1" to "Muse", "2" to "Muse", "3" to "Coldplay", "4" to "Coldplay"))

    @Test fun atMostTwoOfAnArtistButInAnArtistsOwnSet() {
        val s = arrayOf("1" to "Muse", "2" to "Muse", "3" to "Muse", "4" to "Coldplay", "5" to "Keane", "6" to "Travis")
        assertEquals("1 4 2 5 6", spread(true, *s))
        assertEquals("the artist's own set keeps them all", "1 4 2 5 3 6", spread(false, *s))
    }

    @Test fun tooFewLeftAndTheSetStaysAsItWas() =
        assertEquals("1 5 2 3 4", spread(true, "1" to "Muse", "2" to "Muse", "3" to "Muse", "4" to "Muse", "5" to "Keane"))

    @Test fun featuringsAndCapitalsAreTheSameArtist() =
        assertEquals("1 3 2", spread(false, "1" to "Muse", "2" to "MUSE, Someone", "3" to "Keane"))

    // ---- a line and its songs ----
    private val set = listOf(song("Yellow", "Coldplay"), song("Starlight", "Muse"), song("Clocks", "Coldplay, Someone"), song("Hysteria", "Muse"), song("Bedshaped", "Keane"))

    @Test fun aSongTheLineDoesntNameCanGo() {
        assertSame(set, SetRules.without(set, "Starting with “Yellow”.") { false })
        assertEquals("Yellow Starlight Clocks Hysteria", titles(SetRules.without(set, "Starting with “Yellow”.") { it.title == "Bedshaped" }))
    }

    @Test fun aSongOrAnArtistTheLineNamesCantGo() {
        assertNull(SetRules.without(set, "Starting with “Yellow”.") { it.title == "Yellow" })
        assertNull("its artist is named", SetRules.without(set, "More Keane and Muse.") { it.title == "Bedshaped" })
        assertNull("the lead artist of a song with a featuring", SetRules.without(set, "Some Coldplay now.") { it.title == "Clocks" })
    }

    @Test fun fewerThanThreeLeftIsNoSet() =
        assertNull(SetRules.without(set, "Here we go.") { it.artist.startsWith("Coldplay") || it.title == "Hysteria" })

    // ---- the songs kept out ----
    @Test fun theLastThreeHundredOrFewerFromAShortPlaylist() {
        assertEquals(300, SetRules.window(null, 300, 6))
        assertEquals(300, SetRules.window(5000, 300, 6))
        assertEquals(38, SetRules.window(50, 300, 6))
        assertEquals("twelve songs or fewer: none is kept out", 0, SetRules.window(12, 300, 6))
        assertEquals(0, SetRules.window(3, 300, 6))
    }

    @Test fun pickingLeavesOutWhatWasPlayedAndTakesNoSongTwice() {
        val pool = (1..30).map { song("s$it") } + song("s1")
        val got = SetRules.pick(pool, 6, setOf(songKey(song("s2"))), Random(1))
        assertEquals(6, got.size)
        assertEquals(6, got.map { songKey(it) }.toSet().size)
        assertTrue(got.none { it.title == "s2" })
        assertEquals("all there are, when fewer than asked", 2, SetRules.pick(pool.take(3), 6, setOf(songKey(song("s2"))), Random(1)).size)
    }

    // ---- whose turn ----
    private val order = listOf("repeat", "fresh", "hour", "throwback")

    @Test fun theKindPlayedLongestAgoIsFirst() {
        val tries = SetRules.kindsToTry(order, listOf("hour", "repeat", "throwback", "fresh", "repeat"), { false }, null, Random(1))
        assertEquals(listOf("hour", "throwback", "fresh", "repeat"), tries)
    }

    @Test fun aKindNeverPlayedTakesAPlaceAmongTheRestWhichKeepTheirOrder() {
        val places = HashSet<Int>()
        repeat(40) { seed ->
            val tries = SetRules.kindsToTry(order, listOf("repeat", "fresh", "hour"), { false }, null, Random(seed))
            assertEquals("seed $seed", listOf("repeat", "fresh", "hour"), tries - "throwback")
            places += tries.indexOf("throwback")
        }
        assertTrue("not always the same place: $places", places.size > 1)
    }

    @Test fun kindsThatCouldntMakeASetComeAfterAndTheOnePlayingLast() {
        val h = listOf("repeat", "fresh", "hour", "throwback")
        assertEquals(listOf("fresh", "hour", "throwback", "repeat"), SetRules.kindsToTry(order, h, { it == "repeat" }, null, Random(1)))
        assertEquals(listOf("repeat", "hour", "throwback", "fresh"), SetRules.kindsToTry(order, h, { false }, "fresh", Random(1)))
        assertEquals(listOf("hour", "throwback", "repeat", "fresh"), SetRules.kindsToTry(order, h, { it == "repeat" }, "fresh", Random(1)))
    }

    @Test fun yourFavouritesFromAPlaylistIsTheSameKindAsMostPlayed() {
        assertEquals("mostplayed", SetRules.canon("loved"))
        assertEquals("mostplayed", SetRules.canon("favorites"))
        assertEquals(listOf("hour", "loved"), SetRules.kindsToTry(listOf("loved", "hour"), listOf("hour", "mostplayed"), { false }, null, Random(1)))
    }

    // ---- the part of the day ----
    @Test fun theDayAndThePartOfIt() {
        fun at(h: Int) = SetRules.timeKey(LocalDateTime.of(2026, 11, 14, h, 30))
        assertEquals("SATURDAY night", at(4)); assertEquals("SATURDAY morning", at(5)); assertEquals("SATURDAY morning", at(11))
        assertEquals("SATURDAY afternoon", at(12)); assertEquals("SATURDAY afternoon", at(16)); assertEquals("SATURDAY evening", at(17))
        assertEquals("SATURDAY evening", at(22)); assertEquals("SATURDAY night", at(23))
    }
}
