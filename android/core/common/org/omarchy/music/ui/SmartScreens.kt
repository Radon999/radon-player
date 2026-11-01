package org.omarchy.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.omarchy.music.Album
import org.omarchy.music.Dj
import org.omarchy.music.FolderItem
import org.omarchy.music.Moods
import org.omarchy.music.Music
import org.omarchy.music.Pin
import org.omarchy.music.Playlist
import org.omarchy.music.Smart
import org.omarchy.music.SmartLists
import org.omarchy.music.SmartRule
import org.omarchy.music.SmartRules
import org.omarchy.music.norm

// ☰ › Smart playlists: playlists that fill themselves by rules. The list looks and works like Playlists (folders,
// Select, Sort, list or grid); a smart playlist opens like one of yours; New smart playlist and Edit open the editor.

/** The smart playlist being made or changed (null: the list shows). */
object SmartEdit {
    data class Draft(val id: String?, val name: String, val smart: Smart)
    var draft by mutableStateOf<Draft?>(null)

    /** Open the editor: for pl, or for a new one. */
    fun start(pl: Playlist?) {
        val sm = pl?.smart
        draft = if (pl == null || sm == null) Draft(null, "", Smart(rules = listOf(SmartRules.fresh("genre"))))
            else Draft(pl.id, pl.name, sm.upgraded())
    }

    fun close() { draft = null }
}

/** A smart playlist's songs now ([] while they're being worked out). */
private fun songsOf(pl: Playlist) = SmartLists.songs(pl).orEmpty()

/** The Smart playlists tab: the editor, a smart playlist open, a folder open, or the list. */
internal fun LazyListScope.smartPlaylists(ui: Ui) {
    SmartEdit.draft?.let { smartEditor(ui, it); return }
    val open = ui.openMine?.let { Music.lists.get(it) }?.takeIf { it.smart != null }
    val folder = ui.openFolder?.let { Music.playlistFolders.get(it) }
    if (open != null) { smartOpen(ui, open); return }
    if (folder != null) { folderView(ui, folder); return }
    SmartLists.version   // redraw as songs are worked out
    item { ActionRow("＋  New smart playlist") { SmartEdit.start(null) } }
    item { ActionRow("＋  New folder") { ui.askName = Triple("New folder", "") { n -> Music.newFolder(n) } } }
    folderRows(ui)
    val lists = Music.lists.smart()
    if (lists.isNotEmpty() && Music.playlistFolders.items.isNotEmpty()) item { Section("YOUR SMART PLAYLISTS") }
    if (lists.isEmpty()) item {
        Section("NO SMART PLAYLISTS YET")
        Text("A smart playlist fills itself from your music by rules you pick: a genre, a mood, songs you haven't played in a " +
            "while, your 24-bit FLACs… and keeps up as your music changes. Make one, or $TAP a suggestion below.", style = T.sub,
            modifier = Modifier.padding(horizontal = 12.dp))
    }
    val plSort = sortNow("smartlists", PLAYLISTS_SORTS)
    val shown = org.omarchy.music.sortGroups(lists.filter { fits(ui, it.name) }, plSort) { pl ->
        org.omarchy.music.GroupFacts(pl.name, songs = songsOf(pl), created = pl.created, updated = pl.updated, added = pl.created) }
    val picking = ui.pickingIn("smartlists")
    val picked = ui.albumsPicked
    fun menu(pl: Playlist) {
        if (picking) { picked[pl.id] = songsOf(pl); return }
        ui.choices = pl.name to listOf(pinChoice(Pin("smart", pl.id, pl.name)),
            "Edit rules…" to { SmartEdit.start(pl) },
            "Rename…" to { ui.askName = Triple("Rename smart playlist", pl.name) { n -> Music.renamePlaylist(pl.id, n) } },
            "Put in a folder…" to { ui.folderFor = FolderItem.mine(pl) },
            "Select" to { ui.startPick("smartlists", pl.id, songsOf(pl)) },
            "Delete…" to { ui.confirm = "Delete the smart playlist “${pl.name}”? Your songs stay where they are." to { Music.deletePlaylist(pl.id) } })
    }
    fun tapList(pl: Playlist) {
        if (picking) { if (pl.id in picked) picked.remove(pl.id) else picked[pl.id] = songsOf(pl); return }
        ui.openMine = pl.id
    }
    item {
        if (picking) PickBar(ui, "smart playlists", listOf(
            "Delete…" to {
                val ids = picked.keys.toList()
                ui.confirm = ("Delete ${ids.size} smart playlist${if (ids.size != 1) "s" else ""}? " +
                    ids.mapNotNull { Music.lists.get(it)?.name }.joinToString(", ") { "“$it”" } + ". Your songs stay where they are.") to {
                    ids.forEach { Music.deletePlaylist(it) }; picked.clear(); ui.pickingAlbums = false }
            },
            "Rename…" to {
                fun next(ids: List<String>) {
                    val pl = ids.firstOrNull()?.let { Music.lists.get(it) } ?: return
                    ui.askName = Triple("Rename smart playlist", pl.name) { n -> Music.renamePlaylist(pl.id, n); next(ids.drop(1)) }
                }
                next(picked.keys.toList())
            },
            "Edit" to { picked.keys.firstOrNull()?.let { Music.lists.get(it) }?.let { pl -> picked.clear(); ui.pickingAlbums = false; SmartEdit.start(pl) } },
            "Favorite" to { Music.setFavorite(picked.values.flatten().distinctBy { it.id }, true) },
        ))
        else if (lists.isNotEmpty()) Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Select", false) { ui.startPick("smartlists") }
            SortMenu("smartlists", PLAYLISTS_SORTS, names = mapOf("order" to "Last changed first"))
        }
    }
    laidOutItems(shown, ui, key = { "spl-" + shown[it].id }, row = { i ->
        val pl = shown[i]
        val songs = SmartLists.songs(pl)
        val cover = songs?.firstOrNull { it.thumb.isNotEmpty() || it.isFile }
        PlaylistRow(Album(pl.id, pl.name, if (songs == null) "…" else "", "", songs?.size?.toString() ?: ""), cover?.let { artModel(it) },
            onLongClick = { menu(pl) }, checked = if (picking) pl.id in picked else null) { tapList(pl) }
    }) { i, mod, compact ->
        val pl = shown[i]
        val songs = SmartLists.songs(pl)
        val cover = songs?.firstOrNull { it.thumb.isNotEmpty() || it.isFile }
        Tile(cover?.let { artModel(it, tilePx(compact)) }, pl.name, if (songs == null) "…" else "${songs.size} songs", mod, compact,
            ico = Ico.SPARKLE, lit = picking && pl.id in picked, onLongClick = { menu(pl) }) { tapList(pl) }
    }
    // Ready-made ones, a tap each (the ones you have already, by name, left out).
    val have = lists.map { norm(it.name) }.toSet()
    val ideas = SmartRules.IDEAS.filter { norm(it.name) !in have }
    if (ideas.isNotEmpty() && !picking) {
        item(key = "ideas-head") { Section("SUGGESTED · $TAP ONE TO ADD IT".uppercase()) }
        items(ideas.size, key = { "idea-" + ideas[it].name }) { i ->
            val idea = ideas[i]
            PlaylistRow(Album("idea-$i", "＋  " + idea.name, idea.about)) { SmartLists.addIdea(idea) }
        }
    }
}

