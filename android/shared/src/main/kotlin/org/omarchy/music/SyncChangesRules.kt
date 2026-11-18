package org.omarchy.music

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** What a sync changed on this device, in lines you can read: which playlists gained or lost songs (or are new, or
 *  gone), how many favorites came or went, and which other things were brought up to date. Worked out by comparing
 *  this device's data just before the sync with what the sync left. */
object SyncChangesRules {
    private val NAMES = mapOf("playlist-folders.json" to "Playlist folders", "plays.json" to "Play counts", "skips.json" to "Skips and songs heard out",
        "song-speeds.json" to "Song speeds", "song-volume.json" to "Song volumes", "song-volume-outputs.json" to "Song volumes for each output",
        "song-eq.json" to "Custom EQs", SyncRules.STATE to "Settings, pins and Sound presets", SyncRules.SWITCHES to "Switches")

    private fun songs(n: Int) = "%,d song%s".format(n, if (n != 1) "s" else "")
    private fun lists(e: JsonElement?) = (e as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.filter { it["id"].str != null }
    private fun ids(p: JsonObject) = (p["tracks"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("id").str }.toSet()

    /** before, after: each data file's content by its name. The lines, playlists first; empty: nothing changed. */
    fun lines(before: Map<String, JsonElement>, after: Map<String, JsonElement>): List<String> {
        val out = ArrayList<String>()
        after["playlists.json"]?.let { now ->
            val old = lists(before["playlists.json"]).associateBy { it["id"].str!! }
            val new = lists(now).associateBy { it["id"].str!! }
            for ((id, p) in new) {
                val name = p["name"].str ?: "A playlist"; val o = old[id]
                if (o == null) { out += "“$name”: new here, ${songs(ids(p).size)}"; continue }
                val added = (ids(p) - ids(o)).size; val gone = (ids(o) - ids(p)).size
                val renamed = o["name"].str != null && o["name"].str != name
                if (added + gone == 0 && !renamed) continue
                out += "“$name”: " + listOfNotNull(if (added > 0) "${songs(added)} added" else null, if (gone > 0) "${songs(gone)} taken out" else null,
                    if (renamed) "renamed from “${o["name"].str}”" else null).joinToString(", ")
            }
            for ((id, o) in old) if (id !in new && o["synced"].str == "true") out += "“${o["name"].str ?: "A playlist"}”: removed (it was deleted on the other device, or joined into another of the same name)"
        }
        after["favorites.json"]?.let { now ->
            val old = (before["favorites.json"] as? JsonObject)?.keys.orEmpty(); val new = (now as? JsonObject)?.keys.orEmpty()
            val added = (new - old).size; val gone = (old - new).size
            if (added + gone > 0) out += "Favorites: " + listOfNotNull(if (added > 0) "${songs(added)} added" else null, if (gone > 0) "${songs(gone)} taken off" else null).joinToString(", ")
        }
        for ((file, name) in NAMES) { val now = after[file] ?: continue; if (before[file] != null && before[file] != now) out += "$name: brought up to date" }
        return out
    }
}
