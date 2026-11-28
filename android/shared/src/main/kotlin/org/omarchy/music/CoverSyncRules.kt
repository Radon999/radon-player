package org.omarchy.music

/** Sync album covers (the switch above Sync with phone / PC): whether a sync carries the album covers that Tags &
 *  covers match keeps the same on both. Off, a sync is done in a moment once the covers already match; on, they are
 *  compared and the ones that differ go across (which can take several rounds). Pure. */
object CoverSyncRules {
    /** The switch, kept as "don't" so that it is on until you turn it off (as before there was a switch). */
    const val PREF_OFF = "tag_match_no_covers"

    /** Whether this sync carries covers: the switch of the device where Sync now was tapped decides, so flipping it on
     *  there and syncing is enough. pcAsked: the PC's Sync now asked the phone (pcSwitch: as the PC said; null from a
     *  PC app that has no switch yet, which always did). */
    fun carries(pcAsked: Boolean, pcSwitch: Boolean?, phoneSwitch: Boolean): Boolean = if (pcAsked) pcSwitch ?: true else phoneSwitch
}
