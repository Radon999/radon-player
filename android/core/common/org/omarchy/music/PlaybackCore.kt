package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The queue and what's playing: the playback service (docs/rebuild/RADON_REBUILD_PLAN.md, Phase 9), taken out of
 *  [Music] piece by piece with nothing behaving differently. It owns the queue's own state; [Music] keeps every name
 *  the rest of the app uses and hands on to here. The rules themselves (what comes after a song, what counts as a
 *  play, when a song fades into the next) are in the shared code, each tested alone: [QueueRules], [PlayRules],
 *  [EndWatch], [CrossfadeRules].
 *
 *  What it still asks of [Music]: the app's own things (your music, the services, the settings file, notes to you).
 *  Of the DJ it knows only [DjSide].
 *
 *  Everything here runs on the app's main thread, as [Music] does. */
object PlaybackCore {
    // ---- the queue's own state (what the screens show: Compose state, so a change redraws) ----
    var queue by mutableStateOf(listOf<Track>())
    var index by mutableStateOf(-1)
    // Multiple queues: playing from another list (a folder, an album, an artist, a playlist…) starts a queue
    // of its own; the one you were in is kept, where you were in it, to go back to.
    var queues by mutableStateOf(listOf<SavedQueue>())   // the other queues, the last one used first
    var queueName by mutableStateOf("")
    var queueId = ""
    var multiQueue by mutableStateOf(true)
    var listName = ""   // where songs are picked from now (the screen's name): a new queue is called that
    var repeat by mutableStateOf("off")
    var shuffled = false           // the queue was shuffled: repeat-all shuffles it again
    var radioFrom: String? = null  // the song the queue's radio (∞) grew from
    var context = 0                // goes up when the queue becomes another one: what was being got for the old one is let go
    var ended = false              // the queue ran out (nothing came after its last song)

    fun current(): Track? = queue.getOrNull(index)
    fun isRadio() = radioFrom != null

    // ---- the DJ, as the queue sees it ----
    /** The DJ's side of playback: all the core knows of the DJ ([Music] has the DJ's own part and answers these). */
    interface DjSide {
        /** The DJ is playing its sets. */
        val on: Boolean
        /** It's saying a line now. */
        val talking: Boolean
        /** It's picking a set: what it's doing, as the DJ screen says it (null: it isn't). */
        val busy: String?
        /** The music is turned down under its voice, and how far (of its usual). */
        val musicDucked: Boolean
        val duckVolume: Float
        /** It will talk as next begins (a new set's line, or a word about the song): t doesn't fade into it. */
        fun talksBefore(t: Track, next: Track): Boolean
        /** Other songs were picked, or another queue: the DJ stops. */
        fun otherSongsPicked()
        /** It stops talking (Play, Pause, a skip): the music goes on. */
        fun hush()
        /** A song of the queue is being loaded: a new set's line, a word about the song, the next set asked for. */
        fun songLoading(t: Track)
        /** The DJ's queue has no song after this one: on to its next set, as soon as there is one. */
        fun queueRanOut()
        /** A song ran out by itself: the DJ sees to it that the music goes on. */
        fun songEnded()
    }
    private object NoDj : DjSide {
        override val on: Boolean get() = false
        override val talking: Boolean get() = false
        override val busy: String? get() = null
        override val musicDucked: Boolean get() = false
        override val duckVolume: Float get() = 1f
        override fun talksBefore(t: Track, next: Track) = false
        override fun otherSongsPicked() {}
        override fun hush() {}
        override fun songLoading(t: Track) {}
        override fun queueRanOut() {}
        override fun songEnded() {}
    }
    var djSide: DjSide = NoDj

    // ---- starting and switching queues ----
    /** Play tracks[i], then the rest of tracks; the songs you queued come first. */
    fun playFrom(tracks: List<Track>, i: Int, radio: Boolean) {
        // SoundCloud is a service of its own: its songs play in a queue of SoundCloud songs only, and other songs never
        // share a queue with them (SoundCloud's rules)
        val tapped = tracks.getOrNull(i)
        if (tapped != null && tracks.any { (it.source == SoundCloud.KEY) != (tapped.source == SoundCloud.KEY) }) {
            val apart = tracks.filter { (it.source == SoundCloud.KEY) == (tapped.source == SoundCloud.KEY) }
            playFrom(apart, apart.indexOf(tapped).coerceAtLeast(0), radio); return
        }
        if (Remote.active) { Remote.playFrom(tracks, i, radio); return }
        ListenLog.picked(tracks.getOrNull(i), listName)   // Wrapped's Most searched: a song tapped in search results
        val newQueue = multiQueue && listName.isNotEmpty() && listName != queueName
        if (multiQueue) startQueue(listName)
        val queued = if (newQueue) emptyList() else queue.drop(index + 1).filter { it.queued }
        val mine = Music.localsOf(tracks)   // a service's song you have yourself plays from your file or download
        queue = mine.take(i + 1) + queued + mine.drop(i + 1)
        index = i
        radioFrom = null
        shuffled = false
        if (djSide.on) djSide.otherSongsPicked()   // you picked other songs: the DJ stops
        context++
        loadCurrent()
        if (radio) extendRadio(tracks[i], keepUntil = i)   // the service's song: its own radio
    }

