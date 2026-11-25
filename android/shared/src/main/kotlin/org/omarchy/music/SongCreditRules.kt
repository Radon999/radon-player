package org.omarchy.music

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/** A song's credits (who wrote it, produced it and played on it) as Tidal, Genius and MusicBrainz give them, put in
 *  groups to show; and a person matched to your songs by their name, however the services write it. */
object SongCreditRules {
    /** One person's part in a song: the role as the service named it ("Composer", "guitar") and their name. */
    @Serializable
    data class Credit(val role: String, val name: String)

    /** A person in a group as shown: their name, and what they did when that says more than the group's heading. */
    data class Person(val name: String, val roles: List<String>)
    data class Group(val title: String, val people: List<Person>)

    /** The groups, in the order they're shown. */
    val GROUPS = listOf("Featuring", "Written by", "Produced by", "Performers", "Remixed by", "Mixed by", "Mastered by",
        "Engineered by", "Other")

    /** Songs none of the services had credits for are looked up again after this many days (they may have them by then). */
    const val RETRY_DAYS = 30

    // Words of a role that make it a player's part: an instrument, singing, or playing as such.
    private val PLAYED = Regex("vocal|voice|sing|choir|\\brap|guitar|bass|drum|percussion|piano|keyboard|synth|organ|string|violin|viola|" +
        "cello|horn|trumpet|trombone|sax|flute|clarinet|oboe|harp|banjo|mandolin|ukulele|accordion|harmonica|orchestra|instrument|" +
        "performer|programm|turntable|scratch|beatbox|main artist|^artist$|\\bband\\b|ensemble|whistl|clap")

    // Roles about a place or the pictures ("Recorded At", "Photography", "Video Director"): Other, whatever words they share.
    private val ELSEWHERE = Regex("\\bat$|studio|location|venue|video|photo|graphic|art direction|artwork|design|cover")

    // Roles about who owns or sells the song, not who made it: left out.
    private val RIGHTS = Regex("copyright|publish|label|distribut|licens|rights|℗|©|a&r|management|manager|marketing|booking")

    /** The group a role goes in ("Mixing Engineer" is Mixed by); null for one about rights or selling it, left out. */
    fun group(role: String): String? {
        val r = role.lowercase().trim()
        return when {
            RIGHTS.containsMatchIn(r) -> null
            ELSEWHERE.containsMatchIn(r) -> "Other"
            "feat" in r -> "Featuring"
            "master" in r -> "Mastered by"
            "remix" in r -> "Remixed by"
            "mix" in r -> "Mixed by"
            "produc" in r -> "Produced by"
            WRITTEN.containsMatchIn(r) -> "Written by"
            "engineer" in r || "recorded" in r || r == "recording" -> "Engineered by"
            PLAYED.containsMatchIn(r) -> "Performers"
            else -> "Other"
        }
    }

    private val WRITTEN = Regex("writ|compos|lyric|author|librett|words|music by")

    // Roles that only say again what their group's heading says: not shown beside the name.
    private val PLAIN = setOf("producer", "produced by", "production", "writer", "written by", "songwriter", "featured artist", "featuring",
        "featured", "feat", "mixer", "mix", "mixing", "mixing engineer", "mix engineer", "mixed by", "mastering", "mastering engineer",
        "mastered by", "engineer", "remixer", "remixed by", "performer", "main artist", "artist")

    /** The credits in their groups, in the groups' order: each person once in a group (their name as first written), with
     *  their roles in it, each once. Blank names and rights-holders are left out. */
    fun grouped(credits: List<Credit>): List<Group> {
        val by = LinkedHashMap<String, LinkedHashMap<String, Pair<String, LinkedHashSet<String>>>>()
        for (c in credits) {
            val name = c.name.trim()
            if (name.isEmpty()) continue
            val g = group(c.role) ?: continue
            val people = by.getOrPut(g) { LinkedHashMap() }
            val (_, roles) = people.getOrPut(nameKey(name)) { name to LinkedHashSet() }
            val role = c.role.trim().replace(Regex("\\s+"), " ")
            if (role.isNotEmpty() && role.lowercase() !in PLAIN && roles.none { it.equals(role, ignoreCase = true) })
                roles += role.replaceFirstChar { it.uppercase() }
        }
        return GROUPS.mapNotNull { g ->
            by[g]?.values?.map { (name, roles) -> Person(name, roles.toList()) }?.let { Group(g, it) }
        }
    }

    /** A person's name for matching: letters and digits only, any case or accent, without a leading "The" ("The Weeknd",
     *  "the weeknd" and "Weeknd" are one; "Max Martin" and "MAX-MARTIN" too). A name of other alphabets is kept as written. */
    fun nameKey(name: String): String {
        val bare = name.trim().replace(LEADING_THE, "")
        return squashKey(bare).ifEmpty { bare.lowercase().replace(Regex("\\s+"), " ") }
    }
    private val LEADING_THE = Regex("(?i)^the\\s+")

