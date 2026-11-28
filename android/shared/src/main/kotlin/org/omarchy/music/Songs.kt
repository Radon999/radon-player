package org.omarchy.music

import java.security.SecureRandom
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.asKotlinRandom

// Helpers shared by every part of the app, ported from the desktop omarchy-music so
// both agree on what counts as the same song.

val SOURCES = linkedMapOf("ytm" to "YouTube Music", "tidal" to "Tidal", "apple" to "Apple Music", "spotify" to "Spotify", "deezer" to "Deezer",
    "soundcloud" to "SoundCloud")

/** The keys Settings (in the accounts sheet) asks for: none is needed, each makes something better. */
val API_KEYS = linkedMapOf(
    "google_key" to ("Google API key" to "Custom Search JSON API key from console.cloud.google.com: pictures and covers from Google"),
    "google_cx" to ("Google search engine ID" to "A Programmable Search Engine set to search the whole web"),
    "autoeq_api" to ("AutoEq server" to "The address of an AutoEqApi server of your own"),
    "acoustid" to ("AcoustID key" to "A free application key from acoustid.org/new-application: Identify by sound (a song's menu) works out what a song is from its sound"),
)

const val LASTFM_BLANK = "2a96cbd8b46e442fc41c2b86b821562f"   // Last.fm's grey star, for "no picture"

/** Where the search bar's dropdown can search. */
val SEARCH_IN = linkedMapOf("tidal" to "Tidal", "ytm" to "YouTube Music", "soundcloud" to "SoundCloud", "local" to "Your music", "genius" to "Genius")

/** How long (ms) after the last key to search as you type, or null not to: two letters at least;
 *  a moment for your own songs, a little longer for a service; a pasted link at once. */
fun liveSearchWait(q: String, where: String): Long? = when {
    linkSource(q.trim()) != null -> 50
    q.trim().length < 2 -> null
    where == "local" -> 120
    else -> 300
}

/** The service a pasted link is from, or null for words to search. */
fun linkSource(q: String): String? = when {
    "music.apple.com" in q -> "apple"
    "soundcloud.com/" in q -> "soundcloud"
    "tidal.com" in q -> "tidal"
    Regex("open\\.spotify\\.com/|^spotify:").containsMatchIn(q) -> "spotify"
    Regex("youtube\\.com/|youtu\\.be/").containsMatchIn(q) -> "ytm"
    else -> null
}

fun fmtTime(s: Double): String {
    val n = max(0, s.toInt())
    return "${n / 60}:${"%02d".format(n % 60)}"
}

/** How long a list of songs plays: "42 min", "2 h 5 min". */
fun fmtTotal(tracks: List<Track>): String {
    val m = (tracks.sumOf { it.duration } / 60).roundToInt()
    return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
}

/** How a playlist's songs can be listed: in the playlist's own order, or sorted. */
val PLAYLIST_SORTS = linkedMapOf("order" to "Order", "title" to "Title", "artist" to "Artist", "album" to "Album",
    "plays" to "Most played", "length" to "Length")

/** tracks sorted by sort (one of PLAYLIST_SORTS; "order" leaves them be). plays: song key -> plays. */
fun sortSongs(tracks: List<Track>, sort: String, plays: Map<Pair<String, String>, Int> = emptyMap()): List<Track> {
    val lead = { t: Track -> norm(t.artist.split(",")[0]).ifEmpty { "~" } }   // no artist: last
    val tie = compareBy<Track> { norm(it.title) }
    return when (sort) {
        "title" -> tracks.sortedWith(compareBy<Track>({ norm(it.title) }, { lead(it) }))
        "artist" -> tracks.sortedWith(compareBy<Track>({ lead(it) }, { norm(it.album).ifEmpty { "~" } }).then(tie))
        "album" -> tracks.sortedWith(compareBy<Track>({ norm(it.album).ifEmpty { "~" } }, { lead(it) }).then(tie))
        "plays" -> { val n = tracks.associateWith { plays[PlaysApart.playKey(it)] ?: 0 }; tracks.sortedByDescending { n[it] ?: 0 } }   // (each once, not at each comparison)
        "length" -> tracks.sortedByDescending { it.duration }
        else -> tracks
    }
}

/** tracks sorted by sort as [sortSongs] does, each song judged by what it is now (now gives a song as its tags stand
 *  today). A playlist keeps each song as it was when you put it in; a song since moved from a single into its album,
 *  or given a tidier artist name, was still sorted under the old one, apart from the album it now belongs to. The
 *  list's own songs come back, in the new order. */
