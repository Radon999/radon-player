package org.omarchy.music

/** The small rules behind the play buttons, casting and a song's radio. */
object PlayButtonRules {
    /** Next (from the headphones, the car, the lock screen) switches the DJ's set up instead of skipping a song: only
     *  with the DJ on here (not as the desktop's remote), a song there, the music paused, and the DJ not talking. */
    fun nextSwitchesSet(djOn: Boolean, remote: Boolean, paused: Boolean, djTalking: Boolean, hasSong: Boolean): Boolean =
        djOn && !remote && paused && !djTalking && hasSong
}

object CastRules {
    /** What kind of file a cast device is told it's getting, by the file's name (or its address): FLAC unless it says
     *  otherwise. */
    fun mime(nameOrAddress: String): String {
        val name = nameOrAddress.substringBefore('?').lowercase()
        return when {
            name.endsWith(".mp3") -> "audio/mpeg"; name.endsWith(".m4a") || name.endsWith(".aac") -> "audio/mp4"
            name.endsWith(".ogg") || name.endsWith(".opus") -> "audio/ogg"; name.endsWith(".wav") -> "audio/wav"
            else -> "audio/flac"
        }
    }
}

object RadioRules {
    /** Which services are asked for songs like a song, in order: the song's own, the one you browse, then YouTube
     *  Music and Tidal (each once). */
    fun order(songSource: String, browsing: String): List<String> = listOf(songSource, browsing, "ytm", "tidal").distinct()

    /** A service that can't be asked: Apple Music and Spotify have no radio here, and Tidal's needs you signed in. */
    fun skips(key: String, signedIn: Boolean): Boolean = key in setOf("apple", "spotify") || (key == "tidal" && !signedIn)
}
