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

    /** Closing the window: with a tray the app stays there and the music goes on ("tray"); with none (some Linux
     *  desktops) there'd be no way back to it, so the app is minimized and the music goes on ("minimize"). */
    fun onClose(hasTray: Boolean): String = if (hasTray) "tray" else "minimize"
}
