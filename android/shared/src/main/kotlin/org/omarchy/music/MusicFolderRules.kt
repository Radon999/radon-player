package org.omarchy.music

/** The rules of your music folders: which folders are kept when you choose them, what a song becomes when its file
 *  is moved, where a song that isn't on this device is looked for, and what is said. */
object MusicFolderRules {
    /** Your folders become list: each once, in order; one inside another is dropped, since the outer one has its songs. */
    fun clean(list: List<String>): List<String> =
        list.distinct().filter { f -> list.none { it != f && f.startsWith(it) } }.sorted()

    /** The id of a song that is a file of yours at this address (the PC's files are named by where they are). */
    fun fileId(file: String): String = "f" + file.hashCode().toUInt().toString(16)

    /** Song t after files moved (old address to new, with forward slashes): at its new address, and with the id that
     *  goes with it when its id was made from its address; t itself when its file did not move. */
    fun moved(t: Track, moved: Map<String, String>): Track {
        val f = t.file?.replace('\\', '/') ?: return t
        val to = moved[f] ?: return t
        return t.copy(file = to, id = if (t.source == "file" && t.id.startsWith("f")) fileId(to) else t.id)
    }

    /** Where a song that isn't on this device is looked for to stream: where the search bar searches, but never
     *  your own music, and Tidal only when you are signed in; else YouTube Music. */
    fun streamFrom(searchIn: String, tidalSignedIn: Boolean): String = when {
        searchIn == "local" -> "ytm"
        searchIn == "tidal" -> if (tidalSignedIn) "tidal" else "ytm"
        else -> searchIn
    }

    fun movedText(n: Int): String = "Found $n moved song${if (n != 1) "s" else ""} and linked them in your playlists"
    fun matchedText(n: Int): String = "Matched $n song${if (n != 1) "s" else ""} from your backup to your music here"
    fun genresText(n: Int): String = "Genres added to $n download${if (n != 1) "s" else ""}"
}
