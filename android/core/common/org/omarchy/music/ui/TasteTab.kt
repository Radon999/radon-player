package org.omarchy.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.omarchy.music.HabitRules
import org.omarchy.music.Music
import org.omarchy.music.SignalRules
import org.omarchy.music.Signals
import org.omarchy.music.TasteService

private var tasteShow by mutableStateOf("phases")

/** Stats › Taste: what autoplay, radios and the DJ learn from: your phases (new loves, rising, long-time favourites,
 *  faded), who you skip, the songs you always hear to the end, how the new songs they brought went, and everything
 *  you said (More like this, Less like this, Not for autoplay), each to undo with a tap. */
fun androidx.compose.foundation.lazy.LazyListScope.tasteView(ui: Ui) {
    Music.musicVersion
    item {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            for ((k, l) in listOf("phases" to "Phases", "skips" to "Who you skip", "finished" to "Heard to the end",
                "found" to "Discoveries", "said" to "What you said"))
                Chip(l, tasteShow == k) { tasteShow = k }
        }
    }
    item {
        Text("Autoplay, radios and the DJ learn from all of this: what you play after what, the artists you play together, " +
            "what you skip in its first seconds, and what you say with a song's or an artist's More like this, Less like this " +
            "and Not for autoplay.", style = T.sub, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
    when (tasteShow) {
        "phases" -> phases(ui)
        "skips" -> skipsByArtist(ui)
        "finished" -> finished()
        "found" -> discoveries()
        else -> said()
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.phases(ui: Ui) {
    val taste = TasteService.current
    if (taste == null) {
        Music.bg({ TasteService.get() }) { _, _ -> Music.musicVersion++ }
        item { Empty("Adding up your listening…", "A moment: every play there is is counted.") }; return
    }
    val habits = taste.habits
    if (habits == null || habits.phase.isEmpty()) { item { Empty("No phases yet", "They show once there are a few months of plays to go by.") }; return }
    // Each artist with the plays their phase was worked out from (the same plays, the same artist), so the number fits
    // the heading; and nothing hidden from stats (Hide from stats)
    for ((phase, title, sub) in listOf(
        Triple(HabitRules.NEW_LOVE, "NEW LOVES", "First played in the last four months, and a lot since"),
        Triple(HabitRules.RISING, "RISING", "Much more lately than usual"),
        Triple(HabitRules.LONG_TIME, "LONG-TIME FAVOURITES", "Played across three years or more, and still now"),
        Triple(HabitRules.FADED, "FADED", "Played a lot once, nothing in the last six months"))) {
        val who = habits.phase.filterValues { it == phase }.keys
            .map { k -> k to (habits.plays[k] ?: (0 to k)) }
            .filter { (_, p) -> !org.omarchy.music.StatHides.artist(p.second) }
            .sortedByDescending { it.second.first }.take(30)
        if (who.isEmpty()) continue
        item { Section(title) }
        item { Text(sub, style = T.sub, modifier = Modifier.padding(horizontal = 12.dp)) }
        for ((_, p) in who) item {
            val (n, name) = p
            Line(name, "%,d plays".format(n)) { ui.pages += Ui.Page.Artist(name, null) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.skipsByArtist(ui: Ui) {
    val rows = runCatching { Music.songStats.kept() }.getOrDefault(emptyList()).filter { it.track != null }
        .groupBy { it.track!!.artist.split(",")[0].trim() }
        .map { (a, es) -> Triple(a, es.sumOf { it.skips }, es.sumOf { it.full }) }
        .filter { it.second + it.third >= 10 }.sortedByDescending { it.second.toDouble() / (it.second + it.third) }.take(60)
    val early = Signals.all().values.filter { it.early.isNotEmpty() && it.title.isNotEmpty() }.sortedByDescending { it.early.size }.take(30)
    if (rows.isEmpty() && early.isEmpty()) { item { Empty("Not enough skips yet", "An artist shows here after ten of their songs were skipped or heard out.") }; return }
    if (rows.isNotEmpty()) {
        item { Section("ARTISTS YOU SKIP MOST") }
        for ((a, sk, fu) in rows) item { Line(a, "skipped %d%% · %,d skips, %,d heard out".format(sk * 100 / (sk + fu), sk, fu)) { ui.pages += Ui.Page.Artist(a, null) } }
    }
    if (early.isNotEmpty()) {
        item { Section("SKIPPED IN THE FIRST SECONDS") }
        item { Text("Twice in two months and autoplay, radios and the DJ leave it out for a while.", style = T.sub, modifier = Modifier.padding(horizontal = 12.dp)) }
        for (e in early) item { Line("“${e.title}”", "${e.artist} · %d×".format(e.early.size)) {} }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.finished() {
    val rows = runCatching { Music.songStats.kept() }.getOrDefault(emptyList()).filter { it.track != null && it.full >= 3 }
        .sortedWith(compareByDescending<org.omarchy.music.SkipEntry> { it.full * 1.0 / (it.full + it.skips) }.thenByDescending { it.full }).take(80)
    if (rows.isEmpty()) { item { Empty("Nothing yet", "Songs you hear to the end three times or more show here.") }; return }
    item { Section("ALWAYS HEARD TO THE END") }
    for (e in rows) item {
        val t = e.track!!
        Line("“${t.title}”", "${t.artist} · heard out %,d× · skipped %,d×".format(e.full, e.skips)) { Music.playList(listOf(Music.mineOf(t))) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.discoveries() {
    val es = Signals.all().values.filter { it.introAt > 0 }.sortedByDescending { it.introAt }
    if (es.isEmpty()) { item { Empty("No discoveries yet", "Songs new to you that the DJ or autoplay plays show here, with how they went.") }; return }
    val kept = es.count { it.introOut == "kept" }; val heard = es.count { it.introOut == "heard" }; val skipped = es.count { it.introOut == "skipped" }
    item {
        Text("%,d new songs: %,d kept (saved, favourited or put in a playlist), %,d heard to the end, %,d skipped. ".format(es.size, kept, heard, skipped) +
            "Artists whose new songs keep getting skipped aren't brought again.", style = T.sub, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
    val zone = java.time.ZoneId.systemDefault()
    for (e in es.take(150)) item {
        val how = when (e.introOut) { "kept" -> "kept ✓"; "heard" -> "heard to the end"; "skipped" -> "skipped"; else -> "not finished" }
        val day = java.time.Instant.ofEpochSecond(e.introAt).atZone(zone).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
        Line("“${e.title}”", "${e.artist} · ${if (e.introVia == "dj") "the DJ" else "autoplay"} · $day · $how") {}
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.said() {
    val es = Signals.all().entries.filter { (_, e) -> e.up > 0 || e.down > 0 || e.noAuto > 0 }.sortedByDescending { maxOf(it.value.up, it.value.down, it.value.noAuto) }
    if (es.isEmpty()) { item { Empty("Nothing said yet", "A song's or an artist's Options: More like this, Less like this, Not for autoplay.") }; return }
    item { Text("Tap one to undo it.", style = T.sub, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
    for ((k, e) in es) item {
        val artist = k.startsWith("artist:")
        val what = listOfNotNull(
            when (SignalRules.thumb(e)) { 1 -> "More like this"; -1 -> "Less like this"; else -> null },
            "Not for autoplay".takeIf { e.noAuto > 0 }).joinToString(" · ")
        Line(if (artist) e.artist else "“${e.title}”", (if (artist) "Artist" else e.artist) + " · " + what) {
            val t = org.omarchy.music.Track("", title = e.title, artist = e.artist)
            if (artist) { if (e.noAuto > 0) Signals.artistNoAuto(e.artist, false); if (SignalRules.thumb(e) != 0) Signals.artistThumb(e.artist, null) }
            else { if (e.noAuto > 0) Signals.noAuto(t, false); if (SignalRules.thumb(e) != 0) Signals.thumb(t, null) }
        }
    }
}

@Composable
private fun Line(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
