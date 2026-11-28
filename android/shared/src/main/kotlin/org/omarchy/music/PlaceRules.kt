package org.omarchy.music

/** Your place in the song, kept between runs: when it's worth going back to. */
object PlaceRules {
    /** While a song plays, its place is written this often (paused or moved: at once). */
    const val EVERY_MS = 15_000L

    /** Whether a song begun again starts at the place kept: past its first seconds, and not in its last ones (a song
     *  all but over starts from its beginning). duration 0: not known, so only the first rule. */
    fun resumes(at: Double, duration: Double): Boolean = at > 5 && (duration <= 0 || at < duration - 10)
}
