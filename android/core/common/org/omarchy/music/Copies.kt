package org.omarchy.music

import org.omarchy.music.Music.TrashJob

/** Copies of a song: telling that a song to download is one you have, what happens to your copy then (kept, asked
 *  about, or put away once the new one is in), songs going away for another in your playlists, queue and plays,
 *  and the Duplicates list. What the screens read (the question asked, the Duplicates list, the files Android is
 *  asked to bin) stays in [Music], which keeps the names the screens call and hands on. */
object Copies {
    private val lib get() = Music.lib
    private val local get() = Music.local
    private val lists get() = Music.lists
    private val plays get() = Music.plays
    private val ctx get() = Music.ctx
    private fun toast(text: String) = Music.toast(text)
    private fun <T> bg(work: () -> T, done: (T?, Throwable?) -> Unit = { _, _ -> }) = Music.bg(work, done)
    private fun mySongs() = Music.mySongs()
    private fun current() = Music.current()
    private fun saveState() = Music.saveState()
    private fun download(tracks: List<Track>, checked: Boolean = false) = Music.download(tracks, checked)
    private fun unqueueMany(indices: Collection<Int>) = Music.unqueueMany(indices)
    private fun dupeKey(g: List<Track>) = Music.dupeKey(g)
    private val notDupes get() = Music.notDupes
    private val dupeAction get() = Music.dupeAction
    private var dupePrompt: Pair<List<Track>, List<Pair<Track, List<Track>>>>? get() = Music.dupePrompt; set(v) { Music.dupePrompt = v }
    private var trashJob: TrashJob? get() = Music.trashJob; set(v) { Music.trashJob = v }
    private var dupes: List<List<Track>>? get() = Music.dupes; set(v) { Music.dupes = v }
    private var dupesBusy: Boolean get() = Music.dupesBusy; set(v) { Music.dupesBusy = v }
    private var queue: List<Track> get() = Music.queue; set(v) { Music.queue = v }
    private val index get() = Music.index
    private var musicVersion: Int get() = Music.musicVersion; set(v) { Music.musicVersion = v }
    private var savedVersion: Int get() = Music.savedVersion; set(v) { Music.savedVersion = v }

    /** A download's id -> your copies to put away once it's in (Copies: replace). Noted from other threads too. */
    private val replaceAfter = HashMap<String, List<Track>>()
    internal fun replaceLater(id: String, copies: List<Track>) { synchronized(replaceAfter) { replaceAfter[id] = copies } }

    private var copyIndex: Pair<Triple<Int, Int, Double>, Map<String, List<Track>>>? = null
        set(v) { field = v; if (v == null) copyIndexFor = -1 }
    private var copyIndexFor = -1   // your music as it was (Music.musicVersion) when the index was made: a tag change makes it anew

    /** Your songs on the phone by the first letters of their title (squashed): kept until your songs change. */
    private fun copyPool(t: Track): List<Track> {
        val sig = Triple(lib.items.size, local.files.size, local.scanned)
        val idx = copyIndex?.takeIf { it.first == sig && copyIndexFor == musicVersion }?.second ?: mySongs().groupBy { squashKey(cleanTitle(it.title)).take(4) }
            .also { copyIndex = sig to it; copyIndexFor = musicVersion }
        return idx[squashKey(cleanTitle(t.title)).take(4)] ?: emptyList()
    }

