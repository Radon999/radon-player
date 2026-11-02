package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ListeningTest {
    @Test fun diversity() {
        assertEquals(0.0, Listening.diversity(emptyList()), 1e-9)
        assertEquals(0.0, Listening.diversity(listOf(7)), 1e-9)          // one kind: not varied at all
        assertEquals(0.0, Listening.diversity(listOf(0, 0, 4)), 1e-9)    // empty kinds don't count
        assertEquals(1.0, Listening.diversity(listOf(3, 3, 3)), 1e-9)    // all the same: as varied as can be
        val lopsided = Listening.diversity(listOf(90, 5, 5))
        assertTrue(lopsided > 0.2 && lopsided < 0.6)
        assertTrue(Listening.diversity(listOf(40, 30, 30)) > lopsided)
    }

    @Test fun listenerType() {
        val owl = Listening.type(Listening.Traits(fans = 50_000, top10 = 0.2, night = 0.4, skips = 0.1))
        assertEquals("The Night-Owl Explorer", owl.name)
        assertTrue(owl.traits.any { it.word == "Underground" })
        assertTrue(owl.traits.any { it.word == "Finisher" })

        val pop = Listening.type(Listening.Traits(fans = 20_000_000, shuffle = 0.9, top10 = 0.7, newArtists = 0.1))
        assertEquals("The Chart-Topping Shuffler", pop.name)
        assertTrue(pop.traits.any { it.word == "Loyal" })

        assertEquals("The All-Rounder", Listening.type(Listening.Traits()).name)
        // In the middle on every axis: no clear trait
        assertTrue(Listening.traits(Listening.Traits(shuffle = 0.45, top10 = 0.5, night = 0.1, morning = 0.1, skips = 0.25)).isEmpty())
        // Many new artists make an explorer even with a big top 10
        assertTrue(Listening.traits(Listening.Traits(top10 = 0.7, newArtists = 0.6)).any { it.word == "Explorer" })
    }

    @Test fun runsAndTimes() {
        val d = LocalDate.of(2026, 1, 1)
        assertEquals(4, Listening.longestRun(listOf(d, d.plusDays(1), d.plusDays(3), d.plusDays(2), d.plusDays(31))))
        assertEquals(0, Listening.longestRun(emptyList()))
        assertEquals("1 day 1 hour", Listening.duration(90_000.0))
        assertEquals("10 min", Listening.duration(600.0))
        assertEquals("2 hours 5 min", Listening.duration(7_500.0))
        assertEquals(1000L, Listening.weightedMedian(listOf(10L to 1, 1000L to 5, 5L to 1)))
        assertNull(Listening.weightedMedian(emptyList()))
    }
}
