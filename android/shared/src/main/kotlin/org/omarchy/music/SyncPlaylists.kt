package org.omarchy.music

import kotlinx.serialization.json.JsonArray
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
        val out = kept + added
        return if (out == mine) null else JsonArray(out).toString()
    }
}
