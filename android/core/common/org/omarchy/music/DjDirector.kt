package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var djNew by mutableStateOf(true)             // the DJ brings new songs too, or only yours

    /** Only my songs (off), or new ones too (on): the DJ starts again with it. */
    fun chooseDjNew(on: Boolean) {
        djNew = on; Dj.withNew = on; saveState()
        if (dj.on) djStart() else dj = dj.copy(from = DJ_LIBRARY)
    }

    /** Which songs a set may have when it's queued: with new music off, only yours (a file, or a download); else any.
     *  Set building uses the same rule (Dj.playable), before the line is written, so the DJ never names a song the
     *  queue then leaves out. */
    private fun queueable(t: Track) = djNew || t.isFile || lib.has(t.id)
    init { Dj.playable = { queueable(it) } }

    // ---- what it asks of the app ([Music]) ----
    private val lists get() = Music.lists
    private val local get() = Music.local
    private val lib get() = Music.lib
    private fun mySongs() = Music.mySongs()
    private fun librarySongs() = Music.librarySongs()
    private fun localsOf(tracks: List<Track>) = Music.localsOf(tracks)
    private fun saveState() = Music.saveState()
    private val scope get() = Music.scope
    private val player get() = Music.player
    private val cast get() = Music.cast
    private fun bitPerfectNow() = Music.bitPerfectNow()
    private fun toast(text: String) = Music.toast(text)
    private fun <T> bg(work: () -> T, done: (T?, Throwable?) -> Unit = { _, _ -> }) = Music.bg(work, done)

    // ---- what it asks of playback ([PlaybackCore]) ----
    private var queue: List<Track> get() = PlaybackCore.queue; set(v) { PlaybackCore.queue = v }
    private var index: Int get() = PlaybackCore.index; set(v) { PlaybackCore.index = v }
    private fun current() = PlaybackCore.current()
    private var repeat: String get() = PlaybackCore.repeat; set(v) { PlaybackCore.repeat = v }
    private var radioFrom: String? get() = PlaybackCore.radioFrom; set(v) { PlaybackCore.radioFrom = v }
    private var context: Int get() = PlaybackCore.context; set(v) { PlaybackCore.context = v }
    private fun playFrom(tracks: List<Track>, i: Int, radio: Boolean) = PlaybackCore.playFrom(tracks, i, radio)
    private fun loadCurrent() = PlaybackCore.loadCurrent()
    private fun afterQueueChange() = PlaybackCore.afterQueueChange()
    private fun doneAdvancing() = PlaybackCore.doneAdvancing()
    private fun next() = PlaybackCore.next()
    private var advancing: Boolean get() = PlaybackCore.advancing; set(v) { PlaybackCore.advancing = v }
    private val loadedFor get() = PlaybackCore.loadedFor
    private val streaming get() = PlaybackCore.streaming
    private fun step(what: String) = PlaybackCore.step(what)

    // ---- what it shows, and what it plays from ----
    /** What the DJ screen shows: the phone's DJ, or the desktop's while it's the remote. */
    fun shownDj(): Dj.Shown {
        if (Remote.active) return Remote.status.dj
        val shown = dj.sets[dj.shown].takeIf { dj.on }
        return Dj.Shown(dj.on, djBusy ?: "", shown?.kind ?: "", shown?.line ?: "", dj.sets.mapValues { it.value.kind }, djFromName(dj.from), shown?.fact ?: "")
    }

    /** What the DJ plays from, as [Dj.From] keeps it: (name, songs), or null if it's gone. */
    fun djSource(f: Dj.From?): Pair<String, List<Track>>? = when (f?.kind) {
        "library" -> LIBRARY to librarySongs()
        "playlist" -> lists.get(f.id)?.let { it.name to it.tracks }
        "folder" -> djFromName(f) to mySongs().filter { (local.paths[it.file] ?: "").startsWith(f.path) }
        "mine" -> "your music" to mySongs()
        "songs" -> djFromName(f) to f.songs
        else -> null
    }

    fun djFromName(f: Dj.From?): String = when (f?.kind) {
        null, "all" -> ""
        "library" -> LIBRARY
        "playlist" -> lists.get(f.id)?.name ?: "a playlist that's gone"
        "folder" -> f.path.trimEnd('/').substringAfterLast('/').ifEmpty { f.path }
        "mine" -> "your music"
        else -> f.name.ifEmpty { "these songs" }
    }

    /** What the DJ always plays from: every song of yours, in one group (see [librarySongs]). */
    val DJ_LIBRARY = Dj.From("library")
    private const val LIBRARY = "your library"

    // ---- starting and stopping ----
    /** The DJ playing from your library (after a restart, say). */
    private fun djReady() {
        if (Dj.scope != null) return
        djLibrary()?.takeIf { it.second.isNotEmpty() }?.let { Dj.playFrom(it.first, it.second, whole = true) }
    }

    /** What the DJ plays from: your library. With Only my songs, only the songs you have yourself (no streams), from
     *  the start: before, a set was made from them all, its line written, and the streams only then left out (djTag),
     *  so the DJ could name a song that wasn't in its set. */
    private fun djLibrary(): Pair<String, List<Track>>? = djSource(DJ_LIBRARY)?.let { (name, songs) ->
        name to if (djNew) songs else songs.filter { it.isFile || lib.has(it.id) } }

    private fun coming() = queue.drop(maxOf(0, index)).map { songKey(it) }.toSet()

    /** Whether a set not yet queued has a song that mustn't come again now: one the DJ played lately, one still to come
     *  in the queue, or one in another set made ahead (apart from itself). Asked each time such a set is about to be
     *  kept or used: it was picked against the queue as it was then (a set queued, a song added, Switch it up, an
     *  ask since). Either of a song's two keys counts, as in the DJ's memory. */
    private fun clashes(set: Dj.Built): Boolean = Dj.anyUsed(set.songs,
        Dj.keysOf(queue.drop(maxOf(0, index))) + Dj.keysOf(djSpares.filter { it.id != set.id }.flatMap { it.songs }))
    /** Sets kept though they clash (a library too small to do better): not asked again. */
    private val djForced = HashSet<Long>()

    /** Start the DJ: a first set of what you've had on repeat (or whatever it can make). from: what it
     *  plays from; Dj.From("all") for everything; null for what it played from last. */
    fun djStart(from: Dj.From? = null) {
        if (Remote.active) { Remote.djStart(from); return }
        if (djBusy != null) return
        if (repeat == "all") { repeat = "off"; saveState() }   // the DJ goes on to its next set (Repeat one still works)
        Dj.withNew = djNew
        // Always your whole library, as one group of songs (whatever it was asked to play from before)
        val src = DJ_LIBRARY
        run {
            val got = djLibrary()
            if (got == null || got.second.isEmpty()) {
                toast("There are no songs in your library for the DJ to play")
                return
            }
            Dj.playFrom(got.first, got.second, whole = true)
        }
        // Made ahead (djWarm): the first set at once, its line made too
        // (and none of its songs played since it was made: a run started by asking, or one begun as it was being made)
        djFirst?.takeIf { it.src == src && it.withNew == djNew && now() - it.at < FIRST_FRESH && !Dj.stale(it.set) &&
                (it.played == Dj.playedTimes || !clashes(it.set)) }?.let { w ->
            djFirst = null
            djBegin(w.set, src)
            return
        }
        djBusy = Dj.scope?.let { "Picking your first set from ${it.first}…" } ?: "Picking your first set…"
        bg({ Dj.build(first = true) }) { res, err ->
            djBusy = null
            if (res == null) {
                toast(if (err == null) "The DJ couldn't find songs yet. Play a few, or connect Last.fm." else "The DJ couldn't start: ${err.message?.take(100)}")
                return@bg
            }
            djBegin(res, src)
        }
    }

    private fun djBegin(res: Dj.Built, src: Dj.From?) {
        Prefs.setText(DJ_USED, (System.currentTimeMillis() / 1000).toString())
        // A new run of the DJ, its sets numbered from one again: what it said in the middle of the last run's sets is
        // forgotten (kept, set 1, 2, 3… of every later run never got a remark: on the phone, where the app lives for
        // days, the early sets of each session went silent)
        djCommented.clear(); djComments.clear()
        dj = Dj.State(from = src)
        playFrom(djTag(res), 0, radio = false)
        dj = dj.copy(on = true)
        djAnnounce(current())
        // Its next set behind it at once (its first song loaded before the DJ was on, so it didn't ask): the set made
        // ahead, queued the moment it is there. (Both were picked at the same moment before, neither knowing the
        // other's songs: the second and third sets could share songs, or be of one kind.)
        djBehind = true
        djMakeSpare()
        if (djBehind && (djSpares.isNotEmpty() || !djSpareBusy)) { djBehind = false; djMore() }   // (one ready already, or none being made)
    }
    private var djBehind = false
    private var spareRetries = 0

    fun djStop() {
        if (Remote.active) { Remote.cmd("dj", "do" to "stop"); return }
        dj = dj.copy(on = false)
        djSpares.forEach { Dj.discard(it.id) }   // never played: their kinds and facts keep their turn
        djSpares.clear(); djSpareJob++; djSpareBusy = false; djBehind = false; moreRetries = 0; spareRetries = 0; djForced.clear()
        scope.launch(Dispatchers.Main) { delay(3000); runCatching { djWarm() } }   // ready for next time
        saveState()
    }

    /** The set's songs marked as set n of the DJ's (n: the next number), with its line. */
    private fun djTag(made: Dj.Built, after: Track? = null): List<Track> {
        // Made ahead, maybe in another part of the day: its hello is for now. Queued behind a song (after): the thread
        // from that song to its first one, when their tags share one (the same artist, genre or decade).
        val b = Dj.bridged(Dj.asOfNow(made), after)
        val n = (dj.sets.keys.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 0) + 1
        var sets = dj.sets + (n.toString() to Dj.Info(b.kind, b.line, b.fact))
        if (sets.size > 30) sets = sets - sets.keys.minBy { it.toInt() }
        dj = dj.copy(sets = sets)
        val songs = localsOf(b.songs)   // your own copies (Dj.build has found them already: this is quick)
            // Only my songs: never a stream, whatever the set found (Dj.build has kept them out already, see queueable)
            .filter { queueable(it) }
            // (two songs of the set that are one copy of yours, a stream and your file of it: once)
            .distinctBy { it.id }.distinctBy { songKey(it) }
        Dj.played(b.songs + songs)   // none again for the next 300 songs
        Dj.commit(b.id)   // it's played: its kind has had its turn, its fact is told (held till now, see Dj.Hold)
        Dj.queuedAs(b.id, n)
        return songs.map { it.copy(dj = n) }
    }

    // ---- the first set, made ahead ----
    /** The DJ's first set, made ahead while it's off (with its line), so tapping DJ starts it at once. */
    private class Warm(val src: Dj.From?, val withNew: Boolean, val set: Dj.Built, val at: Double, val played: Int)   // played: Dj.playedTimes when it was made
    private var djFirst: Warm? = null
    private var djWarming = false
    private const val FIRST_FRESH = 30 * 60.0   // a first set made ahead is good for half an hour

    /** Make the first set ahead (when the DJ is off and there's none fresh): from where it played from last. */
    /** Whether the first set is made ahead at all: off in the tests but the one about it (made ahead three seconds after
     *  any stop, it put your whole library in place of the songs another test had given the DJ: a mood set then found
     *  none of its songs, and that test failed now and then). */
    @Volatile var warmAhead = true
    /** When the DJ was last started (this device's own): the first set is only made ahead, every ten minutes the app is
     *  open, for someone who uses the DJ. It was made (radio look-ups, the voice's line and all) for everyone. */
    private const val DJ_USED = "sync_dj_used_at"
    private fun usedLately() = (System.currentTimeMillis() / 1000 - (Prefs.text(DJ_USED).toLongOrNull() ?: 0L)) < 21 * 86_400L

    fun djWarm() {
        if (!warmAhead || !usedLately()) return
        if (dj.on || Remote.active || djBusy != null || djWarming) return
        if (djFirst?.let { now() - it.at < FIRST_FRESH - 120 && it.withNew == djNew } == true) return
        val src = DJ_LIBRARY
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
            djFirst = Warm(src, withNew, res, now(), Dj.playedTimes)
            djPrepareLine(res)
        }
    }

    // ---- the next set, and switching ----
    /** You skipped (or a song ended) at the end of the DJ's queue while its next set was still being picked: it
     *  goes on to that set as soon as it's ready (never to ∞'s radio, which isn't the DJ's). */
    private var djWaiting = false

    /** The DJ's next set after the queue: as a set begins that is the last one queued (so it's there long before you
     *  could skip to it), and when you skip past the end. A set made ahead is queued at once; you waiting at the end
     *  (djWaiting) take one even while another set is being picked, and even one made in another part of the day (its
     *  hello is made for now), rather than wait half a minute with the old song playing on. */
    private fun djMore() {
        if (djMoreBusy && !djWaiting) return
        djReady()
        // A set made ahead: queued at once (its line is made already)
        if (dj.on) djSpare(urgent = djWaiting)?.let { spare ->
            djSpares.remove(spare)
            val wait = djWaiting; djWaiting = false
            if (wait && djBusy == WAITING) djBusy = null
            val songs = djTag(spare, queue.lastOrNull())
            queue = queue + songs
            afterQueueChange()
            djPrepare(songs)   // its line as it'll be said now (a thread from the song before may have been added)
            djFillSpares()
            if (wait && songs.isNotEmpty() && index + 1 < queue.size) { index++; loadCurrent() }
            return
        }
        // You're waiting and none is ready: the set being picked, or the next one made ahead, goes on the moment it's
        // there (and the DJ's screen says it's picking)
        if (djWaiting && djBusy == null) djBusy = WAITING
        if (djMoreBusy) return
        // (picked quietly in the background: Switch it up and asking stay free meanwhile)
        djMoreBusy = true
        val ctxNow = context
        val avoid = djKind()
        val coming = coming() + djSpares.flatMap { b -> b.songs.map { songKey(it) } }
        bg({ Dj.build(avoid = avoid, coming = coming) }) { res, _ ->
            djMoreBusy = false
            if (djBusy == WAITING) djBusy = null
            val wait = djWaiting; djWaiting = false
            // Picked while another set was queued or made ahead (a skip to the next song as the DJ began): one that shares
            // songs with them isn't queued; picked again, twice at most
            if (res != null && ctxNow == context && dj.on && moreRetries < 2 && clashes(res)) {
                moreRetries++; Dj.discard(res.id); djWaiting = wait; djMore(); return@bg
            }
            moreRetries = 0
            if (res != null && ctxNow == context && dj.on) {
                val songs = djTag(res, queue.lastOrNull())
                queue = queue + songs
                afterQueueChange()
                djPrepare(songs)
                djFillSpares()
                if (wait && songs.isNotEmpty() && index + 1 < queue.size) { index++; loadCurrent() }   // on to it: the DJ introduces it
            } else if (wait && dj.on && djRetried < 2) {
                // Nothing came (a slow lookup, no network for a moment): try again rather than stop
                djRetried++
                djWaiting = true
                scope.launch(Dispatchers.Main) { delay(3000); if (djWaiting && dj.on) djMore() }
            } else if (wait) { djRetried = 0; doneAdvancing(); player?.pause(); toast("The DJ couldn't pick its next set. Tap Play to try again.") }
            if (res != null && !(ctxNow == context && dj.on)) Dj.discard(res.id)   // made, but not queued after all
            if (res != null) djRetried = 0
        }
    }
    private var djRetried = 0
    private var moreRetries = 0
    private var djMoreBusy = false   // the next set being picked for the queue
    private const val WAITING = "Picking the next set…"

    private fun djKind() = dj.sets[current()?.dj.toString()]?.kind

    /** Now playing's ⏭ set button: on to the DJ's next set now (the first song of the next set queued; with none queued
     *  yet, a new one), skipping the rest of this one. The DJ introduces it as usual. */
    fun djNextSet() {
        if (Remote.active) { Remote.cmd("dj", "do" to "switch"); return }
        val now = current()?.dj ?: 0
        val at = (index + 1 until queue.size).firstOrNull { queue[it].dj > 0 && queue[it].dj != now }
        if (at == null) { djSwitch(); return }
        djHush()
        index = at
        loadCurrent()
    }

    /** Switch it up (or play what you asked for): a new set now, in place of the rest of this one. */
    fun djSwitch(ask: String? = null) {
        if (Remote.active) { if (ask != null) Remote.cmd("dj", "do" to "ask", "text" to ask) else Remote.cmd("dj", "do" to "switch"); return }
        if (djBusy != null) return
        if (!dj.on && ask == null) { djStart(); return }
        djReady()
        // Switch it up: the set made ahead, at once (its line made ahead too), when there is one
        if (ask == null) djSpare()?.let { spare ->
            djSpares.remove(spare)
            djWaiting = false
            djSwitchTo(spare)   // and the spares are made again (none of them repeats it)
            return
        }
        djBusy = if (ask != null) "Looking for ${ask.trim()}…" else "Switching it up…"
        val avoid = djKind()
        val coming = coming()
        bg({ if (ask != null) Dj.ask(ask) else Dj.build(avoid = avoid, coming = coming) }) { res, err ->
            djBusy = null
            djWaiting = false
            if (res == null) {
                toast(if (ask != null) "The DJ found nothing for “${ask.trim()}”. Try an artist, a genre, a decade or a vibe."
                      else "The DJ has nothing else right now" + (err?.let { ": ${it.message?.take(80)}" } ?: "."))
                return@bg
            }
            djSwitchTo(res)
        }
    }

    /** A new set now, in place of the rest of this one. */
    private fun djSwitchTo(res: Dj.Built) {
        if (!dj.on) dj = Dj.State(on = true, from = dj.from)
        val queued = queue.drop(index + 1).filter { it.queued }
        val keep = if (current() != null) queue.take(index + 1) else emptyList()
        // (never the song playing or one you queued yourself, a second time: an ask can find those)
        val stays = Dj.keysOf(listOfNotNull(current()) + queued)
        val songs = djTag(res).filter { t -> TrackKey.of(t).let { it.song !in stays && it.stats !in stays } }
        djFirst?.let { Dj.discard(it.set.id); djFirst = null }   // a first set made ahead is from before this run
        queue = keep + queued + songs
        index = keep.size + queued.size
        radioFrom = null
        context++
        loadCurrent()
        djFillSpares()   // one more made ahead in place of this one (the others stay ready)
    }

    // ---- the sets made ahead ----
    /** Sets made ahead for Switch it up and the set button (each with its line made too), so switching is instant
     *  however fast you go: [SPARES] are kept ready, made one after another in the background and made again after
     *  each new set, so they never repeat what's coming. */
    private val djSpares = ArrayList<Dj.Built>()

    /** A set kept ready, to play now: of another kind than the one playing, if there is one. A set of what you play at
     *  this time of day that was made in another part of the day is let go first (it's no longer this time of day),
     *  unless you're waiting for a set now (urgent) and it's all there is: then it plays, its hello made for now. */
    private fun djSpare(urgent: Boolean = false): Dj.Built? {
        val fresh = djSpares.filter { !Dj.stale(it) }
        if (urgent && fresh.isEmpty()) return djSpares.firstOrNull()
        djSpares.filter { Dj.stale(it) }.forEach { Dj.discard(it.id); djSpares.remove(it) }
        // One with a song played or queued since it was made (you asked for something, added a song, switched) is let
        // go too: used, it repeated that song
        val clean = fresh.filter { it.id in djForced || !clashes(it) }
        (fresh - clean.toSet()).forEach { Dj.discard(it.id); djSpares.remove(it) }
        return clean.firstOrNull { it.kind != djKind() } ?: clean.firstOrNull()
    }
    private var djSpareJob = 0
    private var djSpareBusy = false
    private const val SPARES = 3

    private fun djMakeSpare() {
        spareRetries = 0; djForced.clear()
        if (!dj.on || Remote.active) return
        djSpares.forEach { Dj.discard(it.id) }
        djSpares.clear()
        djSpareJob++
        djSpareBusy = false
        djFillSpares()
    }

    /** One more set ahead, until there are [SPARES]. */
    private fun djFillSpares() {
        if (!dj.on || Remote.active || djSpareBusy || djSpares.size >= SPARES) return
        djSpareBusy = true
        val mine = djSpareJob
        // Not what's coming, nor what the other spares have; a kind other than the last one's
        val avoid = djSpares.lastOrNull()?.kind ?: djKind()
        val coming = coming() + djSpares.flatMap { b -> b.songs.map { songKey(it) } }
        bg({ Dj.build(avoid = avoid, coming = coming) }) { res, _ ->
            if (mine != djSpareJob) { res?.let { Dj.discard(it.id) }; return@bg }   // started again meanwhile
            djSpareBusy = false
            if (res == null || !dj.on) { res?.let { Dj.discard(it.id) }; if (djBehind && dj.on) { djBehind = false; djMore() }; return@bg }
            // What's coming changed while it was being picked (Switch it up, a set queued meanwhile) and it has some of
            // the same songs: not kept, and picked again against what's coming now (a few times at most). Kept, the set
            // after next repeated songs of the next one.
            if (clashes(res)) {
                if (spareRetries < 3) { spareRetries++; Dj.discard(res.id); djFillSpares(); return@bg }
                djForced += res.id   // (a library too small to do better: kept as it is)
            }
            spareRetries = 0
            djSpares += res
            djPrepareLine(res)
            if (djWaiting || djBehind) { djBehind = false; djMore() }   // you skipped past the end with nothing ready (or the DJ just began): this one, now
            djFillSpares()
        }
    }

    /** A set's line, made now (the same words it'll say). */
    private fun djPrepareLine(res: Dj.Built) {
        if (!djVoice) return
        val line = djLines.getOrPut(res.line + "\n" + res.fact) {
            Spoken.humanize(res.line) + if (res.fact.isNotEmpty()) " " + Spoken.humanize(res.fact, filler = 0.0) else "" }
        Voice.prepare(djClean(line))
        Dj.voiced(res.id)
    }

    // ---- a set begins, and a word between songs ----
    /** A set begins: its line, where you see it. */
    private fun djAnnounce(t: Track?) {
        val n = t?.dj?.toString() ?: return
        val set = dj.sets[n]
        if (!dj.on || set == null) return
        if (n == dj.shown) {   // a song in the middle of the set: now and then a word about it, once a set at most
            val c = djComments.remove(t.id)
            if (!c.isNullOrEmpty() && djCommented.add(n)) { djSay(c); Dj.commentSaid(t) }
            djCommentAhead()
            return
        }
        dj = dj.copy(shown = n)
        Dj.announced(t.dj ?: 0)
        // (the line used to pop up at the bottom of the screen as well: taken out, it is heard, and it is on the DJ's screen)
        djCommentAhead()
        // Said the way a person talks (numbers in words, no symbols, contractions): Spoken, whatever the voice
        djSay(djLine(n) ?: return)
    }

    // What the DJ says before a song in the middle of a set (a milestone this play reaches, now and then something
    // about the song), worked out and its voice made while the song before it plays. "" for nothing.
    private val djComments = HashMap<String, String>()
    private val djCommented = HashSet<String>()   // the sets (their numbers) the DJ already spoke in the middle of

    private fun djCommentAhead() {
        if (!djVoice || !Voice.canSpeak || Remote.active) return
        val cur = current() ?: return
        val nxt = queue.getOrNull(index + 1) ?: return
        val n = nxt.dj.toString()
        if (nxt.dj <= 0 || nxt.dj != cur.dj || n in djCommented || nxt.id in djComments) return
        if (djComments.size > 50) djComments.clear()
        val c = Dj.songComment(nxt)?.let { Spoken.humanize(it, filler = 0.0) }.orEmpty()   // made once, so what's made ahead is what's said
        djComments[nxt.id] = c
        if (c.isNotEmpty()) Voice.prepare(djClean(c))
    }

    // ---- its voice ----
    /** The DJ talks (Settings in the DJ: Voice): it says its line, the song waiting until it's done. With your
     *  ElevenLabs key through ElevenLabs; else on the PC Kokoro (once fetched) or Windows' voice, on the phone Android's. */
    val djVoice get() = Prefs.text("dj_voice") != "off"
    fun chooseDjVoice(on: Boolean) { Prefs.setText("dj_voice", if (on) "on" else "off"); if (!on) djHush() }
    var djTalking by mutableStateOf(false)
        private set

    /** The DJ says text over the music, like a radio DJ: the song never waits or starts again; it's turned down (ducked)
     *  while she talks and comes back up when she's done. Bit-perfect to a USB DAC, her voice is mixed into the stream with
     *  the music turned down under it (see UsbDirect.speak). A line whose voice isn't ready soon is left unsaid. */
    private fun djSay(text: String) {
        if (!djVoice || !Voice.canSpeak || Remote.active || text.isBlank()) return
        if (player == null) return
        val mine = ++djTurn
        step("the DJ has a line (${text.length} letters)")
        lastDjLine = text
        djSaidAt = now()
        var started = false
        Voice.say(djClean(text), onStart = {
            if (djTurn == mine) { started = true; djTalking = true; djSaidAt = now(); step("the DJ's voice began"); djDuck(true) }
        }) { djDone(mine) }
        // A voice that hasn't begun within a while (being made, a slow network) is let go; one that hasn't finished
        // long after it should have is cut short. The music never waits either way.
        scope.launch(Dispatchers.Main) { delay(15_000L); if (djTurn == mine && !started) Voice.hush() }
        val most = 25_000L + text.length * 90L
        scope.launch(Dispatchers.Main) { delay(most); if (djTurn == mine && djTalking) { Voice.hush(); djDone(mine) } }
    }

    /** How loud the music is under the DJ's voice (of its usual). */
    private const val DUCK = 0.25f
    private var ducked = false
    private var duckJob: kotlinx.coroutines.Job? = null

    /** The music turned down under the DJ (on) or back up (off), over a moment. Bit-perfect, the DAC's stream does it
     *  itself as it mixes her voice in. */
    private fun djDuck(on: Boolean) {
        val p = player ?: return
        if (on == ducked) return
        ducked = on
        SoundLog.note(if (on) "the DJ talks: music down" else "the DJ is done: music up")
        duckJob?.cancel()
        if (bitPerfectNow()) { if (!on) p.volume = 1f; return }
        val from = p.volume; val to = if (on) DUCK else 1f
        duckJob = scope.launch {
            for (i in 1..32) { delay(10); p.volume = from + (to - from) * i / 32 }   // 320 ms, in steps too small to hear
        }
    }

    private fun djClean(text: String) = text.replace("“", "").replace("”", "")

    /** What the DJ says when set n begins (its line, then its fact). */
    private fun djLine(n: String): String? = dj.sets[n]?.let { set ->
        djLines.getOrPut(set.line + "\n" + set.fact) {   // made once (its fillers are picked at random), so the line made ahead is the one said
            if (djLines.size > 20) djLines.keys.firstOrNull()?.let { djLines.remove(it) }
            Spoken.humanize(set.line) + if (set.fact.isNotEmpty()) " " + Spoken.humanize(set.fact, filler = 0.0) else "" } }
    private val djLines = LinkedHashMap<String, String>()

    /** The next set's line made ahead (while this set plays), so the DJ says it the moment the set begins. */
    private fun djPrepare(songs: List<Track>) {
        if (!djVoice || Remote.active) return
        val n = songs.firstOrNull()?.dj?.toString() ?: return
        djLine(n)?.let { Voice.prepare(djClean(it)) }
    }

    private var djTurn = 0L          // each line the DJ says (a line that's done late, after a newer one began, is ignored)
    private var lastDjLine = ""
    private var djSaidAt = 0.0

    /** Done talking (line mine): the set's first song from its very start (if it's still loading, it starts by itself when ready). */
    private fun djDone(mine: Long) {
        if (mine != djTurn || !djTalking) return
        djTalking = false
        djDuck(false)   // the music back up, where it is
    }

    /** The DJ stops talking (Play, Pause, a skip): the music goes on. */
    fun djHush() {
        if (!djTalking) return
        djTalking = false
        Voice.hush()
        djDuck(false)
    }

    // ---- as playback sees it ----
    /** The DJ, as the playback service ([PlaybackCore]) sees it: all it knows of the DJ. */
    val side: PlaybackCore.DjSide = object : PlaybackCore.DjSide {
        override val on: Boolean get() = dj.on
        override val talking: Boolean get() = djTalking
        override val busy: String? get() = djBusy
        override val musicDucked: Boolean get() = ducked
        override val duckVolume: Float get() = DUCK
        override fun talksBefore(t: Track, next: Track) = dj.on && djVoice && ((next.dj > 0 && next.dj != t.dj) || !djComments[next.id].isNullOrEmpty())
        override fun otherSongsPicked() { dj = dj.copy(on = false) }
        override fun hush() = djHush()
        // The next set queued as soon as the last one queued begins (not two songs before the end: skipping through a
        // set got there before it was picked, and the old song played on until it was)
        override fun songLoading(t: Track) {
            // A song of your own played in the DJ's run (queued by you): remembered as the DJ's are, so its next sets
            // don't bring it straight back
            if (dj.on && t.dj == 0) runCatching { Dj.played(listOf(t)) }
            djAnnounce(t)
            if (index >= queue.size - 2 || (t.dj > 0 && queue.lastOrNull()?.dj == t.dj)) djMore()
        }
        override fun queueRanOut() { djWaiting = true; djMore() }
        override fun songEnded() = djKeepGoing()
        override fun loopRemark(t: Track, kind: String, run: Int, total: Int) {
            if (dj.on) djSay(Spoken.humanize(Dj.loopLine(kind, t, run, total), filler = 0.0))
        }
    }

    /** Play (the app's own, the headphones, the car, the lock screen, the notification), the DJ's part of it, so the DJ
     *  is never left stuck: talking that never came, a set it was still picking, a set it hadn't introduced yet.
     *  True: the DJ saw to it; false: Play does what it always does. */
    fun djPlay(p: Engine): Boolean {
        if (djTalking) djHush()   // she stops; the music (never stopped for her) carries on
        if (dj.on && current() != null && p.state == Engine.ENDED && index + 1 >= queue.size) {
            djWaiting = true; advancing = true; djMore(); return true
        }
        // A new set that never got its line (the skip landed while it was loading): its line, then the song
        current()?.dj?.takeIf { it > 0 && dj.on && it.toString() != dj.shown && dj.sets[it.toString()] != null }?.let {
            if (p.state == Engine.IDLE || !p.hasItem) loadCurrent() else { djAnnounce(current()); p.play() }
            return true
        }
        return false
    }

    /** The DJ never just stops after a song: if, a few seconds after one ran out, the player is still at an end (the
     *  next set being picked never came, a stream couldn't be got back, the next song never started), with no line
     *  being said and no set still being picked, on it goes: the next song (a new set at the end of the queue), or the
     *  song it moved to, loaded again. A pause mid-song is yours, and is left alone. */
    private fun djKeepGoing() {
        val at = index
        scope.launch(Dispatchers.Main) {
            for (i in 0 until 8) {
                delay(5000)
                val p = player ?: return@launch
                if (!dj.on || Remote.active || cast != null) return@launch
                if (djTalking || djBusy != null) continue   // a line being said, or the next set being picked: more time
                if (index != at && loadedFor != current()?.id && i < 7) continue   // the next song still being got (a slow stream)
                if (index == at && streaming && i < 2) continue   // a stream that broke being got back
                if (!(p.state == Engine.ENDED || p.state == Engine.IDLE || !p.hasItem)) return@launch   // playing, or paused by you
                step("the DJ's watchdog went on (state ${p.state}, hasItem ${p.hasItem})")
                SoundLog.note("DJ: the music had stopped after a song (${if (index == at) "still on it" else "the next one hadn't started"}); went on")
                if (index == at) next() else loadCurrent()
                return@launch
            }
        }
    }
}
