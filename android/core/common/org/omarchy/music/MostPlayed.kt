package org.omarchy.music

/** Most played's lists: the songs, artists and albums you played most over a period. Every play is added up by
 *  [Stats] (Last.fm's, your Spotify and Apple history's and, without Last.fm, the plays here); this turns the
 *  counts into your own songs where you have them ([YourCopies]) and adds them up by artist and by album. Until
 *  the counts are in, the plays here stand in. Every song you played is there, whether or not you have it on this
 *  device (the "only songs on this device" choice was taken out in 5.3.1: your whole history, always). The period you
 *  chose is [Music]'s, which hands on to here. */
object MostPlayed {
    data class TopArtist(val name: String, val plays: Int, val thumb: String)
    data class TopAlbum(val key: String, val title: String, val artist: String, val plays: Int, val tracks: List<Track>)

    /** The songs last made and the counts they were made from: kept until the counts change. One value, so a
     *  reader off the main thread (the DJ) never sees one period's songs with another's counts. */
    @Volatile private var made: Pair<List<Stats.Song>, List<Track>>? = null

    /** What you chose changed (the period): the songs are made again. */
    fun forget() { made = null }

    /** Whether Most played's counts come from somewhere other than the plays here. */
    val fromStats get() = Stats.songs.isNotEmpty() && (LastFm.connected || SpotifyHistory.count > 0 || OtherPlays.count > 0 || AppleHistory.count > 0)

    fun songs(period: String = "all"): List<Track> {
        val counts = if (period == "all") Stats.songs else Stats.periods[period] ?: emptyList()
        if (Stats.songs.isEmpty()) return if (period == "all") Music.plays.top(Int.MAX_VALUE) else emptyList()   // Most played: every song
        made?.let { if (it.first === counts) return it.second }
        val songs = songsOf(counts, Music.songIndex())
        made = counts to songs
        return songs
    }

    /** The counted songs as songs to play, most played first: your own copy where you have one (mine: [YourCopies.songIndex]),
     *  and the ones you have no copy of as themselves. */
    fun songsOf(counts: List<Stats.Song>, mine: Map<Pair<String, String>, Track>): List<Track> {
        // Two names for one song of yours (a remaster's, a feat.'s): one row, its plays added up.
        val at = LinkedHashMap<String, Track>()
        for (c in counts) {
            var t = YourCopies.fmSong(c.title, c.artist, c.thumb, mine, c.album)
            // A song is its album's too: plays on an album your copy isn't on are another song's (one you haven't got),
            // never added to your copy from another album
            if (c.album.isNotBlank() && t.album.isNotBlank() && !sameAlbum(t.album, c.album)) t = standIn(c)
            at[t.id] = at[t.id]?.let { it.copy(plays = it.plays + c.plays) } ?: t.copy(plays = c.plays)
        }
        return at.values.sortedByDescending { it.plays }
    }

    private fun sameAlbum(a: String, b: String) = PlaysApart.versionOf(a, listOf(listOf(PlaysApart.albumKey(b)))) != null

    /** A song on an album you haven't got: itself, found on your services when it plays. */
    private fun standIn(c: Stats.Song): Track {
        val sha = java.security.MessageDigest.getInstance("SHA-1").digest("${norm(c.artist)}|${norm(c.title)}|${PlaysApart.albumKey(c.album)}".toByteArray())
        return Track("lfm-" + sha.joinToString("") { "%02x".format(it) }.take(16), "lastfm", c.title, c.artist, album = c.album,
            thumb = c.thumb.takeIf { it.isNotEmpty() && LASTFM_BLANK !in it } ?: SongCovers.known(c.title, c.artist))
    }

    /** Most played artists over a period: every play of theirs (every source with a time), by lead artist. All time: the Artists tab's. */
    fun artists(period: String = "all"): List<TopArtist> {
        if (period == "all") return Stats.artists.map { TopArtist(it.name, it.plays, it.thumb) }
        return artistsOf(Stats.periods[period].orEmpty(), runCatching { Music.artistResolver() }.getOrDefault { c: String -> c })
    }

    /** The counted songs added up by lead artist (resolve: a credit's lead artist, "" when it can't say). */
    fun artistsOf(counts: List<Stats.Song>, resolve: (String) -> String): List<TopArtist> {
        val out = LinkedHashMap<String, TopArtist>()
        val called = HashMap<String, HashMap<String, Int>>()
        for (s in counts) {
            val lead = resolve(s.artist).ifEmpty { splitArtists(s.artist).firstOrNull() ?: s.artist }
            val k = resolvedKey(lead)
            val x = out[k]
            out[k] = if (x == null) TopArtist(lead, s.plays, s.thumb) else x.copy(plays = x.plays + s.plays, thumb = x.thumb.ifEmpty { s.thumb })
            called.getOrPut(k) { HashMap() }.merge(lead, s.plays, Int::plus)
        }
        // "Quinn XCII & Jeremy Zucker" is Quinn XCII's: named as they are on their own.
        return out.map { (k, x) -> x.copy(name = called[k]?.maxByOrNull { it.value }?.key ?: x.name) }.sortedByDescending { it.plays }
    }

    /** Most played albums: Most played's songs (the same period) added up by album. */
    fun albums(period: String = "all"): List<TopAlbum> {
        val songs = songs(period)
        if (songs.isNotEmpty() && YourCopies.localCopies.isEmpty()) Music.songIndex()
        return albumsOf(songs, YourCopies.localCopies)
    }

    /** Songs (each with its plays) added up by album. An album and its deluxe, extended or anniversary edition are
     *  one, under the edition's name; an album split by featured artists ("half•alive & The Walters") is one; a
     *  single's plays count for the album it's on, when you have that album's copy of the song (copies: your copies
     *  of each song, [YourCopies.localCopies]). The desktop's top_albums. */
    fun albumsOf(songs: List<Track>, copies: Map<Pair<String, String>, List<Track>>): List<TopAlbum> {
        class G(val key: String) { val names = HashMap<String, Int>(); val credits = HashMap<String, Int>(); var plays = 0
            val tracks = LinkedHashMap<Pair<String, String>, Track>() }
        val groups = LinkedHashMap<String, G>()
        for (t in songs) {
            var album = t.album.trim(); var credit = t.artist
            if (album.isEmpty()) continue
            val sk = Stats.statsKey(t.title, t.artist)
            if (isSingle(album, t.title)) copies[sk].orEmpty().firstOrNull { it.album.isNotBlank() && !isSingle(it.album, it.title) }?.let {
                album = it.album.trim(); credit = it.artist
            }
            val (base, edition) = albumEdition(album)
            val key = norm(cleanTitle(albumWithoutPart(base))) + "|" + artistKey(credit)   // its parts (Pt. 1, Pt. 2) one too
            val g = groups.getOrPut(key) { G(key) }
            g.names.merge(album, 1 + if (edition.isNotEmpty()) 1000 else 0, Int::plus)
            g.credits.merge(splitArtists(credit).firstOrNull()?.ifEmpty { null } ?: credit, 1, Int::plus)
            g.plays += t.plays
            g.tracks[sk] = g.tracks[sk]?.let { it.copy(plays = it.plays + t.plays) } ?: t   // the same song on both editions: one
        }
        return groups.values.map { g ->
            TopAlbum(g.key, g.names.maxByOrNull { it.value }!!.key, soloName(g.credits), g.plays, g.tracks.values.toList())
        }.sortedByDescending { it.plays }
    }
}
