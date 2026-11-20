package org.omarchy.music

/** How long a song is, by what your histories say its plays ran to: Apple Music's exact plays first, then the history
 *  you imported from Spotify (each play's time played: a play let run to its end is the song's length,
 *  [SameLengthRules.usual]). It tells your file of a song a history has under another of its artists (your file says
 *  "Young Dolph", the history "Key Glock": the same name and the very same length). */
object HistoryLengths {
    @Volatile private var spotify: Map<Pair<String, String>, Double> = emptyMap()
    @Volatile private var read = false
    @Volatile private var reading = false
    /** A test's lengths in place of the histories' (null in the app). */
    @Volatile internal var standIn: Map<Pair<String, String>, Double>? = null

    /** The song's length in seconds, 0 when no history tells. No reading here: an import not read yet is read in the
     *  background, and your music is looked over again once it is ([Music.musicVersion]). */
    fun of(title: String, artist: String): Double {
        standIn?.let { return it[statsKey(title, artist)] ?: 0.0 }
        val apple = AppleHistory.lengthOf(title, artist)
        if (apple > 0) return apple
        if (!read) later()
        return spotify[statsKey(title, artist)] ?: 0.0
    }

    /** (blocking, off the main thread) Every history's lengths read, if they weren't yet. */
    fun ensure() {
        AppleHistory.ensureLengths()
        if (!read) readSpotify()
    }

    /** The imported history changed: its lengths are read again when next asked for. */
    fun forget() { read = false }

    @Synchronized private fun readSpotify() {
        if (read) return
        spotify = runCatching {
            SpotifyHistory.plays.groupBy({ statsKey(it.title, it.artist) }, { it.ms })
                .mapValues { SameLengthRules.usual(it.value) }.filterValues { it > 0 }
        }.getOrDefault(emptyMap())
        read = true
    }

    private fun later() {
        if (reading) return
        reading = true
        runCatching { Music.bg({ readSpotify(); spotify.isNotEmpty() }) { some, _ -> reading = false; if (some == true) Music.musicVersion++ } }
            .onFailure { reading = false }
    }
}
