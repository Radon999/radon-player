package org.omarchy.music

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/** The DJ, like Spotify's, as on the desktop: sets of a few songs, each begun with a line from the DJ,
 *  made from your listening: what you've had on repeat, what you play at this time of day, your
 *  favourites, throwbacks to a year you played something to death, songs you loved and haven't heard
 *  in months, and new ones like yours (Tidal's and YouTube Music's radios). Skips don't keep a song out
 *  (you may just not be in the mood for it): they're only counted, for the DJ to tell you about. Your history is every play Most played counts: Last.fm's scrobbles, your Spotify
 *  and Apple Music imports, and the plays here. "Switch it up" starts another kind of set, and you can
 *  ask for one: an artist, a genre, a decade or a vibe. Building a set
 *  blocks (run it in [Music.bg]); [Music] plays them. */
object Dj {
    const val SET = 6   // songs in a set
    /** The mood sets: one for each of these (moods alike share one), "mood:" and its moods joined by "+"
     *  ("mood:Energetic+Party+Hype"). Songs of any of its moods; the DJ says which ("let's switch it up: some happy songs"). */
    val MOOD_GROUPS = listOf(listOf("Energetic", "Party", "Hype"), listOf("Happy", "Chill", "Calm"), listOf("Dreamy"),
        listOf("Focus"), listOf("Romantic"), listOf("Sad", "Dark"), listOf("Intense"))
    val MOOD_KINDS: List<String> get() = MOOD_GROUPS.map { "mood:" + it.joinToString("+") }
    /** A mood set's name: "Energetic, Party & Hype". */
    private fun groupName(g: List<String>) = if (g.size == 1) g[0] else g.dropLast(1).joinToString(", ") + " & " + g.last()
    val ORDER: List<String> get() = listOf("repeat", "fresh", "hour", "throwback", "discover", "spotlight", "deep", "rediscover", "yearago",
        "mostplayed", "album", "genre", "decade", "like", "neverskip", "streak", "onehit", "second", "unreleased") + MOOD_KINDS
    /** The kinds made for songs you picked (a playlist, a folder, an album), from only those songs. */
    private val FROM_KINDS = listOf("mostplayed", "unplayed", "hour", "artist", "throwback", "mix")
    /** The kinds that bring songs new to you (not from what the DJ plays from). */
    private val NEW_KINDS = setOf("fresh", "like", "discover", "deep")
    /** Playing from songs you picked: every kind of set there is, of only those songs (the ones made for them, then
     *  every kind playing from everything has, kept to them), but for the kinds of new songs. */
    val FROM_ORDER: List<String> get() = FROM_KINDS + ORDER.filter { it !in FROM_KINDS && it !in NEW_KINDS }
    // …and with new songs too: the kinds of new ones like them too.
    val FROM_NEW_ORDER: List<String> get() = FROM_KINDS + ORDER.filter { it !in FROM_KINDS }
    /** A kind of set's name, as the DJ screen and the queue show it ("Happy songs" for a mood's set), or null. */
    fun kindName(k: String?): String? = if (k == null) null else if (k.startsWith("mood:")) groupName(k.removePrefix("mood:").split("+")) + " songs" else KINDS[k]
    @Volatile var withNew = true   // with a scope: new songs like those too
    val KINDS = mapOf("repeat" to "On repeat", "fresh" to "New for you", "throwback" to "Throwback", "deep" to "Deep cuts",
        "genre" to "Your genre", "like" to "More like this", "ask" to "You asked", "loved" to "Most played", "mostplayed" to "Most played",
        "unplayed" to "Not played yet", "artist" to "One artist", "mix" to "Mix", "hour" to "This time of day",
        "rediscover" to "Rediscover", "favorites" to "Your favourites", "discover" to "Artists for you", "mood" to "Your mood",
        "moodmix" to "Mood mix", "spotlight" to "Artist spotlight", "album" to "Album moment", "yearago" to "On this day",
        "decade" to "Decade trip", "onehit" to "One-hit wonders", "neverskip" to "Never skipped", "second" to "Second chances",
        "unreleased" to "Unreleased corner", "streak" to "Streaks")
    /** Words in a request and the Last.fm tags they mean. */
    val WORDS = mapOf(
        "chill" to listOf("chill", "chillout"), "chilled" to listOf("chill", "chillout"), "relax" to listOf("chill", "relaxing"),
        "relaxing" to listOf("relaxing", "chill"), "calm" to listOf("calm", "chill"), "study" to listOf("study", "chill"),
        "studying" to listOf("study", "chill"), "focus" to listOf("focus", "instrumental"), "work" to listOf("focus", "chill"),
        "sleep" to listOf("sleep", "ambient"), "lofi" to listOf("lo-fi"), "sad" to listOf("sad", "melancholic"),
        "happy" to listOf("happy", "feel good"), "upbeat" to listOf("upbeat", "happy"), "hype" to listOf("energetic", "hype"),
        "energy" to listOf("energetic"), "workout" to listOf("workout", "energetic"), "gym" to listOf("workout", "energetic"),
        "running" to listOf("running", "workout"), "party" to listOf("party", "dance"), "dance" to listOf("dance"),
        "love" to listOf("love", "romantic"), "romantic" to listOf("romantic", "love"), "angry" to listOf("aggressive"),
        "rainy" to listOf("rainy day", "chill"), "summer" to listOf("summer"), "driving" to listOf("driving"),
        "drive" to listOf("driving"), "night" to listOf("night", "late night"), "morning" to listOf("morning"),
    )
    /** Tags that say nothing about how a song sounds. */
    private val JUNK_TAGS = setOf("seen live", "favorites", "favourites", "favorite", "favourite", "albums i own", "under 2000 listeners",
        "spotify", "youtube", "male vocalists", "female vocalists", "american", "british", "usa", "uk")
    private val FILLER = setOf("play", "some", "something", "songs", "song", "music", "me", "for", "a", "the", "please", "mix", "of",
        "like", "more", "vibes", "vibe", "tunes", "stuff", "give", "i", "want", "to", "listen", "put", "on", "and")
    private val WORD = Regex("[\\p{L}\\p{N}_'-]+")
    private val DECADE = Regex("(19|20)?\\d0s")

    /** The DJ's sets in the queue (a song's [Track.dj] is its number) and the one whose line was shown. */
    @Serializable
    data class Info(val kind: String, val line: String, val fact: String = "")

    /** What the DJ plays from: kind "all" (everything), "mine" (all your songs), "folder" (path, as
     *  "Music/Album/"), "playlist" (id, one of yours) or "songs" (an album's or a service playlist's). */
    @Serializable
    data class From(val kind: String, val id: String = "", val path: String = "", val name: String = "", val songs: List<Track> = emptyList())

    @Serializable
    data class State(val on: Boolean = false, val sets: Map<String, Info> = emptyMap(), val shown: String? = null, val from: From? = null)

    /** What the DJ screen shows: on, what it's doing, the kind of set playing and its line, what it plays from. */
    data class Shown(val on: Boolean = false, val busy: String = "", val kind: String = "", val line: String = "",
                     val kinds: Map<String, String> = emptyMap(), val from: String = "", val fact: String = "")

    data class Built(val kind: String, val songs: List<Track>, val line: String, val fact: String = "")

    // ---- the parts that don't need the app ----

    /** The Last.fm tags a request means: its words ("chill", "workout"), its decade ("80s", "2010s"), else the
     *  request itself as a genre ("house", "k-pop"). */
    fun tags(text: String): List<String> {
        val every = WORD.findAll(text.lowercase()).map { it.value }.toMutableList()
        val out = LinkedHashSet<String>()
        for (w in every) if (w !in FILLER) {
            if (DECADE.matches(w)) out += w   // "80s", "1980s", "2010s"
            WORDS[w]?.let { out += it }
        }
        while (every.isNotEmpty() && every.first() in FILLER) every.removeAt(0)   // "play some drum and bass music": "drum and bass"
        while (every.isNotEmpty() && every.last() in FILLER) every.removeAt(every.size - 1)
        if (out.isEmpty() && every.isNotEmpty()) out += every.joinToString(" ")
        return out.toList()
    }

    /** n songs of pool at random, the ones nearer the top likelier, none twice and none in used
     *  (song keys of what the DJ played already). */
    fun pick(pool: List<Track>, n: Int, used: Set<Pair<String, String>>, rng: Random = Random.Default): List<Track> {
        val seen = HashSet(used)
        val out = ArrayList<Track>()
        val weighted = pool.mapIndexed { i, t -> Math.pow(rng.nextDouble(), 1 + 8.0 * i / maxOf(1, pool.size)) to t }
        for ((_, t) in weighted.sortedByDescending { it.first }) {
            if (!seen.add(songKey(t))) continue
            out += t
            if (out.size == n) break
        }
        return out
    }

    /** "A and B": the lead artists of tracks, each once. */
    fun names(tracks: List<Track>, n: Int = 2): String {
        val ns = tracks.map { it.artist.split(",")[0].trim() }.filter { it.isNotEmpty() }.distinct().take(n)
        return if (ns.size < 3) ns.joinToString(" and ") else ns.dropLast(1).joinToString(", ") + " and " + ns.last()
    }

    fun greeting(h: Int = LocalTime.now().hour) =
        if (h in 5..11) "Good morning" else if (h in 12..16) "Good afternoon" else if (h in 17..22) "Good evening" else "Hey, night owl"

    /** The DJ's hello on the first set, for the time of day, a different one each time. */
    fun hello(h: Int = LocalTime.now().hour): String = dayHello(h) ?: hourHello(h)

    /** Half the time on a day with a feel of its own (Friday night, the weekend, Monday morning), a hello for it. */
    private fun dayHello(h: Int): String? {
        if (Random.nextBoolean()) return null
        val d = java.time.LocalDate.now().dayOfWeek
        return when {
            d == java.time.DayOfWeek.FRIDAY && h in 17..23 -> say("hello-friday",
                "Happy Friday night! It's your DJ, and the weekend starts right now.",
                "It's Friday night, and your DJ is here. Let's make it a good one.",
                "Friday night, finally. Your DJ here, with the music to match.")
            (d == java.time.DayOfWeek.SATURDAY || d == java.time.DayOfWeek.SUNDAY) && h in 6..12 -> say("hello-weekend-morning",
                "Happy ${if (d == java.time.DayOfWeek.SATURDAY) "Saturday" else "Sunday"}! It's your DJ. No alarms, just music.",
                "Weekend morning, no rush. Your DJ here, taking it easy with you.",
                "Good morning, and happy weekend. It's your DJ.")
            d == java.time.DayOfWeek.SATURDAY && h in 17..23 -> say("hello-saturday-night",
                "Saturday night! Your DJ is here and ready.",
                "It's Saturday night. Your DJ here. Let's turn it up.")
            d == java.time.DayOfWeek.SUNDAY && h in 17..23 -> say("hello-sunday-night",
                "Sunday evening, the calm before the week. Your DJ here.",
                "Hey, it's Sunday night. Your DJ, here to stretch the weekend out a little longer.")
            d == java.time.DayOfWeek.MONDAY && h in 5..11 -> say("hello-monday",
                "Good morning, and happy Monday. It's your DJ. We've got this.",
                "Monday morning. Your DJ here, to make it a little easier.")
            else -> null
        }
    }

    private fun hourHello(h: Int): String =
        if (h in 5..11) say("hello-morning",
            "Good morning! It's your DJ, and I've got your soundtrack ready.",
            "Morning! Your DJ here. Let's ease into the day.",
            "Good morning, it's your DJ. Coffee is optional, music is not.",
            "Rise and shine, it's your DJ. I picked these just for you.",
            "Good morning! Your DJ is in the building, and so are your songs.")
        else if (h in 12..16) say("hello-afternoon",
            "Good afternoon! It's your DJ, here to get you through the rest of the day.",
            "Hey, good afternoon. Your DJ here, with your afternoon pick me up.",
            "Afternoon! It's your DJ. Let's keep the day moving.",
            "Good afternoon, it's your DJ. I've been saving these for you.",
            "Hey there, your DJ here. An afternoon slump? Not on my watch.")
        else if (h in 17..22) say("hello-evening",
            "Good evening! It's your DJ. Let's wind the day down right.",
            "Evening! Your DJ here, and the night is just getting started.",
            "Good evening, it's your DJ. Kick back, I've got the music.",
            "Hey, good evening. Your DJ here, ready when you are.",
            "Evening, friend. It's your DJ, with a little something for tonight.")
        else say("hello-night",
            "Hey, night owl. It's your DJ, keeping you company.",
            "Still up? Me too. It's your DJ.",
            "A late night session? Love that. Your DJ here.",
            "Hey, night owl, your DJ here. Let's keep it going.",
            "It's late, and the music is still on. Your DJ here.")

    // ---- not saying the same thing twice ----
    // What the DJ said lately, kept (Prefs) so a restart doesn't start it over: the kinds of set played (from
    // everything, and from songs you picked), the facts told, and each line's shuffle bag.
    private const val KIND_HISTORY = "dj_kind_history"
    private const val FROM_HISTORY = "dj_kind_history_from"
    private const val FACT_HISTORY = "dj_fact_history"
    private const val LINE_BAGS = "dj_line_bags"
    private const val NO_REPEAT = 15   // a kind of set (or a fact) comes back after about this many others
    private val lock = Any()
    private val saidCache = HashMap<String, List<String>>()
    private var bags: HashMap<String, List<Int>>? = null
    private val dead = ConcurrentHashMap<String, Double>()   // "history:kind": when that kind last couldn't make a set

    /** Kept to Prefs on the main thread (sets are built in the background). */
    private fun keep(key: String, text: String) { runCatching { Music.scope.launch { Prefs.setText(key, text) } } }

    private fun said(key: String): List<String> = synchronized(lock) {
        saidCache.getOrPut(key) { runCatching { NoRepeat.decode(Prefs.text(key)) }.getOrDefault(emptyList()) }
    }

    private fun remember(key: String, what: String, keep: Int = 40) = synchronized(lock) {
        val h = NoRepeat.remember(said(key), what, keep)
        saidCache[key] = h
        keep(key, NoRepeat.encode(h))
    }