/** How a smart playlist is set: "Random · 50 songs · live". */
private fun settingsWords(sm: Smart) = listOf(SmartRules.ORDERS[sm.order] ?: sm.order,
    if (sm.limit > 0) "at most ${sm.limit} songs" else "every song that fits",
    if (sm.live) "updates as your music changes" else "fixed: Refresh takes its songs again").joinToString(" · ")

/** A smart playlist open: Play, Shuffle, DJ, Edit…, the rules in words, its songs. */
private fun LazyListScope.smartOpen(ui: Ui, open: Playlist) {
    val sm = open.smart ?: return
    item { BackRow(open.name) { ui.openMine = null } }
    SmartLists.version
    val songs = SmartLists.songs(open)
    val sorted = Music.sortedPlaylist(songs.orEmpty())
    item {
        ListActions(sorted, onDj = { ui.djFrom(Dj.From("songs", name = open.name, songs = sorted)) }, extra = listOfNotNull(
            "Edit" to { SmartEdit.start(open) },
            "Rename" to { ui.askName = Triple("Rename smart playlist", open.name) { n -> Music.renamePlaylist(open.id, n) } },
            if (!sm.live) "Refresh" to { SmartLists.takeAgain(open) } else null,
            "Delete" to { ui.confirm = "Delete the smart playlist “${open.name}”? Your songs stay where they are." to { Music.deletePlaylist(open.id); ui.openMine = null } },
            "Export" to { ui.exportFor = open.name to sorted },
        ), pin = Pin("smart", open.id, open.name))
        Text(sm.words() + "\n" + settingsWords(sm), style = T.sub, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
    when {
        songs == null -> item { Section("FINDING ITS SONGS…") }
        sorted.isEmpty() -> item { Empty("No songs fit yet", "None of your songs fits its rules now. Edit them, or wait: it fills itself as your music changes.") }
        else -> { item { PlaylistSortChips() }; tracks(sorted, ui, "smart") }
    }
}

// ───────────────────────────── The editor ─────────────────────────────

private fun update(f: (SmartEdit.Draft) -> SmartEdit.Draft) { SmartEdit.draft?.let { SmartEdit.draft = f(it) } }
private fun setSmart(f: (Smart) -> Smart) = update { it.copy(smart = f(it.smart)) }
private fun setRule(i: Int, f: (SmartRule) -> SmartRule) = setSmart { s -> s.copy(rules = s.rules.mapIndexed { j, r -> if (j == i) f(r) else r }) }

/** A chip that opens a list to pick from: "Genre ▾". */
@Composable
private fun PickMenu(label: String, options: List<Pair<String, String>>, now: String?, pick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Chip("$label ▾", true) { open = true }
        DropdownMenu(open, { open = false }, containerColor = C.light) {
            options.forEach { (k, l) ->
                DropdownMenuItem({ Text((if (k == now) "✓  " else "     ") + l, style = T.body) }, { open = false; pick(k) })
            }
        }
    }
}

private fun fmtNum(n: Double) = if (n == Math.floor(n)) n.toLong().toString() else n.toString()

/** Ready numbers for a rule's number, by what it counts. */
private fun presets(unit: String): List<Double> = when {
    unit.startsWith("days") -> listOf(7.0, 14.0, 30.0, 90.0, 180.0, 365.0)
    unit == "minutes" -> listOf(2.0, 3.0, 5.0, 7.0, 10.0)
    unit == "skips" -> listOf(1.0, 3.0, 5.0, 10.0)
    else -> listOf(0.0, 1.0, 3.0, 5.0, 10.0, 25.0, 50.0)
}

/** The editor: a name, All or Any, the rules (each with a field, how, and what), order, limit, live, the preview, Save. */
private fun LazyListScope.smartEditor(ui: Ui, d: SmartEdit.Draft) {
    val sm = d.smart
    item(key = "se-head") {
        BackRow(if (d.id == null) "New smart playlist" else "Edit “${d.name}”") { SmartEdit.close() }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("NAME", style = T.section)
            Field(d.name, "Name it: Late-night chill, Gym…") { n -> update { it.copy(name = n) } }
            Text("SONGS THAT FIT", style = T.section, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("All the rules", !sm.any) { setSmart { it.copy(any = false) } }
                Chip("Any rule", sm.any) { setSmart { it.copy(any = true) } }
            }
        }
    }
    items(sm.rules.size, key = { "se-rule-$it" }) { i -> RuleCard(i, sm.rules[i]) }
    item(key = "se-rest") { EditorFooter(ui, d) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleCard(i: Int, r: SmartRule) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp).clip(R.card).border(1.dp, C.line, R.card)
        .background(C.fg.copy(alpha = 0.03f)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PickMenu(SmartRules.FIELDS[r.field] ?: r.field, SmartRules.FIELDS.toList(), r.field) { f ->
                    if (f != r.field) setRule(i) { SmartRules.fresh(f) } }
                val ops = SmartRules.ops(r.field)
                if (ops.size > 1) PickMenu(ops.firstOrNull { it.first == r.op }?.second ?: r.op, ops, r.op) { o -> setRule(i) { it.copy(op = o) } }
                else ops.firstOrNull()?.second?.takeIf { it.isNotEmpty() }?.let { Text(it, style = T.sub, modifier = Modifier.padding(6.dp)) }
            }
            IconButton(Ico.CLOSE, C.muted, 18.dp, desc = "Remove this rule") { setSmart { s -> s.copy(rules = s.rules.filterIndexed { j, _ -> j != i }) } }
        }
        RuleValue(i, r)
    }
}

