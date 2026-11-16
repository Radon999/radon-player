package org.omarchy.music

/** Settings › Smoothest screen is the phone's: the PC's window already draws at its monitor's own rate, so there is
 *  nothing to ask for here (maxHz 0: the setting isn't shown). */
object ScreenRate {
    const val PREF = "high_refresh"
    fun maxHz(): Int = 0
    fun apply() {}
}
