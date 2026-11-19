package org.omarchy.music

/** Linux desktops whose windows are placed and sized by the desktop itself with no frame round them (Hyprland, Sway,
 *  niri, i3 and the like). The PC app's window kit (Java's) doesn't know these, waits for a frame that never comes and
 *  so never learns its window's real size: the app was drawn at the size it asked for, in a corner of a bigger window,
 *  with a line round it. Java follows the window's size on them when it is started with one setting
 *  ([SETTING] = 1), so on these desktops the app starts itself again with it. Every other desktop (GNOME, KDE, Windows)
 *  is left exactly as it was. */
object WindowSystemRules {
    const val SETTING = "_JAVA_AWT_WM_NONREPARENTING"

    private val TILING = listOf("hyprland", "sway", "niri", "river", "wayfire", "labwc", "dwl", "i3", "bspwm", "dwm", "awesome",
        "xmonad", "qtile", "herbstluftwm", "leftwm", "spectrwm", "cwm")
    /** What each of them sets for the apps it starts, whatever the desktop calls itself. */
    private val SIGNS = listOf("HYPRLAND_INSTANCE_SIGNATURE", "SWAYSOCK", "NIRI_SOCKET", "I3SOCK")

    /** Whether to start again with the setting: Linux, one of those desktops, and the setting not made already (by the
     *  person, or by the start before this one). */
    fun startAgain(os: String, env: Map<String, String>): Boolean = env[SETTING].isNullOrEmpty() && placesWindowsItself(os, env)

    /** Linux on one of those desktops. They have no minimized windows either: a window asked to minimize stays where
     *  it is. */
    fun placesWindowsItself(os: String, env: Map<String, String>): Boolean {
        if (!os.lowercase().contains("linux")) return false
        if (SIGNS.any { !env[it].isNullOrEmpty() }) return true
        val names = listOf("XDG_CURRENT_DESKTOP", "XDG_SESSION_DESKTOP", "DESKTOP_SESSION").flatMap { (env[it] ?: "").lowercase().split(':', ';', ',', ' ', '/') }
        return names.any { n -> n in TILING }
    }
}
