package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The kinds of set that go by what you skip and hear out, and by the songs' moods: each by its own rule, playing
 *  from a list of the test's own. */
class DjMoreKindsTest {
    private fun <T> from(name: String, songs: List<Track>, block: () -> T): T {
        Harness.ctx
        Dj.historyStandIn = { emptyList() }
        Dj.retaste()
        Dj.playFrom(name, songs)
        try { return block() } finally { Dj.historyStandIn = null; Dj.retaste(); Dj.playFrom(null, null) }
    }

    /** A set of kind, made a few times over: always made, and only of the songs meant. */
    private fun only(kind: String, meant: List<Track>) {
        val keys = meant.map { songKey(it) }.toSet()
        repeat(8) {
            val b = Dj.build(kind = kind)
            assertNotNull("a $kind set can be made", b)
            try {
                assertEquals(kind, b!!.kind)
                assertTrue("${b.songs.size} songs", b.songs.size in 3..Dj.SET)
                assertTrue("$kind: ${b.songs.map { it.title }}", b.songs.all { songKey(it) in keys })
                assertEquals("no song twice", b.songs.size, b.songs.map { songKey(it) }.toSet().size)
            } finally { Dj.discard(b!!.id) }
        }
    }

    private fun songs(group: String) = (1..8).map { Track("morekinds-$group-$it", "ytm", "$group song $it", "$group band $it") }

    @Test fun notSkippedAndSecondChancesGoByWhatYouHeardOutAndWhatYouSkip() {
        Harness.ctx
        val heardOut = songs("Heardout"); val skipped = songs("Skipped"); val plain = songs("Plain")
        heardOut.forEach { t -> repeat(2) { Music.songStats.heardOut(t) } }
        skipped.forEach { t -> repeat(4) { Music.songStats.skipped(t) }; Music.songStats.heardOut(t) }
        from("the skip test list", heardOut + skipped + plain) {
            only("neverskip", heardOut)
            only("second", skipped)
        }
    }

    @Test fun aMoodsSetIsOnlySongsOfThatMoodAndTheLoudMoodsShareOne() {
        Harness.ctx
        val files = Harness.files("moody", 22)
        val sad = files.subList(0, 8); val happy = files.subList(8, 16); val hype = files.subList(16, 19); val party = files.subList(19, 22)
        for ((mood, group) in listOf("Sad" to sad, "Happy" to happy, "Hype" to hype, "Party" to party)) {
            Harness.ui { Moods.pick(group, mood) }
            Harness.until("the songs given the mood $mood have it") { group.all { Moods.of(it) == mood } }
        }
        from("the mood test list", files) {
            only("mood:Sad", sad)
            only("mood:Happy", happy)
            only("mood:Energetic+Party+Hype", hype + party)
        }
    }

    private fun file(id: String, title: String, artist: String, album: String = "") =
        Track("file-$id", "file", title, artist, album = album, file = "/music/test/$id.flac")

    /** The year and track number a song's file would say: here, what the test says of it. */
    private fun <T> withTags(tags: Map<String, Pair<Int, Int>>, block: () -> T): T {
        Dj.tagStandIn = { t -> tags[t.id] }
        try { return block() } finally { Dj.tagStandIn = null }
    }

    @Test fun aDecadeTripIsSongsOfOneDecadeAndSaysWhichOne() {
        Harness.ctx
        val eighties = (1..12).map { file("eighties-$it", "Eighties song $it", "Eighties band $it") }
        val noughties = (1..12).map { file("noughties-$it", "Noughties song $it", "Noughties band $it") }
        val tags = eighties.associate { it.id to (1985 to 0) } + noughties.associate { it.id to (2003 to 0) }
        withTags(tags) {
            from("the decade test list", eighties + noughties) {
                val seen = HashSet<String>()
                repeat(10) {
                    val b = Dj.build(kind = "decade")
                    assertNotNull("a decade set can be made", b)
                    val old = b!!.songs.all { it.title.startsWith("Eighties") }
                    val newer = b.songs.all { it.title.startsWith("Noughties") }
                    assertTrue("one decade only: ${b.songs.map { it.title }}", old || newer)
                    assertTrue(b.line, b.line.contains(if (old) "the 80s" else "the 2000s"))
                    assertTrue("${b.songs.size} songs", b.songs.size in 3..Dj.SET)
                    seen += if (old) "80s" else "2000s"
                    Dj.commit(b.id)   // played: the other decade has its turn next
                }
                assertEquals("both decades came up", setOf("80s", "2000s"), seen)
            }
        }
    }

    @Test fun anAlbumMomentIsThreeInARowInTheAlbumsOrder() {
        Harness.ctx
        val record = (1..6).map { file("record-$it", "Record song $it", "The Record Makers", album = "The Test Record") }
        val others = (1..10).map { file("loose-$it", "Loose song $it", "Loose band $it") }
        val tags = record.withIndex().associate { (i, t) -> t.id to (1999 to i + 1) }
        withTags(tags) {
            from("the album test list", (record + others).shuffled()) {
                repeat(8) {
                    val b = Dj.build(kind = "album")
                    assertNotNull("an album moment can be made", b)
                    try {
                        val numbers = b!!.songs.map { it.title.removePrefix("Record song ").toIntOrNull() }
                        assertEquals("three songs: ${b.songs.map { it.title }}", 3, numbers.size)
                        assertTrue("of the album: ${b.songs.map { it.title }}", numbers.all { it != null })
                        assertEquals("in a row, in the album's order", listOf(numbers[0]!!, numbers[0]!! + 1, numbers[0]!! + 2), numbers)
                        assertTrue(b.line, b.line.contains("The Test Record"))
                    } finally { Dj.discard(b!!.id) }
                }
            }
        }
    }

    @Test fun anArtistSpotlightIsOneArtistYouHaveSeveralSongsOf() {
        Harness.ctx
        val theirs = (1..7).map { file("spot-$it", "Spotlit song $it", "The Spotlit") }
        val others = (1..10).map { file("unlit-$it", "Unlit song $it", "Unlit band $it") }
        from("the spotlight test list", theirs + others) {
            repeat(8) {
                val b = Dj.build(kind = "spotlight")
                assertNotNull("a spotlight can be made", b)
                try {
                    assertTrue("${b!!.songs.size} songs", b.songs.size in 4..5)
                    assertTrue("one artist: ${b.songs.map { it.artist }}", b.songs.all { it.artist == "The Spotlit" })
                    assertTrue(b.line, b.line.contains("The Spotlit"))
                } finally { Dj.discard(b!!.id) }
            }
        }
    }
}
