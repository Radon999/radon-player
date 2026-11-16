package org.omarchy.music

/** Linux' media controls (MPRIS: the keyboard's and a headset's play, next and previous keys, and the desktop's own
 *  "now playing" with its buttons): what the app tells the desktop about what's playing, as plain values. The
 *  talking to the desktop itself (D-Bus) is the PC's side. */
object MprisRules {
    /** What is playing now, as the desktop is told. */
    data class Now(val id: String = "", val title: String = "", val artist: String = "", val album: String = "", val art: String = "",
                   val hasSong: Boolean = false, val playing: Boolean = false, val position: Double = 0.0, val duration: Double = 0.0)

    /** "Playing", "Paused", or "Stopped" with nothing there. */
    fun status(now: Now): String = if (!now.hasSong) "Stopped" else if (now.playing) "Playing" else "Paused"

    /** A time in the desktop's own unit (millionths of a second). */
    fun micros(seconds: Double): Long = (maxOf(0.0, seconds) * 1_000_000).toLong()
    fun seconds(micros: Long): Double = micros / 1_000_000.0

    /** A song's name on the desktop's side: a path of letters, digits and underscores only, whatever its id here. */
    fun trackPath(id: String): String =
        if (id.isEmpty()) "/org/mpris/MediaPlayer2/TrackList/NoTrack"
        else "/org/radonplayer/track/" + id.map { c -> if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9') c.toString() else "_" + c.code.toString(16) }.joinToString("")

    /** The artists of a song, each one: "A, B" is two. */
    fun artists(artist: String): List<String> = artist.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** The cover the desktop can show: one on the web, or a file (as a file: address); "" for none. */
    fun artUrl(art: String): String = when {
        art.startsWith("http://") || art.startsWith("https://") || art.startsWith("file:") -> art
        art.startsWith("/") -> "file://" + art.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        else -> ""
    }

    /** What the desktop must be told has changed between two moments: "PlaybackStatus", "Metadata" (another song,
     *  or its length now known). The position moving on by itself is not told (the desktop counts it itself). */
    fun changed(before: Now, after: Now): Set<String> {
        val out = LinkedHashSet<String>()
        if (status(before) != status(after)) out += "PlaybackStatus"
        if (before.copy(position = 0.0, playing = false) != after.copy(position = 0.0, playing = false)) out += "Metadata"
        return out
    }

    /** A jump in the song (a seek, not time passing): the position is not where the last one and the time since
     *  would put it, by more than two seconds. The desktop is told of those. */
    fun jumped(before: Now, after: Now, elapsed: Double): Boolean {
        if (before.id != after.id || !after.hasSong) return false
        val expected = before.position + if (before.playing) elapsed else 0.0
        return Math.abs(after.position - expected) > 2.0
    }

    /** Where a relative seek lands: within the song. */
    fun seekTo(position: Double, offsetMicros: Long, duration: Double): Double {
        val to = maxOf(0.0, position + seconds(offsetMicros))
        return if (duration > 0) minOf(to, duration) else to
    }
}
