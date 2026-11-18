package org.omarchy.music

/** What keeps an equalizer's filter a filter. Its numbers come from its frequency and width; a frequency at or over
 *  half the music's sample rate (a 22 kHz band on 44.1 kHz music), or no width at all, gives numbers that grow
 *  without end as the music plays, until there is no sound left. */
object EqRules {
    /** The frequency a filter is built at: its own, kept between 10 Hz and 45% of the sample rate. */
    fun safeHz(hz: Double, rate: Double): Double {
        val top = (rate * 0.45).coerceAtLeast(20.0)
        return if (hz.isNaN()) 1000.0.coerceAtMost(top) else hz.coerceIn(10.0, top)
    }

    /** Its width (Q): its own, kept between 0.05 and 50. */
    fun safeQ(q: Double): Double = if (q.isNaN()) 0.71 else q.coerceIn(0.05, 50.0)
}