    /** A download whose file isn't where the app has it any more (moved or renamed by a tag change that wasn't noted,
     *  or outside the app). Your file of the same song (the same title and artist, whatever its album says now) is
     *  taken as its file, so it goes on playing from your copy and is still called downloaded. With no such file it
     *  is forgotten as a download (nothing is deleted): it streams, and can be downloaded again. Before, it went on
     *  being called downloaded while it streamed, and downloading it again made a second copy beside the moved one. */
    internal fun heal(t: Track) {
        val e = lib.items[t.id] ?: return
        if (Music.dl.standIn != null) return   // a test's stand-in for the services: its songs have no files
        if (e.file.isEmpty() || !runCatching { Music.ctx.uriMissing(e.file) }.getOrDefault(false)) return
        val found = runCatching { mySongs() }.getOrDefault(emptyList()).firstOrNull { s ->
            val f = s.file
            s.isFile && f != null && f != e.file && CopyRules.sameSong(s.title, s.artist, e.title, e.artist) &&
                !runCatching { Music.ctx.uriMissing(f) }.getOrDefault(true)
        }
        if (found != null) lib.update(e.id) { it.copy(file = found.file!!, album = found.album.ifEmpty { it.album }) }
        else lib.forget(e.id)
        copyIndex = null
        musicVersion++; savedVersion++
    }

    /** Your own copy of a song asked to be downloaded: the same title and artist (never another version), a file or
     *  a download that is really there. Null when you have none. */
    fun ownCopy(t: Track): Track? {
        if (t.isFile || lib.has(t.id)) return null
        val m = runCatching { Music.localOf(t) }.getOrNull() ?: return null
        return m.takeIf { it !== t && it.id != t.id && (it.isFile || lib.has(it.id)) && CopyRules.sameSong(it.title, it.artist, t.title, t.artist) }
    }

    /** Your songs on the phone that are likely t (close is enough: it's asked about), t itself left out. */
    fun copiesOf(t: Track): List<Track> = copyPool(t).filter { it.id != t.id && maybeSame(t, it) && (it.isFile || lib.has(it.id)) }

    /** Whether a song may be saved without asking (Keep, or a service's sync): yes unless you have it
     *  already and Copies says Ask (then it streams; Download asks). With Replace, your copy goes once it's in. */
    fun autoSaveOk(t: Track): Boolean {
        if (t.source == SoundCloud.KEY) return false   // never saved: SoundCloud songs only stream
        if (dupeAction == "keep" || lib.has(t.id)) return true
        val copies = copiesOf(t)
        if (copies.isEmpty()) return true
        if (dupeAction == "replace") { replaceLater(t.id, copies); return true }
        return false
    }

    /** A download is in: your copies of it go, if you said to replace them. */
    internal fun afterSaved(id: String) {
        val old = synchronized(replaceAfter) { replaceAfter.remove(id) } ?: return
        val e = lib.items[id] ?: return
        replaceCopies(old, e.track(), "copies of “${e.title}”")
    }

    /** Put old (copies of new) away: downloads at once, your files in Android's trash once you say yes. */
    fun replaceCopies(old: List<Track>, new: Track, what: String) {
        val playing = current()?.id
        val mine = old.filter { it.id != playing }
        val downloads = mine.filter { lib.has(it.id) && lib.items[it.id]?.file != lib.items[new.id]?.file }
        downloads.forEach { lib.remove(it.id); retarget(listOf(it.id), new) }
        val files = mine.filter { it.isFile && Music.phoneFile(it) != null }
        if (files.isNotEmpty()) queueTrash(TrashJob(files, files.associate { it.id to new }, what))
        else if (downloads.isNotEmpty()) toast("Replaced ${downloads.size} ${if (downloads.size == 1) "copy" else "copies"} of “${new.title}”")
        musicVersion++; savedVersion++
    }

    private val trashQueue = ArrayDeque<TrashJob>()

    /** Ask Android to put files in its trash (one ask at a time; Android 10 can't: they stay). */
    fun queueTrash(job: TrashJob) {
        if (!sdkAtLeast(30)) { toast("Android 10 can't put other apps' files in the trash: your old files stay"); return }
        if (trashJob == null) trashJob = job else trashQueue.addLast(job)
    }

