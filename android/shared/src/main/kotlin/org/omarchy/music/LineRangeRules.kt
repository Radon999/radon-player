package org.omarchy.music

/** Ticking lines for a lyrics card two taps at a time: the first line ticked is marked 1, the next one ticked is marked 2
 *  and every line between them is ticked with it, so a whole verse is the first line and the last one, not each line in
 *  turn. A third line ticked is a new 1 (what was ticked stays), so a second run can be added the same way. A ticked line
 *  tapped is unticked on its own. */
object LineRangeRules {
    /** picked: the lines ticked, in the order the card shows them. first, last: the lines marked 1 and 2 (null: none). */
    data class State(val picked: List<Int> = emptyList(), val first: Int? = null, val last: Int? = null)

    /** Line i tapped, with at most max lines ticked at once. */
    fun tap(s: State, i: Int, max: Int): State {
        if (i in s.picked) {
            // Unticked on its own. The 1 unticked: both marks go. The 2 unticked: the 1 waits for a new 2
            val left = s.picked - i
            return when (i) {
                s.first -> State(left, null, null)
                s.last -> State(left, s.first, null)
                else -> s.copy(picked = left)
            }
        }
        if (s.picked.size >= max) return s
        val from = s.first
        if (from == null || s.last != null || from !in s.picked) return State(s.picked + i, i, null)
        // The 2: every line from the 1 to here, nearest the 1 first, as far as there is room; then in the song's order
        val run = if (i > from) (from + 1)..i else (from - 1) downTo i
        val added = run.filter { it !in s.picked }.take(max - s.picked.size)
        // The 2 lands on the line tapped, or on the furthest one there was room for
        return State((s.picked + added).sorted(), from, added.lastOrNull())
    }

    /** What a line's box shows on top of its tick: "1", "2" or nothing. */
    fun mark(s: State, i: Int): String = when (i) { s.first -> "1"; s.last -> "2"; else -> "" }
}
