package org.omarchy.music.ui

import org.omarchy.music.Ctx
import org.omarchy.music.displayName
import org.omarchy.music.openIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import org.omarchy.music.Flac
import org.omarchy.music.Lyrics
import org.omarchy.music.LyricsTags
import org.omarchy.music.Music
import org.omarchy.music.Tags
import org.omarchy.music.Track

/** What a song's file is, and its lyrics, for the Tagger (blocking). */
object TaggerFiles {
    /** The file a song plays from on the phone (downloaded, or your own), or null if it's streamed. */
    fun uri(t: Track): String? = (Music.lib.items[t.id]?.file ?: t.file?.takeIf { t.isFile })?.takeIf { it.isNotEmpty() }

    /** [(name, value)]: where it is, the format and quality, its size and length, and (a FLAC's) every tag. */
    fun info(ctx: Ctx, t: Track): List<Pair<String, String>> {
        val u = uri(t) ?: return listOf("Source" to "Streamed: not a file on this $DEVICE")
        val out = ArrayList<Pair<String, String>>()
        val entry = Music.lib.items[t.id]
        out += "Source" to if (entry != null) "Downloaded by the app" else "Your file"
        out += "File" to (runCatching { ctx.displayName(u) }.getOrNull() ?: u.substringAfterLast('/'))
        out += fileFacts(u)
        entry?.quality?.takeIf { it.isNotEmpty() && out.none { p -> p.first == "Quality" } }?.let { out += "Quality" to it }
        flacComments(ctx, u)?.toSortedMap()?.forEach { (k, v) ->
            if (k !in setOf("LYRICS", "UNSYNCEDLYRICS", "METADATA_BLOCK_PICTURE")) out += k.lowercase() to v.joinToString(", ").take(300)
        }
        return out.filter { it.second.isNotEmpty() }
    }

    private fun flacComments(ctx: Ctx, u: String): Map<String, List<String>>? = runCatching {
        ctx.openIn(u)!!.buffered().use { s ->
            s.mark(4); val head = ByteArray(4); s.read(head); s.reset()
            if (String(head, Charsets.ISO_8859_1) != "fLaC") null else Flac.comments(Flac.readHeader(s))
        }
    }.getOrNull()

    /** The lyrics in the song's file (a FLAC's LYRICS), or "". */
    fun lyrics(ctx: Ctx, t: Track): String =
        uri(t)?.let { u -> flacComments(ctx, u)?.let { c -> (c["LYRICS"] ?: c["UNSYNCEDLYRICS"])?.firstOrNull() } }?.trim() ?: ""

    /** Replace the lyrics in a FLAC the app downloaded ("" takes them off). */
    fun saveLyrics(ctx: Ctx, t: Track, text: String) {
        val u = Tags.editable(t) ?: error("only songs the app downloaded (FLAC) can be written here")
        Tags.write(ctx, u, mapOf("lyrics" to text, "unsyncedlyrics" to ""))
    }
}

/** Tagger…: everything about the songs picked, in one place. Tags (Save writes the ones you changed;
 *  with several, a field they disagree on is left as it is in each unless you type in it), Cover (a new
 *  one found or picked, as Cover… did), Info (what the files are) and Lyrics (in the file, found, or
 *  typed, saved into it). The desktop's Tagger, the same tabs. */
