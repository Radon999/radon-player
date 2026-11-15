package org.omarchy.music

/** Where a stream comes from when Tidal is connected: Tidal's best always, and anything less only when Tidal hasn't
 *  got the song, never because it didn't answer for a moment. */
object StreamRules {
    /** How many more times Tidal is asked after it failed to answer. */
    const val MORE_TRIES = 2

    /** Whether to ask Tidal again: it failed to answer (trouble: an error that isn't "not on Tidal") and tries are left
     *  (tried: how many more it has had). "Not on Tidal" is an answer: no asking again. */
    fun again(trouble: Throwable?, tried: Int): Boolean = trouble != null && trouble !is NoLosslessMatch && tried < MORE_TRIES

    /** The pause before the nth more try (1, 2…): a second, then two and a half. */
    fun pauseMs(n: Int): Long = if (n <= 1) 1000L else 2500L

    /** Seconds a looked-up stream is used for before it is looked up again: usual, but a minute for one that is less
     *  than Tidal's best only because Tidal didn't answer (soon), so the next play of the song asks Tidal again. */
    fun keptFor(soon: Boolean, usual: Int): Int = if (soon) minOf(60, usual) else usual
}
