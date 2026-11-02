package org.omarchy.music

import kotlinx.serialization.Serializable

// Smart playlists' rules: which of your songs a smart playlist holds, in what order, and how many. Pure (no app),
// so the phone and the PC pick the same songs and it can be tested. The app fills in each song's SongFacts.

/** One rule of a smart playlist: what it looks at (field, a key of [SmartRules.FIELDS]), how (op, a key of its
 *  [SmartRules.ops]), and against what: a word (value) or several (values: any of them fits), or a number (n). */
@Serializable
data class SmartRule(val field: String, val op: String = "", val value: String = "", val values: List<String> = emptyList(),
                     val n: Double = 0.0) {
    /** The words it looks for: values, else value (blank ones left out). */
    val targets: List<String> get() = values.ifEmpty { listOf(value) }.map { it.trim() }.filter { it.isNotEmpty() }
}

/** What the rules know of one song. The app fills in only what the rules ask for ([SmartRules.needs]); the rest stay
 *  at their defaults. Times are seconds since 1970 (0: never or not known). */
data class SongFacts(
    val title: String = "", val artist: String = "", val album: String = "",
    val genre: String = "",                  // the file's genre tag ("Indie Rock; Pop"), "" if none
    val moods: List<String> = emptyList(),   // its moods, its main one first
    val release: String? = null,             // "album", "ep" or "single" (null: not one of yours)
    val format: String? = null,              // "FLAC 24", "FLAC 16", "MP3", "M4A"… (null: not read yet)
    val downloaded: Boolean = false,         // downloaded in the app
    val myFile: Boolean = false,             // one of your own files (from your music folders)
    val favorite: Boolean = false,
    val plays: Int = 0,
    val lastPlayed: Long = 0,
    val added: Double = 0.0,
    val skips: Int = 0,
    val heardOut: Int = 0,
    val length: Double = 0.0,                // seconds
    val folder: String = "",                 // "Music/Rock/Album/"
)

object SmartRules {
    /** What a rule can look at, in the order the editor lists them. */
    val FIELDS = linkedMapOf(
        "genre" to "Genre", "mood" to "Mood", "artist" to "Artist", "album" to "Album", "title" to "Title",
        "release" to "Release type", "format" to "Format", "source" to "Downloaded or my files", "favorite" to "Favorite",
        "plays" to "Play count", "lastplayed" to "Last played", "never" to "Never played", "added" to "Added",
        "skips" to "Skips", "length" to "Length", "folder" to "Folder",
    )

    /** How each field can be matched: (op, its words), the first being the one a new rule starts with. */
    fun ops(field: String): List<Pair<String, String>> = when (field) {
        "genre" -> listOf("contains" to "contains", "is" to "is", "not" to "isn't")
        "mood" -> listOf("is" to "is", "not" to "isn't")
        "artist", "album", "title" -> listOf("contains" to "contains", "is" to "is", "not" to "doesn't contain")
        "release", "format" -> listOf("is" to "is", "not" to "isn't")
        "source" -> listOf("downloaded" to "is downloaded", "files" to "is one of my files")
        "favorite" -> listOf("yes" to "is a favorite", "no" to "isn't a favorite")
        "plays" -> listOf("more" to "more than", "less" to "less than")
        "lastplayed" -> listOf("within" to "within the last", "not" to "not within the last")
        "never" -> listOf("yes" to "")
        "added" -> listOf("within" to "in the last", "not" to "more than")
        "skips" -> listOf("lot" to "skipped a lot", "never" to "never skipped", "more" to "skipped more than")
        "length" -> listOf("longer" to "longer than", "shorter" to "shorter than")
        "folder" -> listOf("under" to "is or is under", "is" to "is exactly")
        else -> emptyList()
    }

    /** The choices a field's value is picked from (empty: typed, or a number). */
    val RELEASES = linkedMapOf("album" to "Album", "ep" to "EP", "single" to "Single")
    val FORMATS = listOf("FLAC 24", "FLAC 16", "FLAC", "WAV", "MP3", "M4A", "OGG", "OPUS")

