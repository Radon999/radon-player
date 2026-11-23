package org.omarchy.music

/** What happens to hi-res music (24 or 32 bits) on its way out of the phone's player. */
object SinkRules {
    /** Whether the music goes on exactly as it is, all its bits: bit-perfect is in effect now (bitPerfectNow: on, and
     *  the sound is on a USB output or already streaming straight to the DAC), or it is about to go straight to the
     *  DAC (dacWanted: bit-perfect on, a DAC plugged in that the app may use).
     *
     *  The second half is the fault this fixes: "in effect now" only became true once a stream to the DAC was running,
     *  and it is asked before each song's stream is set up. Whenever the DAC's stream had been closed in between (and
     *  Android, which the app keeps away from the DAC, no longer listed a USB output), a 24-bit song was brought down
     *  to 16 bits and then sent "bit-perfect" to the DAC: Settings said 16-bit/48 kHz for a 24-bit/48 kHz file. */
    fun asItIs(bitPerfectNow: Boolean, dacWanted: Boolean): Boolean = bitPerfectNow || dacWanted

    /** Whether hi-res music is brought down to 16 bits for Android's own output (some outputs refuse more): only when
     *  it is hi-res and isn't going on as it is. */
    fun toSixteen(hiRes: Boolean, bitPerfectNow: Boolean, dacWanted: Boolean): Boolean = hiRes && !asItIs(bitPerfectNow, dacWanted)

    /** Bit-perfect straight to a USB DAC takes it from Android, and Android, seeing it gone, moves the sound to the speaker;
     *  the player sets up again and the DAC is taken again, or handed back and taken again. Normally it's taken once and
     *  kept, song after song; taken TAKEOVER_MAX times within TAKEOVER_SPAN_MS is the two pulling it between them (static,
     *  silence, the DAC's screen changing all the time), and the app stops taking it. times: when it was taken lately
     *  (older ones are dropped from it here). */
    const val TAKEOVER_MAX = 4
    const val TAKEOVER_SPAN_MS = 60_000L
    fun mayTakeOver(times: MutableList<Long>, now: Long): Boolean {
        times.removeAll { now - it > TAKEOVER_SPAN_MS }
        return times.size < TAKEOVER_MAX
    }
}
