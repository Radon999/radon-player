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

    // ---- a search: a song on a release shows as that release ----
    data class Release(val id: String, val title: String, val artist: String, val art: String = "", val year: String = "")

    private val SINGLE = Regex("""\s*(?:[-–—]\s*single|[(\[]\s*single\s*[)\]])\s*$""", RegexOption.IGNORE_CASE)

    /** A release that is only the song itself: named "Song - Single", or just as the song is. */
    fun isSingleOf(song: String, release: String): Boolean {
        if (SINGLE.containsMatchIn(release)) return true
        val keys = titleKeys(release)
        return titleKeys(song).any { it in keys }
    }

    /** What a search shows of its songs (each with the release Genius has it on, or null) and the releases it
     *  found by name: the songs released by themselves, as songs; and the releases, each once, the ones found by
     *  name first. A song on an album or an EP is not listed by itself: its release is, and holds it. */
    fun fold(songs: List<Pair<Hit, Release?>>, found: List<Release>): Pair<List<Hit>, List<Release>> {
        val alone = songs.filter { (h, r) -> r == null || isSingleOf(h.title, r.title) }.map { it.first }
        val held = songs.mapNotNull { (h, r) -> r?.takeIf { !isSingleOf(h.title, it.title) } }
        return alone to (found + held).distinctBy { it.id }
    }

    // ---- an artist's whole discography ----
    /** Of the releases Genius lists for an artist, the ones the page doesn't have yet (by name, editions aside):
     *  added to it, so a release the service leaves out is still there. */
    fun <T> unlisted(genius: List<T>, have: List<String>, title: (T) -> String): List<T> {
        val bases = have.mapTo(HashSet()) { albumBase(it) }
        val seen = HashSet<String>()
        return genius.filter { g -> albumBase(title(g)).let { it.isNotEmpty() && it !in bases && seen.add(it) } }
    }

    /** With no Genius artist picked, Genius's only artist of the name is taken for the same one when they share a
     *  release with the page (a name alone can be someone else's). */
    fun sharesARelease(genius: List<String>, have: List<String>): Boolean {
        val bases = have.mapTo(HashSet()) { albumBase(it) }
        return genius.any { albumBase(it) in bases }
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
            val t = plainTitle(h.title)
            seen.add(looseTitle(t).ifEmpty { norm(t) } to CopyRules.versionWords(t))
        }
    }

    /** A title without the words that only say which release it is from ("(Single Version)", "- Album Version"). */
    fun plainTitle(title: String): String = EDITION.replace(title, "")

    /** Genius names same-named artists apart with a word in brackets: "HOAX", "Hoax (UK)", "HOAX (Techno)". */
    fun sameNamed(name: String, theirs: String): Boolean =
        norm(name).isNotEmpty() && norm(Regex("""\s*\([^)]*\)\s*$""").replace(theirs, "")) == norm(name)

    /** Of Genius's songs, the ones not among these titles (songs no release on the service has). */
    fun missing(hits: List<Hit>, have: List<String>): List<Hit> {
        val keys = have.flatMapTo(HashSet()) { titleKeys(it) }
        return hits.filter { h -> titleKeys(h.title).none { it in keys } }
    }

    /** What a row says under a song found on Genius that isn't yours. */
    const val NOTE = "A single · found on Genius · plays if Tidal or YouTube has it"
}