/** What a rule looks for: chips to pick from, a box to type in, or a number. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleValue(i: Int, r: SmartRule) {
    @Composable fun chips(options: List<Pair<String, String>>) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (k, l) ->
                val on = r.targets.any { it.equals(k, true) }
                Chip(l, on) { setRule(i) { it.copy(value = "", values = if (on) it.targets.filter { t -> !t.equals(k, true) } else it.targets + k) } }
            }
        }
    }
    /** Typed words: commas between several ("Rock, Pop": either fits); ideas under it add one. */
    @Composable fun typed(hint: String, ideas: List<String>) {
        val text = r.value.ifEmpty { r.values.joinToString(", ") }
        Field(text, hint) { s -> setRule(i) { it.copy(value = s, values = s.split(',').map { w -> w.trim() }.filter { w -> w.isNotEmpty() }) } }
        if (ideas.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ideas.forEach { g ->
                val on = r.targets.any { it.equals(g, true) }
                Chip(g, on) {
                    val now = if (on) r.targets.filter { !it.equals(g, true) } else r.targets + g
                    setRule(i) { it.copy(value = now.joinToString(", "), values = now) }
                }
            }
        }
    }
    when (r.field) {
        "mood" -> {
            chips(Moods.ALL.map { it to it })
            Text("Moods come from Moods (☰ › Moods), as it reads your songs.", style = T.time)
        }
        "release" -> chips(SmartRules.RELEASES.toList())
        "format" -> chips(SmartRules.FORMATS.map { it to it })
        "genre" -> {
            val genres = remember { runCatching { Music.genresHere(16) }.getOrDefault(emptyList()) }
            typed("A genre, e.g. Rock (commas for several)", genres)
        }
        "artist" -> typed("Part of the artist's name (commas for several)", emptyList())
        "album" -> typed("Part of the album's name (commas for several)", emptyList())
        "title" -> typed("A word in the title (commas for several)", emptyList())
        "folder" -> {
            val folders = remember { runCatching { Music.folderSongs().map { it.first }.filter { it.isNotEmpty() } }.getOrDefault(emptyList()).take(40) }
            typed("A folder, e.g. Music/Rock/", folders)
        }
        else -> {
            val unit = SmartRules.unit(r.field, r.op) ?: return
            var text by remember(i, r.field, r.op) { mutableStateOf(fmtNum(r.n)) }
            val shown = if (text.isEmpty() || text.toDoubleOrNull() == r.n) text else fmtNum(r.n)   // a chip picked: its number
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    Field(shown, "A number") { s ->
                        val clean = s.filter { it.isDigit() || it == '.' }.take(6)
                        text = clean
                        clean.toDoubleOrNull()?.let { n -> setRule(i) { it.copy(n = n) } }
                    }
                }
                Text(unit, style = T.body)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                presets(unit).forEach { n -> Chip(fmtNum(n), r.n == n) { text = fmtNum(n); setRule(i) { it.copy(n = n) } } }
            }
        }
    }
}

