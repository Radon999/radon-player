package org.omarchy.music

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** Sync's downloads: each device sends the songs it has downloaded, and the other downloads the ones it hasn't got
 *  (from the service, as any download: tagged, its cover, in My music). Each song is only ever taken once: one you
 *  later delete here isn't downloaded again by the next sync. */
object DownloadSync {
    private val seenFile get() = File(Music.ctx.filesDir, "sync-downloads-seen.json")
    private val setSer = SetSerializer(String.serializer())
    private val listSer = ListSerializer(Track.serializer())

    /** (blocking) The songs downloaded on this device. */
    fun mine(): String {
        val songs = runCatching { synchronized(Music.lib) { Music.lib.items.values.map { it.track() } } }.getOrDefault(emptyList())
        return JSON.encodeToString(listSer, songs)
    }

    /** (the screen's thread) The other device's downloads: the ones new to this device are downloaded. from: its name.
     *  How many were started. */
    fun take(text: String, from: String): Int {
        val theirs = runCatching { JSON.decodeFromString(listSer, text) }.getOrNull() ?: return 0
        val seen = (readText(seenFile)?.let { runCatching { JSON.decodeFromString(setSer, it) }.getOrNull() } ?: emptySet()).toHashSet()
        seen += synchronized(Music.lib) { Music.lib.items.keys.toList() }   // this device's own downloads: nothing to take
        val want = ArrayList<Track>()
        for (t in theirs) {
            if (t.id.isEmpty() || !seen.add(t.id)) continue
            if (Music.lib.has(t.id) || runCatching { Music.copiesOf(t) }.getOrDefault(emptyList()).isNotEmpty()) continue
            want += t
        }
        runCatching { writeAtomic(seenFile, JSON.encodeToString(setSer, seen)) }
        if (want.isNotEmpty()) {
            Music.toast("Downloading ${want.size} song${if (want.size != 1) "s" else ""} you downloaded on $from")
            Music.download(want, checked = true)
        }
        return want.size
    }
}
