package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** Repeats ([RepeatLoopRules]): each time a song plays again by itself with Repeat one on, kept in a file of this
 *  device's (repeats.json, beside the app's other data), with the repeats your imported history and scrobbles show
 *  from before the app counted them. The Repeats tab, the DJ and Wrapped read them. */
object RepeatLoops {
    /** Goes up when the count changes, so the screens showing it draw again. */
    var version by mutableStateOf(0)
        private set
    private var loaded: RepeatLoopRules.Saved? = null
    private var merged: RepeatLoopRules.Saved? = null
    private fun file(): File? = runCatching { File(Music.ctx.filesDir, "repeats.json") }.getOrNull()

    /** As kept: the ones counted here, and the ones from your history beside them. */
    @Synchronized fun saved(): RepeatLoopRules.Saved = loaded ?: (runCatching {
        file()?.takeIf { it.isFile }?.readText()?.let { JSON.decodeFromString(RepeatLoopRules.Saved.serializer(), it) } }.getOrNull() ?: RepeatLoopRules.Saved()).also { loaded = it }

    /** Both as one: what every list and count shows. */
    @Synchronized fun all(): RepeatLoopRules.Saved = merged ?: RepeatLoopRules.all(saved()).also { merged = it }

    @Synchronized private fun keep(s: RepeatLoopRules.Saved) {
        loaded = s; merged = null
        runCatching { file()?.writeText(JSON.encodeToString(RepeatLoopRules.Saved.serializer(), s)) }
    }

    private val zone get() = java.time.ZoneId.systemDefault()
    private fun day(at: Long = System.currentTimeMillis()): String = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toString()
    /** The day a number of days ago, as the counts keep days ("": ever). */
    fun since(days: Int): String = if (days <= 0) "" else day(System.currentTimeMillis() - (days - 1) * 86_400_000L)

    /** What there is to say as a song starts over ([RepeatFactsRules.remark]). */
    class Remark(val kind: String, val run: Int, val total: Int, val best: Int)

    const val NOTES = "repeat_notes"   // a note at a song's 10th, 25th, 50th… repeat and at a new longest run, with the DJ off
    const val NUDGE = "repeat_nudge"   // the DJ offers something else after a long run

    /** The song has just started over by itself (Repeat one). What there is to say about it, if anything. */
    fun again(t: Track): Remark? = runCatching {
        val before = all().songs[RepeatLoopRules.key(t)]
        val now = System.currentTimeMillis()
        val s = RepeatLoopRules.add(saved(), t, day(now), now, java.time.Instant.ofEpochMilli(now).atZone(zone).hour)
        keep(s); version++
        val total = (before?.n ?: 0) + 1
        RepeatFactsRules.remark(s.run, total, before?.best ?: 0, Prefs.on(NUDGE, true))?.let { Remark(it, s.run, total, maxOf(before?.best ?: 0, s.run)) }
    }.getOrNull()

    /** Another song began: the run in one go is over. */
    fun moved() { val s = saved(); if (s.run != 0) keep(RepeatLoopRules.broke(s)) }

    /** How often this song was repeated, ever; its longest run; when it was last repeated (0: never). */
    fun of(t: Track): RepeatLoopRules.Loop? = all().songs[RepeatLoopRules.key(t)]

    /** With the DJ off: a milestone or a new longest run as a note on the screen. */
    fun note(t: Track, r: Remark): String = when (r.kind) {
        "milestone" -> "“${t.title}” has now been ${RepeatLoopRules.times(r.total)}"
        "record" -> "A new longest run for “${t.title}”: ${r.run} times in a row"
        else -> ""
    }

    // ---- from your imported history ----
    /** What the last look through your history found, in words ("": not looked yet). */
    var historyText by mutableStateOf("")
        private set
    var historyBusy by mutableStateOf(false)
        private set

    /** (blocking) Works out the repeats your imported history and Last.fm scrobbles show ([RepeatFactsRules.inferred]),
     *  for the days before the app's own count began, and keeps them beside the counted ones. Done again, it replaces
     *  what it found before (never adds to it). */
    fun fromHistory() {
        if (historyBusy) return
        historyBusy = true
        try {
            fun plays(l: List<SpotifyHistory.Play>) = l.filter { it.t > 0 }.sortedBy { it.t }.map { RepeatFactsRules.Play(it.title, it.artist, it.t, it.ms / 1000.0) }
            val sources = listOf(
                runCatching { plays(SpotifyHistory.plays) }.getOrDefault(emptyList()),
                runCatching { plays(DeezerHistory.plays) }.getOrDefault(emptyList()),
                runCatching { plays(YouTubeHistory.plays) }.getOrDefault(emptyList()),
                runCatching { plays(TidalHistory.plays) }.getOrDefault(emptyList()),
                // Last.fm: every scrobble (fetched year by year if Wrapped hasn't yet; the ones kept already if it can't be reached)
                runCatching {
                    val rows = if (LastFm.connected) runCatching { Wrapped.allScrobbles(Music.ctx) }.getOrElse { Wrapped.keptScrobbles(Music.ctx) } else emptyList()
                    rows.filter { it.t > 0 }.distinctBy { it.t to it.title }.sortedBy { it.t }.map { RepeatFactsRules.Play(it.title, it.artist, it.t, it.ms / 1000.0) }
                }.getOrDefault(emptyList()),
                // What was played in this app before it counted repeats: its listening diary (when each play began and how
                // long it was heard), and the times it keeps for each song's plays
                runCatching { ListenLog.all().filter { it.t > 0 && it.title.isNotEmpty() }.sortedBy { it.t }.map { RepeatFactsRules.Play(it.title, it.artist, it.t, it.secs) } }.getOrDefault(emptyList()),
                runCatching { Music.plays.items.values.toList().flatMap { e -> e.at.map { RepeatFactsRules.Play(e.track.title, e.track.artist, it) } }.sortedBy { it.t } }.getOrDefault(emptyList()),
            ).filter { it.isNotEmpty() }
            val before = saved().since.ifEmpty { day() }
            val (songs, hours) = RepeatLoopRules.fromHistory(sources, { day(it * 1000) }, { java.time.Instant.ofEpochSecond(it).atZone(zone).hour }, before)
            val s = saved()
            // the hours counted here (from the day counting began) stay; the history's are for the months before
            val mine = s.hours.filterKeys { it.take(7) >= before.take(7) }
            keep(s.copy(imports = songs, hours = hours.filterKeys { it.take(7) < before.take(7) } + mine, since = before))
            version++
            val n = songs.values.sumOf { it.n }
            historyText = if (sources.isEmpty()) "No imported history or scrobbles to look through yet"
                else if (n == 0) "Looked through ${"%,d".format(sources.sumOf { it.size })} plays: no song played again the moment it ended"
                else "Found ${"%,d".format(n)} repeats of ${"%,d".format(songs.size)} songs in ${"%,d".format(sources.sumOf { it.size })} plays of your history"
        } catch (e: Exception) { historyText = "Couldn't look through your history: ${e.message}" }
        finally { historyBusy = false }
    }
}
