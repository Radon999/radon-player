package org.omarchy.music

/** A song clip to share (a song's Options › Share a clip with sound, and the Lyrics card's "With the song's sound"): a
 *  short video, a card that stands still with the song itself playing under it, from where you say to where you say.
 *  The song clip's card has the cover, the song and a seek bar that fills as it plays; the lyrics clip is the lyrics
 *  card, with no bar, and runs from the first line ticked to the end of the last. It is an .mp4 any app plays. Only a
 *  song you have as a file (yours, or a download) can be clipped: a stream has no file to take the sound from. */
object ClipRules {
    /** The shortest and longest clip, in seconds; a lyrics clip may be longer (it has to hold its lines). */
    const val MIN = 3.0
    const val MAX = 60.0
    const val MAX_LYRICS = 90.0
    /** The video: 720 wide (the cards are drawn 1080 wide and brought down), 12 pictures a second (a bar filling). */
    const val WIDTH = 720
    const val FPS = 12
    /** How long a clip is when the box opens, if nothing else is set (Settings › Personalization › Song clip length). */
    const val LENGTH = "clip_length"
    val LENGTHS = listOf(15, 30, 45, 60)
    /** The song clip's style, a lyrics card style's key ("": the one the lyrics card uses). */
    const val STYLE = "clip_style"
    /** Lyrics card: With the song's sound. */
    const val CARD_SOUND = "card_sound"

    /** The seek bar's place on the picture (pixels) and its filled colour (0xRRGGBB). */
    data class Bar(val x: Int, val y: Int, val w: Int, val h: Int, val rgb: Int) {
        /** On a picture brought down to [toWidth] from [fromWidth]: whole, even numbers, as video wants. */
        fun scaled(fromWidth: Int, toWidth: Int): Bar {
            fun s(v: Int) = (v.toLong() * toWidth / fromWidth).toInt()
            fun even(v: Int) = (v / 2 * 2).coerceAtLeast(2)
            return Bar(even(s(x)), even(s(y)), even(s(w)), even(s(h)), rgb)
        }
    }

    /** The video's height for a card w×h: the card's shape at [WIDTH] wide, an even number. */
    fun height(w: Int, h: Int): Int = (h.toLong() * WIDTH / w).toInt() / 2 * 2

    /** Where the clip starts and ends, kept inside the song and between the shortest and the longest. songSecs 0: not known. */
    fun bounds(start: Double, end: Double, songSecs: Double, max: Double = MAX): Pair<Double, Double> {
        val song = if (songSecs > 0) songSecs else Double.MAX_VALUE
        var s = start.coerceIn(0.0, maxOf(0.0, song - MIN))
        var e = end.coerceIn(s + MIN, s + max).coerceAtMost(song)
        if (e - s < MIN) s = maxOf(0.0, e - MIN)
        return s to e
    }

    /** A lyrics clip: from the first line ticked to where the last one stops. times: when each timed line of the song
     *  starts, in order (the blank ones too: a blank line is where the one before it stops); picked: places in it.
     *  The last line stops where the next begins; the song's last line gets 6 seconds, or to the song's end.
     *  null: nothing ticked. */
    fun fromLyrics(times: List<Double>, picked: List<Int>, songSecs: Double): Pair<Double, Double>? {
        val ok = picked.filter { it in times.indices }
        if (ok.isEmpty()) return null
        val first = ok.min(); val last = ok.max()
        val start = times[first]
        val end = times.getOrNull(last + 1) ?: (if (songSecs > times[last]) minOf(songSecs, times[last] + 6.0) else times[last] + 6.0)
        val song = if (songSecs > 0) songSecs else Double.MAX_VALUE
        return start to end.coerceAtLeast(start + MIN).coerceAtMost(start + MAX_LYRICS).coerceAtMost(maxOf(song, start + 1.0))
    }

    /** Seconds as a clock: "0:07", "3:25". */
    fun clock(secs: Double): String { val s = secs.toInt().coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

    /** The clip's file name, safe anywhere. */
    fun fileName(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|]"), "").replace(Regex("[^\\x20-\\x7E]"), "-").trim().take(60).ifEmpty { "Radon Player" } + " clip"

    /** The ways the PC's ffmpeg is asked to make the video, best first (one built without the first has the next). */
    val CODECS = listOf("libx264", "libopenh264", "mpeg4")

    private fun num(d: Double) = "%.3f".format(java.util.Locale.ROOT, d)

    /** What the PC's ffmpeg is told: the card (a picture, held), the song from [start] for [secs], and, with a [bar]
     *  (already at the video's size), a strip of its colour slid in from the left under the card's own left edge, so it
     *  fills as the clip plays. w×h: the video. */
    fun ffmpegArgs(ffmpeg: String, card: String, audio: String, out: String, start: Double, secs: Double, bar: Bar?, w: Int, h: Int, codec: String): List<String> {
        val a = mutableListOf(ffmpeg, "-y", "-v", "error", "-nostdin", "-loop", "1", "-framerate", FPS.toString(), "-i", card,
            "-ss", num(start), "-t", num(secs), "-i", audio)
        val graph = if (bar == null) "[0:v]scale=$w:$h,format=yuv420p[v]" else {
            a += listOf("-f", "lavfi", "-i", "color=c=0x%06X:s=%dx%d:r=%d".format(bar.rgb and 0xFFFFFF, bar.w, bar.h, FPS))
            "[0:v]scale=$w:$h,split=2[bg][bg2];[bg2]crop=${bar.x}:${bar.h}:0:${bar.y}[left];" +
                "[bg][2:v]overlay=x='${bar.x}-${bar.w}+${bar.w}*min(t/${num(secs)},1)':y=${bar.y}[a];[a][left]overlay=x=0:y=${bar.y},format=yuv420p[v]"
        }
        a += listOf("-filter_complex", graph, "-map", "[v]", "-map", "1:a:0", "-c:v", codec)
        a += when (codec) {
            "libx264" -> listOf("-preset", "veryfast", "-tune", "stillimage", "-crf", "20")
            "mpeg4" -> listOf("-q:v", "4")
            else -> listOf("-b:v", "2500k")
        }
        a += listOf("-pix_fmt", "yuv420p", "-r", FPS.toString(), "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-ac", "2",
            "-t", num(secs), "-movflags", "+faststart", out)
        return a
    }

    /** How far the bar has filled at a moment of the clip: pixels of [barWidth]. */
    fun filled(atSecs: Double, secs: Double, barWidth: Int): Int = (barWidth * (atSecs / secs).coerceIn(0.0, 1.0)).toInt()

    /** Why a song can't be clipped ("": it can). */
    fun whyNot(hasFile: Boolean): String = if (hasFile) "" else
        "Only a song you have as a file (yours, or a download) can be clipped: this one plays as a stream, which has no file to take the sound from"
}
