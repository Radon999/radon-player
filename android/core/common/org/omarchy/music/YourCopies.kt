package org.omarchy.music

/** Your copy of a song: which of your own files (or downloads) a song named anywhere is: by Last.fm, an import, a
 *  service's list, the queue. Out of `Music.kt` as it was (Phase 13); `Music` keeps the names and hands on. It asks
 *  `Music` only for your music as it is now: the songs saved, your files, the songs played here. */
object YourCopies {
    private val plays get() = Music.plays
    private val lib get() = Music.lib
    private val musicVersion get() = Music.musicVersion
    private fun mySongs() = Music.mySongs()

    /** Song key to your copy of it: saved, in your folders, or played here. */
    fun songIndex(): Map<Pair<String, String>, Track> {
        val mine = HashMap<Pair<String, String>, Track>()
        val copies = HashMap<Pair<String, String>, MutableList<Track>>()
        val byTitle = HashMap<String, MutableList<Track>>()
        for (t in plays.items.values.map { it.track }) { mine[songKey(t)] = t; mine[Stats.statsKey(t.title, t.artist)] = t }
        for (t in mySongs()) {   // yours on the phone win over ones only played (streamed)
            val k = Stats.statsKey(t.title, t.artist)
            mine[songKey(t)] = t
            mine[k] = t
            copies.getOrPut(k) { ArrayList() } += t
            byTitle.getOrPut(k.first) { ArrayList() } += t
        }
        localCopies = copies
        localByTitle = byTitle
        return mine
    }
    /** Your copies of each song (by its Stats key), as [songIndex] last read them. */
    @Volatile var localCopies: Map<Pair<String, String>, List<Track>> = emptyMap()
        private set

    /** Your own copy of a song (file or download) when you have one, else the song as it is: lists built from
     *  plays (Skips, Recently played, favorites) show and edit your file, with its cover and tags, not a streamed copy. */
    fun mineOf(t: Track, byLength: Boolean = true): Track {
        val v = musicVersion
        if (v != mineOfVer) {
            // Built once per change to your music (each song's key worked out once), then one lookup per song;
            // safe from any thread (the DJ builds its sets in the background).
            val all = runCatching { mySongs() }.getOrDefault(emptyList())
            val byKey = HashMap<Pair<String, String>, MutableList<Track>>()
            val byLoose = HashMap<Pair<String, String>, MutableList<Track>>()
            val byCredit = HashMap<Pair<String, String>, MutableList<Track>>()
            val byName = HashMap<String, MutableList<Track>>()
            val byTitle = HashMap<String, MutableList<Track>>()
            for (s in all) {
                val (k, loose) = mineKeys(s)
                byKey.getOrPut(k) { ArrayList() } += s
                byTitle.getOrPut(k.first) { ArrayList() } += s
                byLoose.getOrPut(loose to k.second) { ArrayList() } += s
                // …and under each other artist your file credits ("A & B": B too), for a song named by B alone
                // …a file named with its track number ("11 Ruby"): under its name without it too; and one with no artist
                // in its tags: under its name alone, for a song named by its artist anywhere else
                val bare = withoutTrackNumber(s.title)
                if (bare != s.title) byKey.getOrPut(Stats.statsKey(bare, s.artist)) { ArrayList() } += s
                if (noArtistNamed(s.artist)) for (n in listOf(s.title, bare).distinct()) byName.getOrPut(norm(cleanTitle(n))) { ArrayList() } += s
                if (s.artist.any { it == '&' || it == ',' || it == ';' } || " x " in s.artist || "feat" in s.artist.lowercase() || " ft" in s.artist.lowercase() || " with " in s.artist.lowercase())
                    for (c in creditKeys(s.title, s.artist).drop(1)) byCredit.getOrPut(c) { ArrayList() } += s
            }
            mineOfKey = byKey
            mineOfLoose = byLoose
            mineOfCredit = byCredit
            mineOfId = all.associateBy { it.id }
            mineOfName = byName
            mineOfTitle = byTitle
            mineOfVer = v
        }
        val byKey = mineOfKey
        if (byKey.isEmpty()) return t   // no songs of your own (yet)
        // The very song (a download or a file of yours, by what it is, whatever a list kept of its names): as it is now
        mineOfId[t.id]?.let { same -> return asKept(same, t) }
        val (k, loose) = mineKeys(t)
        // By its name and lead artist; else by its name and any other artist it is credited to (a history says
        // "hard life & BENEE", your file says "BENEE": the same song), either way round
        val copies = byKey[k] ?: mineOfLoose[loose to k.second]
            ?: creditKeys(t.title, t.artist).drop(1).firstNotNullOfOrNull { byKey[it] }
            ?: mineOfCredit[k]
            ?: mineOfName[k.first]   // your file of that name that has no artist in its tags
            // …or named under someone who isn't on your file at all (an unreleased song a history has under an
            // uploader's name): your only song of that name and of that very length
            ?: (if (byLength) sameLength(t.title, t.artist, t.duration, mineOfTitle[k.first]) else null)?.let { listOf(it) }
            ?: return t
        // Already your file: as it is now. A list may have kept it pared down (Repeats keeps a song without its cover,
        // length or format) or from before its tags changed: the file's own record is what is shown, with the marks
        // the list gave it. (Handing back the kept one left a WAV, whose cover isn't inside the file, with no cover.)
        if (!t.file.isNullOrEmpty()) copies.firstOrNull { it.file == t.file }?.let { same -> return asKept(same, t) }
        return bestCopy(copies, t.album)
    }

