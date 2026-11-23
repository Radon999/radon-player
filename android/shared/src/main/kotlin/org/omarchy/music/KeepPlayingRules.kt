package org.omarchy.music

/** Keep playing (the phone, Settings › Playback, on at first): the music isn't paused when another app takes the sound.
 *  Android's way (the player handling audio focus itself) paused it whenever any app asked for the sound for good,
 *  even one that then played nothing (a car connecting over Bluetooth nearby, an app as it opens): on the owner's
 *  phone the music stopped every so often for no reason. Now the app holds the sound itself and decides:
 *  - another app takes it for good: the music plays on;
 *  - for a moment (voice typing, the assistant, a spoken direction): the music turns down, then back up;
 *  - for a call (ringing, or on a call or a voice or video chat): the only pause, and it plays again when the call is over.
 *  Off: Android's way, as before. */
object KeepPlayingRules {
    /** How loud the music is while another app has the sound for a moment (0-1). */
    const val DUCKED = 0.2

    /** What the music does. */
    enum class Act { PLAY_ON, DUCK, UNDUCK, PAUSE_FOR_CALL, PLAY_AFTER_CALL, NOTHING }

    /** What Android said (its audio focus changes). */
    enum class Change { GAIN, LOSS, LOSS_TRANSIENT, LOSS_TRANSIENT_CAN_DUCK }

    /** change: what Android said; inCall: the phone is ringing or on a call; playing: the music is playing now;
     *  pausedForCall: it was this that paused it for a call. */
    fun act(change: Change, inCall: Boolean, playing: Boolean, pausedForCall: Boolean): Act = when (change) {
        Change.GAIN -> if (pausedForCall) Act.PLAY_AFTER_CALL else Act.UNDUCK
        Change.LOSS -> if (inCall && playing) Act.PAUSE_FOR_CALL else Act.PLAY_ON
        Change.LOSS_TRANSIENT -> if (inCall && playing) Act.PAUSE_FOR_CALL else Act.DUCK
        Change.LOSS_TRANSIENT_CAN_DUCK -> if (inCall && playing) Act.PAUSE_FOR_CALL else Act.DUCK
    }

    /** Android's audio mode: ringing (1), on a call (2), a voice or video chat (3). */
    fun inCall(mode: Int): Boolean = mode in 1..3
}
