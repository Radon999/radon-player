package org.omarchy.music

/** What you hid from an artist's page yourself, and what you said belongs there after all. A service (or Genius)
 *  can mix two bands of one name; where neither tells them apart, you do: a song or a release hidden stays off that
 *  artist's page, one kept stays on it whatever Genius's list says. Nothing is deleted: hidden ones are listed under
 *  "left out" and can be put back. */
object ArtistShownRules {
    const val HIDE = 'h'
    const val KEEP = 'k'

    fun prefKey(artist: String) = "artist_marks:" + norm(artist)

    /** A song on the page, whichever version or release it is of ("Drew", "Drew (Live)", "Drew - Single" are one). */
    fun songKey(title: String) = "s:" + looseTitle(title).ifEmpty { norm(title) }

    /** A release, its edition aside. */
    fun albumKey(title: String) = "a:" + albumBase(title).ifEmpty { norm(title) }

    fun read(text: String?): Map<String, Char> = text.orEmpty().lines().mapNotNull { l ->
        val mark = l.firstOrNull() ?: return@mapNotNull null
        if ((mark != HIDE && mark != KEEP) || l.length < 3 || l[1] != '\t') null else l.substring(2) to mark
    }.toMap()

    fun write(marks: Map<String, Char>): String = marks.entries.joinToString("\n") { "${it.value}\t${it.key}" }

    /** The marks with these keys marked (null: no mark any more). */
    fun marked(marks: Map<String, Char>, keys: Collection<String>, mark: Char?): Map<String, Char> =
        LinkedHashMap(marks).apply { for (k in keys) if (mark == null) remove(k) else put(k, mark) }

    /** Whether it shows on the page: what you said wins; without a word from you, whether it is theirs. */
    fun shown(mark: Char?, theirs: Boolean): Boolean = mark == KEEP || (mark != HIDE && theirs)
}
