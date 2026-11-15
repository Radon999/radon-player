package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The DJ's own part (docs/rebuild/RADON_REBUILD_PLAN.md, Phase 8): starting and stopping it, its sets one after
 *  another, the sets made ahead, its lines and its voice over the music. Taken out of [Music] piece by piece with
 *  nothing behaving differently; [Music] keeps every name the rest of the app uses and hands on to here. What a set
 *  is made of (the kinds, their rules, the lines' words) is [Dj]'s and the shared code's.
 *
 *  It reaches playback only through [PlaybackCore], and playback knows of it only [PlaybackCore.DjSide]. Of [Music]
 *  it asks the app's own things: your music, the settings, the player (its volume, turned down under the voice),
 *  notes to you.
 *
 *  Everything here runs on the app's main thread, as [Music] does. */
object DjDirector {
    // ---- what the screens show (Compose state, so a change redraws) ----
    var dj by mutableStateOf(Dj.State())          // the DJ: on, its sets' kinds and lines, the one shown
    var djBusy by mutableStateOf<String?>(null)   // the DJ picking a set: what it's doing

    // ---- what it asks of the app ([Music]) ----
    private val djNew get() = Music.djNew
    private fun <T> bg(work: () -> T, done: (T?, Throwable?) -> Unit = { _, _ -> }) = Music.bg(work, done)

    // ---- still in [Music], on their way here ----
    private val djVoice get() = Music.djVoice
    private val djLines get() = Music.djLines
    private fun djClean(text: String) = Music.djClean(text)
    private fun djLibrary() = Music.djLibrary()
    private fun djKind() = Music.djKind()
    private fun coming() = Music.coming()

    // ---- the first set, made ahead ----
    /** The DJ's first set, made ahead while it's off (with its line), so tapping DJ starts it at once. */
    internal class Warm(val src: Dj.From?, val withNew: Boolean, val set: Dj.Built, val at: Double)
    internal var djFirst: Warm? = null
    private var djWarming = false
    internal const val FIRST_FRESH = 30 * 60.0   // a first set made ahead is good for half an hour

    /** Make the first set ahead (when the DJ is off and there's none fresh): from where it played from last. */
    fun djWarm() {
        if (dj.on || Remote.active || djBusy != null || djWarming) return
        if (djFirst?.let { now() - it.at < FIRST_FRESH - 120 && it.withNew == djNew } == true) return
        val src = Music.DJ_LIBRARY
        val withNew = djNew
        runCatching {
            Dj.withNew = withNew
            val got = djLibrary() ?: return; if (got.second.isEmpty()) return; Dj.playFrom(got.first, got.second, whole = true)
        }.onFailure { return }
        djWarming = true
        bg({ Dj.build(first = true) }) { res, _ ->
            djWarming = false
            if (res == null || dj.on) { res?.let { Dj.discard(it.id) }; return@bg }
            djFirst?.let { Dj.discard(it.set.id) }   // the one it takes the place of was never played
            djFirst = Warm(src, withNew, res, now())
            djPrepareLine(res)
        }
    }

    // ---- the sets made ahead ----
    /** Sets made ahead for Switch it up and the set button (each with its line made too), so switching is instant
     *  however fast you go: [SPARES] are kept ready, made one after another in the background and made again after
     *  each new set, so they never repeat what's coming. */
    internal val djSpares = ArrayList<Dj.Built>()

    /** A set kept ready, to play now: of another kind than the one playing, if there is one. A set of what you play at
     *  this time of day that was made in another part of the day is let go first (it's no longer this time of day). */
    internal fun djSpare(): Dj.Built? {
        djSpares.filter { Dj.stale(it) }.forEach { Dj.discard(it.id); djSpares.remove(it) }
        return djSpares.firstOrNull { it.kind != djKind() } ?: djSpares.firstOrNull()
    }
    internal var djSpareJob = 0
    internal var djSpareBusy = false
    private const val SPARES = 3

    internal fun djMakeSpare() {
        if (!dj.on || Remote.active) return
        djSpares.forEach { Dj.discard(it.id) }
        djSpares.clear()
        djSpareJob++
        djSpareBusy = false
        djFillSpares()
    }

    /** One more set ahead, until there are [SPARES]. */
    internal fun djFillSpares() {
        if (!dj.on || Remote.active || djSpareBusy || djSpares.size >= SPARES) return
        djSpareBusy = true
        val mine = djSpareJob
        // Not what's coming, nor what the other spares have; a kind other than the last one's
        val avoid = djSpares.lastOrNull()?.kind ?: djKind()
        val coming = coming() + djSpares.flatMap { b -> b.songs.map { songKey(it) } }
        bg({ Dj.build(avoid = avoid, coming = coming) }) { res, _ ->
            if (mine != djSpareJob) { res?.let { Dj.discard(it.id) }; return@bg }   // started again meanwhile
            djSpareBusy = false
            if (res == null || !dj.on) { res?.let { Dj.discard(it.id) }; return@bg }
            djSpares += res
            djPrepareLine(res)
            djFillSpares()
        }
    }

    /** A set's line, made now (the same words it'll say). */
    private fun djPrepareLine(res: Dj.Built) {
        if (!djVoice) return
        val line = djLines.getOrPut(res.line + "\n" + res.fact) {
            Spoken.humanize(res.line) + if (res.fact.isNotEmpty()) " " + Spoken.humanize(res.fact, filler = 0.0) else "" }
        Voice.prepare(djClean(line))
    }
}
