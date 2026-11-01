package org.omarchy.music

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Sync with phone/PC (Settings › Play on): what of the app's data goes across, and what stays the device's own.
 *  Everything here is listed by name on purpose: a new file or setting stays put until it's added here. */
object SyncRules {
    /** The data files that sync, as the app keeps them (in its files folder), each merged by [SyncMerge.RULES]. */
    val FILES = listOf("playlists.json", "playlist-folders.json", "favorites.json", "plays.json", "skips.json", "song-speeds.json")

    /** The volumes and Custom EQs you gave songs: made for the headphones you use with each device, so they go across
     *  only when both devices have "Songs' own volumes and EQs" on (as a backup, they restore only on the same kind). */
    val SONG_FILES = listOf("song-volume.json", "song-volume-outputs.json", "song-eq.json")

    const val STATE = "state.json"         // the app's own settings out of state.json: only STATE_KEYS
    const val SWITCHES = "switches.json"   // the switches (Prefs): all but the device's own ([prefSyncs])
    const val MOODS = "moods-backup.json"  // songs' moods, by song (Moods.backup): given to the same songs on the other device

    /** Never synced (kept for reference, and checked by the tests): your songs and where they are, sign-ins, the other
     *  device's pairing, imported histories (big, and imported per device or brought by a backup), caches. */
    val NOT_FILES = listOf("library.json", "missing.json", "remote.json", "remote-tokens.json", "spotify-history.json",
        "apple-history.json", "deezer-history.json", "youtube-history.json", "tidal-history.json", "new-releases.json",
        "tidal.json", "spotify.json", "lastfm.json", "apple-token.json", "scrobbles.json", "stats.json", "tidal-matches.json")

    /** state.json's settings that sync: how lists are sorted and shown, the looks, the DJ's "new songs too", tabs you put
     *  releases in, what you kept apart or told never to show again, pins, and the equalizer's presets and outputs. */
    val STATE_VALUES = listOf("lyrics_mode", "music_sort", "playlist_sort", "skip_order", "skip_period", "top_mine_only",
        "top_period", "top_kind", "collage_size", "look", "np_fallback", "vis_preset", "wrapped_style", "autoplay",
        "multi_queue", "lyrics_to_files", "dupe_action", "dj_new")
    val STATE_MAPS = listOf("tab_sorts", "tab_picks", "artist_picks", "album_picks", "song_picks")
    val STATE_SETS = listOf("similar_kept", "singles_kept", "not_dupes")
    val STATE_KEYS = STATE_VALUES + STATE_MAPS + STATE_SETS + listOf("eq", "pins")

    /** state.json's settings that stay on the device: what's playing and its queues, where the music and downloads are,
     *  the services it syncs from, bit-perfect, the menu's and pages' layout (screen sizes differ), your API keys. */
    val STATE_DEVICE = listOf("queue", "index", "radio_from", "repeat", "shuffled", "queues", "queue_name", "queue_id",
        "source", "sync_sources", "search_in", "search_before_local", "browse_local", "folders", "download_dir",
        "keep_played", "bit_perfect", "replay_gain", "mic_asked", "drawer_folds", "layouts", "api_keys", "dj", "styled", "apple_ui")

    /** The equalizer's parts that sync: Sound presets you saved, headphone corrections you added, and each portable
     *  output's EQ and correction (headphones, a USB DAC, the car). The EQ playing now is the output's own. */
    val EQ_KEYS = listOf("myPresets", "profiles", "devices", "outputs", "perOutput")

    /** An output both devices can meet: Bluetooth headphones and speakers, a USB DAC, the car. The phone's speaker and
     *  jack, and the PC's speakers, are each device's own. */
    fun portableOutput(key: String) = key.startsWith("bt:") || key.startsWith("bluetooth:") || key.startsWith("usb:") || key.startsWith("car:")

