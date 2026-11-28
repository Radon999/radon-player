package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** Download: which of the songs asked for are new, the asking about copies you have already, and the songs waiting
 *  saved a batch at a time ([Downloader] does the saving). The songs waiting ([Music.downloads]) and the line shown
 *  while it runs ([Music.downloadNote]) stay where the screens read them. [Music] keeps the names the screens call
 *  and hands on. */
object Downloads {
    private fun toast(text: String) = Music.toast(text)
    private var running = false

    // ---- kept over a restart: the songs still waiting, and the ones that didn't come ----
    // Before, both lived only while the app ran: closing it in the middle of 200 downloads lost the rest without a
    // word, and a song that failed was named once in a message and then forgotten.
    @Serializable data class Failed(val track: Track, val why: String = "")
    private val FAILED = ListSerializer(Failed.serializer())
    private val WAITING = ListSerializer(Track.serializer())
    private fun queueFile() = File(Music.ctx.filesDir, "download-queue.json")
    private fun failedFile() = File(Music.ctx.filesDir, "download-failed.json")

    /** The songs that didn't download, the newest first, each with why (Downloads › Didn't download). */
    var failed by mutableStateOf(listOf<Failed>())
        private set

    private fun keepQueue() { val q = Music.downloads; Music.bg({ runCatching { writeAtomic(queueFile(), JSON.encodeToString(WAITING, q)) } }) }
    private fun keepFailed(now: List<Pair<Track, String>>) {
        failed = now.map { Failed(it.first, it.second) }
        val f = failed
        Music.bg({ runCatching { writeAtomic(failedFile(), JSON.encodeToString(FAILED, f)) } })
    }

    /** As the app starts: the list of the ones that didn't come, and the downloads left waiting last time carried on
     *  (a little later, once the services are connected). */
    fun restore() {
        Music.bg({
            val f = readText(failedFile())?.let { runCatching { JSON.decodeFromString(FAILED, it) }.getOrNull() }.orEmpty()
            val q = readText(queueFile())?.let { runCatching { JSON.decodeFromString(WAITING, it) }.getOrNull() }.orEmpty()
            f to q
        }) { got, _ ->
            val (f, q) = got ?: return@bg
            failed = f.filter { !Music.lib.has(it.track.id) }
            val left = newOf(q, { Music.lib.has(it) }, Music.downloads.map { it.id }.toSet())
            if (left.isEmpty()) { if (q.isNotEmpty()) keepQueue(); return@bg }
            Music.scope.launch {
                kotlinx.coroutines.delay(15_000)
                val now = newOf(left, { Music.lib.has(it) }, Music.downloads.map { it.id }.toSet())
                if (now.isEmpty()) return@launch
                Music.downloads = Music.downloads + now
                toast(DownloadQueueRules.carryOn(now.size))
                Music.savedVersion++
                if (!running) next()
            }
        }
    }

    /** Try again: these of the ones that didn't come (all of them when none are named). */
    fun retry(tracks: List<Track>? = null) {
        val again = tracks ?: failed.map { it.track }
        if (again.isEmpty()) return
        val ids = again.map { it.id }.toSet()
        keepFailed(failed.filter { it.track.id !in ids }.map { it.track to it.why })
        start(again, checked = true)
    }

    fun clearFailed() = keepFailed(emptyList())

    /** The songs of tracks to download: not your own files, not downloaded or waiting already (has), not
     *  SoundCloud's (they only stream). */
    fun newOf(tracks: List<Track>, has: (String) -> Boolean, waiting: Set<String>): List<Track> =
        tracks.filter { !it.isFile && !has(it.id) && it.id !in waiting && it.source != SoundCloud.KEY }

    /** Why a song can't be downloaded now, in a few words; "" when it can (a streamed song you don't have). */
    fun whyNot(t: Track?, has: (String) -> Boolean, waiting: Set<String>): String = when {
        t == null -> "nothing is playing"
        t.isFile -> "it is your own file"
        has(t.id) -> "it is downloaded already"
        t.id in waiting -> "it is being downloaded"
        t.source == SoundCloud.KEY -> "SoundCloud songs only stream"
        else -> ""
    }