    /** One of lines, from a shuffle bag kept for key: none said again until all the others have been. */
    private fun say(key: String, vararg lines: String): String = synchronized(lock) {
        val all = bags ?: HashMap<String, List<Int>>().also { m ->
            runCatching { Prefs.text(LINE_BAGS) }.getOrDefault("").split("\n").forEach { row ->
                val kv = row.split("=", limit = 2)
                if (kv.size == 2) m[kv[0]] = kv[1].split(",").mapNotNull { it.toIntOrNull() }
            }
            bags = m
        }
        val bag = NoRepeat.bag(all[key].orEmpty(), lines.size)
        all[key] = bag
        keep(LINE_BAGS, all.entries.joinToString("\n") { "${it.key}=${it.value.joinToString(",")}" })
        lines[bag.last()]
    }

    /** A kind as the history keeps it: playing from a playlist, "Your favourites" is the same kind as from everything. */
    private fun canon(k: String) = if (k == "loved" || k == "favorites") "mostplayed" else k

    /** The kinds of set to try next, best first: the one played longest ago first, so none comes back until every
     *  other kind has had its turn. One history for every set, whatever the DJ plays from (switching to a playlist,
     *  turning the DJ off, closing the app don't start it over). A kind never played yet takes a place at random among
     *  the rest (new kinds don't all come at once). Kinds that couldn't make a set in the last ten minutes after them,
     *  and avoid (the kind playing now) last of all. */
    private fun kindsToTry(order: List<String>, key: String, avoid: String?): List<String> {
        val t = now()
        val kinds = order.distinct()
        val h = said(KIND_HISTORY)
        val rank = kinds.associateWith { k -> h.lastIndexOf(canon(k)).let { if (it >= 0) it.toDouble() else Random.nextDouble(-1.0, h.size.toDouble()) } }
        val usable = kinds.filter { t - (dead["$key:$it"] ?: -1e12) > 600 }
        var tries = usable.shuffled().sortedBy { rank.getValue(it) } + kinds.filter { it !in usable }.sortedBy { rank.getValue(it) }
        if (avoid != null) tries = tries.filter { it != avoid } + tries.filter { it == avoid }
        return tries
    }

    private fun q(t: Track) = "“${t.title}”"
    private fun by(t: Track) = names(listOf(t))
    private fun num(n: Int) = String.format(java.util.Locale.ENGLISH, "%,d", n)

    // ---- what you like ----
    /** all: your most played songs of all time; recent: of the last 30 days; hour: what you play around this
     *  hour; years: each year's most played; last: when you last played each song (stats key); artists: the
     *  ones you've played most lately; moods: the moods (Moods) of what you played
     *  in the last 30 days, and moodHour of what you play around this hour, each with how many plays. */
    private class Taste(val mine: Map<Pair<String, String>, Track>, val recent: List<Track>, val all: List<Track>,
                        val artists: List<String>, val last: Map<Pair<String, String>, Long>,
                        val hour: List<Track> = emptyList(), val years: Map<Int, List<Track>> = emptyMap(),
                        val facts: Facts = Facts(),
                        val moods: Map<String, Int> = emptyMap(), val moodHour: Map<String, Int> = emptyMap(),
                        val moodPlays: List<MoodPlay> = emptyList(),
                        val dayAgo: Map<Int, List<Track>> = emptyMap(), val streak: List<Pair<Track, Int>> = emptyList())

    /** What the DJ knows to tell you about: every song's plays and first play, your artists all told and this month,
     *  the hours you listen at, how many plays in all, each year's number one and plays, the moods of this month's plays. */
    private class Facts(val count: Map<Pair<String, String>, Int> = emptyMap(), val first: Map<Pair<String, String>, Long> = emptyMap(),
                        val artistAll: Map<String, Int> = emptyMap(), val artistMonth: Map<String, Int> = emptyMap(),
                        val hours: IntArray = IntArray(24), val total: Int = 0, val yearTop: Map<Int, Pair<Heard, Int>> = emptyMap(),
                        val since: Long = 0, val moodMonth: Map<String, Int> = emptyMap(), val yearPlays: Map<Int, Int> = emptyMap(),
                        val artistFirst: Map<String, Long> = emptyMap(), val monthPlays: Int = 0)

    private var taste: Taste? = null
    private var tasteAt = 0.0
    /** How many songs the DJ plays before one can come back: none of its last [SONG_GAP] songs (kept across restarts,
     *  counted from 3.2.26 on) is picked again for any set till then; every set takes another song of its kind in its
     *  place. A song's turn comes back as the count goes on (the oldest drops off as each new one comes). */
    const val SONG_GAP = 300
    private const val RECENT_SONGS = "dj_recent_songs"
    private val recentLock = Any()
    private var recentSongs: ArrayDeque<String>? = null   // each song the DJ played, oldest first: "title|artist\ttitle|artist" (its two keys)
    private var usedCache: Triple<Int, Int, Set<Pair<String, String>>>? = null   // (window, count when made, keys)
    private var recentCount = 0

    private fun recent(): ArrayDeque<String> = recentSongs ?: ArrayDeque<String>().also { q ->
        Prefs.text(RECENT_SONGS).split('\n').filter { it.isNotBlank() }.forEach { q += it }
        while (q.size > SONG_GAP) q.removeFirst()
        recentSongs = q
    }

    private fun keysOf(entry: String): List<Pair<String, String>> = entry.split('\t').filter { it.contains('|') }
        .map { it.substringBeforeLast('|') to it.substringAfterLast('|') }

    /** How many of the last songs are kept out now: all [SONG_GAP], or playing from a few songs (a playlist), fewer, so
     *  there are always some to pick (the ones played longest ago come back first). */
    private fun window(): Int = scope?.second?.size?.let { minOf(SONG_GAP, maxOf(0, it - 2 * SET)) } ?: SONG_GAP

    /** Song keys (both of each: the DJ's and Most played's) of the DJ's last songs: none is picked again yet. */
    val used: Set<Pair<String, String>> get() = usedInLast(window())

    /** Songs the DJ is about to play (a set queued): counted, none comes back for [SONG_GAP] songs. */
    fun played(tracks: List<Track>) {
        val text = synchronized(recentLock) {
            val q = recent()
            for (t in tracks.distinctBy { songKey(it) }) {
                val a = songKey(t); val b = Stats.statsKey(t.title, t.artist)
                q += "${a.first}|${a.second}\t${b.first}|${b.second}".replace("\n", " ")
            }
            while (q.size > SONG_GAP) q.removeFirst()
            recentCount++
            q.joinToString("\n")
        }
        Prefs.setText(RECENT_SONGS, text)
    }

    /** Song keys of the DJ's last n songs. */
    private fun usedInLast(n: Int): Set<Pair<String, String>> = synchronized(recentLock) {
        usedCache?.let { (w, c, k) -> if (w == n && c == recentCount && recentSongs != null) return k }
        val k = recent().toList().takeLast(n).flatMap { keysOf(it) }.toHashSet()
        usedCache = Triple(n, recentCount, k)
        k
    }
    private fun isUsed(t: Track, keys: Set<Pair<String, String>>) = songKey(t) in keys || Stats.statsKey(t.title, t.artist) in keys

    /** (on starting) What the last sets played, read in. */
    fun loadRecent() { synchronized(recentLock) { recent() } }
    @Volatile var scope: Pair<String, List<Track>>? = null   // (name, songs) the DJ plays from; null: everything
        private set

    /** Play only songs (named name: a playlist, a folder), or with null everything. */
    fun playFrom(name: String?, songs: List<Track>?) {
        // A playlist's songs from a service that you have yourself: your copies (file, tags, cover) from the start
        scope = if (name != null && !songs.isNullOrEmpty()) name to Music.localsOf(songs) else null
        dead.clear()   // other songs: every kind may work again
    }

    private data class Heard(val t: Long, val artist: String, val title: String)

    /** (blocking) Every play the DJ goes by: your Last.fm scrobbles and your imports; the plays here only without any. */
    private fun history(): List<Heard> {
        // Last.fm's scrobbles and your imports (Spotify, Apple Music, Deezer, YouTube Music, Tidal): what every count,
        // set and fact is made from. The plays here only when there's none of those at all (a fallback).
        val out = ArrayList<Heard>()
        if (LastFm.connected) runCatching { Wrapped.allScrobbles(Music.ctx).forEach { out += Heard(it.t, it.artist, it.title) } }
        for (p in SpotifyHistory.plays + OtherPlays.all + AppleHistory.timed()) out += Heard(p.t, p.artist, p.title)
        if (out.isEmpty()) out += herePlays()
        return out
    }

    /** The plays here (the app's own diary of when you played what). */
    private fun herePlays(): List<Heard> = runCatching {
        Music.plays.items.values.toList().flatMap { e -> e.at.map { Heard(it, e.track.artist, e.track.title) } }
    }.getOrDefault(emptyList())


    /** (blocking) Every play (Last.fm, your imports, the plays here) of a song of yours whose mood is known, oldest
     *  first, read again every ten minutes. See [MoodPlays]. */
    fun moodPlays(): List<MoodPlay> = loadTaste().moodPlays

    /** Your copy of the song a [MoodPlay.song] names (title and artist key), or null. */
    fun moodSong(song: String): Track? {
        val parts = song.split("\t")
        if (parts.size != 2) return null
        return (taste ?: loadTaste()).mine[parts[0] to parts[1]]
    }

    /** (blocking) When you last played each song (seconds), by Stats.statsKey: for Sort › Recently played. */
    fun lastHeard(): Map<Pair<String, String>, Long> = loadTaste().last