    /** Switches that are the device's own: its earbuds and Bluetooth handling, its window, tray and startup, Discord,
     *  notifications and the lock screen, its sound card (the PC's resampler, bit-perfect), stream quality on its
     *  network, Mono for the earbud in now, mood scans of its files, and the sync's own notes. */
    val PREFS_DENY = BackupMerge.DEVICE_SWITCHES + setOf("auto_mono", "auto_mono_device", "lock_screen_player", "hq_resample",
        "mono", "stream_quality", "discord_app_id", "moods_write", "moods_full_done", "ignore_bt", "ignore_bt_devices", "ignore_bt_seen")
    val PREFS_DENY_PREFIX = listOf("sync_", "window", "startup", "tray", "notif", "discord", "bit_perfect", "pc_")

    fun prefSyncs(key: String) = !BackupMerge.isSignIn(key) && key !in PREFS_DENY && PREFS_DENY_PREFIX.none { key.startsWith(it) }

    /** The switches (as BackupMerge.switchesToSave writes them) that sync. */
    fun prefs(all: JsonObject) = JsonObject(all.filterKeys { prefSyncs(it) })

    /** The settings of state that sync: STATE_KEYS, the equalizer's EQ_KEYS with only portable outputs. */
    fun state(st: JsonObject): JsonObject {
        val out = LinkedHashMap<String, JsonElement>()
        for (k in STATE_KEYS) {
            val v = st[k] ?: continue
            out[k] = if (k == "eq" && v is JsonObject) eq(v) else v
        }
        return JsonObject(out)
    }

    private fun eq(e: JsonObject): JsonObject {
        val out = LinkedHashMap<String, JsonElement>()
        for (k in EQ_KEYS) {
            val v = e[k] ?: continue
            out[k] = if ((k == "outputs" || k == "devices") && v is JsonObject) JsonObject(v.filterKeys { portableOutput(it) }) else v
        }
        return JsonObject(out)
    }

    /** What a file holds when it isn't there yet. */
    fun empty(name: String) = if (name == "playlists.json" || name == "playlist-folders.json") "[]" else "{}"
}

/** How one part of the data comes together: three copies of it (as both devices last agreed, if they synced before;
 *  this device's; the other's), one result. */
sealed class Rule {
    /** One value: whichever side changed it since the last sync; both did, the newer (or the one you tapped Sync on). */
    object Value : Rule()
    /** The device's own copy of something both have (a song in a playlist: here it's this device's file): kept as it is. */
    object Own : Rule()
    /** An object, each field by its rule (other: the rest). ts: the field that says when it last changed, deciding
     *  which side's change wins when both changed (playlists' "updated"). */
    class Fields(val fields: Map<String, Rule> = emptyMap(), val other: Rule = Value, val ts: String? = null) : Rule()
    /** An object of items by key (songs' volumes, favorites…): added on either side kept, deleted on one side (and not
     *  changed on the other) gone, the rest merged item by item. */
    class ByKey(val child: Rule = Value) : Rule()
    /** A list of items, each found by key (playlists by id, songs by title and artist): as [ByKey], in order. newFirst:
     *  the other side's new ones go first (pins, newest first), else last. */
    class ById(val key: (JsonObject) -> String, val child: Rule = Value, val newFirst: Boolean = false) : Rule()
    /** A list of plain values, as a set (the never-show-again lists). */
    object Bag : Rule()
    /** Counts with the times they happened (plays, skips): what each side added since the last sync, added up.
     *  counts: (count field, its times field); latest: fields that keep the larger; the rest: [Own]. */
    class Tally(val counts: List<Pair<String, String>>, val latest: List<String>, val cap: Int) : Rule()
}

/** Bringing two devices' data together, newest change wins per item, nothing lost that only one side has. */
object SyncMerge {
    private fun playlistSong(o: JsonObject): String {
        val title = o["title"].str ?: ""
        if (title.isBlank()) return "id:" + (o["id"].str ?: "")
        return norm(cleanTitle(title)) + "|" + norm((o["artist"].str ?: "").split(",")[0])
    }

