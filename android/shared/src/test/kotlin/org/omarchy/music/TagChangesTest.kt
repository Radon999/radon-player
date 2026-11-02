package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Test

class TagChangesTest {
    @Test fun renamesFollowAndDoneAreSkipped() {
        val a = TagChange("1", 1.0, "Old Name", "Band", mapOf("title" to "New Name"))
        val b = TagChange("2", 2.0, "New Name", "Band", mapOf("genre" to "Indie"))
        val c = TagChange("3", 3.0, "Other", "Band", mapOf("album" to "X"))
        val index = mapOf(TagChanges.key("old name", "band") to listOf("/a.flac"))
        val plan = TagChanges.plan(listOf(b, c, a, a), index, emptySet())
        assertEquals(listOf("1", "2", "3"), plan.map { it.first.id })
        assertEquals(listOf("/a.flac"), plan[0].second)
        assertEquals(listOf("/a.flac"), plan[1].second)   // found by its new name
        assertEquals(emptyList<String>(), plan[2].second)
        assertEquals(listOf("2", "3"), TagChanges.plan(listOf(a, b, c), index, setOf("1")).map { it.first.id })
    }

    @Test fun lyricsAndMoodStay() {
        assertEquals(mapOf("title" to "x"), TagChanges.kept(mapOf("TITLE" to "x", "LYRICS" to "la", "mood" to "Happy")))
    }
}
