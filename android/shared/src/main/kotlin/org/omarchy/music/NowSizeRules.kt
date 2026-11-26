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
}