    private fun pinKey(o: JsonObject): String {
        val kind = o["kind"].str ?: ""; val id = o["id"].str ?: ""
        return if (kind == "artist") "artist:" + norm(id) else "$kind:${o["source"].str ?: ""}:$id"
    }

    /** Each file's rule. */
    val RULES: Map<String, Rule> = mapOf(
        "playlists.json" to Rule.ById({ it["id"].str ?: "" }, Rule.Fields(mapOf("tracks" to Rule.ById(::playlistSong, Rule.Own)), ts = "updated")),
        "playlist-folders.json" to Rule.ById({ it["id"].str ?: "" }, Rule.Fields(mapOf("playlists" to
            Rule.ById({ "${it["kind"].str}:${it["source"].str ?: ""}:${it["id"].str}" })))),
        "favorites.json" to Rule.ByKey(Rule.Fields(mapOf("track" to Rule.Own))),
        "plays.json" to Rule.ByKey(Rule.Tally(listOf("plays" to "at"), listOf("last"), 500)),
        "skips.json" to Rule.ByKey(Rule.Tally(listOf("skips" to "skips_at", "full" to "full_at"), listOf("last_skip"), 300)),
        "song-speeds.json" to Rule.ByKey(),
        "song-volume.json" to Rule.ByKey(),
        "song-volume-outputs.json" to Rule.ByKey(Rule.ByKey()),
        "song-eq.json" to Rule.ByKey(Rule.ByKey()),
        SyncRules.STATE to Rule.Fields(
            SyncRules.STATE_MAPS.associateWith<String, Rule> { Rule.ByKey() } + SyncRules.STATE_SETS.associateWith<String, Rule> { Rule.Bag } + mapOf<String, Rule>(
                "pins" to Rule.ById(::pinKey, newFirst = true),
                "eq" to Rule.Fields(mapOf("myPresets" to Rule.ByKey(), "profiles" to Rule.ByKey(), "devices" to Rule.ByKey(),
                    "outputs" to Rule.ByKey())))),
        SyncRules.SWITCHES to Rule.ByKey(),
    )

    /** Every file both sides sent, merged. base: the result of the last sync between these two (null: never synced, so
     *  nothing counts as deleted); preferLocal: when both changed the same thing and nothing says which is newer, this
     *  device's wins (it's the one Sync was tapped on). */
    fun mergeAll(base: Map<String, JsonElement>?, local: Map<String, JsonElement>, remote: Map<String, JsonElement>,
                 preferLocal: Boolean): LinkedHashMap<String, JsonElement> {
        val m = Merger(preferLocal)
        val out = LinkedHashMap<String, JsonElement>()
        for ((name, rule) in RULES) {
            val l = local[name] ?: continue
            val r = remote[name] ?: continue
            out[name] = m.merge(rule, base?.get(name), l, r, null) ?: JsonObject(emptyMap())
        }
        return out
    }

    private class Merger(val preferLocal: Boolean) {
        fun merge(rule: Rule, b: JsonElement?, l0: JsonElement?, r0: JsonElement?, newer: Boolean?): JsonElement? {
            val l = l0.real(); val r = r0.real(); val base = b.real()
            return when (rule) {
                Rule.Value -> value(base, l, r, newer)
                Rule.Own -> l ?: r
                Rule.Bag -> bag(base, l, r)
                is Rule.Fields -> if (l is JsonObject && r is JsonObject) fields(rule, base as? JsonObject, l, r, newer) else value(base, l, r, newer)
                is Rule.ByKey -> byKey(rule, base as? JsonObject, l as? JsonObject, r as? JsonObject, newer)
                is Rule.ById -> byId(rule, base as? JsonArray, l as? JsonArray, r as? JsonArray, newer)
                is Rule.Tally -> if (l is JsonObject && r is JsonObject) tally(rule, base as? JsonObject, l, r) else l ?: r
            }
        }