fun sortSongsAsNow(tracks: List<Track>, sort: String, plays: Map<Pair<String, String>, Int> = emptyMap(), now: (Track) -> Track): List<Track> {
    if (sort !in PLAYLIST_SORTS || sort == "order") return tracks
    // (each of today's copies is marked with its song's place in the list, to find the list's own song again)
    val today = tracks.mapIndexed { i, t -> now(t).copy(id = i.toString()) }
    return sortSongs(today, sort, plays).map { tracks[it.id.toInt()] }
}

/** Shuffle: every order equally likely (Fisher-Yates), from the system's secure random source,
 *  so no order of even a long playlist is out of reach the way it can be with a seeded one. */
val SHUFFLE_RANDOM: kotlin.random.Random = SecureRandom().asKotlinRandom()

/** Smart shuffle's weight for a song played plays times, last at last (seconds; 0: not here): loved songs weigh
 *  more, the more so the longer it's been (up to a month); one heard in the last day weighs little. */
fun smartWeight(plays: Int, last: Double, now: Double): Double {
    val love = 1.0 + kotlin.math.ln(1.0 + plays)
    val days = if (last > 0) (now - last) / 86400 else 60.0
    val fresh = if (days < 1) 0.25 else minOf(1.5, 0.5 + days / 30)
    return love * fresh
}

fun <T> bestNamed(items: List<T>, name: String, get: (T) -> String): T? {
    val best = items.maxByOrNull { similar(norm(get(it)), norm(name)) } ?: return null
    return if (similar(norm(get(best)), norm(name)) >= 0.8) best else null
}

/** The same song on any service: its name and lead artist, tidied. */
fun songKey(t: Track) = norm(cleanTitle(t.title)) to norm(t.artist.split(",")[0])

/** A speed as the app shows it: "1.25×", "1×". */
fun fmtSpeed(s: Double) = (if (s == Math.floor(s)) s.toLong().toString() else s.toString().trimEnd('0')) + "×"

fun safeName(id: String) = Regex("[^\\w.-]").replace(id, "_")

fun randomHex(bytes: Int): String {
    val b = ByteArray(bytes)
    SecureRandom().nextBytes(b)
    return b.joinToString("") { "%02x".format(it) }
}

/** YouTube Music art URLs end in =w120-h120-...; Apple's in /226x226bb.jpg: ask for the size we draw. */
fun sizedThumb(url: String, px: Int): String = when {
    "googleusercontent" in url -> Regex("=w\\d+-h\\d+").replace(url, "=w$px-h$px")
    "mzstatic.com" in url -> Regex("/\\d+x\\d+(bb|cc)?\\.(jpg|webp|png)$").replace(url, "/${px}x${px}bb.jpg")
    else -> url
}

fun now() = System.currentTimeMillis() / 1000.0

/** A song in an .m3u or .m3u8 playlist: where it says the file is, as written, and the names
 *  and length from its #EXTINF line (empty and 0 where it has none). */
data class M3uEntry(val loc: String, val artist: String, val title: String, val seconds: Int)

fun readM3u(raw: ByteArray): List<M3uEntry> {
    val text = runCatching { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(raw)).toString() }
        .getOrElse { String(raw, Charsets.ISO_8859_1) }.removePrefix("\uFEFF")   // an old .m3u
    val out = ArrayList<M3uEntry>()
    var info: Triple<String, String, Int>? = null
    for (line0 in text.lines()) {
        val line = line0.trim()
        if (line.startsWith("#EXTINF:")) {
            val head = line.substring(8).substringBefore(',')
            val name = line.substring(8).substringAfter(',', "").trim()
            val secs = Regex("^-?\\d+(\\.\\d+)?").find(head.trim())?.value?.toDouble()?.toInt()?.coerceAtLeast(0) ?: 0
            info = if (" - " in name) Triple(name.substringBefore(" - ").trim(), name.substringAfter(" - ").trim(), secs) else Triple("", name, secs)
        } else if (line.isNotEmpty() && !line.startsWith("#")) {
            out += M3uEntry(line, info?.first ?: "", info?.second ?: "", info?.third ?: 0)
            info = null
        }
    }
    return out
}

