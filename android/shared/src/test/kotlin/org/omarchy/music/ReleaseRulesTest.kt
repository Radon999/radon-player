package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A single that is on an album or an EP already is left out on a service; the song is found on its release. */
class ReleaseRulesTest {
    private fun t(title: String, album: String = "", artist: String = "HOAX") = Track("id-$title-$album", title = title, artist = artist, album = album)
    private fun rel(id: String, title: String, vararg songs: String) = Album(id, title) to songs.map { t(it, title) }

    @Test fun aSingleOnAnEpOrAnAlbumIsLeftOut() {
        val folded = ReleaseRules.foldedSingles(listOf(
            rel("ep", "b? Sides", "into the blackhole", "clouds", "deleted scenes", "end credits"),
            rel("album", "b?", "Grow", "more than you know", "Clouds"),
            rel("s1", "clouds", "clouds"),
            rel("s2", "Grow", "Grow (Single Version)"),
            rel("s3", "Moon Moon Baby", "Moon Moon Baby"),
            rel("s4", "Grow (Live)", "Grow (Live)")))
        assertEquals("the singles on a release; the EP stays though the album has its song; a live one is its own",
            setOf("s1", "s2"), folded)
    }

    @Test fun withoutABiggerReleaseNothingIsLeftOut() {
        assertTrue(ReleaseRules.foldedSingles(listOf(rel("s1", "clouds", "clouds"), rel("s2", "Clouds", "clouds"))).isEmpty())
        assertTrue(ReleaseRules.foldedSingles(emptyList()).isEmpty())
        assertTrue("a release whose songs aren't known is left alone", ReleaseRules.foldedSingles(listOf(Album("x", "clouds") to emptyList())).isEmpty())
    }

    @Test fun aSearchShowsTheReleasesVersionOfASong() {
        val found = Found(
            listOf(t("clouds", "clouds"), t("clouds", "b? Sides"), t("Moon Moon Baby", "Moon Moon Baby"), t("Clouds", "Clouds", artist = "Someone Else"),
                t("Grow", "b?"), t("Grow (Live)", "Grow (Live)")),
            albums = listOf(Album("1", "clouds", "HOAX"), Album("2", "b? Sides", "HOAX"), Album("3", "Clouds", "Someone Else"), Album("4", "Moon Moon Baby", "HOAX")))
        val out = ReleaseRules.search(found)
        assertEquals(listOf("clouds|b? Sides", "Moon Moon Baby|Moon Moon Baby", "Clouds|Clouds", "Grow|b?", "Grow (Live)|Grow (Live)"),
            out.songs.map { "${it.title}|${it.album}" })
        assertEquals("the single isn't listed; another artist's of that name is", listOf("2", "3", "4"), out.albums.map { it.id })
    }

    @Test fun aReleaseSaysWhereItsSongsAre() {
        val ep = listOf("into the blackhole", "clouds", "deleted scenes", "end credits")
        assertEquals("STREAMING", ReleaseRules.whereMark(ep, emptyList()))
        assertEquals("LOCAL", ReleaseRules.whereMark(ep, listOf("Into The Blackhole", "Clouds", "Deleted Scenes", "End Credits")))
        assertEquals("BOTH · 2 LOCAL · 2 STREAMING", ReleaseRules.whereMark(ep, listOf("into the blackhole", "deleted scenes (Single Version)")))
        assertEquals("not known yet how many it has: what you have is local", "LOCAL", ReleaseRules.whereMark(null, listOf("clouds")))
        assertEquals("yours under other names than the service's: still yours", "LOCAL", ReleaseRules.whereMark(ep, listOf("Something Else")))
    }

    @Test fun aSearchWithNoReleaseVersionsIsLeftAsItIs() {
        val found = Found(listOf(t("clouds", "clouds"), t("Grow", "")), albums = listOf(Album("1", "clouds", "HOAX")))
        assertEquals(found, ReleaseRules.search(found))
    }
}