        fun value(b: JsonElement?, l: JsonElement?, r: JsonElement?, newer: Boolean?): JsonElement? = when {
            l == r -> l
            b == l -> r          // only the other side changed it
            b == r -> l          // only this side did
            else -> if (newer ?: preferLocal) l else r
        }

        fun fields(rule: Rule.Fields, b: JsonObject?, l: JsonObject, r: JsonObject, inherited: Boolean?): JsonObject {
            val newer = rule.ts?.let { ts -> val a = l[ts].dbl; val c = r[ts].dbl; if (a != null && c != null && a != c) a > c else null } ?: inherited
            val out = LinkedHashMap<String, JsonElement>()
            for (k in (l.keys + r.keys)) {
                val v = if (k == rule.ts) listOfNotNull(l[k], r[k]).maxByOrNull { it.dbl ?: 0.0 }
                    else merge(rule.fields[k] ?: rule.other, b?.get(k), l[k], r[k], newer)
                if (v != null) out[k] = v
            }
            return JsonObject(out)
        }

        /** Whether an item one side doesn't have was deleted there: the other side's is as it was at the last sync. */
        private fun gone(child: Rule, base: JsonElement?, kept: JsonElement) = base != null && same(child, base, kept)

        fun byKey(rule: Rule.ByKey, b: JsonObject?, l: JsonObject?, r: JsonObject?, newer: Boolean?): JsonObject {
            val lm = l ?: JsonObject(emptyMap()); val rm = r ?: JsonObject(emptyMap())
            val out = LinkedHashMap<String, JsonElement>()
            for (k in (lm.keys + rm.keys)) {
                val lv = lm[k].real(); val rv = rm[k].real(); val bv = b?.get(k).real()
                val v = when {
                    lv != null && rv != null -> merge(rule.child, bv, lv, rv, newer)
                    lv != null -> lv.takeUnless { gone(rule.child, bv, it) }
                    rv != null -> rv.takeUnless { gone(rule.child, bv, it) }
                    else -> null
                }
                if (v != null) out[k] = v
            }
            return JsonObject(out)
        }

        fun byId(rule: Rule.ById, b: JsonArray?, l: JsonArray?, r: JsonArray?, newer: Boolean?): JsonArray {
            val lm = keyed(rule, l); val rm = keyed(rule, r); val bm = keyed(rule, b)
            val merged = LinkedHashMap<String, JsonElement>()
            for (k in (lm.keys + rm.keys)) {
                val lv = lm[k]; val rv = rm[k]; val bv = bm[k]
                val v = when {
                    lv != null && rv != null -> merge(rule.child, bv, lv, rv, newer)
                    lv != null -> lv.takeUnless { gone(rule.child, bv, it) }
                    rv != null -> rv.takeUnless { gone(rule.child, bv, it) }
                    else -> null
                }
                if (v != null) merged[k] = v
            }
            // In the order of the side whose change wins, the other side's new ones before or after.
            val first = if (newer ?: preferLocal) lm else rm
            val second = if (first === lm) rm else lm
            val main = first.keys.filter { it in merged }
            val added = second.keys.filter { it in merged && it !in first }
            return JsonArray((if (rule.newFirst) added + main else main + added).map { merged[it]!! })
        }

        private fun keyed(rule: Rule.ById, a: JsonArray?): LinkedHashMap<String, JsonElement> {
            val out = LinkedHashMap<String, JsonElement>()
            for (e in a.orEmpty()) {
                val o = e as? JsonObject ?: continue
                val k0 = rule.key(o)
                var k = k0; var n = 2
                while (k in out) k = "$k0#${n++}"   // the same song twice in a playlist stays twice
                out[k] = o
            }
            return out
        }

