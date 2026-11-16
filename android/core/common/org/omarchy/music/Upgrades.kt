package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Better copies of what you have: Upgrade (your own files for Tidal's FLAC where it's better), Downloads' Upgrade
 *  (the downloads that came from YouTube got again from Tidal), and Downloads' Refresh. [Downloader] does the
 *  fetching; how far each has got ([Music.upgrading], [Music.flacing]) stays where the screens read it. [Music]
 *  keeps the names the screens call and hands on. */
object Upgrades {
    private fun toast(text: String) = Music.toast(text)

    /** YouTube downloads looked up on Tidal already, this time the app's open: read by the screen while a run adds to it. */
    private val tried: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** What Upgrade says at its end. */
    fun doneText(done: Int, missing: Int, kept: Int, failed: Int): String =
        "Upgraded $done song${if (done != 1) "s" else ""} to FLAC" +
            (if (missing > 0) " · $missing not on Tidal" else "") +
            (if (kept > 0) " · $kept as good already" else "") +
            (if (failed > 0) " · $failed couldn't" else "")

    /** What Downloads' Upgrade says at its end. */
    fun flacText(got: Int, none: Int): String = "$got upgraded (FLAC from Tidal, else AAC)" + if (none > 0) " · $none left as they are" else ""

    /** What Downloads' Refresh says. */
    fun refreshedText(more: Int): String = if (more > 0) "Refreshed: $more more download${if (more != 1) "s" else ""}" else "Downloads are up to date"

    /** Upgrade: your files (tracks, or all of them) got again from Tidal in FLAC where that's better; the old files
     *  go to the bin once you say yes, and the song playing carries on in its new file. force: the ones as good
     *  already too. */
    fun songs(tracks: List<Track>? = null, force: Boolean = false) {
        if (Music.upgrading != null) return
        if (!Music.tidal.connected) { toast("Connect Tidal in Settings first: the new copies come from there, in FLAC"); return }
        val todo = (tracks ?: Music.local.files).filter { upgradable(it) }
        if (todo.isEmpty()) { toast("No songs of yours to upgrade"); return }
        Music.upgradeStop = false
        Music.upgrading = "Upgrading · 0 of ${todo.size}"
        Tagger.pause()   // the new FLACs are tagged together at the end
        val roots = Music.folders.sortedByDescending { it.length }
        Music.bg({ Music.dl.upgrade(todo, force, { Music.upgradeStop }, { t -> Music.folderOf(t).let { f -> roots.firstOrNull { f.startsWith(it) } } }) { n ->
            Music.scope.launch { Music.upgrading = "Upgrading · $n of ${todo.size}" } } }) { out, err ->
            Music.upgrading = null
            Tagger.resume()
            if (out == null) { toast("Couldn't upgrade: ${err?.message?.take(90)}"); return@bg }
            val lib = Music.lib
            val playing = Music.current()?.id
            // The song playing carries on in its new file from the same moment; its old file goes with the rest.
            out.done.firstOrNull { (old, id) -> old.id == playing && lib.has(id) }?.let { (old, id) -> swapPlaying(old, id) }
            val done = out.done.filter { (_, id) -> lib.has(id) }
            if (done.isNotEmpty()) Music.queueTrash(Music.TrashJob(done.map { it.first }, done.associate { (old, id) -> old.id to lib.items[id]!!.track() }, "upgraded songs"))
            toast(doneText(done.size, out.missing.size, out.kept.size, out.failed.size))
            if (out.kept.isNotEmpty() && !force) Music.upgradeKept = out.kept
            Music.savedVersion++; Music.musicVersion++
        }
    }

    /** Downloads that came from YouTube (Opus), and the YouTube AAC ones not looked up on Tidal yet: Downloads' Upgrade. */
    fun youtubeDownloads() = Music.lib.items.values.filter { it.format == "opus" || (it.quality.startsWith("YouTube") && it.id !in tried) }

    /** Downloads' Upgrade: each of those got again from Tidal in FLAC where it has it, else AAC in place of Opus. */
    fun toFlac() {
        if (Music.flacing != null) return
        val todo = youtubeDownloads().map { it.id }
        if (todo.isEmpty()) return
        Music.flacing = "Getting FLAC… 0 of ${todo.size}"
        Music.bg({
            var got = 0; var none = 0; var i = 0
            for (id in todo) {
                i++
                val at = i
                Music.scope.launch(Dispatchers.Main) { Music.flacing = "Getting FLAC… $at of ${todo.size}" }
                if (runCatching { Music.dl.toFlac(id) }.getOrDefault(false)) got++ else none++
                tried += id
            }
            got to none
        }) { r, err ->
            Music.flacing = null
            Music.savedVersion++; Music.musicVersion++
            toast(err?.message?.let { "Couldn't get FLAC: $it" } ?: r?.let { (got, none) -> flacText(got, none) } ?: "")
        }
    }

    /** Downloads' Refresh: the list read again (a download that didn't show up comes in). */
    fun refresh() {
        Music.bg({ Music.lib.refresh() }) { n, _ ->
            Music.savedVersion++; Music.musicVersion++
            toast(refreshedText(n ?: 0))
        }
    }

    /** The song playing (old) was upgraded to id: play on in the new file from where you are. */
    internal fun swapPlaying(old: Track, id: String) {
        val new = Music.lib.items[id]?.track() ?: return
        val at = Music.player?.positionMs?.let { it / 1000.0 } ?: Music.position
        val wasPaused = Music.paused
        Music.retarget(listOf(old.id), new)
        Music.queue = Music.queue.toMutableList().also { if (Music.index in it.indices) it[Music.index] = new.slim() }
        Music.resumeAt = at
        PlaybackCore.goingBack = true   // not a skip
        Music.loadCurrent(autoplay = !wasPaused)
    }
}
