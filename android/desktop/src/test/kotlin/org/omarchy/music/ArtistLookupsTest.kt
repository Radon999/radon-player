package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** An artist's page: the service asked for answers first, a pair is found by its first name, and the songs come from
 *  the next service when the first has none. Services of the test's own stand in for Apple Music and YouTube Music. */
class ArtistLookupsTest {
    private class TestService(override val key: String, val knows: Map<String, ArtistInfo>) : Source {
        val askedFor = ArrayList<String>()
        override val name = "The test's service"
        override val hint = ""
        override val signedIn = false
        override fun search(q: String): List<Track> = emptyList()
        override fun find(q: String): Found = Found(songs = emptyList(), albums = emptyList())
        override fun myPlaylists(): List<Album> = emptyList()
        override fun playlist(id: String): List<Track> = emptyList()
        override fun likedIds(): Set<String> = emptySet()
        override fun rate(t: Track, like: Boolean) {}
        override fun album(id: String): List<Track> = emptyList()
        override fun artist(name: String): ArtistInfo? { askedFor += name; return knows[name] }
        override fun radio(t: Track): List<Track> = emptyList()
    }

    @Test fun thePageComesFromTheServiceAskedForAndItsSongsFromTheNext() {
        Harness.ctx
        val pair = "Lookups Test One & Lookups Test Two"
        val top = listOf(Track("al-top", "ytm", "Their Best Song", pair))
        val apple = TestService("apple", mapOf("Lookups Test One" to ArtistInfo("Lookups Test One", "apple-picture", emptyList(), emptyList())))
        val ytm = TestService("ytm", mapOf(pair to ArtistInfo(pair, "ytm-picture", top, emptyList())))
        val hadApple = Music.sources["apple"]; val hadYtm = Music.sources["ytm"]
        try {
            Harness.ui { Music.sources["apple"] = apple; Music.sources["ytm"] = ytm }
            val info = ArtistLookups.info(pair, prefer = "apple")!!
            assertEquals("the pair, then its first name", listOf(pair, "Lookups Test One"), apple.askedFor)
            assertEquals("the page is the asked-for service's", "apple", info.source)
            assertEquals("apple-picture", info.thumb)
            assertEquals("its songs from the next service, which has some", listOf("al-top"), info.top.map { it.id })
            assertEquals("ytm", info.topSource)
            assertNull("nobody knows them: no page", ArtistLookups.info("Lookups Test Nobody", prefer = "apple"))
        } finally {
            Harness.ui {
                if (hadApple != null) Music.sources["apple"] = hadApple else Music.sources.remove("apple")
                if (hadYtm != null) Music.sources["ytm"] = hadYtm else Music.sources.remove("ytm")
            }
        }
    }
}
