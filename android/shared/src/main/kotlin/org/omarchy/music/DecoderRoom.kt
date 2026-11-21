package org.omarchy.music

/** How much room the phone's decoder is given for one piece of a song. A FLAC is stored in frames; the player hands
 *  the decoder one frame at a time, in a buffer whose size is set when the song starts, from what the file says its
 *  biggest frame is. Many FLACs don't say (a download put together from a stream, a file some encoders wrote), and the
 *  decoder then gets its own default, 32 KB on a Samsung. A loud passage of a hi-res song has frames bigger than that
 *  (“Kentucky (Demo)”, 21 Nov 2026: "Buffer too small (32768 < 41436)"); each such frame was thrown away, which is
 *  the stutter, the static and the "timestamp discontinuity" of whole blocks (209, 313, 418 ms…) seen since 4.0.27. */
object DecoderRoom {
    /** The room for one FLAC frame: the most a frame can hold (65,535 samples a channel, 4 bytes each) and its header,
     *  whatever the file says. said: what the file says its biggest frame is (0 or less: it doesn't). */
    fun flac(channels: Int, said: Int): Int = maxOf(said, channels.coerceIn(1, 8) * 4 * 65_535 + 65_536)
}
