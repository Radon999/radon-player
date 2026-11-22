package org.omarchy.music

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** Tag changes across Sync: every change you make to a song's tags here (Edit tags, the Tagger, Fix titles, album
 *  merges…) is noted and kept until the other device has it; at a sync the other device makes the same change to its
 *  copy of the song (found by title and artist), once. */
object TagSync {
    private val lock = Any()
    private val dir get() = Music.ctx.filesDir
    private val pendingFile get() = File(dir, "tag-changes.json")
    private val appliedFile get() = File(dir, "tag-changes-applied.json")
    private val listSer = ListSerializer(TagChange.serializer())
    private val setSer = SetSerializer(String.serializer())
    /** Changes made while taking the other device's: not noted again (they'd go back to it). */
    private val taking = ThreadLocal<Boolean>()

    private fun pending(): MutableList<TagChange> =
        readText(pendingFile)?.let { runCatching { JSON.decodeFromString(listSer, it) }.getOrNull() }?.toMutableList() ?: mutableListOf()
    private fun applied(): MutableSet<String> =
        readText(appliedFile)?.let { runCatching { JSON.decodeFromString(setSer, it) }.getOrNull() }?.toMutableSet() ?: mutableSetOf()

    /** (blocking) A change to the song file at uri was written: noted for the next sync. before: its title and artist
     *  before the change. */
    /** A scan's tag write (Fix albums and artists, Live, Soundtrack notes, songs alone in an album, Fix genre names,
     *  Identify by sound…): written, and noted for your other device as Edit tags' changes are, so it gets the same
     *  change at the next sync (with Tags match off too). */
    fun write(ctx: Ctx, uri: String, changes: Map<String, String>): Boolean {
        val before = runCatching { FileTags.read(ctx, uri) }.getOrNull()
        val ok = FileTags.write(ctx, uri, changes)
        if (ok && before != null) note(before["title"].orEmpty(), before["artist"].orEmpty(), changes)
        return ok
    }

    fun note(title: String, artist: String, changes: Map<String, String>) {
        if (taking.get() == true || title.isBlank()) return
        val kept = TagChanges.kept(changes).takeIf { it.isNotEmpty() } ?: return
        runCatching { synchronized(lock) {
            val all = pending()
            all += TagChange(randomHex(8), now(), title, artist, kept)
            writeAtomic(pendingFile, JSON.encodeToString(listSer, all))
        } }
    }

    // ---- covers you pick (Cover…, the Tagger's Cover): they go across as your tag changes do ----
    private val coverDir get() = File(dir, "tag-covers").apply { mkdirs() }
    private fun coverFile(mark: String) = File(coverDir, "$mark.jpg")
    private fun markOf(jpeg: ByteArray) = java.security.MessageDigest.getInstance("SHA-1").digest(jpeg).take(10).joinToString("") { "%02x".format(it) }

    /** (blocking) You picked a cover for these songs (each its title and artist): noted for the next sync, where the
     *  other device puts the same picture into its copies of them. */
    fun noteCover(songs: List<Pair<String, String>>, jpeg: ByteArray) {
        if (taking.get() == true || songs.isEmpty()) return
        runCatching { synchronized(lock) {
            val mark = markOf(jpeg)
            coverFile(mark).writeBytes(jpeg)
            val all = pending()
            // (a cover picked again for the same song: the newer one is the one that goes)
            val keys = songs.map { TagChanges.key(it.first, it.second) }.toSet()
            all.removeAll { it.cover.isNotEmpty() && it.changes.isEmpty() && TagChanges.key(it.title, it.artist) in keys }
            for ((title, artist) in songs.distinct()) if (title.isNotBlank()) all += TagChange(randomHex(8), now(), title, artist, emptyMap(), mark)
            writeAtomic(pendingFile, JSON.encodeToString(listSer, all))
            tidyCovers(all)
        } }
    }

    /** The pictures no pending change names any more are let go. */
    private fun tidyCovers(all: List<TagChange>) {
        val used = all.map { it.cover }.filter { it.isNotEmpty() }.toSet()
        coverDir.listFiles()?.filter { it.nameWithoutExtension !in used }?.forEach { it.delete() }
    }

    /** Changes made here not noted for the other device (they came from it: Tags & covers match). */
    fun quietly(run: () -> Unit) { taking.set(true); try { run() } finally { taking.set(false) } }

    /** The songs (title and artist, before and after) changed here and not yet taken by the other device. */
    fun waitingKeys(): Set<String> = runCatching { synchronized(lock) { pending() } }.getOrDefault(emptyList())
        .flatMap { c -> listOf(TagChanges.key(c.title, c.artist), TagChanges.after(c).let { (t, a) -> TagChanges.key(t, a) }) }.toSet()

