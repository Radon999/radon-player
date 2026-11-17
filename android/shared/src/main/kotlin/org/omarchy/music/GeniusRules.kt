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

    /** What a row says under a song found on Genius that isn't yours. */
    const val NOTE = "Found on Genius · plays if Tidal or YouTube has it"
}
