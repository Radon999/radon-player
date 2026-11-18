package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Concert dates: what is asked, and which of the listed shows are the artist's. */
class ConcertRulesTest {
    private val answer = JSON.parseToJsonElement("""{"_embedded":{"events":[
        {"name":"HOAX: The b? Tour","url":"https://example.test/1","dates":{"start":{"localDate":"2027-03-14"}},
         "_embedded":{"venues":[{"name":"The Fillmore","city":{"name":"San Francisco"},"country":{"name":"United States Of America"}}],"attractions":[{"name":"Hoax"},{"name":"Someone Else"}]}},
        {"name":"A Tribute to HOAX","url":"https://example.test/2","dates":{"start":{"localDate":"2027-01-02"}},
         "_embedded":{"venues":[{"name":"Small Room","city":{"name":"Reno"},"country":{"name":"United States Of America"}}],"attractions":[{"name":"The Hoaxers"}]}},
        {"name":"HOAX","url":"https://example.test/3","dates":{"start":{"localDate":"2027-02-01"}},
         "_embedded":{"venues":[{"name":"Roxy","city":{"name":"Los Angeles"},"country":{"name":"United States Of America"}}]}},
        {"name":"HOAX: The b? Tour","url":"https://example.test/4","dates":{"start":{"localDate":"2027-03-14"}},
         "_embedded":{"venues":[{"name":"The Fillmore","city":{"name":"San Francisco"},"country":{"name":"United States Of America"}}],"attractions":[{"name":"HOAX"}]}}
    ]}}""")

    @Test fun theShowsTheArtistIsBilledInSoonestFirstEachOnce() {
        val shows = ConcertRules.parse(answer, "HOAX")
        assertEquals("the tribute act's show isn't theirs; the show listed twice is one", listOf("2027-02-01", "2027-03-14"), shows.map { it.date })
        assertEquals("Mon 1 Feb 2027 · Roxy, Los Angeles", ConcertRules.line(shows[0]))
        assertEquals("Sun 14 Mar 2027 · The Fillmore, San Francisco", ConcertRules.line(shows[1]))
        assertEquals("https://example.test/1", shows[1].url)
    }

    @Test fun nothingListedIsAnEmptyList() {
        assertTrue(ConcertRules.parse(JSON.parseToJsonElement("""{"page":{"totalElements":0}}"""), "HOAX").isEmpty())
        assertTrue(ConcertRules.parse(null, "HOAX").isEmpty())
    }

    @Test fun whatIsAsked() {
        assertEquals("https://app.ticketmaster.com/discovery/v2/events.json?classificationName=music&sort=date,asc&size=50&keyword=half%E2%80%A2alive&city=San+Francisco&apikey=KEY",
            ConcertRules.url("half•alive", " KEY ", "San Francisco"))
        assertTrue("no city: every show listed", "&city=" !in ConcertRules.url("HOAX", "KEY", " "))
        assertTrue(ConcertRules.why(401).startsWith("Ticketmaster didn't accept the key"))
    }
}
