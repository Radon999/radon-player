package org.omarchy.music

/** A sort of its own for each playlist: the one you pick in a playlist is kept for that playlist; one you never
 *  sorted follows the last sort you picked anywhere (as every playlist did before). Pure. */
object PlaylistSortRules {
    const val PREF = "playlist_sorts"   // "playlist's id<TAB>sort", one a line
    const val KEEP = 500

    fun parse(text: String): Map<String, String> = text.split('\n').mapNotNull { l ->
        val i = l.indexOf('\t'); if (i <= 0) null else l.substring(0, i) to l.substring(i + 1).trim()
    }.filter { it.second in PLAYLIST_SORTS }.toMap()

    fun write(m: Map<String, String>): String = m.entries.toList().takeLast(KEEP).joinToString("\n") { "${it.key}\t${it.value}" }

    /** The sort a playlist is shown in. */
    fun of(id: String?, own: Map<String, String>, everywhere: String): String = id?.let { own[it] } ?: everywhere

    /** After you pick sort in playlist id (the newest pick last, so the oldest go first when there are too many). */
    fun picked(own: Map<String, String>, id: String, sort: String): Map<String, String> = LinkedHashMap(own).apply { remove(id); put(id, sort) }
}
