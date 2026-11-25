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

    /** How often the phone asks the paired PC whether its Sync now is waiting, with the app on the screen. */
    const val SYNC_ASK_MS = 20_000L
    /** ... and with the app off the screen, music playing or not (it asked every 20 seconds while music played: about
     *  4,000 times a day of music, each one the radio woken, through Tailscale too). The PC's Sync now waits five
     *  minutes for the phone, so every two minutes still answers it in time; Auto playlist sync catches up as often. */
    const val SYNC_ASK_AWAY_MS = 120_000L
    /** With the app on the screen, the PC not answering: asked again this much later. */
    const val SYNC_RETRY_MS = 30_000L

    /** When to ask the PC again after it didn't answer failures times in a row (it's off, or you're away from it): on the
     *  screen after [SYNC_RETRY_MS]; off it two minutes, then four, then every five. */
    fun syncRetryMs(failures: Int, shown: Boolean): Long =
        if (shown) SYNC_RETRY_MS else minOf(5 * 60_000L, SYNC_ASK_AWAY_MS shl (failures - 1).coerceIn(0, 2))

    /** Whether to ask the PC now. nextCheck: when the next question was planned (0: one is wanted at once, as just
     *  after pairing or a playlist made synced; later after the PC didn't answer, see [syncRetryMs]); askedAt: when it
     *  was last asked; shown: the app is on the screen (then never longer than [SYNC_RETRY_MS] between questions, so
     *  coming back to it catches up at once). */
    fun syncAskDue(now: Long, nextCheck: Long, askedAt: Long, shown: Boolean): Boolean {
        if (nextCheck == 0L) return true
        if (shown) return now >= nextCheck || now - askedAt >= SYNC_RETRY_MS
        return now >= nextCheck && now - askedAt >= SYNC_ASK_AWAY_MS
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
     *  yet (it's fetched ahead while this one plays, or ∞ and the DJ go looking for more). Your own files with more of
     *  your own after them need none, the DJ on too (its next set is queued well ahead, and its lookups go through with
     *  Wi-Fi's power saving on): Wi-Fi then keeps to its own power saving, as it does anyway with no music playing, and
     *  still works. nextOnPhone: null when there is no next song. */
    fun needsNetwork(currentUri: String?, nextOnPhone: Boolean?): Boolean =
        !onPhone(currentUri) || nextOnPhone != true
}
