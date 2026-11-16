package org.omarchy.music

/** Where a DJ set is in its life (the plan's DjSet record, rule R-D1): planned (its songs are being picked), scripted
 *  (its line is written), voiced (its line is made as speech ahead), queued (its songs are in the queue), announced
 *  (its line was shown or said); or dropped (let go unplayed). */
enum class SetState { PLANNED, SCRIPTED, VOICED, QUEUED, ANNOUNCED, DROPPED }

object SetLife {
    /** A set only moves forward: planned → scripted → (voiced) → queued → announced, and it can be dropped any time
     *  before it's announced. Announced and dropped are the end. */
    fun allowed(from: SetState, to: SetState): Boolean = when (from) {
        SetState.PLANNED -> to == SetState.SCRIPTED || to == SetState.DROPPED
        SetState.SCRIPTED -> to == SetState.VOICED || to == SetState.QUEUED || to == SetState.DROPPED
        SetState.VOICED -> to == SetState.QUEUED || to == SetState.DROPPED
        SetState.QUEUED -> to == SetState.ANNOUNCED || to == SetState.DROPPED
        SetState.ANNOUNCED, SetState.DROPPED -> false
    }

    /** The sets made lately (the last keep of them) and where each is, by its own number; and, once queued, by the
     *  number its songs carry in the queue. Safe to ask from any thread. */
    class Book(private val keep: Int = 200) {
        private val states = LinkedHashMap<Long, SetState>()
        private val queued = LinkedHashMap<Int, Long>()

        @Synchronized fun planned(id: Long) {
            states[id] = SetState.PLANNED
            while (states.size > keep) states.remove(states.keys.first())
        }

        /** The set moves on to: false (and nothing changes) when it isn't known or can't go there from where it is. */
        @Synchronized fun move(id: Long, to: SetState): Boolean {
            val from = states[id] ?: return false
            if (!allowed(from, to)) return false
            states[id] = to
            return true
        }

        @Synchronized fun of(id: Long): SetState? = states[id]

        /** Its songs went into the queue as set number n. */
        @Synchronized fun queuedAs(id: Long, n: Int): Boolean {
            if (!move(id, SetState.QUEUED)) return false
            queued[n] = id
            while (queued.size > keep) queued.remove(queued.keys.first())
            return true
        }

        /** Set number n of the queue began: its line was shown or said. */
        @Synchronized fun announced(n: Int): Boolean = queued[n]?.let { move(it, SetState.ANNOUNCED) } ?: false

        /** How many sets are in each state now. */
        @Synchronized fun counts(): Map<SetState, Int> = states.values.groupingBy { it }.eachCount()
    }
}