@Composable
fun TaggerSheet(ui: Ui, tracks: List<Track>) {
    var tab by remember { mutableStateOf("tags") }
    val close = { ui.taggerFor = null }
    val n = tracks.size
    FullScreen(if (n == 1) "Tagger" else "Tagger · $n songs", close) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("tags" to "Tags", "cover" to "Cover", "info" to "Info", "lyrics" to "Lyrics", "tab" to "Tab").forEach { (k, l) -> Chip(l, tab == k) { tab = k } }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (n == 1) tracks[0].title else "$n songs", style = T.title, modifier = Modifier.padding(top = 6.dp))
            if (n == 1) Text(listOf(tracks[0].artist, tracks[0].album).filter { it.isNotEmpty() }.joinToString(" · "), style = T.sub)
            // Hear it while you choose its tags and moods (several: from the first, the others after)
            run {
                val playing = tracks.any { it.id == Music.current()?.id }
                Pill(if (playing && !Music.paused) "❚❚ Pause" else if (n == 1) "▶ Play it" else "▶ Play them", quiet = !playing) {
                    if (playing) Music.togglePause() else Music.playFrom(tracks, 0, radio = false)
                }
            }
            when (tab) {
                "tags" -> TaggerTags(ui, tracks)
                "tab" -> TaggerTab(tracks)
                "cover" -> {
                    ArtImage(artModel(tracks[0], 640), 220.dp)
                    val albums = tracks.map { org.omarchy.music.Covers.albumKey(it) }.distinct().size
                    Text(if (albums > 1) "$albums albums: one cover for all of them, or each its own, one after the other"
                        else "The cover of “${tracks[0].album.ifEmpty { tracks[0].title }}”", style = T.sub)
                    Pill("Find a new cover…") { ui.pickCovers(tracks) }
                }
                "info" -> TaggerInfo(tracks)
                else -> TaggerLyrics(tracks)
            }
        }
    }
}

/** The names to suggest in the Tagger: (artists, albums) of your songs. */
private fun suggestNames(): Pair<List<String>, List<String>> {
    val songs = runCatching { Music.mySongs() }.getOrDefault(emptyList())
    val artists = songs.flatMap { org.omarchy.music.ownArtists(it.artist) + listOf(it.artist).filter { a -> "," !in a } }
    return artists.filter { it.isNotBlank() }.distinctBy { org.omarchy.music.norm(it) } to
        songs.map { it.album }.filter { it.isNotBlank() }.distinctBy { org.omarchy.music.norm(it) }
}

