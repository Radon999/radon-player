package org.omarchy.music

// Fix albums and artists (the desktop's fixup_plan, fixup_other, fixup_album_row, fixup_with_others, fixup_moves):
// albums split by featured artists ("Quinn XCII & Jeremy Zucker"), an album beside its deluxe edition, an album in
// parts, and singles and remixes of songs on an album you have, fixed in the files' own tags (artist, album artist,
// album, track number; titles never), then the files moved into the right folders. Only the deciding is here: reading
// tags, looking things up (Deezer), comparing covers, writing tags and moving files are the caller's.

/** A song as Fix albums and artists reads it from its file's tags (the desktop's track_from_file): file is its path;
 *  title is the file's name when it has none; track is its track number tag as written ("3", "3/12", ""). */
data class FixupSong(
    val file: String,
    val title: String = "",
    val artist: String = "",
    val albumartist: String = "",
    val album: String = "",
    val track: String = "",
    val date: String = "",
    val duration: Int = 0,
)

/** The four tags Fix albums and artists changes, as a song has them. */
data class FixupTags(val artist: String = "", val albumartist: String = "", val album: String = "", val tracknumber: String = "") {
    /** The tag called k ("artist", "albumartist", "album", "tracknumber"). */
    operator fun get(k: String): String = when (k) {
        "artist" -> artist
        "albumartist" -> albumartist
        "album" -> album
        "tracknumber" -> tracknumber
        else -> ""
    }

    /** These tags with new's over them. */
    fun with(new: Map<String, String>) = FixupTags(new["artist"] ?: artist, new["albumartist"] ?: albumartist,
        new["album"] ?: album, new["tracknumber"] ?: tracknumber)
}

/** Another song of a changed song's album that needed nothing itself (for Other artist: it goes with the album). */
data class FixupSibling(val file: String, val title: String, val old: FixupTags)

/** One song's change (fixup_plan's): new holds only the tags that change ("artist", "albumartist", "album",
 *  "tracknumber"). kinds: "artist", "parts", "edition", "single", "remix", "cover". cover: a file to take the cover from
 *  (a single that has none). unsure: a duo neither of whom is an artist of their own here (Leave alone unless you say).
 *  others, group, siblings: for a featured-artist fix, the album's other artists (Other artist), which album it is,
 *  and its songs that needed nothing. */
data class FixupChange(
    val file: String,
    val title: String,
    val kinds: List<String>,
    val why: String,
    val old: FixupTags,
    val new: Map<String, String>,
    val cover: String? = null,
    val unsure: Boolean = false,
    val others: List<String>? = null,
    val group: Int? = null,
    val siblings: List<FixupSibling>? = null,
    val unfound: Boolean = false,
)

/** What you picked for a change: Change (as planned), Leave (left alone) or Other (an artist's name, or an album's
 *  for a change of an album only; own: a name you typed, the desktop's OwnName). */
sealed class FixupPick {
    object Change : FixupPick()
    object Leave : FixupPick()
    data class Other(val name: String, val own: Boolean = false) : FixupPick()
}

/** The names Fix albums and artists never takes for an artist: a compilation's. */
val VARIOUS = setOf("various artists", "various", "va", "verschiedene interpreten")

/** Of a cover's picture hash's 64 bits: this many or fewer different, the same cover. */
const val COVER_ALIKE = 10

private val FIXUP_FEAT = Regex("\\s+(?:feat\\.?|ft\\.?|featuring|with)\\s+", RegexOption.IGNORE_CASE)
private val FIXUP_JOINED = Regex(" & | x | X | and ")
private val FIXUP_NONWORD = Regex("[\\W_]+")
private val FIXUP_SINGLE_WORD = Regex("\\bsingles?\\b", RegexOption.IGNORE_CASE)
private val FIXUP_TRACK = Regex("^\\s*(\\d+)")

/** A song's number on its album ("3/12" is 3), for sorting; songs without one go last. */
fun trackNo(track: String?): Int {
    val m = FIXUP_TRACK.find(track ?: "") ?: return 9999
    return m.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
}

private val REMIX_PART = Regex("\\s*[(\\[][^)\\]]*\\b(?:re-?mix|rework|flip|bootleg)\\b[^)\\]]*[)\\]]|\\s[-–]\\s[^-–]*\\b(?:re-?mix|rework|flip|bootleg)\\b.*$",
    RegexOption.IGNORE_CASE)

/** The song a remix is of ("Sorry I'm Late (Roosevelt Remix)" -> "Sorry I'm Late"), or "" if it isn't one. */
fun remixOf(title: String?): String {
    val t = title ?: ""
    if (!REMIX_PART.containsMatchIn(t)) return ""
    return REMIX_PART.replace(t, "").trim()
}

private val FIXUP_PART_END = Regex("\\s*(?:[:,(\\[]|\\s[-–])?\\s*\\b(?:pt\\.?|part)\\s*(\\d+|[ivx]+|one|two|three|four)\\s*[)\\]]?\\s*$",
    RegexOption.IGNORE_CASE)
private val PART_WORDS = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "i" to 1, "ii" to 2, "iii" to 3, "iv" to 4, "v" to 5)

/** (the album's name without its part, the part's number) — ("The Original Motion Picture Soundtrack", 2) for "…: Pt. 2"
 *  — or (album, 0). */