    /** Songs are played from the list called name: if it isn't the queue you're in, that one is kept to come back to. */
    private fun startQueue(name0: String) {
        val name = name0.ifEmpty { queueName.ifEmpty { "Queue" } }
        if (name == queueName && queueId.isNotEmpty()) return
        if (queue.isNotEmpty() && index >= 0 && queueName.isNotEmpty()) {
            val keep = SavedQueue(queueId.ifEmpty { randomHex(6) }, queueName, queue.map { it.copy(queued = false) }, index, Music.livePosition(), now())
            queues = QueueRules.putAside(queues, keep, MAX_QUEUES)
        }
        queueId = queues.firstOrNull { it.name == name }?.id ?: randomHex(6)
        queues = queues.filter { it.name != name }
        queueName = name
    }

    /** Go back to one of your other queues, where you left it. */
    fun switchQueue(id: String) {
        val q = queues.firstOrNull { it.id == id } ?: return
        if (Remote.active) { Remote.playFrom(q.tracks, q.index.coerceIn(0, maxOf(0, q.tracks.size - 1)), false); return }
        if (queue.isNotEmpty() && index >= 0) {
            val keep = SavedQueue(queueId.ifEmpty { randomHex(6) }, queueName.ifEmpty { "Queue" }, queue.map { it.copy(queued = false) }, index, Music.livePosition(), now())
            // The queue you leave takes the place of the one you go to: the order you put them in stays.
            queues = QueueRules.swap(queues, keep, id)
        } else queues = queues.filter { it.id != id }
        queue = q.tracks
        index = q.index.coerceIn(0, maxOf(0, q.tracks.size - 1))
        queueName = q.name
        queueId = q.id
        radioFrom = null
        shuffled = false
        if (djSide.on) djSide.otherSongsPicked()
        context++
        resumeAt = q.position
        loadCurrent()
        Music.toast("Back to “${q.name}”")
    }

    /** Move one of your other queues up (-1) or down (+1). */
    fun moveQueue(id: String, step: Int) {
        val l = queues.toMutableList()
        val k = l.indexOfFirst { it.id == id }
        if (k < 0 || k + step !in l.indices) return
        l[k] = l[k + step].also { l[k + step] = l[k] }
        queues = l
        Music.saveState()
    }

    fun renameQueue(id: String, name: String) {
        val n = name.trim().ifEmpty { return }
        if (id == queueId || id.isEmpty()) queueName = n else queues = queues.map { if (it.id == id) it.copy(name = n) else it }
        Music.saveState()
    }

    fun deleteQueue(id: String) { queues = queues.filter { it.id != id }; Music.saveState() }
    fun removeOtherQueues() { queues = emptyList(); Music.saveState(); Music.toast("Only this queue is left") }
    fun chooseMultiQueue(on: Boolean) { multiQueue = on; Music.saveState() }

    /** The next of your other queues with songs still to play (the last one used first), or null. */
    fun nextQueue(): SavedQueue? = if (!multiQueue) null else QueueRules.next(queues, queueId)

    // ---- what's in the queue ----
    /** "next" plays the songs right after this one, "queue" after the songs you queued before. */
    fun enqueue(tracks: List<Track>, how: String = "queue") {
        val cur = current()
        if (cur != null && tracks.any { (it.source == SoundCloud.KEY) != (cur.source == SoundCloud.KEY) }) {
            Music.toast(if (cur.source == SoundCloud.KEY) "A SoundCloud queue holds SoundCloud songs only: play this to start a queue of its own"
                else "SoundCloud songs play in a queue of their own: play one to start it")
            val fit = tracks.filter { (it.source == SoundCloud.KEY) == (cur.source == SoundCloud.KEY) }
            if (fit.isEmpty()) return
            enqueue(fit, how); return
        }
        if (Remote.active) { Remote.enqueue(tracks, how); return }
        val ts = Music.localsOf(tracks).map { it.copy(queued = true) }   // your own copies of the songs you have
        if (ts.isEmpty()) return
        if (current() == null) { playFrom(ts, 0, false); return }
        // Settings › Playback › Move, don't duplicate: a song already waiting further on is moved up, not put in twice
        queue = QueueRules.enqueued(queue, index, ts, next = how == "next", move = Prefs.on(Prefs.QUEUE_MOVE))
        afterQueueChange()
        val many = if (ts.size > 1) "${ts.size} songs" else "“${ts[0].title}”"
        Music.toast(if (ts.size > 1) "$many ${if (how == "next") "play next" else "added to the queue"}"
              else "${if (how == "next") "Playing next" else "Added to the queue"}: $many")
        if (ended) next()
    }

    fun unqueueMany(indices: Collection<Int>) {
        if (Remote.active) { Remote.cmd("unqueue", "indices" to indices.toList()); return }
        val drop = indices.filter { it in queue.indices && it != index }.toSet()
        val before = drop.count { it < index }
        queue = queue.filterIndexed { i, _ -> i !in drop }
        index -= before
        afterQueueChange()
    }

    /** The queue's Clear: the songs you queued by hand go (what plays on from the list stays). */
    fun clearQueued() {
        if (Remote.active) { Music.toast("Clear the queue on the desktop"); return }
        queue = queue.take(index + 1) + queue.drop(index + 1).filter { !it.queued }
        afterQueueChange()
    }

    /** The song at i, dragged to j (both after the one playing). */
    fun moveInQueue(i: Int, j: Int) {
        if (Remote.active || i == j || i !in queue.indices || j !in queue.indices || i <= index || j <= index) return
        val q = queue.toMutableList()
        val t = q.removeAt(i)
        q.add(j, t)
        queue = q
        afterQueueChange()
    }

