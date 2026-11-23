package org.omarchy.music

/** The music paused because another app took the sound for good (Android's audio focus, lost for good), on the phone.
 *  Some takers never play anything: a car connecting over Bluetooth, an app grabbing the sound as it opens. The music
 *  then stayed paused for no reason you could hear. With Carry on when nothing else plays (Settings › Playback, on at
 *  first), the app looks a few times in the first seconds, and goes on by itself when nothing else is playing and no
 *  call is on. Another app that really plays keeps the sound: the music stays paused, as before. */
object FocusRules {
    /** The first look, and the time between looks (ms). */
    const val FIRST_MS = 1500L
    const val EVERY_MS = 1000L
    /** How many looks before giving up (about nine seconds). */
    const val LOOKS = 8
    /** At most this many times going on by itself within [SPAN_MS]: more is a fight with another app, so it stops. */
    const val MOST = 4
    const val SPAN_MS = 5 * 60_000L

    /** Whether to go on now. on: the switch; stillPaused: nothing has played or paused it since the sound was taken;
     *  inCall: a call (or a voice or video chat) is on; otherPlaying: some other app is playing; resumed: when it went
     *  on by itself before (ms), now: the time now. */
    fun goOn(on: Boolean, stillPaused: Boolean, inCall: Boolean, otherPlaying: Boolean, resumed: List<Long>, now: Long): Boolean =
        on && stillPaused && !inCall && !otherPlaying && resumed.count { now - it < SPAN_MS } < MOST
}
