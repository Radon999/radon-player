package org.omarchy.music

/** Size of Now playing (Settings › General, under Size of everything): the Now playing screen drawn smaller or bigger
 *  as a whole (its cover, its words and its buttons together), for a big monitor where the cover takes up more room
 *  than it needs. Kept on each device (never synced), like Size of everything. 100% is the usual size. */
object NowSizeRules {
    const val PREF = "np_size"
    /** Per cent of the usual size. */
    val CHOICES = listOf(50, 60, 70, 80, 90, 100, 110, 125)

    /** The size kept, as per cent: 100 when nothing (or nonsense) is kept, never outside the choices' range. */
    fun percent(kept: String): Int = kept.trim().toIntOrNull()?.coerceIn(CHOICES.first(), CHOICES.last()) ?: 100
    fun factor(kept: String): Float = percent(kept) / 100f
    fun label(percent: Int): String = if (percent == 100) "100% (usual)" else "$percent%"
    /** The menu: (what is kept, what is shown). */
    val OPTIONS: List<Pair<String, String>> get() = CHOICES.map { it.toString() to label(it) }

    /** Now playing's visualizer size (a slider, Settings › General): every visualizer on Now playing (over the cover,
     *  round it, along the bottom) that much smaller or bigger, on top of the size of Now playing; full screen ones
     *  still fill the screen. Per cent, [VIS_MIN] to [VIS_MAX]; 100 is the usual size. */
    const val VIS_PREF = "np_vis_size"
    const val VIS_MIN = 25
    const val VIS_MAX = 200
    /** The album cover's size on Now playing (a slider): the cover alone smaller, up to the full room it has (100).
     *  Per cent, [COVER_MIN] to 100. */
    const val COVER_PREF = "np_cover_size"
    const val COVER_MIN = 30
    const val COVER_MAX = 100
    /** The sliders move in steps of this many per cent. */
    const val STEP = 5

    /** A slider's value kept (a number; nothing kept: 100), on a step, within its range. */
    fun visPercent(kept: Float?): Int = stepped(kept, VIS_MIN, VIS_MAX)
    fun coverPercent(kept: Float?): Int = stepped(kept, COVER_MIN, COVER_MAX)
    private fun stepped(kept: Float?, min: Int, max: Int): Int {
        val v = kept?.takeIf { !it.isNaN() } ?: return 100
        return ((Math.round(v / STEP) * STEP)).coerceIn(min, max)
    }

    /** Whether Size of Now playing is in use: not once either slider (the album cover's, the visualizer's) has been
     *  moved off 100%: those size Now playing's parts themselves, so the overall size is switched off (kept, not lost:
     *  both sliders back at 100% and it's in use again). */
    fun overallOn(visPercent: Int, coverPercent: Int): Boolean = visPercent == 100 && coverPercent == 100

    /** The overall size as used now: [factor] while it's on, else the usual size. */
    fun overall(kept: String, visPercent: Int, coverPercent: Int): Float =
        if (overallOn(visPercent, coverPercent)) factor(kept) else 1f
}
