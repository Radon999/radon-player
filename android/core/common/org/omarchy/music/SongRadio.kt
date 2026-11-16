package org.omarchy.music

/** Songs like a song (its radio, and what plays on when the queue runs out): asked of the song's own service first,
 *  then the one you browse, then YouTube Music and Tidal. */
object SongRadio {
    /** (blocking) Songs like t, from the first service that has some. If none has and one failed, its error. */
    internal fun tracks(t: Track): List<Track> {
        var last: Throwable? = null
        for (key in RadioRules.order(t.source, Music.source)) {
            val src = Music.sources[key] ?: continue
            if (RadioRules.skips(key, src.signedIn)) continue
            val found = try { src.radio(t) } catch (e: Exception) { last = e; continue }
            if (found.isNotEmpty()) return found
        }
        last?.let { throw it }
        return emptyList()
    }
}
