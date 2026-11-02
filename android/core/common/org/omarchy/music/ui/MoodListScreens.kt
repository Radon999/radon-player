package org.omarchy.music.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.omarchy.music.Dj
import org.omarchy.music.GroupFacts
import org.omarchy.music.MoodLists
import org.omarchy.music.Moods
import org.omarchy.music.Music
import org.omarchy.music.Pin
import org.omarchy.music.Track
import org.omarchy.music.sortGroups

// ☰ › Mood lists: a playlist per mood, each with your songs in that mood as they were when it was made or last
// refreshed (MoodLists). The list looks and works like Smart playlists (Select, Sort, list or grid); one opens like a playlist.

/** The tab's sorts: the moods' own order, name, how many songs, recently refreshed. */
private val MOODLIST_SORTS = listOf("order", "name", "name-desc", "songs", "songs-least", "updated")
private val MOODLIST_SORT_NAMES = mapOf("order" to "Mood order", "updated" to "Recently refreshed")

private fun moodPin(mood: String) = Pin("mood", mood, mood)

/** "Last refreshed 3 days ago", or "Not made yet". */
private fun refreshedWords(mood: String): String = MoodLists.refreshed(mood).let { at ->
    if (at <= 0) "Not made yet" else "Last refreshed " + org.omarchy.music.Sync.ago(at.toLong())
}

/** A list's line under its name: "120 songs", or why it's empty. */
private fun countWords(mood: String, songs: List<Track>): String = when {
    songs.isNotEmpty() -> "%,d song%s".format(songs.size, if (songs.size != 1) "s" else "")
    MoodLists.list(mood) == null -> if (MoodLists.busy) "…" else "No songs yet"
    else -> "No songs"
}

/** The Mood lists tab: a list open, or the lists. */
internal fun LazyListScope.moodLists(ui: Ui) {
    MoodLists.version   // redraw as lists are made or refreshed
    Music.musicVersion
    val open = ui.openMine?.takeIf { it in Moods.ALL }
    if (open != null) { moodListOpen(ui, open); return }
    val all = Moods.ALL.map { it to MoodLists.songs(it) }
    val sort = sortNow("moodlists", MOODLIST_SORTS)
    val shown = sortGroups(all.filter { fits(ui, it.first) }, sort) { (mood, songs) ->
        GroupFacts(mood, songs = songs, updated = MoodLists.refreshed(mood)) }
    val picking = ui.pickingIn("moodlists")
    val picked = ui.albumsPicked
    fun menu(mood: String, songs: List<Track>) {
        if (picking) { picked[mood] = songs; return }
        val sorted = Music.sortedPlaylist(songs)
        ui.choices = mood to listOfNotNull(
            "Refresh" to { MoodLists.refresh(listOf(mood)) },
            if (sorted.isNotEmpty()) "Play" to { Music.playList(sorted) } else null,
            if (sorted.isNotEmpty()) "Shuffle" to { Music.playList(sorted, shuffled = true) } else null,
            pinChoice(moodPin(mood)),
            if (sorted.isNotEmpty()) "Select" to { ui.startPick("moodlists", mood, songs) } else null,
            if (sorted.isNotEmpty()) "Export" to { ui.exportFor = mood to sorted } else null)
    }
    fun tapList(mood: String, songs: List<Track>) {
        if (picking) { if (mood in picked) picked.remove(mood) else picked[mood] = songs; return }
        ui.openMine = mood
    }
    item {
        if (picking) PickBar(ui, "mood lists", listOf("Refresh" to { MoodLists.refresh(picked.keys.toList()); picked.clear(); ui.pickingAlbums = false }))
        else Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(if (MoodLists.busy) "Refreshing…" else "Refresh all", false) { MoodLists.refresh(Moods.ALL) }
            Chip("Select", false) { ui.startPick("moodlists") }
            SortMenu("moodlists", MOODLIST_SORTS, names = MOODLIST_SORT_NAMES)
        }
        if (!picking) Text("A playlist for each mood, with your songs in that mood. They stay as they are until you refresh " +
            "them: Refresh picks up the songs that got that mood since.", style = T.sub, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
    }
    laidOutItems(shown, ui, key = { "ml-" + shown[it].first }, row = { i ->
        val (mood, songs) = shown[i]
        MoodListRow(mood, countWords(mood, songs) + if (MoodLists.list(mood) != null) " · " + refreshedWords(mood).replaceFirstChar { it.lowercase() } else "",
            checked = if (picking) mood in picked else null, onLongClick = { menu(mood, songs) }) { tapList(mood, songs) }
    }) { i, mod, compact ->
        val (mood, songs) = shown[i]
        Tile(null, mood, countWords(mood, songs), mod, compact, ico = Ico.MOOD, lit = picking && mood in picked,
            onLongClick = { menu(mood, songs) }, over = { MoodSwatch(mood, Modifier.fillMaxSize(), if (compact) 26.dp else 40.dp) }) { tapList(mood, songs) }
    }
}

/** A mood list's row: its colour, its name, how many songs and when it was refreshed. */
@Composable
private fun MoodListRow(mood: String, sub: String, checked: Boolean?, onLongClick: () -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(R.card)
        .pressable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (checked != null) Tick(checked, Modifier.padding(end = 10.dp))   // picking: a tick, and a tap ticks it
        MoodSwatch(mood, Modifier.size(52.dp).clip(R.card), 24.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(mood, style = T.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = T.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A mood list open: Play, Shuffle, DJ, Pin, Refresh, Export, when it was refreshed, its songs. */
private fun LazyListScope.moodListOpen(ui: Ui, mood: String) {
    item { BackRow(mood) { ui.openMine = null } }
    val songs = MoodLists.songs(mood)
    val sorted = Music.sortedPlaylist(songs)
    item {
        ListActions(sorted, onDj = { ui.djFrom(Dj.From("songs", name = "$mood mood list", songs = sorted)) }, extra = listOfNotNull(
            (if (MoodLists.busy) "Refreshing…" else "Refresh") to { MoodLists.refresh(listOf(mood)) },
            if (sorted.isNotEmpty()) "Export" to { ui.exportFor = mood to sorted } else null,
        ), pin = moodPin(mood))
        Text(refreshedWords(mood) + " · Refresh picks up your songs that are ${Moods.adjective(mood)} now.", style = T.sub,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
    if (sorted.isEmpty()) item {
        Empty(if (MoodLists.busy) "Making it…" else "No songs in it yet",
            "Songs get their moods in the Moods tab (Scan moods). Once some are ${Moods.adjective(mood)}, tap Refresh.")
    } else { item { PlaylistSortChips() }; tracks(sorted, ui, "mood") }
}
