package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Setlists: which shows in setlist.fm's answer are the artist's, their songs in order with the encores, and what a
 *  playlist of them is called. */
class SetlistRulesTest {
    private val answer = JSON.parseToJsonElement("""{"type":"setlists","itemsPerPage":20,"page":1,"total":4,"setlist":[
        {"id":"older","eventDate":"02-03-2027","artist":{"mbid":"m1","name":"HOAX"},
         "venue":{"name":"Roxy","city":{"name":"Los Angeles","country":{"code":"US","name":"United States"}}},
         "tour":{"name":"The b? Tour"},"url":"https://www.setlist.fm/older",
         "sets":{"set":[{"song":[{"name":"Intro","tape":true},{"name":"Rain"},{"name":"Fire"},{"name":""},{"name":"Gold"}]},
                        {"encore":1,"song":[{"name":"Stars"}]}]}},
        {"id":"newest","eventDate":"14-03-2027","artist":{"mbid":"m1","name":"Hoax"},
         "venue":{"name":"The Fillmore","city":{"name":"San Francisco","country":{"code":"US","name":"United States"}}},
         "tour":{"name":"The b? Tour"},"url":"https://www.setlist.fm/newest",
         "sets":{"set":[{"song":[{"name":"Intro","tape":true},{"name":"Fire","info":"acoustic"},{"name":"Rain"},
                                 {"name":"Wonderwall","cover":{"name":"Oasis"},"with":{"name":"Friend"}}]},
                        {"name":"Acoustic set","song":[{"name":"Gold"}]},
                        {"encore":1,"song":[{"name":"Stars"}]},
                        {"encore":2,"song":[{"name":"Rain (Reprise)"}]}]}},
        {"id":"other-band","eventDate":"20-03-2027","artist":{"mbid":"m2","name":"The Hoaxers"},
         "venue":{"name":"Small Room","city":{"name":"Reno","country":{"name":"United States"}}},"sets":{"set":[{"song":[{"name":"Rain"}]}]}},
        {"id":"no-songs","eventDate":"01-01-2027","artist":{"mbid":"m1","name":"HOAX"},
         "venue":{"name":"Bowl","city":{"name":"Leeds","country":{"name":"United Kingdom"}}},"sets":{"set":[]}}
    ]}""")

    @Test fun theArtistsShowsNewestFirstWithTheirSongsInOrder() {
        val shows = SetlistRules.parse(answer, "HOAX")
        assertEquals("the look-alike band's show isn't theirs; newest first", listOf("newest", "older", "no-songs"), shows.map { it.id })
        val s = shows[0]
        assertEquals("2027-03-14", s.date)
        assertEquals("Sun 14 Mar 2027 · The Fillmore, San Francisco", SetlistRules.line(s))
        assertEquals(listOf("Intro", "Fire", "Rain", "Wonderwall", "Gold", "Stars", "Rain (Reprise)"), s.songs.map { it.name })
        assertEquals("the set each is in: the main set, a named one, then the encores",
            listOf("", "", "", "", "Acoustic set", "Encore", "Encore 2"), s.songs.map { it.part })
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 2), s.songs.map { it.encore })
        assertEquals("Oasis", s.songs[3].cover); assertEquals("Friend", s.songs[3].with); assertEquals("acoustic", s.songs[1].info)
        assertEquals("the tape intro isn't played", listOf("Fire", "Rain", "Wonderwall", "Gold", "Stars", "Rain (Reprise)"), s.playable.map { it.name })
        assertEquals("a song with no name is left out", listOf("Rain", "Fire", "Gold", "Stars"), shows[1].playable.map { it.name })
        assertEquals("The b? Tour · 6 songs · United States", SetlistRules.sub(s))
        assertEquals("no songs listed yet · United Kingdom", SetlistRules.sub(shows[2]))
    }

    @Test fun nothingFoundIsAnEmptyList() {
        assertTrue(SetlistRules.parse(JSON.parseToJsonElement("""{"code":404,"message":"not found"}"""), "HOAX").isEmpty())
        assertTrue(SetlistRules.parse(null, "HOAX").isEmpty())
        assertNull(SetlistRules.lately(SetlistRules.parse(answer, "HOAX").filter { it.id == "no-songs" }))
    }

    @Test fun thePlaylistsName() {
        assertEquals("HOAX · The Fillmore, San Francisco · 14 Mar 2027", SetlistRules.playlistName("HOAX", "The Fillmore", "San Francisco", "2027-03-14"))
        assertEquals("HOAX · Leeds · 1 Jan 2027", SetlistRules.playlistName(" HOAX ", "", "Leeds", "2027-01-01"))
    }

    @Test fun whatTheyHaveBeenPlayingOnThisTour() {
        val l = SetlistRules.lately(SetlistRules.parse(answer, "HOAX"))!!
        assertEquals("the two shows of the tour (the one with no songs doesn't count)", 2, l.shows)
        assertEquals("in the order they usually come, the encore last",
            listOf("Rain", "Fire", "Wonderwall", "Gold", "Stars", "Rain (Reprise)"), l.songs.map { it.name })
        assertEquals(listOf(2, 2, 1, 2, 2, 1), l.songs.map { it.played })
        assertEquals("Stars", l.songs.single { it.encore > 0 && it.played == 2 }.name)
    }

    @Test fun aShowThatHappenedOpensItsOwnSetlist() {
        val shows = SetlistRules.parse(answer, "HOAX")
        assertEquals("newest", SetlistRules.forShow("2027-03-14", "2027-03-20", shows)?.id)
        assertEquals("a day off (another time zone) is the same show", "older", SetlistRules.forShow("2027-03-03", "2027-03-20", shows)?.id)
        assertEquals("a coming show: what they've been playing", "lately:newest", SetlistRules.forShow("2027-04-01", "2027-03-20", shows)?.id)
    }

    @Test fun whatIsAsked() {
        assertEquals("https://api.setlist.fm/rest/1.0/search/setlists?artistName=half%E2%80%A2alive&p=1", SetlistRules.url(" half•alive "))
        assertEquals(mapOf("x-api-key" to "KEY", "Accept" to "application/json"), SetlistRules.headers(" KEY "))
        assertTrue(SetlistRules.why(403).startsWith("setlist.fm didn't accept the key"))
    }
}
