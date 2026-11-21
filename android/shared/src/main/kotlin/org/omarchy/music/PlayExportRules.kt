package org.omarchy.music

/** The app's own play history in a file (Settings › Accounts › This app's own play history): what the app counted
 *  itself (its plays, the songs you skipped or heard out, its listening diary), to bring into Radon Player on another
 *  device. Importing adds what the other device has and this one hasn't; importing the same file again adds nothing. */
object PlayExportRules {
    /** The file's mark: a file without it isn't one of these. */
    const val MARK = "radon_player_history"
    const val VERSION = 1

    /** A count and the times (seconds) it happened; the count may be more than the times (older ones kept no time). */
    data class Counted(val count: Int, val times: List<Long>)

    /** Two devices' counts of one thing as one. Each time once (a play both have is one play); of the ones without a
     *  time, the larger lot (they can't be told apart, and adding them would count them twice on a second import).
     *  cap: how many times the app keeps (the latest); past it the count is never less than either side's. */
    fun merge(here: Counted, theirs: Counted, cap: Int): Counted {
        val all = (here.times + theirs.times).distinct().sorted()
        val loose = maxOf(here.count - here.times.size, theirs.count - theirs.times.size, 0)
        val count = if (all.size <= cap) all.size + loose else maxOf(here.count, theirs.count, all.size)
        return Counted(maxOf(count, here.count), all.takeLast(cap))
    }

    /** What an import says when it's done. */
    fun done(songs: Int, plays: Int, skips: Int, diary: Int): String =
        if (songs == 0 && plays == 0 && skips == 0 && diary == 0) "Nothing new in it: this device had all of it already"
        else "Added %,d play%s of %,d song%s".format(plays, if (plays != 1) "s" else "", songs, if (songs != 1) "s" else "") +
            (if (skips > 0) ", %,d skip%s and song%s heard out".format(skips, if (skips != 1) "s" else "", if (skips != 1) "s" else "") else "") +
            (if (diary > 0) ", and %,d stretch%s of listening".format(diary, if (diary != 1) "es" else "") else "")
}
