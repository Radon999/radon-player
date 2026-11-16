package org.omarchy.music

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Your music as the tabs show it: every song you have, by album, by genre, by folder, by artist, and which tab
 *  (Albums, EPs, Singles) a release is in. The rules of the tabs are in [ReleaseTabRules]. [Music] keeps the names
 *  the screens call and hands on. */
object YourMusic {
    private val lib get() = Music.lib
    private val local get() = Music.local

    /** Every song you have: downloaded here (once each) and in your folders. */
    fun songs(): List<Track> {
        val seen = HashSet<String>()
        val saved = lib.items.values.sortedByDescending { it.added }.filter { seen.add(it.file) }.map { it.track() }
        return saved + local.files.filter { it.file !in seen }
    }

    /** Your songs by album, the albums in name order (each with its songs in their order). */
    fun albums(): List<Pair<Album, List<Track>>> = songs().groupBy { Covers.albumKey(it) }.values.map { songs ->
        val t = songs.first()
        val artist = songs.groupingBy { it.artist.split(",")[0].trim() }.eachCount().maxByOrNull { it.value }?.key ?: t.artist
        Album("album:" + Covers.albumKey(t), t.album.ifEmpty { "Unknown album" }, artist, count = songs.size.toString()) to songs
    }.sortedBy { norm(it.first.title) }

    /** Which tab a release of yours is in: "album", "ep" or "single", by its album tag as taggers write it ("… - EP",
     *  "… - Single", "… (Single)"); any other is an album, however few of its songs you have (Albums › Low song count
     *  albums). Songs in an Artists folder (Artists/<Artist>/<Title - EP>…) go by their folder; songs with no album at
     *  all (single) are singles. */
    fun releaseKind(title: String, songs: List<Track>, single: Boolean = false): String {
        ReleaseTabRules.byHand(songs.mapNotNull { Music.tabPicks[tabKey(it)] }, songs.size)?.let { return it }   // put there by hand (Tagger › Tab)
        // Where most of its songs are, then what its album tag says: a folder named as an EP or a single decides, a
        // plainly named one leaves it to the tag (releaseKindOf)
        val folder = ReleaseTabRules.most(songs.mapNotNull { folderKind(folderOf(it)) })
        return releaseKindOf(folder, ReleaseTabRules.ofTitle(title, single))
    }

    /** A song's key for Tagger › Tab: its file, written one way. */
    fun tabKey(t: Track): String = ReleaseTabRules.key(t.file ?: lib.items[t.id]?.file ?: t.id)

    /** Which tab one song of yours sits in ("album", "ep", "single"), as its release is sorted; null if it isn't yours. */
    fun tabOf(t: Track): String? {
        if (!t.isFile && !lib.has(t.id)) return null
        Music.tabPicks[tabKey(t)]?.let { return it }
        val folder = folderKind(folderOf(t))
        return releaseKindOf(folder, ReleaseTabRules.ofSong(t.album, folder))
    }

    /** Tagger › Tab: put the songs in Albums, EPs or Singles whatever their tags say (null: back to the usual way). */
    fun setTab(tracks: List<Track>, kind: String?) {
        for (t in tracks) if (kind == null) Music.tabPicks.remove(tabKey(t)) else Music.tabPicks[tabKey(t)] = kind
        Music.saveState(); Music.musicVersion++
        Music.toast(ReleaseTabRules.movedText(kind))
    }

    /** A song's genre, as its file says (songs without one: Unknown genre). */
    fun genreOf(t: Track): String {
        val e = lib.items[t.id]
        val id = LocalFiles.idOf(t.file ?: e?.file)
        return id?.let { genreFix[it] }?.takeIf { it.isNotBlank() }   // written here since the last library read
            ?: e?.genre?.takeIf { it.isNotBlank() }                    // a download: its own genre
            ?: id?.let { local.genres[it] }?.takeIf { it.isNotBlank() } ?: ReleaseTabRules.UNKNOWN_GENRE
    }

    /** Genres written into files here, by file: the phone's media library can take a while to notice. */
    private val GENRE_FIX_SER = MapSerializer(String.serializer(), String.serializer())
    private val genreFix: MutableMap<String, String> by lazy {
        java.util.concurrent.ConcurrentHashMap(runCatching { JSON.decodeFromString(GENRE_FIX_SER, Prefs.text("genre_fix").ifEmpty { "{}" }) }.getOrDefault(emptyMap()))
    }
    internal fun noteGenre(file: String, g: String) {
        val id = LocalFiles.idOf(file) ?: return
        genreFix[id] = g
        Prefs.setText("genre_fix", JSON.encodeToString(GENRE_FIX_SER, genreFix.toMap()))
    }

    /** Your songs by genre, the most songs first. */
    fun genres(): List<Pair<String, List<Track>>> = ReleaseTabRules.genreOrder(songs().groupBy { genreOf(it) }.toList())

    /** A song's folder on the phone ("Music/Album/"). */
    fun folderOf(t: Track): String {
        val f = t.file ?: lib.items[t.id]?.file
        return local.paths[f] ?: LocalFiles.idOf(f)?.let { local.allPaths[it] } ?: LocalFiles.DOWNLOADS
    }

    /** Your songs by folder, in folder order. */
    fun folderSongs(): List<Pair<String, List<Track>>> = songs().groupBy { folderOf(it) }.toList().sortedBy { it.first.lowercase() }

    /** Your songs by the artist called name: saved, in your folders or playlists, or played. */
    fun songsBy(name: String): List<Track> {
        val want = norm(name)
        val seen = HashSet<Pair<String, String>>()
        return (songs() + Music.lists.items.flatMap { it.tracks } + Music.plays.recent(2000)).filter { t ->
            want in splitArtists(t.artist).map { norm(it) } && seen.add(songKey(t))
        }
    }

    /** The artist's songs you played or have, each with how often you played it (every play added
     *  up: see [Stats]): the counted ones, then the rest of yours. */
    fun artistSongs(name: String): List<Track> {
        val mine = Music.songIndex()
        val seen = HashSet<Pair<String, String>>()
        val out = ArrayList<Track>()
        for (s in Stats.artistSongs(name)) {
            val t = Music.fmSong(s.title, s.artist, s.thumb, mine, s.album, sameArtistOnly = true).copy(plays = s.plays)
            if (seen.add(songKey(t))) out += t
        }
        val here = Music.plays.items.values.associate { songKey(it.track) to it.plays }
        for (t0 in songsBy(name)) {
            // A playlist's or a played stream's copy of a song you have: yours instead (its file plays, its cover shows).
            val t = if (t0.isFile || t0.file != null || lib.has(t0.id)) t0
                else Music.fmSong(t0.title, t0.artist, t0.thumb, mine, t0.album, sameArtistOnly = true).takeIf { it.isFile || it.file != null || lib.has(it.id) } ?: t0
            if (seen.add(songKey(t))) out += t.copy(plays = here[songKey(t0)] ?: here[songKey(t)] ?: 0)
        }
        return out
    }
}