    /** The copy of a song to play and show: the song itself when it's yours (a file or a download), else your own copy
     *  of it when you have one ([mineOf]: the same name and artist; never another version, so a live or remixed one
     *  stays the service's), else the song as it is. Its marks in the queue (queued, the DJ's set, plays) go with it.
     *  One lookup per song once [mineOf] has read your music. */
    fun localOf(t: Track): Track {
        if (t.isFile || lib.has(t.id)) return t
        // (not by its length alone: what plays must be the song asked for, and a service's song has its own artist right)
        val m = runCatching { mineOf(t, byLength = false) }.getOrNull() ?: return t
        if (m === t || m.id == t.id || !(m.isFile || lib.has(m.id))) return t
        if (versionWords(m.title) != versionWords(t.title)) return t   // "Song (Live)" isn't your "Song"
        return m.copy(queued = t.queued, dj = t.dj, plays = t.plays)
    }

    /** [localOf] for each song of a list (the queue, a DJ set, a radio). */
    fun localsOf(tracks: List<Track>): List<Track> = if (tracks.isEmpty()) tracks else tracks.map { localOf(it) }

    private fun versionWords(title: String): Set<String> = SONG_VERSION.findAll(title).map { it.value.lowercase() }.toSet()

    /** A song's Stats key and loose title, worked out once per name and artist (they take a few regexes each, and
     *  [mineOf] reads thousands of songs again whenever your music changes). */
    private fun mineKeys(t: Track): Pair<Pair<String, String>, String> {
        val id = t.title + "\u0000" + t.artist
        mineKeyMemo[id]?.let { return it }
        if (mineKeyMemo.size > 50_000) mineKeyMemo.clear()
        val made = Stats.statsKey(t.title, t.artist) to looseTitle(t.title)
        mineKeyMemo[id] = made
        return made
    }
    private val mineKeyMemo = java.util.concurrent.ConcurrentHashMap<String, Pair<Pair<String, String>, String>>()
    @Volatile private var mineOfVer = -1
    @Volatile private var mineOfKey: Map<Pair<String, String>, List<Track>> = emptyMap()
    @Volatile private var mineOfLoose: Map<Pair<String, String>, List<Track>> = emptyMap()
    @Volatile private var mineOfCredit: Map<Pair<String, String>, List<Track>> = emptyMap()
    @Volatile private var mineOfId: Map<String, Track> = emptyMap()
    @Volatile private var mineOfName: Map<String, List<Track>> = emptyMap()
    @Volatile private var mineOfTitle: Map<String, List<Track>> = emptyMap()