    /** Android said yes (ok) or no to the trash job. */
    fun trashed(job: TrashJob, ok: Boolean) {
        trashJob = trashQueue.removeFirstOrNull()
        FolderCleanup.afterSongs(job.tracks)   // their folders, if no music is left in them (some may have gone even if not all did)
        if (!ok) { toast("Your old files stay where they are"); return }
        val ids = job.tracks.map { it.id }.toSet()
        local.files = local.files.filter { it.id !in ids }
        for (t in job.tracks) {
            val new = job.replacements[t.id]
            if (new != null) retarget(listOf(t.id), new)
            else lists.all().filter { p -> p.tracks.any { it.id == t.id } }.forEach { lists.remove(it.id, setOf(t.id)) }
        }
        unqueueMany(queue.indices.filter { queue[it].id in ids && job.replacements[queue[it].id] == null })
        dupes = dupes?.map { g -> g.filter { it.id !in ids } }?.filter { it.size > 1 }
        toast(if (job.replacements.isNotEmpty()) "Replaced ${job.tracks.size} file${if (job.tracks.size != 1) "s" else ""} (in Android's trash)"
              else "${job.tracks.size} file${if (job.tracks.size != 1) "s" else ""} in Android's trash")
        musicVersion++; savedVersion++
    }

    /** A playlist's songs with keep where the songs going (their ids: gone) were: at the first one's place, and once
     *  (not again where another copy of it was, or where it was already). */
    fun swapped(tracks: List<Track>, gone: Set<String>, keep: Track): List<Track> {
        val have = HashSet<String>()
        return tracks.map { if (it.id in gone) keep else it }.filter { have.add(it.id) }
    }

    /** Songs going away for new: your playlists and the queue get new in their place, their plays go with it. */
    fun retarget(oldIds: List<String>, new: Track) {
        val gone = oldIds.toSet() - new.id
        if (gone.isEmpty()) return
        val keep = new.slim()
        for (p in lists.all()) if (p.tracks.any { it.id in gone }) lists.replaceTracks(p.id, swapped(p.tracks, gone, keep))
        queue = queue.mapIndexed { i, t -> if (t.id in gone && i != index) keep.copy(queued = t.queued) else t }
        plays.merge(gone, keep)
        saveState()
    }

    /** Download the songs you were asked about: r is "keep", "replace" or "skip" (only the new ones). */
    fun answerDupes(r: String) {
        val (new, dupes) = dupePrompt ?: return
        dupePrompt = null
        if (r == "cancel") return
        val have = dupes.map { it.first.id }.toSet()
        if (r == "replace") dupes.forEach { (t, c) -> replaceLater(t.id, c) }
        download(new.filter { r != "skip" || it.id !in have }, checked = true)
    }

    // ---- Duplicates ----
    fun findDupes() {
        if (dupesBusy) return
        dupesBusy = true
        bg({
            val no = notDupes
            fillLengths()
            groupsOf(mySongs().filter { it.isFile || lib.has(it.id) }, { lib.items[it.id]?.file ?: it.file }, ::copyRank) { dupeKey(it) in no }
        }) { res, err ->
            dupesBusy = false
            dupes = res ?: emptyList()
            if (err != null) toast("Couldn't look for duplicates: ${err.message?.take(80)}")
        }
    }

    /** (blocking) A download kept with no length (one downloaded from a list that had none: Repeats, a history) gets
     *  its FLAC's own. Without it the song showed no time and could not be told to be another file of the same
     *  recording by its length (Duplicates, your copy of a song). Returns how many were filled in. */
    fun fillLengths(): Int {
        var n = 0
        for (e in lib.items.values.toList()) {
            if (e.duration > 0 || e.file.isEmpty() || e.format != "flac") continue
            val secs = runCatching { ctx.openIn(e.file)?.use { Flac.seconds(it) } }.getOrNull() ?: 0.0
            if (secs > 0) { lib.update(e.id) { it.copy(duration = secs) }; n++ }
        }
        return n
    }

