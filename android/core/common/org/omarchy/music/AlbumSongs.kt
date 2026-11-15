package org.omarchy.music

/** An album (or EP) of yours as its page shows it: its songs as they are now, in the album's own order (each
 *  file's track number, read once and kept). */
object AlbumSongs {
    /** Track numbers read already, by file: read off the main thread, by several pages at once. */
    private val numbers = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** In a test: a song's track number, in place of reading its file's tags (null in the app). */
    @Volatile var numberStandIn: ((Track) -> Int)? = null

    /** The same for the disc a song is on (0: its tags don't say). */
    private val discs = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private fun fileOf(t: Track): String? = (t.file ?: Music.lib.items[t.id]?.file)?.takeIf { it.isNotEmpty() }

    /** (blocking) The song's number on its album, from its file's tags; 9999 when it has none or isn't a file here. */
    fun number(t: Track): Int {
        numberStandIn?.let { return it(t) }
        val f = fileOf(t) ?: return 9999
        return numbers.getOrPut(f) { runCatching { trackNo(FileTags.read(Music.ctx, f)?.get("tracknumber")) }.getOrDefault(9999) }
    }

    /** (blocking) The disc the song is on, from its file's tags ("2", "2/3"); 0 when they don't say. */
    fun disc(t: Track): Int {
        if (numberStandIn != null) return 0
        val f = fileOf(t) ?: return 0
        return discs.getOrPut(f) { runCatching { FileTags.disc(Music.ctx, f) }.getOrDefault(0) }
    }

    /** (blocking) The songs in the album's order: disc by disc, each by track number ([AlbumOrder.sorted]). */
    fun inOrder(tracks: List<Track>): List<Track> = AlbumOrder.sorted(tracks, ::disc, ::number)

    /** (blocking) An album page's songs now, in order. was: the songs it opened with. An album of yours (its id is one
     *  of [Music.albums]') is read again from your songs, so a song downloaded since is in it and one deleted is gone;
     *  any other list of songs keeps its songs. reread (Refresh): the track numbers are read from the files again. */
    fun now(albumId: String, was: List<Track>, reread: Boolean = false): List<Track> {
        val songs = if (albumId.startsWith("album:")) runCatching { Music.albums().firstOrNull { it.first.id == albumId }?.second }.getOrNull() ?: was else was
        if (reread) songs.forEach { t -> fileOf(t)?.let { numbers.remove(it); discs.remove(it) } }
        return inOrder(songs)
    }
}