        fun bag(b: JsonElement?, l: JsonElement?, r: JsonElement?): JsonArray {
            val ls = (l as? JsonArray).orEmpty(); val rs = (r as? JsonArray).orEmpty(); val bs = (b as? JsonArray).orEmpty().toSet()
            val lset = ls.toSet(); val rset = rs.toSet()
            return JsonArray((ls + rs).distinct().filter { (it in lset && it in rset) || it !in bs })
        }

        fun tally(rule: Rule.Tally, b: JsonObject?, l: JsonObject, r: JsonObject): JsonObject {
            val out = LinkedHashMap<String, JsonElement>()
            for (k in (l.keys + r.keys)) (l[k] ?: r[k])?.let { out[k] = it }   // the rest: this device's (its song)
            for ((countKey, atKey) in rule.counts) {
                val lc = l[countKey].long ?: 0L; val rc = r[countKey].long ?: 0L
                val la = times(l[atKey]); val ra = times(r[atKey])
                val bc = b?.get(countKey).long
                val count = if (bc != null) lc + rc - bc   // what each side added since the last sync
                    else {   // never synced: the same plays (from a backup of the other) counted once, by their times
                        val both = la.intersect(ra.toSet()).size
                        val cappedAndSame = both > 0 && both == minOf(la.size, ra.size) && maxOf(la.size, ra.size) >= rule.cap
                        if (cappedAndSame) maxOf(lc, rc) else maxOf(lc, rc, lc + rc - both)
                    }
                out[countKey] = JsonPrimitive(maxOf(0L, count))
                if (la.isNotEmpty() || ra.isNotEmpty()) out[atKey] = JsonArray((la + ra).distinct().sorted().takeLast(rule.cap).map { JsonPrimitive(it) })
            }
            for (k in rule.latest) listOfNotNull(l[k], r[k]).maxByOrNull { it.dbl ?: 0.0 }?.let { out[k] = it }
            return JsonObject(out)
        }

        private fun times(e: JsonElement?): List<Long> = (e as? JsonArray).orEmpty().mapNotNull { it.long }
    }

    private fun JsonElement?.real(): JsonElement? = if (this == null || this is JsonNull) null else this

    /** Whether a and b are the same, as rule sees it (a song's own copy on each device counts as the same). */
    fun same(rule: Rule, a0: JsonElement?, b0: JsonElement?): Boolean {
        val a = a0.real(); val b = b0.real()
        if (a == null || b == null) return when (rule) {
            Rule.Own -> (a == null) == (b == null)
            is Rule.ByKey, is Rule.ById, Rule.Bag -> size(a) == 0 && size(b) == 0
            else -> a == b
        }
        return when (rule) {
            Rule.Value -> a == b
            Rule.Own -> true
            Rule.Bag -> (a as? JsonArray).orEmpty().toSet() == (b as? JsonArray).orEmpty().toSet()
            is Rule.Fields -> if (a is JsonObject && b is JsonObject) (a.keys + b.keys).all { same(rule.fields[it] ?: rule.other, a[it], b[it]) } else a == b
            is Rule.ByKey -> if (a is JsonObject && b is JsonObject) a.keys == b.keys && a.keys.all { same(rule.child, a[it], b[it]) } else a == b
            is Rule.ById -> {
                val x = keyedFor(rule, a); val y = keyedFor(rule, b)
                x.keys == y.keys && x.keys.all { same(rule.child, x[it], y[it]) }
            }
            is Rule.Tally -> (rule.counts.flatMap { listOf(it.first) } + rule.latest).all { (a as? JsonObject)?.get(it) == (b as? JsonObject)?.get(it) }
        }
    }

    private fun keyedFor(rule: Rule.ById, e: JsonElement?): Map<String, JsonElement> {
        val out = LinkedHashMap<String, JsonElement>()
        for (x in (e as? JsonArray).orEmpty()) { val o = x as? JsonObject ?: continue; var k = rule.key(o); var n = 2; val k0 = k; while (k in out) k = "$k0#${n++}"; out[k] = o }
        return out
    }

    private fun size(e: JsonElement?): Int = when (e) { is JsonObject -> e.size; is JsonArray -> e.size; else -> 0 }