    /** The Duplicates list: your songs that are one song ([dupeClusters]: the same name, a leading track number or
     *  punctuation aside, by the same artist or by one they share, by a file that names no artist, or the same name and
     *  the very same length whoever each file names) in two or
     *  more files (fileOf: one file listed twice is one copy), each group's best copy first (rank), the groups by
     *  artist then name; the groups you marked "Not a duplicate" (kept) left out. */
    fun groupsOf(songs: List<Track>, fileOf: (Track) -> String?, rank: (Track) -> Double, kept: (List<Track>) -> Boolean): List<List<Track>> =
        dupeClusters(songs).map { g -> g.distinctBy(fileOf).sortedByDescending(rank) }
            .filter { it.size > 1 && !kept(it) }.sortedBy { norm(it[0].artist) + "|" + norm(it[0].title) }

    /** Which copy is best to keep: a FLAC first, a download (24-bit) over a file, then the longer one. */
    fun copyRank(t: Track): Double {
        val e = lib.items[t.id]
        val q = e?.quality ?: ""
        val name = (e?.file ?: t.file ?: "").lowercase()
        val flac = e?.format == "flac" || name.endsWith(".flac") || copyInfo(t).startsWith("FLAC")
        return (if (flac) 1000.0 else 0.0) + (if (q.startsWith("24")) 100.0 else 0.0) + t.duration / 1000
    }

    /** What a copy is: its format and quality, as far as the phone says. */
    fun copyInfo(t: Track): String {
        lib.items[t.id]?.let { e -> return listOf(e.format.uppercase().ifEmpty { "?" }, e.quality).filter { it.isNotEmpty() }.joinToString(" · ") }
        val f = t.file ?: return ""
        return runCatching {
            val (mime, size) = ctx.typeAndSize(f) ?: return@runCatching ""
            listOf(mime.substringAfter('/').uppercase().replace("X-", "").replace("MPEG", "MP3"), "%.1f MB".format(size / 1e6)).joinToString(" · ")
        }.getOrDefault("")
    }

    /** Where a song is, for the Duplicates list. */
    fun whereIs(t: Track): String = lib.items[t.id]?.let { "Downloaded here" } ?: local.paths[t.file ?: ""]?.trimEnd('/') ?: ""

    /** Duplicates' Keep / Remove: in each group the copies picked go (downloads at once, files to the bin together),
     *  the first one kept taking their place in playlists. A group with every copy picked keeps its best. */
    fun removeDupes(picks: List<Pair<List<Track>, Set<String>>>) {
        val playing = current()?.id
        val files = ArrayList<Track>(); val to = HashMap<String, Track>(); val gone = HashSet<String>()
        var skipped = false
        for ((g, rm) in picks) {
            val keep = g.firstOrNull { it.id !in rm } ?: g.first()
            for (t in g) {
                if (t.id !in rm || t.id == keep.id) continue
                if (t.id == playing) { skipped = true; continue }
                if (lib.has(t.id)) { lib.remove(t.id); retarget(listOf(t.id), keep); gone += t.id }
                else { files += t; to[t.id] = keep }
            }
        }
        if (gone.isNotEmpty()) {
            dupes = dupes?.map { g -> g.filter { it.id !in gone } }?.filter { it.size > 1 }
            savedVersion++; musicVersion++
        }
        if (files.isNotEmpty()) queueTrash(TrashJob(files, to, "${files.size} duplicate cop${if (files.size != 1) "ies" else "y"}"))
        val n = gone.size + files.size
        toast((if (n > 0) "Removing $n cop${if (n != 1) "ies" else "y"}" else "Nothing to remove") + if (skipped) " · the playing one is left: skip it first" else "")
    }

    // ---- A playlist's duplicates ----
    /** A playlist's Find duplicates as last looked: the playlist (id), its songs as they were then, the groups of one
     *  song in it more than once (places in tracks, the copy best kept first; null: still looking), and where each of
     *  those copies plays from. */
    class PlaylistDupes(val id: String, val tracks: List<Track>, val groups: List<List<Int>>?, val where: Map<Int, String> = emptyMap())