    fun playIndex(i: Int) {
        if (Remote.active) { Remote.cmd("play_index", "index" to i); return }
        if (i in queue.indices) { index = i; loadCurrent() }
    }

    fun cycleRepeat() {
        if (Remote.active) { Remote.cmd("repeat"); return }
        // With the DJ: just Repeat one (a song you want again before the DJ goes on) or off; Repeat all means nothing
        // to the DJ, whose sets go on and on
        repeat = if (djSide.on) (if (repeat == "one") "off" else "one") else mapOf("off" to "all", "all" to "one", "one" to "off")[repeat]!!
        Music.saveState()
    }

    /** Shuffle the whole list (the songs already played too): the song playing first, the songs you queued next, in
     *  your order, then all the rest shuffled. (Before, only what was still to come was shuffled: started halfway
     *  down a playlist, the songs above never came.) */
    fun shuffle() {
        if (Remote.active) { Remote.cmd("shuffle"); return }
        if (djSide.on) { Music.toast("The DJ plays each set in its own order, so shuffle isn't used with the DJ"); return }
        if (index !in queue.indices) return
        queue = QueueRules.reshuffled(queue, index) { shuffleSongs(it) }
        index = 0
        shuffled = true
        afterQueueChange()
    }

    // ---- what's playing (what the screens show) ----
    var paused by mutableStateOf(true)
    var position by mutableStateOf(0.0)
    var duration by mutableStateOf(0.0)
    var streaming by mutableStateOf(false)
    var resumeAt = 0.0                       // where the next song loaded starts (a queue taken up again, a cast begun)
    var pendingLoad: (() -> Unit)? = null    // a song got before there was a player: into it once there is one
    private var restorePending = false       // the song from last time has to come from a service: wait for the player
    var loadedFor: String? = null            // the song the player has in now (its id)
    private var lastLoaded: Track? = null
    private var loadSeq = 0
    private var streamRetry = -1
    private var failedInARow = 0             // songs that couldn't be had, one after another (none played between)
    private var finishing = false            // the song playing ran out on its own (not a skip)
    var goingBack = false                    // Previous: going back isn't skipping

    /** A song ran out and the next one is on its way: to the notification (and Android) it's still
     *  playing, so the app stays a foreground music player between songs. Were it "ended" for that
     *  moment, Android would take the app out of the foreground, and with the screen off it can't come
     *  back: a minute later Android closed it (the app "crashing" every couple of songs). */
    @Volatile var advancing = false

    // A crossfade under way (see maybeCrossfade): the song fading out, and how the next one comes in
    private var fadingId: String? = null
    private var fadeIn = 0.0
    private var fadeJob: kotlinx.coroutines.Job? = null
    private var fadingOut: Engine? = null   // a crossfade's ending song, fading out in its own player
    private var crossTo: String? = null     // the song a crossfade is bringing in

    // ---- a song change that took too long, written down (Settings › Problems) ----
    // From a song ending by itself to the next one sounding should take a moment. When it takes more than 2.5 seconds
    // (the DJ on the phone stopped after every song heard to its end: the plan's D16), each step it went through and
    // when (milliseconds from the song's end) is noted, so the step that held it up can be read. The first 12 only.
    private var changeAt = 0L
    private val changeSteps = ArrayList<String>()
    private var changeNotes = 0
    private var changeLate = false
    var endHow = ""      // what said the song was over (the player, the watch, a crossfade)
    var fadeNote = ""    // how the last crossfade went, if it gave up

    /** A step of a song change (its own, the DJ's, the phone's player's). */
    fun step(what: String) {
        if (changeAt == 0L) return
        if (changeSteps.size < 60) changeSteps += "${System.currentTimeMillis() - changeAt} $what"
    }
    private fun changeBegins(how: String) {
        changeAt = System.currentTimeMillis(); changeSteps.clear(); changeLate = false
        val p = Music.player
        step("$how${fadeNote.also { fadeNote = "" }}: dj=${djSide.on} song=${index + 1}/${queue.size} at=${"%.1f".format(position)}/${"%.1f".format(duration)} state=${p?.state} " +
            "pwr=${p?.playWhenReady} advancing=$advancing streaming=$streaming fading=${fadingId != null} talking=${djSide.talking} busy=${djSide.busy ?: "-"} loadedFor=${if (loadedFor == current()?.id) "this" else "other"}")
    }
    fun changeWatch(p: Engine?) {
        if (changeAt == 0L) return
        val took = System.currentTimeMillis() - changeAt
        val there = p != null && p.isPlaying && loadedFor == current()?.id && !advancing
        if (!there && took > 20_000 && !changeLate) { changeLate = true; step("still not playing: state=${p?.state} pwr=${p?.playWhenReady} hasItem=${p?.hasItem} advancing=$advancing paused=$paused") }
        if (!there && took < 120_000) return
        if ((took > 2500 || !there) && changeNotes < 12) {
            changeNotes++
            Crashes.note("A song change took ${"%.1f".format(took / 1000.0)} s${if (!there) " and never played" else ""}: " + changeSteps.joinToString(" | "))
        }
        changeAt = 0L
    }

    // ---- what counts as played ----
    private class Listen(val id: String, val track: Track, var played: Double = 0.0, var counted: Boolean = false,
                         var sent: Boolean = true, val start: Double = now())
    private var listen: Listen? = null

