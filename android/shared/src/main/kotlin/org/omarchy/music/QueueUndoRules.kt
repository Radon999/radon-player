package org.omarchy.music

/** Undo for the queue: a queue cleared, shuffled, reordered or thinned out by accident is put back as it was. Each
 *  change you make to the queue keeps the queue from just before (the last ten); Undo puts the latest back. The song
 *  playing is never interrupted: it goes on, at its place in the queue that comes back. Starting something new (a
 *  song tapped, another queue) is not a change to undo: what was kept is forgotten then. */
object QueueUndoRules {
    const val KEEP = 10

    /** What each kind of change is called on the Undo button. */
    fun label(what: String, n: Int = 0): String = when (what) {
        "clear" -> "Undo: queue cleared"
        "shuffle" -> "Undo: shuffled"
        "move" -> "Undo: song moved"
        "remove" -> if (n > 1) "Undo: %,d songs removed".format(n) else "Undo: song removed"
        "add" -> if (n > 1) "Undo: %,d songs added".format(n) else "Undo: song added"
        else -> "Undo"
    }

    /** Where the song playing now is in the queue that comes back (ids: that queue's songs; was: where the playing
     *  song was in it then): its place nearest to where it was, since a queue may hold a song twice. null: the song
     *  playing isn't in that queue (something else was started since), so there is nothing to put back. */
    fun indexIn(ids: List<String>, was: Int, playing: String?): Int? {
        if (playing == null) return null
        return ids.indices.filter { ids[it] == playing }.minByOrNull { kotlin.math.abs(it - was) }
    }

    /** The kept queues with one more: the newest last, no more than [KEEP]. */
    fun <T> kept(stack: List<T>, one: T): List<T> = (stack + one).takeLast(KEEP)

    /** What is said when it is put back. */
    fun done(what: String): String = when (what) {
        "clear" -> "The queue is back as it was before you cleared it"
        "shuffle" -> "The queue is back in the order it had before the shuffle"
        "move" -> "The song is back where it was"
        "remove" -> "Back in the queue"
        "add" -> "Taken out of the queue again"
        else -> "The queue is back as it was"
    }
}