fun albumPart(album: String?): Pair<String, Int> {
    val a = (album ?: "").trim()
    val m = FIXUP_PART_END.find(a)
    if (m == null || a.substring(0, m.range.first).trim().isEmpty()) return a to 0
    val p = m.groupValues[1].lowercase()
    val n = if (p.all { it.isDigit() }) (p.toIntOrNull() ?: 0) else (PART_WORDS[p] ?: 0).let { if (it == 0) 1 else it }
    return a.substring(0, m.range.first).trim(' ', ':', '-', '–', ',') to n
}

/** Each artist in a credit: "A & B feat. C" -> [A, B, C]. */
fun creditNames(credit: String?): List<String> =
    (credit ?: "").split(Regex(",|;|\\s+(?:feat\\.?|ft\\.?|featuring|with)\\s+| & | x | X | and ", RegexOption.IGNORE_CASE))
        .map { it.trim() }.filter { it.isNotEmpty() }

/** The artists who are credited on their own somewhere (keyed as soloKey keys them): "Quinn XCII" is, so "Quinn XCII &
 *  Jeremy Zucker" is him with a guest; "Simon" never is, so "Simon & Garfunkel" stays a duo. The desktop's solo_artists. */
fun fixupSoloArtists(songs: List<FixupSong>): Set<String> {
    val out = LinkedHashSet<String>()
    for (t in songs) {
        for (credit in listOf(t.albumartist, t.artist)) {
            val parts = ArrayList<String>()
            for (piece in credit.split(Regex(";|\\s+(?:feat\\.?|ft\\.?|featuring|with)\\s+", RegexOption.IGNORE_CASE))) {
                val oneComma = piece.count { it == ',' } == 1 && !FIXUP_JOINED.containsMatchIn(piece)
                if (oneComma) parts += piece else parts += piece.split(",")   // "Tyler, The Creator" whole
            }
            for (part0 in parts) {
                val part = part0.trim()
                if (part.isNotEmpty() && !FIXUP_JOINED.containsMatchIn(part)) out += FIXUP_NONWORD.replace(norm(part), "")
            }
        }
    }
    out.remove("")
    return out
}

/** How many different credits each artist (artistKey) is in across your songs: Berner, in "Berner & Cozmo" and "Berner &
 *  Styles P", is the one those two have in common. */
fun creditCounts(songs: List<FixupSong>): Map<String, Int> {
    val seen = LinkedHashMap<String, MutableSet<String>>()
    for (t in songs) {
        for (credit in linkedSetOf(t.albumartist, t.artist)) {
            if (credit.isEmpty()) continue
            for (n in creditNames(credit)) seen.getOrPut(artistKey(n)) { LinkedHashSet() } += norm(credit)
        }
    }
    return seen.mapValues { it.value.size }
}

/** {artist key: how they're spelt on the songs credited to them alone, the most common}: K.Flay and KFlay are one. */
fun soloSpellings(songs: List<FixupSong>): Map<String, String> {
    val seen = LinkedHashMap<String, LinkedHashMap<String, Int>>()
    val featured = Regex("\\s(?:feat\\.?|ft\\.?|featuring|with)\\s", RegexOption.IGNORE_CASE)
    for (t in songs) {
        for (credit in listOf(t.albumartist, t.artist)) {
            if (credit.isNotEmpty() && duoParts(credit).size == 1 && !featured.containsMatchIn(credit))
                seen.getOrPut(FIXUP_NONWORD.replace(norm(credit), "")) { LinkedHashMap() }.merge(credit.trim(), 1, Int::plus)
        }
    }
    return seen.mapValues { (_, c) -> mostCommon(c) }
}

/** Counter.most_common(1)'s: the most counted, the first counted of those tied. */
private fun <K> mostCommon(c: Map<K, Int>): K = c.entries.maxWithOrNull(compareBy { it.value })!!.key

/** Counter.most_common()'s order: the most counted first, ties as first counted. */
private fun <K> byCount(c: Map<K, Int>): List<K> = c.entries.sortedByDescending { it.value }.map { it.key }

/** A file's name without its folder or extension (pathlib's stem). */
fun fixupStem(path: String): String {
    val name = path.trimEnd('/').substringAfterLast('/')
    val suffix = fixupSuffix(name)
    return name.substring(0, name.length - suffix.length)
}

/** A file's extension with its dot, as pathlib's suffix: ".flac"; "" for ".DS_Store" or "cover." or none. */
fun fixupSuffix(name: String): String {
    val n = name.substringAfterLast('/')
    val i = n.lastIndexOf('.')
    return if (i > 0 && i < n.length - 1) n.substring(i) else ""
}

private fun albumKey(album: String) = norm(cleanTitle(album))

/** The tags a song has as Fix albums and artists reads them. */
private fun tagsOf(t: FixupSong) = FixupTags(t.artist, t.albumartist, t.album, t.track)

private class FixupNow(var artist: String, var albumartist: String, var album: String, var tracknumber: String)

/**
 * What Fix albums and artists would change: one FixupChange for each song with something to change.
 * songs: your files, as track_from_file gives them. tracklist(artist, album) -> {clean title: track number} (or null):
 * the deluxe edition's numbers, looked up. findAlbum(artist, title) -> the albums a song is on (Deezer's: its single
 * among them), or []: for singles and remixes whose album you have but not the song on it. coverOf(file) -> its cover's
 * picture hash, or null: albums of an artist's with the same cover (a single, an edition, a part) onto one of them.
 */
