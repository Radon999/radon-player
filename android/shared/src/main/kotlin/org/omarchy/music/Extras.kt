package org.omarchy.music

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.text.Normalizer

// The desktop's 1.2 additions, on the phone: favorites, skips, a song's own volume, telling
// copies of a song apart, and where downloads go. The same files and rules as the desktop's.

/** Letters and digits only, run together: "That’s All I Need" and "Thats all I need" are one. */
fun squashKey(s: String?): String =
    Normalizer.normalize(s ?: "", Normalizer.Form.NFKD).filter { it.code < 128 }.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

/** Each artist of t, squashed, without a leading "the". */
fun looseArtists(t: Track): Set<String> =
    t.artist.split(Regex("(?i),|&|\\bfeat\\.?|\\bft\\.?|\\bx\\b|\\band\\b")).map { squashKey(it).removePrefix("the") }
        .filter { it.isNotEmpty() }.toSet()

/** A song for finding duplicates: its title and lead artist, squashed (punctuation, accents,
 *  "the", feat. credits and (Remastered)-type notes don't count). */
fun looseKey(t: Track): Pair<String, String> =
    squashKey(cleanTitle(t.title)) to squashKey(t.artist.split(",")[0]).removePrefix("the")

/** Whether a and b are likely the same song, to ask about: the same or nearly the same title by an
 *  artist they share (or nearly the same name), or nearly the same title on the same album. Lengths
 *  and other tags aren't looked at: they differ between copies of one song. */
fun maybeSame(a: Track, b: Track): Boolean {
    val ta = squashKey(cleanTitle(a.title))
    val tb = squashKey(cleanTitle(b.title))
    if (ta.isEmpty() || tb.isEmpty()) return false
    val digits = Regex("\\d+")
    if (digits.findAll(ta).map { it.value }.toList() != digits.findAll(tb).map { it.value }.toList()) return false
    val title = if (ta == tb) 1.0 else similar(ta, tb)
    val inside = minOf(ta.length, tb.length) >= 4 && (ta in tb || tb in ta)
    if (title < 0.75 && !inside) return false
    val aa = looseArtists(a)
    val ab = looseArtists(b)
    val artist = aa.any { it in ab } || aa.any { x -> ab.any { y ->
        similar(x, y) >= 0.85 || (x.length >= 4 && y.length >= 4 && (x in y || y in x)) } }
    val album = squashKey(a.album).let { it.isNotEmpty() && it == squashKey(b.album) }
    return (artist && (title >= 0.85 || ta in tb || tb in ta)) || (album && title >= 0.75)
}

/** Whether Upgrade can swap t: a file of yours (not one of the app's downloads), on the phone or a writable one on the PC. */
fun upgradable(t: Track) = t.isFile && t.file?.let { f ->
    f.startsWith("content://media/") || (!f.startsWith("content:") && File(f).let { it.isFile && it.canWrite() })
} == true

private fun <V> readMap(file: File, ser: kotlinx.serialization.KSerializer<V>): MutableMap<String, V> =
    readText(file)?.let { runCatching { JSON.decodeFromString(MapSerializer(String.serializer(), ser), it) }.getOrNull() }
        ?.toMutableMap() ?: mutableMapOf()

private fun songId(t: Track) = songKey(t).let { "${it.first}|${it.second}" }

@Serializable
data class Fav(val track: Track, val added: Double = 0.0)

/** The songs you starred, kept by song (the same song from any service is one favorite): the
 *  Favorites and Not favorited lists. The desktop's favorites.json. */
class Favorites(private val file: File) {
    private val items = readMap(file, Fav.serializer())

    fun has(t: Track) = songId(t) in items
    val size get() = items.size

    @Synchronized
    fun set(tracks: List<Track>, on: Boolean) {
        for (t in tracks) if (on) items.putIfAbsent(songId(t), Fav(t.slim(), now())) else items.remove(songId(t))
        writeAtomic(file, JSON.encodeToString(MapSerializer(String.serializer(), Fav.serializer()), items))
    }

    /** Each favorite's song as f makes it (a backup's song files become this device's). */
    @Synchronized
    fun remap(f: (Track) -> Track) {
        items.replaceAll { _, v -> v.copy(track = f(v.track)) }
        writeAtomic(file, JSON.encodeToString(MapSerializer(String.serializer(), Fav.serializer()), items))
    }

    /** Your favorites, the last one starred first. */
    fun tracks(): List<Track> = items.values.sortedByDescending { it.added }.map { it.track }

    /** The ones starred between start and end (seconds). */
    fun addedBetween(start: Double, end: Double) = items.values.filter { it.added >= start && it.added < end }
        .sortedByDescending { it.added }.map { it.track }
}

@Serializable
data class SkipEntry(val skips: Int = 0, val full: Int = 0, val last_skip: Double = 0.0, val track: Track? = null,
                     val skips_at: List<Long> = emptyList(), val full_at: List<Long> = emptyList())

