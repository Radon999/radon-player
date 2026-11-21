package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonElement
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
    fun songs(tracks: List<Track>? = null, force: Boolean = false, picks: Map<String, JsonElement> = emptyMap()) {
        if (Music.upgrading != null) return
        if (!Music.tidal.connected) { toast("Connect Tidal in Settings first: the new copies come from there, in FLAC"); return }
        val todo = (tracks ?: Music.local.files).filter { upgradable(it) }
        if (todo.isEmpty()) { toast("No songs of yours to upgrade"); return }
        Music.upgradeStop = false
        Music.upgrading = "Upgrading · 0 of ${todo.size}"
        Tagger.pause()   // the new FLACs are tagged together at the end
        val roots = Music.folders.sortedByDescending { it.length }
        Music.bg({ Music.dl.upgrade(todo, force, { Music.upgradeStop }, { t -> Music.folderOf(t).let { f -> roots.firstOrNull { f.startsWith(it) } } }, picks) { n ->
            Music.scope.launch { Music.upgrading = "Upgrading · $n of ${todo.size}" } } }) { out, err ->
            Music.upgrading = null
            Tagger.resume()
            if (out == null) { toast("Couldn't upgrade: ${err?.message?.take(90)}"); return@bg }
            val lib = Music.lib
            val playing = Music.current()?.id
            // The song playing carries on in its new file from the same moment; its old file goes with the rest.
            out.done.firstOrNull { (old, id) -> old.id == playing && lib.has(id) }?.let { (old, id) -> swapPlaying(old, id) }
            val done = out.done.filter { (_, id) -> lib.has(id) }
            FileCheck.replaced(done.map { it.first })   // (a damaged file with its new copy here leaves the damaged list)
            if (done.isNotEmpty()) Music.queueTrash(Music.TrashJob(done.map { it.first }, done.associate { (old, id) -> old.id to lib.items[id]!!.track() }, "upgraded songs"))
            toast(doneText(done.size, out.missing.size, out.kept.size, out.failed.size))
            if (out.kept.isNotEmpty() && !force) Music.upgradeKept = out.kept
            Music.savedVersion++; Music.musicVersion++
        }
    }

    /** What Download new copies says of the app's own downloads. */
    fun againText(got: Int, failed: Int): String = "Downloaded $got damaged song${if (got != 1) "s" else ""} again" +
        if (failed > 0) " · $failed couldn't (left as they are)" else ""

    /** Whether Download new copies can get t again: one of the app's downloads, or a file of yours Upgrade can swap. */
    fun canGetAgain(t: Track) = Music.lib.has(t.id) || upgradable(t)

    /** Storage and damaged files' Download new copies. The app's own downloads among them are got again under the same
     *  song ([Downloader.again]); your own files go through Upgrade, forced (every one Tidal has), which bins the
     *  damaged file once its new copy is here and you say yes. */
    /** What a damaged song would be replaced with: the Tidal song itself (saved as it is when you go on), how it
     *  reads, and in what it differs from yours. */
    class Planned(val match: JsonElement, val track: Track, val info: String, val differs: List<String>)
    /** By song: what was found for it (null: Tidal hasn't got it). A song not in here hasn't been looked up. */
    var plans by mutableStateOf<Map<String, Planned?>>(emptyMap())
        private set
    var planning by mutableStateOf<String?>(null)   // "Looking up 3 of 12…" while it runs
        private set

    /** Each of these damaged songs looked up, as its download would look it up, and nothing downloaded: [plans]. */
    fun plan(tracks: List<Track>) {
        if (planning != null || tracks.isEmpty()) return
        if (!Music.tidal.connected) { toast("Connect Tidal in Settings first: the new copies come from there"); return }
        planning = "Looking up 0 of ${tracks.size}…"
        val lib = Music.lib
        Music.bg({
            val out = HashMap<String, Planned?>()
            tracks.forEachIndexed { i, t ->
                val saved = lib.items[t.id] ?: lib.items.values.firstOrNull { it.file == t.file }
                val m = runCatching { Music.dl.planAgain(saved?.track() ?: t, own = saved == null) }.getOrNull()
                out[t.id] = m?.let {
                    val n = Tidal.trackFromTidal(it)
                    val hi = "HIRES_LOSSLESS" in it.path("mediaMetadata", "tags").list.map { x -> x.str }
                    Planned(it, n, listOfNotNull(n.album.ifBlank { null }, fmtTime(n.duration), if (hi) "hi-res" else "CD quality",
                        if (it["explicit"].bool == true) "explicit" else null).joinToString(" · "),
                        ReplaceRules.differs(t.title, t.artist, t.album, t.duration, n.title, n.artist, n.album, n.duration))
                }
                Music.scope.launch { if (planning != null) planning = "Looking up ${i + 1} of ${tracks.size}…" }
            }
            out
        }) { out, err ->
            planning = null
            if (out == null) toast("Couldn't look them up: ${err?.message?.take(90)}") else plans = plans + out
        }
    }

    fun damaged(tracks: List<Track>, picks: Map<String, JsonElement> = emptyMap()) {
        if (Music.upgrading != null) return
        val lib = Music.lib
        val saved = tracks.mapNotNull { t -> (lib.items[t.id] ?: lib.items.values.firstOrNull { it.file == t.file })?.let { t to it.id } }.distinctBy { it.second }
        val savedIds = saved.map { it.first.id }.toSet()
        val mine = tracks.filter { it.id !in savedIds && upgradable(it) }
        if (saved.isEmpty()) { songs(mine, force = true, picks = picks); return }
        Music.upgradeStop = false
        Music.upgrading = "Downloading again · 0 of ${saved.size}"
        val roots = Music.folders.sortedByDescending { it.length }
        val playing = Music.current()?.id
        Music.bg({
            var got = 0; var failed = 0
            val fresh = java.util.Collections.synchronizedList(ArrayList<Track>())
            saved.forEachIndexed { i, (t, id) ->
                if (Music.upgradeStop) return@forEachIndexed
                val root = runCatching { Music.folderOf(t).let { f -> roots.firstOrNull { f.startsWith(it) } } }.getOrNull()
                if (id == playing) failed++
                else try { Music.dl.again(id, root, picks[t.id]); got++; fresh += t } catch (e: Exception) {
                    failed++
                    Crashes.caught(e, "Downloading ${t.artist} – ${t.title} again (its file is damaged)")
                }
                Music.scope.launch { if (Music.upgrading != null) Music.upgrading = "Downloading again · ${i + 1} of ${saved.size}" }
            }
            Triple(got, failed, fresh.toList())
        }) { out, err ->
            Music.upgrading = null
            out?.third?.let { FileCheck.replaced(it) }
            Music.savedVersion++; Music.musicVersion++
            toast(out?.let { againText(it.first, it.second) } ?: "Couldn't download them again: ${err?.message?.take(90)}")
            if (mine.isNotEmpty()) songs(mine, force = true, picks = picks)
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
