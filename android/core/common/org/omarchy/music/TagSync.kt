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
    fun note(title: String, artist: String, changes: Map<String, String>) {
        if (taking.get() == true || title.isBlank()) return
        val kept = TagChanges.kept(changes).takeIf { it.isNotEmpty() } ?: return
        runCatching { synchronized(lock) {
            val all = pending()
            all += TagChange(randomHex(8), now(), title, artist, kept)
            writeAtomic(pendingFile, JSON.encodeToString(listSer, all))
        } }
    }

    /** (blocking) What goes to the other device. */
    fun outgoing(): String = synchronized(lock) {
        JSON.encodeToString(TagChangesSent.serializer(), TagChangesSent(pending(), applied().toList().takeLast(2000)))
    }

    /** (blocking) The other device's changes made here, to your copies of the same songs; its list of the changes it has
     *  taken lets this device's go. How many songs were changed. */
    fun take(text: String): Int {
        val got = runCatching { JSON.decodeFromString(TagChangesSent.serializer(), text) }.getOrNull() ?: return 0
        synchronized(lock) {
            val mine = pending()
            if (mine.removeAll { it.id in got.applied }) writeAtomic(pendingFile, JSON.encodeToString(listSer, mine))
        }
        if (got.pending.isEmpty()) return 0
        val done = synchronized(lock) { applied() }
        // Your songs with files here, by title and artist
        val index = HashMap<String, MutableList<String>>()
        for (t in runCatching { Music.mySongs() }.getOrDefault(emptyList())) {
            val f = Music.lib.items[t.id]?.file ?: t.file?.takeIf { t.isFile } ?: continue
            if (Tags.writable(f)) index.getOrPut(TagChanges.key(t.title, t.artist)) { mutableListOf() }.let { if (f !in it) it += f }
        }
        val playing = runCatching { Music.current() }.getOrNull()?.let { Music.lib.items[it.id]?.file ?: it.file }
        var changed = 0
        val took = ArrayList<String>()
        for ((c, files) in TagChanges.plan(got.pending, index, done)) {
            var ok = true
            for (f in files) {
                if (f == playing) { ok = false; continue }   // its file is being read: next sync
                taking.set(true)
                try {
                    val res = runCatching { Tags.write(Music.ctx, f, c.changes) }
                    if (res.isSuccess) { changed++; renameInLibrary(f, res.getOrThrow()) } else ok = false
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
