package org.omarchy.music

/** The PC's small rules that differ between Windows and Linux: starting with the system on Linux, and which of a
 *  release's files is this system's installer. */
object StartupRules {
    /** Where a Linux desktop looks for what starts when you sign in: $XDG_CONFIG_HOME/autostart, else
     *  ~/.config/autostart. */
    fun autostartDir(xdgConfigHome: String?, home: String): String =
        (xdgConfigHome?.takeIf { it.isNotBlank() } ?: "$home/.config").trimEnd('/') + "/autostart"

    /** The program the app runs as is its own launcher, not the Java inside it. */
    fun launcher(command: String): Boolean = command.substringAfterLast('/').let { it.isNotEmpty() && it != "java" }

    /** The autostart file: the app started in the tray (--tray). The program's path is quoted as the desktop-entry
     *  rules ask (a quote, a backtick, a dollar sign and a backslash each behind a backslash). */
    fun desktopEntry(exe: String): String {
        val quoted = exe.replace("\\", "\\\\").replace("\"", "\\\"").replace("`", "\\`").replace("$", "\\$")
        return "[Desktop Entry]\nType=Application\nName=Radon Player\nComment=Radon Player, started in the tray\n" +
            "Exec=\"$quoted\" --tray\nTerminal=false\nX-GNOME-Autostart-enabled=true\n"
    }

    /** Which of a release's files installs on this system: the .msi on Windows, the .deb on Linux; null when the
     *  release has none (its page is opened instead). */
    fun installer(names: List<String>, osName: String): String? {
        val os = osName.lowercase()
        val ext = if (os.contains("win")) ".msi" else if (os.contains("linux")) ".deb" else return null
        return names.firstOrNull { it.endsWith(ext, ignoreCase = true) }
    }
}