    private fun beginListen(t: Track) {
        listen = Listen(t.id, t, sent = !LastFm.connected)
        ListenLog.begin(t)   // Wrapped: where it's played from, shuffled or not, on which output
        if (LastFm.connected) Music.bg({ LastFm.nowPlaying(t) })
    }

    /** A song counts as played (Recent, Most played) after PLAY_AFTER seconds; Last.fm gets
     *  it once you've listened to half of it (or four minutes). */
    fun listened(seconds: Double) {
        val ls = listen ?: return
        if (ls.sent && ls.counted) return
        ls.played += seconds
        val dur = duration.takeIf { it > 0 } ?: ls.track.duration
        if (!ls.counted && PlayRules.countsAsPlay(ls.played, dur, PLAY_AFTER.toDouble())) {   // a play: half the song heard
            ls.counted = true
            Music.plays.add(ls.track)
            Music.lib.played(ls.id)
            Music.musicVersion++
        }
        if (!ls.sent && PlayRules.scrobbles(ls.played, dur)) {
            ls.sent = true
            val start = ls.start
            Music.bg({ LastFm.scrobble(ls.track, start) })
        }
    }

    /** The song playing is about to change: count it as skipped, unless it ran out on its own or you went back. */
    private fun noteLeaving() {
        val ls = listen
        val fin = finishing; val back = goingBack
        finishing = false; goingBack = false
        if (ls == null || fin || back) return
        val dur = duration.takeIf { it > 0 } ?: ls.track.duration
        if (PlayRules.isSkip(ls.played, dur, position, SongStats.SKIP_LEFT.toDouble())) {
            Music.bg({ Music.songStats.skipped(ls.track) }) { _, _ -> Music.musicVersion++ }
        }
    }

    // ---- the queue's radio (∞) ----
    private fun extendRadio(t: Track, keepUntil: Int? = null) {
        if (djSide.on) return   // the DJ makes its own sets: ∞ never adds to them
        radioFrom = t.id
        val ctxNow = context
        Music.bg({ Music.localsOf(Music.radioTracks(t)) }) { res, err ->   // the radio's songs you have play from your copies
            if (ctxNow != context || err != null || res.isNullOrEmpty()) { if (ended) doneAdvancing(); return@bg }
            var q = queue
            if (keepUntil != null) {
                // A song picked from results: its radio replaces the rest of the results (not your queue).
                val cut = maxOf(keepUntil, index)
                q = q.take(cut + 1) + q.drop(cut + 1).filter { it.queued }
            }
            val ids = q.map { it.id }.toMutableSet()
            val songs = q.map { songKey(it) }.toMutableSet()
            q = q + res.filter { ids.add(it.id) && songs.add(songKey(it)) }
            queue = q
            afterQueueChange()
            if (ended && index + 1 < queue.size) next()   // it had stopped at the end: on it goes
            else if (ended) doneAdvancing()
        }
    }

    /** ∞: a couple of songs before the end, songs like the last one, so there's no gap. */
    fun autoplaySoon() {
        if (Music.autoplay && !djSide.on && repeat == "off" && queue.isNotEmpty() && queue.size - index <= 2 && radioFrom != queue.last().id)
            extendRadio(queue.last())
    }

    // ---- moving on ----
    fun next() {
        if (Remote.active) { Remote.cmd("next"); return }
        djSide.hush()
        val other = nextQueue()
        step("next: ${QueueRules.after(index, queue.size, djSide.on, repeat, other != null)}")
        when (QueueRules.after(index, queue.size, djSide.on, repeat, other != null)) {
            QueueRules.After.NEXT_SONG -> { index++; loadCurrent(); autoplaySoon() }
            // The DJ's queue is done (you skipped to its end, or a set ran out): its next set, not ∞'s radio of streams,
            // round again (Repeat all is the DJ's next set) or your next queue
            QueueRules.After.DJ_SET -> djSide.queueRanOut()
            QueueRules.After.ROUND_AGAIN -> {
                if (shuffled) { queue = shuffleSongs(queue, after = current()); afterQueueChange() }   // round again in a new order
                index = 0; loadCurrent()
            }
            QueueRules.After.NEXT_QUEUE -> switchQueue(other!!.id)   // this queue is done: your next one goes on
            QueueRules.After.END -> {
                ended = true
                val more = Music.autoplay && repeat == "off" && current() != null
                if (more && advancing) extendRadio(current()!!)   // ∞: songs like it, and on it goes (it stays "playing" meanwhile)
                else {
                    doneAdvancing(); Music.player?.pause()
                    if (more) current()?.let { extendRadio(it) }
                }
            }
        }
    }

    fun previous() {
        djSide.hush()
        if (Remote.active) { Remote.cmd("prev"); return }
        if (QueueRules.previousRestarts(position, index)) {   // ⏮: the start of the song; in its first 10 seconds, the song before
            Music.player?.seekTo(0)
            val t = current()
            if (t != null && listen?.let { it.sent || it.counted } == true) beginListen(t)   // listening again counts again
        } else { index--; goingBack = true; loadCurrent() }
    }

    /** Done moving on (playing the next song, or stopped): the notification gets the player's real state. */
    fun doneAdvancing() {
        if (!advancing) return
        advancing = false
        val p = Music.player ?: return
        if (p.state == Engine.ENDED) p.pause()   // nothing came: it stops, and Android hears it
    }

    /** The player says its song ended. Ignored while it's already moving on to another song (after a crossfade the
     *  ending one plays on silently in the player while the next loads, and its end skipped the next song unheard). */
    fun engineEnded() {
        if (advancing && loadedFor != null && loadedFor != current()?.id) { step("the player's ENDED left alone (moving on already)"); return }
        endHow = "the player said ENDED"
        trackEnded()
    }