/** A song an imported playlist's import didn't find: its line in the file (n), where among the
 *  songs that were found it goes (at), and why it wasn't found. */
data class Missed(val n: Int, val at: Int, val artist: String, val title: String, val seconds: Int, val loc: String, val why: String) {
    val name get() = if (artist.isNotEmpty()) "$artist - $title" else title
}

/** Where m goes back in its playlist: its place, after the ones of placed (put back already) that were before it. */
fun missedAt(m: Missed, placed: List<Missed>) = m.at + placed.count { it.n < m.n }

/** (artist, title) from a song file's name: "Artist - Title.flac", "01 Title.flac". */
fun namesFromFile(path: String): Pair<String, String> {
    val stem = Regex("^\\d{1,3}[\\s.\\-_]+").replace(path.substringAfterLast('/').substringBeforeLast('.'), "")
    return if (" - " in stem) stem.substringBefore(" - ") to stem.substringAfter(" - ") else "" to stem
}

/** The search hit that is the song called title by artist, about seconds long (0: unknown), or null. */
fun bestMatch(hits: List<Track>, artist: String, title: String, seconds: Int): Track? {
    var best: Track? = null
    var bestScore = 0.0
    for (h in hits) {
        if (seconds > 0 && h.duration > 0 && Math.abs(h.duration - seconds) > 8) continue
        var score = similar(cleanTitle(title), cleanTitle(h.title))
        if (artist.isNotEmpty()) {
            val lead = artistNames(listOf(artist)).maxOfOrNull { x -> artistNames(h.artist.split(",")).maxOfOrNull { y -> similar(x, y) } ?: 0.0 } ?: 0.0
            if (lead < 0.6) continue
            score = 0.6 * score + 0.4 * lead
        }
        if (score >= 0.7 && score > bestScore) { best = h; bestScore = score }
    }
    return best
}

/** Your songs, artists and albums whose names have every word of q in them. */
fun localMatches(songs: List<Track>, q: String): Found {
    val words = searchWords(q)   // commas, apostrophes and other symbols don't matter
    if (words.isEmpty()) return Found(emptyList())
    fun has(text: String) = searchHit(words, text)
    val hits = songs.filter { has("${it.title} ${it.artist} ${it.album}") }
    val artists = sortedMapOf<String, String>()
    val albums = LinkedHashMap<String, Pair<Album, MutableList<Track>>>()
    for (t in songs) {
        for (name in splitArtists(t.artist)) if (has(name)) artists.putIfAbsent(norm(name), name)
        if (t.album.isNotEmpty() && has("${t.album} ${t.artist}")) {
            val lead = t.artist.split(",")[0].trim()
            val id = "local-" + norm("${t.album} $lead")
            albums.getOrPut(id) { Album(id, t.album, lead) to ArrayList() }.second += t
        }
    }
    val shown = albums.values.take(12).map { (al, tr) ->
        al.copy(sub = listOf(al.sub, "${tr.size} song${if (tr.size != 1) "s" else ""}").filter { it.isNotEmpty() }.joinToString(" · ")) to tr
    }
    return Found(hits, artists.values.take(8).map { Album("ar-" + norm(it), it, "Artist") }, shown.map { it.first },
        shown.associate { it.first.id to it.second })
}


// An album's edition at the end of its name: "(Deluxe)", "[Deluxe Edition]", "(Extended)", "(10th Anniversary
// Edition)", " - Deluxe". Only deluxe, extended and anniversary ones: "(Sessions)", "(Bonus Track Version)",
// "(Expanded)"… are albums of their own. The desktop's album_edition.
/** A credit's main artist as a key ("Quinn XCII & Jeremy Zucker" = "Quinn XCII"): as statsKey keys a song's artist. */
fun artistKey(credit: String) = statsKey("", credit).second

private val DUO = Regex(" & | x |,")

fun soloKey(n: String) = norm(n).replace(NOT_LETTERS, "")

/** Of the names one artist goes by ("Quinn XCII", "Quinn XCII & Jeremy Zucker"), the one they have on their own; with only
 *  "A & B", A when A is an artist of their own (solo), else the duo ("Simon & Garfunkel"). The desktop's artist_named. */
fun soloName(names: Map<String, Int>, solo: Set<String> = emptySet()): String {
    val best = names.entries.minWithOrNull(compareBy<Map.Entry<String, Int>>({ DUO.containsMatchIn(it.key) }, { -it.value }))?.key ?: return ""
    if (!DUO.containsMatchIn(best)) return best
    val head = best.split(Regex(",| & | x ")).first().trim()
    return if (head.isNotEmpty() && soloKey(head) in solo) head else best
}


