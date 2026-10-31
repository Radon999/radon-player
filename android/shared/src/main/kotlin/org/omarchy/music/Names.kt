package org.omarchy.music

import java.text.Normalizer

// Names of songs, artists and albums, tidied and compared: the same on the phone and the PC (the desktop's norm,
// clean_title, similar, album_edition, is_single, album_kind, is_ep and strip_live). Shared by every app.

private val NON_WORD = Regex("[^\\w\\s]")
private val SPACES = Regex("\\s+")

private fun squash(s: String) = s.split(SPACES).filter { it.isNotEmpty() }.joinToString(" ")

/** Lowercase letters and digits only, accents dropped: for comparing names across services. */
fun norm(s: String?): String {
    val text = s ?: ""
    val ascii = Normalizer.normalize(text, Normalizer.Form.NFKD).filter { it.code < 128 }.ifEmpty { text }
    return squash(NON_WORD.replace(ascii.lowercase(), " "))
}

private val NOISE_BRACKETS = Regex(
    "[(\\[][^)\\]]*\\b(official|video|audio|lyrics?|visuali[sz]er|hd|4k|mv|remaster(ed)?|feat|ft|with" +
        "|original|album version|explicit|clean|single version)\\b[^)\\]]*[)\\]]"
)
private val NOISE_DASH = Regex("\\s[-–]\\s[^-–]*\\b(official|video|audio|lyrics?|remaster(ed)?)\\b.*$")
private val FEAT_TAIL = Regex("\\b(feat|ft)\\b\\.?\\s.*$")
// No Unicode flag ("?U"): Android's ICU regex rejects it, and its \b knows Unicode letters anyway.
private val FEATURING = Regex("\\b(?:feat|ft|with)\\b\\.?\\s*([^)\\]]+)", RegexOption.IGNORE_CASE)

/** A title without what YouTube adds: (Official Video), feat. credits, remaster notes, punctuation. */
fun cleanTitle(s: String): String {
    var t = s.lowercase()
    t = NOISE_BRACKETS.replace(t, " ")
    t = NOISE_DASH.replace(t, " ")
    t = FEAT_TAIL.replace(t, " ")
    return squash(NON_WORD.replace(t, " "))
}

/** Just the song's name: no brackets, nothing after " - ". */
fun bareTitle(s: String) = cleanTitle(Regex("[(\\[][^)\\]]*[)\\]]|\\s[-–]\\s.*$").replace(s, " "))

/** An album's name for telling albums apart: its edition left out ("Nectar (Deluxe Edition)", "Nectar - EP" are Nectar),
 *  but not the rest of it ("Mercury - Act 1", "Mercury - Act 2" and "Loom - Sessions" are each their own album). */
fun albumBase(s: String): String {
    var t = s.lowercase()
    t = ALBUM_EDITION_BRACKETS.replace(t, " ")
    t = ALBUM_EDITION_DASH.replace(t, " ")
    return norm(cleanTitle(t))
}
private val ALBUM_EDITION_WORDS = "deluxe|edition|expanded|remaster(ed)?|anniversary|bonus|explicit|clean|version|extended|complete|special|collector'?s?|super|reissue"
private val ALBUM_EDITION_BRACKETS = Regex("[(\\[][^)\\]]*\\b($ALBUM_EDITION_WORDS)\\b[^)\\]]*[)\\]]")
private val ALBUM_EDITION_DASH = Regex("\\s[-–]\\s(ep|single|[^-–]*\\b($ALBUM_EDITION_WORDS)\\b[^-–]*)\\s*$")

/** Artists credited in a title: "Song (feat. A & B)" -> {a, b}. */
fun featured(title: String): Set<String> {
    val m = FEATURING.find(title) ?: return emptySet()
    return artistNames(listOf(m.groupValues[1]))
}

/** Each artist in names, lowercased; splits "A, B & C" and drops "- Topic". */
fun artistNames(names: Iterable<String>): Set<String> {
    val out = linkedSetOf<String>()
    for (n0 in names) {
        val n = Regex("\\s*-\\s*topic$|vevo$").replace(n0.lowercase().trim(), "")
        for (part in n.split(Regex(",|&|\\bx\\b|\\band\\b|\\bfeat\\b\\.?|\\bft\\b\\.?"))) {
            val p = squash(NON_WORD.replace(part, " "))
            if (p.isNotEmpty()) out += p
        }
    }
    return out
}