    // ---- what the sync did, in words ----

    /** How many of each thing there are after the sync, and how many items changed here. */
    class Summary(val totals: List<Pair<String, Int>>, val changed: Int) {
        /** "Synced: 12 playlists, 340 song volumes, 3 Sound presets · 25 changes here". */
        fun text(): String {
            val parts = totals.filter { it.second > 0 }.take(7).joinToString(", ") { (what, n) -> "$n ${if (n == 1) one(what) else what}" }
            val ch = when (changed) { 0 -> "nothing new here"; 1 -> "1 change here"; else -> "$changed changes here" }
            return "Synced: " + (parts.ifEmpty { "nothing to sync yet" }) + " · " + ch
        }

        private fun one(what: String) = if (what == "songs' plays") "song's plays" else what.removeSuffix("s")
    }

    fun summary(merged: Map<String, JsonElement>, local: Map<String, JsonElement>): Summary {
        val t = ArrayList<Pair<String, Int>>()
        val lists = (merged["playlists.json"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        merged["playlists.json"]?.let { t += "playlists" to lists.count { it["smart"].real() == null } }
        merged["playlists.json"]?.let { t += "smart playlists" to lists.count { it["smart"].real() != null } }
        merged["song-volume.json"]?.let {
            t += "song volumes" to size(it) + (merged["song-volume-outputs.json"] as? JsonObject).orNone().values.sumOf { o -> size(o) }
        }
        merged["song-eq.json"]?.let { t += "Custom EQs" to (it as? JsonObject).orNone().values.sumOf { o -> size(o) } }
        val st = merged[SyncRules.STATE] as? JsonObject
        val eq = st?.get("eq") as? JsonObject
        if (st != null) {
            t += "Sound presets" to size(eq?.get("myPresets"))
            t += "headphone corrections" to size(eq?.get("profiles"))
            t += "output EQs" to size(eq?.get("outputs"))
        }
        merged["favorites.json"]?.let { t += "favorites" to size(it) }
        if (st != null) t += "pins" to size(st["pins"])
        merged["playlist-folders.json"]?.let { t += "playlist folders" to size(it) }
        merged["song-speeds.json"]?.let { t += "song speeds" to size(it) }
        merged["plays.json"]?.let { t += "songs' plays" to size(it) }
        var changed = 0
        for ((name, v) in merged) RULES[name]?.let { changed += diffCount(it, local[name], v) }
        return Summary(t, changed)
    }

    /** How many items differ between a and b: each item of a list or a keyed object, each setting. */
    fun diffCount(rule: Rule, a: JsonElement?, b: JsonElement?): Int = when (rule) {
        is Rule.ByKey -> {
            val x = (a as? JsonObject).orNone(); val y = (b as? JsonObject).orNone()
            (x.keys + y.keys).count { !same(rule.child, x[it], y[it]) }
        }
        is Rule.ById -> {
            val x = keyedFor(rule, a); val y = keyedFor(rule, b)
            (x.keys + y.keys).count { !same(rule.child, x[it], y[it]) }
        }
        is Rule.Fields -> {
            val x = (a as? JsonObject).orNone(); val y = (b as? JsonObject).orNone()
            (x.keys + y.keys).sumOf { k ->
                when (val c = rule.fields[k] ?: rule.other) {
                    is Rule.ByKey, is Rule.ById, is Rule.Fields -> diffCount(c, x[k], y[k])
                    Rule.Bag -> (x[k] as? JsonArray).orEmpty().toSet().let { p -> val q = (y[k] as? JsonArray).orEmpty().toSet(); (p - q).size + (q - p).size }
                    else -> if (same(c, x[k], y[k])) 0 else 1
                }
            }
        }
        else -> if (same(rule, a, b)) 0 else 1
    }

    private fun JsonObject?.orNone(): JsonObject = this ?: JsonObject(emptyMap())
}
