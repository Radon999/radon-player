package org.omarchy.music

/** Discovery playlists: an artist's (or a new release's) songs you haven't got, heard one by one. They come in
 *  pairs: "… Discovery playlist" holds the songs to hear, "… discovered" the ones you heard to the end, to
 *  download or let go. The playlists themselves are [Playlists]' (each carries a [Discovery]: whose it is, and
 *  which of the pair); [Music] keeps the names the screens call and hands on to here. */
object Discoveries {
    private val lists get() = Music.lists
    private fun toast(text: String) = Music.toast(text)

    /** Artists whose list is being made (the screen shows "Finding songs…"). */
    val making = androidx.compose.runtime.mutableStateListOf<String>()

    /** (artist, the discovery playlist, the discovered one) of every artist you're discovering. */
    fun pairs(): List<Triple<String, Playlist?, Playlist?>> =
        lists.all().filter { it.discovery != null }.groupBy { norm(it.discovery!!.artist) }.values.map { ps ->
            Triple(ps.first().discovery!!.artist, ps.firstOrNull { it.discovery!!.role == "discover" },
                ps.firstOrNull { it.discovery!!.role == "discovered" })
        }

    /** A discovery playlist for artist: every song of theirs the services have that you don't, album by
     *  album ("Artist Discovery playlist"), and one for the songs you hear to the end ("Artist discovered"). */
    fun make(artist0: String) {
        val artist = artist0.split(",")[0].trim()
        if (artist.isEmpty() || norm(artist) in making) return
        making += norm(artist)
        toast("Finding $artist's songs you haven't got…")
        Music.bg({
            val info = Music.artistInfo(artist, null) ?: error("no service knows $artist")
            val src = Music.sources[info.source]
            val have = HashSet(Music.songIndex().keys)
            pairs().firstOrNull { norm(it.first) == norm(artist) }?.let { (_, d, f) ->
                (d?.tracks.orEmpty() + f?.tracks.orEmpty()).forEach { have += songKey(it) } }
            val name = info.name.ifEmpty { artist }
            val yours = yourTitles(Music.mySongs(), listOf(artist, name))
            Triple(name, notYours(info, have, yours) { al -> runCatching { src?.album(al.id) ?: emptyList() }.getOrDefault(emptyList()) }, yours)
        }) { res, err ->
            making.remove(norm(artist))
            if (res == null) { toast("Couldn't make it: ${err?.message?.take(100)}"); return@bg }
            val (name, songs, yours) = res
            // Made again: songs put in it before that are yours after all (under another name of the artist's) go.
            pairs().firstOrNull { norm(it.first) == norm(name) }?.second?.let { d ->
                d.tracks.filter { SameArtist.title(it.title) in yours }.map { it.id }.toSet().takeIf { it.isNotEmpty() }?.let { lists.remove(d.id, it) } }
            val (disc, n) = keep(name, songs)
            toast(if (n > 0) "“${disc.name}”: $n song${if (n != 1) "s" else ""} of $name's you haven't got" else "You have every song of $name's the services list")
            Music.musicVersion++
        }
    }

    /** The artist's songs that aren't among have (song keys: yours, and the ones in their discovery playlists
     *  already): each album's in the service's order (albumSongs reads one), then their top songs; no song twice;
     *  a song the service gave no album gets its album's name. yourTitles ([yourTitles]): the names of your songs by
     *  this artist under any name of theirs; a song named so is yours, whoever the service credits it to. */
    fun notYours(info: ArtistInfo, have: Set<Pair<String, String>>, yourTitles: Set<String> = emptySet(), albumSongs: (Album) -> List<Track>): List<Track> {
        val seen = HashSet<Pair<String, String>>()
        val out = ArrayList<Track>()
        fun new(t: Track): Boolean { val k = songKey(t); return k !in have && SameArtist.title(t.title) !in yourTitles && seen.add(k) }
        for (al in info.albums)
            for (t in albumSongs(al)) if (new(t)) out += if (t.album.isEmpty()) t.copy(album = al.title) else t
        for (t in info.top) if (new(t)) out += t
        return out
    }

    /** The names ([SameArtist.title]) of your songs by the artist, under any of these names of theirs or one like them
     *  ("Forrest" in your files is the services' "Forrest Frank": [SameArtist.alias]). */
    fun yourTitles(mine: List<Track>, names: List<String>): Set<String> =
        mine.filter { t -> names.any { SameArtist.alias(t.artist, it) } }.mapTo(HashSet()) { SameArtist.title(it.title) }

    /** The songs go into name's discovery playlist; it and its discovered one are made if they aren't there.
     *  (The discovery playlist, how many songs were new to it.) */
    fun keep(name: String, songs: List<Track>): Pair<Playlist, Int> {
        val pair = pairs().firstOrNull { norm(it.first) == norm(name) }
        val disc = pair?.second ?: lists.create("$name Discovery playlist", discovery = Discovery(name, "discover"))
        if (pair?.third == null) lists.create("$name discovered", discovery = Discovery(name, "discovered"))
        return disc to lists.add(disc.id, songs)
    }

    /** A release's discovery playlist's name: “Persona (Extended) from half•alive”. */
    fun releaseName(r: NewReleases.Release) = "${r.title} from ${r.artist}"

    /** A discovery playlist (and its discovered one) for each release, with the songs picked. They work as an
     *  artist's do: a song heard to the end moves to the discovered one. */
    fun makeForReleases(chosen: List<Pair<NewReleases.Release, List<Track>>>, quiet: Boolean = false): List<String> {
        val made = ArrayList<String>()
        for ((r, songs) in chosen) {
            val name = releaseName(r)
            keep(name, songs)
            made += name
        }
        if (made.isEmpty()) return made
        if (!quiet) toast(if (made.size == 1) "Made “${made[0]} Discovery playlist”" else "Made ${made.size} discovery playlists")
        Music.musicVersion++
        return made
    }

    /** A song heard to the end: out of its discovery playlist, into the discovered one. */
    fun heardOut(t: Track) {
        for ((_, d, f) in pairs()) if (d != null && f != null && d.tracks.any { it.id == t.id }) {
            lists.remove(d.id, setOf(t.id))
            lists.add(f.id, listOf(t))
            Music.musicVersion++
            return
        }
    }

    /** The discovered list's review: download some songs, take some out. */
    fun review(id: String, download: Set<String>, remove: Set<String>) {
        val pl = lists.get(id) ?: return
        pl.tracks.filter { it.id in download }.takeIf { it.isNotEmpty() }?.let { Music.download(it) }
        if (remove.isNotEmpty()) lists.remove(id, remove)
        Music.musicVersion++
    }
}