    /** (blocking) What you play, read again every ten minutes. */
    @Synchronized private fun loadTaste(): Taste {
        taste?.let { if (now() - tasteAt < 600) return it }
        val mine = Music.songIndex()
        val nowS = now().toLong()
        val zone = java.time.ZoneId.systemDefault()
        val h = LocalTime.now().hour
        val names = HashMap<Pair<String, String>, Heard>()
        val last = HashMap<Pair<String, String>, Long>()
        val recent = HashMap<Pair<String, String>, Int>()
        val hour = HashMap<Pair<String, String>, Int>()
        val years = HashMap<Int, HashMap<Pair<String, String>, Int>>()
        val artists = HashMap<String, Int>()
        val count = HashMap<Pair<String, String>, Int>()
        val first = HashMap<Pair<String, String>, Long>()
        val artistAll = HashMap<String, Int>()
        val artistMonth = HashMap<String, Int>()
        val hours = IntArray(24)
        var total = 0
        var since = Long.MAX_VALUE
        // Moods of what you played: only your own songs have one (Moods reads their files), found once per song.
        // Every play of a song with a mood is kept (MoodPlays: the mood facts, and Most played can count by mood too).
        val moodOf = HashMap<Pair<String, String>, String?>()
        val moodPlays = ArrayList<MoodPlay>()
        val moodMonth = HashMap<String, Int>()
        val moodHour = HashMap<String, Int>()
        // On this day in past years (within three days), the days of the last week each song was played on, each
        // artist's first play, and this calendar month's plays
        val today = java.time.LocalDate.now(zone)
        val dayAgo = HashMap<Int, HashMap<Pair<String, String>, Int>>()
        val days = HashMap<Pair<String, String>, HashSet<Long>>()
        val artistFirst = HashMap<String, Long>()
        var monthPlays = 0
        for (p in history()) {
            if (p.t <= 0) continue
            total++
            since = minOf(since, p.t)
            val k = Stats.statsKey(p.title, p.artist)
            names.putIfAbsent(k, p)
            last[k] = maxOf(last[k] ?: 0, p.t)
            val age = nowS - p.t
            val at = java.time.Instant.ofEpochSecond(p.t).atZone(zone)
            if (age < 30 * 86_400L) recent.merge(k, 1, Int::plus)
            if (age < 90 * 86_400L) artists.merge(p.artist.split(",")[0].trim(), 1, Int::plus)
            if (age < 365 * 86_400L && Math.floorMod(at.hour - h + 12, 24) - 12 in -1..1) hour.merge(k, 1, Int::plus)
            years.getOrPut(at.year) { HashMap() }.merge(k, 1, Int::plus)
            count.merge(k, 1, Int::plus)
            first[k] = minOf(first[k] ?: Long.MAX_VALUE, p.t)
            val lead = p.artist.split(",")[0].trim()
            if (lead.isNotEmpty()) { artistAll.merge(lead, 1, Int::plus); if (age < 30 * 86_400L) artistMonth.merge(lead, 1, Int::plus) }
            hours[at.hour]++
            val d = at.toLocalDate()
            val ya = today.year - d.year
            if (ya in 1..15 && kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(d.plusYears(ya.toLong()), today)) <= 3)
                dayAgo.getOrPut(ya) { HashMap() }.merge(k, 1, Int::plus)
            if (age < 7 * 86_400L) days.getOrPut(k) { HashSet() } += d.toEpochDay()
            if (lead.isNotEmpty()) artistFirst[lead] = minOf(artistFirst[lead] ?: Long.MAX_VALUE, p.t)
            if (d.year == today.year && d.month == today.month) monthPlays++
            val mood = moodOf.getOrPut(k) { mine[k]?.let { runCatching { Moods.of(it) }.getOrNull() } }
            if (mood != null) {
                moodPlays += MoodPlay(p.t, mood, "${k.first}\t${k.second}")
                if (age < 30 * 86_400L) moodMonth.merge(mood, 1, Int::plus)
                if (age < 365 * 86_400L && Math.floorMod(at.hour - h + 12, 24) - 12 in -1..1) moodHour.merge(mood, 1, Int::plus)
            }
        }
        // All time, an artist's and a song's plays are Most played's (Stats): Last.fm and your Spotify, Apple Music,
        // Deezer, YouTube Music and Tidal imports (with or without a time), an artist's under one name. The plays here
        // only count when there's nothing from those at all.
        val anyOutside = Stats.artists.any { it.plays > it.here }
        fun outside(all: Int, here: Int) = if (anyOutside) all - here else all
        if (Stats.artists.isNotEmpty()) { artistAll.clear(); Stats.artists.forEach { val n = outside(it.plays, it.here); if (n > 0) artistAll.merge(it.name, n, Int::plus) } }
        val statSongs = HashMap<Pair<String, String>, Int>()
        Stats.songs.forEach { statSongs.merge(Stats.statsKey(it.title, it.artist), outside(it.plays, it.here), Int::plus) }
        statSongs.forEach { (k, n) -> if (n > (count[k] ?: 0)) count[k] = n }
        // When you last played a song: the plays here too (not counted anywhere), so one you played a minute ago,
        // before Last.fm has it, isn't picked again straight away
        for (p in herePlays()) { val k = Stats.statsKey(p.title, p.artist); if (p.t > (last[k] ?: 0L)) last[k] = p.t }
        val yearTop = years.mapNotNull { (y, c) -> c.maxByOrNull { it.value }?.let { (k, n) -> y to (names.getValue(k) to n) } }.toMap()
        val facts = Facts(count, first, artistAll, artistMonth, hours, total, yearTop, if (since == Long.MAX_VALUE) 0 else since, moodMonth,
            years.mapValues { it.value.values.sum() }, artistFirst, monthPlays)
        fun songs(c: Map<Pair<String, String>, Int>, n: Int) = c.entries.sortedByDescending { it.value }.take(n)
            .map { (k, v) -> val p = names.getValue(k); Music.fmSong(p.title, p.artist, "", mine).copy(plays = v) }
        // Every song: all you've played, most played first, then every song of yours you haven't played yet
        var all = runCatching { Music.mostPlayed() }.getOrDefault(emptyList())
        if (all.isEmpty()) all = Music.plays.items.values.sortedByDescending { it.plays }.map { it.track.copy(plays = it.plays) }
        val inAll = all.mapTo(HashSet()) { songKey(it) }
        all = all + runCatching { Music.mySongs() }.getOrDefault(emptyList()).filter { inAll.add(songKey(it)) }
        var top = artists.entries.sortedByDescending { it.value }.map { it.key }.filter { it.isNotEmpty() }.take(20)
        val rec = songs(recent, 60)
        if (top.isEmpty()) top = (rec + all).map { it.artist.split(",")[0].trim() }.filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(20).map { it.key }
        val streak = days.filter { it.value.size >= 4 }.entries.sortedByDescending { it.value.size * 1000 + (recent[it.key] ?: 0) }.take(40)
            .map { (k, ds) -> val p = names.getValue(k); Music.fmSong(p.title, p.artist, "", mine).copy(plays = count[k] ?: 0) to ds.size }
        return Taste(mine, rec, all, top, last, songs(hour, 120),
            years.filter { it.value.values.sum() >= 30 }.mapValues { songs(it.value, 60) }, facts, moodMonth, moodHour,
            moodPlays.sortedBy { it.t }, dayAgo.filter { it.value.values.sum() >= 8 }.mapValues { songs(it.value, 40) }, streak).also { taste = it; tasteAt = now() }
    }

    private fun lastPlayed(taste: Taste, t: Track) = taste.last[Stats.statsKey(t.title, t.artist)] ?: 0L
    /** The songs of a pool the DJ may play: all but its last 300 (see [SONG_GAP]). Skips never keep a song out of its
     *  sets (you may just not be in the mood for it); they're only counted, for Skips and the DJ's facts. */
    @Suppress("UNUSED_PARAMETER")
    private fun ok(taste: Taste, pool: List<Track>): List<Track> { val u = used; return pool.filter { inScope(it) && !isUsed(it, u) } }

    /** While a set of a kind made for everything is made for songs you picked (on this thread: sets are made side
     *  by side), the songs it may have; null: any. */
    private val only = ThreadLocal<Set<Pair<String, String>>?>()
    private fun inScope(t: Track): Boolean = only.get()?.let { songKey(t) in it || Stats.statsKey(t.title, t.artist) in it } ?: true
    private fun newToYou(t: Track, known: Set<Pair<String, String>>) = songKey(t) !in known && Stats.statsKey(t.title, t.artist) !in known

    private fun known(taste: Taste): Set<Pair<String, String>> =
        taste.mine.keys + taste.all.map { songKey(it) } + taste.all.map { Stats.statsKey(it.title, it.artist) } + taste.last.keys + taste.facts.count.keys + playedHere()

    /** A song's plays, from Last.fm and your imports (the plays here only when there are none of those at all; see
     *  loadTaste), matched as Most played matches songs (its lead artist, featurings and punctuation aside). */
    private fun playsOf(taste: Taste, t: Track) = taste.facts.count[Stats.statsKey(t.title, t.artist)] ?: 0

    /** The songs you've ever played in the app (one of those is never said to be new to you, even with no Last.fm). */
    private fun playedHere(): Set<Pair<String, String>> = runCatching {
        Music.plays.items.values.filter { it.plays > 0 }.mapTo(HashSet()) { Stats.statsKey(it.track.title, it.track.artist) }
    }.getOrDefault(emptySet())

    private fun radio(seed: Track, key: String): List<Track> {
        val src = Music.sources[key] ?: return emptyList()
        if (key == "tidal" && !src.signedIn) return emptyList()
        return runCatching { src.radio(seed) }.getOrDefault(emptyList())   // the other service, then
    }

    /** The services new songs come from: Tidal and YouTube Music both, when both are there. */
    private fun services() = listOfNotNull("tidal".takeIf { Music.sources["tidal"]?.signedIn == true }, "ytm")

    // ---- the kinds of set ----
    // Each returns its songs and the DJ's line for them (the first set's hello goes before it, in build).
    private fun setRepeat(taste: Taste): Pair<List<Track>, String>? {
        val songs = pick(ok(taste, taste.recent.take(60)).take(25), SET, used)
        if (songs.size < 3) return null
        val s = songs[0]
        return songs to say("repeat",
            "Here's what you've had on repeat lately, starting with ${q(s)} by ${by(s)}.",
            "You know these ones by heart. ${names(songs, 3)}, all on heavy rotation.",
            "Your heavy rotation, coming right up. First, ${q(s)}.",
            "These are the songs you can't stop playing. Honestly? I get it.",
            "On repeat, and for good reason. ${names(songs, 3)}. Sing along, nobody's listening.",
            "If it's been stuck in your head lately, it's probably in this set. ${q(s)} kicks it off.",
            "Your current obsessions, all in one place. ${q(s)} leads the way.",
            "You've worn these out lately, and I'm not about to stop you. ${q(s)} first.",
            "The songs living rent free in your head right now. Starting with ${q(s)} by ${by(s)}.")
    }

    /** A year you played something to death: that year's most played, the ones you haven't played in two months. */
    private fun setThrowback(taste: Taste): Pair<List<Track>, String>? {
        val t = now().toLong()
        val thisYear = java.time.LocalDate.now().year
        for (y in taste.years.keys.filter { it < thisYear }.shuffled()) {
            val pool = ok(taste, taste.years.getValue(y)).filter { t - lastPlayed(taste, it) > 60 * 86_400L }
            val songs = pick(pool.take(80), SET, used)
            if (songs.size < 3) continue
            val s = songs[0]
            val n = num(s.plays)
            return songs to if (s.plays > 1) say("throwback-plays",
                    "Throwback to $y! You played ${q(s)} $n times that year.",
                    "Let's take it back to $y, when ${q(s)} was basically your whole personality. $n plays, to be exact.",
                    "Remember $y? You had ${q(s)} on replay, $n times.",
                    "The time machine is warmed up. Destination, $y, and ${q(s)}, which you played $n times.",
                    "A quick trip back to $y. ${q(s)} was on constant rotation back then, with $n plays.",
                    "Back to $y we go. ${q(s)} got $n plays from you that year. Let's make it one more.",
                    "If $y had a theme song for you, it was ${q(s)}. $n plays!")
                else say("throwback",
                    "Throwback to $y, starting with ${q(s)} by ${by(s)}.",
                    "Going back to $y now, with the songs you were into back then.",
                    "Let's dust off $y. Starting with ${q(s)}.",
                    "Remember $y? These were your songs.",
                    "Flashback time: $y. ${names(songs, 3)}.",
                    "A little nostalgia never hurt. Here's your $y, starting with ${q(s)}.")
        }
        return null
    }

    /** Songs you've played a lot, all told, but not for three months. */
    private fun setRediscover(taste: Taste): Pair<List<Track>, String>? {
        val t = now().toLong()
        val recent = taste.recent.map { songKey(it) }.toSet()
        val pool = ok(taste, taste.all).filter { it.plays >= 3 && songKey(it) !in recent && t - lastPlayed(taste, it) > 90 * 86_400L }
        val songs = pick(pool, SET, used)
        if (songs.size < 3) return null
        val s = songs[0]
        val since = lastPlayed(taste, s).takeIf { it > 0 }?.let { "since ${month(it)}" } ?: "in ages"
        val times = if (s.plays > 0) num(s.plays) else "a lot of"
        return songs to say("rediscover",
            "Remember this one? You've played ${q(s)} $times times, but not $since.",
            "Some old favourites you haven't heard $since. Starting with ${q(s)}.",
            "Let's dig up a few you used to love. You haven't played ${q(s)} $since.",
            "Here's one that's been gathering dust. ${q(s)}, $times plays, and nothing $since.",
            "Rediscovery time! These used to be big for you, starting with ${q(s)} by ${by(s)}.",
            "Long time no hear. ${q(s)} by ${by(s)}, not played $since.",
            "From the back of the shelf: ${q(s)}. You loved it once, $times plays, but not $since.",
            "Let's bring back a few you forgot about. ${q(s)} first.")
    }

    /** What you usually play around this hour. */
    private fun setHour(taste: Taste): Pair<List<Track>, String>? {
        val songs = pick(ok(taste, taste.hour.take(60)), SET, used)
        if (songs.size < 3) return null
        val s = songs[0]
        val h = LocalTime.now().hour
        val whenText = if (h in 5..11) "this morning" else if (h in 12..16) "this afternoon" else if (h in 17..22) "tonight" else "at this hour"
        val part = if (h in 5..11) "morning" else if (h in 12..16) "afternoon" else if (h in 17..22) "evening" else "late night"
        return songs to say("hour",
            "Here's what you usually play $whenText, starting with ${q(s)}.",
            "What you usually play $whenText: ${names(songs, 3)}.",
            "I've noticed what you like $whenText. Starting with ${q(s)}.",
            "Right on schedule. These are your go to songs $whenText.",
            "Your $part regulars are here, led by ${q(s)} by ${by(s)}.",
            "Same time, same vibe. ${names(songs, 3)}, just how you like it $whenText.",
            "Clockwork. Around now you usually reach for ${q(s)}, so here it is.",
            "Your $part soundtrack, as usual. ${names(songs, 3)}.",
            "I know what time it is. ${q(s)} time.")
    }

    /** The moods you've been in: what you've played lately, and what you play around this hour counted double; a
     *  mood picked from the top three, the bigger likelier. Null when none of your plays has a mood yet. */
    private fun pickMood(taste: Taste): Pair<String, Boolean>? {
        val weight = HashMap<String, Int>()
        taste.moods.forEach { (m, n) -> weight.merge(m, n, Int::plus) }
        taste.moodHour.forEach { (m, n) -> weight.merge(m, 2 * n, Int::plus) }
        val top = weight.entries.sortedByDescending { it.value }.take(3).filter { it.value >= 3 }
        if (top.isEmpty()) return null
        var r = Random.nextInt(top.sumOf { it.value })
        val mood = top.first { r -= it.value; r < 0 }.key
        val byHour = (taste.moodHour[mood] ?: 0) * 2 >= (taste.moods[mood] ?: 0)
        return mood to byHour
    }

    // ---- Mood mix: one mood at a time, a different one each time ----
    private const val MOODMIX_HISTORY = "dj_moodmix_history"
    private val artistMoodCache = ConcurrentHashMap<String, List<String>>()

    /** (blocking) The moods an artist's Last.fm tags point to (the same rules Moods uses for a song's tags), best first;
     *  empty with no Last.fm key or no tags. Kept while the app runs; a failed lookup is tried again next time. */
    private fun artistMoods(artist: String): List<String> {
        val lead = artist.split(",")[0].trim()
        val k = norm(lead)
        if (k.isEmpty() || LastFm.key.isEmpty()) return emptyList()
        artistMoodCache[k]?.let { return it }
        val tags = runCatching { LastFm.artistTags(lead) }.getOrNull() ?: return emptyList()
        val got = MoodRules.decide(null, "", tags.filter { it.first.isNotBlank() && it.second > 0 && it.first.lowercase() !in JUNK_TAGS }
            .associate { it.first.lowercase() to it.second / 100.0 }).map { it.first }
        artistMoodCache[k] = got
        return got
    }

    /** Whether a song you don't have (a stream, a new one) fits mood, judged by its artist's tags: its top two moods.
     *  unknownFits: what to say when there's nothing to judge by (no Last.fm key, no tags). */
    private fun fitsMood(t: Track, mood: String, unknownFits: Boolean): Boolean {
        val ms = artistMoods(t.artist)
        return if (ms.isEmpty()) unknownFits else mood in ms.take(2)
    }

    /** What the DJ says to open a mood mix: a few lines per mood, in his own words. */
    private fun switchUp(mood: String, s: Track): Array<String> {
        val m = mood.lowercase()
        return arrayOf("Hey, let's switch it up. We're going to play some $m songs, starting with ${q(s)}.",
            "Let's switch it up: some $m songs now, and ${q(s)} goes first.",
            "Time for a change. A few $m songs, starting with ${q(s)} by ${by(s)}.")
    }

    /** What the DJ says to open a set of moods alike. */
    private fun groupIntro(group: List<String>, s: Track): String {
        val key = "moodset-" + group.joinToString("+")
        return when (group[0]) {
            "Energetic" -> say(key, *switchUp("high-energy", s),
                "Hey, let's switch it up. Energy, party and hype, starting with ${q(s)}.",
                "Let's turn it up. Some high-energy ones now, and ${q(s)} gets us going.",
                "Okay, let's get moving. Party and hype, with ${q(s)} first.")
            "Happy" -> say(key, *switchUp("happy, chill", s),
                "Hey, let's switch it up. Something happy and easy now, starting with ${q(s)}.",
                "Let's keep it light. Happy, chill and calm, with ${q(s)} first.",
                "Good vibes only for a bit. ${q(s)} kicks it off.")
            "Sad" -> say(key, *switchUp("sad, dark", s),
                "Hey, let's switch it up. Something sad and dark now, starting with ${q(s)}.",
                "Lights down. A few of the heavier ones, ${q(s)} first.",
                "Let's sit with it for a while. Sad and dark, starting with ${q(s)}.")
            else -> say(key, *switchUp(groupName(group).lowercase().replace(" & ", " and "), s))
        }
    }

    private fun moodIntro(mood: String, s: Track): String = when (mood) {
        "Happy" -> say("moodmix-Happy", *switchUp(mood, s), "Let's lift things up a little. A happy run, starting with ${q(s)}.",
            "Sun's out, at least in here. Some happy ones, kicking off with ${q(s)}.", "Time for a mood boost. Happy songs only, and ${q(s)} goes first.")
        "Energetic" -> say("moodmix-Energetic", *switchUp(mood, s), "Let's pick up the pace. Energy up, starting with ${q(s)}.",
            "Okay, let's get moving. ${q(s)} gets us going.", "Shake off the slump. This one's all energy, and ${q(s)} leads the way.")
        "Party" -> say("moodmix-Party", *switchUp(mood, s), "Let's turn this into a party. ${q(s)} first, so turn it up.",
            "Party mode, on. We're starting with ${q(s)}.", "If you're not dancing yet, give it a minute. ${q(s)} is up.")
        "Hype" -> say("moodmix-Hype", *switchUp(mood, s), "Let's get hyped. ${q(s)}, and it only goes up from here.",
            "Hype mode. Hold on tight, ${q(s)} is first.", "Energy check. We're going full hype, with ${q(s)}.")
        "Chill" -> say("moodmix-Chill", *switchUp(mood, s), "Let's go somewhere chill. ${q(s)}, then a few more to sink into.",
            "Time to slow it down and get comfy. ${q(s)} first.", "Deep breath. Something chill now, starting with ${q(s)}.")
        "Calm" -> say("moodmix-Calm", *switchUp(mood, s), "Let's bring it way down. Calm and easy, starting with ${q(s)}.",
            "A quiet one now. ${q(s)}, nice and calm.", "Let's take a breather. ${q(s)}, and a few more like it.")
        "Dreamy" -> say("moodmix-Dreamy", *switchUp(mood, s), "Let's drift for a while. Something dreamy, starting with ${q(s)}.",
            "Head in the clouds time. ${q(s)} first.", "A little dreamy detour now, starting with ${q(s)}.")
        "Focus" -> say("moodmix-Focus", *switchUp(mood, s), "Heads down, let's focus. ${q(s)}, and a few more to get you in the zone.",
            "Getting things done? I've got you. Focus mode, starting with ${q(s)}.", "No distractions, just flow. ${q(s)} first.")
        "Romantic" -> say("moodmix-Romantic", *switchUp(mood, s), "Let's set the mood. Something romantic, starting with ${q(s)}.",
            "A little romantic stretch now. ${q(s)} first.", "Candles optional. Romantic songs, starting with ${q(s)}.")
        "Sad" -> say("moodmix-Sad", *switchUp(mood, s), "Let's sit with our feelings for a bit. Something sad, starting with ${q(s)}.",
            "Sometimes you just need a sad song. Here's ${q(s)}.", "Okay, tissues ready? A few sad ones, ${q(s)} first.")
        "Dark" -> say("moodmix-Dark", *switchUp(mood, s), "Let's go somewhere darker. ${q(s)} opens the way.",
            "Lights down. Something dark now, starting with ${q(s)}.", "Into the shadows for a bit. ${q(s)} first.")
        "Intense" -> say("moodmix-Intense", *switchUp(mood, s), "Let's turn it up. Something intense, starting with ${q(s)}.",
            "Brace yourself, it's getting intense. ${q(s)} first.", "No holding back now. ${q(s)} kicks it off.")
        else -> say("moodmix-other", *switchUp(mood, s), "Let's change the mood. Something ${Moods.adjective(mood)}, starting with ${q(s)}.",
            "New mood, coming up. A ${Moods.adjective(mood)} run, starting with ${q(s)}.")
    }

    /** Mood mix: one mood (a different one from last time, rotating through them all), songs in it you know (yours, or
     *  played from a service, its mood from its file or its artist's tags) and, with new songs on, a couple you haven't
     *  heard that the DJ thinks you'll like (radios of the known ones, artists like yours) in that mood too. pool: the
     *  songs the DJ plays from (null: everything). */
    private fun setMoodMix(taste: Taste, pool: List<Track>?, only: String? = null): Pair<List<Track>, String>? {
        val withNewSongs = pool == null || withNew
        val t0 = now().toLong()
        // Songs you've played that aren't files (streams): judged by their artists' tags, a few artists at a time.
        val streamed = (taste.recent + taste.hour + taste.all.take(150))
            .filter { it.file.isNullOrEmpty() && runCatching { Moods.of(it) }.getOrNull() == null }.distinctBy { songKey(it) }
        var lookups = 0
        for (group in if (only != null) listOf(only.split("+")) else MoodTally.moodOrder(Moods.ALL, said(MOODMIX_HISTORY)).take(5).map { listOf(it) }) {
            val mood = group[0]
            // Known: your songs in the moods (or the ones you picked to play from), those not played lately first
            val mine = (pool?.filter { s -> Moods.all(s).any { it in group } } ?: group.flatMap { Moods.songs(it) }.distinctBy { songKey(it) })
            var have = ok(taste, mine).filter { songKey(it) !in used }.shuffled().sortedBy { (t0 - lastPlayed(taste, it)) < 7 * 86_400L }
            if (pool == null && have.size < 6) {
                val more = streamed.filter { s ->
                    val k = norm(s.artist.split(",")[0].trim())
                    if (!artistMoodCache.containsKey(k)) { if (lookups >= 12) return@filter false; lookups++ }
                    group.any { fitsMood(s, it, unknownFits = false) }
                }
                have = have + ok(taste, more).filter { songKey(it) !in used }.shuffled()
            }
            val knownPick = pick(have.take(60), if (withNewSongs) 4 else SET, used)
            if (knownPick.size < 2) continue
            val fresh = ArrayList<Track>()
            if (withNewSongs) {
                val notNew = known(taste) + used + knownPick.map { songKey(it) }
                val noKey = LastFm.key.isEmpty()
                // Radios of two of the known ones: songs like them, so likely in the same mood; checked by their artist's tags
                val keys = services()
                for ((i, seed) in knownPick.take(2).withIndex()) {
                    if (fresh.size >= 3) break
                    val got = radio(seed, keys[i % keys.size]).filter { newToYou(it, notNew) && songKey(it) !in fresh.map { f -> songKey(f) }.toSet() }
                    fresh += got.take(10).filter { t -> group.any { fitsMood(t, it, unknownFits = noKey) } }.take(2)
                }
                // Artists like yours whose sound is this mood
                if (fresh.size < 2 && !noKey) for ((name, _) in artistsForYou(taste).take(8)) {
                    if (fresh.size >= 3) break
                    if (artistMoods(name).take(2).none { it in group }) continue
                    val info = runCatching { Music.artistInfo(name, null) }.getOrNull() ?: continue
                    fresh += info.top.filter { newToYou(it, notNew) }.take(1)
                }
            }
            val news = pick(fresh, 2, used)
            // Known, known, new, known, new, known…
            val songs = ArrayList<Track>()
            val ki = knownPick.iterator(); val ni = news.iterator()
            var i = 0
            while ((ki.hasNext() || ni.hasNext()) && songs.size < SET) {
                if ((i == 2 || i == 4) && ni.hasNext()) songs += ni.next() else if (ki.hasNext()) songs += ki.next() else songs += ni.next()
                i++
            }
            if (songs.size < 3) continue
            remember(MOODMIX_HISTORY, mood)
            val intro = if (group.size > 1) groupIntro(group, songs[0]) else moodIntro(mood, songs[0])
            val tail = if (news.isNotEmpty()) " " + say("moodmix-new",
                "I've mixed in a couple of new ones I think you'll like.",
                "There's something new in there too. Tell me what you think.",
                "And a few you haven't heard yet, same vibe.",
                "Plus a new find or two, picked just for you.") else ""
            return songs to intro + tail
        }
        return null
    }

    /** Your most played songs: from well down the list too (the top 150 not played by the DJ lately), at random, so
     *  it's never just the same few at the top. */
    private fun setMostPlayed(taste: Taste): Pair<List<Track>, String>? {
        val top = ok(taste, taste.all.filter { playsOf(taste, it) > 0 }).filter { !isUsed(it, used) }.take(150)
        if (top.size < 3) return null
        val songs = top.shuffled().take(SET)
        val s = songs[0]
        return songs to say("mostplayed",
            "Some of your most played, starting with ${q(s)} by ${by(s)}.",
            "Your most played, and not just the very top. ${q(s)} first.",
            "The ones you keep coming back to. Let's dig into the list, starting with ${q(s)}.",
            "From your most played: ${names(songs, 3)}. You know these ones.",
            "A run through your most played songs, with ${q(s)} by ${by(s)} up first.")
    }

    /** Your starred songs, the ones you play most likelier. */
    private fun setFavorites(taste: Taste): Pair<List<Track>, String>? {
        val favs = Music.favorites.tracks().sortedByDescending { playsOf(taste, it) }
        val songs = pick(ok(taste, favs), SET, used)
        if (songs.size < 3) return null
        val s = songs[0]
        return songs to say("favorites",
            "Some of your favourites, like ${q(s)} by ${by(s)}.",
            "Straight from your favourites: ${names(songs, 3)}. You starred these for a reason.",
            "Only the best now, your starred songs. Starting with ${q(s)}.",
            "You gave these a star, so I'm giving them the spotlight. ${q(s)} first.",
            "Favourites time. No skips needed, ${q(s)} leads the way.",
            "The ones you saved for a reason. ${q(s)} by ${by(s)} first.",
            "All killer, no filler. Your favourites, starting with ${q(s)}.")
    }

    private fun setFresh(taste: Taste): Pair<List<Track>, String>? {
        val seeds = pick(taste.recent.take(20).ifEmpty { taste.all.take(40) }, 2, emptySet())
        if (seeds.isEmpty()) return null
        val known = known(taste) + used
        val keys = services()
        val found = seeds.mapIndexed { i, seed ->
            val got = radio(seed, keys[i % keys.size]).ifEmpty { radio(seed, keys[(i + 1) % keys.size]) }
            got.filter { newToYou(it, known) }
        }
        val mixed = (0 until (found.maxOfOrNull { it.size } ?: 0)).flatMap { i -> found.mapNotNull { it.getOrNull(i) } }
        val songs = pick(mixed.take(30), SET, used)
        if (songs.size < 3) return null
        val from = names(seeds)
        return songs to say("fresh",
            "Something new for you, picked from $from. Let's see what you think.",
            "Here's some music you haven't heard yet, because you've been playing $from.",
            "Fresh finds, inspired by $from. Skip if they're not for you, I won't take it personally.",
            "New music alert. If you like $from, these should feel right at home.",
            "Let's try something new. I went looking for songs like $from, and I think I found some gems.",
            "New songs incoming. You've been playing $from, so these are for you.",
            "I went digging for new stuff in the world of $from. Here's what I found.",
            "Ready for something you've never heard? Inspired by $from.")
    }

    private fun setDeep(taste: Taste): Pair<List<Track>, String>? {
        for (name in taste.artists.take(10).shuffled().take(3)) {
            val info = runCatching { Music.artistInfo(name, null) }.getOrNull() ?: continue   // the next artist
            val known = known(taste) + used
            val songs = info.top.filter { newToYou(it, known) }.take(SET)
            val who = info.name.ifEmpty { name }
            if (songs.size >= 3) return songs to say("deep",
                "Deep cuts from $who: songs of theirs you haven't played yet.",
                "You play a lot of $who, but have you heard these? Let's go deeper.",
                "You know the hits from $who. Now for the ones only real fans know.",
                "Beyond the big ones. Here's the $who you haven't heard yet.",
                "Time to dig into the $who catalogue. Trust me, there's treasure in here.",
                "Let's go past the singles. More $who, all new to you.",
                "There's more to $who than the songs you know. Here's proof.")
        }
        return null
    }

    private fun setGenre(taste: Taste): Pair<List<Track>, String>? {
        mineGenre(taste)?.let { return it }
        if (LastFm.key.isEmpty()) return null
        val weight = HashMap<String, Int>()
        val lead = taste.artists.take(3)
        for (name in lead) runCatching {   // else the other artists' tags
            for ((tag, n) in LastFm.artistTags(name).take(6)) if (tag.lowercase() !in JUNK_TAGS) weight.merge(tag.lowercase(), n, Int::plus)
        }
        if (weight.isEmpty()) return null
        val tag = weight.entries.sortedByDescending { it.value }.take(3).random().key
        val songs = tagSongs(tag, taste)
        if (songs.size < 3) return null
        val a = lead[0]
        return songs to say("genre",
            "Some $tag, since you've been playing $a so much.",
            "You've had $a on a lot lately, so let's lean into some $tag.",
            "A little $tag for you. If you like $a, you'll feel right at home.",
            "Genre of the moment: $tag. Blame $a for this one.",
            "Since $a is on your mind, let's explore some more $tag.",
            "A detour into $tag, courtesy of $a.")
    }

    /** Radio of what's playing (or, when no service has one of it, of a song you play a lot). */
    private fun setLike(taste: Taste): Pair<List<Track>, String>? {
        val seeds = listOfNotNull(Music.current()) + taste.recent.take(5)
        for (seed in seeds.take(4)) for (key in services()) {
            val songs = pick(radio(seed, key).take(25).filter { songKey(it) != songKey(seed) }, SET, used)
            if (songs.size >= 3) return songs to say("like",
                "More like ${q(seed)} by ${by(seed)}.",
                "If you liked ${q(seed)}, you'll like where this is going.",
                "Let's stay in the world of ${q(seed)}, with a few songs that sound just like it.",
                "Riffing on ${q(seed)} now. Same energy, new songs.",
                "Inspired by ${q(seed)} by ${by(seed)}. Let's ride this wave.",
                "${q(seed)} started something. Here's more in that vein.",
                "Same lane as ${q(seed)}, new songs. Let's go.")
        }
        return null
    }

    /** A set from your own songs' genre tags: one of the genres you've been playing most lately (as your files name
     *  them), its songs from your music. Null when your songs have no genres or too few of that one. */
    private fun mineGenre(taste: Taste): Pair<List<Track>, String>? {
        val weight = HashMap<String, Int>()
        for (t in (taste.recent + taste.all.take(100))) for (g in genreParts(genreOf(t))) weight.merge(g, 1, Int::plus)
        if (weight.isEmpty()) return null
        for (g in weight.entries.sortedByDescending { it.value }.take(3).shuffled().map { it.key }) {
            val songs = genreSongs(g, taste)
            if (songs.size < 3) continue
            val name = g.lowercase()
            return songs to say("genre-mine",
                "Some $name from your music, since you've been playing a lot of it lately.",
                "You've been on a real $name kick lately. Here's more from your own collection.",
                "Let's dig through your $name, starting with ${q(songs[0])}.",
                "Your music, your genre. A little $name, coming up.",
                "A $name run, straight from your library.")
        }
        return null
    }

    /** A song's genre tag as its file says ("" when it has none): of your copy of it, when it's a song you have. */
    private fun genreOf(t: Track): String {
        val mine = t.takeIf { it.file != null } ?: (taste ?: loadTaste()).mine[songKey(t)] ?: return ""
        return Music.genreOf(mine).takeIf { it != "Unknown genre" } ?: ""
    }

    /** Your songs whose genre tag fits genre ("rock": Rock, Indie Rock…), the ones you play less often first, so a
     *  genre set brings back songs as well as favourites. */
    private fun genreSongs(genre: String, taste: Taste): List<Track> {
        val fits = mySongs().filter { genreFits(Music.genreOf(it), genre) }
        if (fits.size < 3) return emptyList()
        return pick(ok(taste, fits).ifEmpty { fits }.shuffled(), SET, used)
    }

    /** Songs Last.fm files under tag, the ones by artists you play first. */
    private fun tagSongs(tag: String, taste: Taste): List<Track> {
        if (LastFm.key.isEmpty()) return emptyList()
        val rows = runCatching { LastFm.call("tag.getTopTracks", params = mapOf("tag" to tag, "limit" to 100)).path("tracks", "track").list }
            .getOrNull() ?: return emptyList()
        val yours = taste.artists.map { norm(it) }.toSet()
        val pool = rows.mapNotNull { x ->
            val name = x["name"].str ?: return@mapNotNull null
            val artist = x["artist"]["name"].str ?: return@mapNotNull null
            val img = x["image"].list.reversed().firstNotNullOfOrNull { it["#text"].str?.takeIf { s -> s.isNotEmpty() } } ?: ""
            Music.fmSong(name, artist, img, taste.mine)
        }.sortedBy { norm(it.artist.split(",")[0]) !in yours }   // stable: Last.fm's order within each
        return pick(pool, SET, used)
    }

    // ---- what the DJ tells you between sets ----
    private fun month(t: Long): String {
        val d = java.time.Instant.ofEpochSecond(t).atZone(java.time.ZoneId.systemDefault())
        return d.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + d.year
    }
    private fun hoursText(h: Double) = if (h >= 10) String.format(java.util.Locale.ENGLISH, "%.0f", h)
        else String.format(java.util.Locale.ENGLISH, "%.1f", h).removeSuffix(".0")
    private fun hourName(h: Int) = when (h) { 0 -> "midnight"; 12 -> "noon"; else -> "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}" }
    private fun ordinal(n: Int) = when (n) { 1 -> "one"; 2 -> "two"; 3 -> "three"; 4 -> "four"; 5 -> "five"; else -> n.toString() }

    /** (blocking) Something the DJ can tell you, about the songs coming up or your listening: how long you've spent
     *  on a song, when you first played it, that you never skip it (or always do), your artists, genres and moods, the
     *  hours you listen at, a year's number one… Each fact has a key ("hours:<song>", "top-artist-month"); one is
     *  picked at random from the ones not told in the last 15 (fewer when there are few to tell). Null when it knows
     *  nothing worth saying. */
    fun fact(next: List<Track> = emptyList()): String? = runCatching {
        val taste = loadTaste()
        val f = taste.facts
        val nowS = now().toLong()
        val out = LinkedHashMap<String, String>()   // key to what the DJ says
        val rows = runCatching { Music.songStats.rows() }.getOrDefault(emptyList())
        val heard = rows.associate { songKey(it.first) to (it.second to it.third) }
        val allRank = taste.all.withIndex().associate { songKey(it.value) to it.index }
        val artistRank = f.artistAll.entries.sortedByDescending { it.value }.take(5).withIndex().associate { it.value.key.lowercase() to (it.index + 1 to it.value.value) }
        // A milestone coming up (a song's 100th play…) is always told
        for (s in next.take(4)) {
            val n = (f.count[Stats.statsKey(s.title, s.artist)] ?: 0) + 1
            if (n in MILESTONES) {
                val key = "milestone:${songKey(s)}:$n"
                if (key !in said(FACT_HISTORY)) {
                    remember(FACT_HISTORY, key)
                    return@runCatching say("fact-milestone",
                        "Milestone alert! Coming up is your ${num(n)}th play of ${q(s)}. Let's celebrate.",
                        "Big one in this set: ${q(s)} hits ${num(n)} plays. Congratulations to you both.",
                        "Get ready, ${q(s)} is about to reach ${num(n)} plays. That's a milestone.")
                }
            }
        }
        val topMood = f.moodMonth.maxByOrNull { it.value }?.takeIf { it.value >= 15 }?.key
        val streakDays = taste.streak.associate { songKey(it.first) to it.second }
        val yearAgo = taste.dayAgo[1].orEmpty().map { songKey(it) }.toSet()
        // The songs coming up
        for (s in next.take(4)) {
            val k = Stats.statsKey(s.title, s.artist)
            val id = "${k.first}|${k.second}"
            val n = f.count[k] ?: 0
            streakDays[songKey(s)]?.let { d -> out["streak:$id"] = "You've played ${q(s)} on $d of the last seven days. That's a streak, and I'm keeping it alive." }
            if (songKey(s) in yearAgo) out["year-ago:$id"] = "One year ago this week, you were playing ${q(s)} too. Some things never change."
            if (topMood != null && runCatching { Moods.of(s) }.getOrNull() == topMood)
                out["mood-next:$id"] = "${q(s)} is a ${Moods.adjective(topMood)} one, which fits: it's the mood you've played most this month."
            s.artist.split(",")[0].trim().takeIf { it.isNotEmpty() }?.let { a ->
                f.artistFirst[a]?.let { t ->
                    val m = f.artistMonth[a] ?: 0
                    if (nowS - t > 3 * 365 * 86_400L) out["artist-since:$a"] = "You've been listening to $a since ${month(t)}. That's loyalty."
                    else if (nowS - t < 30 * 86_400L && m >= 10) out["artist-new:$a"] = "You only found $a this month, and you've already played them ${num(m)} times."
                }
            }
            val dur = s.duration.takeIf { it > 0 } ?: taste.mine[songKey(s)]?.duration ?: 0.0
            if (n >= 8 && dur > 0) {
                val h = n * dur / 3600
                if (h >= 1) out["hours:$id"] = "Did you know? You've spent about ${hoursText(h)} hours listening to ${q(s)}. That's ${num(n)} plays."
                else out["minutes:$id"] = "You've played ${q(s)} ${num(n)} times. That's about ${(n * dur / 60).toInt()} minutes of it."
            }
            f.first[k]?.let { t ->
                val days = ((nowS - t) / 86_400L).toInt()
                if (nowS - t > 2 * 365 * 86_400L) out["first:$id"] = "Fun fact: you first played ${q(s)} way back in ${month(t)}."
                else if (days in 1..21 && n >= 5) out["crush:$id"] = "You only found ${q(s)} $days days ago, and you've already played it ${num(n)} times. Someone has a new favourite."
            }
            heard[songKey(s)]?.let { (sk, full) ->
                if (full >= 8 && sk == 0) out["never-skipped:$id"] = "You've heard ${q(s)} all the way through ${num(full)} times, and you've never skipped it. Not once."
                else if (sk >= 4 && sk > full) out["skipped:$id"] = "Real talk: you've skipped ${q(s)} ${num(sk)} times. Let's see if it wins you over today."
            }
            allRank[songKey(s)]?.takeIf { it < 10 && n >= 10 }?.let { i ->
                out["top-ten:$id"] = if (i == 0) "Heads up: ${q(s)} is your most played song of all time. Number one!"
                    else "${q(s)} is one of your top ten songs of all time. Number ${ordinal(i + 1)}, in fact."
            }
            val lead = s.artist.split(",")[0].trim()
            artistRank[lead.lowercase()]?.takeIf { it.second >= 20 }?.let { (r, plays) ->
                out["artist-rank:$lead"] = if (r == 1) "$lead is your number one artist of all time, with ${num(plays)} plays. Of course they made the set."
                    else "$lead is number ${ordinal(r)} on your all time artist list, with ${num(plays)} plays."
            }
        }
        // Your moods
        val moodPlays = f.moodMonth.values.sum()
        f.moodMonth.maxByOrNull { it.value }?.takeIf { moodPlays >= 20 && it.value * 4 >= moodPlays }?.let { (m, n) ->
            out["mood-month"] = "Lately you've mostly played ${Moods.adjective(m)} songs. That's ${n * 100 / moodPlays} percent of what you played from your own music this month."
        }
        taste.moodHour.maxByOrNull { it.value }?.takeIf { it.value >= 10 }?.let { (m, _) ->
            out["mood-hour"] = "Around this time of day, you usually go for something ${Moods.adjective(m)}. I took notes."
        }
        moodFacts(taste, nowS, out)
        // Your artists and songs
        val byMonth = f.artistMonth.entries.sortedByDescending { it.value }
        byMonth.getOrNull(0)?.takeIf { it.value >= 10 }?.let { (a, n) -> out["top-artist-month"] = "Your most played artist this month? $a, with ${num(n)} plays. No surprise there." }
        byMonth.getOrNull(1)?.takeIf { it.value >= 8 }?.let { (b, n) ->
            out["second-artist-month"] = "Your number two artist this month is $b, with ${num(n)} plays. ${byMonth[0].key} had better watch out."
        }
        f.artistAll.maxByOrNull { it.value }?.takeIf { it.value >= 50 }?.let { (a, n) -> out["top-artist-all"] = "All time, you've played $a ${num(n)} times. They're your number one." }
        if (f.artistAll.size >= 50) out["artists-count"] = "You've listened to ${num(f.artistAll.size)} different artists. Variety is the spice of life."
        if (f.count.size >= 200) out["songs-count"] = "You've played ${num(f.count.size)} different songs. That's a seriously deep library."
        taste.recent.firstOrNull()?.takeIf { it.plays >= 5 }?.let { s ->
            out["top-song-month"] = "Your most played song this month? ${q(s)} by ${by(s)}, with ${num(s.plays)} plays."
        }
        // Your genre lately, as your own files name it
        val genres = HashMap<String, Int>()
        for (t in taste.recent) for (g in genreParts(genreOf(t))) genres.merge(g.lowercase(), maxOf(1, t.plays), Int::plus)
        genres.maxByOrNull { it.value }?.takeIf { it.value >= 15 }?.let { (g, _) ->
            out["top-genre-month"] = "Your top genre lately is $g. You've played more of it than anything else this month."
        }
        // When you listen
        f.hours.withIndex().maxByOrNull { it.value }?.takeIf { it.value >= 50 }?.let { out["peak-hour"] = "You listen to the most music around ${hourName(it.index)}. I see you." }
        if (f.total >= 200) {
            val night = (0..4).sumOf { f.hours[it] } * 100 / f.total
            val early = (5..9).sumOf { f.hours[it] } * 100 / f.total
            if (night >= 10) out["night-share"] = "About $night percent of your listening happens after midnight. Night owl, confirmed."
            if (early >= 25) out["morning-share"] = "About $early percent of your music happens before 10 AM. Early bird energy."
        }
        if (f.monthPlays >= 100) out["month-plays"] = say("fact-month-plays",
            "You've played ${num(f.monthPlays)} songs so far this month. Busy ears.",
            "${num(f.monthPlays)} plays this month and counting. I'm keeping track.")
        f.artistFirst.entries.filter { nowS - it.value < 30 * 86_400L && (f.artistMonth[it.key] ?: 0) >= 15 }.maxByOrNull { f.artistMonth[it.key] ?: 0 }?.let { (a, _) ->
            out["new-artist-month"] = "Your big discovery this month? $a. ${num(f.artistMonth[a] ?: 0)} plays since you found them."
        }
        if (f.total >= 1000) out["total-plays"] = "I've counted ${num(f.total)} plays of yours. That's a lot of music."
        val years = (nowS - f.since) / (365 * 86_400L)
        if (f.since > 0 && years >= 2) out["since"] = "Your listening history goes back to ${month(f.since)}. That's over ${num(years.toInt())} years of music, and I've been reading all of it."
        // Your years
        val thisYear = java.time.LocalDate.now().year
        for ((y, top) in f.yearTop) if (y < thisYear && top.second >= 10)
            out["year-top:$y"] = "Your number one song of $y was “${top.first.title}” by ${top.first.artist.split(",")[0].trim()}, with ${num(top.second)} plays."
        f.yearPlays.takeIf { it.size >= 2 }?.maxByOrNull { it.value }?.takeIf { it.value >= 500 }?.let { (y, n) ->
            out["busiest-year"] = if (y == thisYear) "This is your biggest year for music yet, with ${num(n)} plays so far. Keep it up."
                else "Your biggest year for music was $y, with ${num(n)} plays. Think you can beat it?"
        }
        // Skips
        rows.filter { it.second >= 5 }.maxByOrNull { it.second }?.let { (t, sk, _) ->
            out["most-skipped"] = "The song you skip the most is ${q(t)}, with ${num(sk)} skips. No hard feelings though, it stays in the mix for when the mood is right."
        }
        rows.filter { it.second == 0 }.maxByOrNull { it.third }?.takeIf { it.third >= 15 }?.let { (t, _, full) ->
            out["never-skipped"] = "You've never skipped ${q(t)}. ${num(full)} times, start to finish."
        }
        val skips = rows.sumOf { it.second }; val outs = rows.sumOf { it.third }
        if (skips >= 25) out["skips-all"] = "You've skipped ${num(skips)} songs in all. Don't worry, a skip never keeps a song out of my sets, it just means the mood wasn't right."
        runCatching { Music.songStats.rows(nowS - 7 * 86_400L) }.getOrNull()?.let { wk ->
            val n = wk.sumOf { it.second }
            if (n >= 10) out["skips-week"] = "You've skipped ${num(n)} songs this week. Picky mood lately? That's fine, I'll keep the hits coming."
            wk.filter { it.second >= 3 }.maxByOrNull { it.second }?.let { (t, sk, _) ->
                out["skipped-week:${songKey(t)}"] = "This week you skipped ${q(t)} ${num(sk)} times. Not feeling it right now? It'll be back when you are."
            }
        }
        if (skips + outs >= 100) out["heard-out"] = "You hear ${outs * 100 / (skips + outs)} percent of songs all the way through. That's commitment."
        val key = NoRepeat.pick(out.keys.toList(), said(FACT_HISTORY), NO_REPEAT) ?: return@runCatching null
        remember(FACT_HISTORY, key)
        out.getValue(key)
    }.getOrNull()

    /** Facts about your moods (see [MoodTally]), from every play of your songs whose mood is known: this week's and
     *  all time's top mood, a mood you've gone for more than usual this month, your go-to song in a mood, the time of
     *  day you lean to a mood, how much of it is Hype, and a mood you've drifted away from. Into out, by key. */
    private fun moodFacts(taste: Taste, nowS: Long, out: MutableMap<String, String>) {
        val plays = taste.moodPlays
        if (plays.size < 20) return
        val day = 86_400L
        val week = MoodTally.counts(plays, nowS - 7 * day, nowS + 1)
        val month = MoodTally.counts(plays, nowS - 30 * day, nowS + 1)
        val before = MoodTally.counts(plays, nowS - 60 * day, nowS - 30 * day)
        val all = MoodTally.counts(plays, Long.MIN_VALUE, Long.MAX_VALUE)
        fun adj(m: String) = Moods.adjective(m)
        MoodTally.top(week, minPlays = 15)?.let { (m, n) ->
            out["mood-week"] = say("fact-mood-week",
                "This week you've mostly played ${adj(m)} songs. ${num(n)} plays of them, to be exact.",
                "The mood of the week in your plays? ${m}. ${MoodTally.percent(week, m)} percent of what you played from your music.",
                "If this week had a mood, it'd be $m. You played ${num(n)} ${adj(m)} songs.")
        }
        MoodTally.top(all, minPlays = 100)?.let { (m, n) ->
            out["mood-all"] = "All time, the mood you play most is $m. That's ${num(n)} plays, and ${MoodTally.percent(all, m)} percent of your music."
        }
        MoodTally.shift(month, before)?.let { (m, was, pct) ->
            out["mood-shift:$m"] = say("fact-mood-shift",
                "You've been going for more $m than usual lately. Up from $was percent last month to $pct percent now.",
                "Something's changed. Your $m songs went from $was percent of your plays to $pct percent this month.",
                "I'm noticing more ${adj(m)} songs from you lately. About $pct percent this month, from $was percent before.")
        }
        // Your go-to song in your top mood of the month (else of all time)
        val topMood = MoodTally.top(month, minPlays = 20)?.first ?: MoodTally.top(all, minPlays = 50)?.first
        if (topMood != null) MoodTally.topSong(plays, topMood, minPlays = 5)?.let { (song, n) ->
            moodSong(song)?.let { t ->
                out["mood-song:$topMood"] = "Your go to ${adj(topMood)} song? ${q(t)} by ${by(t)}. You've played it ${num(n)} times."
            }
        }
        // The time of day you lean to a mood
        val zone = java.time.ZoneId.systemDefault()
        MoodTally.leanings(plays, { java.time.Instant.ofEpochSecond(it).atZone(zone).hour }).entries
            .maxByOrNull { it.value.second }?.let { (m, lean) ->
                val part = lean.first
                out["mood-time:$m"] = when (part) {
                    "late night" -> "Late at night, you get ${adj(m)}. Your $m plays spike after ten."
                    "morning" -> "Mornings are for $m, apparently. That's when you play the most ${adj(m)} songs."
                    "afternoon" -> "Your afternoons lean $m. That's when the ${adj(m)} songs come out."
                    else -> "Evenings bring out your $m side. That's when you play the most ${adj(m)} music."
                }
            }
        // Hype's share
        val hype = MoodTally.percent(all, "Hype")
        if (all.values.sum() >= 100 && hype >= 5) out["mood-hype"] = if (hype >= 25) "About $hype percent of your listening is Hype. You don't do quiet, do you?"
            else "About $hype percent of your music is Hype. Just enough to keep things moving."
        // A mood you've drifted away from
        MoodTally.neglected(all, month)?.let { m ->
            out["mood-neglected:$m"] = "You used to play a lot of ${adj(m)} songs, but hardly any lately. Maybe I'll sneak one in."
        }
    }

    // ---- artists you might like ----
    @Serializable
    private data class Similar(val at: Long, val names: List<Pair<String, Double>>)
    private val similarCache = ConcurrentHashMap<String, Similar>()
    private val SIMILAR_MAP by lazy { kotlinx.serialization.builtins.MapSerializer(String.serializer(), Similar.serializer()) }
    private val similarFile get() = java.io.File(Music.ctx.filesDir, "similar-artists.json")
    @Volatile private var similarLoaded = false

    private fun deezer(url: String) = runCatching { Pictures.deezerPace(); httpJson(url) }.getOrNull()

    /** (blocking) Artists like name, each with how alike (0–1): Last.fm's similar artists, else Deezer's related ones;
     *  kept for a month. */
    private fun similarTo(name: String): List<Pair<String, Double>> {
        synchronized(similarCache) {
            if (!similarLoaded) {
                similarLoaded = true
                runCatching { readText(similarFile)?.let { JSON.decodeFromString(SIMILAR_MAP, it) } }.getOrNull()?.let { similarCache.putAll(it) }
            }
        }
        val key = norm(name)
        similarCache[key]?.takeIf { now().toLong() - it.at < 30 * 86_400L }?.let { return it.names }
        var got: List<Pair<String, Double>> = emptyList()
        if (LastFm.key.isNotEmpty()) got = runCatching {
            LastFm.call("artist.getSimilar", params = mapOf("artist" to name, "limit" to 30, "autocorrect" to 1)).path("similarartists", "artist").list
                .mapNotNull { a -> (a["name"].str ?: return@mapNotNull null) to (a["match"].str?.toDoubleOrNull() ?: a["match"].dbl ?: 0.5) }
        }.getOrDefault(emptyList())
        if (got.isEmpty()) got = runCatching {
            val id = deezer("https://api.deezer.com/search/artist?q=${java.net.URLEncoder.encode(name, "UTF-8")}&limit=3")!!["data"].list
                .firstOrNull { norm(it["name"].str ?: "") == key }?.let { it["id"].long } ?: return@runCatching emptyList()
            deezer("https://api.deezer.com/artist/$id/related?limit=25")!!["data"].list.mapIndexedNotNull { i, a ->
                (a["name"].str ?: return@mapIndexedNotNull null) to (1.0 - i / 30.0) }
        }.getOrDefault(emptyList())
        similarCache[key] = Similar(now().toLong(), got)
        runCatching { writeAtomic(similarFile, JSON.encodeToString(SIMILAR_MAP, similarCache.toMap())) }
        return got
    }

    /** (blocking) The artists you might like, best first, each with the ones of yours it's like: the artists you play
     *  most (all told, from Last.fm and every import, and lately counted double), each one's similar artists added up,
     *  the ones you already play left out. */
    private fun artistsForYou(taste: Taste): List<Pair<String, List<String>>> {
        val f = taste.facts
        val weight = HashMap<String, Double>()
        f.artistAll.entries.sortedByDescending { it.value }.take(12).forEach { (a, n) -> weight.merge(a, Math.log(1.0 + n), Double::plus) }
        f.artistMonth.entries.sortedByDescending { it.value }.take(6).forEach { (a, n) -> weight.merge(a, 2 * Math.log(1.0 + n), Double::plus) }
        if (weight.isEmpty()) taste.artists.take(10).forEach { weight[it] = 1.0 }
        val known = (f.artistAll.filterValues { it >= 3 }.keys + taste.mine.values.map { it.artist.split(",")[0].trim() }).map { norm(it) }.toSet()
        val score = HashMap<String, Double>(); val because = HashMap<String, MutableList<String>>(); val shown = HashMap<String, String>()
        for ((seed, w) in weight.entries.sortedByDescending { it.value }.take(10)) for ((name, match) in similarTo(seed)) {
            val k = norm(name)
            if (k.isEmpty() || k in known) continue
            score.merge(k, w * match, Double::plus); shown.putIfAbsent(k, name)
            because.getOrPut(k) { ArrayList() }.let { if (seed !in it) it += seed }
        }
        return score.entries.sortedByDescending { it.value }.map { (k, _) -> shown.getValue(k) to because[k].orEmpty() }
    }

    private val introduced: MutableSet<String> = ConcurrentHashMap.newKeySet()   // the artists for you the DJ played already

    /** Artists you might like: two you don't play yet, a few of each one's best known songs. */
    private fun setDiscover(taste: Taste): Pair<List<Track>, String>? {
        val known = known(taste) + used
        val picked = ArrayList<Pair<String, List<String>>>(); val songs = ArrayList<Track>()
        for ((name, because) in artistsForYou(taste).filter { norm(it.first) !in introduced }.take(12).shuffled()) {
            val info = runCatching { Music.artistInfo(name, null) }.getOrNull() ?: continue
            val top = info.top.filter { newToYou(it, known) }.take(3)
            if (top.size < 2) continue
            picked += (info.name.ifEmpty { name }) to because; songs += top
            introduced += norm(name)
            if (picked.size == 2) break
        }
        if (songs.size < 3) return null
        val (a, why) = picked[0]
        val like = why.take(2).joinToString(" and ")
        val both = if (picked.size > 1) "$a and ${picked[1].first}" else a
        return songs to say("discover",
            "Here's someone new I think you'll like: $a. If you're into $like, this is right up your alley.",
            "Time to find you a new favourite. Meet $both. You play a lot of $like, so trust me on this one.",
            "Fans of $like tend to love $a. Let's see if you do too.",
            "I've got a good feeling about this one. $a, for the $like fan in you.",
            "New artist alert! Say hello to $both. You can thank $like for this one.",
            "Someone you should know: $a. Big $like energy.",
            "Let me introduce you to $both. If $like is your thing, you're in for a treat.")
    }

    // ---- more kinds of set (3.2.15) ----
    private fun leadOf(t: Track) = t.artist.split(",")[0].trim()
    private fun mySongs(): List<Track> = only.get()?.let { scope?.second } ?: runCatching { Music.mySongs() }.getOrDefault(emptyList())

    // A song file's release year and track number, read from its tags once and kept (dj-tags.txt): for Decade trip and
    // Album moment. A file read once is never read again.
    private val tagInfo = ConcurrentHashMap<String, Pair<Int, Int>>()
    @Volatile private var tagInfoLoaded = false
    private val tagFile get() = java.io.File(Music.ctx.filesDir, "dj-tags.txt")
    private fun tagsLoaded() {
        if (tagInfoLoaded) return
        tagInfoLoaded = true
        runCatching { tagFile.readLines().forEach { l ->
            val p = l.split('\t'); if (p.size == 3) tagInfo[p[0]] = (p[1].toIntOrNull() ?: 0) to (p[2].toIntOrNull() ?: 0) } }
    }
    private fun saveTags() = runCatching {
        tagFile.writeText(tagInfo.entries.joinToString("\n") { "${it.key}\t${it.value.first}\t${it.value.second}" })
    }
    /** (year, track number) of your song (0 where its tags don't say), reading its file the first time; null when it
     *  isn't a file or can't be read. */
    private fun tagOf(t: Track): Pair<Int, Int>? {
        val f = t.file ?: return null
        tagsLoaded()
        tagInfo[f]?.let { return it }
        val tags = runCatching { FileTags.read(Music.ctx, f) }.getOrNull() ?: return null
        val year = Regex("(19|20)\\d\\d").find(tags["date"].orEmpty())?.value?.toIntOrNull() ?: 0
        val no = trackNo(tags["tracknumber"]).takeIf { it in 1..999 } ?: 0
        return (year to no).also { tagInfo[f] = it }
    }

    /** Artist spotlight: one artist you love, a few of their songs you play most and a few you hardly do. */
    private fun setSpotlight(taste: Taste): Pair<List<Track>, String>? {
        val byArtist = mySongs().groupBy { norm(leadOf(it)) }
        val cands = taste.facts.artistAll.entries.sortedByDescending { it.value }.take(25)
            .filter { (byArtist[norm(it.key)]?.size ?: 0) >= 5 }.shuffled()
        for ((a, n) in cands.take(4)) {
            val theirs = byArtist[norm(a)].orEmpty().filter { songKey(it) !in used }.sortedByDescending { playsOf(taste, it) }
            val hits = pick(theirs.take(10), 3, used)
            val deep = pick(theirs.drop(10).ifEmpty { theirs.drop(3) }.shuffled(), 2, used + hits.map { songKey(it) })
            val songs = (hits + deep).distinctBy { songKey(it) }
            if (songs.size < 4) continue
            val s = songs[0]
            return songs to say("spotlight",
                "Artist spotlight: $a. You've played them ${num(n)} times, so here's a mix of the big ones and a few deeper cuts, starting with ${q(s)}.",
                "Let's give $a the stage for a few songs. Some favourites, some you don't play as much, and ${q(s)} goes first.",
                "Spotlight on $a now. ${num(n)} plays tell me you're a fan. Here's ${q(s)}.",
                "It's a $a moment. The hits, the hidden ones, the lot. Starting with ${q(s)}.",
                "Five songs, one artist: $a. Let's go, with ${q(s)}.")
        }
        return null
    }

    /** Album moment: three in a row from an album of yours you play a lot, in the album's order. */
    private fun setAlbum(taste: Taste): Pair<List<Track>, String>? {
        val groups = mySongs().filter { it.album.isNotBlank() }.groupBy { norm(it.album) + "|" + norm(leadOf(it)) }.values.filter { it.size >= 4 }
        val ranked = groups.sortedByDescending { g -> g.sumOf { playsOf(taste, it) } }.take(15).shuffled()
        var read = 0
        for (g in ranked.take(4)) {
            val withNo = g.take(30).map { t -> if (read < 40) { read++; t to (tagOf(t)?.second ?: 0) } else t to 0 }
            if (withNo.count { it.second > 0 } < 3) continue
            val ordered = withNo.filter { it.second > 0 }.sortedBy { it.second }.map { it.first }
            // Three in a row, the run you play most (none the DJ played lately)
            val runs = (0..ordered.size - 3).map { i -> ordered.subList(i, i + 3) }.filter { r -> r.none { songKey(it) in used } }
            val run = runs.maxByOrNull { r -> r.sumOf { playsOf(taste, it) } + Random.nextInt(20) } ?: continue
            saveTags()
            val s = run[0]
            val album = s.album
            return run to say("album",
                "An album moment: three in a row from $album by ${by(s)}, in order, the way it was meant to be heard.",
                "Let's sit with $album for a bit. Three straight from the record, starting with ${q(s)}.",
                "Album time. From $album, back to back: ${q(s)} and the two that follow it.",
                "No shuffling for this one. $album by ${by(s)}, three songs in a row.")
        }
        if (read > 0) saveTags()
        return null
    }

    /** One year ago today (or two, or five): what you were playing around this date back then. */
    private fun setYearAgo(taste: Taste): Pair<List<Track>, String>? {
        for (ya in taste.dayAgo.keys.shuffled()) {
            val songs = pick(ok(taste, taste.dayAgo.getValue(ya)).take(30), SET, used)
            if (songs.size < 3) continue
            val s = songs[0]
            val ago = if (ya == 1) "One year ago" else "${ordinal(ya).replaceFirstChar { it.uppercase() }} years ago"
            return songs to say("yearago",
                "$ago this week, this is what you were listening to. Starting with ${q(s)} by ${by(s)}.",
                "Flashback to this week, ${ago.lowercase()}. You had ${q(s)} on.",
                "$ago today, give or take a few days, these were your songs. ${q(s)} first.",
                "On this week ${ago.lowercase()}: ${names(songs, 3)}. Bring back any memories?")
        }
        return null
    }

    private const val DECADE_HISTORY = "dj_decade_history"
    /** Decade trip: your songs released in one decade (their files' year), a different decade from last time. */
    private fun setDecade(taste: Taste): Pair<List<Track>, String>? {
        val mine = mySongs()
        if (mine.size < 20) return null
        tagsLoaded()
        // What's known already, plus a few more files read each time (so it learns your whole library over time)
        var read = 0
        val sample = mine.shuffled().take(400).mapNotNull { t ->
            val known = t.file?.let { tagInfo[it] }
            val info = known ?: if (read < 60) { read++; tagOf(t) } else null
            info?.first?.takeIf { it in 1950..2100 }?.let { t to it / 10 * 10 }
        }
        if (read > 0) saveTags()
        val byDecade = sample.groupBy({ it.second }, { it.first }).filter { it.value.size >= 8 }
        if (byDecade.isEmpty()) return null
        val decade = MoodTally.moodOrder(byDecade.keys.map { it.toString() }, said(DECADE_HISTORY)).firstOrNull()?.toIntOrNull()
            ?: byDecade.keys.random()
        val songs = pick(ok(taste, byDecade.getValue(decade)).shuffled(), SET, used)
        if (songs.size < 3) return null
        remember(DECADE_HISTORY, decade.toString())
        val d = if (decade < 2000) "the ${decade % 100}s" else "the ${decade}s"
        val s = songs[0]
        return songs to say("decade",
            "Let's take a trip to $d. Everything in this set came out back then, starting with ${q(s)}.",
            "Decade trip! We're heading to $d with ${q(s)} by ${by(s)}.",
            "Set the dial to $d. Your music from that decade, starting with ${q(s)}.",
            "A little time travel: $d, straight from your collection.")
    }

    /** One-hit wonders of yours: artists you have only a song or two from. */
    private fun setOneHit(taste: Taste): Pair<List<Track>, String>? {
        val few = mySongs().groupBy { norm(leadOf(it)) }.values.filter { it.size in 1..2 }.flatten()
        if (few.size < 6) return null
        val songs = pick(ok(taste, few).shuffled(), SET, used).distinctBy { norm(leadOf(it)) }
        if (songs.size < 3) return null
        val s = songs[0]
        return songs to say("onehit",
            "Your one hit wonders: artists you've only got a song or two from. Starting with ${q(s)} by ${by(s)}.",
            "Some artists only made it into your music once. Here's a few of them, like ${by(s)}.",
            "The lone stars of your library. One song each, starting with ${q(s)}.",
            "Single appearances only. ${names(songs, 3)}, each the only one of theirs you have.")
    }

    /** Never skipped: songs you've always heard to the end. */
    private fun setNeverSkipped(taste: Taste): Pair<List<Track>, String>? {
        val rows = runCatching { Music.songStats.rows() }.getOrDefault(emptyList())
        val pool = rows.filter { (_, sk, full) -> sk == 0 && full >= 3 }.sortedByDescending { it.third }
            .map { (t, _, _) -> taste.mine[songKey(t)] ?: t }
        val songs = pick(ok(taste, pool).take(80), SET, used)
        if (songs.size < 3) return null
        val s = songs[0]
        return songs to say("neverskip",
            "Songs you've never skipped. Not once. Starting with ${q(s)}.",
            "Every one of these, you've always heard to the end. ${q(s)} first.",
            "Zero skips between them. These are the keepers, starting with ${q(s)} by ${by(s)}.",
            "Start to finish, every time. Here's ${names(songs, 3)}.")
    }

    /** Second chances: songs you skip a lot, played in a different set, in case the mood's right this time. */
    private fun setSecondChance(taste: Taste): Pair<List<Track>, String>? {
        val rows = runCatching { Music.songStats.rows() }.getOrDefault(emptyList())
        val pool = rows.filter { (_, sk, full) -> sk >= 3 && sk >= full }.map { (t, _, _) -> taste.mine[songKey(t)] ?: t }
        val songs = pick(ok(taste, pool).shuffled(), SET, used)
        if (songs.size < 3) return null
        val s = songs[0]
        return songs to say("second",
            "Second chances. These are ones you usually skip, but maybe today's the day. ${q(s)} first.",
            "Hear me out. You skip these a lot, but I think the mood might be right this time. Starting with ${q(s)}.",
            "A few songs that never get a fair shot. Let's give ${q(s)} one more try.",
            "Second chance round. If one still isn't for you, skip away, no hard feelings.")
    }

    /** Unreleased corner: songs from your Unreleased folders. */
    private fun setUnreleased(taste: Taste): Pair<List<Track>, String>? {
        val paths = runCatching { Music.local.paths }.getOrDefault(emptyMap())
        val pool = mySongs().filter { t -> (paths[t.file ?: ""] ?: t.file ?: "").contains("unreleased", ignoreCase = true) }
        val songs = pick(ok(taste, pool).shuffled(), SET, used)
        if (songs.size < 3) return null
        val s = songs[0]
        return songs to say("unreleased",
            "Welcome to the unreleased corner. Songs you won't find anywhere else, starting with ${q(s)}.",
            "Off the record now. Some unreleased ones from your vault, like ${q(s)} by ${by(s)}.",
            "From the vault: unreleased tracks only. ${q(s)} first.",
            "These never came out officially, but they're in your music. Here's ${q(s)}.")
    }

    /** Listening streaks: songs you've played on most days this past week. */
    private fun setStreak(taste: Taste): Pair<List<Track>, String>? {
        val days = taste.streak.associate { songKey(it.first) to it.second }
        val songs = pick(ok(taste, taste.streak.map { it.first }), SET, used)
        if (songs.size < 3) return null
        val s = songs[0]
        val n = days[songKey(s)] ?: 4
        return songs to say("streak",
            "You've played ${q(s)} on $n of the last seven days. That's a streak. Here it is, with a few more you can't put down.",
            "Streak alert! ${q(s)} has been on almost every day this week. Let's keep it going.",
            "These are the songs you've played day after day this week, starting with ${q(s)}.",
            "Every day this week, practically. ${names(songs, 3)}. On a streak, and I'm not stopping it.")
    }

    // ---- variety, and a mood arc, for every set ----
    /** How much energy a mood has (1 calm … 10 hype), for a set's arc. */
    private val ENERGY = mapOf("Calm" to 1, "Sad" to 2, "Dreamy" to 3, "Chill" to 3, "Focus" to 4, "Romantic" to 4, "Dark" to 5,
        "Happy" to 6, "Energetic" to 8, "Intense" to 8, "Party" to 9, "Hype" to 10)

    /** The set's songs after its first (the one its line names) in an arc by their moods: building up from a calm
     *  first song, easing down from a loud one, so it flows. Unchanged when few of them have a mood. */
    private fun arc(songs: List<Track>): List<Track> {
        if (songs.size < 3) return songs
        val e = songs.associate { songKey(it) to runCatching { Moods.of(it) }.getOrNull()?.let { m -> ENERGY[m] } }
        if (e.values.count { it != null } < 3) return songs
        val known = e.values.filterNotNull().sorted()
        val mid = known[known.size / 2]
        val start = e[songKey(songs[0])] ?: mid
        val rest = songs.drop(1).sortedBy { (e[songKey(it)] ?: mid) * if (start >= 6) -1 else 1 }
        return listOf(songs[0]) + rest
    }

    /** No artist twice in a row, and (but for an artist's own sets) at most two of an artist in a set. The first song
     *  stays first. */
    private fun spread(songs: List<Track>, cap: Boolean): List<Track> {
        if (songs.size < 3) return songs
        val counts = HashMap<String, Int>()
        val kept = songs.filter { t -> val a = norm(leadOf(t)); val n = (counts[a] ?: 0) + 1; counts[a] = n; !cap || n <= 2 }
            .let { if (it.size >= minOf(4, songs.size)) it else songs }   // too few left: the set as it was
        val out = ArrayList<Track>(); val left = kept.toMutableList()
        out += left.removeAt(0)
        while (left.isNotEmpty()) {
            val prev = norm(leadOf(out.last()))
            val i = left.indexOfFirst { norm(leadOf(it)) != prev }.takeIf { it >= 0 } ?: 0
            out += left.removeAt(i)
        }
        return out
    }

    // ---- what the DJ says about the time, and between songs ----
    /** Now and then, a word about the day before a set's line ("Friday night, let's go."), "" most of the time. */
    private fun timeFlavor(): String {
        if (Random.nextInt(10) >= 3) return ""
        val n = java.time.LocalDateTime.now()
        val h = n.hour
        return when {
            n.dayOfWeek == java.time.DayOfWeek.FRIDAY && h >= 17 -> say("t-fri", "It's Friday night. Let's go.", "Friday night, and the weekend starts now.", "Happy Friday!")
            n.dayOfWeek == java.time.DayOfWeek.SATURDAY -> say("t-sat", "Saturday, no rush.", "It's the weekend, so let's enjoy it.", "Saturday vibes, coming right up.")
            n.dayOfWeek == java.time.DayOfWeek.SUNDAY && h < 13 -> say("t-sun", "Easy Sunday morning.", "Lazy Sunday? I've got you.", "Sunday mornings are for good music.")
            n.dayOfWeek == java.time.DayOfWeek.MONDAY && h in 5..11 -> say("t-mon", "Monday morning. Let's ease into the week.", "New week, fresh start.", "Mondays are better with music.")
            h in 0..3 -> say("t-late", "It's late, but we're still going.", "Past midnight, and the music's still on.", "Late night, keeping you company.")
            h in 5..7 -> say("t-early", "Early start today.", "Up with the sun, I see.")
            else -> ""
        }
    }

    private val MILESTONES = listOf(25, 50, 100, 150, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000)

    /** (quick: from what's known already, nothing loaded) What the DJ might say before a song in the middle of a set:
     *  a milestone this play reaches (always), else now and then something about the song (your top ten, never skipped,
     *  a new obsession, an old friend). Null for nothing. */
    fun songComment(t: Track): String? = runCatching {
        val taste = Dj.taste ?: return null
        val f = taste.facts
        val k = Stats.statsKey(t.title, t.artist)
        val n = f.count[k] ?: 0
        val mk = "milestone:${songKey(t)}:${n + 1}"
        if (n + 1 in MILESTONES && mk !in said(FACT_HISTORY)) {
            remember(FACT_HISTORY, mk)
            return "Big moment: this is your ${num(n + 1)}th play of ${q(t)}. Here's to ${num(n + 1)} more."
        }
        if (Random.nextInt(10) >= 3) return null
        val out = LinkedHashMap<String, String>()
        val id = songKey(t).toString()
        taste.all.indexOfFirst { songKey(it) == songKey(t) }.takeIf { it in 0..9 && n >= 10 }?.let { i ->
            out["c-top:$id"] = if (i == 0) "Your number one song of all time, coming up." else "This one's in your all time top ten. Number ${ordinal(i + 1)}."
        }
        Music.songStats.rows().firstOrNull { songKey(it.first) == songKey(t) }?.let { (_, sk, full) ->
            if (sk == 0 && full >= 8) out["c-never:$id"] = "You've never skipped this one. ${num(full)} times, start to finish."
        }
        f.first[k]?.let { first ->
            val days = ((now().toLong() - first) / 86_400L).toInt()
            if (days in 1..21 && n >= 5) out["c-crush:$id"] = "You found this one $days days ago and you've played it ${num(n)} times already."
            if (days >= 730) out["c-old:$id"] = "An old friend: you first played this back in ${month(first)}."
        }
        val key = NoRepeat.pick(out.keys.toList(), said(FACT_HISTORY), NO_REPEAT) ?: return null
        remember(FACT_HISTORY, key)
        out.getValue(key)
    }.getOrNull()


    // ---- playing from songs you picked ----
    /** A set of kind from the songs the DJ plays from, or null. coming: the song keys still to come in the queue. */
    private fun fromSet(kind: String, taste: Taste, coming: Set<Pair<String, String>>): Pair<List<Track>, String>? {
        val (name, pool) = scope ?: return null
        var fresh = pool.filter { !isUsed(it, used) && songKey(it) !in coming }
        // Too few left: the ones played longest ago come back first, never the very last ones or ones still to come
        var gap = window()
        while (fresh.size < minOf(3, pool.size) && gap > 0) {
            gap /= 2
            val lately = usedInLast(gap)
            fresh = pool.filter { !isUsed(it, lately) && songKey(it) !in coming }
        }
        if (fresh.size < minOf(3, pool.size)) fresh = pool.filter { songKey(it) !in coming }.ifEmpty { pool }
        // Each song's plays: Last.fm's and your imports' (Most played); the plays here only when there are none of those
        // (playsOf: matched the way Most played matches, so "Song (feat. X)" by "A & B" is the song you scrobbled as
        // "Song" by "A"; before, a song you'd played could count as never played)
        val recent = taste.recent.map { songKey(it) }.toSet()
        val t = now().toLong()
        val n = minOf(SET, fresh.size)
        val least = minOf(3, fresh.size)
        var songs: List<Track> = emptyList()
        var line = ""
        when (kind) {
            "loved", "mostplayed" -> {
                // From well down the list too, not only the very top (those played lately are kept out anyway)
                songs = fresh.filter { playsOf(taste, it) > 0 }.sortedByDescending { playsOf(taste, it) }.take(150).shuffled().take(n)
                if (songs.isNotEmpty()) line = say("from-loved",
                    "From $name, the ones you keep coming back to, like ${q(songs[0])} by ${by(songs[0])}.",
                    "The best of $name, by your count. ${q(songs[0])} is up first.",
                    "Your top picks from $name, starting with ${q(songs[0])}.",
                    "These are the songs from $name you play the most. No surprises, just the good stuff.")
            }
            "unplayed" -> {
                val here = playedHere()
                songs = fresh.filter { playsOf(taste, it) == 0 && lastPlayed(taste, it) == 0L && Stats.statsKey(it.title, it.artist) !in here }.shuffled().take(n)
                if (songs.isNotEmpty()) line = say("from-unplayed",
                    "Some from $name you haven't played yet, starting with ${q(songs[0])} by ${by(songs[0])}.",
                    "Hiding in $name: songs you've never played. Let's fix that, starting with ${q(songs[0])}.",
                    "First listen time. These are from $name, and they're all new to you.",
                    "You've got songs in $name you haven't even heard yet. Here's ${q(songs[0])}.")
            }
            "throwback" -> {
                songs = fresh.filter { playsOf(taste, it) > 0 && songKey(it) !in recent && t - lastPlayed(taste, it) > 21 * 86_400L }
                    .shuffled().take(n)
                if (songs.isNotEmpty()) line = say("from-throwback",
                    "From $name, a few you haven't played in a while, like ${q(songs[0])}.",
                    "Let's revisit some of $name you haven't heard lately. ${q(songs[0])} first.",
                    "It's been a while for these ones from $name. Welcome back, ${q(songs[0])}.",
                    "Dusting off a few from $name, starting with ${q(songs[0])} by ${by(songs[0])}.")
            }
            "hour" -> {
                val at = taste.hour.withIndex().associate { songKey(it.value) to it.index }
                songs = pick(fresh.filter { songKey(it) in at }.sortedBy { at[songKey(it)] }, n, emptySet())
                if (songs.isNotEmpty()) line = say("from-hour",
                    "From $name, what you usually play around now, like ${q(songs[0])}.",
                    "Right on time. From $name, your usual for this hour, starting with ${q(songs[0])}.",
                    "From $name, the ones that always come out around this time. ${q(songs[0])} leads.",
                    "The songs from $name you reach for at this hour, starting with ${q(songs[0])}.")
            }
            "artist" -> {
                val lead = { s: Track -> s.artist.split(",")[0].trim() }
                val count = fresh.groupingBy(lead).eachCount().filter { it.key.isNotEmpty() && it.value >= least }
                if (count.size > 1) {   // one artist in it all: that's not a set of its own
                    var r = Random.nextInt(count.values.sum())
                    val who = count.entries.first { r -= it.value; r < 0 }.key
                    songs = fresh.filter { lead(it) == who }.shuffled().take(n)
                    if (songs.isNotEmpty()) line = say("from-artist",
                        "A few from $who, from $name.",
                        "Spotlight on $who now, straight from $name.",
                        "Let's give $who some time. A few of theirs from $name.",
                        "From $name, a little run of $who, starting with ${q(songs[0])}.")
                }
            }
            else -> {
                songs = fresh.shuffled().take(n)
                if (songs.isNotEmpty()) line = say("from-mix",
                    "More from $name: ${names(songs, 3)}.",
                    "A bit of everything from $name. ${q(songs[0])} kicks it off.",
                    "Shuffling through $name. Up next, ${q(songs[0])} by ${by(songs[0])}.",
                    "Back to $name with a mix. ${names(songs, 3)}.")
            }
        }
        if (songs.isEmpty() || songs.size < least || line.isEmpty()) return null
        return songs to line
    }

    /** A set of the songs the DJ plays from that match what you asked (artist, title, album). */
    private fun askFrom(text: String): Built? {
        val (name, pool) = scope ?: return null
        val words = WORD.findAll(text).map { it.value }.filter { it.lowercase() !in FILLER }.map { norm(it) }.filter { it.isNotEmpty() }.toList()
        if (words.isEmpty()) return null
        // A mood ("something chill"): the ones of them with that mood
        Moods.fromWords(text)?.takeIf { words.size <= 2 }?.let { mood ->
            val hits = pool.filter { mood in Moods.all(it) }
            val songs = pick(hits.shuffled(), SET, used)   // none of the last sets' songs: others of the mood in their place
            if (songs.size >= minOf(3, hits.size) && songs.isNotEmpty())
                return Built("ask", songs, "From $name, something ${Moods.adjective(mood)}: “${songs[0].title}” by ${names(songs.take(1))} first.")
        }
        val hits = pool.filter { t -> val all = norm("${t.title} ${t.artist} ${t.album} ${genreOf(t)}"); words.all { it in all } }
        val songs = pick(hits.shuffled(), SET, used)
        if (songs.isEmpty()) return null
        return Built("ask", songs, "From $name, you asked for ${text.trim()}: “${songs[0].title}” by ${names(songs.take(1))} first.")
    }

    /** (blocking) A set for what you asked: an artist, a genre, a decade or a vibe; null if nothing fits.
     *  Playing from songs you picked: the ones of them that match, else from everywhere. */
    fun ask(text: String): Built? = askAny(text)?.let { it.copy(songs = Music.localsOf(it.songs)) }   // your own copies

    private fun askAny(text: String): Built? {
        val sc = scope ?: return askAll(text)
        askFrom(text)?.let { return it }
        if (!withNew) return null   // Only my songs: nothing from anywhere else
        return askAll(text)?.let { it.copy(line = "There's nothing like that in ${sc.first}, so from everywhere: ${it.line}") }
    }

    /** A set of your songs with mood, for what you asked; null when fewer than three have it. */
    private fun askMood(mood: String, asked: String, taste: Taste): Built? {
        val have = Moods.songs(mood)
        val songs = pick(ok(taste, have).ifEmpty { have }.shuffled(), SET, used)
        if (songs.size < 3) return null
        return Built("ask", songs, "You asked for $asked: here's something ${Moods.adjective(mood)} from your music, starting with “${songs[0].title}” by ${names(songs.take(1))}.")
    }

    private fun askAll(text: String): Built? {
        val taste = loadTaste()
        val asked = text.trim()
        val words = Regex("[\\p{L}\\p{N}_'&.-]+").findAll(asked).map { it.value }.filter { it.lowercase() !in FILLER }.joinToString(" ")
        val named = HashMap<String, String>()
        taste.artists.forEach { named[norm(it)] = it }
        taste.mine.values.forEach { val a = it.artist.split(",")[0].trim(); if (a.isNotEmpty()) named[norm(a)] = a }
        // A mood ("something chill", "sad songs"): your own songs with that mood first (Moods), when that's all you asked
        val mood = Moods.fromWords(words)
        val justMood = mood != null && words.split(" ").count { it.isNotBlank() } <= 2
        if (justMood) askMood(mood!!, asked, taste)?.let { return it }
        var who = if (words.isNotEmpty()) bestNamed(named.values.toList(), words) { it } else null
        val vibe = justMood || words.lowercase().split(" ").any { it in WORDS } || Regex("\\d0s").containsMatchIn(words)
        if (who == null && words.isNotEmpty() && !vibe) {
            val info = runCatching { Music.artistInfo(words, null) }.getOrNull()   // else not an artist
            // Only an artist called just that: "drum and bass" isn't DrymBass.
            val the = Regex("^the ", RegexOption.IGNORE_CASE)
            if (info != null && norm(info.name.replace(the, "")) == norm(words.replace(the, ""))) who = info.name
        }
        if (who != null) {
            val info = runCatching { Music.artistInfo(who, null) }.getOrNull()
            val theirs = taste.mine.values.filter { t -> norm(who) in splitArtists(t.artist).map { norm(it) } }
            val songs = pick(pick(theirs, 2, used) + (info?.top ?: emptyList()), SET, used)
            if (songs.isNotEmpty()) return Built("ask", songs, "${info?.name?.ifEmpty { null } ?: who}, coming up: some you know, and some you might not.")
        }
        if (mood != null) askMood(mood, asked, taste)?.let { return it }
        val tags = tags(asked)
        // Your own songs' genres first ("rock": your songs tagged Rock or Indie Rock), then Last.fm's
        for (g in (listOf(words) + tags).filter { it.isNotBlank() }.distinct()) {
            val songs = genreSongs(g, taste)
            if (songs.size >= 3) return Built("ask", songs, "You asked for $asked: here's ${g.lowercase()} from your music, starting with “${songs[0].title}” by ${names(songs.take(1))}.")
        }
        for (tag in tags) {
            val songs = tagSongs(tag, taste)
            if (songs.size >= 3) return Built("ask", songs, "You asked for $asked, so here's some $tag, starting with “${songs[0].title}” by ${names(songs.take(1))}.")
        }
        // A playlist of it: named by its mood ("chill"), which finds a curated one more often than a sentence.
        for (q in (tags.take(1) + asked).distinct()) for (key in services()) {
            val found = runCatching { Music.sources[key]?.mixFor(q) ?: emptyList() }.getOrDefault(emptyList())   // else the other service
            val songs = pick(found, SET, used)
            if (songs.size >= 3) return Built("ask", songs, "You asked for $asked. Here's a mix of it, starting with “${songs[0].title}” by ${names(songs.take(1))}.")
        }
        return null
    }

    /** A set of kind as playing from everything makes it (or for songs you picked, with [only] set). */
    private fun regularSet(k: String, taste: Taste): Pair<List<Track>, String>? = when (k) {
        "repeat" -> setRepeat(taste)
        "throwback" -> setThrowback(taste)
        "fresh" -> setFresh(taste)
        "deep" -> setDeep(taste)
        "genre" -> setGenre(taste)
        "like" -> setLike(taste)
        "hour" -> setHour(taste)
        "rediscover" -> setRediscover(taste)
        "mostplayed" -> setMostPlayed(taste)
        "discover" -> setDiscover(taste)
        "spotlight" -> setSpotlight(taste)
        "album" -> setAlbum(taste)
        "yearago" -> setYearAgo(taste)
        "decade" -> setDecade(taste)
        "onehit" -> setOneHit(taste)
        "neverskip" -> setNeverSkipped(taste)
        "second" -> setSecondChance(taste)
        "unreleased" -> setUnreleased(taste)
        "streak" -> setStreak(taste)
        else -> null
    }

    /** (blocking) The next set: of kind, else a kind picked at random that has songs, none of the kinds of the last
     *  15 sets (fewer when there are few kinds to choose from; see [kindsToTry]), avoid (the kind playing now) only
     *  when no other has. first: the DJ's first set, with its hello. coming: the song keys still to come in the
     *  queue. Null if there are none. */
    fun build(kind: String? = null, avoid: String? = null, first: Boolean = false, coming: Set<Pair<String, String>> = emptySet()): Built? {
        loadRecent()   // the last sets' songs (kept across restarts), so none comes back too soon
        val taste = loadTaste()
        val sc0 = scope
        val order = if (sc0 != null) (if (withNew) FROM_NEW_ORDER else FROM_ORDER) else ORDER
        val key = if (sc0 != null) FROM_HISTORY else KIND_HISTORY
        val tries = if (kind != null) listOf(kind) else kindsToTry(order, key, avoid)
        for (k in tries) {
            val sc = scope
            val built = if (k.startsWith("mood:")) setMoodMix(taste, sc?.second, k.removePrefix("mood:"))   // the mood's songs, known and new
            else if (sc != null && k in FROM_KINDS) fromSet(k, taste, coming)
            else if (sc != null) {
                // A kind made for everything, for the songs picked: of only them; the kinds of new songs go by them
                // (songs like them, their artists)
                val keys = HashSet<Pair<String, String>>()
                sc.second.forEach { keys += songKey(it); keys += Stats.statsKey(it.title, it.artist) }
                if (k in NEW_KINDS) {
                    val artists = sc.second.groupingBy { it.artist.split(",")[0].trim() }.eachCount().entries
                        .filter { it.key.isNotEmpty() }.sortedByDescending { it.value }.map { it.key }.take(20)
                    regularSet(k, Taste(taste.mine, sc.second.shuffled().take(20), sc.second, artists, taste.last, taste.hour, taste.years,
                        taste.facts, taste.moods, taste.moodHour, taste.moodPlays, taste.dayAgo, taste.streak))
                } else {
                    only.set(keys)
                    try {
                        regularSet(k, taste)?.let { (songs, line) -> songs.filter { inScope(it) }.takeIf { it.size >= 3 }?.let { it to line } }
                    } finally { only.set(null) }
                }
            } else regularSet(k, taste)
            // Never a song the DJ played in its last 300 (or one still to come), whatever the kind: the kinds made for the
            // songs picked keep to that themselves (letting the longest-ago back in when there are few)
            val got = if (built == null || (sc != null && k in FROM_KINDS)) built else {
                val avoidNow = used
                built.first.filter { !isUsed(it, avoidNow) && songKey(it) !in coming }.takeIf { it.size >= 3 }?.let { it to built.second }
            }
            if (got == null) {   // can't make one now: tried after the others for a while, and not counted
                if (kind == null) dead["$key:$k"] = now()
                continue
            }
            dead.remove("$key:$k")
            if (k in order) remember(KIND_HISTORY, canon(k), keep = 200)   // played: it waits until every other kind has had a turn
            // Every set flows by mood (calm building up, loud easing down) and no artist comes twice in a row (nor more
            // than twice in a set, but for an artist's own set); an album moment stays in the album's order
            val songs = if (k == "album") got.first else spread(arc(got.first), cap = k !in setOf("spotlight", "deep", "artist", "discover"))
            val line = if (first) hello() + " " + got.second else timeFlavor().let { if (it.isEmpty()) got.second else "$it ${got.second}" }
            // Now and then (and never on the first set: that has its hello) something the DJ knows about you
            val fact = if (!first && Random.nextInt(5) < 2) fact(songs) ?: "" else ""
            // Every song you have yourself (a file or a download) plays from your copy, its cover and tags shown from the start
            return Built(k, Music.localsOf(songs), line, fact)
        }
        return null
    }
}
