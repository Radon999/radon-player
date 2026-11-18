package org.omarchy.music

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** Synced playlists (⇄) in Sync: only they go to the other device; a device's own playlists stay its own. A playlist's
 *  "synced" is true (synced), false (just turned off: sent once more so the other device keeps its copy, as a normal
 *  playlist) or missing (a normal playlist). */
object SyncPlaylists {
    private fun list(text: String): List<JsonObject> =
        runCatching { (JSON.parseToJsonElement(text) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject } }.getOrDefault(emptyList())
    private fun synced(o: JsonObject) = o["synced"].let { it != null && it !is JsonNull }

    /** playlists.json's text with only the playlists that sync. */
    fun shared(text: String): String = JsonArray(list(text).filter(::synced)).toString()

    /** This device's playlists.json after a sync: got (the synced playlists as both devices now have them; one turned
     *  off becomes a normal playlist here), and this device's own playlists as they were. A synced one that isn't in
     *  got was deleted on the other device. null: nothing changes. */
    fun putBack(local: String, got: String): String? {
        val mine = list(local)
        val theirs = list(got).map { o -> if (o["synced"].str == "false") JsonObject(o - "synced") else o }
        val ids = theirs.mapNotNull { it["id"].str }.toSet()
        val own = mine.filter { !synced(it) && it["id"].str !in ids }
        // In this device's order, the new ones after
        val byId = theirs.associateBy { it["id"].str }
        val kept = mine.mapNotNull { o -> if (o in own) o else byId[o["id"].str] }
        val added = theirs.filter { t -> mine.none { it["id"].str == t["id"].str } }
        val out = oneOfEach(kept + added)
        return if (out == mine) null else JsonArray(out).toString()
    }

    private fun on(o: JsonObject) = o["synced"].str == "true"
    private fun real(e: JsonElement?) = e != null && e !is JsonNull
    private fun num(o: JsonObject, k: String) = o[k].str?.toDoubleOrNull() ?: 0.0
    private fun songs(o: JsonObject) = (o["tracks"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    private fun songKey(t: JsonObject) = LibraryMatchRules.key(t["title"].str ?: "", t["artist"].str ?: "")

    /** What a playlist is, when there may only be one of it: Listen later, or an artist's discovery playlist (or its
     *  discovered one). null: an ordinary playlist, of which there can be any number by any name. */
    private fun kind(o: JsonObject): String? {
        val d = o["discovery"] as? JsonObject
        if (d != null) return "d|" + (d["artist"].str ?: "").trim().lowercase() + "|" + (d["role"].str ?: "")
        return if (!real(o["smart"]) && o["name"].str == ListenLaterRules.NAME) "later" else null
    }

    /** Each device makes its own Listen later, and its own discovery playlists for an artist. Once one of them syncs,
     *  a device would have two: the synced one and its own. So there is one again: the synced one (the oldest, if both
     *  devices made theirs synced, so both choose the same). Listen later takes in the songs of the other, so nothing
     *  put aside is lost; a synced discovery playlist takes the place of the device's own for that artist. Playlists
     *  that don't sync, and ordinary playlists, are left as they are. */
    fun oneOfEach(all: List<JsonObject>): List<JsonObject> {
        val drop = ArrayList<JsonObject>()
        val change = HashMap<String?, JsonObject>()
        for ((k, group) in all.filter { kind(it) != null }.groupBy { kind(it) }) {
            if (group.size < 2 || group.none(::on)) continue
            val keep = group.filter(::on).minWith(compareBy<JsonObject>({ num(it, "created") }, { it["id"].str ?: "" }))
            val rest = group.filter { it !== keep }
            drop += rest
            if (k != "later") continue
            val have = songs(keep)
            val ids = have.mapNotNullTo(HashSet()) { it["id"].str }
            val keys = have.mapTo(HashSet()) { songKey(it) }
            val more = rest.flatMap { songs(it) }.filter { t -> t["id"].str.let { it != null && it !in ids } && keys.add(songKey(t)) }
            if (more.isNotEmpty()) change[keep["id"].str] = JsonObject(keep + ("tracks" to JsonArray(have + more)) +
                ("updated" to JsonPrimitive(group.maxOf { num(it, "updated") } + 1)))
        }
        if (drop.isEmpty()) return all
        return all.filter { o -> drop.none { it === o } }.map { o -> change[o["id"].str] ?: o }
    }

    /** The switch "New playlists sync" (Settings › Your files), on from the start: a playlist made while it is on (a
     *  normal, smart or discovery playlist, or Listen later) is set to sync. Most people want all their playlists on
     *  both devices; the few they don't are switched off one by one in Playlists that sync. */
    const val AUTO = "playlists_autosync"

    /** The mark every list of playlists shows on each one, so it is plain which sync and which don't. */
    fun mark(synced: Boolean): String = if (synced) "⇄ SYNCED" else "THIS DEVICE ONLY"

    /** What Settings says under Playlists that sync. */
    fun said(n: Int): String = if (n == 0) "None yet: each device keeps its own playlists"
        else "%,d playlist%s: the same on both devices".format(n, if (n != 1) "s sync" else " syncs")
}
