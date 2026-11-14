package org.omarchy.music

/** When a song fades into the next (Equalizer › Crossfade), and when it doesn't: the rules alone, taken out of Music
 *  (the playback service, docs/rebuild/RADON_REBUILD_PLAN.md, Phase 9). The fading itself, with its second player,
 *  stays where the players are. Seconds throughout. */
object CrossfadeRules {
    /** Crossfade isn't used at all now: it's off, or it can't be done (bit-perfect: the music goes out untouched;
     *  Repeat one; this device is only the remote; casting; a stream, which can't be opened twice) or shouldn't
     *  (the music is turned down under the DJ's voice). */
    fun unavailable(on: Boolean, bitPerfect: Boolean, repeatOne: Boolean, remote: Boolean, casting: Boolean, streaming: Boolean, ducked: Boolean): Boolean =
        !on || bitPerfect || repeatOne || remote || casting || streaming || ducked

    /** Where in a song of this length the fade begins, fading over this many seconds; null for a song too short to
     *  fade (under three times the fade). */
    fun fadeAt(length: Double, seconds: Double): Double? = if (length < seconds * 3) null else length - seconds

    /** These two songs aren't faded into each other: they follow each other on one album (Smart transitions: they
     *  flow as the record has them), or the DJ talks between them (a new set, or a word before the next song). */
    fun keptApart(sameAlbum: Boolean, smartTransitions: Boolean, djTalksBetween: Boolean): Boolean = (smartTransitions && sameAlbum) || djTalksBetween

    /** Time to get the ending ready in its second player: in the five seconds before the fade, if it isn't yet. */
    fun getReady(position: Double, fadeAt: Double, ready: Boolean): Boolean = !ready && position >= fadeAt - 5 && position < fadeAt
}
