package org.omarchy.music

import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a sync changed here, in words. */
class SyncChangesRulesTest {
    private fun j(s: String): JsonElement = JSON.parseToJsonElement(s)

    @Test fun playlistsFavoritesAndTheRest() {
        val before = mapOf("playlists.json" to j("""[{"id":"a","name":"Gym","synced":true,"tracks":[{"id":"1"},{"id":"2"}]},{"id":"b","name":"Old","synced":true,"tracks":[]},{"id":"m","name":"Mine","tracks":[{"id":"9"}]}]"""),
            "favorites.json" to j("""{"1":{},"2":{}}"""), "plays.json" to j("""{"1":{"plays":3}}"""), "skips.json" to j("{}"))
        val after = mapOf("playlists.json" to j("""[{"id":"a","name":"Gym days","synced":true,"tracks":[{"id":"1"},{"id":"3"},{"id":"4"}]},{"id":"c","name":"Road trip","synced":true,"tracks":[{"id":"5"}]},{"id":"m","name":"Mine","tracks":[{"id":"9"}]}]"""),
            "favorites.json" to j("""{"1":{},"3":{},"4":{}}"""), "plays.json" to j("""{"1":{"plays":5}}"""), "skips.json" to j("{}"))
        assertEquals(listOf("“Gym days”: 2 songs added, 1 song taken out, renamed from “Gym”", "“Road trip”: new here, 1 song",
            "“Old”: removed (it was deleted on the other device, or joined into another of the same name)",
            "Favorites: 2 songs added, 1 song taken off", "Play counts: brought up to date"), SyncChangesRules.lines(before, after))
    }

    @Test fun nothingChangedSaysNothing() {
        val same = mapOf("playlists.json" to j("""[{"id":"a","name":"Gym","tracks":[{"id":"1"}]}]"""), "favorites.json" to j("""{"1":{}}"""))
        assertTrue(SyncChangesRules.lines(same, same).isEmpty())
        assertTrue("a file the sync didn't carry", SyncChangesRules.lines(same, emptyMap()).isEmpty())
    }
}
