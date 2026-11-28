package org.omarchy.music

/** New releases found by themselves, and the wake-up timer: what is said, and when. */
object NewReleaseRules {
    /** A look that reached nobody (no connection): asked artists, and how many of them couldn't be asked. Half or more
     *  failing, of at least three asked, is no look at all: nothing found before is touched, and the daily look tries
     *  again soon. (It had counted as a look that found nothing, and emptied the list.) */
    fun lookFailed(asked: Int, failed: Int): Boolean = asked > 0 && failed > 0 && (failed == asked || (asked >= 3 && failed * 2 >= asked))

    /** After a look: which of the releases found before stay (their keys). before: (key, artist key, date). Those the
     *  look found again are its own. Kept: the releases of the artists that couldn't be asked this time (failed), back
     *  to since; and, when the look went less far back than the list does (the daily week against a month or a year:
     *  widerSince, the list's own reach), the ones from before the look's reach. */
    fun keptFromBefore(before: List<Triple<String, String, String>>, foundNow: Set<String>, failed: Set<String>,
                       since: String, widerSince: String?): Set<String> =
        before.filter { (k, artist, date) ->
            k !in foundNow && ((artist in failed && date >= (widerSince ?: since)) || (widerSince != null && date < since && date >= widerSince))
        }.mapTo(HashSet()) { it.first }

    /** Where a new release plays from, said (where: "tidal", "ytm", "none", or null before it's looked up). */
    fun whereLabel(where: String?): String = when (where) {
        "tidal" -> "Tidal"
        "ytm" -> "YouTube Music"
        "none" -> "Deezer only: can't play yet"
        else -> ""
    }

    /** Why a new release (found on Deezer, where the app looks for them) can't be played: the services it was looked
     *  for on (looked), those that couldn't be reached (failed: "Tidal (why)"), and whether Tidal isn't signed in. */
    fun whyNot(title: String, artist: String, looked: List<String>, failed: List<String>, tidalOff: Boolean): String {
        val what = "“$title” by $artist"
        return when {
            looked.isEmpty() && failed.isNotEmpty() -> "Couldn't play $what: couldn't reach ${failed.joinToString(" or ")}. Check your internet and try again"
            looked.isEmpty() -> "Couldn't play $what: no streaming service is set up (sign in to Tidal in Settings)"
            else -> "Couldn't play $what: it's listed on Deezer, where new releases are found, but isn't on " +
                looked.joinToString(" or ") + " yet" +
                (if (failed.isNotEmpty()) " (and ${failed.joinToString(" or ")} couldn't be reached)" else "") +
                (if (tidalOff) ". Signing in to Tidal (Settings) may find it" else "")
        }
    }

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