/** The artists in a credit, as written: "A, B feat. C" -> [A, B, C]. "A & B" may be a
 *  duo's name, so it is offered whole and as each of them. */
fun splitArtists(text: String?): List<String> {
    val out = linkedSetOf<String>()
    for (part0 in (text ?: "").split(Regex("(?i),\\s*|\\s+(?:feat\\.?|ft\\.?|featuring|with)\\s+"))) {
        val part = part0.trim()
        if (part.isEmpty() || part.lowercase() == "unknown artist") continue
        out += part
        if (" & " in part) part.split(" & ").map { it.trim() }.filter { it.isNotEmpty() }.forEach { out += it }
    }
    return out.toList()
}

fun hasWord(text: String, word: String) = Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(text)

/** difflib.SequenceMatcher(None, a, b).ratio(), the measure the desktop app matches songs with. */
fun similar(a: String, b: String): Double {
    if (a.isEmpty() && b.isEmpty()) return 1.0
    val b2j = HashMap<Char, MutableList<Int>>()
    b.forEachIndexed { j, c -> b2j.getOrPut(c) { mutableListOf() } += j }
    var matches = 0
    val stack = ArrayDeque<IntArray>().apply { add(intArrayOf(0, a.length, 0, b.length)) }
    while (stack.isNotEmpty()) {
        val (alo, ahi, blo, bhi) = stack.removeLast().let { listOf(it[0], it[1], it[2], it[3]) }
        var besti = alo
        var bestj = blo
        var bestsize = 0
        var j2len = HashMap<Int, Int>()
        for (i in alo until ahi) {
            val newj2len = HashMap<Int, Int>()
            for (j in b2j[a[i]] ?: emptyList()) {
                if (j < blo) continue
                if (j >= bhi) break
                val k = (j2len[j - 1] ?: 0) + 1
                newj2len[j] = k
                if (k > bestsize) {
                    besti = i - k + 1
                    bestj = j - k + 1
                    bestsize = k
                }
            }
            j2len = newj2len
        }
        if (bestsize > 0) {
            matches += bestsize
            if (alo < besti && blo < bestj) stack.add(intArrayOf(alo, besti, blo, bestj))
            if (besti + bestsize < ahi && bestj + bestsize < bhi)
                stack.add(intArrayOf(besti + bestsize, ahi, bestj + bestsize, bhi))
        }
    }
    return 2.0 * matches / (a.length + b.length)
}

private val EDITION_END = Regex("\\s*(?:[(\\[]\\s*([^)\\]]{1,60}?)\\s*[)\\]]|\\s[-–:]\\s*([^-–:()\\[\\]]{1,60}?))\\s*$")
private val EDITION_WORDS = Regex("^(?:the\\s+)?(?:[\\w']+\\s+){0,2}(?:deluxe|extended|anniversary|edition|expanded)(?:\\s+[\\w']+){0,2}$", RegexOption.IGNORE_CASE)   // "Full Moon Edition": any edition
private val SESSIONS = Regex("\\bsessions?\\b", RegexOption.IGNORE_CASE)

/** (the album's name without its edition, the edition) — ("Persona", "Extended") — or (album, ""). */
fun albumEdition(album: String): Pair<String, String> {
    val a = album.trim()
    val m = EDITION_END.find(a) ?: return a to ""
    val inner = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
    val base = a.substring(0, m.range.first).trim()
    return if (EDITION_WORDS.matches(inner) && !SESSIONS.containsMatchIn(inner) && base.isNotEmpty()) base to inner else a to ""
}

private val SINGLE_END = Regex("\\bsingles?\\b", RegexOption.IGNORE_CASE)   // "Talk - Single", "RUN (Single)", "Singles Collection": the word anywhere

/** An album that's a single: named for its song ("Song", "Song - Single"), or called a single. */
fun isSingle(album: String, title: String): Boolean {
    val a = album.trim()
    if (a.isEmpty()) return false
    if (SINGLE_END.containsMatchIn(a)) return true
    return title.isNotBlank() && norm(cleanTitle(a)) == norm(cleanTitle(title))
}

fun albumKind(album: String, title: String = ""): Int {
    val name = album.trim()
    return when {
        isSingle(name, title) -> 0
        Regex("(?:^|[\\s(\\[-])EP\\b").containsMatchIn(name) -> 1
        Regex("\\bsessions?\\b|\\bgreatest hits\\b|\\bbest of\\b", RegexOption.IGNORE_CASE).containsMatchIn(name) -> 1
        albumEdition(name).second.isNotEmpty() -> 3
        else -> 2
    }
}

