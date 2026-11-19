package org.omarchy.music

/** Size of everything (Settings › General): the app's text and everything else drawn bigger or smaller together, for
 *  a screen where it all comes out too small (a 4K monitor, a desktop that doesn't scale older apps) or too big. */
object UiSizeRules {
    const val PREF = "ui_size"
    /** Per cent of the usual size. */
    val CHOICES = listOf(80, 90, 100, 110, 125, 150, 175, 200, 250)

    /** The size kept, as per cent: 100 when nothing (or nonsense) is kept, never outside what can be read and used. */
    fun percent(kept: String): Int = kept.trim().toIntOrNull()?.coerceIn(CHOICES.first(), CHOICES.last()) ?: 100
    fun factor(kept: String): Float = percent(kept) / 100f
    fun label(percent: Int): String = if (percent == 100) "100% (usual)" else "$percent%"
    /** The menu: (what is kept, what is shown). */
    val OPTIONS: List<Pair<String, String>> get() = CHOICES.map { it.toString() to label(it) }
}