/** Add a rule, order, limit, live, how many songs fit, Save and Cancel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorFooter(ui: Ui, d: SmartEdit.Draft) {
    val sm = d.smart
    var preview by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(sm) {
        preview = null
        kotlinx.coroutines.delay(350)   // wait till the typing stops
        preview = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { SmartLists.count(sm) }.getOrNull() }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var adding by remember { mutableStateOf(false) }
        Box {
            Pill("＋  Add a rule", quiet = true) { adding = true }
            DropdownMenu(adding, { adding = false }, containerColor = C.light) {
                SmartRules.FIELDS.forEach { (k, l) ->
                    DropdownMenuItem({ Text(l, style = T.body) }, { adding = false; setSmart { s -> s.copy(rules = s.rules + SmartRules.fresh(k)) } })
                }
            }
        }
        Text("ORDER", style = T.section, modifier = Modifier.padding(top = 6.dp))
        PickMenu(SmartRules.ORDERS[sm.order] ?: sm.order, SmartRules.ORDERS.toList(), sm.order) { o -> setSmart { it.copy(order = o) } }
        Text("HOW MANY", style = T.section, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SmartRules.LIMITS.forEach { n -> Chip(if (n == 0) "All" else "$n songs", sm.limit == n) { setSmart { it.copy(limit = n) } } }
        }
        Text("KEEP IT", style = T.section, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Live: updates as your music changes", sm.live) { setSmart { it.copy(live = true) } }
            Chip("Fixed: keeps the songs it has", !sm.live) { setSmart { it.copy(live = false) } }
        }
        Text(when (val n = preview) { null -> "Counting the songs that fit…"; 1 -> "1 song fits now"; else -> "$n songs fit now" },
            style = T.title.copy(color = C.accent), modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
            Pill("Save") {
                if (sm.rules.isEmpty()) { Music.toast("Add a rule first"); return@Pill }
                SmartLists.save(d.id, d.name.ifBlank { sm.rules.firstOrNull()?.let { SmartRules.words(it) } ?: "Smart playlist" }, sm) { id ->
                    SmartEdit.close(); ui.openFolder = null; ui.openMine = id }
            }
            Pill("Cancel", quiet = true) { SmartEdit.close() }
            if (d.id != null) Pill("Delete", quiet = true) {
                val id = d.id ?: return@Pill
                ui.confirm = "Delete the smart playlist “${d.name}”? Your songs stay where they are." to {
                    Music.deletePlaylist(id); SmartEdit.close(); ui.openMine = null }
            }
        }
    }
}