    /** (blocking) What goes to the other device. */
    fun outgoing(): String = synchronized(lock) {
        val all = pending()
        val art = all.map { it.cover }.filter { it.isNotEmpty() }.distinct().mapNotNull { m ->
            runCatching { m to java.util.Base64.getEncoder().encodeToString(coverFile(m).readBytes()) }.getOrNull() }.toMap()
        JSON.encodeToString(TagChangesSent.serializer(), TagChangesSent(all, applied().toList().takeLast(2000), art))
    }

    /** (blocking) The other device's changes made here, to your copies of the same songs; its list of the changes it has
     *  taken lets this device's go. How many songs were changed. */
    fun take(text: String): Int {
        val got = runCatching { JSON.decodeFromString(TagChangesSent.serializer(), text) }.getOrNull() ?: return 0
        synchronized(lock) {
            val mine = pending()
            if (mine.removeAll { it.id in got.applied }) { writeAtomic(pendingFile, JSON.encodeToString(listSer, mine)); runCatching { tidyCovers(mine) } }
        }
        if (got.pending.isEmpty()) return 0
        val done = synchronized(lock) { applied() }
        // Your songs with files here, by title and artist
        val index = HashMap<String, MutableList<String>>()
        for (t in runCatching { Music.mySongs() }.getOrDefault(emptyList())) {
            val f = Music.lib.items[t.id]?.file ?: t.file?.takeIf { t.isFile } ?: continue
            if (Tags.writable(f) && !SyncIgnore.ignored(f)) index.getOrPut(TagChanges.key(t.title, t.artist)) { mutableListOf() }.let { if (f !in it) it += f }
        }
        val playing = runCatching { Music.current() }.getOrNull()?.let { Music.lib.items[it.id]?.file ?: it.file }
        var changed = 0
        val took = ArrayList<String>()
        for ((c, files) in TagChanges.plan(got.pending, index, done)) {
            var ok = true
            // A cover picked on the other device: the same picture into your copies here (your own choice, made there,
            // so it goes in as one picked by hand does). A picture that didn't come across: at the next sync.
            val jpeg = if (c.cover.isEmpty()) null else runCatching { java.util.Base64.getDecoder().decode(got.art[c.cover]!!) }.getOrNull()
            if (c.cover.isNotEmpty() && jpeg == null) ok = false
            for (f in files) {
                if (f == playing) { ok = false; continue }   // its file is being read: next sync
                taking.set(true)
                try {
                    if (c.changes.isNotEmpty()) {
                        val res = runCatching { Tags.write(Music.ctx, f, c.changes) }
                        if (res.isSuccess) { changed++; renameInLibrary(f, res.getOrThrow()) } else ok = false
                    }
                    if (jpeg != null) {
                        val wrote = runCatching { CoverGuard.byHand {
                            if (Music.lib.items.values.any { it.file == f && it.format == "flac" }) { Covers.write(Music.ctx, f, jpeg); true }
                            else FileTags.writeCover(Music.ctx, f, jpeg)
                        } }.getOrDefault(false)
                        if (wrote) { changed++; TagMatch.touched(f); coverInLibrary(f, jpeg) } else ok = false
                    }
                } finally { taking.set(false) }
            }
            if (ok) took += c.id   // (a song you haven't got here: nothing to do, taken all the same)
        }
        synchronized(lock) {
            val all = applied(); all += took
            writeAtomic(appliedFile, JSON.encodeToString(setSer, all.toList().takeLast(4000).toSet()))
        }
        return changed
    }

    /** A download's picture in your music follows the cover put into its file. */
    private fun coverInLibrary(file: String, jpeg: ByteArray) = runCatching {
        Music.lib.items.values.filter { it.file == file }.forEach { e ->
            val art = File(Music.lib.artDir, "${safeName(e.id)}-${randomHex(4)}.jpg").apply { writeBytes(jpeg) }
            val old = e.art
            Music.lib.update(e.id) { it.copy(art = art.path) }
            if (old.isNotEmpty() && Music.lib.items.values.none { it.art == old }) File(old).delete()
        }
    }

    /** A download's new names in your music (your own files are read again by the scan). */
    private fun renameInLibrary(file: String, c: Map<String, List<String>>) {
        val title = c["TITLE"]?.firstOrNull() ?: return
        val artist = c["ARTIST"]?.joinToString(", ")?.ifEmpty { null } ?: return
        val album = c["ALBUM"]?.firstOrNull() ?: ""
        Music.lib.items.values.filter { it.file == file }.forEach { e ->
            Music.lib.update(e.id) { it.copy(title = title, artist = artist, album = album, genre = c["GENRE"]?.firstOrNull() ?: it.genre) }
        }
    }
}
