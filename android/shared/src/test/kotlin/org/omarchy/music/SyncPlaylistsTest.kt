package org.omarchy.music

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncPlaylistsTest {
    private fun ids(t: String) = (JSON.parseToJsonElement(t) as JsonArray).map { (it as JsonObject)["id"].str }

    @Test fun onlySyncedOnesGo() {
        val local = """[{"id":"a","name":"Mine"},{"id":"b","name":"Both","synced":true},{"id":"c","name":"Off","synced":false}]"""
        assertEquals(listOf("b", "c"), ids(SyncPlaylists.shared(local)))
    }

    @Test fun putBackKeepsOwnAndFollowsTheOther() {
        val local = """[{"id":"a","name":"Mine"},{"id":"b","name":"Both","synced":true},{"id":"d","name":"Gone","synced":true}]"""
        // b changed on the other device, d deleted there, e new there, c turned off there
        val got = """[{"id":"b","name":"Both","synced":true,"tracks":[{"id":"1"}]},{"id":"e","name":"New","synced":true},{"id":"c","name":"Off","synced":false}]"""
        val out = SyncPlaylists.putBack(local, got)!!
        assertEquals(listOf("a", "b", "e", "c"), ids(out))
        val c = (JSON.parseToJsonElement(out) as JsonArray).map { it as JsonObject }.first { it["id"].str == "c" }
        assertNull(c["synced"])
        assertNull(SyncPlaylists.putBack(out, SyncPlaylists.shared(out)))
    }
}
