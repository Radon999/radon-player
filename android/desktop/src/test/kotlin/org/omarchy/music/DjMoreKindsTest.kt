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
}
