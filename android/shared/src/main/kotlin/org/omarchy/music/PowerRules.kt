package org.omarchy.music

/** Saving battery without changing anything you see or hear: the app's timers wake the phone less often while nothing
 *  needs them (the music paused, the app off the screen). Everything here is about how often something is looked at;
 *  what is done when it is looked at stays the same. */
object PowerRules {
    /** The playback clock while music plays or is about to: where the song is, what counts as played, crossfade, the
     *  watches for a song stuck at its end. */
    const val CLOCK_MS = 500L
    /** The playback clock with the music paused: nothing moves then, so it only keeps an eye out. Pressing Play wakes
     *  it at once (see PlaybackCore), so this never delays anything. */
    const val CLOCK_RESTING_MS = 2000L

    /** Whether the playback clock can rest: there is no player, or the player is paused for real (not playing, not
     *  meant to be, the app not moving on to another song, and no song change being timed for Settings › Problems).
     *  Every watch it runs does nothing then: the position only moves while playing, the end watch and the idle watch
     *  only look at a song meant to be playing, the volume watch only at one that sounds. */
    fun clockRests(hasPlayer: Boolean, isPlaying: Boolean, playWhenReady: Boolean, paused: Boolean, advancing: Boolean, changing: Boolean): Boolean =
        !hasPlayer || (!isPlaying && !playWhenReady && paused && !advancing && !changing)

    /** How often the phone asks the paired PC whether its Sync now is waiting, with the app on screen or music playing. */
    const val SYNC_ASK_MS = 20_000L
    /** ... and with the app off the screen and the music paused. The PC's Sync now waits five minutes for the phone,
     *  so a minute still answers it in plenty of time. */
    const val SYNC_ASK_IDLE_MS = 60_000L

    /** Whether to ask the PC now. nextCheck: when the next question was planned (0: one is wanted at once, as just
     *  after pairing or a playlist made synced); askedAt: when it was last asked; shown: the app is on the screen;
     *  playing: the phone is playing music. */
    fun syncAskDue(now: Long, nextCheck: Long, askedAt: Long, shown: Boolean, playing: Boolean): Boolean {
        if (nextCheck == 0L) return true
        if (now < nextCheck) return false
        return shown || playing || now - askedAt >= SYNC_ASK_IDLE_MS
    }

    /** A song's address that is read from the phone itself (a file, Android's content:// address) rather than over
     *  the network (a stream's http address, or a stream that comes in pieces, handed over as a data: address). */
    fun onPhone(uri: String?): Boolean {
        if (uri.isNullOrEmpty()) return false
        if (uri.startsWith("/")) return true
        val scheme = uri.substringBefore(':', "").lowercase()
        return scheme == "file" || scheme == "content" || scheme == "android.resource"
    }

    /** Whether the player should keep Wi-Fi fully awake (a Wi-Fi lock) while it plays: only when the music may need
     *  the network to go on. The song playing is a stream; or the next one isn't on the phone, or there's no next one
     *  yet (it's fetched ahead while this one plays, or ∞ and the DJ go looking for more); or the DJ is on (its sets
     *  and its lines come from the network). Your own files with more of your own after them need none: Wi-Fi then
     *  keeps to its own power saving, as it does anyway with no music playing, and still works. nextOnPhone: null
     *  when there is no next song. */
    fun needsNetwork(currentUri: String?, nextOnPhone: Boolean?, djOn: Boolean): Boolean =
        djOn || !onPhone(currentUri) || nextOnPhone != true
}
