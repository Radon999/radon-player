package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
        /** Other songs were picked, or another queue: the DJ stops. */
        fun otherSongsPicked()
    }
    private object NoDj : DjSide {
        override val on: Boolean get() = false
        override fun otherSongsPicked() {}
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
        Music.loadCurrent()
        if (radio) Music.extendRadio(tracks[i], keepUntil = i)   // the service's song: its own radio
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
        Music.resumeAt = q.position
        Music.loadCurrent()
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
        var pos = index + 1
        if (how != "next") while (pos < queue.size && queue[pos].queued) pos++
        queue = queue.take(pos) + ts + queue.drop(pos)
        Music.afterQueueChange()
        val many = if (ts.size > 1) "${ts.size} songs" else "“${ts[0].title}”"
        Music.toast(if (ts.size > 1) "$many ${if (how == "next") "play next" else "added to the queue"}"
              else "${if (how == "next") "Playing next" else "Added to the queue"}: $many")
        if (ended) Music.next()
    }

    fun unqueueMany(indices: Collection<Int>) {
        if (Remote.active) { Remote.cmd("unqueue", "indices" to indices.toList()); return }
        val drop = indices.filter { it in queue.indices && it != index }.toSet()
        val before = drop.count { it < index }
        queue = queue.filterIndexed { i, _ -> i !in drop }
        index -= before
        Music.afterQueueChange()
    }

    /** The queue's Clear: the songs you queued by hand go (what plays on from the list stays). */
    fun clearQueued() {
        if (Remote.active) { Music.toast("Clear the queue on the desktop"); return }
        queue = queue.take(index + 1) + queue.drop(index + 1).filter { !it.queued }
        Music.afterQueueChange()
    }

    /** The song at i, dragged to j (both after the one playing). */
    fun moveInQueue(i: Int, j: Int) {
        if (Remote.active || i == j || i !in queue.indices || j !in queue.indices || i <= index || j <= index) return
        val q = queue.toMutableList()
        val t = q.removeAt(i)
        q.add(j, t)
        queue = q
        Music.afterQueueChange()
    }

    fun playIndex(i: Int) {
        if (Remote.active) { Remote.cmd("play_index", "index" to i); return }
        if (i in queue.indices) { index = i; Music.loadCurrent() }
    }

    fun cycleRepeat() {
        if (Remote.active) { Remote.cmd("repeat"); return }
        // With the DJ: just Repeat one (a song you want again before the DJ goes on) or off; Repeat all means nothing
        // to the DJ, whose sets go on and on
        repeat = if (djSide.on) (if (repeat == "one") "off" else "one") else mapOf("off" to "all", "all" to "one", "one" to "off")[repeat]!!
        Music.saveState()
    }

    /** Shuffle what's coming up; the songs you queued stay first, in your order. */
    fun shuffle() {
        if (Remote.active) { Remote.cmd("shuffle"); return }
        if (djSide.on) { Music.toast("The DJ plays each set in its own order, so shuffle isn't used with the DJ"); return }
        val rest = queue.drop(index + 1)
        queue = queue.take(index + 1) + rest.filter { it.queued } + shuffleSongs(rest.filter { !it.queued })
        shuffled = true
        Music.afterQueueChange()
    }
}
