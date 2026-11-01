package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class SortingTest {
    private val a = Track("a", title = "Bravo", artist = "Zed", album = "One", duration = 100.0, added = 3.0)
    private val b = Track("b", title = "alpha", artist = "Amy", album = "Two", duration = 300.0, added = 1.0)
    private val c = Track("c", title = "Charlie", artist = "Moe", album = "", duration = 200.0, added = 2.0)
    private val all = listOf(a, b, c)

    @Test fun songs() {
        assertEquals(listOf(b, a, c), sortTracks(all, "title"))
        assertEquals(listOf(c, a, b), sortTracks(all, "title-desc"))
        assertEquals(listOf(b, c, a), sortTracks(all, "artist"))
        assertEquals(listOf(a, c, b), sortTracks(all, "added"))
        assertEquals(listOf(b, c, a), sortTracks(all, "added-old"))
        assertEquals(listOf(b, c, a), sortTracks(all, "length"))
        assertEquals(all, sortTracks(all, "order"))
        val d = SortData(plays = { mapOf("a" to 5, "b" to 9).getOrDefault(it.id, 0) }, last = { if (it.id == "c") 50L else 1L })
        assertEquals(listOf(b, a, c), sortTracks(all, "plays", d))
        assertEquals(c, sortTracks(all, "played", d).first())
    }

    @Test fun groups() {
        val g = listOf("x" to listOf(a), "y" to listOf(b, c), "w" to emptyList())
        val facts = { p: Pair<String, List<Track>> -> GroupFacts(p.first, p.second.firstOrNull()?.artist ?: "", p.second) }
        assertEquals(listOf("w", "x", "y"), sortGroups(g, "name", facts = facts).map { it.first })
        assertEquals(listOf("y", "x", "w"), sortGroups(g, "songs", facts = facts).map { it.first })
        assertEquals(listOf("x", "y", "w"), sortGroups(g, "added", facts = facts).map { it.first })
        assertEquals(listOf("y", "x", "w"), sortGroups(g, "length", facts = facts).map { it.first })
        assertEquals(listOf("x", "y", "w"), sortGroups(g, "order", facts = facts).map { it.first })
    }
}
