package org.omarchy.music

/** Listen later: the songs you put aside to hear another time (see [ListenLaterRules]). It is kept as a playlist of
 *  yours named "Listen later", made the first time you add to it, so it is saved, backed up and shown with your
 *  other playlists too, and can be synced like any of them; the Listen later tab is that playlist's songs. */
object ListenLater {
    private const val ID = "listen_later_id"

    /** The playlist behind it (the one remembered, else one of that name), or null: nothing was ever added. */
    fun playlist(): Playlist? =
        Prefs.text(ID).takeIf { it.isNotEmpty() }?.let { Music.lists.get(it) } ?: Music.lists.named(ListenLaterRules.NAME)

    /** Its songs, the last added first. */
    fun tracks(): List<Track> = playlist()?.tracks.orEmpty().asReversed()

    fun has(t: Track): Boolean = playlist()?.tracks?.any { it.id == t.id } == true

    /** Put songs on it (the ones on it already are left where they are), and say how many went on. */
    fun add(tracks: List<Track>) {
        if (tracks.any { it.source == SoundCloud.KEY }) Music.toast("SoundCloud songs can't go on Listen later: like them on SoundCloud instead")
        val ok = tracks.filter { it.source != SoundCloud.KEY }.distinctBy { it.id }
        if (ok.isEmpty()) return
        val pl = playlist() ?: Music.lists.create(ListenLaterRules.NAME)
        Prefs.setText(ID, pl.id)
        val new = Music.lists.add(pl.id, ok)
        Music.toast(ListenLaterRules.addedText(ok.size, new))
        Music.musicVersion++
    }

    /** Take songs off it (heard, or not wanted after all). */
    fun remove(tracks: List<Track>) {
        val pl = playlist() ?: return
        val ids = tracks.map { it.id }.toSet()
        val n = pl.tracks.count { it.id in ids }
        if (n == 0) return
        Music.lists.remove(pl.id, ids)
        Music.toast(ListenLaterRules.removedText(n))
        Music.musicVersion++
    }
}