    /** How downloads come, for a toast: "in FLAC (hi-res where Tidal has it)", "from YouTube"… */
    fun qualityText(tidalConnected: Boolean, quality: String, canMp3: Boolean): String =
        if (!tidalConnected) "from YouTube, its best (Opus 160 kbps; Tidal isn't connected)" else when (quality) {
            "flac16" -> "in FLAC 16-bit"
            "mp3" -> if (canMp3) "in MP3 320 kbps" else "at 320 kbps (AAC)"
            else -> "in FLAC (hi-res where Tidal has it)"
        }

    fun qualityNote(): String = qualityText(Music.tidal.connected, Music.downloadQuality, CAN_MP3)

    /** Why the song playing can't be downloaded now; "" when it can. The Download button in the notification and on
     *  the lock screen is only there when it can. */
    fun nowNote(): String = whyNot(Music.current(), { Music.lib.has(it) }, Music.downloads.map { it.id }.toSet())

    /** Download these: the new ones among them. Unless checked (you answered already), a song you seem to have a copy
     *  of is asked about first (Copies: ask), or your copy goes once it's in (Copies: replace). */
    fun start(tracks: List<Track>, checked: Boolean = false) {
        val waiting = Music.downloads.map { it.id }.toSet()
        if (tracks.isNotEmpty() && tracks.all { it.source == SoundCloud.KEY }) { toast("SoundCloud songs can't be downloaded: they only stream"); return }
        tracks.forEach { Copies.heal(it) }   // a download whose file was moved: found again, before it's called missing
        val asked = newOf(tracks, { Music.lib.has(it) }, waiting)
        if (asked.isEmpty()) { toast("Those are downloaded already"); return }
        // A song you have already (the same title and artist, whatever its album is tagged as) is never downloaded a
        // second time: your copy is the one that plays. Unless you said to keep both (in Settings, or when asked).
        val (new, have) = CopyRules.toDownload(asked, keepBoth = checked || Music.dupeAction != "ask") { Copies.ownCopy(it) != null }
        if (have.isNotEmpty()) toast(CopyRules.haveText(have.map { it.title }))
        if (new.isEmpty()) return
        if (!checked && Music.dupeAction != "keep") {
            val dupes = new.mapNotNull { t -> Music.copiesOf(t).takeIf { it.isNotEmpty() }?.let { t to it } }
            if (dupes.isNotEmpty()) {
                if (Music.dupeAction == "replace") dupes.forEach { (t, c) -> Music.replaceLater(t.id, c) }
                else { Music.dupePrompt = new to dupes; return }
            }
        }
        Music.downloads = Music.downloads + new
        keepQueue()
        toast("Downloading ${new.size} song${if (new.size != 1) "s" else ""} ${qualityNote()}" + if (Remote.active) " to this phone" else "")
        Music.savedVersion++
        if (!running) next()
    }

    /** The songs waiting, saved together; then any that came in meanwhile; the tagger waits until all are in. */
    private fun next() {
        if (Music.downloads.isEmpty()) {
            if (running) {
                Tagger.resume()   // the songs that came in are tagged together now
                // Every download of the batch is in: your folders are read again, so the new files are counted and
                // anything else new in them shows too (one download, or hundreds: once, when the last is done)
                runCatching { Music.rescan() }
            }
            running = false; Music.downloadNote = ""; Music.savedVersion++; Music.musicVersion++; return
        }
        if (!running) Tagger.pause()
        running = true
        val batch = Music.downloads
        Music.downloadNote = "Downloading · 0 of ${batch.size}"
        Music.bg({ Music.dl.sync(batch, force = true, workers = Downloader.SONGS_AT_ONCE) { n -> Music.scope.launch { Music.savedVersion++; Music.downloadNote = "Downloading · $n of ${batch.size}" } } }) { out, err ->
            Music.downloads = Music.downloads.filter { d -> batch.none { it.id == d.id } }
            keepQueue()
            // The ones that didn't come are kept, each with why (the whole batch when it couldn't even begin)
            val bad = if (out != null) out.failed.map { it.first to DownloadQueueRules.why(it.second) } + out.missing.map { it to DownloadQueueRules.NOT_FOUND }
                else batch.filter { !Music.lib.has(it.id) }.map { it to DownloadQueueRules.why(err?.message) }
            keepFailed(DownloadQueueRules.failedNow(failed.map { it.track to it.why }, bad, { Music.lib.has(it.id) }) { it.id })
            toast((out?.summary() ?: "Couldn't download: ${err?.message?.take(90)}") + if (bad.isNotEmpty()) ". They're kept in Downloads to try again" else "")
            next()
        }
    }
}
