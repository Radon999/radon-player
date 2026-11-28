package org.omarchy.music

/** The phone's home screen widgets, in rules that need no phone: the Lyrics widget's line being sung, which lines it
 *  shows and how long it waits before it looks again; what a widget asks the app to open or to play, written as one
 *  piece of text; and how a tap that starts music is carried out. */
object PhoneWidgetRules {
    /** How early a line counts as sung: a quarter of a second, as you read ahead (the Lyrics tab's own). */
    const val LEAD = 0.25

    /** The shortest and the longest the Lyrics widget waits before it looks at the music again (ms): never so short that
     *  it keeps the phone busy, never so long that a jump in the song (a seek) goes unseen for more than a second. */
    const val MIN_WAIT = 80L
    const val MAX_WAIT = 1000L

    /** The line being sung at pos seconds (its place in times), or -1 before the first. times: each line's start, in order. */
    fun lineAt(times: List<Double>, pos: Double): Int = times.indexOfLast { it <= pos + LEAD }

    /** The lines the widget shows in its slots: the line being sung stays `before` lines from the top once there are
     *  that many above it (before the first line, and on the first few, from the first line); near the end the slots
     *  under the last line are left empty rather than the lines jumping back. */
    fun window(cur: Int, count: Int, slots: Int, before: Int): IntRange {
        if (count <= 0 || slots <= 0) return IntRange.EMPTY
        val start = maxOf(0, cur - before).coerceAtMost(count - 1)
        return start until minOf(count, start + slots)
    }

    /** How long (ms) from pos until the next line comes up: the widget looks again then and not before (a little after,
     *  so it lands on the new line), kept between [MIN_WAIT] and [MAX_WAIT]. After the last line: [MAX_WAIT]. */
    fun waitMs(times: List<Double>, pos: Double): Long {
        val next = times.firstOrNull { it > pos + LEAD } ?: return MAX_WAIT
        return ((next - LEAD - pos) * 1000 + 20).toLong().coerceIn(MIN_WAIT, MAX_WAIT)
    }

    /** What a widget asks for, written "kind:rest" ("list:ab12cd:1", "pin:mine::ab12cd", "wrapped:month"), as
     *  (kind, rest). The rest may hold colons of its own (a pin's key does), so only the first colon splits. */
    fun split(link: String): Pair<String, String> = link.substringBefore(':') to link.substringAfter(':', "")

    /** Start a playlist of yours, in order or shuffled. */
    fun list(id: String, shuffled: Boolean) = "list:$id:${if (shuffled) 1 else 0}"

    /** The playlist and whether shuffled, back from [list]; null if the link isn't a playlist's (or has no id). Read from
     *  the end, so the id is whatever comes between. */
    fun playlistOf(link: String): Pair<String, Boolean>? {
        val (kind, rest) = split(link)
        if (kind != "list" || rest.isEmpty()) return null
        val flag = rest.substringAfterLast(':', "")
        val id = if (flag == "0" || flag == "1") rest.substringBeforeLast(':') else rest
        return id.takeIf { it.isNotEmpty() }?.let { it to (flag == "1") }
    }

    /** The Wrapped widget's periods: (what the link says, the button's name). */
    val WRAPPED = listOf("week" to "Weekly", "month" to "Monthly", "all" to "All time")

    /** How a widget's tap that starts music is carried out. "now": in the app as it runs, when the music is already
     *  playing (that keeps the app in the foreground, and the new music follows on in it) or the phone is the PC's
     *  remote (nothing plays here). Else "service": the music's service is started in the foreground first and does it
     *  there, because Android lets an app that is paused in the background start playing again only that way (a paused
     *  app told to play from a widget was stopped with "startForegroundService() not allowed"). */
    fun startHow(playerUp: Boolean, playing: Boolean, remote: Boolean): String = if (remote || (playerUp && playing)) "now" else "service"

    /** What the DJ widget's tap does: start the DJ when it's off; when it's on, carry on if it was paused, or a new
     *  set now (Switch it up) when it's playing. */
    fun djTap(on: Boolean, paused: Boolean): String = when {
        !on -> "start"
        paused -> "resume"
        else -> "switch"
    }

    // ---- the New releases widget ----

    /** The newest n releases of all the app found (its own daily look's and the screen's Refresh…), each once (key: the
     *  same release found by both), the latest release date first. Dates are written year-month-day, so as text they
     *  sort as dates do. */
    fun <T> newest(all: List<T>, key: (T) -> String, date: (T) -> String, n: Int): List<T> =
        all.distinctBy(key).sortedByDescending(date).take(n)

    /** When a release came out, as the widget says it: Today, Yesterday, 3 days ago, 2 weeks ago, then the date (with
     *  its year when it isn't this one); one still to come: "Out Dec 5". A date it can't read is shown as it is. */
    fun released(date: String, today: java.time.LocalDate): String {
        val d = runCatching { java.time.LocalDate.parse(date) }.getOrNull() ?: return date
        val days = java.time.temporal.ChronoUnit.DAYS.between(d, today)
        fun day() = d.format(java.time.format.DateTimeFormatter.ofPattern(if (d.year == today.year) "MMM d" else "MMM d, yyyy", java.util.Locale.ENGLISH))
        return when {
            days < 0 -> "Out " + day()
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7 -> "$days days ago"
            days < 35 -> (days / 7).let { w -> "$w week${if (w != 1L) "s" else ""} ago" }
            else -> day()
        }
    }

    // ---- the Visualizer widget ----

    /** About 24 frames a second: as smooth as film (8 a second, as it was, looked choppy). It only runs on the home
     *  screen while music plays, and rests after [VIZ_RUN_MS]. */
    const val VIZ_FRAME_MS = 42L
    const val VIZ_BARS = 24
    /** The longest the bars move at a time (ms): Android can't tell an app whether the home screen itself is showing
     *  (another app may be in front with the phone unlocked), so after this they rest until the screen, the music or the
     *  song changes. */
    const val VIZ_RUN_MS = 10 * 60_000L

    /** The bars move only while all of these hold: a Visualizer widget on the home screen, the screen on and the phone
     *  unlocked (screenOn), the app not in front (its own visualizer then), and music playing. Otherwise nothing is
     *  captured or drawn at all. */
    fun vizLive(present: Boolean, screenOn: Boolean, appShown: Boolean, playing: Boolean) = present && screenOn && !appShown && playing

    /** n bars from the spectrum's bands (Spectrum.bands: low to high, 0..1): each bar the loudest of its share of them. */
    fun bars(bands: FloatArray, n: Int): FloatArray {
        if (n <= 0 || bands.isEmpty()) return FloatArray(maxOf(0, n))
        return FloatArray(n) { i ->
            val from = i * bands.size / n
            val to = maxOf(from + 1, (i + 1) * bands.size / n).coerceAtMost(bands.size)
            var m = 0f
            for (k in from until to) m = maxOf(m, bands[k])
            m.coerceIn(0f, 1f)
        }
    }

    /** The bars shown next: a bar goes up at once with the music, and comes down gently (keep: how much of its height it
     *  keeps each frame), so they don't flicker at a few frames a second. */
    fun fall(shown: FloatArray, now: FloatArray, keep: Float = 0.7f): FloatArray =
        FloatArray(now.size) { i -> maxOf(now[i], shown.getOrElse(i) { 0f } * keep) }
}
