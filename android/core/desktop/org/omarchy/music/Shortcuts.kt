package org.omarchy.music

import java.io.File

/** Make desktop shortcuts… (Settings › General, the PC): files that start the app with a command, so one click plays a
 *  playlist, starts the DJ or opens Wrapped. The app hands the command to the one already running (Instance), or starts
 *  with it. Linux: desktop entries in the app menu (~/.local/share/applications) and, if asked, on the desktop. Windows:
 *  shortcuts (.lnk) in the Start menu (a folder of their own) and, if asked, on the desktop, made by PowerShell. What
 *  goes into the files is [DesktopWidgetRules]'. */
object Shortcuts {
    private val os = System.getProperty("os.name").lowercase()
    private val windows = os.contains("win")
    private val linux = os.contains("linux")
    val supported = windows || linux
    /** Where the shortcuts go besides the desktop, as the switch names it. */
    val menuName = if (windows) "Start menu" else "app menu"

    private val home get() = System.getProperty("user.home") ?: ""
    private fun xdg(name: String, otherwise: String) = (System.getenv(name)?.takeIf { it.isNotBlank() } ?: otherwise).trimEnd('/')
    private fun linuxMenu() = File(xdg("XDG_DATA_HOME", "$home/.local/share"), "applications")
    private fun linuxDesktop() = File(DesktopWidgetRules.desktopDir(
        runCatching { File(xdg("XDG_CONFIG_HOME", "$home/.config"), "user-dirs.dirs").readText() }.getOrNull(), home))
    private const val WIN_DESKTOP = "[Environment]::GetFolderPath('Desktop')"
    private const val WIN_MENU = "(Join-Path ([Environment]::GetFolderPath('Programs')) 'Radon Player shortcuts')"

    /** The app's picture beside its launcher (the .deb puts it in <install>/lib). */
    private fun linuxIcon(exe: String): String? =
        File(exe).parentFile?.parentFile?.let { File(it, "lib") }?.listFiles()?.firstOrNull { it.extension == "png" }?.path

    /** Makes them where asked (off the main thread); what to tell you. */
    fun make(list: List<DesktopWidgetRules.Shortcut>, menu: Boolean, desktop: Boolean): String {
        if (list.isEmpty()) return "Pick at least one shortcut"
        if (!menu && !desktop) return "Pick where they go: the $menuName, the desktop or both"
        val n = list.size
        val what = "$n shortcut${if (n != 1) "s" else ""}"
        val where = listOfNotNull(if (menu) "the $menuName" else null, if (desktop) "the desktop" else null).joinToString(" and ")
        if (linux) {
            val exe = Startup.linuxExe() ?: return "Couldn't: only the installed app can make shortcuts"
            val dirs = listOfNotNull(if (menu) linuxMenu() else null, if (desktop) linuxDesktop() else null)
            return if (writeLinux(exe, list, dirs, linuxIcon(exe)) == list.size * dirs.size) "Made $what on $where"
                else "Couldn't make them all: check the folders can be written to"
        }
        if (windows) {
            val exe = Startup.exe() ?: return "Couldn't: only the installed app can make shortcuts"
            val folders = listOfNotNull(if (menu) WIN_MENU else null, if (desktop) WIN_DESKTOP else null)
            return if (Startup.ps(DesktopWidgetRules.windowsScript(exe, list, folders))) "Made $what on $where" else "Windows didn't make them"
        }
        return "Shortcuts are made on Windows and Linux"
    }

    /** Linux: each shortcut's desktop entry written into each folder; how many were written. On the desktop the file is
     *  marked as a program to run (and as trusted, for GNOME), so a double-click starts it rather than opening it. */
    fun writeLinux(exe: String, list: List<DesktopWidgetRules.Shortcut>, dirs: List<File>, icon: String?): Int = dirs.sumOf { dir ->
        list.count { s ->
            runCatching {
                dir.mkdirs()
                val f = File(dir, DesktopWidgetRules.linuxFile(s.name))
                writeAtomic(f, DesktopWidgetRules.desktopEntry(exe, s, icon))
                f.setExecutable(true)
                if (dir != linuxMenu()) runCatching { ProcessBuilder("gio", "set", f.path, "metadata::trusted", "true").start().waitFor() }
                true
            }.getOrDefault(false)
        }
    }

    /** Takes away the shortcuts made here (only those: the files carry their mark); what to tell you. */
    fun removeAll(): String {
        if (linux) {
            val gone = removeLinux(listOf(linuxMenu(), linuxDesktop()))
            return if (gone == 0) "There were no shortcuts to take away" else "Took away $gone shortcut file${if (gone != 1) "s" else ""}"
        }
        if (windows) return if (Startup.ps(DesktopWidgetRules.windowsRemoveScript(listOf(WIN_MENU, WIN_DESKTOP)))) "Took the shortcuts away"
            else "Windows didn't take them away"
        return "Shortcuts are made on Windows and Linux"
    }

    fun removeLinux(dirs: List<File>): Int = dirs.sumOf { dir ->
        dir.listFiles()?.count { f ->
            f.name.startsWith(DesktopWidgetRules.LINUX_PREFIX) && f.name.endsWith(".desktop") &&
                runCatching { DesktopWidgetRules.MARK in f.readText() && f.delete() }.getOrDefault(false)
        } ?: 0
    }
}