    /** Of your songs of one name, the one that is this recording by its length ([SameLengthRules]); the length is the
     *  song's own when it has one, else what your Apple Music history says its plays ran to. */
    private fun sameLength(title: String, artist: String, secs: Double, named: List<Track>?): Track? {
        if (named.isNullOrEmpty()) return null
        val want = if (secs > 0) secs else AppleHistory.lengthOf(title, artist)
        return named.getOrNull(SameLengthRules.pick(want, named.map { it.duration }))
    }

    /** Your song as it is now, with the marks a list gave the one it kept (t itself when nothing differs). */
    private fun asKept(now: Track, t: Track): Track =
        if (now === t) t else now.copy(queued = t.queued, dj = t.dj, plays = if (t.plays != 0) t.plays else now.plays).let { if (it == t) t else it }
    @Volatile private var localByTitle: Map<String, List<Track>> = emptyMap()

    private fun bare(a: String) = norm(a).replace(Regex("[\\W_]+"), "")

    /** Of your copies of a song, the one on the album asked for, then the best file (FLAC first). */
    private fun bestCopy(copies: List<Track>, album: String): Track {
        val want = norm(cleanTitle(album))
        val rank = mapOf("flac" to 0, "wav" to 1, "alac" to 1, "m4a" to 2, "aac" to 2, "ogg" to 3, "opus" to 3, "mp3" to 4)
        return copies.minWith(compareBy<Track>({ !(want.isNotEmpty() && norm(cleanTitle(it.album)) == want) },
            { rank[lib.items[it.id]?.format ?: it.file?.substringAfterLast('.')?.lowercase()] ?: 5 }))
    }

    /** A song Last.fm (or Spotify, or Apple Music) named: your file of it when you have one (the same song name
     *  and artist; the one on the same album first), else one played here, else one to be found on Tidal or
     *  YouTube Music when it plays. Call [songIndex] first: it reads your files. sameArtistOnly (an artist's page): never
     *  a file of the same song name on the same album by someone else (its cover and name would show on their page). */
    fun fmSong(name: String, artist: String, image: String, mine: Map<Pair<String, String>, Track>, album: String = "",
               sameArtistOnly: Boolean = false): Track {
        val k = Stats.statsKey(name, artist)
        var copies = localCopies[k].orEmpty()
        if (copies.isEmpty()) {   // the artist written differently: one of the song's artists is one of your file's
            val theirs = splitArtists(artist).map { bare(it) }.filter { it.isNotEmpty() }.toSet()
            copies = localByTitle[k.first].orEmpty().filter { t -> splitArtists(t.artist).any { bare(it) in theirs } }
        }
        if (copies.isEmpty() && album.isNotBlank()) {   // the artist tagged another way still: the same song name on the same album
            val want = norm(cleanTitle(album))
            copies = localByTitle[k.first].orEmpty().filter { norm(cleanTitle(it.album)) == want && (!sameArtistOnly || sameArtist(it.artist, artist)) }
        }
        // …or under someone who isn't on your file at all: your only song of that name and of that very length
        if (copies.isEmpty()) sameLength(name, artist, 0.0, localByTitle[k.first])?.let { copies = listOf(it) }
        if (copies.isNotEmpty()) return bestCopy(copies, album)
        return mine[songKey(Track("", title = name, artist = artist))] ?: mine[k] ?: run {
            val sha = java.security.MessageDigest.getInstance("SHA-1").digest("${norm(artist)}|${norm(name)}".toByteArray())
            Track("lfm-" + sha.joinToString("") { "%02x".format(it) }.take(16), "lastfm", name, artist,
                album = album, thumb = image.takeIf { it.isNotEmpty() && LASTFM_BLANK !in it } ?: SongCovers.known(name, artist))   // one looked up before
        }
    }

    /** Songs a service listed (an artist's top songs): your own copy of each where you have one (its file plays,
     *  its cover shows), matched as Most played matches; the service's song only where you have none. */
    fun yourCopies(tracks: List<Track>): List<Track> {
        if (tracks.isEmpty()) return tracks
        val mine = songIndex()
        fun yours(t: Track) = t.isFile || t.file != null || lib.has(t.id)
        return tracks.map { t ->
            if (yours(t)) t
            else fmSong(t.title, t.artist, t.thumb, mine, t.album, sameArtistOnly = true).takeIf { yours(it) }?.copy(plays = t.plays) ?: t
        }
    }
}