private val PART_END = Regex("\\s*(?:[:,(\\[]|\\s[-–])?\\s*\\b(?:pt\\.?|part)\\s*(\\d+|[ivx]+|one|two|three|four)\\s*[)\\]]?\\s*$", RegexOption.IGNORE_CASE)

/** An album's name without its part ("The Original Motion Picture Soundtrack: Pt. 2" -> "The Original Motion Picture
 *  Soundtrack"), or the name. The desktop's album_part. */
fun albumWithoutPart(album: String): String {
    val m = PART_END.find(album.trim()) ?: return album.trim()
    val base = album.trim().substring(0, m.range.first).trim().trim(':', '-', '–', ',', ' ')
    return base.ifEmpty { album.trim() }
}


/** An EP: "EP" as a word in its name ("South - EP", "Ruby EP", "Remote EP (Deluxe)"). The desktop's is_ep. */
fun isEp(album: String): Boolean = Regex("(?:^|[\\s(\\[-])EP\\b").containsMatchIn(album)


private val LIVE_PART = Regex("\\s*[(\\[][^)\\]]*\\blive\\b[^)\\]]*[)\\]]|\\s+[-–—]\\s+[^-–—]*\\blive\\b.*$", RegexOption.IGNORE_CASE)

/** A song's name without its live part ("Song (Live)", "Song - Live at Red Rocks" -> "Song"); "Live Forever" stays. */
fun stripLive(title: String): String = LIVE_PART.replace(title, "").trim().ifEmpty { title }

fun hasLive(title: String): Boolean = LIVE_PART.containsMatchIn(title)

/** "(From the original motion picture …)", "[From “Barbie”]", "- From the Netflix Series …", "(Original Motion Picture
 *  Soundtrack)", "(Music from the film …)": a soundtrack's note in a song's name, not the song's own words. Brackets
 *  starting "from" count only with a film/series/soundtrack word or a quoted title, so "(From Me to You)" stays. */
private val MOVIE_WORDS = "(motion picture|soundtrack|film|movie|series|musical|original score|o\\.?s\\.?t\\.?|netflix|disney|pixar|video game|tv show|television|anime|the album)"
private val MOVIE_PART = Regex(
    "\\s*[(\\[]\\s*(?:(?:music\\s+)?from\\s+(?:[^)\\]]*?$MOVIE_WORDS[^)\\]]*|[\"“”'‘][^)\\]]+)|(?:the\\s+)?(?:original\\s+)?(?:motion picture|movie|film|series)?\\s*soundtrack[^)\\]]*)[)\\]]" +
        "|\\s+[-–—]\\s+(?:music\\s+)?from\\s+(?:.*?$MOVIE_WORDS.*|[\"“].+)$",
    RegexOption.IGNORE_CASE)

/** A song's name with the soundtrack note taken off ("Song (From the Motion Picture “X”)" -> "Song"), or null if it
 *  has none. */
fun movieName(title: String?): String? {
    val t = title ?: return null
    if (!MOVIE_PART.containsMatchIn(t)) return null   // no note: not offered (a space after the name doesn't count as one)
    val plain = MOVIE_PART.replace(t, "").trim()
    return if (plain != t.trim() && plain.isNotEmpty()) plain else null
}

/** The file a song would have after its title changed: the old title in the file's name swapped for the new one, or
 *  else the track number kept ("03 - ") and the new title after it. Same folder, same extension; characters Windows
 *  won't take become "_". Null if the name wouldn't change. */
fun renamedFile(path: String, oldTitle: String, newTitle: String): String? {
    val cut = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
    val dir = if (cut >= 0) path.substring(0, cut + 1) else ""
    val name = path.substring(cut + 1)
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    fun safe(t: String) = t.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trimEnd('.')
    val new = safe(newTitle).ifEmpty { return null }
    val old = safe(oldTitle)
    val at = if (old.isNotEmpty()) base.indexOf(old, ignoreCase = true) else -1
    val nb = if (at >= 0) base.substring(0, at) + new + base.substring(at + old.length)
        else (Regex("^\\d{1,3}(?:[ ._-]+|$)").find(base)?.value ?: "") + new
    return if (nb == base) null else dir + nb + ext
}