fun fixupPlan(
    songs: List<FixupSong>,
    tracklist: ((artist: String, album: String) -> Map<String, Int>?)? = null,
    findAlbum: ((artist: String, title: String) -> List<String>?)? = null,
    coverOf: ((file: String) -> Long?)? = null,
): List<FixupChange> {
    val solo = fixupSoloArtists(songs)
    val fields = listOf("artist", "albumartist", "album", "tracknumber")
    val now = LinkedHashMap<String, FixupNow>()
    for (t in songs) now[t.file] = FixupNow(t.artist, t.albumartist, t.album, t.track)
    val why = LinkedHashMap<String, MutableList<String>>()
    val kinds = LinkedHashMap<String, MutableSet<String>>()
    val cover = LinkedHashMap<String, String>()
    fun kindsOf(f: String) = kinds.getOrPut(f) { LinkedHashSet() }
    fun whyOf(f: String) = why.getOrPut(f) { ArrayList() }
    fun has(f: String) = !kinds[f].isNullOrEmpty()

    // 1. Featured artists: each album's songs credited to its main artist only.
    val albums = LinkedHashMap<Pair<String, String>, MutableList<FixupSong>>()
    for (t in songs) {
        val credit = t.albumartist.ifEmpty { t.artist }
        if (t.album.isEmpty() || norm(credit) in VARIOUS || norm(t.artist) in VARIOUS) continue
        albums.getOrPut(albumKey(t.album) to artistKey(credit)) { ArrayList() } += t
    }
    val others = HashMap<String, List<String>>()
    val siblings = HashMap<String, List<FixupSong>>()
    val groupOf = HashMap<String, Int>()
    val unsure = HashSet<String>()
    val creditsOf = creditCounts(songs)
    val spelt = soloSpellings(songs)
    val alone = solo.filter { it in spelt }.associateWith { spelt[it]!! }.ifEmpty { solo.associateWith { it } }
    for ((gn, group) in albums.values.withIndex()) {
        val picks = group.map { pickMain(it.albumartist.ifEmpty { it.artist }, alone, creditsOf) }
        val mains = LinkedHashMap<String, Int>()
        for ((m, _) in picks) mains.merge(m, 1, Int::plus)
        val main = mains.keys.minWithOrNull(compareBy<String>({ Regex(" & | x |,").containsMatchIn(it) }, { -mains[it]!! }, { it.length }))!!
        if (main.isEmpty()) continue
        val sure = picks.filter { it.first == main }.all { it.second }
        // The other artists credited on the album, for "Other artist" (when the main one picked is the wrong one).
        val names = LinkedHashMap<String, Int>()
        for (t in group) {
            for (credit in linkedSetOf(t.albumartist, t.artist)) {
                val lead = credit.split(FIXUP_FEAT)
                for (part in duoParts(lead[0]) + lead.drop(1).map { it.trim() }) {
                    if (part.isNotEmpty() && artistKey(part) != artistKey(main)) names.merge(part, 1, Int::plus)
                }
            }
        }
        val alt = byCount(names)
        val changedBefore = kinds.keys.toSet()
        for (t in group) {
            val cur = now[t.file]!!
            val credit = cur.albumartist.ifEmpty { cur.artist }
            if (cur.artist != main || (cur.albumartist.isNotEmpty() && cur.albumartist != main)) {
                val theirs = HashSet<String>()
                for (c in listOf(cur.artist, credit))
                    for (n in listOf(c) + creditNames(c) + duoParts(c.split(FIXUP_FEAT)[0])) theirs += artistKey(n)
                if (artistKey(main) !in theirs) continue   // someone else's song on the album (a compilation): not ours to change
                if (!sure) unsure += t.file
                val was = cur.artist
                cur.artist = main
                if (cur.albumartist.isNotEmpty()) cur.albumartist = main
                kindsOf(t.file) += "artist"
                whyOf(t.file) += "“$was” → “$main”"
            }
        }
        val mine = group.map { it.file }.filter { kinds[it]?.contains("artist") == true && it !in changedBefore }
        if (mine.isNotEmpty()) {
            val sib = group.filter { it.file !in mine }
            for (f in mine) {
                others[f] = alt; siblings[f] = sib; groupOf[f] = gn
            }
        }
    }

    // 1b. An album in parts ("…: Pt. 1", "…: Pt. 2", and the whole one): all of it onto the one with no part,
    // numbered as the whole album has them (looked up), else one part after the other.
    val parts = LinkedHashMap<Pair<String, String>, MutableList<FixupSong>>()
    for (t in songs) {
        val cur = now[t.file]!!
        if (cur.album.isNotEmpty()) {
            val base = albumPart(cur.album).first
            parts.getOrPut(albumKey(base) to artistKey(cur.albumartist.ifEmpty { cur.artist })) { ArrayList() } += t
        }
    }
    for (group in parts.values) {
        val names = LinkedHashSet(group.map { now[it.file]!!.album })
        if (names.size < 2 || names.none { albumPart(it).second != 0 }) continue
        val whole = albumPart(names.first()).first
        val lead = now[group[0].file]!!
        val numbers = (tracklist?.invoke(lead.albumartist.ifEmpty { lead.artist }, whole)) ?: emptyMap()
        val order = group.sortedWith(compareBy<FixupSong>({ albumPart(now[it.file]!!.album).second },
            { trackNo(now[it.file]!!.tracknumber) }, { norm(it.title) }))
        val before = LinkedHashMap<Int, Int>()
        for (t in order) before.merge(albumPart(now[t.file]!!.album).second, 1, Int::plus)
        for (t in order) {
            val cur = now[t.file]!!
            val part = albumPart(cur.album).second
            if (part == 0) continue
            val was = cur.album
            cur.album = whole
            var n = numbers[norm(cleanTitle(t.title))] ?: 0
            if (n == 0 && cur.tracknumber.isNotEmpty())
                n = trackNo(cur.tracknumber) + before.filter { it.key < part }.values.sum()
            if (n != 0) cur.tracknumber = n.toString()
            kindsOf(t.file) += "parts"
            whyOf(t.file) += "“$was” → “$whole”"
        }
    }

    // 2. Deluxe, extended and anniversary editions: the album's songs moved onto the edition.
    val editions = LinkedHashMap<Pair<String, String>, MutableList<FixupSong>>()
    for (t in songs) {
        val cur = now[t.file]!!
        if (cur.album.isNotEmpty()) {
            val base = albumEdition(cur.album).first
            editions.getOrPut(albumKey(base) to artistKey(cur.albumartist.ifEmpty { cur.artist })) { ArrayList() } += t
        }
    }
    for (group in editions.values) {
        val names = LinkedHashMap<String, Int>()
        for (t in group) now[t.file]!!.album.let { if (albumEdition(it).second.isNotEmpty()) names.merge(it, 1, Int::plus) }
        if (names.isEmpty() || group.all { albumEdition(now[it.file]!!.album).second.isNotEmpty() })
            continue   // no edition, or nothing but editions
        val edition = mostCommon(names)
        var numbers: Map<String, Int>? = null
        for (t in group) {
            val cur = now[t.file]!!
            if (cur.album == edition) continue
            val ed = albumEdition(cur.album).second
            if (ed.isNotEmpty() && ed != albumEdition(edition).second)
                continue   // another edition (Deluxe beside Anniversary): left as it is
            if (numbers == null) numbers = tracklist?.invoke(cur.albumartist.ifEmpty { cur.artist }, edition) ?: emptyMap()
            val was = cur.album
            cur.album = edition
            kindsOf(t.file) += "edition"
            whyOf(t.file) += "“$was” → “$edition”"
            val n = numbers[norm(cleanTitle(t.title))] ?: 0
            if (n != 0 && trackNo(cur.tracknumber) != n) cur.tracknumber = n.toString()
        }
        if (!numbers.isNullOrEmpty()) {   // the edition's own songs numbered wrong too
            for (t in group) {
                val cur = now[t.file]!!
                val n = numbers[norm(cleanTitle(t.title))] ?: 0
                if (cur.album == edition && n != 0 && trackNo(cur.tracknumber) != n) {
                    cur.tracknumber = n.toString()
                    kindsOf(t.file) += "edition"
                }
            }
        }
    }

    // 3. Singles of songs on an album you have: put on the album (its number; its cover if the single has none).
    // A single: an album called one ("- Single", the song's name), or any song alone on its album (the only song
    // of it you have), or with no album at all.
    val sizes = HashMap<Pair<String, String>, Int>()
    for (t in songs) {
        val cur = now[t.file]!!
        if (cur.album.isNotEmpty()) sizes.merge(albumKey(cur.album) to artistKey(cur.albumartist.ifEmpty { cur.artist }), 1, Int::plus)
    }

    fun alone(t: FixupSong): Boolean {
        val cur = now[t.file]!!
        if (cur.album.isEmpty()) return true
        return isSingle(cur.album, t.title) ||
            (sizes[albumKey(cur.album) to artistKey(cur.albumartist.ifEmpty { cur.artist })] ?: 0) <= 1
    }
    val onAlbums = LinkedHashMap<Pair<String, String>, MutableList<FixupSong>>()
    for (t in songs) {
        val cur = now[t.file]!!
        if (cur.album.isNotEmpty() && !isSingle(cur.album, t.title))   // (an album you have one song of is a home too)
            onAlbums.getOrPut(statsKey(t.title, cur.artist)) { ArrayList() } += t
    }
    for (t in songs) {
        val cur = now[t.file]!!
        if (!alone(t)) continue
        val mineSingle = cur.album.isEmpty() || isSingle(cur.album, t.title)
        val home = (onAlbums[statsKey(t.title, cur.artist)] ?: emptyList()).filter { h ->
            h.file != t.file && now[h.file]!!.album != cur.album && (mineSingle || !alone(h))
        }   // two copies each alone on an album: neither moves
        if (home.isEmpty()) continue
        val best = home[0]
        val to = now[best.file]!!
        val was = cur.album
        cur.album = to.album
        cur.albumartist = if (cur.albumartist.isNotEmpty() || to.albumartist.isNotEmpty()) to.albumartist else ""
        if (to.tracknumber.isNotEmpty()) cur.tracknumber = to.tracknumber
        kindsOf(t.file) += "single"
        whyOf(t.file) += "single “$was” → “${to.album}”"
        cover[t.file] = best.file
    }

    // 4. Singles of songs on an album you have, but not that song on it (Deezer says which album), and remixes
    // (on a single, or a remixes EP): onto the album their song is on. Onto its deluxe or extended edition
    // when you have that: "Songs" is on Persona, you have Persona (Extended): there.
    val mine = LinkedHashMap<Pair<String, String>, LinkedHashMap<String, Int>>()   // (album without edition, artist) -> your albums of it
    val first = HashMap<Pair<Pair<String, String>, String>, FixupSong>()
    for (t in songs) {
        val cur = now[t.file]!!
        if (cur.album.isNotEmpty() && !isSingle(cur.album, t.title) && remixOf(cur.album).isEmpty() && kinds[t.file]?.contains("single") != true) {
            val k = albumKey(albumEdition(cur.album).first) to artistKey(cur.albumartist.ifEmpty { cur.artist })
            mine.getOrPut(k) { LinkedHashMap() }.merge(cur.album, 1, Int::plus)
            first.putIfAbsent(k to cur.album, t)
        }
    }
    val artistsWithAlbums = mine.keys.map { it.second }.toSet()

    /** The first of names that's an album of yours (an edition of it wins), by artist: its name, or null. skip: the
     *  album the song is alone on now (not where it goes). */
    fun yourAlbum(names: List<String>, artist: String, skip: String = ""): String? {
        val own = if (skip.isNotEmpty()) albumKey(albumEdition(skip).first) else null
        for (name in names) {
            if (own != null && albumKey(albumEdition(name).first) == own) continue
            val have = mine[albumKey(albumEdition(name).first) to artistKey(artist)]
            if (!have.isNullOrEmpty())
                return have.keys.maxWithOrNull(compareBy<String>({ albumEdition(it).second.isNotEmpty() }, { have[it]!! }))
        }
        return null
    }

    val looked = HashMap<Pair<String, String>, List<String>>()

    fun deezerHome(artist: String, title: String): List<String> {
        if (findAlbum == null || artistKey(artist) !in artistsWithAlbums) return emptyList()
        val k = artistKey(artist) to albumKey(title)
        if (k !in looked) {
            looked[k] = try {
                findAlbum(artist, title) ?: emptyList()
            } catch (e: Exception) {   // offline: left as it is
                emptyList()
            }
        }
        return looked[k] ?: emptyList()
    }

    val lookedNumbers = HashMap<Pair<String, String>, Map<String, Int>>()

    fun numbersOf(artist: String, album: String): Map<String, Int> =
        lookedNumbers.getOrPut(artistKey(artist) to norm(album)) {
            try {
                tracklist?.invoke(artist, album) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        }

    for (t in songs) {
        val f = t.file
        val cur = now[f]!!
        if (kinds[f]?.any { it == "single" || it == "edition" } == true) continue
        val title = t.title
        val base = remixOf(title)
        val artist = cur.albumartist.ifEmpty { cur.artist }
        var to: String?
        if (base.isNotEmpty()) {   // a remix: on a single, a remixes EP, or no album
            if (cur.album.isNotEmpty() && !(isSingle(cur.album, title) || remixOf(cur.album).isNotEmpty()
                    || albumKey(base) in albumKey(cur.album)))
                continue   // already on an album of its own (a remix album): left there
            val home = onAlbums[statsKey(base, cur.artist)]
            to = if (!home.isNullOrEmpty()) now[home[0].file]!!.album else null
            to = if (!to.isNullOrEmpty()) yourAlbum(listOf(to), artist) else yourAlbum(deezerHome(cur.artist, base), artist)
        } else if (alone(t)) {   // a single, or a song alone on its album (or with none): the album or EP it's on, if you have it
            to = yourAlbum(deezerHome(cur.artist, title), artist, skip = cur.album)
            if (!to.isNullOrEmpty() && cur.album.isNotEmpty() && !isSingle(cur.album, title) && albumKind(to) < albumKind(cur.album))
                to = null   // alone on an album: not off it onto a sessions record or a best-of
        } else {
            continue
        }
        if (to.isNullOrEmpty() || to == cur.album) continue
        val was = cur.album
        val src = first[(albumKey(albumEdition(to).first) to artistKey(artist)) to to]
        cur.album = to
        if (src != null) {
            val ref = now[src.file]!!
            cur.albumartist = if (cur.albumartist.isNotEmpty() || ref.albumartist.isNotEmpty()) ref.albumartist else ""
            cover[f] = src.file
        }
        if (base.isNotEmpty()) {   // not on the album's track list: after its songs
            val last = songs.filter { now[it.file]!!.album == to && it.file != f }.maxOfOrNull { trackNo(now[it.file]!!.tracknumber) } ?: 0
            cur.tracknumber = if (last != 0) (last + 1).toString() else cur.tracknumber
            kindsOf(f) += "remix"
            whyOf(f) += "remix of “$base”: “${was.ifEmpty { "(no album)" }}” → “$to”"
        } else {
            val n = numbersOf(artist, to)[norm(cleanTitle(title))] ?: 0
            if (n != 0) cur.tracknumber = n.toString()
            kindsOf(f) += "single"
            whyOf(f) += "single “$was” → “$to”"
        }
    }

    // 5. The same cover: an artist's albums whose covers look alike (a single with its album's cover, a deluxe
    // beside the album, the parts of one) onto one of them: the album over a single or a remix, an edition
    // over the plain one, the one with the most songs. Only ones nothing above moved, and you check each.
    if (coverOf != null) {
        val byAlbum = LinkedHashMap<Pair<String, String>, MutableList<FixupSong>>()   // (artist, album as it'll be) -> its songs
        for (t in songs) {
            val cur = now[t.file]!!
            if (cur.album.isNotEmpty() && !has(t.file))
                byAlbum.getOrPut(artistKey(cur.albumartist.ifEmpty { cur.artist }) to cur.album) { ArrayList() } += t
        }
        val perArtist = LinkedHashMap<String, MutableList<String>>()
        for ((key, _) in byAlbum) perArtist.getOrPut(key.first) { ArrayList() } += key.second
        for ((ak, names) in perArtist) {
            if (names.size < 2) continue
            val looks = LinkedHashMap<String, Long>()
            for (name in names) {
                for (t in byAlbum[ak to name]!!.take(3)) {   // the first song of it with a cover
                    val h = try {
                        coverOf(t.file)
                    } catch (e: Exception) {   // unreadable: no cover
                        null
                    }
                    if (h != null) {
                        looks[name] = h
                        break
                    }
                }
            }
            val parent = HashMap<String, String>()
            for (n in looks.keys) parent[n] = n

            fun root(n0: String): String {
                var n = n0
                while (parent[n] != n) {
                    parent[n] = parent[parent[n]!!]!!
                    n = parent[n]!!
                }
                return n
            }
            val named = looks.keys.sorted()
            for ((i, x) in named.withIndex()) {
                for (y in named.drop(i + 1)) {
                    if (java.lang.Long.bitCount(looks[x]!! xor looks[y]!!) <= COVER_ALIKE) parent[root(x)] = root(y)
                }
            }
            val clusters = LinkedHashMap<String, MutableList<String>>()
            for (n in named) clusters.getOrPut(root(n)) { ArrayList() } += n
            for (same in clusters.values) {
                if (same.size < 2) continue
                val to = same.maxWithOrNull(compareBy<String>(
                    { name -> !(byAlbum[ak to name]!!.all { isSingle(name, it.title) } || remixOf(name).isNotEmpty()) },
                    { name -> albumEdition(name).second.isNotEmpty() },
                    { name -> albumPart(name).second == 0 },
                    { name -> byAlbum[ak to name]!!.size },
                    { name -> -name.length }))!!
                val ref = now[byAlbum[ak to to]!![0].file]!!
                var numbers: Map<String, Int>? = null
                for (name in same) {
                    if (name == to) continue
                    for (t in byAlbum[ak to name]!!) {
                        val f = t.file
                        val cur = now[f]!!
                        cur.album = to
                        cur.albumartist = if (cur.albumartist.isNotEmpty() || ref.albumartist.isNotEmpty()) ref.albumartist else ""
                        if (numbers == null) {
                            numbers = try {
                                tracklist?.invoke(ref.albumartist.ifEmpty { ref.artist }, to) ?: emptyMap()
                            } catch (e: Exception) {
                                emptyMap()
                            }
                        }
                        val n = numbers[norm(cleanTitle(t.title))] ?: 0
                        if (n != 0) cur.tracknumber = n.toString()
                        kindsOf(f) += "cover"
                        whyOf(f) += "same cover as “$to”: “$name” → “$to”"
                    }
                }
            }
        }
    }

    // Never onto a single: a song only ever goes onto an album or an EP. A single: "single" in its name, or named for
    // its song ("Songs"), or one of yours with a song or none on it named for that one song.
    val titlesOn = HashMap<String, MutableSet<String>>()
    for (t in songs) if (t.album.isNotEmpty()) titlesOn.getOrPut(albumKey(t.album)) { LinkedHashSet() } += t.title

    fun aSingle(name: String, title: String): Boolean {
        if (name.isEmpty()) return false
        if (FIXUP_SINGLE_WORD.containsMatchIn(name)) return true
        val on = (titlesOn[albumKey(name)] ?: emptySet()) - title   // the other songs of yours on it
        if (on.isEmpty()) return isSingle(name, title)   // nothing else on it, and named for this song
        return on.size == 1 && isSingle(name, on.first())   // one song on it, and named for that one
    }
    for (t in songs) {
        val f = t.file
        val cur = now[f]!!
        if (has(f) && cur.album != t.album && aSingle(cur.album, t.title)) {
            cur.album = t.album   // back as it was: its album, number and album artist
            cur.tracknumber = t.track
            val k = kinds[f]!!
            if ("artist" !in k) cur.albumartist = t.albumartist
            k.removeAll(setOf("single", "remix", "edition", "parts", "cover"))
            why[f] = if ("artist" in k) mutableListOf("“${t.artist}” → “${cur.artist}”") else mutableListOf()
            cover.remove(f)
        }
    }

    val out = ArrayList<FixupChange>()
    for (t in songs) {
        val f = t.file
        if (!has(f)) continue
        val old = tagsOf(t)
        val n = now[f]!!
        val cur = FixupTags(n.artist, n.albumartist, n.album, n.tracknumber)
        val new = LinkedHashMap<String, String>()
        for (k in fields) if (cur[k] != old[k]) new[k] = cur[k]
        if (new.isEmpty()) continue
        val grouped = f in groupOf
        out += FixupChange(
            file = f, title = t.title.ifEmpty { fixupStem(f) }, kinds = kinds[f]!!.sorted(),
            why = (why[f] ?: emptyList<String>()).joinToString("; "), old = old, new = new, cover = cover[f],
            unsure = f in unsure,   // a duo neither of whom is an artist of their own here: Leave alone unless you say
            others = if (grouped) others[f] else null, group = if (grouped) groupOf[f] else null,
            siblings = if (grouped) siblings[f]!!.map { x -> FixupSibling(x.file, x.title.ifEmpty { fixupStem(x.file) }, tagsOf(x)) } else null,
        )
    }
    return out   // (a single with no album or EP of yours found for it: left as it is, not listed)
}

/** A change (fixupPlan's) with its album credited to other instead of the main artist picked: the songs other is
 *  credited on become theirs; the rest keep their artist and get other as album artist, so the album stays together
 *  under other. own: a name you typed for this song: its artist too. The change's album and track fixes stay. */
fun fixupOther(c: FixupChange, other: String, own: Boolean = false): FixupChange {
    val old = c.old
    val new = LinkedHashMap(c.new.filterKeys { it != "artist" && it != "albumartist" })
    val artist = if (own || creditNames(old.artist).any { artistKey(it) == artistKey(other) }) other else old.artist
    val albumartist = if (old.albumartist.isNotEmpty() || artist != other) other else ""
    if (artist != old.artist) new["artist"] = artist
    if (albumartist != old.albumartist) new["albumartist"] = albumartist
    return c.copy(new = new, kinds = (c.kinds.toSet() + "artist").sorted(),
        why = "“${old.albumartist.ifEmpty { old.artist }}” → “$other”")
}

/** Whether a change (fixupPlan's) is one of an album only (an edition, a single, a remix, parts): its third choice is
 *  Other album, not Other artist. */
fun fixupAlbumRow(c: FixupChange): Boolean = "artist" !in c.kinds && ("album" in c.new || c.unfound)

/** The changes to make: picked, for each change, Change (as planned), Leave (left alone) or Other (Other artist:
 *  fixupOther, and the album's songs that needed nothing moved to that artist too; Other album, for a change of an
 *  album only: onto the album named instead). */
fun fixupWithOthers(changes: List<FixupChange>, picked: List<FixupPick>): List<FixupChange> {
    val out = ArrayList<FixupChange>()
    val doneGroups = HashSet<Int>()
    val files = changes.map { it.file }.toSet()
    for ((c, p) in changes.zip(picked)) {
        if (p is FixupPick.Leave || (p is FixupPick.Other && p.name.isEmpty())) continue
        if (p is FixupPick.Change) {
            if (c.new.isNotEmpty()) out += c   // (a single with nothing found: nothing to change unless you pick an album)
            continue
        }
        p as FixupPick.Other
        if (fixupAlbumRow(c)) {   // Other album: onto the one picked instead (its number left as it was)
            val new = LinkedHashMap(c.new.filterKeys { it != "album" && it != "tracknumber" })
            if (p.name != c.old.album) new["album"] = p.name
            if (new.isNotEmpty()) out += c.copy(new = new, why = "“${c.old.album.ifEmpty { "(no album)" }}” → “${p.name}”")
            continue
        }
        out += fixupOther(c, p.name, own = p.own)
        if (c.group != null && c.group !in doneGroups) {
            doneGroups += c.group
            for (s in c.siblings ?: emptyList()) {
                if (s.file in files) continue
                val d = fixupOther(FixupChange(s.file, s.title, emptyList(), "", s.old, emptyMap(), null), p.name)
                if (d.new.isNotEmpty()) out += d
            }
        }
    }
    return out
}

/** A path tidied as os.path.normpath does it (POSIX): no "." or empty parts, ".." taken back, no slash at the end. */
fun fixupNormPath(path: String): String {
    if (path.isEmpty()) return "."
    val initial = if (path.startsWith("/")) (if (path.startsWith("//") && !path.startsWith("///")) 2 else 1) else 0
    val out = ArrayList<String>()
    for (p in path.split('/')) {
        if (p.isEmpty() || p == ".") continue
        if (p != ".." || (initial == 0 && out.isEmpty()) || (out.isNotEmpty() && out.last() == "..")) out += p
        else if (out.isNotEmpty()) out.removeAt(out.size - 1)
    }
    val s = "/".repeat(initial) + out.joinToString("/")
    return s.ifEmpty { "." }
}

/** Whether path is one of folders or inside one. */
fun inFolders(path: String, folders: Collection<String>?): Boolean {
    val p = fixupNormPath(path)
    for (f0 in folders ?: emptyList()) {
        val f = fixupNormPath(f0)
        if (p == f || p.startsWith(f.trimEnd('/') + "/")) return true
    }
    return false
}

/** The folders Fix albums and artists leaves alone, as kept: each once, in the order given, none empty. */
fun fixupSkipList(folders: List<String>): List<String> = folders.filter { it.isNotEmpty() }.distinct()

/** fixupPlan's changes with those in folders left alone taken out: (the ones kept, how many were taken out). */
fun fixupKept(plan: List<FixupChange>, skip: Collection<String>): Pair<List<FixupChange>, Int> {
    val kept = plan.filter { !inFolders(it.file, skip) }
    return kept to (plan.size - kept.size)
}

/** A path as pathlib keeps it (POSIX): no empty or "." parts, no slash at the end (".." kept as it is). */
private fun purePath(path: String): String {
    val abs = path.startsWith("/")
    val lead = if (path.startsWith("//") && !path.startsWith("///")) "//" else if (abs) "/" else ""
    val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
    val s = lead + parts.joinToString("/")
    return s.ifEmpty { "." }
}

private fun parentOf(path: String): String {
    val p = purePath(path)
    val i = p.lastIndexOf('/')
    return when {
        i < 0 -> "."
        i == 0 -> "/"
        p.substring(0, i).all { it == '/' } -> p.substring(0, i + 1)
        else -> p.substring(0, i)
    }
}

private fun joinPath(root: String, parts: List<String>): String =
    purePath(parts.fold(purePath(root)) { acc, p -> if (acc.endsWith("/")) acc + p else "$acc/$p" })

/**
 * Where each changed file goes after its tags are fixed: [(file, new path)], only those that move. Your folders are
 * Artist/…/Album/song: the artist's folder becomes the main artist's (theirs if they have one), and an album's song goes
 * to the folder its album's other songs are in. Never onto a file that's there, and nothing out of or into skip (the
 * folders Fix albums and artists leaves alone). exists(path): whether a file is there; subfolders(folder): the names of
 * the folders in it (none if it isn't one) — the caller's, from the disk.
 */
fun fixupMoves(
    changes: List<FixupChange>,
    songs: List<FixupSong>,
    roots: List<String>,
    skip: Collection<String> = emptyList(),
    exists: (path: String) -> Boolean = { false },
    subfolders: (folder: String) -> List<String> = { emptyList() },
): List<Pair<String, String>> {
    val byAlbum = LinkedHashMap<Pair<String, String>, LinkedHashMap<String, Int>>()   // (album, artist) -> the folders its songs are in
    val final = LinkedHashMap<String, FixupTags>()
    for (t in songs) final[t.file] = tagsOf(t)
    for (c in changes) final[c.file] = (final[c.file] ?: FixupTags()).with(c.new)
    val moving = changes.map { it.file }.toSet()
    for ((f, t) in final) {
        if (f !in moving && t.album.isNotEmpty())
            byAlbum.getOrPut(albumKey(t.album) to artistKey(t.albumartist.ifEmpty { t.artist })) { LinkedHashMap() }
                .merge(parentOf(f), 1, Int::plus)
    }
    val out = ArrayList<Pair<String, String>>()
    val taken = HashSet<String>()
    for (c in changes) {
        val src = purePath(c.file)
        val t = final[c.file]!!
        val root = roots.firstOrNull { src.startsWith(purePath(it) + "/") }?.let(::purePath) ?: continue
        val there = byAlbum[albumKey(t.album) to artistKey(t.albumartist.ifEmpty { t.artist })]
        val folder: String
        if (!there.isNullOrEmpty()) {
            folder = purePath(mostCommon(there))
        } else {
            val parts = parentOf(src).removePrefix(root).trimStart('/').split('/').filter { it.isNotEmpty() }.toMutableList()
            if (parts.isEmpty()) continue
            val old = c.old
            val new = old.with(c.new)
            val main = new.albumartist.ifEmpty { new.artist }
            for (i in parts.indices) {   // the artist's folder: the main artist's
                val p = parts[i]
                if ((norm(p) == norm(old.artist) || norm(p) == norm(old.albumartist)) && norm(p) != norm(main)) {
                    val at = joinPath(root, parts.subList(0, i))
                    val sib = subfolders(at).firstOrNull { norm(it) == norm(main) }
                    parts[i] = sib ?: pathPart(main)
                }
            }
            if (old.album != new.album) {   // the album's folder: the album's new name
                for (i in parts.indices) if (norm(parts[i]) == norm(old.album)) parts[i] = pathPart(new.album)
            }
            folder = joinPath(root, parts)
        }
        val dest = joinPath(folder, listOf(src.substringAfterLast('/')))
        if (dest == src || exists(dest) || dest.lowercase() in taken || inFolders(src, skip) || inFolders(dest, skip)) continue
        taken += dest.lowercase()
        out += src to dest
    }
    return out
}

/** The albums you could pick with Other album for a change of an album only, and the list to pick from: (its key, the
 *  names offered first — where it's planned to go, the artist's own albums, where it is — and every album of yours).
 *  Never a single. As the desktop's Fix albums and artists dialog offers them. */
fun fixupAlbumChoices(c: FixupChange, songs: List<FixupSong>): Triple<String, List<String>, List<String>> {
    val onIt = HashMap<String, MutableSet<String>>()
    for (t in songs) if (t.album.isNotEmpty()) onIt.getOrPut(t.album) { LinkedHashSet() } += t.title

    fun notSingle(name: String): Boolean {   // albums and EPs only to pick from: never a single
        val on = onIt[name] ?: emptySet<String>()
        return !FIXUP_SINGLE_WORD.containsMatchIn(name) && !(on.size == 1 && isSingle(name, on.first()))
    }
    val albumNames = songs.map { it.album }.filter { it.isNotEmpty() && notSingle(it) }.distinct().sortedBy { norm(it) }
    val byArtist = HashMap<String, MutableSet<String>>()
    for (t in songs) if (t.album.isNotEmpty() && notSingle(t.album))
        byArtist.getOrPut(artistKey(t.albumartist.ifEmpty { t.artist })) { LinkedHashSet() } += t.album
    val old = c.old.album
    val planned = c.new["album"].orEmpty().ifEmpty { old }
    val theirs = (byArtist[artistKey(c.old.albumartist.ifEmpty { c.old.artist })] ?: emptySet<String>()).sortedBy { norm(it) }
    val names = (listOf(planned) + theirs + (if (old.isNotEmpty() && notSingle(old)) listOf(old) else emptyList())).distinct()
    return Triple("al:" + norm(planned), names, albumNames)
}

/** Which changes start ticked (Change): all but the unsure ones and the singles with nothing found. */
fun fixupDefaults(changes: List<FixupChange>): List<Boolean> = changes.map { !it.unsure && !it.unfound }
