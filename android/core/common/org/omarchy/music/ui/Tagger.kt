package org.omarchy.music.ui

import org.omarchy.music.Ctx
import org.omarchy.music.displayName
import org.omarchy.music.openIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            listOf("tags" to "Tags", "cover" to "Cover", "info" to "Info", "lyrics" to "Lyrics").forEach { (k, l) -> Chip(l, tab == k) { tab = k } }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (n == 1) tracks[0].title else "$n songs", style = T.title, modifier = Modifier.padding(top = 6.dp))
            if (n == 1) Text(listOf(tracks[0].artist, tracks[0].album).filter { it.isNotEmpty() }.joinToString(" · "), style = T.sub)
            when (tab) {
                "tags" -> TaggerTags(ui, tracks)
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
    val songs = remember(tracks) { tracks.filter { Tags.editableAny(it) != null } }
    if (songs.isEmpty()) {
        Text(if (onPhone) "Only songs the app downloaded (FLAC) can have their tags changed here: Android keeps other apps' files " +
            "(your own songs) to themselves. Download them first. The cover can still be changed."
            else "These songs' files can't be changed (they're streamed, or the files are read-only). The cover can still be changed.", style = T.sub)
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
    }
    var renameFiles by remember { mutableStateOf(true) }
    if (org.omarchy.music.FileTags.canMove && typed["title"] != null) Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(renameFiles, { renameFiles = it })
        Text("Rename the file${if (songs.size != 1) "s" else ""} too (the new title in the file name)", style = T.body)
    }
    Pill("Save the tags") {
        if (typed.isEmpty()) Music.toast("Nothing changed")
        else { Music.editTags(songs, typed.toMap(), renameFiles && org.omarchy.music.FileTags.canMove); typed.clear() }
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
