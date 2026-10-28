package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Repeat: what you've had on repeat, as charts: your 50 most played songs of the last week, 100 of
 *  the last month and 200 of the last year. Your scrobbles (or the plays here, without Last.fm), and
 *  Spotify's and Apple Music's plays Last.fm hasn't got, as the desktop adds them up. */
object Repeat {
    val PERIODS = linkedMapOf("week" to (7 to 50), "month" to (30 to 100), "year" to (365 to 200))   // days back, songs
    private val made = HashMap<String, Pair<Double, List<Track>>>()
    var version by mutableStateOf(0)   // bumps when a chart is in
        private set
    private var busy: String? = null   // the chart being added up (not state: asked for while the list is built)

    /** The chart for period, if it's been added up (null: not yet; it's being added up). */
    fun chart(period: String): List<Track>? {
        version
        val got = made[period]
        if (got == null || now() - got.first > Stats.FOR) refresh(period)
        return got?.second
    }

    private fun refresh(period: String) {
        if (busy == period) return
        busy = period
        Music.bg({ top(period) }) { res, err ->
            busy = null
            if (err != null) Music.toast("Couldn't add up your plays: ${err.message?.take(100)}")
            made[period] = now() to (res ?: emptyList())
            version++
        }
    }

    /** (blocking) The chart. */
    private fun top(period: String): List<Track> {
        val (days, n) = PERIODS[period] ?: return emptyList()
        val since = now().toLong() - days * 86_400L
        val rows = ArrayList<SpotifyHistory.Play>()
        if (LastFm.connected) {
            val zone = ZoneId.systemDefault()
            val first = Instant.ofEpochSecond(since).atZone(zone)
            val periods = if (period == "year") (first.year..java.time.LocalDate.now().year).map { Wrapped.Period.Year(it) }
                else generateSequence(YearMonth.from(first)) { it.plusMonths(1) }.takeWhile { !it.isAfter(YearMonth.now()) }
                    .map { Wrapped.Period.Month(it) }.toList()
            for (p in periods) rows += Wrapped.scrobblePlays(Music.ctx, p).filter { it.t >= since }
        } else for (e in Music.plays.items.values.toList()) for (x in e.at) if (x >= since) rows += SpotifyHistory.Play(x, e.track.artist, e.track.title, e.track.album)
        val extra = (SpotifyHistory.plays + AppleHistory.timed()).filter { it.t >= since }
        rows += Stats.notScrobbled(extra, rows.map { it.t to it.title })
        val counts = LinkedHashMap<Pair<String, String>, Pair<SpotifyHistory.Play, Int>>()
        for (r in rows) {
            val k = Stats.statsKey(r.title, r.artist)
            counts[k] = (counts[k]?.first ?: r) to ((counts[k]?.second ?: 0) + 1)
        }
        val mine = Music.songIndex()
        return counts.values.sortedByDescending { it.second }.take(n).map { (p, c) ->
            Music.fmSong(p.title, p.artist, "", mine, p.album).copy(plays = c)
        }
    }
}