    /** Whether the rule takes a number (n), and what the number counts. */
    fun unit(field: String, op: String): String? = when (field) {
        "plays" -> "plays"
        "lastplayed" -> "days"
        "added" -> "days" + if (op == "not") " ago" else ""
        "skips" -> if (op == "more") "skips" else null
        "length" -> "minutes"
        else -> null
    }

    /** A new rule of field, with a sensible start. */
    fun fresh(field: String): SmartRule {
        val op = ops(field).firstOrNull()?.first ?: ""
        return when (field) {
            "plays" -> SmartRule(field, op, n = 10.0)
            "lastplayed" -> SmartRule(field, op, n = 30.0)
            "added" -> SmartRule(field, op, n = 30.0)
            "skips" -> SmartRule(field, op, n = 3.0)
            "length" -> SmartRule(field, op, n = 5.0)
            "release" -> SmartRule(field, op, "album")
            "format" -> SmartRule(field, op, "FLAC 24")
            else -> SmartRule(field, op)
        }
    }

    /** The orders a smart playlist can have: (key, words). */
    val ORDERS = linkedMapOf(
        "random" to "Random", "plays" to "Most played", "plays-least" to "Least played", "added" to "Recently added",
        "added-old" to "Added longest ago", "played" to "Recently played", "played-old" to "Not played for the longest",
        "title" to "Title (A–Z)", "artist" to "Artist (A–Z)", "album" to "Album (A–Z)", "length" to "Longest first",
        "length-short" to "Shortest first",
    )

    /** How many songs it can hold (0: all of them). */
    val LIMITS = listOf(25, 50, 100, 250, 0)

    /** Played this much and skipped at least as often as heard to the end: skipped a lot. */
    const val SKIPS_A_LOT = 3

