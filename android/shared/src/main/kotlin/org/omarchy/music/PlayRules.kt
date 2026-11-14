package org.omarchy.music

/** What counts as a play, a scrobble and a skip: the rules alone, taken out of Music (the first piece of the
 *  playback service, docs/rebuild/RADON_REBUILD_PLAN.md, Phase 9) so they can be read and tested by themselves.
 *  Seconds throughout. */
object PlayRules {
    /** A song counts as played (Recently played, Most played) once half of it has been heard; one whose length
     *  isn't known, after playAfter seconds. */
    fun countsAsPlay(heard: Double, length: Double, playAfter: Double): Boolean = heard >= (if (length > 0) length / 2 else playAfter)

    /** Last.fm gets a song once half of it has been heard, or four minutes of it; never a song of half a minute or less. */
    fun scrobbles(heard: Double, length: Double): Boolean = length > 30 && heard >= minOf(length / 2, 240.0)

    /** Leaving a song is a skip when at least a second of it was heard and it's left before its last skipLeft seconds
     *  (a song too short to have a middle, twice skipLeft or less, is never skipped). */
    fun isSkip(heard: Double, length: Double, at: Double, skipLeft: Double): Boolean = heard >= 1 && length > skipLeft * 2 && at < length - skipLeft
}