    fun trackEnded() {
        changeBegins(endHow.ifEmpty { "song ended" }); endHow = ""
        // The song ran out silently under a long line: it waits at its start for the DJ (never skipped)
        advancing = true
        if (djSide.on) djSide.songEnded()
        if (streaming && duration > 0 && position < duration - 5) { step("taken for a stream that broke"); streamBroke(); return }   // the network went away mid-song
        current()?.let { t -> Music.bg({ Music.songStats.heardOut(t) }); Music.discovered(t) }
        finishing = true
        if (repeat == "one") { resumeAt = 0.0; position = 0.0; loadCurrent() } else next()   // Repeat one: from its very start
        if (ended && !(Music.autoplay && repeat == "off")) doneAdvancing()
    }

    fun afterQueueChange() {
        Music.saveState()
        prefetch()
    }

    // ---- loading a song ----
    fun loadCurrent(autoplay: Boolean = true) {
        val c = current() ?: return
        step("load “${c.title.take(30)}” play=$autoplay")
        Music.heldTagsSoon()   // the song that was playing: its tag changes, once its file is free
        // A song you have yourself (a file or a download) plays from your copy, whatever list it came from (a queue
        // kept from before you had it too): never the service's stream of it.
        if (!Remote.active) Copies.heal(c)   // a download whose file was moved (a tag change): your file of it is found again
        val t0 = if (Remote.active) c else Music.localOf(c)
        if (t0 !== c) {
            queue = queue.toMutableList().also { it[index] = t0 }
            if (radioFrom == c.id) radioFrom = t0.id
        }
        val t = t0.copy(queued = false)   // playing now, not waiting in your queue
        if (t0.queued) queue = queue.toMutableList().also { it[index] = t }
        if (t.isFile && !Music.fileHere(t)) { Music.standIn(t, autoplay); return }   // the other device's song file (a backup's)
        if (autoplay) noteLeaving()
        finishing = false; goingBack = false
        restorePending = false
        ended = false
        listen = null
        // The song before this one is done: its lyrics can go into its file now (never into the one playing).
        lastLoaded?.let { prev -> if (prev.id != t.id) Music.lyricsIntoFile(prev) }
        lastLoaded = t
        if (Music.lyricsToFiles && !Remote.active) Music.bg({ Lyrics.get(Music.ctx, t) })   // found while it plays, kept for later
        val seq = ++loadSeq
        val at = resumeAt
        resumeAt = 0.0
        position = at
        duration = t.duration
        val onDisk = Music.lib.has(t.id) || t.isFile
        streaming = !(onDisk || (Music.keepPlayed && Music.autoSaveOk(t)))
        Music.status = if (onDisk) "" else if (Music.keepPlayed) "Downloading FLAC…" else "Loading the stream…"
        val work: () -> Downloader.Ready = if (streaming || onDisk) ({ Music.dl.stream(t) })
            else ({ try { Music.dl.download(t, youtubeToo = false) } catch (_: Exception) {}; Music.dl.stream(t) })   // no FLAC to keep (or saving failed): stream what there is
        Music.bg(work) { ready, err ->
            if (seq != loadSeq) return@bg   // you moved on while it loaded
            step(if (err != null || ready == null) "couldn't get it: ${err?.message?.take(60)}" else "got it${if (Music.player == null) " (no player yet)" else ""}")
            Music.status = ""
            if (err != null || ready == null) {
                failedInARow++
                // Song after song that can't be had (no connection, a service turning the app away): it stops after
                // a few instead of skipping through the whole queue, each one flashing by with its own note
                if (autoplay && PlayRules.givesUp(failedInARow)) {
                    failedInARow = 0
                    Music.toast("${PlayRules.FAILS_BEFORE_STOPPING} songs in a row couldn't be played, so the music has stopped. The last: ${err?.message?.take(120)}")
                    paused = true; doneAdvancing()
                    return@bg
                }
                Music.toast(if (err is NoLosslessMatch) "Skipped “${t.title}”: ${err.message}" else "Couldn't get “${t.title}”: ${err?.message}")
                if (autoplay) Music.scope.launch { delay(1500); if (seq == loadSeq) next() } else { paused = true; doneAdvancing() }
                return@bg
            }
            failedInARow = 0
            if (!ready.onDisk) streaming = true   // Keep found no FLAC to save: it streams
            val start = { play(t, ready, autoplay, at) }
            if (Music.player == null) pendingLoad = start else start()
            if (autoplay) beginListen(t)
            Music.savedVersion++
            prefetch()
        }
        Music.saveState()
        // Keep a radio going: near the end of the queue, ask for more like the last song.
        if (radioFrom != null && !djSide.on && index >= queue.size - 2) extendRadio(queue.last())
        if (djSide.on) {
            step("the DJ's part begins")
            djSide.songLoading(t)
            step("the DJ's part done")
        }
        Music.waitingCoversSoon(t)   // covers picked while their song played: in now, once the file that was playing is free
    }

    // ---- remembered between runs ----
    /** The queue's part of what the app remembers between runs (state.json: [Music] reads and writes the file, where
     *  it sits among the settings, under the names it always had). */
    class Saved(val queue: List<Track>, val index: Int, val radioFrom: String?, val repeat: String, val shuffled: Boolean,
                val queues: List<SavedQueue>, val queueName: String, val queueId: String, val multiQueue: Boolean)

