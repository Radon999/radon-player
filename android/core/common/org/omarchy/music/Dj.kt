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
 *  in months, and new ones like yours (Tidal's and YouTube Music's radios), leaving out the ones you
 *  keep skipping. Your history is every play Most played counts: Last.fm's scrobbles, your Spotify
 *  and Apple Music imports, and the plays here. "Switch it up" starts another kind of set, and you can
 *  ask for one: an artist, a genre, a decade or a vibe. Building a set
 *  blocks (run it in [Music.bg]); [Music] plays them. */
object Dj {
    const val SET = 6   // songs in a set
    val ORDER = listOf("repeat", "fresh", "hour", "throwback", "discover", "mood", "deep", "rediscover", "fresh", "favorites", "genre", "like", "discover", "moodmix")
    /** Playing from songs you picked (a playlist, a folder, an album): sets of only those. */
    val FROM_ORDER = listOf("loved", "unplayed", "hour", "mood", "artist", "throwback", "mix")
    // …and with new songs too: sets of new ones like them in between.
    val FROM_NEW_ORDER = listOf("loved", "fresh", "unplayed", "hour", "like", "mood", "artist", "fresh", "throwback", "mix", "moodmix")
    @Volatile var withNew = true   // with a scope: new songs like those too
    val KINDS = mapOf("repeat" to "On repeat", "fresh" to "New for you", "throwback" to "Throwback", "deep" to "Deep cuts",
        "genre" to "Your genre", "like" to "More like this", "ask" to "You asked", "loved" to "Your favourites",
        "unplayed" to "Not played yet", "artist" to "One artist", "mix" to "Mix", "hour" to "This time of day",
        "rediscover" to "Rediscover", "favorites" to "Your favourites", "discover" to "Artists for you", "mood" to "Your mood",
        "moodmix" to "Mood mix")
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
    fun hello(h: Int = LocalTime.now().hour): String =
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