    /** A rule in words: "Genre contains Rock", "Last played not within the last 30 days". */
    fun words(r: SmartRule): String {
        val field = FIELDS[r.field] ?: r.field
        val op = ops(r.field).firstOrNull { it.first == r.op }?.second ?: r.op
        val what = when {
            r.field == "release" -> r.targets.joinToString(" or ") { RELEASES[it] ?: it }
            unit(r.field, r.op) != null -> "${fmtNum(r.n)} ${unit(r.field, r.op)}"
            else -> r.targets.joinToString(" or ")
        }
        return listOf(field, op, what).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun fmtNum(n: Double) = if (n == Math.floor(n)) n.toLong().toString() else n.toString()

    /** Which facts the rules and order look at (field names of [SongFacts], loosely: "plays", "lastPlayed"…). */
    fun needs(rules: List<SmartRule>, order: String): Set<String> {
        val out = HashSet<String>()
        for (r in rules) when (r.field) {
            "never" -> { out += "plays"; out += "lastplayed" }
            "skips" -> out += "skips"
            "source" -> out += "source"
            else -> out += r.field
        }
        when (order) {
            "plays", "plays-least" -> out += "plays"
            "played", "played-old" -> out += "lastplayed"
            "added", "added-old" -> out += "added"
        }
        return out
    }

    private fun has(text: String, word: String) = norm(text).contains(norm(word))
    private fun same(a: String, b: String) = norm(a) == norm(b)
    private fun folderKey(p: String) = p.replace('\\', '/').trim().trimEnd('/').lowercase() + "/"

    /** Whether the song fits one rule (now: seconds since 1970). A rule with nothing to look for fits every song. */
    fun fits(r: SmartRule, f: SongFacts, now: Long): Boolean {
        val words = r.targets
        val day = 86_400.0
        return when (r.field) {
            "genre" -> when (r.op) {
                "is" -> words.isEmpty() || words.any { w -> genreParts(f.genre).any { same(it, w) } || same(f.genre, w) }
                "not" -> words.none { w -> genreFits(f.genre, w) || same(f.genre, w) }
                else -> words.isEmpty() || words.any { w -> genreFits(f.genre, w) || (w.length >= 4 && has(f.genre, w)) }
            }
            "mood" -> if (r.op == "not") words.none { w -> f.moods.any { it.equals(w, true) } }
                else words.isEmpty() || words.any { w -> f.moods.any { it.equals(w, true) } }
            "artist", "album", "title" -> {
                val text = when (r.field) { "artist" -> f.artist; "album" -> f.album; else -> f.title }
                when (r.op) {
                    "is" -> words.isEmpty() || words.any { w -> same(text, w) || (r.field == "artist" && splitArtists(text).any { same(it, w) }) }
                    "not" -> words.none { has(text, it) }
                    else -> words.isEmpty() || words.any { has(text, it) }
                }
            }
            "release" -> if (r.op == "not") f.release != null && words.none { it.equals(f.release, true) }
                else f.release != null && (words.isEmpty() || words.any { it.equals(f.release, true) })
            "format" -> {
                val fmt = f.format?.uppercase()
                val hit = fmt != null && words.any { w -> val u = w.uppercase(); fmt == u || fmt.startsWith("$u ") }
                if (r.op == "not") fmt != null && !hit else hit || (fmt != null && words.isEmpty())
            }
            "source" -> if (r.op == "files") f.myFile else f.downloaded
            "favorite" -> if (r.op == "no") !f.favorite else f.favorite
            "plays" -> if (r.op == "less") f.plays < r.n else f.plays > r.n
            "lastplayed" -> {
                val within = f.lastPlayed > 0 && f.lastPlayed >= now - r.n * day
                if (r.op == "not") !within else within
            }
            "never" -> f.plays == 0 && f.lastPlayed == 0L
            "added" -> {
                val within = f.added > 0 && f.added >= now - r.n * day
                if (r.op == "not") f.added > 0 && !within else within
            }
            "skips" -> when (r.op) {
                "never" -> f.skips == 0 && f.heardOut > 0
                "more" -> f.skips > r.n
                else -> f.skips >= SKIPS_A_LOT && f.skips >= f.heardOut
            }
            "length" -> f.length > 0 && if (r.op == "shorter") f.length < r.n * 60 else f.length > r.n * 60
            "folder" -> {
                val here = folderKey(f.folder)
                words.isEmpty() || words.any { w -> val k = folderKey(w); if (r.op == "is") here == k else here.startsWith(k) }
            }
            else -> true   // a rule this version doesn't know (made by a newer one): left out, not every song dropped
        }
    }

    /** Whether the song fits the rules: all of them, or any one (any). No rules: every song. */
    fun fits(rules: List<SmartRule>, any: Boolean, f: SongFacts, now: Long): Boolean =
        rules.isEmpty() || if (any) rules.any { fits(it, f, now) } else rules.all { fits(it, f, now) }

    /** The smart playlist's songs: those of items that fit the rules, in order, at most limit (0: all). seed: the
     *  random order's, so the same seed gives the same order. */
    fun <T> pick(items: List<T>, facts: (T) -> SongFacts, rules: List<SmartRule>, any: Boolean, order: String, limit: Int,
                 now: Long, seed: Long = 0): List<T> {
        val hits = items.mapNotNull { t -> val f = facts(t); if (fits(rules, any, f, now)) t to f else null }
        val byTitle = compareBy<Pair<T, SongFacts>>({ norm(it.second.title) }, { norm(it.second.artist) })
        val sorted = when (order) {
            "plays" -> hits.sortedWith(compareByDescending<Pair<T, SongFacts>> { it.second.plays }.then(byTitle))
            "plays-least" -> hits.sortedWith(compareBy<Pair<T, SongFacts>> { it.second.plays }.then(byTitle))
            "added" -> hits.sortedWith(compareByDescending<Pair<T, SongFacts>> { it.second.added }.then(byTitle))
            "added-old" -> hits.sortedWith(compareBy<Pair<T, SongFacts>> { if (it.second.added > 0) it.second.added else Double.MAX_VALUE }.then(byTitle))
            "played" -> hits.sortedWith(compareByDescending<Pair<T, SongFacts>> { it.second.lastPlayed }.then(byTitle))
            "played-old" -> hits.sortedWith(compareBy<Pair<T, SongFacts>> { it.second.lastPlayed }.then(byTitle))
            "title" -> hits.sortedWith(byTitle)
            "artist" -> hits.sortedWith(compareBy<Pair<T, SongFacts>>({ norm(it.second.artist) }, { norm(it.second.album) }, { norm(it.second.title) }))
            "album" -> hits.sortedWith(compareBy<Pair<T, SongFacts>>({ norm(it.second.album) }, { norm(it.second.artist) }, { norm(it.second.title) }))
            "length" -> hits.sortedWith(compareByDescending<Pair<T, SongFacts>> { it.second.length }.then(byTitle))
            "length-short" -> hits.sortedWith(compareBy<Pair<T, SongFacts>> { it.second.length }.then(byTitle))
            else -> hits.shuffled(java.util.Random(seed))
        }
        return (if (limit > 0) sorted.take(limit) else sorted).map { it.first }
    }

    /** A ready-made smart playlist: its name, what it's for, and its rules. */
    data class Idea(val name: String, val about: String, val rules: List<SmartRule>, val any: Boolean = false,
                    val order: String = "random", val limit: Int = 0)

    /** The smart playlists offered with one tap. */
    val IDEAS = listOf(
        Idea("Most played I haven't heard in a month", "Old favourites to come back to",
            listOf(SmartRule("plays", "more", n = 5.0), SmartRule("lastplayed", "not", n = 30.0)), order = "plays", limit = 50),
        Idea("Never played", "Songs of yours you haven't heard yet", listOf(SmartRule("never", "yes")), order = "added"),
        Idea("Recently added", "What came into your music in the last month", listOf(SmartRule("added", "within", n = 30.0)), order = "added"),
        Idea("Chill evening", "Chill, calm and dreamy songs", listOf(SmartRule("mood", "is", values = listOf("Chill", "Calm", "Dreamy"))), limit = 50),
        Idea("Workout", "Energetic and hype songs", listOf(SmartRule("mood", "is", values = listOf("Energetic", "Hype", "Intense"))), limit = 50),
        Idea("Favorites I skip", "Favorites you keep skipping: still favorites?",
            listOf(SmartRule("favorite", "yes"), SmartRule("skips", "lot")), order = "plays"),
        Idea("Never skipped", "Songs you always hear to the end", listOf(SmartRule("skips", "never")), order = "plays", limit = 100),
        Idea("Forgotten favorites", "Favorites you haven't played in three months",
            listOf(SmartRule("favorite", "yes"), SmartRule("lastplayed", "not", n = 90.0)), order = "played-old", limit = 50),
        Idea("Hi-res", "Your 24-bit FLACs", listOf(SmartRule("format", "is", "FLAC 24")), order = "artist"),
        Idea("Heavy rotation", "Played most in the last two weeks",
            listOf(SmartRule("lastplayed", "within", n = 14.0), SmartRule("plays", "more", n = 3.0)), order = "plays", limit = 50),
        Idea("Deep cuts", "Songs of yours played only once or twice", listOf(SmartRule("plays", "more", n = 0.0),
            SmartRule("plays", "less", n = 3.0)), limit = 50),
        Idea("Party", "Party and happy songs", listOf(SmartRule("mood", "is", values = listOf("Party", "Happy"))), limit = 50),
        Idea("Focus", "Focus and calm songs to work to", listOf(SmartRule("mood", "is", values = listOf("Focus", "Calm"))), limit = 50),
        Idea("Long songs", "Over seven minutes", listOf(SmartRule("length", "longer", n = 7.0)), order = "length"),
        Idea("EPs and singles", "Songs from your EPs and singles", listOf(SmartRule("release", "is", values = listOf("ep", "single"))), order = "added"),
    )
}
