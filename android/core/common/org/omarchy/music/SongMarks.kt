package org.omarchy.music

/** What you set on a song yourself: a favorite (the star), and its own volume (Volume…), on one output or on all.
 *  The stars are kept by [Favorites], the volumes by [SongGains]; this is what the screens ask for, with what it
 *  tells you. [Music] keeps the names the screens call and hands on. */
object SongMarks {
    private fun toast(text: String) = Music.toast(text)
    private fun what(tracks: List<Track>) = if (tracks.size == 1) "“${tracks[0].title}”" else "${tracks.size} songs"

    /** What starring or unstarring says. */
    fun favoriteText(tracks: List<Track>, star: Boolean): String = if (star) "${what(tracks)} in your favorites" else "${what(tracks)} out of your favorites"

    /** What Volume… says: db is the change kept now (0: none), where the output's name ("": every output). */
    fun gainText(tracks: List<Track>, db: Double, where: String): String {
        val on = if (where.isEmpty()) "" else " on $where"
        return if (db == 0.0) "${what(tracks)} back to normal volume$on" else "${what(tracks)} at ${"%+.1f".format(db)} dB$on"
    }

    /** Star the songs, or take the star off (on; null: the other way from the first one's). SoundCloud's songs stay
     *  out of your favorites: one of them alone is liked (or not) on SoundCloud instead. */
    fun favorite(tracks: List<Track>, on: Boolean? = null) {
        if (tracks.any { it.source == SoundCloud.KEY }) {   // its own service: liked on SoundCloud, not mixed into your favorites
            val sc = tracks.filter { it.source == SoundCloud.KEY }
            val src = Music.sources[SoundCloud.KEY]
            if (sc.size == 1 && src?.signedIn == true) {
                val t0 = sc[0]; val like = t0.id !in Music.liked
                Music.liked = if (like) Music.liked + t0.id else Music.liked - t0.id
                Music.bg({ src.rate(t0, like) }) { _, err -> if (err != null) toast("SoundCloud said: ${err.message?.take(80)}") }
                toast(if (like) "Liked on SoundCloud" else "Not liked on SoundCloud any more")
            } else toast("SoundCloud songs can't go in your favorites: like them on SoundCloud (sign in under Settings › Accounts)")
        }
        val mine = tracks.filter { it.source != SoundCloud.KEY }
        if (mine.isEmpty()) return
        val star = on ?: !Music.favorites.has(mine[0])
        Music.favorites.set(mine, star)
        toast(favoriteText(mine, star))
        Music.musicVersion++
    }

    /** Volume…: db for tracks, on output (the one playing now, by default) or on every output (null). */
    fun gain(tracks: List<Track>, db: Double, output: String? = Music.outputNow.key) {
        if (tracks.isEmpty()) return
        Music.gains.set(tracks, db, output)
        refreshVolume()   // the playing song's own volume now; Dual volume off: the Boost goes or comes back with it
        toast(gainText(tracks, Music.gains.saved(tracks[0], output) ?: 0.0, if (output == null) "" else Music.outLabel(output)))
    }

    /** Songs' own volumes always apply: this re-applies the playing song's. */
    fun refreshVolume() {
        Music.current()?.let { Music.eqProcessor.songGain = gainOf(it) }
        Music.applyEq()
        Music.musicVersion++
    }

    /** The change t plays with here and now (dB): its own for this output, else its every-output one (0: none). */
    fun gainNow(t: Track): Double = Music.gains.get(t, Music.outputNow.key)

    /** The same as what the sound is multiplied by. */
    internal fun gainOf(t: Track) = Math.pow(10.0, gainNow(t) / 20)
}
