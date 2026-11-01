package org.omarchy.music

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {
    private fun j(s: String): JsonElement = JSON.parseToJsonElement(s)
    private fun files(vararg p: Pair<String, String>) = p.associate { it.first to j(it.second) }
    private fun merge(base: Map<String, JsonElement>?, l: Map<String, JsonElement>, r: Map<String, JsonElement>, preferLocal: Boolean = true) =
        SyncMerge.mergeAll(base, l, r, preferLocal)
    private fun names(e: JsonElement?) = (e as JsonArray).map { it["name"].str }

    @Test fun allowAndDenyLists() {
        // the device's own settings never go across; the app's do
        assertFalse(SyncRules.prefSyncs("auto_mono_device")); assertFalse(SyncRules.prefSyncs("pc_volume"))
        assertFalse(SyncRules.prefSyncs("discord")); assertFalse(SyncRules.prefSyncs("hq_resample"))
        assertFalse(SyncRules.prefSyncs("lock_screen_player")); assertFalse(SyncRules.prefSyncs("notif_asked"))
        assertFalse(SyncRules.prefSyncs("ytm_cookie")); assertFalse(SyncRules.prefSyncs("sync_last"))
        assertFalse(SyncRules.prefSyncs("ignore_bt_devices")); assertFalse(SyncRules.prefSyncs("stream_quality"))
        assertTrue(SyncRules.prefSyncs("crossfade")); assertTrue(SyncRules.prefSyncs("dj_voice")); assertTrue(SyncRules.prefSyncs("dl_quality"))
        assertTrue(SyncRules.prefSyncs("live_kept")); assertTrue(SyncRules.prefSyncs("ignore_singles"))
        // no file is both synced and not
        assertTrue((SyncRules.FILES + SyncRules.SONG_FILES).none { it in SyncRules.NOT_FILES })
        assertTrue(SyncRules.STATE_KEYS.none { it in SyncRules.STATE_DEVICE })
        // every synced file has a rule
        assertTrue((SyncRules.FILES + SyncRules.SONG_FILES + SyncRules.STATE + SyncRules.SWITCHES).all { it in SyncMerge.RULES })
    }

    @Test fun stateKeepsTheDevicesOwn() {
        val st = j("""{"queue":[{"id":"a"}],"folders":["C:/Music"],"download_dir":"D:/x","bit_perfect":true,"look":"apple",
            "tab_sorts":{"music":"artist"},"layouts":{"music":"grid"},"api_keys":{"lastfm":"k"},
            "eq":{"on":true,"bands":[1.0],"myPresets":{"a":{"name":"Mine","bands":[1.0]}},
                  "outputs":{"speaker":{"on":true},"bt:WH-1000XM6":{"preset":"bass"},"usb:DAC":{}},
                  "devices":{"speaker":"x","bt:WH-1000XM6":"wh1000xm6-bt"}}}""") as JsonObject
        val o = SyncRules.state(st)
        assertEquals(setOf("look", "tab_sorts", "eq"), o.keys)
        val eq = o["eq"] as JsonObject
        assertEquals(setOf("myPresets", "outputs", "devices"), eq.keys)   // not the EQ playing now
        assertEquals(setOf("bt:WH-1000XM6", "usb:DAC"), (eq["outputs"] as JsonObject).keys)
        assertEquals(setOf("bt:WH-1000XM6"), (eq["devices"] as JsonObject).keys)
        val sw = SyncRules.prefs(j("""{"crossfade":true,"pc_volume":0.5,"auto_mono_device":"Buds","dj_voice":"on"}""") as JsonObject)
        assertEquals(setOf("crossfade", "dj_voice"), sw.keys)
    }

    @Test fun firstSyncIsAUnion() {
        val l = files("playlists.json" to """[{"id":"p1","name":"Gym","created":1,"updated":10,"tracks":[]}]""",
            "song-volume.json" to """{"a|x":2.0}""")
        val r = files("playlists.json" to """[{"id":"p2","name":"Chill","created":2,"updated":20,"tracks":[]}]""",
            "song-volume.json" to """{"b|y":-3.0}""")
        val m = merge(null, l, r)
        assertEquals(listOf("Gym", "Chill"), names(m["playlists.json"]))
        assertEquals(setOf("a|x", "b|y"), (m["song-volume.json"] as JsonObject).keys)
    }

    @Test fun newestWinsAndChangesOnBothSidesCome() {
        val base = files("playlists.json" to """[{"id":"p1","name":"Gym","created":1,"updated":10,
            "tracks":[{"id":"1","title":"One","artist":"A"},{"id":"2","title":"Two","artist":"B"}]}]""")
        // here: renamed at 30 and song Three added; there: song Four added at 20, song Two removed
        val l = files("playlists.json" to """[{"id":"p1","name":"Gym 2","created":1,"updated":30,
            "tracks":[{"id":"1","title":"One","artist":"A"},{"id":"2","title":"Two","artist":"B"},{"id":"3","title":"Three","artist":"C"}]}]""")
        val r = files("playlists.json" to """[{"id":"p1","name":"Gym","created":1,"updated":20,
            "tracks":[{"id":"1","title":"One","artist":"A"},{"id":"4","title":"Four","artist":"D"}]}]""")
        val p = (merge(base, l, r, preferLocal = false)["playlists.json"] as JsonArray)[0] as JsonObject
        assertEquals("Gym 2", p["name"].str)
        assertEquals(30L, p["updated"].long)
        assertEquals(listOf("One", "Three", "Four"), (p["tracks"] as JsonArray).map { it["title"].str })
        // a conflict with no time to go by: the device Sync was tapped on wins
        val b2 = files("song-speeds.json" to """{"s":{"speed":1.1}}""")
        val l2 = files("song-speeds.json" to """{"s":{"speed":1.2}}"""); val r2 = files("song-speeds.json" to """{"s":{"speed":1.3}}""")
        assertEquals(1.2, merge(b2, l2, r2, true)["song-speeds.json"]["s"]["speed"].dbl)
        assertEquals(1.3, merge(b2, l2, r2, false)["song-speeds.json"]["s"]["speed"].dbl)
        // only one side changed it: that change, whoever tapped
        val r3 = files("song-speeds.json" to """{"s":{"speed":1.1}}""")
        assertEquals(1.2, merge(b2, l2, r3, false)["song-speeds.json"]["s"]["speed"].dbl)
    }

    @Test fun deletionsAfterTheFirstSync() {
        val base = files("favorites.json" to """{"a":{"added":1},"b":{"added":2}}""", "playlists.json" to """[{"id":"p","name":"X","updated":1}]""")
        val l = files("favorites.json" to """{"a":{"added":1}}""", "playlists.json" to """[{"id":"p","name":"X","updated":1}]""")   // b unstarred here
        val r = files("favorites.json" to """{"a":{"added":1},"b":{"added":2},"c":{"added":3}}""", "playlists.json" to "[]")    // p deleted there
        val m = merge(base, l, r)
        assertEquals(setOf("a", "c"), (m["favorites.json"] as JsonObject).keys)
        assertEquals(0, (m["playlists.json"] as JsonArray).size)
        // without a last sync nothing counts as deleted
        assertEquals(setOf("a", "b", "c"), (merge(null, l, r)["favorites.json"] as JsonObject).keys)
        // deleted on one side but changed on the other: the change is kept
        val r2 = files("favorites.json" to "{}", "playlists.json" to """[{"id":"p","name":"Renamed","updated":5}]""")
        assertEquals(listOf("Renamed"), names(merge(base, l, r2)["playlists.json"]))
    }

    @Test fun ownSongCopiesStayAndDontCountAsChanges() {
        val base = files("favorites.json" to """{"a|x":{"added":1,"track":{"id":"pc1","title":"A","file":"C:/a.flac"}}}""")
        val l = files("favorites.json" to """{"a|x":{"added":1,"track":{"id":"ph1","title":"A","file":"content://1"}}}""")
        val m = merge(base, l, base)
        assertEquals("content://1", m["favorites.json"]["a|x"]["track"]["file"].str)
        assertEquals(0, SyncMerge.summary(m, l).changed)
    }

    @Test fun playsAddUp() {
        // since the last sync: 2 more plays here, 3 more there
        val base = files("plays.json" to """{"t":{"plays":5,"last":100,"at":[1,2,3,4,5]}}""")
        val l = files("plays.json" to """{"t":{"plays":7,"last":200,"at":[1,2,3,4,5,10,11]}}""")
        val r = files("plays.json" to """{"t":{"plays":8,"last":300,"at":[1,2,3,4,5,20,21,22]}}""")
        val m = merge(base, l, r)["plays.json"]["t"]
        assertEquals(10L, m["plays"].long); assertEquals(300L, m["last"].long)
        assertEquals(10, (m["at"] as JsonArray).size)
        // never synced, the same plays on both (a backup of the other): not counted twice
        val m2 = merge(null, l, l)["plays.json"]["t"]
        assertEquals(7L, m2["plays"].long)
        // never synced, different plays: added up
        val m3 = merge(null, files("plays.json" to """{"t":{"plays":2,"at":[1,2]}}"""), files("plays.json" to """{"t":{"plays":3,"at":[5,6,7]}}"""))
        assertEquals(5L, m3["plays.json"]["t"]["plays"].long)
        // applying the other device's result on top of what was sent: nothing added twice
        val sent = l
        val result = merge(base, l, r)
        assertEquals(10L, merge(sent, sent, result, preferLocal = false)["plays.json"]["t"]["plays"].long)
    }

    @Test fun setsAndPins() {
        val base = files("state.json" to """{"not_dupes":["a","b"],"pins":[{"kind":"album","id":"1","title":"X"}]}""")
        val l = files("state.json" to """{"not_dupes":["a","b","c"],"pins":[{"kind":"album","id":"1","title":"X"}]}""")
        val r = files("state.json" to """{"not_dupes":["b"],"pins":[{"kind":"artist","id":"Björk","title":"Björk"},{"kind":"album","id":"1","title":"X"}]}""")
        val st = merge(base, l, r)["state.json"] as JsonObject
        assertEquals(listOf("b", "c"), (st["not_dupes"] as JsonArray).map { it.str })
        assertEquals(listOf("artist", "album"), (st["pins"] as JsonArray).map { it["kind"].str })   // the new pin first
    }

    @Test fun eqPresetsAndSettings() {
        val base = files("state.json" to """{"look":"apple","eq":{"myPresets":{"a":{"name":"A","bands":[0.0]}}}}""",
            "switches.json" to """{"crossfade":false,"crossfade_secs":4.0}""")
        val l = files("state.json" to """{"look":"apple","eq":{"myPresets":{"a":{"name":"A","bands":[0.0]},"b":{"name":"B","bands":[1.0]}}}}""",
            "switches.json" to """{"crossfade":true,"crossfade_secs":4.0}""")
        val r = files("state.json" to """{"look":"apple-dark","eq":{"myPresets":{},"outputs":{"bt:Buds":{"preset":"bass"}}}}""",
            "switches.json" to """{"crossfade":false,"crossfade_secs":6.0}""")
        val m = merge(base, l, r)
        val st = m["state.json"] as JsonObject
        assertEquals("apple-dark", st["look"].str)
        assertEquals(setOf("b"), (st["eq"]["myPresets"] as JsonObject).keys)   // a deleted there, b made here
        assertEquals(setOf("bt:Buds"), (st["eq"]["outputs"] as JsonObject).keys)
        assertEquals(true, m["switches.json"]["crossfade"].bool); assertEquals(6.0, m["switches.json"]["crossfade_secs"].dbl)
        val s = SyncMerge.summary(m, l)
        assertTrue(s.text(), s.text().startsWith("Synced: "))
        assertTrue(s.text(), s.text().contains("1 Sound preset,") || s.text().contains("1 Sound preset ·"))
        assertTrue(s.changed >= 4)   // look, preset a gone, output added, crossfade_secs
    }

    @Test fun onlyFilesBothSent() {
        val l = files("song-volume.json" to """{"a":1.0}""", "favorites.json" to "{}")
        val r = files("favorites.json" to "{}")
        assertEquals(setOf("favorites.json"), merge(null, l, r).keys)   // the songs' volumes stay put
    }
}
