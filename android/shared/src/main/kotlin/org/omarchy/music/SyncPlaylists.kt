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

    /** What two playlists must share to be the same playlist made twice (once on each device): Listen later; an
     *  artist's discovery playlist, or its discovered one; else a playlist's name. null: a smart playlist (two sets of
     *  rules can't be joined, so smart playlists of one name stay two). */
    private fun kind(o: JsonObject): String? {
        val d = o["discovery"] as? JsonObject
        if (d != null) return "d|" + (d["artist"].str ?: "").trim().lowercase() + "|" + (d["role"].str ?: "")
        if (real(o["smart"])) return null
        val name = o["name"].str?.trim().orEmpty()
        return if (name == ListenLaterRules.NAME) "later" else if (name.isEmpty()) null else "n|" + name.lowercase()
    }

    /** Syncing a playlist means its songs come together: what you add on the phone is on the PC's, and the other way.
     *  A playlist both devices have under one id does that already. But each device may have made its own: "Gym" on
     *  the phone and "Gym" on the PC, its own Listen later, its own discovery playlists for an artist. Once they sync,
     *  a device would have two of the same. So they are joined into one: the oldest that syncs is kept (both devices
     *  choose the same one), with the songs of all of them, each song once. A discovery playlist loses the songs its
     *  joined discovered list holds (heard to the end on either device is heard). Which are joined: playlists of one
     *  name that both sync; for Listen later and discovery playlists, of which a device can only have one, this
     *  device's own too once the other's syncs. A playlist you keep to its device (not synced) is otherwise left
     *  alone, and so are smart playlists. */
    fun oneOfEach(all: List<JsonObject>): List<JsonObject> {
        val drop = ArrayList<JsonObject>()
        val change = HashMap<String?, JsonObject>()
        val joined = HashSet<String>()   // the discovery pairs something was joined in (their artists)
        fun top(group: List<JsonObject>) = JsonPrimitive(group.maxOf { num(it, "updated") } + 1)
        for ((k, group) in all.filter { kind(it) != null }.groupBy { kind(it)!! }) {
            val single = !k.startsWith("n|")
            val some = if (single) group else group.filter(::on)
            if (some.size < 2 || some.none(::on)) continue
            val keep = some.filter(::on).minWith(compareBy<JsonObject>({ num(it, "created") }, { it["id"].str ?: "" }))
            val rest = some.filter { it !== keep }
            drop += rest
            if (k.startsWith("d|")) joined += k.split("|")[1]
            val have = songs(keep)
            val ids = have.mapNotNullTo(HashSet()) { it["id"].str }
            val keys = have.mapTo(HashSet()) { songKey(it) }
            val more = rest.flatMap { songs(it) }.filter { t -> t["id"].str.let { it != null && it !in ids } && keys.add(songKey(t)) }
            if (more.isNotEmpty()) change[keep["id"].str] = JsonObject(keep + ("tracks" to JsonArray(have + more)) + ("updated" to top(group)))
        }
        if (drop.isEmpty()) return all
        val out = all.filter { o -> drop.none { it === o } }.map { o -> change[o["id"].str] ?: o }
        if (joined.isEmpty()) return out
        // Heard to the end on either device: out of the songs still to hear
        return out.map { o ->
            val artist = kind(o)?.takeIf { it.startsWith("d|") && it.endsWith("|discover") }?.split("|")?.get(1)
            val heard = if (artist == null || artist !in joined) null else out.firstOrNull { kind(it) == "d|$artist|discovered" }
            if (heard == null) return@map o
            val ids = songs(heard).mapNotNullTo(HashSet()) { it["id"].str }
            val keys = songs(heard).mapTo(HashSet()) { songKey(it) }
            val left = songs(o).filter { (it["id"].str ?: "") !in ids && songKey(it) !in keys }
            if (left.size == songs(o).size) o else JsonObject(o + ("tracks" to JsonArray(left)) + ("updated" to top(listOf(o, heard))))
        }
    }

    /** The switch "New playlists sync" (Settings › Your files), on from the start: a playlist made while it is on (a
     *  normal, smart or discovery playlist, or Listen later) is set to sync. Most people want all their playlists on
     *  both devices; the few they don't are switched off one by one in Playlists that sync. */
    const val AUTO = "playlists_autosync"

    /** The mark every list of playlists shows on each one, so it is plain which sync and which don't. */
    fun mark(synced: Boolean): String = if (synced) "⇄ SYNCED" else "THIS DEVICE ONLY"

    /** The switch "Auto playlist sync" (Settings › Remote & sync), off until you turn it on: the synced playlists are
     *  synced by themselves when the phone's app finds the PC's (the app opened, back on the same network), and again
     *  when one of them changes on either while both are open. Only the playlists: the rest waits for Sync now. */
    const val AUTO_SYNC = "playlists_sync_by_themselves"

    /** Said wherever a playlist is set to sync, so nobody takes "synced" for "sent as I go" when it isn't. */
    fun notice(auto: Boolean): String =
        if (auto) "Auto playlist sync is on: songs you add or take out reach the other device by themselves while Radon Player is open on both, on the same network. Otherwise at the next time they meet, or when you tap Sync now"
        else "Nothing crosses by itself: songs you add or take out reach the other device only when you tap Sync now, unless you turn on Auto playlist sync (both in Settings › Remote & sync)"

    /** Whether the phone syncs the playlists now. met: it had found the PC already (false: it just did, or the app
     *  just opened). pcAt, myAt: when each device's playlists last changed; seenPc, seenMine: as they were at the last
     *  sync by itself. So: once on meeting, then only when something changed on either. */
    fun autoNow(on: Boolean, pcCan: Boolean, met: Boolean, pcAt: Long, seenPc: Long, myAt: Long, seenMine: Long): Boolean =
        on && pcCan && (!met || pcAt != seenPc || myAt != seenMine)

    /** What Settings says under Playlists that sync. */
    fun said(n: Int): String = if (n == 0) "None yet: each device keeps its own playlists"
        else "%,d playlist%s: the same on both devices".format(n, if (n != 1) "s sync" else " syncs")
}
