package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/** Queued clean/explicit changes (Utilities): songs you have in the wrong version, put aside to change (select songs ›
 *  Queue a clean/explicit change). Songs got from another service or downloader (DumpMedia, mostly) often come as the
 *  clean version when you wanted the explicit one, or the other way round. The scan looks each one up on Tidal by its
 *  name, artist, album and length and finds the version picked beside the scan (Explicit or Clean); Replace downloads
 *  that version and puts your old file in the trash (Android's, or the Recycle Bin), the new one taking its place in your
 *  playlists, queue and plays. The list is kept as a setting, one song a line ("title\tartist\talbum"), so it goes across
 *  with Sync like Queued mood edits; what the scan found is this device's own, until the list changes. */
object VersionQueue {
    const val PREF = "version_queue"
    private const val WANT = "version_queue_want"   // "explicit" or "clean": what the songs are changed to

    class Item(val title: String, val artist: String, val album: String) {
        val key get() = statsKey(title, artist)
    }

    fun items(): List<Item> = Prefs.text(PREF).lines().mapNotNull { l ->
        val f = l.split("\t")
        if (f.size < 2 || f[0].isBlank()) null else Item(f[0], f[1], f.getOrNull(2).orEmpty())
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
    private fun save(list: List<Item>) {
        Prefs.setText(PREF, list.distinctBy { it.key }.joinToString("\n") {
            listOf(clean(it.title), clean(it.artist), clean(it.album)).joinToString("\t") })
        version++
    }
    /** Bumped at each change of the list here, so the screen shows it (a Sync's change shows the next time it's drawn). */
    var version by mutableStateOf(0)
        private set

    /** Songs put on the list (once each). */
    fun add(tracks: List<Track>) {
        val now = items()
        val have = now.map { it.key }.toSet()
        val new = tracks.filter { it.title.isNotBlank() }.distinctBy { statsKey(it.title, it.artist) }.filter { statsKey(it.title, it.artist) !in have }
        save(now + new.map { Item(it.title, it.artist, it.album) })
        Music.toast(if (new.isEmpty()) "Already in Queued clean/explicit changes" else
            "%,d song%s put in Queued clean/explicit changes (Utilities)".format(new.size, if (new.size != 1) "s" else ""))
    }

    fun remove(i: Item) {
        save(items().filter { it.key != i.key })
        found.remove(i.key); state.remove(i.key)
        if (previewing == i.key) stopPreview()
    }

    fun clear() { save(emptyList()); found.clear(); state.clear(); stopPreview() }

    /** Explicit (true) or clean: what the songs on the list are changed to (the menu beside Scan). */
    var explicit by mutableStateOf(Prefs.text(WANT, "explicit") != "clean")
        private set
    fun want(explicit: Boolean) {
        if (explicit == this.explicit) return
        this.explicit = explicit
        Prefs.setText(WANT, if (explicit) "explicit" else "clean")
        found.clear(); state.clear()   // found for the other version: look again
    }

    /** Your copy of the song (a file of yours or a download), or null when it isn't on this device. */
    fun song(i: Item): Track? = runCatching { Music.songIndex()[i.key] }.getOrNull()?.takeIf { it.isFile || Music.lib.has(it.id) }

    // ---- the scan ----
    /** What the scan found for a song: Tidal's song in the version wanted (null: none), whether it's the explicit one,
     *  and a word about it. */
    class Found(val tidal: JsonElement?, val track: Track?, val note: String, val explicit: Boolean = false)
    /** By the song's key: what the last scan found (for the version picked then). */
    val found = mutableStateMapOf<Pair<String, String>, Found>()
    /** By the song's key: where its change is ("Downloading…", "Replaced…", or why it couldn't be). */
    val state = mutableStateMapOf<Pair<String, String>, String>()
    /** Where the scan is: (done, of) while it runs, null otherwise. */
    var scanning by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    @Volatile private var stop = false
    fun stopScan() { stop = true }

    /** Looks every song on the list up on Tidal (its name, artist, album and length) for the version picked. */
    fun scan() {
        if (scanning != null) return
        if (!Music.tidal.signedIn) { Music.toast("The scan finds the other version on Tidal: sign in to Tidal in Settings first"); return }
        val list = items()
        if (list.isEmpty()) return
        val want = explicit
        stop = false
        scanning = 0 to list.size
        Music.bg({
            val out = LinkedHashMap<Pair<String, String>, Found>()
            for ((n, i) in list.withIndex()) {
                if (stop) break
                Music.scope.launch { scanning = n to list.size }
                val mine = song(i)
                val t = mine ?: Track("", "file", i.title, i.artist, i.album)
                val c = runCatching { Music.dl.versionToSave(t, want) }
                val got = c.getOrNull()
                val have = mine?.let { Music.lib.items[it.id]?.tidal } ?: mine?.tidal
                out[i.key] = when {
                    c.isFailure -> Found(null, null, "Couldn't look it up: ${c.exceptionOrNull()?.message?.take(80)}")
                    got == null -> Found(null, null, "Tidal has no ${if (want) "explicit" else "clean"} version of it")
                    have != null && have == got["id"].long -> Found(null, null, "Yours is the ${if (want) "explicit" else "clean"} version already")
                    else -> Found(got, Tidal.trackFromTidal(got), "", got["explicit"].bool == true)
                }
            }
            out
        }) { res, err ->
            scanning = null
            if (res != null) { found.putAll(res); res.keys.forEach { state.remove(it) } }
            val n = res?.values?.count { it.tidal != null } ?: 0
            Music.toast(if (err != null) "The scan stopped: ${err.message?.take(80)}"
                else "%,d of %,d found in the %s version".format(n, list.size, if (want) "explicit" else "clean"))
        }
    }

    // ---- replacing ----
    /** The version found downloaded, then your old copy in the trash, the new one in its place in your playlists, queue and
     *  plays (Copies.replaceCopies); the song off the list. Not the song playing now: skip it first. */
    fun replace(i: Item) {
        val f = found[i.key] ?: return
        val c = f.tidal ?: return
        val newT = f.track ?: return
        if (state[i.key] == DOWNLOADING) return
        val old = song(i)
        if (old != null && Music.current()?.id == old.id) { state[i.key] = "It's the song playing now: skip it first"; return }
        if (previewing == i.key) stopPreview()
        state[i.key] = DOWNLOADING
        val kind = if (c["explicit"].bool == true) "explicit" else "clean"
        Music.bg({ Music.dl.download(newT, youtubeToo = false, final = c) }) { _, e ->
            if (e != null) { state[i.key] = "Couldn't download it: ${e.message?.take(100)}"; return@bg }
            Music.musicVersion++; Music.savedVersion++
            if (old != null && old.id != newT.id) Copies.replaceCopies(listOf(old), newT, "the old version of “${i.title}”")
            save(items().filter { it.key != i.key }); found.remove(i.key); state.remove(i.key)
            Music.toast("The $kind version of “${i.title}” is in your music" + if (old != null) "; your old one goes to the trash" else "")
        }
    }
    private const val DOWNLOADING = "Downloading…"

    /** Every song the scan found a version for, replaced one after another. */
    fun replaceAll() = items().filter { found[it.key]?.tidal != null }.forEach { replace(it) }

    // ---- listening before you choose ----
    /** The song (its key) whose found version is playing beside the music (the music paused meanwhile), or null. */
    var previewing by mutableStateOf<Pair<String, String>?>(null)
        private set
    private var preview: Engine? = null

    /** Tidal's version found for the song, played on a second player (the queue left as it is), so you can hear it's the
     *  same song in the version you want before you replace yours. Again: stopped. */
    fun listen(i: Item) {
        if (previewing == i.key) { stopPreview(); return }
        val c = found[i.key]?.tidal ?: return
        stopPreview()
        if (Music.player?.isPlaying == true) Music.togglePause()
        val key = i.key
        previewing = key
        Music.bg({ Music.tidal.stream(c, lossy = true, quality = "HIGH").uri }) { uri, e ->
            if (previewing != key) return@bg
            val x = uri?.let { u -> runCatching { Music.player?.tail(u, 0) }.getOrNull() }
            if (x == null) { previewing = null; Music.toast("Can't play it here" + (e?.message?.let { ": ${it.take(80)}" } ?: "")); return@bg }
            x.volume = 1f; x.playWhenReady = true; x.play()
            preview = x
        }
    }

    fun stopPreview() {
        preview?.let { runCatching { it.release() } }
        preview = null; previewing = null
    }
}