/** The artists in a credit, for lists of artists: a credit of one ("Tyler, The Creator") whole; "A & B feat. C" as
 *  A, B and C. The desktop's own_artists. */
fun ownArtists(credit: String?): List<String> {
    val c = credit?.trim().orEmpty()
    if (c.isEmpty()) return emptyList()
    // "and" is never split: it's in band names ("Of Monsters and Men", "Fitz and The Tantrums", "Esquivel and His
    // Orchestra"), and two artists are credited with "&", "x" or a comma
    val many = Regex(" & | x |;|\\s(?:feat\\.?|ft\\.?|featuring|(?-i:with))\\s", RegexOption.IGNORE_CASE)
    if (!many.containsMatchIn(c)) return listOf(c)
    val out = ArrayList<String>()
    for (part in c.split(Regex(",|;|\\s+(?:feat\\.?|ft\\.?|featuring|(?-i:with))\\s+", RegexOption.IGNORE_CASE))) {
        val p = part.trim()
        if (p.isEmpty()) continue
        val pieces = p.split(Regex(" & | x (?![&+])"))   // ("Lil Nas X & Jack Harlow": the X is the name's)
        // A band named "A & B" ("Mumford & Sons", "Florence + the Machine"-like "& The …") stays whole
        if (pieces.size == 2 && bandTail(pieces[1])) out += p else out += pieces.map { it.trim() }.filter { it.isNotEmpty() }
    }
    return out.distinct()
}

/** The end of a band's name after "&" ("Sons", "The Machine", "His Orchestra"), not a second artist. */
fun bandTail(s: String): Boolean {
    val t = s.trim().lowercase()
    return t.startsWith("the ") || t.startsWith("his ") || t.startsWith("her ") || t.startsWith("their ") ||
        t in setOf("sons", "men", "friends", "co", "co.", "company", "daughters", "brothers", "sisters", "family", "crew", "band",
            "orchestra", "the gang", "gang", "boys", "girls")
}

// ---- Whose plays a joint credit's are (the desktop's artist_resolver) ----
private val FEAT_SPLIT = Regex("\\s+(?:feat\\.?|ft\\.?|featuring|(?-i:with))\\s+", RegexOption.IGNORE_CASE)
private val PAIR_SEP = Regex(" & | x |,|;")
private fun plainKey(n: String) = norm(n).replace(NOT_LETTERS, "")

/** The artists of a credit joined by "&", "x" or commas; a band's own name stays whole ("Of Monsters and Men",
 *  "Lil Nas X", "Tyler, The Creator"). */
fun duoParts(credit: String?): List<String> {
    val c = credit?.trim().orEmpty()
    if (c.isEmpty()) return emptyList()
    if (!PAIR_SEP.containsMatchIn(c) || (c.count { it == ',' } == 1 && !Regex(" & | x |;").containsMatchIn(c))) return listOf(c)
    return c.split(PAIR_SEP).map { it.trim() }.filter { it.isNotEmpty() }
}

/** A pair's key, whichever way round they're billed. */
fun pairKey(credit: String): String =
    duoParts(credit.split(FEAT_SPLIT).first().trim()).map(::plainKey).sorted().joinToString("&")

/** The key an artist's plays are added up under: a pair kept as a pair is its own. */
fun resolvedKey(name: String): String = if (duoParts(name).size > 1) plainKey(name) else statsKey(name, name).second

/** (spellings of the artists credited alone in your songs {key: the most common spelling}, how many credits each is in). */
private fun soloInfo(songs: List<Track>): Pair<Map<String, String>, Map<String, Int>> {
    val spelt = HashMap<String, HashMap<String, Int>>()
    val credits = HashMap<String, HashSet<String>>()
    for (t in songs) {
        val c = t.artist.trim()
        if (c.isEmpty() || c.equals("unknown artist", true)) continue
        val lead = c.split(FEAT_SPLIT).first().trim()
        val parts = duoParts(lead)
        if (parts.size == 1 && !FEAT_SPLIT.containsMatchIn(c)) spelt.getOrPut(plainKey(lead)) { HashMap() }.merge(lead, 1, Int::plus)
        for (p in parts + c.split(FEAT_SPLIT).drop(1)) credits.getOrPut(artistKey(p)) { HashSet() } += norm(c)
    }
    return spelt.mapValues { (_, m) -> m.maxByOrNull { it.value }!!.key } to credits.mapValues { it.value.size }
}