    /** Look through a playlist for songs in it more than once: the same song twice, or two copies of one song (as
     *  Duplicates tells them: a leading track number, punctuation, a file with no artist…). */
    fun findPlaylistDupes(id: String) {
        val tracks = lists.get(id)?.tracks ?: return
        val no = notDupes
        val look = PlaylistDupes(id, tracks, null)
        Music.playlistDupes = look
        bg({
            val state = HashMap<Track, Int>()
            fun st(t: Track) = state.getOrPut(t) { entryState(t) }
            val groups = playlistGroups(tracks, { st(it) * 100_000.0 + copyRank(it) }) { dupeKey(it) in no }
            groups to groups.flatten().associateWith { i -> val t = tracks[i]
                when (st(t)) { 2 -> whereIs(t).ifEmpty { "On this ${org.omarchy.music.ui.DEVICE}" }; 1 -> "Streaming"
                    else -> if (t.file?.let { BackupMerge.fileHere(it, platformName == "android") } == false) "The other device's file: not on this one"
                        else "Not on this ${org.omarchy.music.ui.DEVICE} any more: can't be played" } }
        }) { res, err ->
            if (Music.playlistDupes !== look) return@bg   // closed, or looked again since
            Music.playlistDupes = PlaylistDupes(id, tracks, res?.first ?: emptyList(), res?.second ?: emptyMap())
            if (err != null) toast("Couldn't look for duplicates: ${err.message?.take(80)}")
        }
    }

    /** A playlist's songs that are one song more than once, as places in tracks: each group's best entry first (rank;
     *  the playlist's order between equals), the groups by artist then name, the ones you marked "Not a duplicate"
     *  (kept) left out. */
    fun playlistGroups(tracks: List<Track>, rank: (Track) -> Double, kept: (List<Track>) -> Boolean): List<List<Int>> =
        dupeClusterIndices(tracks).map { g -> g.sortedByDescending { rank(tracks[it]) } }
            .filter { g -> !kept(g.map { tracks[it] }) }
            .sortedBy { norm(tracks[it[0]].artist) + "|" + norm(tracks[it[0]].title) }

    /** Where a playlist's song plays from, best first: 2 on this device (its file is there, or a download), 1 streaming,
     *  0 a file that's gone (it can't be played). (blocking: the file is looked for) */
    private fun entryState(t: Track): Int = when {
        lib.has(t.id) -> 2
        !t.isFile -> 1
        runCatching { YourFolders.fileHere(t) }.getOrDefault(false) -> 2
        else -> 0
    }

    /** A group of the playlist's Find duplicates marked "Not a duplicate": remembered, and not shown again. */
    fun playlistNotDupe(g: List<Int>) {
        val d = Music.playlistDupes ?: return
        Music.markNotDupe(g.map { d.tracks[it] })
        Music.playlistDupes = PlaylistDupes(d.id, d.tracks, d.groups?.filter { it != g })
    }

    /** The playlist's Keep / Remove: in each group the copies picked come out of the playlist (only out of it: your
     *  files and downloads stay); a group with every copy picked keeps its best. */
    fun removePlaylistDupes(picks: List<Pair<List<Int>, Set<Int>>>) {
        val d = Music.playlistDupes ?: return
        val drop = HashSet<Int>()
        for ((g, rm) in picks) {
            val keep = g.firstOrNull { it !in rm } ?: g.first()
            drop += rm.filter { it in g && it != keep }
        }
        val name = lists.get(d.id)?.name ?: return
        if (!lists.removeAt(d.id, d.tracks, drop)) { toast("“$name” changed since: look again"); findPlaylistDupes(d.id); return }
        Music.playlistDupes = null
        musicVersion++
        toast(if (drop.isEmpty()) "Nothing to remove" else "Took ${drop.size} cop${if (drop.size != 1) "ies" else "y"} out of “$name” (your files stay)")
    }
}
