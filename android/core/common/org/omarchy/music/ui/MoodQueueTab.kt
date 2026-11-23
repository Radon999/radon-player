package org.omarchy.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.omarchy.music.MoodQueue
import org.omarchy.music.MoodRules
import org.omarchy.music.Moods
import org.omarchy.music.Music

/** Utilities › Queued mood edits: the songs you put aside to look at their moods (Queue a mood edit), and the ones the
 *  sound check found, each with its moods to tick and untick and Save, which writes them into the file as the Tagger
 *  does. A suggestion from the sound check shows with why, and Use it (or Use every suggestion) takes it. */
fun androidx.compose.foundation.lazy.LazyListScope.moodQueueView(ui: Ui) {
    Music.musicVersion; Moods.version
    val items = MoodQueue.items()
    item {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text("Songs whose moods you want to look at: select songs anywhere (in a DJ set, say) and pick Queue a mood edit. " +
                "Tick the moods each should have and Save: they're written into the file, as the Tagger does.", style = T.sub)
            Text("The sound check hears your songs (only their sound: no words read, nothing asked of the AI) and adds the ones " +
                "that sound plainly otherwise than their moods say, a loud, fast song in Chill or a soft, slow one in Hype, " +
                "with what they should be.", style = T.sub, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                val c = MoodQueue.checking
                if (c != null) {
                    Text("Checking the sound: %,d of %,d".format(c.first, c.second), style = T.sub.copy(color = C.accent))
                    Pill("Stop", quiet = true) { MoodQueue.stopCheck() }
                } else Pill("Check moods against the sound") { MoodQueue.checkSound() }
                val withFix = items.filter { it.suggest.isNotEmpty() }
                if (withFix.isNotEmpty()) Pill("Use every suggestion (%,d)".format(withFix.size), quiet = true) {
                    ui.confirm = "Write the suggested moods into %,d song files?".format(withFix.size) to {
                        withFix.forEach { MoodQueue.write(it, it.suggest) } }
                }
                // Listen to them all, one after another, while you go down the list
                val here = items.mapNotNull { MoodQueue.song(it) }
                if (here.isNotEmpty()) Pill("Listen to them all", quiet = true) { Music.playList(here) }
                if (items.isNotEmpty()) Pill("Clear the list", quiet = true) { ui.confirm = "Take every song off the list? Their moods stay as they are." to { MoodQueue.clear() } }
            }
        }
    }
    if (items.isEmpty()) { item { Empty("No queued mood edits", "Select songs and pick Queue a mood edit, or check moods against the sound.") }; return }
    item { Section("%,d SONG%s".format(items.size, if (items.size != 1) "S" else "")) }
    for (i in items) item(key = "mq-" + i.title + "\t" + i.artist) { QueuedMoodRow(i) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueuedMoodRow(i: MoodQueue.Item) {
    val t = remember(i.title, i.artist, Music.musicVersion) { MoodQueue.song(i) }
    val now = remember(t, Moods.version) { t?.let { runCatching { Moods.all(it) }.getOrDefault(emptyList()) }.orEmpty() }
    var picked by remember(t, now) { mutableStateOf(now) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(C.light)
        .border(1.dp, C.line, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtImage(t?.let { artModel(it) }, 44.dp, Modifier.clickable { t?.let { Music.playList(listOf(it)) } })
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(i.title, style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(i.artist + " · now: " + (now.joinToString(", ").ifEmpty { "no mood" }), style = T.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (t == null) { Text("Not on this device: its file is on your other one.", style = T.sub, modifier = Modifier.padding(top = 6.dp)); return@Column }
        // Listen before deciding: play it (or pause), and jump on to hear its quiet and loud parts
        val playing = Music.current()?.let { org.omarchy.music.statsKey(it.title, it.artist) == i.key } == true
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!playing) Pill("▶ Listen") { Music.playList(listOf(t)) }
            else {
                Pill(if (Music.paused) "▶ Play" else "⏸ Pause") { Music.togglePause() }
                val len = org.omarchy.music.PlaybackCore.duration
                if (len > 30) {
                    Pill("Middle", quiet = true) { Music.seek(len * 0.45) }
                    Pill("+20 s", quiet = true) { Music.seek((org.omarchy.music.PlaybackCore.position + 20).coerceAtMost(len - 1)) }
                }
                Text("playing", style = T.sub.copy(color = C.accent))
            }
        }
        if (i.suggest.isNotEmpty()) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Suggested: ${i.suggest.joinToString(", ")}. ${i.why.replaceFirstChar { it.uppercase() }}", style = T.sub.copy(color = C.accent),
                modifier = Modifier.weight(1f).padding(end = 8.dp))
            Pill("Use it", quiet = true) { picked = i.suggest }
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (m in MoodRules.ALL) Chip(m, m in picked) { picked = if (m in picked) picked - m else (picked + m).take(MoodRules.MOST) }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(if (picked == now) "Keep as it is" else "Save to the file") {
                if (picked == now) MoodQueue.remove(i) else MoodQueue.write(i, picked)
            }
            Pill("Take off the list", quiet = true) { MoodQueue.remove(i) }
            if (picked.size >= MoodRules.MOST) Text("(at most ${MoodRules.MOST})", style = T.sub)
        }
    }
}