    /** As it is now, to be written down. */
    fun saved() = Saved(queue, index, radioFrom, repeat, shuffled, queues, queueName, queueId, multiQueue)

    /** What was written down, back in place as the app starts (nothing goes into the player yet: [restored]). */
    fun restore(s: Saved) {
        queue = s.queue
        index = s.index.coerceAtMost(queue.size - 1)
        radioFrom = s.radioFrom
        repeat = s.repeat.takeIf { it in setOf("off", "all", "one") } ?: "off"
        shuffled = s.shuffled
        queues = s.queues
        queueName = s.queueName
        queueId = s.queueId
        multiQueue = s.multiQueue
    }

    /** The rest of what the app remembers is back too: the song from last time goes into the player, not playing. */
    fun restored() {
        val t = current()
        if (t != null && (Music.lib.has(t.id) || t.isFile)) loadCurrent(autoplay = false)
        else if (t != null) restorePending = true   // it has to come from Tidal: wait for the player and the login
    }

    /** Called once the playback service is up: a song restored from Tidal can load now. */
    fun playerReady() {
        if (restorePending) {
            restorePending = false
            loadCurrent(autoplay = false)
        }
    }

    private fun play(t: Track, ready: Downloader.Ready, autoplay: Boolean, startAt: Double = 0.0) {
        val p = Music.player ?: return
        val art = if (Music.cast == null) Music.nowArt(t) else t.thumb.takeIf { it.isNotEmpty() }?.let { sizedThumb(it, 544) }
        // Casting: the device fetches the song itself: a stream as it is, a file of the phone's from here over the network.
        val uri = if (Music.cast != null && !ready.uri.startsWith("http")) PhoneShare.forCast(t, Music.castHost) ?: ready.uri else ready.uri
        val item = PlayItem(uri, t.id, if (ready.isDash) "application/dash+xml" else if (t.source == SoundCloud.KEY) "application/x-mpegURL"
            else if (Music.cast != null) Music.castMime(t, uri) else null,
            t.title, t.artist, t.album, art)
        step("into the player (at ${startAt.toInt()} s, play=$autoplay, state before ${p.state})")
        p.load(item, (startAt * 1000).toLong())
        loadedFor = t.id
        Music.playbackOf(t).let { (sp, pi) -> p.setSpeed(sp, pi) }
        Music.eqProcessor.songGain = Music.gainOf(t)
        Music.applyEq()   // the song's own EQ on this output, or the usual one
        // Never held for the DJ: she talks over the song, turned down under her (see DjDirector.djSay)
        p.playWhenReady = autoplay
        advancing = false   // buffering for real now
        fadeJob?.cancel()
        if (djSide.musicDucked && !Music.bitPerfectNow()) { fadeIn = 0.0; p.volume = djSide.duckVolume }
        else if (fadeIn < 0 && t.id == crossTo) { fadeIn = 0.0; p.volume = 0f }   // a crossfade brings it up (see Music.maybeCrossfade)
        else if (fadeIn < 0) { fadeIn = 0.0; p.volume = 1f }   // another song than the one it was fading to (a skip)
        else if (fadeIn > 0) {   // crossfading: this one comes up as the last one fades away
            val n = fadeIn; fadeIn = 0.0
            p.volume = 0f
            fadeJob = Music.scope.launch {
                val steps = (n * 20).toInt().coerceAtLeast(1)
                for (i in 1..steps) { delay(50); p.volume = kotlin.math.sin(Math.PI / 2 * i / steps).toFloat() }   // …the next comes up
                p.volume = 1f
            }
        } else p.volume = 1f
    }

    /** The next two songs' streams looked up while this one plays, so they start straight away. */
    private fun prefetch() {
        for (k in 1..2) {
            val nxt = queue.getOrNull(index + k) ?: return
            if (Music.lib.has(nxt.id) || nxt.isFile) continue
            if (Music.keepPlayed && Music.autoSaveOk(nxt)) Music.bg({ Music.dl.download(nxt, youtubeToo = false) }) { _, _ -> Music.savedVersion++ } else Music.bg({ Music.dl.stream(nxt) })
        }
    }

    /** Tidal's stream stopped short: look it up again and carry on from the same place, once per song. */
    fun streamBroke() {
        val t = current() ?: return
        if (!streaming) return
        val seq = loadSeq
        val pos = position
        val wasPlaying = Music.player?.playWhenReady == true
        if (streamRetry == seq) {
            Music.toast("Lost the stream of “${t.title}”")
            if (wasPlaying) next()
            return
        }
        streamRetry = seq
        Music.dl.forgetStream(t.id)
        Music.bg({ Music.dl.stream(t) }) { ready, err ->
            if (seq != loadSeq) return@bg
            if (err != null || ready == null) { Music.toast("Couldn't get “${t.title}” back: ${err?.message}"); return@bg }
            play(t, ready, wasPlaying, pos)
        }
    }

    // ---- the clock ----
    /** Twice a second, for as long as the app runs: where the song is, what counts as played, a crossfade coming up,
     *  a song stuck at its end, a song change that's taking too long. Started once, by [Music.init]. */
    fun start() {
        Music.scope.launch {
            while (true) {   // the position, and what counts as played
                val p = Music.player
                if (p != null && p.isPlaying) {
                    val now = p.positionMs / 1000.0
                    val moved = now - position
                    position = now
                    if (moved in 0.0..1.5) { listened(moved); ListenLog.heard(moved) }   // (Wrapped's diary of listening time)
                    p.durationMs.takeIf { it > 0 }?.let { duration = maxOf(it / 1000.0, now) }   // a file longer than it says: its bar goes on to its real end
                    maybeCrossfade(p)
                }
                if (p != null) endWatch(p)
                changeWatch(p)
                delay(500)
            }
        }
    }

