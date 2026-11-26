package org.omarchy.music

/** Which visualizer the Visualizer widget (the phone's and the PC's) draws, from its own choice (Settings ›
 *  Personalization › Widgets):
 *  - [FOLLOW] (the default): Now playing's visualizer, as picked in Personalization: its style, and with Dual on its bottom
 *    one too. Now playing's Off has nothing to show, so then the Visualizer tab's style;
 *  - [TAB]: the Visualizer tab's style;
 *  - a style of its own (any of Now playing's: the slim bars or one of the Visualizer's).
 *  A style that's gone (one you made and deleted) is followed as [FOLLOW] is. */
object WidgetVisRules {
    const val FOLLOW = ""
    const val TAB = "tab"
    const val OFF = "off"

    /** What the widget draws: main, and second along the bottom under it (Dual's bottom style), or null. */
    data class Pick(val main: String, val second: String? = null)

    /** choice: the widget's own; now and nowSecond: Now playing's (its style, and Dual's bottom one when it's drawn);
     *  tab: the Visualizer tab's; known: whether a style still exists. */
    fun pick(choice: String, now: String, nowSecond: String?, tab: String, known: (String) -> Boolean): Pick = when {
        choice == TAB -> Pick(tab)
        choice != FOLLOW && choice != OFF && known(choice) -> Pick(choice)
        now == OFF || !known(now) -> Pick(tab)
        else -> Pick(now, nowSecond?.takeIf { it != OFF && it != now && known(it) })
    }
}
