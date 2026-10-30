package org.omarchy.music

/** The player underneath the app: ExoPlayer on the phone (or a cast device), mpv on the PC. The app (Music) tells it
 *  what to play and asks it where it is; everything else (queues, plays, crossfade's timing) is the app's own. */
interface Engine {
    /** Sounding now (not paused, not loading). */
    val isPlaying: Boolean
    /** Where it is, and how long the song is (0 if not known yet), in milliseconds. */
    val positionMs: Long
    val durationMs: Long
    /** One of [IDLE], [BUFFERING], [READY], [ENDED]. */
    val state: Int
    /** Whether it has a song loaded at all. */
    val hasItem: Boolean
    /** The address of the song loaded (for crossfade's second player), or null. */
    val currentUri: String?
    /** 0 to 1. */
    var volume: Float
    /** Plays as soon as it can (true), or waits paused (false). */
    var playWhenReady: Boolean

    fun play()
    fun pause()
    fun seekTo(ms: Long)
    /** Load item, from startMs; it plays once [playWhenReady]. */
    fun load(item: PlayItem, startMs: Long)
    /** Speed (1 = as recorded); pitch 1 keeps the pitch, otherwise it goes with the speed. */
    fun setSpeed(speed: Float, pitch: Float)
    /** The song's sample rate and bits (0 if not known), for bit-perfect. */
    val audioRate: Int
    val audioBits: Int

    /** A second player of the same kind, the song at uri ready and paused at startMs, silent: crossfade's ending song.
     *  null where there's none to be had (a cast device). */
    fun tail(uri: String, startMs: Long): Engine?
    /** Run once when this one starts sounding. */
    fun onceSounding(run: () -> Unit)
    /** Done with it (a tail). */
    fun release()

    companion object {
        const val IDLE = 1
        const val BUFFERING = 2
        const val READY = 3
        const val ENDED = 4
    }
}

/** A song for the engine: where it is, what kind (null: the engine works it out), and what it's called (for the
 *  notification and the lock screen). art: a picture's address (a path or a web address), or null. */
data class PlayItem(val uri: String, val id: String, val mime: String?, val title: String, val artist: String,
                    val album: String, val art: String?)