    private fun remember(key: String, what: String) = synchronized(lock) {
        val h = NoRepeat.remember(said(key), what)
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

    /** The kinds of set to try next, best first: at random (a kind twice in order is twice as likely), none of the
     *  last 15 sets' kinds (fewer when there are few kinds, so there's always a choice), then those, the stalest first;
     *  kinds that couldn't make a set in the last ten minutes after them, and avoid last of all. */
    private fun kindsToTry(order: List<String>, key: String, avoid: String?): List<String> {
        val t = now()
        val usable = order.distinct().filter { t - (dead["$key:$it"] ?: -1e12) > 600 }
        val w = NoRepeat.window(NO_REPEAT, usable.size, 2)
        var tries = NoRepeat.order(order.filter { it in usable }, said(key), w) + order.distinct().filter { it !in usable }
        if (avoid != null) tries = tries.filter { it != avoid } + tries.filter { it == avoid }
        return tries
    }

    private fun q(t: Track) = "“${t.title}”"
    private fun by(t: Track) = names(listOf(t))
    private fun num(n: Int) = String.format(java.util.Locale.ENGLISH, "%,d", n)

    // ---- what you like ----
    /** all: your most played songs of all time; recent: of the last 30 days; hour: what you play around this
     *  hour; years: each year's most played; last: when you last played each song (stats key); artists: the
     *  ones you've played most lately; skipped: songs you keep skipping; moods: the moods (Moods) of what you played
     *  in the last 30 days, and moodHour of what you play around this hour, each with how many plays. */
    private class Taste(val mine: Map<Pair<String, String>, Track>, val recent: List<Track>, val all: List<Track>,
                        val artists: List<String>, val last: Map<Pair<String, String>, Long>,
                        val hour: List<Track> = emptyList(), val years: Map<Int, List<Track>> = emptyMap(),
                        val skipped: Set<Pair<String, String>> = emptySet(), val facts: Facts = Facts(),
                        val moods: Map<String, Int> = emptyMap(), val moodHour: Map<String, Int> = emptyMap(),
                        val moodPlays: List<MoodPlay> = emptyList())

    /** What the DJ knows to tell you about: every song's plays and first play, your artists all told and this month,
     *  the hours you listen at, how many plays in all, each year's number one and plays, the moods of this month's plays. */
    private class Facts(val count: Map<Pair<String, String>, Int> = emptyMap(), val first: Map<Pair<String, String>, Long> = emptyMap(),
                        val artistAll: Map<String, Int> = emptyMap(), val artistMonth: Map<String, Int> = emptyMap(),
                        val hours: IntArray = IntArray(24), val total: Int = 0, val yearTop: Map<Int, Pair<Heard, Int>> = emptyMap(),
                        val since: Long = 0, val moodMonth: Map<String, Int> = emptyMap(), val yearPlays: Map<Int, Int> = emptyMap())

    private var taste: Taste? = null
    private var tasteAt = 0.0
    /** How many sets go by before a song the DJ played can come back: in the meantime every set (a mood, This time
     *  of day, your favourites…) takes another song of its kind in its place. */
    const val SONG_GAP = 20
    private const val RECENT_SETS = "dj_recent_sets"
    private val recentLock = Any()
    private var recentSets: ArrayDeque<List<String>>? = null   // the last SONG_GAP sets' songs, oldest first ("title|artist")

    /** Song keys of what the DJ played in its last [SONG_GAP] sets (kept across restarts): never picked again till then. */
    @Volatile var used: Set<Pair<String, String>> = emptySet()
        private set

    private fun recent(): ArrayDeque<List<String>> = recentSets ?: ArrayDeque<List<String>>().also { q ->
        Prefs.text(RECENT_SETS).split('\u0001').filter { it.isNotBlank() }.forEach { set -> q += set.split('\n').filter { it.isNotBlank() } }
        while (q.size > SONG_GAP) q.removeFirst()
        recentSets = q
        used = q.flatten().map { it.substringBeforeLast('|') to it.substringAfterLast('|') }.toHashSet()
    }

    /** A set the DJ is about to play: its songs (keys) can't come back for [SONG_GAP] sets. */
    fun played(keys: Collection<Pair<String, String>>) {
        val text = synchronized(recentLock) {
            val q = recent()
            q += keys.map { "${it.first}|${it.second}" }.distinct()
            while (q.size > SONG_GAP) q.removeFirst()
            used = q.flatten().map { it.substringBeforeLast('|') to it.substringAfterLast('|') }.toHashSet()
            q.joinToString("\u0001") { it.joinToString("\n") }
        }
        Prefs.setText(RECENT_SETS, text)
    }

    /** Song keys of the last n sets. */
    private fun usedInLast(n: Int): Set<Pair<String, String>> = synchronized(recentLock) {
        recent().toList().takeLast(n).flatten().map { it.substringBeforeLast('|') to it.substringAfterLast('|') }.toHashSet()
    }

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

    /** (blocking) Every play Most played counts: your Last.fm scrobbles (else the plays here) and your imports. */
    private fun history(): List<Heard> {
        val out = ArrayList<Heard>()
        if (LastFm.connected) runCatching { Wrapped.allScrobbles(Music.ctx).forEach { out += Heard(it.t, it.artist, it.title) } }
        // The plays here, always (they're the DJ's own diary of when you played what): with Last.fm on too, only the ones
        // Last.fm hasn't got, so none counts twice. They never count in Most played or your stats.
        val near = HashSet<Pair<Pair<String, String>, Long>>()
        out.forEach { near += Stats.statsKey(it.title, it.artist) to it.t / 900 }
        for (e in Music.plays.items.values.toList()) for (x in e.at) {
            val k = Stats.statsKey(e.track.title, e.track.artist)
            if ((k to x / 900) in near || (k to x / 900 - 1) in near || (k to x / 900 + 1) in near) continue
            out += Heard(x, e.track.artist, e.track.title)
        }
        for (p in SpotifyHistory.plays + OtherPlays.all + AppleHistory.timed()) out += Heard(p.t, p.artist, p.title)
        return out
    }

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
            val mood = moodOf.getOrPut(k) { mine[k]?.let { runCatching { Moods.of(it) }.getOrNull() } }
            if (mood != null) {
                moodPlays += MoodPlay(p.t, mood, "${k.first}\t${k.second}")
                if (age < 30 * 86_400L) moodMonth.merge(mood, 1, Int::plus)
                if (age < 365 * 86_400L && Math.floorMod(at.hour - h + 12, 24) - 12 in -1..1) moodHour.merge(mood, 1, Int::plus)
            }
        }
        val yearTop = years.mapNotNull { (y, c) -> c.maxByOrNull { it.value }?.let { (k, n) -> y to (names.getValue(k) to n) } }.toMap()
        val facts = Facts(count, first, artistAll, artistMonth, hours, total, yearTop, if (since == Long.MAX_VALUE) 0 else since, moodMonth,
            years.mapValues { it.value.values.sum() })
        fun songs(c: Map<Pair<String, String>, Int>, n: Int) = c.entries.sortedByDescending { it.value }.take(n)
            .map { (k, v) -> val p = names.getValue(k); Music.fmSong(p.title, p.artist, "", mine).copy(plays = v) }
        var all = runCatching { Music.mostPlayed().take(300) }.getOrDefault(emptyList())
        if (all.isEmpty()) all = Music.plays.items.values.sortedByDescending { it.plays }.take(300).map { it.track.copy(plays = it.plays) }
        var top = artists.entries.sortedByDescending { it.value }.map { it.key }.filter { it.isNotEmpty() }.take(20)
        val rec = songs(recent, 60)
        if (top.isEmpty()) top = (rec + all).map { it.artist.split(",")[0].trim() }.filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(20).map { it.key }
        // Skipped more often than heard out, three times or more: left out of the DJ's sets.
        val skipped = runCatching { Music.songStats.rows().filter { (_, n, f) -> n >= 3 && n > f }
            .flatMap { (t, _, _) -> listOf(songKey(t), Stats.statsKey(t.title, t.artist)) }.toSet() }.getOrDefault(emptySet())
        return Taste(mine, rec, all, top, last, songs(hour, 80),
            years.filter { it.value.values.sum() >= 30 }.mapValues { songs(it.value, 60) }, skipped, facts, moodMonth, moodHour,
            moodPlays.sortedBy { it.t }).also { taste = it; tasteAt = now() }
    }

    private fun lastPlayed(taste: Taste, t: Track) = taste.last[Stats.statsKey(t.title, t.artist)] ?: 0L
    private fun ok(taste: Taste, pool: List<Track>) = pool.filter { songKey(it) !in taste.skipped && Stats.statsKey(it.title, it.artist) !in taste.skipped }
    private fun newToYou(t: Track, known: Set<Pair<String, String>>) = songKey(t) !in known && Stats.statsKey(t.title, t.artist) !in known

    private fun known(taste: Taste): Set<Pair<String, String>> = taste.mine.keys + taste.all.map { songKey(it) } + taste.last.keys

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
        val songs = pick(ok(taste, taste.recent.take(25)), SET, used)
        if (songs.size < 3) return null
        val s = songs[0]
        return songs to say("repeat",
            "Here's what you've had on repeat lately, starting with ${q(s)} by ${by(s)}.",
            "You know these ones by heart. ${names(songs, 3)}, all on heavy rotation.",
            "Your heavy rotation, coming right up. First, ${q(s)}.",
            "These are the songs you can't stop playing. Honestly? I get it.",
            "On repeat, and for good reason. ${names(songs, 3)}. Sing along, nobody's listening.",
            "If it's been stuck in your head lately, it's probably in this set. ${q(s)} kicks it off.")
    }

    /** A year you played something to death: that year's most played, the ones you haven't played in two months. */
    private fun setThrowback(taste: Taste): Pair<List<Track>, String>? {
        val t = now().toLong()
        val thisYear = java.time.LocalDate.now().year
        for (y in taste.years.keys.filter { it < thisYear }.shuffled()) {
            val pool = ok(taste, taste.years.getValue(y)).filter { t - lastPlayed(taste, it) > 60 * 86_400L }
            val songs = pick(pool.take(40), SET, used)
            if (songs.size < 3) continue
            val s = songs[0]
            val n = num(s.plays)
            return songs to if (s.plays > 1) say("throwback-plays",
                    "Throwback to $y! You played ${q(s)} $n times that year.",
                    "Let's take it back to $y, when ${q(s)} was basically your whole personality. $n plays, to be exact.",
                    "Remember $y? You had ${q(s)} on replay, $n times.",
                    "The time machine is warmed up. Destination, $y, and ${q(s)}, which you played $n times.",
                    "A quick trip back to $y. ${q(s)} was on constant rotation back then, with $n plays.")
                else say("throwback",
                    "Throwback to $y, starting with ${q(s)} by ${by(s)}.",
                    "Going back to $y now, with the songs you were into back then.",
                    "Let's dust off $y. Starting with ${q(s)}.",
                    "Remember $y? These were your songs.")
        }
        return null
    }

    /** Songs you've played a lot, all told, but not for three months. */
    private fun setRediscover(taste: Taste): Pair<List<Track>, String>? {
        val t = now().toLong()
        val recent = taste.recent.map { songKey(it) }.toSet()
        val pool = ok(taste, taste.all).filter { songKey(it) !in recent && t - lastPlayed(taste, it) > 90 * 86_400L }
        val songs = pick(pool.take(120), SET, used)
        if (songs.size < 3) return null
        val s = songs[0]
        val since = lastPlayed(taste, s).takeIf { it > 0 }?.let { "since ${month(it)}" } ?: "in ages"
        val times = if (s.plays > 0) num(s.plays) else "a lot of"
        return songs to say("rediscover",
            "Remember this one? You've played ${q(s)} $times times, but not $since.",
            "Some old favourites you haven't heard $since. Starting with ${q(s)}.",
            "Let's dig up a few you used to love. You haven't played ${q(s)} $since.",
            "Here's one that's been gathering dust. ${q(s)}, $times plays, and nothing $since.",
            "Rediscovery time! These used to be big for you, starting with ${q(s)} by ${by(s)}.")
    }

    /** What you usually play around this hour. */
    private fun setHour(taste: Taste): Pair<List<Track>, String>? {
        val songs = pick(ok(taste, taste.hour.take(40)), SET, used)
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
            "Same time, same vibe. ${names(songs, 3)}, just how you like it $whenText.")
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

    /** Your own songs in the mood you've been in lately (or usually are at this hour), the ones you haven't played
     *  in a while likelier. */
    private fun setMood(taste: Taste): Pair<List<Track>, String>? {
        val (mood, byHour) = pickMood(taste) ?: return null
        val t = now().toLong()
        val pool = ok(taste, Moods.songs(mood)).shuffled().sortedBy { (t - lastPlayed(taste, it)) < 14 * 86_400L }   // not played in two weeks first
        val songs = pick(pool.take(80), SET, used)
        if (songs.size < 3) return null
        val s = songs[0]
        val adj = Moods.adjective(mood)
        val h = LocalTime.now().hour
        val whenText = if (h in 5..11) "in the morning" else if (h in 12..16) "in the afternoon" else if (h in 17..22) "in the evening" else "this late"
        return songs to if (byHour) say("mood-hour",
                "You're usually in a $adj mood $whenText, so here's more of that, starting with ${q(s)}.",
                "Something $adj, like you usually go for $whenText: ${names(songs, 3)}.",
                "Around this time you tend to go $adj. I'm not judging, I'm matching. ${q(s)} first.",
                "${adj.replaceFirstChar { it.uppercase() }} $whenText? That's your thing. Here's ${q(s)} by ${by(s)}.")
            else say("mood",
                "Lately you've been in a $adj mood. Let's keep it going with ${q(s)} by ${by(s)}.",
                "You've been playing a lot of $adj songs lately, so here are a few more: ${names(songs, 3)}.",
                "I'm picking up a $adj vibe from you lately. Here's ${q(s)} to match.",
                "Feeling $adj? Your recent plays say yes. ${q(s)} is up first.")
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
    private fun moodIntro(mood: String, s: Track): String = when (mood) {
        "Happy" -> say("moodmix-Happy", "Let's lift things up a little. A happy run, starting with ${q(s)}.",
            "Sun's out, at least in here. Some happy ones, kicking off with ${q(s)}.", "Time for a mood boost. Happy songs only, and ${q(s)} goes first.")
        "Energetic" -> say("moodmix-Energetic", "Let's pick up the pace. Energy up, starting with ${q(s)}.",
            "Okay, let's get moving. ${q(s)} gets us going.", "Shake off the slump. This one's all energy, and ${q(s)} leads the way.")
        "Party" -> say("moodmix-Party", "Let's turn this into a party. ${q(s)} first, so turn it up.",
            "Party mode, on. We're starting with ${q(s)}.", "If you're not dancing yet, give it a minute. ${q(s)} is up.")
        "Hype" -> say("moodmix-Hype", "Let's get hyped. ${q(s)}, and it only goes up from here.",
            "Hype mode. Hold on tight, ${q(s)} is first.", "Energy check. We're going full hype, with ${q(s)}.")
        "Chill" -> say("moodmix-Chill", "Let's go somewhere chill. ${q(s)}, then a few more to sink into.",
            "Time to slow it down and get comfy. ${q(s)} first.", "Deep breath. Something chill now, starting with ${q(s)}.")
        "Calm" -> say("moodmix-Calm", "Let's bring it way down. Calm and easy, starting with ${q(s)}.",
            "A quiet one now. ${q(s)}, nice and calm.", "Let's take a breather. ${q(s)}, and a few more like it.")
        "Dreamy" -> say("moodmix-Dreamy", "Let's drift for a while. Something dreamy, starting with ${q(s)}.",
            "Head in the clouds time. ${q(s)} first.", "A little dreamy detour now, starting with ${q(s)}.")
        "Focus" -> say("moodmix-Focus", "Heads down, let's focus. ${q(s)}, and a few more to get you in the zone.",
            "Getting things done? I've got you. Focus mode, starting with ${q(s)}.", "No distractions, just flow. ${q(s)} first.")
        "Romantic" -> say("moodmix-Romantic", "Let's set the mood. Something romantic, starting with ${q(s)}.",
            "A little romantic stretch now. ${q(s)} first.", "Candles optional. Romantic songs, starting with ${q(s)}.")
        "Sad" -> say("moodmix-Sad", "Let's sit with our feelings for a bit. Something sad, starting with ${q(s)}.",
            "Sometimes you just need a sad song. Here's ${q(s)}.", "Okay, tissues ready? A few sad ones, ${q(s)} first.")
        "Dark" -> say("moodmix-Dark", "Let's go somewhere darker. ${q(s)} opens the way.",
            "Lights down. Something dark now, starting with ${q(s)}.", "Into the shadows for a bit. ${q(s)} first.")
        "Intense" -> say("moodmix-Intense", "Let's turn it up. Something intense, starting with ${q(s)}.",
            "Brace yourself, it's getting intense. ${q(s)} first.", "No holding back now. ${q(s)} kicks it off.")
        else -> say("moodmix-other", "Let's change the mood. Something ${Moods.adjective(mood)}, starting with ${q(s)}.",
            "New mood, coming up. A ${Moods.adjective(mood)} run, starting with ${q(s)}.")
    }

    /** Mood mix: one mood (a different one from last time, rotating through them all), songs in it you know (yours, or
     *  played from a service, its mood from its file or its artist's tags) and, with new songs on, a couple you haven't
     *  heard that the DJ thinks you'll like (radios of the known ones, artists like yours) in that mood too. pool: the
     *  songs the DJ plays from (null: everything). */
    private fun setMoodMix(taste: Taste, pool: List<Track>?): Pair<List<Track>, String>? {
        val withNewSongs = pool == null || withNew
        val t0 = now().toLong()
        // Songs you've played that aren't files (streams): judged by their artists' tags, a few artists at a time.
        val streamed = (taste.recent + taste.hour + taste.all.take(150))
            .filter { it.file.isNullOrEmpty() && runCatching { Moods.of(it) }.getOrNull() == null }.distinctBy { songKey(it) }
        var lookups = 0
        for (mood in MoodTally.moodOrder(Moods.ALL, said(MOODMIX_HISTORY)).take(5)) {
            // Known: your songs in the mood (or the ones you picked to play from), those not played lately first
            val mine = (pool?.filter { mood in Moods.all(it) } ?: Moods.songs(mood))
            var have = ok(taste, mine).filter { songKey(it) !in used }.shuffled().sortedBy { (t0 - lastPlayed(taste, it)) < 7 * 86_400L }
            if (pool == null && have.size < 6) {
                val more = streamed.filter { s ->
                    val k = norm(s.artist.split(",")[0].trim())
                    if (!artistMoodCache.containsKey(k)) { if (lookups >= 12) return@filter false; lookups++ }
                    fitsMood(s, mood, unknownFits = false)
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
                    fresh += got.take(10).filter { fitsMood(it, mood, unknownFits = noKey) }.take(2)
                }
                // Artists like yours whose sound is this mood
                if (fresh.size < 2 && !noKey) for ((name, _) in artistsForYou(taste).take(8)) {
                    if (fresh.size >= 3) break
                    if (mood !in artistMoods(name).take(2)) continue
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
            val intro = moodIntro(mood, songs[0])
            val tail = if (news.isNotEmpty()) " " + say("moodmix-new",
                "I've mixed in a couple of new ones I think you'll like.",
                "There's something new in there too. Tell me what you think.",
                "And a few you haven't heard yet, same vibe.",
                "Plus a new find or two, picked just for you.") else ""
            return songs to intro + tail
        }
        return null
    }

    /** Your starred songs, the ones you play most likelier. */
    private fun setFavorites(taste: Taste): Pair<List<Track>, String>? {
        val plays = taste.all.associate { songKey(it) to it.plays }
        val favs = Music.favorites.tracks().sortedByDescending { plays[songKey(it)] ?: 0 }
        val songs = pick(ok(taste, favs), SET, used)
        if (songs.size < 3) return null
        val s = songs[0]
        return songs to say("favorites",
            "Some of your favourites, like ${q(s)} by ${by(s)}.",
            "Straight from your favourites: ${names(songs, 3)}. You starred these for a reason.",
            "Only the best now, your starred songs. Starting with ${q(s)}.",
            "You gave these a star, so I'm giving them the spotlight. ${q(s)} first.",
            "Favourites time. No skips needed, ${q(s)} leads the way.")
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
            "Let's try something new. I went looking for songs like $from, and I think I found some gems.")
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
                "Time to dig into the $who catalogue. Trust me, there's treasure in here.")
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
            "Genre of the moment: $tag. Blame $a for this one.")
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
                "Inspired by ${q(seed)} by ${by(seed)}. Let's ride this wave.")
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
                "Your music, your genre. A little $name, coming up.")
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
        val fits = runCatching { Music.mySongs() }.getOrDefault(emptyList()).filter { genreFits(Music.genreOf(it), genre) }
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
        val artistRank = f.artistAll.entries.sortedByDescending { it.value }.take(5).withIndex().associate { it.value.key to (it.index + 1 to it.value.value) }
        // The songs coming up
        for (s in next.take(4)) {
            val k = Stats.statsKey(s.title, s.artist)
            val id = "${k.first}|${k.second}"
            val n = f.count[k] ?: 0
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
            artistRank[lead]?.takeIf { it.second >= 20 }?.let { (r, plays) ->
                out["artist-rank:$lead"] = if (r == 1) "$lead is your number one artist of all time, with ${num(plays)} plays. Of course they made the set."
                    else "$lead is number ${ordinal(r)} on your all time artist list, with ${num(plays)} plays."
            }
        }
        // Your moods
        val moodPlays = f.moodMonth.values.sum()
        f.moodMonth.maxByOrNull { it.value }?.takeIf { moodPlays >= 20 && it.value * 4 >= moodPlays }?.let { (m, n) ->
            out["mood-month"] = "Lately you've been in a ${Moods.adjective(m)} mood. That's ${n * 100 / moodPlays} percent of what you played from your own music this month."
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
            out["most-skipped"] = "The song you skip the most is ${q(t)}, with ${num(sk)} skips. Don't worry, I'm keeping it out of my sets."
        }
        rows.filter { it.second == 0 }.maxByOrNull { it.third }?.takeIf { it.third >= 15 }?.let { (t, _, full) ->
            out["never-skipped"] = "You've never skipped ${q(t)}. ${num(full)} times, start to finish."
        }
        val skips = rows.sumOf { it.second }; val outs = rows.sumOf { it.third }
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
                "This week you've mostly been in a ${adj(m)} mood. ${num(n)} plays of it, to be exact.",
                "Your mood of the week? ${m}. ${MoodTally.percent(week, m)} percent of what you played from your music.",
                "If this week had a mood, it'd be $m. You played ${num(n)} ${adj(m)} songs.")
        }
        MoodTally.top(all, minPlays = 100)?.let { (m, n) ->
            out["mood-all"] = "All time, your number one mood is $m. That's ${num(n)} plays, and ${MoodTally.percent(all, m)} percent of your music."
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
            "New artist alert! Say hello to $both. You can thank $like for this one.")
    }

    // ---- playing from songs you picked ----
    /** A set of kind from the songs the DJ plays from, or null. coming: the song keys still to come in the queue. */
    private fun fromSet(kind: String, taste: Taste, coming: Set<Pair<String, String>>): Pair<List<Track>, String>? {
        val (name, pool) = scope ?: return null
        var fresh = pool.filter { songKey(it) !in used }
        // Fewer songs than 20 sets take: the ones played longest ago come back first, never the last few sets' or ones
        // still to come
        var gap = SONG_GAP - 1
        while (fresh.size < minOf(3, pool.size) && gap > 0) {
            val lately = usedInLast(gap)
            fresh = pool.filter { songKey(it) !in lately && songKey(it) !in coming }
            gap--
        }
        if (fresh.size < minOf(3, pool.size)) fresh = pool.filter { songKey(it) !in coming }.ifEmpty { pool }
        val plays = HashMap<Pair<String, String>, Int>()
        Music.plays.items.values.forEach { plays[songKey(it.track)] = it.plays }
        taste.all.forEach { plays.merge(songKey(it), it.plays) { a, b -> maxOf(a, b) } }
        val recent = taste.recent.map { songKey(it) }.toSet()
        val t = now().toLong()
        fresh = ok(taste, fresh).ifEmpty { fresh }
        val n = minOf(SET, fresh.size)
        val least = minOf(3, fresh.size)
        var songs: List<Track> = emptyList()
        var line = ""
        when (kind) {
            "loved" -> {
                songs = pick(fresh.filter { (plays[songKey(it)] ?: 0) > 0 }.sortedByDescending { plays[songKey(it)] }.take(30), n, emptySet())
                if (songs.isNotEmpty()) line = say("from-loved",
                    "From $name, the ones you keep coming back to, like ${q(songs[0])} by ${by(songs[0])}.",
                    "The best of $name, by your count. ${q(songs[0])} is up first.",
                    "Your top picks from $name, starting with ${q(songs[0])}.",
                    "These are the songs from $name you play the most. No surprises, just the good stuff.")
            }
            "unplayed" -> {
                songs = fresh.filter { (plays[songKey(it)] ?: 0) == 0 && lastPlayed(taste, it) == 0L }.shuffled().take(n)
                if (songs.isNotEmpty()) line = say("from-unplayed",
                    "Some from $name you haven't played yet, starting with ${q(songs[0])} by ${by(songs[0])}.",
                    "Hiding in $name: songs you've never played. Let's fix that, starting with ${q(songs[0])}.",
                    "First listen time. These are from $name, and they're all new to you.",
                    "You've got songs in $name you haven't even heard yet. Here's ${q(songs[0])}.")
            }
            "throwback" -> {
                songs = fresh.filter { (plays[songKey(it)] ?: 0) > 0 && songKey(it) !in recent && t - lastPlayed(taste, it) > 21 * 86_400L }
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
            "mood" -> {
                // The mood you've been in, if enough of these songs have it; else the mood most of them have
                val counts = HashMap<String, Int>()
                for (s in fresh) Moods.of(s)?.let { counts.merge(it, 1, Int::plus) }
                val mine = pickMood(taste)?.first?.takeIf { (counts[it] ?: 0) >= least }
                val mood = mine ?: counts.entries.filter { it.value >= least }.maxByOrNull { it.value }?.key
                if (mood != null && counts.size > 1) {   // all one mood: not a set of its own
                    songs = fresh.filter { mood in Moods.all(it) }.shuffled().take(n)
                    val adj = Moods.adjective(mood)
                    if (songs.isNotEmpty()) line = if (mine != null) say("from-mood-yours",
                            "From $name, the $adj ones, since that's the mood you've been in.",
                            "You've been feeling $adj lately, so here's the $adj side of $name.",
                            "Matching your mood. Here's something $adj from $name.",
                            "A $adj stretch from $name, because that's where your head's at lately.")
                        else say("from-mood",
                            "From $name, something $adj: ${names(songs, 3)}.",
                            "Let's take $name in a $adj direction. ${q(songs[0])} first.",
                            "The $adj corner of $name, starting with ${q(songs[0])}.",
                            "Time for the $adj side of $name. Here's ${q(songs[0])} by ${by(songs[0])}.")
                }
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
            val got = if (k == "moodmix") setMoodMix(taste, sc?.second)   // one mood, songs you know and new ones
            else if (sc != null && k in setOf("fresh", "like")) {   // new songs, from the ones it plays from
                val t2 = Taste(taste.mine, sc.second.shuffled().take(20), sc.second, taste.artists, taste.last, skipped = taste.skipped)
                if (k == "fresh") setFresh(t2) else setLike(t2)
            } else if (sc != null) fromSet(k, taste, coming) else when (k) {
                "repeat" -> setRepeat(taste)
                "throwback" -> setThrowback(taste)
                "fresh" -> setFresh(taste)
                "deep" -> setDeep(taste)
                "genre" -> setGenre(taste)
                "like" -> setLike(taste)
                "hour" -> setHour(taste)
                "mood" -> setMood(taste)
                "rediscover" -> setRediscover(taste)
                "favorites" -> setFavorites(taste)
                "discover" -> setDiscover(taste)
                else -> null
            }
            if (got == null) {   // can't make one now: tried after the others for a while, and not counted
                if (kind == null) dead["$key:$k"] = now()
                continue
            }
            dead.remove("$key:$k")
            if (k in order) remember(key, k)   // asked for or picked, it played: kept out of the next few
            val line = if (first) hello() + " " + got.second else got.second
            // Now and then (and never on the first set: that has its hello) something the DJ knows about you
            val fact = if (!first && Random.nextInt(5) < 2) fact(got.first) ?: "" else ""
            // Every song you have yourself (a file or a download) plays from your copy, its cover and tags shown from the start
            return Built(k, Music.localsOf(got.first), line, fact)
        }
        return null
    }
}