/** A credit's main artist and whether that's sure (the desktop's pick_main). */
fun pickMain(credit: String, alone: Map<String, String>, counts: Map<String, Int>): Pair<String, Boolean> {
    val lead = credit.split(FEAT_SPLIT).first().trim()
    val parts = duoParts(lead)
    fun named(n: String) = alone[plainKey(n)] ?: n
    if (parts.size <= 1) return (parts.firstOrNull()?.let(::named) ?: lead) to true
    val solo = parts.filter { plainKey(it) in alone }
    if (solo.size == 1) return named(solo[0]) to true
    if (solo.size > 1) return named(solo[0]) to false
    val ranked = parts.sortedByDescending { counts[artistKey(it)] ?: 0 }
    val a = counts[artistKey(ranked[0])] ?: 0; val b = counts[artistKey(ranked[1])] ?: 0
    return named(ranked[0]) to (a > maxOf(1, b))
}

/** credit -> the artist its plays count for (the desktop's artist_resolver): "Quinn XCII & Jeremy Zucker" is Quinn XCII,
 *  "Big Wild & Surfaces" is Surfaces, "A feat. B" is A, your Joint artists… picks stand, an unclear pair stays a pair. */
fun artistResolver(songs: List<Track>, picks: Map<String, String>): (String) -> String {
    val (alone, counts) = soloInfo(songs)
    val cache = HashMap<String, String>()
    return { credit ->
        val c = credit.trim()
        cache.getOrPut(c) {
            val lead0 = c.split(FEAT_SPLIT).first().split(";").first().trim()
            val pk = pairKey(c)
            when {
                pk in picks -> picks[pk]!!.ifEmpty { lead0 }
                else -> {
                    var lead = lead0
                    if (',' in lead && duoParts(lead).size == 1 && plainKey(lead) !in alone) lead = lead.substringBefore(',').trim()
                    if (duoParts(lead).size <= 1) alone[plainKey(lead)] ?: lead
                    else pickMain(lead, alone, counts).let { (m, sure) -> if (sure) m else lead }
                }
            }
        }
    }
}

/** The pairs Most played can't tell the main artist of: [(credit, names, picked or null)], the most played first. */
fun jointCredits(credits: Map<String, Int>, songs: List<Track>, picks: Map<String, String>): List<Triple<String, List<String>, String?>> {
    val (alone, counts) = soloInfo(songs)
    val seen = HashSet<String>()
    val out = ArrayList<Triple<String, List<String>, String?>>()
    for ((credit, _) in credits.entries.sortedByDescending { it.value }) {
        val lead = credit.split(FEAT_SPLIT).first().trim()
        val parts = duoParts(lead)
        val pk = pairKey(lead)
        if (parts.size <= 1 || !seen.add(pk)) continue
        if (!pickMain(lead, alone, counts).second || pk in picks) out += Triple(lead, parts, picks[pk])
    }
    return out
}

/** Words too common in band names to say two are one (the desktop's NAME_WORDS). */
private val NAME_WORDS = ("a an and the of in on at to for from with by or x vs feat ft featuring my your our his her their its " +
    "i me we you he she it they this that these those is are was be been am not no yes all one two three four five six seven " +
    "eight nine ten band boys girls boy girl men women man woman brothers sisters kids club crew gang squad family orchestra " +
    "quartet trio duo ensemble group project collective society experience company sound sounds music records beats dj mc " +
    "lil big little young old new good bad best real true dead live love life time day night world city street house home " +
    "heart soul fire blue black white red green gold golden silver dark light sun moon star stars sky sea ocean river rain " +
    "summer winter spring fall wild sweet hot cold king queen kings queens lord lady prince princess saint st mr mrs miss dr " +
    "el la le los las les de del da di du van von official").split(" ").toSet()

/** Similar artists…: artists that look like another of yours ("cavetown offline" and Cavetown): [(name, theirs, plays,
 *  same songs' titles)], those with songs of the same name first. The desktop's similar_artists. */
