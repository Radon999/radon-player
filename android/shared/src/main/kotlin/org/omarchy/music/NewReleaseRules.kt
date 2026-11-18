package org.omarchy.music

/** New releases found by themselves, and the wake-up timer: what is said, and when. */
object NewReleaseRules {
    /** Of the releases a look found (its key, then its title and artist), the ones not told about before, each once. */
    fun fresh(found: List<Pair<String, Pair<String, String>>>, seen: Set<String>): List<Pair<String, Pair<String, String>>> {
        val once = HashSet<String>()
        return found.filter { it.first !in seen && once.add(it.first) }
    }

    fun title(n: Int): String = if (n == 1) "A new release" else "%,d new releases".format(n)

    /** "Persona by half•alive; ARMS by HOAX; and 3 more": the first three named. */
    fun said(releases: List<Pair<String, String>>): String =
        releases.take(3).joinToString("; ") { (title, artist) -> "$title by $artist" } + if (releases.size > 3) "; and %,d more".format(releases.size - 3) else ""
}

/** Wake-up timer (Settings › Playback): at the time you set, the app starts playing what you chose. It is a timer
 *  inside the app, not the phone's alarm clock: it goes off only while the app is running, so on the phone it is for
 *  a phone left on its stand with the app open, and it is no substitute for a real alarm. */
object WakeUpRules {
    /** "07:30" as minutes from midnight, or null: not a time. */
    fun minutes(time: String): Int? {
        val m = Regex("""^\s*(\d{1,2})[:.](\d{2})\s*$""").find(time) ?: return null
        val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h * 60 + min else null
    }

    /** Whether it goes off now. at: the time set (minutes from midnight); now: the time now (minutes from midnight);
     *  today, firedOn: the day of the year now, and the one it last went off on. It goes off in the minute it is set
     *  for and the four after (in case the app was busy), once a day. */
    fun due(on: Boolean, at: Int?, now: Int, today: Int, firedOn: Int): Boolean =
        on && at != null && firedOn != today && now >= at && now - at < 5

    fun shown(at: Int): String = "%d:%02d".format(at / 60, at % 60)
}
