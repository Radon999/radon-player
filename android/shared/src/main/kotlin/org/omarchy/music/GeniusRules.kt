package org.omarchy.music

/** What of a Genius search is shown: songs, each once, without Genius's own pages (translations, romanizations,
 *  annotated lists), which aren't songs by anyone. */
object GeniusRules {
    data class Hit(val title: String, val artist: String, val art: String = "")

    /** Genius's own pages go under artists named "Genius …" ("Genius English Translations", "Genius Romanizations"). */
    fun isSong(h: Hit): Boolean = h.title.isNotBlank() && h.artist.isNotBlank() && !h.artist.trim().lowercase().startsWith("genius")

    /** The songs of a search, in Genius's order, each once (the same title and artist as Most played matches them,
     *  and the same kind of version). Genius's placeholder picture is no cover. */
    fun songs(hits: List<Hit>): List<Hit> {
        val seen = HashSet<Pair<Pair<String, String>, Set<String>>>()
        return hits.filter { isSong(it) && seen.add(statsKey(it.title, it.artist) to CopyRules.versionWords(it.title)) }
            .map { if ("default_cover" in it.art) it.copy(art = "") else it }
    }

    // ---- Genius as the judge of whose a song is ----
    /** The ways a title is compared: as it is tidied, without what is in brackets or after a dash ("Song - Live"),
     *  and loosely. One of them the same is the same title. */
    fun titleKeys(title: String): Set<String> =
        setOf(norm(cleanTitle(title)), norm(bareTitle(title)), norm(looseTitle(title))).filterTo(HashSet()) { it.isNotEmpty() }

    /** Whether a title is one of the artist's, going by the songs Genius lists for them. Two bands a service has
     *  put under one name are told apart this way: Genius keeps them as two artists. */
    fun judge(theirSongs: List<Hit>): (String) -> Boolean {
        val keys = theirSongs.flatMapTo(HashSet()) { titleKeys(it.title) }
        return { title -> titleKeys(title).any { it in keys } }
    }

    /** A release (its songs' titles) is the artist's when half its songs or more are theirs; one whose songs
     *  aren't known is left in (nothing to judge it by). */
    fun releaseIsTheirs(titles: List<String>, theirs: (String) -> Boolean): Boolean =
        titles.isEmpty() || titles.count(theirs) * 2 >= titles.size

    private val EDITION = Regex("""\s*(?:[(\[]\s*(?:album|single|ep|lp|original|studio)\s+version\s*[)\]]|\s[-–—]\s+(?:album|single|ep|lp|original|studio)\s+version\s*$)""", RegexOption.IGNORE_CASE)

    /** An artist's songs, each once. Genius often has a page for the album's, the single's and the EP's version
     *  of one song: those are one song here (a live one, a remix or a demo is still its own). */
    fun artistSongs(hits: List<Hit>): List<Hit> {
        val seen = HashSet<Pair<String, Set<String>>>()
        return songs(hits).filter { h ->
            val t = EDITION.replace(h.title, "")
            seen.add(looseTitle(t).ifEmpty { norm(t) } to CopyRules.versionWords(t))
        }
    }

    /** Genius names same-named artists apart with a word in brackets: "HOAX", "Hoax (UK)", "HOAX (Techno)". */
    fun sameNamed(name: String, theirs: String): Boolean =
        norm(name).isNotEmpty() && norm(Regex("""\s*\([^)]*\)\s*$""").replace(theirs, "")) == norm(name)

    /** Of Genius's songs, the ones not among these titles (songs no release on the service has). */
    fun missing(hits: List<Hit>, have: List<String>): List<Hit> {
        val keys = have.flatMapTo(HashSet()) { titleKeys(it) }
        return hits.filter { h -> titleKeys(h.title).none { it in keys } }
    }

    /** What a row says under a song found on Genius that isn't yours. */
    const val NOTE = "Found on Genius · plays if Tidal or YouTube has it"
}