fun similarArtists(library: Set<String>, played: Map<String, Int>, titles: Map<String, Set<String>>, picks: Map<String, String>,
                   kept: Set<String>, counts: Map<String, Int>): List<Pair<Pair<String, String>, Pair<Int, List<String>>>> {
    fun key(n: String) = plainKey(n)
    fun words(n: String) = Regex("\\w+").findAll(norm(n)).map { it.value }.toList()
    fun distinct(w: String) = w.length >= 4 && w !in NAME_WORDS && !w.all { it.isDigit() }
    fun close(a: String, b: String) = kotlin.math.abs(a.length - b.length) <= 2 && minOf(a.length, b.length) >= 5 && similar(a, b) >= 0.85
    fun inside(a: List<String>, b: List<String>) = b.size < a.size && (0..a.size - b.size).any { a.subList(it, it + b.size) == b }
    val names = LinkedHashMap<String, String>()
    for (n in library + played.keys) if (n.isNotBlank() && duoParts(n).size == 1 && pairKey(n) !in picks && key(n).length >= 3) names.putIfAbsent(key(n), n)
    val mine = library.map(::key).toSet()
    val byWord = HashMap<String, HashSet<String>>()
    for ((k, n) in names) for (w in words(n).toSet()) if (distinct(w)) byWord.getOrPut(w) { HashSet() } += k
    val byStart = names.keys.groupBy { it.take(2) }
    val seen = HashSet<String>()
    val out = ArrayList<Pair<Pair<String, String>, Pair<Int, List<String>>>>()
    for (ka in names.keys) {
        val cands = HashSet<String>()
        for (w in words(names[ka]!!).toSet()) byWord[w]?.let { cands += it }
        for (kb in byStart[ka.take(2)].orEmpty()) if (kb != ka && close(ka, kb)) cands += kb
        for (kb in cands) {
            if (kb == ka || !seen.add(listOf(ka, kb).sorted().joinToString("|"))) continue
            if (ka !in mine && kb !in mine) continue
            val a = names[ka]!!; val b = names[kb]!!
            val wa = words(a); val wb = words(b)
            val same = (titles[ka].orEmpty() intersect titles[kb].orEmpty())
            val shortWords = if (wa.size <= wb.size) wa else wb
            val isInside = inside(wa, wb) || inside(wb, wa)
            val shared = wa.toSet().intersect(wb.toSet()).any(::distinct)
            if (!((isInside && (shortWords.size >= 2 || minOf(ka.length, kb.length) >= 6 || same.isNotEmpty())) || close(ka, kb) ||
                    (shared && same.isNotEmpty()))) continue
            val aFirst = (counts[ka] ?: 0) < (counts[kb] ?: 0) || ((counts[ka] ?: 0) == (counts[kb] ?: 0) && (played[a] ?: 0) < (played[b] ?: 0))
            val (src, dst) = if (aFirst) a to b else b to a
            if (key(dst) !in mine || "${key(src)}>${key(dst)}" in kept) continue
            out += (src to dst) to ((played[src] ?: 0) to same.sorted())
        }
    }
    return out.sortedWith(compareBy({ it.second.second.isEmpty() }, { -it.second.first }, { norm(it.first.first) }))
}


/** A song on one of its releases, as Music.albumPicks keeps which album its plays count for. */
fun albumPlayKey(title: String, artist: String, album: String): String =
    statsKey(title, artist).let { "${it.first}|${it.second}|${norm(cleanTitle(album))}" }


val SONG_VERSION = Regex("\\b(acoustic|remix|rmx|live|demo|stripped|orchestra|session|sessions|alternate|commentary|instrumental|" +
    "sped|slowed|mix|edit|rework|piano|reprise|cover|karaoke|cappella|unplugged|extended|voice memo|home recording|recorded at|version|vip|bootleg|flip)\\b",
    RegexOption.IGNORE_CASE)

// The patterns the song keys below are made with, built once: these run for every play of every history (tens of
// thousands at a time, on the phone too), and making each pattern again on every call was time and battery for nothing.
private val BRACKETS = Regex("[(\\[][^)\\]]*[)\\]]")
private val DASH_REST = Regex("\\s[-–—]\\s.*$")
private val FEAT_REST = Regex("\\s(?:feat|ft|featuring)\\b.*$")
private val NOT_LETTERS = Regex("[\\W_]+")
// A track number (or a disc and track: "1-11", "01.11", "1/11"), maybe in brackets, then a sign and a space, a space,
// or straight on after "_", "." or ")": "11 Ruby", "11. Ruby", "11 - Ruby", "(11) Ruby", "11_Ruby", "1-11 Ruby"
private val LEADING_NUMBER = Regex("^\\s*[(\\[]?\\d{1,3}(?:\\s?[-./]\\s?\\d{1,3})?[)\\]]?(?:\\s*[-–—._:)|]\\s+|[._)\\]]|\\s+)(\\S.*)$")
/** Where an artist credit splits into its artists (statsKey's lead is the first); any case, as names are written. */
internal val CREDIT_SPLIT = Regex(",|;| & | x | feat\\.? | ft\\.? | featuring | with ", RegexOption.IGNORE_CASE)

