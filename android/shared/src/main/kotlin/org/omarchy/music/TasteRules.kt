package org.omarchy.music

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** One play of a song, from anywhere (Last.fm, an import, the app's own diary): when (seconds), and as it was named. */
data class Heard(val t: Long, val artist: String, val title: String)

/** What a listening history adds up to: every count the DJ's sets and facts are made from. A song is known by its
 *  stats key ([statsKey]: one song across Last.fm, the imports and your files), an artist by the lead name. */
class TasteCounts {
    /** Each song as it was named the first time it's met. */
    val names = HashMap<Pair<String, String>, Heard>()
    /** When each song was last played, and first. */
    val last = HashMap<Pair<String, String>, Long>()
    val first = HashMap<Pair<String, String>, Long>()
    /** Each song's plays: in all, in the last 30 days, and around this hour in the last year. */
    val count = HashMap<Pair<String, String>, Int>()
    val recent = HashMap<Pair<String, String>, Int>()
    val hour = HashMap<Pair<String, String>, Int>()
    /** Each year's plays, song by song. */
    val years = HashMap<Int, HashMap<Pair<String, String>, Int>>()
    /** Each artist's plays: in the last 90 days (an empty name counted too, as it always was), in all, in the last 30 days. */
    val artists = HashMap<String, Int>()
    val artistAll = HashMap<String, Int>()
    val artistMonth = HashMap<String, Int>()
    val artistFirst = HashMap<String, Long>()
    /** Plays at each hour of the day. */
    val hours = IntArray(24)
    var total = 0
    /** The first play of all; Long.MAX_VALUE with none. */
    var since = Long.MAX_VALUE
    /** Every play of a song whose mood is known, in the order met; the moods of the last 30 days, and around this hour. */
    val moodPlays = ArrayList<MoodPlay>()
    val moodMonth = HashMap<String, Int>()
    val moodHour = HashMap<String, Int>()
    /** On this day (within three days) so many years ago: each song's plays. */
    val dayAgo = HashMap<Int, HashMap<Pair<String, String>, Int>>()
    /** The days of the last week each song was played on. */
    val days = HashMap<Pair<String, String>, HashSet<Long>>()
    /** Plays in this calendar month. */
    var monthPlays = 0
}

object TasteRules {
    const val DAY = 86_400L

    /** Within an hour either side of hour, round midnight too. */
    fun aroundHour(at: Int, hour: Int) = Math.floorMod(at - hour + 12, 24) - 12 in -1..1

    /** Adds up plays as of nowS (seconds), the hour and the day it is in zone. moodOf: the mood of a song of yours
     *  by its stats key, or null (asked once per song). A play with no time is left out. */
    fun count(plays: Iterable<Heard>, nowS: Long, hour: Int, today: LocalDate, zone: ZoneId,
              moodOf: (Pair<String, String>) -> String?): TasteCounts {
        val c = TasteCounts()
        val moods = HashMap<Pair<String, String>, String?>()
        for (p in plays) {
            if (p.t <= 0) continue
            c.total++
            c.since = minOf(c.since, p.t)
            val k = statsKey(p.title, p.artist)
            c.names.putIfAbsent(k, p)
            c.last[k] = maxOf(c.last[k] ?: 0, p.t)
            val age = nowS - p.t
            val at = Instant.ofEpochSecond(p.t).atZone(zone)
            if (age < 30 * DAY) c.recent.merge(k, 1, Int::plus)
            if (age < 90 * DAY) c.artists.merge(WholeArtists.lead(p.artist), 1, Int::plus)
            if (age < 365 * DAY && aroundHour(at.hour, hour)) c.hour.merge(k, 1, Int::plus)
            c.years.getOrPut(at.year) { HashMap() }.merge(k, 1, Int::plus)
            c.count.merge(k, 1, Int::plus)
            c.first[k] = minOf(c.first[k] ?: Long.MAX_VALUE, p.t)
            val lead = WholeArtists.lead(p.artist)
            if (lead.isNotEmpty()) { c.artistAll.merge(lead, 1, Int::plus); if (age < 30 * DAY) c.artistMonth.merge(lead, 1, Int::plus) }
            c.hours[at.hour]++
            val d = at.toLocalDate()
            val ya = today.year - d.year
            if (ya in 1..15 && abs(ChronoUnit.DAYS.between(d.plusYears(ya.toLong()), today)) <= 3)
                c.dayAgo.getOrPut(ya) { HashMap() }.merge(k, 1, Int::plus)
            // (the last seven days of the calendar: seven times 24 hours touch eight of them, and the DJ said "8 of the last seven days")
            if (d.toEpochDay() > today.toEpochDay() - 7) c.days.getOrPut(k) { HashSet() } += d.toEpochDay()
            if (lead.isNotEmpty()) c.artistFirst[lead] = minOf(c.artistFirst[lead] ?: Long.MAX_VALUE, p.t)
            if (d.year == today.year && d.month == today.month) c.monthPlays++
            // A song with no mood is asked about once too (before: at every one of its plays)
            val mood = if (moods.containsKey(k)) moods[k] else moodOf(k).also { moods[k] = it }
            if (mood != null) {
                c.moodPlays += MoodPlay(p.t, mood, "${k.first}\t${k.second}")
                if (age < 30 * DAY) c.moodMonth.merge(mood, 1, Int::plus)
                if (age < 365 * DAY && aroundHour(at.hour, hour)) c.moodHour.merge(mood, 1, Int::plus)
            }
        }
        return c
    }

    /** Most played's count as the DJ takes it: when there are plays from outside the app at all (Last.fm, an
     *  import), only those (the plays here taken off); with none, the plays here are all there is. */
    fun outside(all: Int, here: Int, anyOutside: Boolean): Int = if (anyOutside) all - here else all
}