/** How you take each song: how often you skipped it (went on to another with more than SKIP_LEFT
 *  seconds of it left) and how often you heard it to the end. Kept by song. Last.fm keeps no
 *  skips, so they're counted from the day this came in. The desktop's skips file. */
class SongStats(private val file: File) {
    private val items = readMap(file, SkipEntry.serializer())

    private fun bump(t: Track, skip: Boolean) {
        val e = items[songId(t)] ?: SkipEntry()
        val at = now().toLong()
        items[songId(t)] = if (skip) e.copy(skips = e.skips + 1, last_skip = now(), track = t.slim(), skips_at = (e.skips_at + at).takeLast(300))
            else e.copy(full = e.full + 1, track = t.slim(), full_at = (e.full_at + at).takeLast(300))
        writeAtomic(file, JSON.encodeToString(MapSerializer(String.serializer(), SkipEntry.serializer()), items))
    }

    @Synchronized fun skipped(t: Track) = bump(t, true)
    @Synchronized fun heardOut(t: Track) = bump(t, false)

    fun get(t: Track) = items[songId(t)]?.let { it.skips to it.full } ?: (0 to 0)

    /** (track, skips, heard out) of every song you've skipped or heard out. */
    fun rows(since: Long = 0): List<Triple<Track, Int, Int>> = items.values.mapNotNull { e ->
        val t = e.track ?: return@mapNotNull null
        if (since <= 0) return@mapNotNull Triple(t, e.skips, e.full)
        val sk = e.skips_at.count { it >= since }
        val fu = e.full_at.count { it >= since }
        if (sk + fu > 0) Triple(t, sk, fu) else null   // only what you did since then (this week, month, year)
    }

    data class Habits(val skips: Int, val full: Int, val never: List<Pair<Track, Int>>, val neverCount: Int,
                      val mostSkipped: List<Pair<Track, Int>>, val favsAdded: List<Track>, val favsTotal: Int)

    /** For Wrapped: how you took songs between start and end (seconds). */
    fun habits(start: Double, end: Double, favorites: Favorites?): Habits {
        val rows = items.values.mapNotNull { e ->
            val t = e.track ?: return@mapNotNull null
            val sk = e.skips_at.count { it >= start && it < end }
            val fu = e.full_at.count { it >= start && it < end }
            if (sk + fu > 0) Triple(t, sk, fu) else null
        }
        val never = rows.filter { it.second == 0 && it.third >= 2 }.sortedByDescending { it.third }
        val most = rows.filter { it.second > 0 }.sortedWith(compareByDescending<Triple<Track, Int, Int>> { it.second }.thenBy { it.third })
        return Habits(rows.sumOf { it.second }, rows.sumOf { it.third }, never.take(5).map { it.first to it.third }, never.size,
            most.take(5).map { it.first to it.second }, favorites?.addedBetween(start, end) ?: emptyList(), favorites?.size ?: 0)
    }

    companion object {
        const val SKIP_LEFT = 15   // seconds left: more than this, and going on was a skip
        fun rate(skips: Int, full: Int) = if (skips + full > 0) skips.toDouble() / (skips + full) else 0.0
    }
}

/** The volume you gave songs (Volume…), in dB: quiet unreleased songs, mostly. Kept by song, either for every output
 *  (the desktop's song-volume.json, as before) or for one output (song-volume-outputs.json: headphones a song is quiet
 *  on, without it blasting in the car). A song's own one for the output it plays on comes first, else its every-output
 *  one. */
class SongGains(private val file: File, private val perFile: File? = null) {
    private val items = readMap(file, Double.serializer())
    private val per: MutableMap<String, MutableMap<String, Double>> =
        (perFile?.let { f -> readText(f)?.let { runCatching {
            JSON.decodeFromString(MapSerializer(String.serializer(), MapSerializer(String.serializer(), Double.serializer())), it)
        }.getOrNull() } } ?: emptyMap()).mapValues { it.value.toMutableMap() }.toMutableMap()

    /** The change for t on output (null: the every-output one only). */
    fun get(t: Track, output: String? = null): Double =
        output?.let { per[it]?.get(songId(t)) } ?: items[songId(t)] ?: 0.0

    /** Exactly what's saved for t at this scope (output null: every output), or null. */
    fun saved(t: Track, output: String?): Double? = if (output == null) items[songId(t)] else per[output]?.get(songId(t))

    /** Whether t has a change anywhere: for every output, or for any one. */
    fun has(t: Track): Boolean { val id = songId(t); return id in items || per.values.any { id in it } }

    /** The outputs t has its own change for, with it. */
    fun outputsOf(t: Track): Map<String, Double> { val id = songId(t); return per.mapNotNull { (o, m) -> m[id]?.let { o to it } }.toMap() }