/** A song's name with everything after it left out and no punctuation ("N.P.D" = "NPD"). The desktop's loose_title. */
fun looseTitle(title: String): String {
    var t = Censored.plain(Normalizer.normalize(title.lowercase(), Normalizer.Form.NFKC))
    t = BRACKETS.replace(t, " ")
    t = DASH_REST.replace(t, " ")
    t = FEAT_REST.replace(t, " ")
    return NOT_LETTERS.replace(t, "")
}

/** A title without the track number a file's name starts with ("11 Ruby", "03 - Intro", "13. unconditional", "11_Ruby",
 *  "(11) Ruby", "1-11 Ruby"): what a file with no tags is called by its name alone. The title as it is when nothing but
 *  numbers would be left ("1-800-273-8255", "12-51", "1999"), or when no number leads it. ("24 Hours" loses its 24 too,
 *  so this is only ever tried beside the title as it is.) */
fun withoutTrackNumber(title: String): String {
    val m = LEADING_NUMBER.find(title) ?: return title
    return m.groupValues[1].trim().takeIf { r -> r.any { it.isLetter() } } ?: title
}

/** No artist in its tags: nothing, or what a device puts in its place. */
fun noArtistNamed(artist: String?): Boolean = (artist ?: "").replace('_', ' ').trim().lowercase().let { it.isEmpty() || it == "unknown" || it == "unknown artist" || it == "<unknown>" }

/** A song's [statsKey] under each artist it is credited to, the lead first: "OTT" by "hard life & BENEE" is OTT by
 *  hard life and OTT by BENEE. A history names a song by everyone on it; your own file of it may be tagged with just one
 *  of them, and not the first, so it is looked for under each. */
fun creditKeys(title: String, artist: String): List<Pair<String, String>> {
    val t = norm(cleanTitle(title))
    return artist.lowercase().split(CREDIT_SPLIT)
        .map { p -> norm(p).let { n -> n.replace(NOT_LETTERS, "").ifEmpty { n } } }
        .filter { it.isNotEmpty() }.distinct().map { t to it }
}

fun songPickKey(title: String, artist: String): String = statsKey(title, artist).let { "${it.first}|${it.second}" }

/** A song across Last.fm, Spotify, Apple Music and here: its name and lead artist, tidied
 *  (feat., & and x credits, remaster notes left out). The desktop's stats_key. */
fun statsKey(title: String, artist: String): Pair<String, String> = STATS_KEYS.of(title to artist) {
    norm(cleanTitle(title)) to norm(artist.lowercase().split(CREDIT_SPLIT)[0])
        .let { n -> n.replace(NOT_LETTERS, "").ifEmpty { n } }   // K.Flay = kflay
}
private val STATS_KEYS = Memo<Pair<String, String>, Pair<String, String>>(200_000)

/** A service's songs for a search, the best fits first: the song named just what you typed, then ones whose name and
 *  artist hold all your words, then the rest, each in the service's own order. */
fun rankSongs(songs: List<Track>, q: String): List<Track> {
    val words = searchWords(q)
    if (words.isEmpty() || songs.size < 2) return songs
    val whole = searchNorm(q)
    fun score(t: Track): Int {
        val title = searchNorm(cleanTitle(t.title))
        return when {
            title == whole || searchNorm("${cleanTitle(t.title)} ${t.artist.split(",")[0]}") == whole || searchNorm("${t.artist.split(",")[0]} ${cleanTitle(t.title)}") == whole -> 3
            searchHit(words, "${t.title} ${t.artist}") -> 2
            words.any { it in title } -> 1
            else -> 0
        }
    }
    return songs.withIndex().sortedWith(compareByDescending<IndexedValue<Track>> { score(it.value) }.thenBy { it.index }).map { it.value }
}
