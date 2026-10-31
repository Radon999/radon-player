package org.omarchy.music

import kotlinx.serialization.Serializable
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
    val ORDER = listOf("repeat", "fresh", "hour", "throwback", "deep", "rediscover", "fresh", "favorites", "genre", "like")
    /** Playing from songs you picked (a playlist, a folder, an album): sets of only those. */
    val FROM_ORDER = listOf("loved", "unplayed", "hour", "artist", "throwback", "mix")
    // …and with new songs too: sets of new ones like them in between.
    val FROM_NEW_ORDER = listOf("loved", "fresh", "unplayed", "hour", "like", "artist", "fresh", "throwback", "mix")
    @Volatile var withNew = true   // with a scope: new songs like those too
    val KINDS = mapOf("repeat" to "On repeat", "fresh" to "New for you", "throwback" to "Throwback", "deep" to "Deep cuts",
        "genre" to "Your genre", "like" to "More like this", "ask" to "You asked", "loved" to "Your favourites",
        "unplayed" to "Not played yet", "artist" to "One artist", "mix" to "Mix", "hour" to "This time of day",
        "rediscover" to "Rediscover", "favorites" to "Your favourites")
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
    data class Info(val kind: String, val line: String)

    /** What the DJ plays from: kind "all" (everything), "mine" (all your songs), "folder" (path, as
     *  "Music/Album/"), "playlist" (id, one of yours) or "songs" (an album's or a service playlist's). */
    @Serializable
    data class From(val kind: String, val id: String = "", val path: String = "", val name: String = "", val songs: List<Track> = emptyList())

    @Serializable
    data class State(val on: Boolean = false, val sets: Map<String, Info> = emptyMap(), val shown: String? = null, val from: From? = null)

    /** What the DJ screen shows: on, what it's doing, the kind of set playing and its line, what it plays from. */
    data class Shown(val on: Boolean = false, val busy: String = "", val kind: String = "", val line: String = "",
                     val kinds: Map<String, String> = emptyMap(), val from: String = "")

    data class Built(val kind: String, val songs: List<Track>, val line: String)

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

    // ---- what you like ----
    /** all: your most played songs of all time; recent: of the last 30 days; hour: what you play around this
     *  hour; years: each year's most played; last: when you last played each song (stats key); artists: the
     *  ones you've played most lately; skipped: songs you keep skipping. */
    private class Taste(val mine: Map<Pair<String, String>, Track>, val recent: List<Track>, val all: List<Track>,
                        val artists: List<String>, val last: Map<Pair<String, String>, Long>,
                        val hour: List<Track> = emptyList(), val years: Map<Int, List<Track>> = emptyMap(),
                        val skipped: Set<Pair<String, String>> = emptySet())

    private var taste: Taste? = null
    private var tasteAt = 0.0
    val used: MutableSet<Pair<String, String>> = ConcurrentHashMap.newKeySet()   // song keys the DJ has played since the app started
    private var step = 0   // where in ORDER (or FROM_ORDER) the next set comes from
    @Volatile var scope: Pair<String, List<Track>>? = null   // (name, songs) the DJ plays from; null: everything
        private set

    /** Play only songs (named name: a playlist, a folder), or with null everything. */
    fun playFrom(name: String?, songs: List<Track>?) {
        scope = if (name != null && !songs.isNullOrEmpty()) name to songs else null
        step = 0
    }

    private data class Heard(val t: Long, val artist: String, val title: String)

    /** (blocking) Every play Most played counts: your Last.fm scrobbles (else the plays here) and your imports. */
    private fun history(): List<Heard> {
        val out = ArrayList<Heard>()
        if (LastFm.connected) runCatching { Wrapped.allScrobbles(Music.ctx).forEach { out += Heard(it.t, it.artist, it.title) } }
        if (out.isEmpty()) for (e in Music.plays.items.values.toList()) for (x in e.at) out += Heard(x, e.track.artist, e.track.title)
        for (p in SpotifyHistory.plays + OtherPlays.all + AppleHistory.timed()) out += Heard(p.t, p.artist, p.title)
        return out
    }

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
        for (p in history()) {
            val k = Stats.statsKey(p.title, p.artist)
            names.putIfAbsent(k, p)
            last[k] = maxOf(last[k] ?: 0, p.t)
            val age = nowS - p.t
            val at = java.time.Instant.ofEpochSecond(p.t).atZone(zone)
            if (age < 30 * 86_400L) recent.merge(k, 1, Int::plus)
            if (age < 90 * 86_400L) artists.merge(p.artist.split(",")[0].trim(), 1, Int::plus)
            if (age < 365 * 86_400L && Math.floorMod(at.hour - h + 12, 24) - 12 in -1..1) hour.merge(k, 1, Int::plus)
            years.getOrPut(at.year) { HashMap() }.merge(k, 1, Int::plus)
        }
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
            years.filter { it.value.values.sum() >= 30 }.mapValues { songs(it.value, 60) }, skipped).also { taste = it; tasteAt = now() }
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
    private fun setRepeat(taste: Taste, first: Boolean): Pair<List<Track>, String>? {
        val songs = pick(ok(taste, taste.recent.take(25)), SET, used)
        if (songs.size < 3) return null
        return songs to if (first) "${greeting()}, I'm your DJ. Let's start with what you've had on repeat, like “${songs[0].title}” by ${names(songs.take(1))}."
            else "Back to what you've had on repeat lately: ${names(songs, 3)}."
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
            return songs to if (s.plays > 1) "Throwback to $y. You played “${s.title}” ${s.plays} times that year."
                else "Throwback to $y, starting with “${s.title}” by ${names(listOf(s))}."
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
        val when_ = lastPlayed(taste, s).takeIf { it > 0 }?.let {
            val d = java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault())
            ", not since ${d.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())} ${d.year}" } ?: ""
        return songs to "Remember this one? You've played “${s.title}” ${if (s.plays > 0) s.plays.toString() else "a lot of"} times$when_."
    }

    /** What you usually play around this hour. */
    private fun setHour(taste: Taste, first: Boolean): Pair<List<Track>, String>? {
        val songs = pick(ok(taste, taste.hour.take(40)), SET, used)
        if (songs.size < 3) return null
        val h = LocalTime.now().hour
        val whenText = if (h in 5..11) "this morning" else if (h in 12..16) "this afternoon" else if (h in 17..22) "tonight" else "this late"
        return songs to if (first) "${greeting()}, I'm your DJ. Here's what you usually play $whenText, starting with “${songs[0].title}”."
            else "What you usually play $whenText: ${names(songs, 3)}."
    }

    /** Your starred songs, the ones you play most likelier. */
    private fun setFavorites(taste: Taste): Pair<List<Track>, String>? {
        val plays = taste.all.associate { songKey(it) to it.plays }
        val favs = Music.favorites.tracks().sortedByDescending { plays[songKey(it)] ?: 0 }
        val songs = pick(ok(taste, favs), SET, used)
        if (songs.size < 3) return null
        return songs to "Some of your favourites, like “${songs[0].title}” by ${names(songs.take(1))}."
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
        return songs to "Something new for you, picked from ${names(seeds)}. Let's see what you think."
    }

    private fun setDeep(taste: Taste): Pair<List<Track>, String>? {
        for (name in taste.artists.take(10).shuffled().take(3)) {
            val info = runCatching { Music.artistInfo(name, null) }.getOrNull() ?: continue   // the next artist
            val known = known(taste) + used
            val songs = info.top.filter { newToYou(it, known) }.take(SET)
            if (songs.size >= 3) return songs to "Deep cuts from ${info.name.ifEmpty { name }}: songs of theirs you haven't played yet."
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
        return songs to "Some $tag, since you've been playing ${lead[0]} so much."
    }

    /** Radio of what's playing (or, when no service has one of it, of a song you play a lot). */
    private fun setLike(taste: Taste): Pair<List<Track>, String>? {
        val seeds = listOfNotNull(Music.current()) + taste.recent.take(5)
        for (seed in seeds.take(4)) for (key in services()) {
            val songs = pick(radio(seed, key).take(25).filter { songKey(it) != songKey(seed) }, SET, used)
            if (songs.size >= 3) return songs to "More like “${seed.title}” by ${names(listOf(seed))}."
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
            if (songs.size >= 3) return songs to "Some ${g.lowercase()} from your music, since you've been playing a lot of it lately."
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

    // ---- playing from songs you picked ----
    /** A set of kind from the songs the DJ plays from, or null. coming: the song keys still to come in the queue. */
    private fun fromSet(kind: String, taste: Taste, first: Boolean, coming: Set<Pair<String, String>>): Pair<List<Track>, String>? {
        val (name, pool) = scope ?: return null
        var fresh = pool.filter { songKey(it) !in used }
        if (fresh.size < minOf(3, pool.size)) {   // all of them played: around again, but not with songs still to come
            fresh = pool.filter { songKey(it) !in coming }
            if (fresh.size < minOf(3, pool.size)) fresh = pool
        }
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
                if (songs.isNotEmpty()) line = "From $name, the ones you keep coming back to, like “${songs[0].title}” by ${names(songs.take(1))}."
            }
            "unplayed" -> {
                songs = fresh.filter { (plays[songKey(it)] ?: 0) == 0 && lastPlayed(taste, it) == 0L }.shuffled().take(n)
                if (songs.isNotEmpty()) line = "Some from $name you haven't played yet, starting with “${songs[0].title}” by ${names(songs.take(1))}."
            }
            "throwback" -> {
                songs = fresh.filter { (plays[songKey(it)] ?: 0) > 0 && songKey(it) !in recent && t - lastPlayed(taste, it) > 21 * 86_400L }
                    .shuffled().take(n)
                if (songs.isNotEmpty()) line = "From $name, a few you haven't played in a while, like “${songs[0].title}”."
            }
            "hour" -> {
                val at = taste.hour.withIndex().associate { songKey(it.value) to it.index }
                songs = pick(fresh.filter { songKey(it) in at }.sortedBy { at[songKey(it)] }, n, emptySet())
                if (songs.isNotEmpty()) line = "From $name, what you usually play around now, like “${songs[0].title}”."
            }
            "artist" -> {
                val lead = { s: Track -> s.artist.split(",")[0].trim() }
                val count = fresh.groupingBy(lead).eachCount().filter { it.key.isNotEmpty() && it.value >= least }
                if (count.size > 1) {   // one artist in it all: that's not a set of its own
                    var r = Random.nextInt(count.values.sum())
                    val who = count.entries.first { r -= it.value; r < 0 }.key
                    songs = fresh.filter { lead(it) == who }.shuffled().take(n)
                    line = "A few from $who, from $name."
                }
            }
            else -> {
                songs = fresh.shuffled().take(n)
                if (songs.isNotEmpty()) line = "More from $name: ${names(songs, 3)}."
            }
        }
        if (songs.isEmpty() || songs.size < least) return null
        if (first) line = "${greeting()}, I'm your DJ, playing $name. " + line.replaceFirstChar { it.uppercase() }
        return songs to line
    }

    /** A set of the songs the DJ plays from that match what you asked (artist, title, album). */
    private fun askFrom(text: String): Built? {
        val (name, pool) = scope ?: return null
        val words = WORD.findAll(text).map { it.value }.filter { it.lowercase() !in FILLER }.map { norm(it) }.filter { it.isNotEmpty() }.toList()
        if (words.isEmpty()) return null
        val hits = pool.filter { t -> val all = norm("${t.title} ${t.artist} ${t.album} ${genreOf(t)}"); words.all { it in all } }
        val songs = pick(hits.filter { songKey(it) !in used }.shuffled() + hits.shuffled(), SET, emptySet())
        if (songs.isEmpty()) return null
        return Built("ask", songs, "From $name, you asked for ${text.trim()}: “${songs[0].title}” by ${names(songs.take(1))} first.")
    }

    /** (blocking) A set for what you asked: an artist, a genre, a decade or a vibe; null if nothing fits.
     *  Playing from songs you picked: the ones of them that match, else from everywhere. */
    fun ask(text: String): Built? {
        val sc = scope ?: return askAll(text)
        askFrom(text)?.let { return it }
        return askAll(text)?.let { it.copy(line = "There's nothing like that in ${sc.first}, so from everywhere: ${it.line}") }
    }

    private fun askAll(text: String): Built? {
        val taste = loadTaste()
        val asked = text.trim()
        val words = Regex("[\\p{L}\\p{N}_'&.-]+").findAll(asked).map { it.value }.filter { it.lowercase() !in FILLER }.joinToString(" ")
        val named = HashMap<String, String>()
        taste.artists.forEach { named[norm(it)] = it }
        taste.mine.values.forEach { val a = it.artist.split(",")[0].trim(); if (a.isNotEmpty()) named[norm(a)] = a }
        var who = if (words.isNotEmpty()) bestNamed(named.values.toList(), words) { it } else null
        val vibe = words.lowercase().split(" ").any { it in WORDS } || Regex("\\d0s").containsMatchIn(words)
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

    /** (blocking) The next set: of kind, else the next kind in the DJ's order that has songs
     *  (avoid, the kind playing now, only when no other has). coming: the song keys still to
     *  come in the queue. Null if there are none. */
    fun build(kind: String? = null, avoid: String? = null, first: Boolean = false, coming: Set<Pair<String, String>> = emptySet()): Built? {
        val taste = loadTaste()
        val order = if (scope != null) (if (withNew) FROM_NEW_ORDER else FROM_ORDER) else ORDER
        var spots = if (kind != null) listOf(-1) else order.indices.map { (step + it) % order.size }
        if (avoid != null && kind == null) spots = spots.filter { order[it] != avoid } + spots.filter { order[it] == avoid }.take(1)
        for (spot in spots) {
            val k = kind ?: order[spot]
            val sc = scope
            val got = (if (sc != null && k in setOf("fresh", "like")) {   // new songs, from the ones it plays from
                val t2 = Taste(taste.mine, sc.second.shuffled().take(20), sc.second, taste.artists, taste.last, skipped = taste.skipped)
                if (k == "fresh") setFresh(t2) else setLike(t2)
            } else if (sc != null) fromSet(k, taste, first, coming) else when (k) {
                "repeat" -> setRepeat(taste, first)
                "throwback" -> setThrowback(taste)
                "fresh" -> setFresh(taste)
                "deep" -> setDeep(taste)
                "genre" -> setGenre(taste)
                "like" -> setLike(taste)
                "hour" -> setHour(taste, first)
                "rediscover" -> setRediscover(taste)
                "favorites" -> setFavorites(taste)
                else -> null
            }) ?: continue
            if (kind == null) step = spot + 1
            return Built(k, got.first, got.second)
        }
        return null
    }
}