@Composable
private fun TaggerTags(ui: Ui, tracks: List<Track>) {
    var tick by remember { mutableStateOf(0) }
    val songs = remember(tracks, tick) { tracks.filter { Tags.editableAny(it) != null } }
    // Back from Android's settings with All files access on: the songs can be changed now
    LaunchedEffect(songs.isEmpty()) {
        if (songs.isEmpty() && onPhone && !Music.canWriteOwnFiles()) {
            while (!Music.canWriteOwnFiles()) kotlinx.coroutines.delay(1000)
            tick++
        }
    }
    if (songs.isEmpty()) {
        val ask = onPhone && !Music.canWriteOwnFiles()
        Text(if (ask) "To change your own songs' tags (any file: FLAC, MP3, M4A and more), Android needs to let the app change your " +
            "music files: tap Allow, turn on All files access for Radon Player, then come back. The cover can still be changed."
            else if (onPhone) "These songs' files can't be changed (they're streamed, or Android won't let the app write them). The cover can still be changed."
            else "These songs' files can't be changed (they're streamed, or the files are read-only). The cover can still be changed.", style = T.sub)
        if (ask) Box(Modifier.padding(vertical = 8.dp)) { Pill("Allow") { openSystemSettings("allfiles") } }
        TaggerMood(tracks)   // a mood can still be picked: it's kept in the app even where the file can't be written
        return
    }
    var have by remember { mutableStateOf<Map<String, String?>?>(null) }
    val typed = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(songs) { Music.bg({ Tags.read(Music.ctx, songs.mapNotNull { Tags.editableAny(it) }) }) { h, _ -> have = h ?: emptyMap() } }
    val h = have
    if (h == null) { Text("Reading the tags…", style = T.sub); return }
    if (songs.size < tracks.size) Text("${tracks.size - songs.size} of the songs ${if (onPhone) "aren't downloads of the app's" else "have no file that can be changed"}: they're left out.", style = T.sub)
    val names = remember { suggestNames() }
    Tags.FIELDS.forEach { (k, label) ->
        Text(label, style = T.sub, modifier = Modifier.padding(top = 2.dp))
        Field(typed[k] ?: h[k] ?: "", if (h.containsKey(k) && h[k] == null) "Several (left as they are)" else "") { typed[k] = it }
        // Artist, Album artist, Album: yours that match what you type, under it; a tap fills it in
        val pool = when (k) { "artist", "albumartist" -> names.first; "album" -> names.second; else -> null }
        val q = typed[k]
        if (pool != null && !q.isNullOrBlank()) {
            val before = if (k == "artist") Regex("^(.*(?:,|\\s&)\\s*)[^,&]*$").find(q)?.groupValues?.get(1).orEmpty() else ""
            val part = org.omarchy.music.norm(q.removePrefix(before).trim())
            val hits = if (part.isEmpty()) emptyList() else pool.filter { part in org.omarchy.music.norm(it) && org.omarchy.music.norm(it) != org.omarchy.music.norm(q) }
                .sortedWith(compareBy({ n -> org.omarchy.music.norm(n).let { if (it.startsWith(part)) 0 else if (it.split(" ").any { w -> w.startsWith(part) }) 1 else 2 } },
                    { org.omarchy.music.norm(it) })).take(6)
            hits.forEach { n ->
                Text(n, style = T.body, modifier = Modifier.fillMaxWidth().clickable { typed[k] = before + n }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        if (k == "tracknumber") TaggerMood(tracks)   // Mood: right under Track, like the other fields
    }
    var renameFiles by remember { mutableStateOf(true) }
    if (org.omarchy.music.FileTags.canMove && typed["title"] != null) Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(renameFiles, { renameFiles = it })
        Text("Rename the file${if (songs.size != 1) "s" else ""} too (the new title in the file name)", style = T.body)
    }
    // Find tags online: each song's title, artist, album, year, track and genre looked up; shown before anything is saved
    var finding by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<Pair<Track, Map<String, Pair<String, String>>>>?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Save the tags") {
            if (typed.isEmpty()) Music.toast("Nothing changed")
            else { Music.editTags(songs, typed.toMap(), renameFiles && org.omarchy.music.FileTags.canMove); typed.clear() }
        }
        Pill(if (finding) "Finding tags…" else "Find tags online", quiet = true) {
            if (finding) return@Pill
            finding = true
            Music.bg({
                songs.map { s ->
                    val f = Tags.editableAny(s)!!
                    val cur = runCatching { Tags.read(Music.ctx, listOf(f)) }.getOrDefault(emptyMap())
                    val got = org.omarchy.music.TagFinder.find(cur["title"] ?: s.title, cur["artist"] ?: s.artist, cur["album"] ?: s.album)
                    s to got.mapNotNull { (k, v) -> val was = cur[k].orEmpty(); if (was.trim() == v.trim()) null else k to (was to v) }.toMap()
                }
            }) { res, err ->
                finding = false
                when {
                    res == null -> Music.toast("Couldn't look them up: ${err?.message?.take(80)}")
                    res.all { it.second.isEmpty() } -> Music.toast(if (songs.size == 1) "Its tags already match what's online (or it wasn't found)"
                        else "Their tags already match what's online (or they weren't found)")
                    else -> found = res.filter { it.second.isNotEmpty() }
                }
            }
        }
    }
    found?.let { list -> FoundTags(list, onDone = { found = null }) }
}

/** What Find tags online found, field by field (what it is now → what it would be), each ticked to apply; Apply writes
 *  the ticked ones, Cancel nothing. */
