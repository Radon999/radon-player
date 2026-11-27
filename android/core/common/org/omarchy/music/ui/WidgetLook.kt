package org.omarchy.music.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import org.omarchy.music.Music
import org.omarchy.music.Prefs
import org.omarchy.music.WidgetVisRules

/** What the widgets (the phone's home screen ones and the PC's desktop ones) take from the app (Settings ›
 *  Personalization › Widgets): the Visualizer widget's visualizer and its colour, and whether they all wear the app's
 *  look. Kept like every setting, and synced between the phone and the PC (the owner wants them the same on both).
 *
 *  The look copied is the look in use: a preset's when one is on (a preset puts on its look), the look picked otherwise;
 *  the Now playing look in the colours of the cover playing. Only its colours are copied (background, writing, accent),
 *  never its effects, stars or moving backdrops. */
object WidgetLook {
    /** The Visualizer widget's visualizer: "" Now playing's (the default), "tab" the Visualizer tab's, or a style. */
    const val VIS = "widget_vis_style"
    /** Its colour: "" the one the app draws that style in (the default), or a colour (a VIS_COLORS key). */
    const val VIS_COLOR = "widget_vis_color"
    /** Every widget in the app's look (its colours) instead of the cover's colour. */
    const val LOOK = "widget_look"

    fun copyLook(): Boolean = Prefs.on(LOOK)

    /** The look the widgets copy, said plainly (Settings): its name, and whether it takes the cover's colours itself. */
    fun copying(): String {
        val name = Looks.ALL.firstOrNull { it.first == Music.look }?.second ?: Music.look
        return if (Music.look == Looks.NOW_PLAYING || CoverScene.on())
            "Copying “$name”, which is in the colours of the cover playing, so the widgets are too. Pick another look (or a " +
                "preset without Follows the cover) to have them in other colours"
        else "Copying “$name”"
    }

    /** The Visualizer widget's choices, for its menu: follow Now playing, the Visualizer tab's, or one of its own. */
    val VIS_CHOICES: List<Pair<String, String>> get() = listOf(WidgetVisRules.FOLLOW to "Same as Now playing", WidgetVisRules.TAB to "Same as the Visualizer tab") +
        NP_VIS_STYLES.filter { it.first != WidgetVisRules.OFF }
    val COLOR_CHOICES: List<Pair<String, String>> get() = listOf("" to "Same as the app") + VIS_COLORS

    /** What the Visualizer widget draws now (it changes as soon as Now playing's visualizer is changed). */
    fun pick(): WidgetVisRules.Pick {
        val known = NP_VIS_STYLES.map { it.first }.toSet()
        return WidgetVisRules.pick(Prefs.text(VIS), Vis.nowStyle(), Vis.nowSecond(), Vis.style()) { it in known }
    }

    /** The colour key a style is drawn in on the widget: the widget's own colour (over any set for the style), else the app's. */
    fun colorKey(): String = Prefs.text(VIS_COLOR).takeIf { k -> k.isNotEmpty() && VIS_COLORS.any { it.first == k } }?.let { "!$it" } ?: Vis.color()

    /** The colours copied from the app's look, with Copy the app's look on (null: off: the cover's colour as always).
     *  coverRgb: the cover playing's main colour, for the Now playing look (worked out by the widgets themselves, so it's
     *  right even while the app's own window isn't open). */
    fun colors(coverRgb: Int?, live: Boolean = false): Copied? {
        if (!copyLook()) return null
        // live (the PC, whose window is always running): exactly the colours the app shows now, whatever made them (a
        // preset, Follows the cover, the Now playing look). The phone's widgets can be drawn while the app isn't, so
        // there the Now playing look is worked out from the widgets' own cover
        val c = if (!live && Music.look == Looks.NOW_PLAYING && coverRgb != null) Looks.coverColors(coverRgb)
            else Looks.LookColors(C.bg, C.light, C.fg, C.muted, C.accent, C.gradient, false, C.glow)
        return Copied(c.bg, c.grad?.takeIf { it.size >= 2 }, c.card, c.fg, c.muted, c.accent, c.bg.luminance() > 0.45f)
    }

    /** A look's colours as the widgets use them: background (and its gradient, if it has one), cards, writing, quiet
     *  writing, accent, and whether it's a light look (dark writing). */
    class Copied(val bg: Color, val gradient: List<Color>?, val card: Color, val fg: Color, val muted: Color, val accent: Color, val light: Boolean) {
        /** One colour for the background where only one can be used (the phone's widgets): the gradient's middle. */
        val solid: Color get() = gradient?.let { g -> androidx.compose.ui.graphics.lerp(g[(g.size - 1) / 2], g[g.size / 2], 0.5f) } ?: bg
    }
}

/** The Visualizer widget's picture: [WidgetLook.pick]'s style over the whole area (the slim bars as Now playing draws
 *  them), and Dual's bottom style along the bottom under it. base: the colour "Look's colour" means here (the cover's
 *  light shade, or the look's accent when the look is copied); fg: the writing's colour. */
fun DrawScope.drawWidgetVis(s: VisState, base: Color, fg: Color) {
    val pick = WidgetLook.pick()
    val key = WidgetLook.colorKey()
    fun DrawScope.one(style: String, part: Int) {
        if (style == "slim") { drawSlim(s.levels, VisPalette.of("bars", key, base, s.phase).main, 2.6f); return }
        MyVis.drawingPart(part) { drawVis(style, s, VisPalette.of(style, key, base, s.phase), fg, backdrop = true) }
    }
    clipRect {
        one(pick.main, if (pick.second != null) MyVis.PART_MIDDLE else MyVis.PART_ALL)
        pick.second?.let { b ->
            val h = size.height * 0.4f
            translate(top = size.height - h) { withSize(size.width, h) { one(b, MyVis.PART_BOTTOM) } }
        }
    }
}
