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

    /** Whether a song file's address is one this device has its songs at, so a file there that can't be found is gone
     *  from here (not the other device's, from a synced playlist or a backup): on the phone its media library or its
     *  storage; on a PC a path of its own kind (C:\… on Windows, /… on Linux), never the phone's. */
    fun thisDevices(file: String, android: Boolean, windows: Boolean): Boolean {
        val p = file.replace('\\', '/').removePrefix("file://")
        val phone = p.startsWith("content:") || p.startsWith("/storage/") || p.startsWith("/sdcard/") || p.startsWith("/data/")
        if (android) return phone
        if (phone) return false
        return if (windows) DRIVE.containsMatchIn(p) || p.startsWith("//") else p.startsWith("/") && !DRIVE.containsMatchIn(p)
    }
    private val DRIVE = Regex("^/?[A-Za-z]:/")

    /** Finds your copy of a song whose file is gone (moved, renamed, tagged anew), among songs (your files and
     *  downloads that are there): the same name and lead artist ([looseKey]); else the same name with a leading track
     *  number or signs left out ([dupeNames]: "11 Ruby", "♡") by an artist they share; else, for a file that names no
     *  artist, the one song of yours of that name (none when two artists have a song of it). Null when there's none. */
    fun copyFinder(songs: List<Track>): (Track) -> Track? {
        val byKey = HashMap<Pair<String, String>, Track>()
        val byName = HashMap<String, MutableList<Track>>()
        for (s in songs) {
            looseKey(s).takeIf { it.first.isNotEmpty() }?.let { byKey.putIfAbsent(it, s) }
            for (n in dupeNames(s)) byName.getOrPut(n) { ArrayList() } += s
        }
        fun artists(t: Track) = looseArtists(t.copy(artist = t.artist.replace(';', ','))) + looseKey(t).second
        return finder@{ t ->
            looseKey(t).takeIf { it.first.isNotEmpty() }?.let { k -> byKey[k]?.takeIf { it.id != t.id }?.let { return@finder it } }
            val named = dupeNames(t).flatMap { byName[it].orEmpty() }.filter { it.id != t.id }.distinctBy { it.id }
            if (named.isEmpty()) return@finder null
            if (noArtistNamed(t.artist)) {
                val leads = named.filter { !noArtistNamed(it.artist) }.ifEmpty { named }
                return@finder leads.takeIf { l -> l.map { looseKey(it).second }.distinct().size == 1 }?.first()
            }
            val mine = artists(t)
            named.firstOrNull { s -> artists(s).any { it in mine } }
        }
    }

    /** Whether so many of this device's song files in your lists are gone at once that it's more likely a folder isn't
     *  there for now (a memory card out, a drive not plugged in, the music not read yet) than songs you let go: then
     *  none is taken out of your lists or made a stream. */
    fun tooManyGone(gone: Int, files: Int): Boolean = gone > 25 && gone * 5 > files

    fun goneText(linked: Int, streamed: Int, removed: Int): String = listOfNotNull(
        "$linked linked to your copies".takeIf { linked > 0 },
        "$streamed now stream".takeIf { streamed > 0 },
        "$removed taken out (not on this device or streaming)".takeIf { removed > 0 },
    ).joinToString(", ").let { "Songs whose files are gone, in your playlists, queue and favorites: $it" }

    fun movedText(n: Int): String = "Found $n moved song${if (n != 1) "s" else ""} and linked them in your playlists"
    fun matchedText(n: Int): String = "Matched $n song${if (n != 1) "s" else ""} from your backup to your music here"
    fun genresText(n: Int): String = "Genres added to $n download${if (n != 1) "s" else ""}"
}
