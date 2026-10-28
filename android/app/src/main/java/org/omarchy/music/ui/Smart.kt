package org.omarchy.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.omarchy.music.Moods
import org.omarchy.music.Music
import org.omarchy.music.Smart

/** New smart playlist (or Edit… one): a name, and the moods and genres it takes your songs from. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartDialog(ui: Ui) {
    val pl = ui.smartEdit
    val rule = pl?.smart ?: Smart()
    var name by remember(pl) { mutableStateOf(pl?.name ?: "") }
    val moods = remember(pl) { mutableStateListOf<String>().apply { addAll(rule.moods) } }
    val genres = remember(pl) { mutableStateListOf<String>().apply { addAll(rule.genres) } }
    val known = remember(pl) { (Music.genresHere(24) + rule.genres).distinct() }
    fun close() { ui.smartNew = false; ui.smartEdit = null }
    AlertDialog(onDismissRequest = { close() }, containerColor = C.light,
        title = { Text(if (pl != null) "Edit the smart playlist" else "New smart playlist", style = T.head) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("It fills itself with your songs in any of the moods you pick, of any of the genres you pick (pick none of one: any). " +
                    "Moods come from the mood scan (Utilities › Moods).", style = T.sub)
                Field(name, "Name") { name = it }
                Section("MOODS AND FEELINGS")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Moods.MOODS.keys.forEach { m ->
                        Chip(m.replaceFirstChar { it.uppercase() }, m in moods) { if (m in moods) moods.remove(m) else moods.add(m) }
                    }
                }
                Section("GENRES")
                if (known.isEmpty()) Text("Your songs' tags name no genres yet (the tagger fills them in).", style = T.sub)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    known.forEach { g -> Chip(g, g in genres) { if (g in genres) genres.remove(g) else genres.add(g) } }
                }
            }
        },
        confirmButton = {
            TextButton({
                val smart = Smart(Moods.MOODS.keys.filter { it in moods }, genres.toList())
                if (smart.moods.isEmpty() && smart.genres.isEmpty()) { Music.toast("Pick a mood or a genre for it"); return@TextButton }
                val nm = name.trim().ifEmpty { smart.words() }
                if (pl != null) Music.editSmartPlaylist(pl.id, nm, smart)
                else { val id = Music.newSmartPlaylist(nm, smart); ui.openMine = id }
                close()
            }) { Text(if (pl != null) "Save" else "Make it", color = C.accent) }
        },
        dismissButton = { TextButton({ close() }) { Text("Cancel", color = C.muted) } })
}