@Composable
private fun FoundTags(list: List<Pair<Track, Map<String, Pair<String, String>>>>, onDone: () -> Unit) {
    val keep = remember(list) { mutableStateMapOf<String, Boolean>().apply { list.forEach { (t, m) -> m.keys.forEach { put(t.id + "|" + it, true) } } } }
    androidx.compose.material3.AlertDialog(onDismissRequest = onDone, containerColor = C.light,
        title = { Text("Apply these tags?", style = T.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Found online. Untick any you'd rather keep as they are. Moods aren't changed.", style = T.sub)
                list.forEach { (t, m) ->
                    if (list.size > 1) Text(t.title, style = T.title, modifier = Modifier.padding(top = 6.dp))
                    m.forEach { (k, v) ->
                        val key = t.id + "|" + k
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { keep[key] = keep[key] != true }) {
                            androidx.compose.material3.Checkbox(keep[key] == true, { keep[key] = it })
                            Column {
                                Text(Tags.FIELDS.firstOrNull { it.first == k }?.second ?: k, style = T.sub)
                                Text("${v.first.ifEmpty { "(empty)" }}  →  ${v.second}", style = T.body)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton({
            var n = 0
            list.forEach { (t, m) ->
                val ch = m.filterKeys { keep[t.id + "|" + it] == true }.mapValues { it.value.second }
                if (ch.isNotEmpty()) { Music.editTags(listOf(t), ch); n++ }
            }
            if (n == 0) Music.toast("Nothing applied")
            onDone()
        }) { Text("Apply", color = C.accent) } },
        dismissButton = { androidx.compose.material3.TextButton(onDone) { Text("Cancel", color = C.muted) } })
}

/** Mood, a row of the Tags tab under Track that looks like the other fields: the songs' moods (every one that fits, the
 *  main one first); a tap ticks the moods it should have, exactly those (saved straight away, no need for Save the
 *  tags), or Clear to have them worked out again. Kept in the
 *  app, and written into the files' MOOD tags where the app may write them (on the PC all your files; on the phone its
 *  own downloads). Any song that's a file can have one, even where its other tags can't be changed. */
@Composable
private fun TaggerMood(tracks: List<Track>) {
    val moods = org.omarchy.music.Moods
    val mine = remember(tracks) { tracks.filter { it.isFile || Music.lib.has(it.id) } }
    Text("Mood", style = T.sub, modifier = Modifier.padding(top = 2.dp))
    if (mine.isEmpty()) { Text("Only songs that are files (yours or downloaded) have a mood", style = T.sub); return }
    val v = moods.version
    val now = remember(mine, v) { mine.map { runCatching { moods.all(it) }.getOrDefault(emptyList()) }.distinct() }
    val yours = remember(mine, v) { mine.map { runCatching { moods.picked(it) }.getOrDefault("") }.distinct() }
    val one = now.singleOrNull()?.takeIf { it.isNotEmpty() }
    // The moods ticked: the ones you set (all the songs the same), else the moods worked out
    val ticked = remember(mine, v) {
        yours.singleOrNull()?.takeIf { it.isNotEmpty() }?.split(';')?.filter { it.isNotEmpty() } ?: one.orEmpty()
    }
    val byYou = yours.singleOrNull()?.isNotEmpty() == true
    var open by remember { mutableStateOf(false) }
    Box {
        // Drawn like Field (the same border and background), but a tap opens the list of moods
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).border(1.dp, C.line).background(C.fg.copy(alpha = 0.04f))
            .clickable { open = true }.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(when {
                byYou -> ticked.joinToString(", ")
                one != null -> one.joinToString(", ")
                now.size > 1 -> "Several (a pick gives them all the same)"
                else -> "No mood yet: tap to pick"
            }, style = if (one != null || byYou) T.body else T.body.copy(color = C.muted), modifier = Modifier.weight(1f))
            Text(if (byYou) "picked by you  ▾" else if (one != null) "worked out  ▾" else "▾", style = T.sub)
        }
        // Tick as many moods as fit the song; each tick is saved straight away (the list stays open for more)
        DropdownMenu(open, { open = false }, containerColor = C.light) {
            Text("Tick every mood that fits", style = T.sub, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            for (m in moods.ALL) DropdownMenuItem({ Text((if (m in ticked) "✓  " else "     ") + m, style = T.body) }, {
                val next = if (m in ticked) ticked - m else ticked + m
                moods.pick(mine, next.ifEmpty { null })
            })
            if (byYou)
                DropdownMenuItem({ Text("     Clear (work it out again)", style = T.body) }, { open = false; moods.pick(mine, null as String?) })
            DropdownMenuItem({ Text("     Done", style = T.body.copy(color = C.accent)) }, { open = false })
        }
    }
}

/** Tab: which of Albums, EPs and Singles the songs show in, when their tags put them in the wrong one. Automatic: as
 *  their tags (or their Artists folder) say. Nothing in the files changes. */
@Composable
private fun TaggerTab(tracks: List<Track>) {
    val mine = tracks.filter { it.isFile || Music.lib.has(it.id) }
    if (mine.isEmpty()) { Text("Only songs in your music have a tab", style = T.sub); return }
    val picked = mine.map { Music.tabPicks[Music.tabKey(it)] }.distinct().singleOrNull()
    val now = mine.mapNotNull { Music.tabOf(it) }.distinct()
    val names = mapOf("album" to "Albums", "ep" to "EPs", "single" to "Singles")
    Text("In " + now.joinToString(" and ") { names[it] ?: it } + if (picked != null) " (put there by hand)" else " (as the tags say)", style = T.body)
    Text("If ${if (mine.size == 1) "this song is" else "these songs are"} in the wrong tab, put ${if (mine.size == 1) "it" else "them"} " +
        "in the right one here. A release goes where most of its songs are put, so pick all of an album's songs to move it. " +
        "The files aren't changed; Automatic goes back to what the tags say.", style = T.sub)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("Automatic", picked == null && mine.none { Music.tabPicks.containsKey(Music.tabKey(it)) }) { Music.setTab(mine, null) }
        for ((k, l) in names) Chip(l, picked == k) { Music.setTab(mine, k) }
    }
}

@Composable
private fun TaggerInfo(tracks: List<Track>) {
    var info by remember { mutableStateOf<List<Pair<Track, List<Pair<String, String>>>>?>(null) }
    LaunchedEffect(tracks) { Music.bg({ tracks.take(40).map { it to TaggerFiles.info(Music.ctx, it) } }) { r, _ -> info = r ?: emptyList() } }
    val got = info
    if (got == null) { Text("Reading the files…", style = T.sub); return }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((t, rows) in got) {
                Column {
                    if (tracks.size > 1) Text("${t.title} · ${t.artist}", style = T.title)
                    for ((k, v) in rows) Row {
                        Text(k, style = T.sub, modifier = Modifier.width(110.dp))
                        Text(v, style = T.body)
                    }
                }
            }
            if (tracks.size > 40) Text("…and ${tracks.size - 40} more", style = T.sub)
        }
    }
}