    private val endWatcher = EndWatch()

    /** A song that reached its end but never said so (seen with some AAC/M4A files, and with some sound outputs that
     *  never report the last sound played): the player sits at -0:00 "playing" and nothing comes next, or its clock
     *  runs on past the end (with Repeat one it then never started again). Within its last 1.5 seconds, still meant to
     *  be playing, and the position hasn't moved for 2.5 seconds; or 1.5 seconds past its end: it's over, on to the next
     *  (or, on Repeat one, the same song from the start). On both the phone and the PC. */
    private fun endWatch(p: Engine) {
        val t = current()
        // Its length: the player's, or the song's own (its tags', the service's) when that's longer, so a file whose
        // player underestimates its length (a VBR MP3 without its header, a stream) isn't cut short while it still plays.
        val engineMs = p.durationMs
        val songMs = ((t?.duration ?: 0.0) * 1000).toLong()
        val at = p.positionMs
        // The watching itself is EndWatch's (the rules alone, tested by themselves); here, what's done about it
        val verdict = endWatcher.look(System.currentTimeMillis(), engineMs, songMs, at, playWhenReady = p.playWhenReady, advancing = advancing,
            remote = Remote.active, loaded = t != null && loadedFor == t.id, ready = p.state == Engine.READY, buffering = p.state == Engine.BUFFERING,
            idle = p.state == Engine.IDLE, audioPlaying = p.audioPlaying, audioEnded = p.audioEnded)
        when (verdict) {
            EndWatch.Verdict.PLAYING -> {}
            // Its music has all been played out, but the player hasn't said it ended: on to the next, nothing for Problems
            EndWatch.Verdict.AUDIO_DONE -> { advancing = false; endHow = "its music was all played out (the watch)"; trackEnded() }
            EndWatch.Verdict.STUCK, EndWatch.Verdict.PAST -> {
                Crashes.note("“${t?.title}” ${if (verdict == EndWatch.Verdict.PAST) "ran on past" else "stopped at"} its end without finishing: " +
                    (if (repeat == "one") "played again" else "moved on to the next song") +
                    " (at ${at / 1000.0}s of ${engineMs / 1000.0}s, the song says ${songMs / 1000.0}s, state ${p.state})")
                advancing = false
                endHow = "the watch: $verdict"
                trackEnded()
            }
        }
    }

    // ---- 2.0: song crossfade ----
    private var tail: Engine? = null   // the song ending, ready to carry on fading in a player of its own
    private var tailFor: String? = null
    private var tailSounded: Engine? = null   // the crossfade's second player once it got going (lined up or not, it's handled)

    private fun dropTail() {
        tail?.let { runCatching { it.release() } }
        tail = null; tailFor = null
    }