    /** db for tracks: on output (null: every output); 0 takes the change away. */
    @Synchronized
    fun set(tracks: List<Track>, db: Double, output: String? = null) {
        val v = Math.round(db.coerceIn(LOW, HIGH) * 10) / 10.0
        val m = if (output == null) items else per.getOrPut(output) { mutableMapOf() }
        for (t in tracks) if (v == 0.0) m.remove(songId(t)) else m[songId(t)] = v
        if (output == null) writeAtomic(file, JSON.encodeToString(MapSerializer(String.serializer(), Double.serializer()), items))
        else perFile?.let { f ->
            per.entries.removeAll { it.value.isEmpty() }
            writeAtomic(f, JSON.encodeToString(MapSerializer(String.serializer(), MapSerializer(String.serializer(), Double.serializer())),
                per.mapValues { it.value.toMap() }))
        }
    }

    companion object {
        const val LOW = -12.0
        const val HIGH = 12.0
    }
}

/** name made safe as one folder or file name. */
fun pathPart(name: String?, fallback: String = "Unknown"): String =
    Regex("[<>:\"/\\\\|?*\\x00-\\x1f]").replace(name ?: "", "_").trim().trimEnd('.', ' ').ifEmpty { fallback }.take(120)

/** Where a download goes in a download folder of yours: (its folder, its file name) as
 *  Artists/<Artist>/<Album>/, Artists/<Artist>/<Title - EP>/ or Artists/<Artist>/<Title - Single>/, "NN Title.flac".
 *  The folder named "Artists" is your download folder itself when it's called that, else one made inside it.
 *  kind is the service's ("ALBUM", "EP", "SINGLE"); without one, the album's name says (an EP, a single, or an album). */
fun downloadPlace(root: String, artist: String, album: String, kind: String?, number: Long?, title: String): Pair<String, String> {
    val k = kind?.uppercase() ?: when {
        album.isBlank() -> "SINGLE"
        isEp(album) -> "EP"
        norm(cleanTitle(album)) == norm(cleanTitle(title)) || Regex("\\bsingle[)\\]]?$", RegexOption.IGNORE_CASE).containsMatchIn(album.trim()) -> "SINGLE"
        else -> "ALBUM"
    }
    val base = Regex("\\s*[-–]?\\s*[(\\[]?\\b(EP|Single)\\b[)\\]]?\\s*$", RegexOption.IGNORE_CASE).replace(album.ifBlank { title }.trim(), "").trim()
        .ifEmpty { album.ifBlank { title }.trim() }
    val release = when (k) { "EP" -> "$base - EP"; "SINGLE" -> "$base - Single"; else -> album.ifBlank { title } }
    val r = root.replace('\\', '/').trimEnd('/')
    val artists = if (r.substringAfterLast('/') == ARTISTS) r else "$r/$ARTISTS"
    val folder = artists + "/" + pathPart(artist, "Unknown artist") + "/" + pathPart(release, "Unknown album") + "/"
    val name = (if (number != null && number > 0) "%02d ".format(number) else "") + pathPart(title, "Untitled") + ".flac"
    return folder to name
}

/** The main music folder's name: inside it, one folder per artist, and in each one a folder per release. */
const val ARTISTS = "Artists"

/** A release's kind from where its song is: Artists/<Artist>/<Release>/… — a release folder ending in "EP" ("Spring EP",
 *  "South - EP") is an EP, one ending in "Single" ("Talk - Single") a single, any other an album. null outside an Artists folder. */
fun folderKind(folder: String?): String? {
    val parts = (folder ?: return null).replace('\\', '/').trim('/').split('/')
    val i = parts.lastIndexOf(ARTISTS)
    if (i < 0 || parts.size < i + 3) return null
    val release = parts[i + 2].trim()
    return when {
        Regex("(?:^|[\\s(\\[-])EP[)\\]]?$").containsMatchIn(release) -> "ep"
        Regex("(?:^|[\\s(\\[-])Single[)\\]]?$", RegexOption.IGNORE_CASE).containsMatchIn(release) -> "single"
        else -> "album"
    }
}

/** tracks as an .m3u8 playlist: a file path where the song is on the phone, else its page on its service. */
fun m3u8Text(tracks: List<Track>, fileOf: (Track) -> String?): String = buildString {
    append("#EXTM3U\n")
    for (t in tracks) {
        append("#EXTINF:${Math.round(t.duration)},${t.artist} - ${t.title}\n")
        append((fileOf(t) ?: songPage(t)) + "\n")
    }
}

/** The page of a song on its service, for a playlist file. */
fun songPage(t: Track): String = when (t.source) {
    "tidal" -> "https://tidal.com/browse/track/${t.tidal ?: t.id.removePrefix("td")}"
    "spotify" -> "https://open.spotify.com/track/${t.spotify ?: t.id}"
    "apple" -> "https://music.apple.com/song/${t.apple ?: t.id}"
    "deezer" -> "https://www.deezer.com/track/${t.id.removePrefix("dz")}"
    else -> "https://music.youtube.com/watch?v=${t.id}"
}


/** One of your queues, kept to come back to: its songs, where you were, and how far into that song. */
@kotlinx.serialization.Serializable
data class SavedQueue(val id: String, val name: String, val tracks: List<Track>, val index: Int, val position: Double = 0.0,
                      val time: Double = 0.0)

const val MAX_QUEUES = 12