@Composable
private fun TaggerLyrics(tracks: List<Track>) {
    if (tracks.size > 1) {
        Text("Each song's lyrics, found and put into its own file (songs that have lyrics already keep them).", style = T.sub)
        Pill("Put the lyrics into the files") {
            tracks.forEach { Music.lyricsIntoFile(it, force = true) }
            Music.toast("Putting the lyrics into the files…")
        }
        return
    }
    val t = tracks[0]
    val canSave = Tags.editable(t) != null
    var text by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(t.id) {
        Music.bg({ TaggerFiles.lyrics(Music.ctx, t) }) { r, _ ->
            text = r ?: ""
            status = when {
                !r.isNullOrEmpty() -> "In the file"
                canSave -> "None in the file yet: Find them, or type them"
                else -> "Only songs the app downloaded (FLAC) can have lyrics saved here; they can still be found and read"
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Find the lyrics", quiet = true) {
            status = "Looking…"
            Music.bg({ Lyrics.get(Music.ctx, t)?.let { LyricsTags.words(it) } }) { w, err ->
                if (!w.isNullOrEmpty()) { text = w; status = if (canSave) "Found: Save puts them into the file" else "Found" }
                else status = "None found" + (err?.message?.let { ": ${it.take(80)}" } ?: "")
            }
        }
        if (canSave) Pill("Save into the file") {
            val words = text ?: ""
            Music.bg({ TaggerFiles.saveLyrics(Music.ctx, t, words) }) { _, err ->
                status = if (err != null) "Couldn't save: ${err.message?.take(100)}" else "Saved into the file"
            }
        }
    }
    Text(status, style = T.sub)
    if (text != null) Field(text!!, "The lyrics", multiLine = true) { text = it }
}