    /** The artists a song names itself: its artist tag, each of them, and those featured in its title. */
    fun songArtists(title: String, artist: String): Set<String> =
        (splitArtists(artist) + featured(title)).mapTo(LinkedHashSet()) { nameKey(it) }.apply { remove("") }

    /** Of songs, the ones person worked on, each with how: "Artist" when the song is under their name (or features them),
     *  and the roles its credits give them. In the order given. */
    fun <T> workedOn(person: String, songs: List<T>, names: (T) -> Pair<String, String>, credits: (T) -> List<Credit>?): List<Pair<T, List<String>>> {
        val key = nameKey(person)
        if (key.isEmpty()) return emptyList()
        return songs.mapNotNull { s ->
            val (title, artist) = names(s)
            val how = LinkedHashSet<String>()
            if (key in songArtists(title, artist)) how += "Artist"
            credits(s)?.filter { nameKey(it.name) == key && group(it.role) != null }?.forEach { c ->
                val role = c.role.trim().replaceFirstChar { it.uppercase() }
                // "Main artist" says no more than Artist does
                if ("Artist" in how && role.lowercase() in setOf("main artist", "artist", "primary artist")) return@forEach
                if (role.isNotEmpty() && how.none { it.equals(role, ignoreCase = true) }) how += role
            }
            if (how.isEmpty()) null else s to how.toList()
        }
    }

    /** Whether a song's saved credits are looked up again: never when some were found; after [RETRY_DAYS] when none were. */
    fun lookAgain(found: Boolean, savedAt: Double, now: Double): Boolean = !found && now - savedAt > RETRY_DAYS * 86400.0

    // ---- what each service sends ----

    /** Tidal's credits of a track: a list of {type, contributors: [{name}]} (its "credits"), or {items: [{name, role}]}
     *  (its "contributors"). */
    fun fromTidal(j: JsonElement?): List<Credit> {
        val rows = (j as? JsonArray) ?: j["items"].list
        return rows.flatMap { r ->
            val type = r["type"].str
            if (type != null) r["contributors"].list.mapNotNull { c -> c["name"].str?.let { Credit(type, it) } }
            else listOfNotNull(r["name"].str?.let { n -> r["role"].str?.let { Credit(it, n) } })
        }
    }

    /** Genius's song (its "song" object): the writers, producers and featured artists, and its other credits
     *  (custom_performances: a label, such as "Mixing Engineer", and its artists). */
    fun fromGenius(song: JsonElement?): List<Credit> {
        fun names(key: String, role: String) = song[key].list.mapNotNull { a -> a["name"].str?.let { Credit(role, it) } }
        return names("featured_artists", "Featured artist") + names("writer_artists", "Writer") + names("producer_artists", "Producer") +
            song["custom_performances"].list.flatMap { p ->
                val label = p["label"].str ?: return@flatMap emptyList()
                p["artists"].list.mapNotNull { a -> a["name"].str?.let { Credit(label, it) } }
            }
    }

    // MusicBrainz's words that only say how someone took part ("additional", "guest"), not in what.
    private val MB_MODIFIERS = setOf("additional", "guest", "solo", "co", "executive", "assistant", "associate", "lead", "background", "choir")

    /** A MusicBrainz recording looked up with inc=artist-credits+artist-rels+work-rels+work-level-rels: who's featured
     *  in its artist credit, the people of its relationships (producer, mix, instrument: guitar…), and the writers of its
     *  work (composer, lyricist, writer). */
    fun fromMusicBrainz(rec: JsonElement?): List<Credit> {
        val out = ArrayList<Credit>()
        var featuring = false
        for (a in rec["artist-credit"].list) {
            val n = a["name"].str ?: a.path("artist", "name").str
            if (featuring && n != null) out += Credit("Featured artist", n)
            if (Regex("(?i)\\b(feat|ft|featuring|with)\\b").containsMatchIn(a["joinphrase"].str ?: "")) featuring = true
        }
        fun role(rel: JsonElement): String {
            val type = rel["type"].str ?: ""
            val attrs = rel["attributes"].list.mapNotNull { it.str }
            val what = attrs.filter { it.lowercase() !in MB_MODIFIERS }
            return when (type) {
                "instrument" -> what.joinToString(", ").ifEmpty { "instruments" }
                "vocal" -> attrs.joinToString(", ").ifEmpty { "vocals" }.let { if ("vocal" in it) it else "$it vocals" }
                "producer" -> (attrs.filter { it in setOf("co", "executive", "additional", "assistant") }.map { if (it == "co") "co-" else "$it " }
                    .joinToString("") + "producer")
                "mix" -> "mixing engineer"
                "mastering" -> "mastering engineer"
                "recording", "sound", "audio" -> "$type engineer"
                else -> type
            }
        }
        for (rel in rec["relations"].list) {
            when (rel["target-type"].str) {
                "artist" -> rel.path("artist", "name").str?.let { out += Credit(role(rel), it) }
                "work" -> for (w in rel.path("work", "relations").list) {
                    if (w["target-type"].str == "artist") w.path("artist", "name").str?.let { out += Credit(role(w), it) }
                }
            }
        }
        return out
    }
}
