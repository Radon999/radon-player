package org.omarchy.music

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private fun arr(t: String) = (JSON.parseToJsonElement(t) as JsonArray).map { it as JsonObject }
    private fun titles(o: JsonObject) = (o["tracks"] as JsonArray).map { (it as JsonObject)["title"].str }

    @Test fun listenLaterBecomesOneWithTheSongsOfBoth() {
        // This device's own Listen later (not synced), and the other device's synced one arriving
        val local = """[{"id":"mine","name":"Listen later","created":5,"updated":9,"tracks":[{"id":"1","title":"Drew","artist":"HOAX"},{"id":"2","title":"Soju","artist":"HOAX"}]},{"id":"a","name":"Mine"}]"""
        val got = """[{"id":"theirs","name":"Listen later","synced":true,"created":7,"updated":8,"tracks":[{"id":"9","title":"drew","artist":"Hoax"},{"id":"3","title":"TV","artist":"HOAX"}]}]"""
        val out = arr(SyncPlaylists.putBack(local, got)!!)
        assertEquals(listOf("a", "theirs"), out.map { it["id"].str })
        val later = out.first { it["id"].str == "theirs" }
        assertEquals("the other's songs, then this device's that it hadn't (Drew is on it already)", listOf("drew", "TV", "Soju"), titles(later))
        assertEquals("changed here, so the next sync takes it to the other", 10.0, later["updated"].str!!.toDouble(), 0.0)
        assertEquals("true", later["synced"].str)
    }

    @Test fun bothMadeTheirsSyncedThenTheOlderIsTheOneOnBoth() {
        val a = """{"id":"a","name":"Listen later","synced":true,"created":5,"updated":5,"tracks":[{"id":"1","title":"Drew","artist":"HOAX"}]}"""
        val b = """{"id":"b","name":"Listen later","synced":true,"created":7,"updated":7,"tracks":[{"id":"2","title":"TV","artist":"HOAX"}]}"""
        for (order in listOf("[$a,$b]", "[$b,$a]")) {
            val out = SyncPlaylists.oneOfEach(arr(order))
            assertEquals(listOf("a"), out.map { it["id"].str })
            assertEquals(listOf("Drew", "TV"), titles(out[0]))
        }
    }

    @Test fun aDiscoveryPairBecomesOneWithWhatEachDeviceHeard() {
        fun t(id: String, title: String) = """{"id":"$id","title":"$title","artist":"HOAX"}"""
        // Here: Drew and Soju still to hear, TV heard. There: Drew heard, Soju and Clouds still to hear
        val local = """[{"id":"d1","name":"HOAX Discovery playlist","discovery":{"artist":"HOAX","role":"discover"},"created":1,"updated":4,"tracks":[${t("1", "Drew")},${t("2", "Soju")}]},
            {"id":"d2","name":"HOAX discovered","discovery":{"artist":"HOAX","role":"discovered"},"created":1,"updated":4,"tracks":[${t("3", "TV")}]},
            {"id":"j1","name":"Joji Discovery playlist","discovery":{"artist":"Joji","role":"discover"},"created":1}]"""
        val got = """[{"id":"s1","name":"HOAX Discovery playlist","synced":true,"discovery":{"artist":"hoax","role":"discover"},"created":3,"updated":5,"tracks":[${t("2", "Soju")},${t("4", "Clouds")}]},
            {"id":"s2","name":"HOAX discovered","synced":true,"discovery":{"artist":"hoax","role":"discovered"},"created":3,"updated":5,"tracks":[${t("9", "drew")}]}]"""
        val out = arr(SyncPlaylists.putBack(local, got)!!)
        assertEquals("Joji's isn't synced and has no twin: it stays this device's own", listOf("j1", "s1", "s2"), out.map { it["id"].str })
        assertEquals("heard on either device", listOf("drew", "TV"), titles(out[2]))
        assertEquals("still to hear on both: Drew was heard there, so it is off the list", listOf("Soju", "Clouds"), titles(out[1]))
    }

    @Test fun playlistsOfOneNameThatBothSyncBecomeOne() {
        val phone = """{"id":"p","name":"Gym","synced":true,"created":5,"updated":5,"tracks":[{"id":"1","title":"Drew","artist":"HOAX"},{"id":"2","title":"TV","artist":"HOAX"}]}"""
        val pc = """{"id":"c","name":"gym ","synced":true,"created":9,"updated":12,"tracks":[{"id":"7","title":"TV","artist":"Hoax"},{"id":"3","title":"Soju","artist":"HOAX"}]}"""
        for (order in listOf("[$phone,$pc]", "[$pc,$phone]")) {
            val out = SyncPlaylists.oneOfEach(arr(order))
            assertEquals("the older is kept on both devices", listOf("p"), out.map { it["id"].str })
            assertEquals("the songs of both, each once", listOf("Drew", "TV", "Soju"), titles(out[0]))
            assertEquals(13.0, out[0]["updated"].str!!.toDouble(), 0.0)
        }
    }

    @Test fun aPlaylistKeptToItsDeviceIsNotJoined() {
        val mine = arr("""[{"id":"a","name":"Gym","tracks":[{"id":"1","title":"Drew","artist":"HOAX"}]},{"id":"b","name":"Gym","synced":true,"tracks":[]},
            {"id":"c","name":"Mix","synced":false},{"id":"d","name":"Mix","synced":true}]""")
        assertEquals(mine, SyncPlaylists.oneOfEach(mine))
    }

    @Test fun nothingIsJoinedThatDoesNotSync() {
        val two = arr("""[{"id":"a","name":"Listen later"},{"id":"b","name":"Listen later"},{"id":"c","name":"Mix","synced":true},{"id":"d","name":"Mix"},
            {"id":"e","name":"Listen later","smart":{"live":true},"synced":true},{"id":"f","name":"Best","smart":{"live":true},"synced":true},
            {"id":"g","name":"Best","smart":{"live":false},"synced":true}]""")
        assertEquals(two, SyncPlaylists.oneOfEach(two))
    }

    @Test fun playlistsSyncByThemselvesOnMeetingAndWhenOneChanges() {
        fun now(on: Boolean = true, can: Boolean = true, met: Boolean = true, pc: Long = 5, seenPc: Long = 5, mine: Long = 7, seenMine: Long = 7) =
            SyncPlaylists.autoNow(on, can, met, pc, seenPc, mine, seenMine)
        assertTrue("the phone just found the PC (or the app just opened)", now(met = false))
        assertFalse("nothing changed since: not again every 20 seconds", now())
        assertTrue("a playlist changed on the PC", now(pc = 6))
        assertTrue("a playlist changed on the phone", now(mine = 8))
        assertFalse("the switch is off on both", now(on = false, met = false))
        assertFalse("a PC app from before this: it would take it for a whole sync", now(can = false, met = false))
        assertTrue(SyncPlaylists.notice(false).startsWith("Nothing crosses by itself"))
        assertTrue(SyncPlaylists.notice(true).startsWith("Auto playlist sync is on"))
    }

    @Test fun whatSettingsSays() {
        assertEquals("⇄ SYNCED", SyncPlaylists.mark(true))
        assertEquals("THIS DEVICE ONLY", SyncPlaylists.mark(false))
        assertEquals("⇄ SYNCED · CHANGES WAITING", SyncPlaylists.mark(true, waiting = true))
        assertEquals("a playlist that doesn't sync has nothing waiting", "THIS DEVICE ONLY", SyncPlaylists.mark(false, waiting = true))
        assertFalse("as it was at the last sync", SyncPlaylists.waiting(true, 100.0, 100.0))
        assertTrue("a song added since", SyncPlaylists.waiting(true, 130.0, 100.0))
        assertTrue("set to sync, never yet synced", SyncPlaylists.waiting(true, 100.0, null))
        assertFalse(SyncPlaylists.waiting(false, 130.0, 100.0))
        assertEquals("None yet: each device keeps its own playlists", SyncPlaylists.said(0))
        assertEquals("1 playlist syncs: the same on both devices", SyncPlaylists.said(1))
        assertEquals("4 playlists sync: the same on both devices", SyncPlaylists.said(4))
    }
}