    /** Crossfade (Equalizer › Crossfade): the last seconds of a song fade out while the next fades in. A few seconds
     *  before, a second player gets the song ending ready, paused at the same place; at the fade it takes over from
     *  the same moment (only once it's really playing does this one move on), so there's no gap: the ending song
     *  gets quieter while the next one comes up from quiet, both at once. Not with bit-perfect, Repeat one, streams,
     *  casting, or (Smart transitions) between songs of one album, which flow into each other. When it happens and
     *  when it doesn't are [CrossfadeRules]'; this is the doing of it. */
    private fun maybeCrossfade(p: Engine) {
        val t = current()
        if (t == null || CrossfadeRules.unavailable(Prefs.on(Prefs.CROSSFADE), Music.bitPerfect, repeat == "one", Remote.active, Music.cast != null, streaming, djSide.musicDucked)) {
            if (tail != null && tailFor != fadingId) dropTail()
            return
        }
        if (fadingId == t.id) return
        if (tailFor != null && tailFor != t.id) dropTail()   // moved on (skipped) before it faded
        val n = Prefs.num(Prefs.CROSSFADE_SECS, 6f).toDouble()
        val fadeAt = CrossfadeRules.fadeAt(duration, n) ?: return
        val nxt = queue.getOrNull(index + 1) ?: return
        // A new DJ set next (the DJ talks between them), or the DJ says a word before the next song: no fade
        val djTalks = djSide.talksBefore(t, nxt)
        if (CrossfadeRules.keptApart(Music.sameAlbum(t, nxt), Prefs.on(Prefs.SMART_TRANSITIONS), djTalks)) return
        // Get the ending ready early: a player of its own, this song, paused near where the fade starts.
        if (CrossfadeRules.getReady(position, fadeAt, tail != null)) {
            val uri = p.currentUri ?: return
            runCatching {
                val x = p.tail(uri, (fadeAt * 1000).toLong()) ?: return
                tail = x; tailFor = t.id
            }
            return
        }
        val x = tail ?: return
        if (position < fadeAt) return
        if (x.state != Engine.READY) {   // not ready in time: no crossfade this once, just the usual change
            if (position > duration - 1) dropTail()
            return
        }
        // Take over: the second player plays on from this moment, at this volume; once it's really playing, this one
        // moves on to the next song (coming up from quiet), and the ending fades out over the same seconds.
        fadingId = t.id
        val from = p.volume.coerceAtLeast(0.05f)
        val left = maxOf(1.0, minOf(n, duration - position))
        x.seekTo(p.positionMs + 40)
        // Silent until it's really playing, then the two swap in one go: before, both played the ending at full
        // volume for the moment the second took to start, a doubled (6 dB louder) blip at the start of every crossfade.
        x.volume = 0f
        x.onceSounding {
            tailSounded = x
            if (fadingId != t.id || current()?.id != t.id) { runCatching { x.release() }; return@onceSounding }
            Music.scope.launch {
                // Its sound takes a moment to reach the speakers after it says it's playing: wait for that, then hand
                // over gently, this one going down as the second comes up (an equal-power blend over a third of a
                // second), so there's never a moment of silence (the old instant swap left a short cut) nor both at once.
                delay(200)
                if (fadingId != t.id || current()?.id != t.id) { runCatching { x.release() }; return@launch }
                // It started a moment after it was told to, so it's behind the song as you hear it: handing over then
                // played the last half second or so again (a word heard twice before the fade). Line it up first, still
                // silent; if it can't be lined up, no crossfade this once: the song ends as usual, nothing repeated.
                if (!alignTail(x, t.id)) { fadeNote = " (after a crossfade that couldn't be lined up)"; runCatching { x.release() }; return@launch }
                if (fadingId != t.id || current()?.id != t.id) { runCatching { x.release() }; return@launch }
                val swap = 7
                for (k in 1..swap) {
                    delay(45)
                    val a = (Math.PI / 2 * k / swap)
                    x.volume = from * kotlin.math.sin(a).toFloat()
                    Music.player?.volume = from * kotlin.math.cos(a).toFloat()
                }
                x.volume = from
                Music.player?.volume = 0f   // the second player has it now
                fadeIn = -1.0   // the next song comes in silent; this fade brings it up, in step with the ending
                crossTo = queue.getOrNull(index + 1)?.id
                fadingOut = x
                val mark = System.currentTimeMillis()
                endHow = "crossfade"
                trackEnded()
                // The ending plays on at full until the next song is really sounding (it takes a moment to load): before,
                // the ending began fading at once while the next only came up once it had loaded, so for that moment
                // both were low, a dip that sounded like a cut.
                val nextId = current()?.id
                var waited = 0
                while (fadingOut === x && current()?.id == nextId && waited < 4000 && !(loadedFor == nextId && Music.player?.isPlaying == true)) { delay(20); waited += 20 }
                if (current()?.id != nextId) fadingOut = null   // skipped meanwhile: the ending stops now
                val left2 = (if (x.durationMs > 0) (x.durationMs - x.positionMs) / 1000.0 else left - (System.currentTimeMillis() - mark) / 1000.0)
                val rest = maxOf(0.6, minOf(n, left2 - 0.1))
                val steps = (rest * 25).toInt().coerceAtLeast(1)
                for (k in 1..steps) {
                    delay(40)
                    if (fadingOut !== x || current()?.id != nextId) break   // stopped (the DJ began talking, or you skipped)
                    val a = Math.PI / 2 * k / steps
                    x.volume = from * kotlin.math.cos(a).toFloat()                    // an equal-power crossfade: as this goes down…
                    if (loadedFor == nextId) Music.player?.volume = kotlin.math.sin(a).toFloat()   // …the next comes up, together
                }
                if (loadedFor == nextId && current()?.id == nextId) Music.player?.volume = 1f
                if (fadingOut === x) fadingOut = null
                runCatching { x.release() }
            }
        }
        x.play()
        tail = null; tailFor = null
        Music.scope.launch {   // if it never got going, move on anyway
            delay(1800)
            if (fadingId == t.id && current()?.id == t.id && tailSounded !== x) { fadeIn = 0.0; runCatching { x.release() }; endHow = "a crossfade whose second player never got going"; trackEnded() }
        }
    }

    /** Puts crossfade's second player (silent) at exactly the place the song is sounding now, so the handover repeats
     *  nothing: it measures how far behind (or ahead) it is, seeks it that far on plus the moment a seek takes, and
     *  measures again, until they're within a few hundredths of a second. false if it can't (the song moved on). */
    private suspend fun alignTail(x: Engine, id: String): Boolean {
        var est = -1L   // how long a seek holds it up, learned from each try
        repeat(6) {
            val d = tailLag(x, id) ?: return false
            if (kotlin.math.abs(d) <= 40) return true
            if (est < 0) est = d.coerceIn(0L, 1500L) else est = (est + d).coerceIn(0L, 1500L)
            val p = Music.player ?: return false
            if (p.durationMs > 0 && p.positionMs + est > p.durationMs - 1500) return false   // too near the end now
            x.seekTo(p.positionMs + est)
        }
        return (tailLag(x, id) ?: return false).let { kotlin.math.abs(it) <= 40 }
    }

    /** How far the second player is behind the song as it sounds (ms; less than 0: ahead), once it's really moving. */
    private suspend fun tailLag(x: Engine, id: String): Long? {
        var waited = 0
        while (true) {
            if (fadingId != id || current()?.id != id) return null
            val a = x.positionMs
            delay(150); waited += 150
            val b = x.positionMs
            if (x.isPlaying && b - a in 90L..260L) break
            if (waited > 2500) return null
        }
        val p = Music.player ?: return null
        if (!p.isPlaying) return null
        val ds = (1..5).map { delay(20); p.positionMs - x.positionMs }.sorted()
        return ds[2]
    }
}
