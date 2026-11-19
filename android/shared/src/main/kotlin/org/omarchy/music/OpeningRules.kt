package org.omarchy.music

/** Linux' small rules for the PC app: what opens a link, how the window is drawn, and what closing the window does. */
object OpeningRules {
    /** What a Linux desktop opens a link or a file with, in the order tried: each a command the address is added to. */
    val LINUX_OPENERS: List<List<String>> = listOf(listOf("xdg-open"), listOf("gio", "open"))

    /** Whether the window is drawn without the graphics card: RADON_RENDER says so for one start ("software" on,
     *  "opengl" or "gpu" off, whatever the setting), else the setting. */
    fun softwareDrawing(env: String?, setting: Boolean): Boolean = when (env?.trim()?.lowercase()) {
        "software", "soft", "cpu" -> true
        "opengl", "gl", "gpu" -> false
        else -> setting
    }

    /** Closing the window. With a tray the app stays there and the music goes on ("tray"). With none (some Linux
     *  desktops) it is minimized and the music goes on ("minimize"). On a desktop that has no tray and no minimized
     *  windows either (noMinimize: Hyprland, Sway and the like, where a window asked to minimize stays where it is, so
     *  the close button did nothing at all): the window is hidden while music plays, which goes on and is still worked
     *  by the desktop's media controls and keys, and opening the app again shows it ("hide"); with nothing playing the
     *  app closes ("quit"). */
    fun onClose(hasTray: Boolean, noMinimize: Boolean = false, playing: Boolean = false): String = when {
        hasTray -> "tray"
        !noMinimize -> "minimize"
        playing -> "hide"
        else -> "quit"
    }
}
