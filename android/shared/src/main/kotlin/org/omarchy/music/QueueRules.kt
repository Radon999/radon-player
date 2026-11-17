package org.omarchy.music

/** The queue's rules alone, taken out of Music (the playback service, docs/rebuild/RADON_REBUILD_PLAN.md, Phase 9):
 *  what happens when a song is done or Next is pressed, what Previous does, and how your other queues are kept. No
 *  player and no app here, so each rule is read and tested by itself. */
object QueueRules {
    /** What comes after the song playing, when it ends or Next is pressed. */
    enum class After {
        /** The next song of this queue. */ NEXT_SONG,
        /** The DJ's next set (its queue is done: never a radio, never round again). */ DJ_SET,
        /** Repeat all: round to the first song again. */ ROUND_AGAIN,
        /** This queue is done and another of yours has songs still to play: that one goes on. */ NEXT_QUEUE,
        /** Nothing more here: the player stops, or Autoplay finds songs like the last one. */ END,
    }

    /** index: the song playing; size: how many the queue has; repeat: "off", "all" or "one" (Repeat one is the
     *  caller's: it never gets here when a song ends by itself). */
    fun after(index: Int, size: Int, djOn: Boolean, repeat: String, anotherQueue: Boolean): After = when {
        index + 1 < size -> After.NEXT_SONG
        djOn -> After.DJ_SET
        repeat == "all" && size > 0 -> After.ROUND_AGAIN
        repeat == "off" && anotherQueue -> After.NEXT_QUEUE
        else -> After.END
    }

    /** Previous: the start of this song again (true) when it's 10 seconds in or more, or it's the first song; in a
     *  song's first 10 seconds, the song before (false). */
    fun previousRestarts(position: Double, index: Int): Boolean = position >= 10 || index <= 0

    /** The next of your other queues with songs still to play (the last one used first), or null. */
    fun next(queues: List<SavedQueue>, current: String): SavedQueue? = queues.firstOrNull { it.id != current && it.index < it.tracks.size - 1 }

    /** Your other queues once the one you're leaving (keep) is put aside for songs from a new list: it goes first
     *  (the last used), in place of any older copy of itself, and only the most are kept. */
    fun putAside(queues: List<SavedQueue>, keep: SavedQueue, most: Int): List<SavedQueue> =
        (listOf(keep) + queues.filter { it.id != keep.id && it.name != keep.name }).take(most)

    /** Your other queues once you go back to one of them (to): the one you leave (keep) takes its place, so the
     *  order you put them in stays. */
    fun swap(queues: List<SavedQueue>, keep: SavedQueue, to: String): List<SavedQueue> =
        queues.filter { it.id != keep.id }.map { if (it.id == to) keep else it }

    /** Shuffle: the whole list again, not only what's still to come. The song playing goes first, then the songs you
     *  queued by hand (in your order), then every other song of the list, the ones already played too, shuffled
     *  (shuffle does that). No song is lost and none comes twice. The song playing is at 0 afterwards. */
    fun reshuffled(queue: List<Track>, index: Int, shuffle: (List<Track>) -> List<Track>): List<Track> {
        if (index !in queue.indices) return queue
        val coming = queue.drop(index + 1)
        return listOf(queue[index]) + coming.filter { it.queued } + shuffle(queue.take(index) + coming.filter { !it.queued })
    }

    /** The queue with songs added: right after the one playing (next), or after the songs queued by hand before.
     *  move (Settings › Playback): a song that is already waiting further on is taken from there, so it is in the
     *  queue once; without it the song is there twice (where it was, and where you put it). What has played, and
     *  the song playing, stay as they are. */
    fun enqueued(queue: List<Track>, index: Int, add: List<Track>, next: Boolean, move: Boolean): List<Track> {
        var coming = queue.drop(index + 1)
        if (move) { val ids = add.mapTo(HashSet()) { it.id }; coming = coming.filter { it.id !in ids } }
        var pos = 0
        if (!next) while (pos < coming.size && coming[pos].queued) pos++
        return queue.take(index + 1) + coming.take(pos) + add + coming.drop(pos)
    }
}
