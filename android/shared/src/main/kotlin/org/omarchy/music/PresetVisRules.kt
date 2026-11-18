package org.omarchy.music

/** Presets and your visualizers (Settings › Personalization, under Preset Shuffle): two switches, "Presets leave my
 *  middle visualizer" and "Presets leave my bottom visualizer". With one on, a preset (picked by hand, by the shuffle,
 *  by the time of day, or Follows the cover's scene) changes everything else and leaves that visualizer of Now
 *  playing's as you set it: none, if you have none. So: just the looks (both on), or the preset's bottom one under
 *  your own middle one (middle on).
 *
 *  Now playing keeps its visualizers as three things: the style ([Trio.now]), whether Dual is on, and Dual's bottom
 *  style. Without Dual the one style is either a middle one (round the cover) or a bottom one (along the bottom). */
object PresetVisRules {
    const val KEEP_MIDDLE = "preset_keep_vis_middle"
    const val KEEP_BOTTOM = "preset_keep_vis_bottom"

    data class Trio(val now: String, val dual: Boolean, val bottom: String)

    private fun some(k: String) = k.isNotBlank() && k != "off"
    /** The one in the middle (null: none). isBottom: whether a style stands along the bottom. */
    fun middle(t: Trio, isBottom: (String) -> Boolean): String? = t.now.takeIf { some(it) && !isBottom(it) }
    /** The one along the bottom (null: none): Dual's, under a middle one; else the style itself when it is a bottom one. */
    fun bottom(t: Trio, isBottom: (String) -> Boolean): String? =
        if (middle(t, isBottom) != null) t.bottom.takeIf { t.dual && some(it) } else t.now.takeIf { some(it) }

    /** What to put on: the preset's, with yours kept where its switch is on. Neither on: the preset's as it is. */
    fun mix(yours: Trio, preset: Trio, keepMiddle: Boolean, keepBottom: Boolean, isBottom: (String) -> Boolean): Trio {
        if (!keepMiddle && !keepBottom) return preset
        val m = middle(if (keepMiddle) yours else preset, isBottom)
        val b = bottom(if (keepBottom) yours else preset, isBottom)
        return join(m, b, yours.bottom)
    }

    /** A middle one and a bottom one (either may be none) as the three things Now playing keeps. keptBottom: what
     *  Dual's bottom style stays set to when there is no bottom one. */
    fun join(m: String?, b: String?, keptBottom: String): Trio = when {
        m != null && b != null -> Trio(m, true, b)
        m != null -> Trio(m, false, keptBottom)
        b != null -> Trio(b, false, b)
        else -> Trio("off", false, keptBottom)
    }
}
