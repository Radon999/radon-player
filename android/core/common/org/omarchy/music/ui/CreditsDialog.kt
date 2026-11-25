package org.omarchy.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.omarchy.music.Credits
import org.omarchy.music.Music
import org.omarchy.music.SongCreditRules
import org.omarchy.music.Track

/** A song's Options › Credits: who wrote, produced and played on it, in groups. A name opens the songs of yours that
 *  person worked on (each plays on a tap), with Look through my music to find more of them. */
@Composable
fun CreditsDialog(ui: Ui, t: Track) {
    var person by remember(t.id) { mutableStateOf<String?>(null) }
    val close = { ui.creditsFor = null }
    AlertDialog(onDismissRequest = close, containerColor = C.light,
        title = { Text(person ?: "Credits", style = T.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = { val p = person; if (p != null) PersonSongs(p) else SongCredits(t) { person = it } },
        confirmButton = { TextButton(close) { Text("Close", color = C.muted) } },
        dismissButton = { if (person != null) TextButton({ person = null }) { Text("‹ Credits", color = C.accent) } })
}

/** The song's credits, looked up (once: they're kept) as the dialog opens. */
@Composable
private fun SongCredits(t: Track, onPerson: (String) -> Unit) {
    var saved by remember(t.id) { mutableStateOf<Credits.Saved?>(null) }
    var done by remember(t.id) { mutableStateOf(false) }
    LaunchedEffect(t.id) { Music.bg({ Credits.get(t) }) { r, _ -> saved = r; done = true } }
    val groups = remember(saved) { saved?.let { SongCreditRules.grouped(it.credits) }.orEmpty() }
    Column {
        Text("“${t.title}” — ${t.artist}", style = T.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val s = saved
        when {
            !done -> Said("Looking up who made it…")
            s == null -> Said("Couldn't look them up: Tidal, Genius and MusicBrainz can't be reached. Try again when you're online")
            groups.isEmpty() -> Said("No credits found for this song. It's looked up again in a month, in case they're added")
            else -> {
                LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 6.dp)) {
                    groups.forEach { g ->
                        item { Text(g.title.uppercase(), style = T.sub.copy(color = C.accent), modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) }
                        g.people.forEach { p ->
                            item {
                                Column(Modifier.fillMaxWidth().clickable { onPerson(p.name) }.padding(vertical = 5.dp)) {
                                    Text(p.name, style = T.body)
                                    if (p.roles.isNotEmpty()) Text(p.roles.joinToString(", "), style = T.sub)
                                }
                            }
                        }
                    }
                }
                Said("From ${s.from}. Tap a name for the songs of yours they worked on")
            }
        }
    }
}

/** Your songs person worked on: under their name, or in the credits kept for your songs. Each plays on a tap. */
@Composable
private fun PersonSongs(person: String) {
    var list by remember(person) { mutableStateOf<List<Pair<Track, List<String>>>?>(null) }
    // Found again as Look through my music goes (every ten songs, not every song)
    LaunchedEffect(person, if (Credits.progress == null) Credits.version else Credits.version / 10) {
        Music.bg({ Credits.workedOn(person) }) { r, _ -> list = r.orEmpty() } }
    val l = list
    Column {
        Said(when {
            l == null -> "Looking through your songs…"
            l.isEmpty() -> "None of your songs name $person yet"
            else -> "%,d song%s of yours $person worked on".format(l.size, if (l.size != 1) "s" else "")
        })
        if (!l.isNullOrEmpty()) LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 4.dp)) {
            items(l.size) { i ->
                val (s, how) = l[i]
                Row(Modifier.fillMaxWidth().clickable { Music.playFrom(l.map { it.first }, i, radio = false) }.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    ArtImage(artModel(s), 40.dp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(s.title, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${s.artist} · ${how.joinToString(", ")}", style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Said(creditsScanSaid().ifEmpty { "Only the songs whose credits have been looked up are found by them" } +
            ". Look through my music looks up the rest in the background, a song every few seconds" +
            (if (onPhone) ", only while the phone is charging" else ""))
        Row(Modifier.padding(top = 6.dp)) { CreditsScanButton() }
    }
}

@Composable
private fun Said(text: String) = Text(text, style = T.sub, modifier = Modifier.padding(top = 6.dp))

/** What Look through my music is doing, in a line: how far it is, waiting for the phone to charge, or how many of your
 *  songs have their credits looked up. */
@Composable
fun creditsScanSaid(): String {
    var counts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Counted when it isn't looking (while it is, how far it is is said instead)
    LaunchedEffect(Credits.version, Credits.progress == null) {
        if (Credits.progress == null) Music.bg({ Credits.counted() }) { r, _ -> counts = r } }
    val p = Credits.progress
    val c = counts
    return when {
        p != null && Credits.waiting -> "Waiting for the phone to charge · %,d of %,d looked up".format(p.first, p.second)
        p != null -> "Looking through your music… %,d of %,d".format(p.first, p.second)
        c != null -> "Credits looked up for %,d of your %,d songs".format(c.first, c.second)
        else -> ""
    }
}

/** Look through my music, or Stop while it's looking. */
@Composable
fun CreditsScanButton() {
    if (Credits.progress != null) Pill("Stop", quiet = true) { Credits.stop() }
    else Pill("Look through my music", quiet = true) { Credits.scan() }
}
