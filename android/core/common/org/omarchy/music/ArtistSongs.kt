package org.omarchy.music

/** What is an artist's among your music (their songs, the ones they're featured on, their albums), and Create
 *  artist playlist. [Music] keeps the names the screens call and hands on. */
object ArtistSongs {
    private fun toast(text: String) = Music.toast(text)
    private fun bare(a: String) = norm(a).replace(Regex("[\\W_]+"), "")

    /** (main thread only) Artists whose playlist is being made. */
    private val making = HashSet<String>()

    /** Whether the song is the artist's: one of its credited artists (featured ones too, in the credit or the title's
     *  "feat."), or one merged into them (Merge into…: picks, a credit's key to the name its plays count for). */
    fun credits(t: Track, name: String, picks: Map<String, String> = Music.artistPicks): Boolean {
        val want = bare(name)
        if (want.isEmpty()) return false
        return (splitArtists(t.artist) + ownArtists(t.artist) + featured(t.title)).any { n ->
            bare(n) == want || picks[pairKey(n)]?.let { it.isNotEmpty() && bare(it) == want } == true
        }
    }

    /** Every song of the artist's you have: downloaded or in your folders, the ones they're featured on too. */
    fun ownSongs(name: String): List<Track> = Music.mySongs().filter { credits(it, name) }

    /** Your albums (as Albums groups them) that are the artist's: billed to them, or most of their songs theirs. */
    fun ownAlbums(name: String): List<Pair<Album, List<Track>>> = Music.albums().filter { (al, songs) -> isTheirs(al, songs, name) }

    /** Whether an album is the artist's: billed to them, or more than half of its songs theirs. */
    fun isTheirs(al: Album, songs: List<Track>, name: String, picks: Map<String, String> = Music.artistPicks): Boolean =
        bare(al.sub) == bare(name) || songs.count { credits(it, name, picks) } * 2 > songs.size

    /** An artist playlist's order: by album (songs with none last), then each song's number on it (9999: none), then
     *  its name; each song once. */
    fun inOrder(songs: List<Pair<Track, Int>>): List<Track> =
        songs.sortedWith(compareBy<Pair<Track, Int>>({ it.first.album.isBlank() }, { norm(it.first.album) }, { it.second },
            { norm(it.first.title) })).map { it.first.slim() }.distinctBy { it.id }

    /** What making it says at the end. */
    fun madeText(name: String, songs: Int, updated: Boolean, unreachable: Boolean): String =
        "${if (updated) "Updated" else "Made"} the playlist $name ($songs song${if (songs != 1) "s" else ""})" +
            if (unreachable) ": only yours, the services couldn't be reached" else ""

    /** Create artist playlist: a playlist named after the artist with every song of theirs you have, by album, then
     *  track number (from the files' tags), then title. With Settings › Artist playlists include songs you don't have,
     *  their songs on the streaming service too (top songs and every album's, ones you have left out by title). A
     *  playlist of that exact name already there is brought up to date instead of made again. */
    fun makePlaylist(name0: String) {
        val name = name0.trim()
        if (name.isEmpty() || !making.add(norm(name))) return
        val withStreams = Prefs.on(Music.ARTIST_LISTS_ALL)
        if (withStreams) toast("Making the playlist $name: looking up their songs…")
        Music.bg({
            val out = ArrayList<Pair<Track, Int>>()   // each song and its number on its album (9999: none)
            for (t in ownSongs(name)) {
                val f = t.file ?: Music.lib.items[t.id]?.file
                val no = f?.let { runCatching { trackNo(FileTags.read(Music.ctx, it)?.get("tracknumber")) }.getOrNull() } ?: 9999
                out += t to no
            }
            var unreachable = false
            if (withStreams) {
                val have = out.mapTo(HashSet()) { norm(cleanTitle(it.first.title)) }
                fun take(t: Track, no: Int) { if (have.add(norm(cleanTitle(t.title)))) out += t to no }
                val info = try { Music.artistInfo(name, null) } catch (e: Exception) { null }
                if (info == null) unreachable = true
                else {
                    val src = Music.sources[info.source]
                    info.albums.forEachIndexed { i, al ->
                        if (i > 0 && i % 10 == 0) toast("Making the playlist $name: album $i of ${info.albums.size}…")
                        val songs = runCatching { src?.album(al.id).orEmpty() }.getOrDefault(emptyList())
                        songs.forEachIndexed { j, t -> take(if (t.album.isEmpty()) t.copy(album = al.title) else t, j + 1) }
                    }
                    info.top.forEach { take(it, 9999) }
                }
            }
            inOrder(out) to unreachable
        }) { res, err ->
            making.remove(norm(name))
            if (res == null) { toast("Couldn't make the playlist: ${err?.message?.take(100)}"); return@bg }
            val (songs, unreachable) = res
            if (songs.isEmpty()) {
                toast(if (unreachable) "You have no songs of $name's, and the services couldn't be reached" else "You have no songs of $name's yet")
                return@bg
            }
            val old = Music.lists.named(name)
            val pl = old ?: Music.lists.create(name)
            Music.lists.replaceTracks(pl.id, songs)
            Music.musicVersion++
            toast(madeText(name, songs.size, updated = old != null, unreachable = unreachable))
        }
    }
}
