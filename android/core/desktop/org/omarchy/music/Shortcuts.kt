package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/** Make desktop shortcuts… (Settings › General, the PC): one-click shortcuts that start the app with a command, so one
 *  click plays a playlist, starts the DJ or opens Wrapped. The app hands the command to the one already running
 *  (Instance), or starts with it. Each has a picture of what it does (ui.ShortcutIcons) and a plain name.
 *  Linux: desktop entries in the app menu (~/.local/share/applications) and, on desktops that show files on the desktop,
 *  there too (Hyprland, Sway and the like show none, and a file manager opens one there as text: not made there). Windows:
 *  shortcuts (.lnk) in the Start menu (a folder of their own) and, if asked, on the desktop, made by PowerShell. Every one
 *  made is kept, and the Shortcuts widget shows them as tiles: the one-click shortcuts on the screen where the desktop has
 *  no icons. What goes into the files is [DesktopWidgetRules]'. */
object Shortcuts {
    private val os = System.getProperty("os.name").lowercase()
    private val windows = os.contains("win")
    private val linux = os.contains("linux")
    val supported = windows || linux
    /** Where the shortcuts go besides the desktop, as the switch names it. */
    val menuName = if (windows) "Start menu" else "app menu"
    /** Whether files on the desktop show here (Windows; a Linux desktop that draws them). */
    val desktopWorks: Boolean = windows || (linux && DesktopWidgetRules.desktopIconsWork(System.getenv()))

    /** The shortcuts made (the Shortcuts widget's), kept on this PC only (pc_: never synced). */
    const val LIST = "pc_shortcuts"
    fun made(): List<DesktopWidgetRules.Shortcut> = DesktopWidgetRules.decodeList(Prefs.text(LIST))

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

    /** (off the main thread) Makes them where asked: the menu, the desktop (where it shows files), and keeps them for the
     *  Shortcuts widget (widget: it's opened too). What to tell you. */
    fun make(list: List<DesktopWidgetRules.Shortcut>, menu: Boolean, desktop: Boolean, widget: Boolean): String {
        if (list.isEmpty()) return "Pick at least one shortcut"
        val onDesktop = desktop && desktopWorks
        if (!menu && !onDesktop && !widget) return "Pick where they go"
        keep(list, widget)
        val n = list.size
        val what = "$n shortcut${if (n != 1) "s" else ""}"
        val where = listOfNotNull(if (menu) "the $menuName" else null, if (onDesktop) "the desktop" else null,
            if (widget) "the Shortcuts widget" else null).joinToString(" and ")
        if (!menu && !onDesktop) return "Made $what on $where"
        if (linux) {
            val exe = Startup.linuxExe() ?: return if (widget) "Made $what on the Shortcuts widget (only the installed app can make menu shortcuts)"
                else "Couldn't: only the installed app can make shortcuts"
            val dirs = listOfNotNull(if (menu) linuxMenu() else null, if (onDesktop) linuxDesktop() else null)
            val fallback = linuxIcon(exe)
            val icons = list.associate { it.key to (org.omarchy.music.ui.ShortcutIcons.png(it, fresh = true)?.path ?: fallback) }
            return if (writeLinux(exe, list, dirs) { icons[it.key] } == list.size * dirs.size) "Made $what on $where"
                else "Couldn't make them all: check the folders can be written to"
        }
        if (windows) {
            val exe = Startup.exe() ?: return "Couldn't: only the installed app can make shortcuts"
            val folders = listOfNotNull(if (menu) WIN_MENU else null, if (onDesktop) WIN_DESKTOP else null)
            val icons = list.mapNotNull { s -> org.omarchy.music.ui.ShortcutIcons.ico(s, fresh = true)?.let { s.key to it.path } }.toMap()
            return if (Startup.ps(DesktopWidgetRules.windowsScript(exe, list, folders, icons))) "Made $what on $where" else "Windows didn't make them"
        }
        return "Shortcuts are made on Windows and Linux"
    }

    /** The ones made, kept with those before (on the app's thread), and the Shortcuts widget opened if asked. */
    private fun keep(list: List<DesktopWidgetRules.Shortcut>, open: Boolean) {
        Music.scope.launch(Dispatchers.Main) {
            Prefs.setText(LIST, DesktopWidgetRules.encodeList(DesktopWidgetRules.merged(made(), list)))
            if (open) org.omarchy.music.ui.DesktopWidgets.open("shortcuts", true)
        }
    }

    /** Linux: each shortcut's desktop entry written into each folder (one made before for the same command, under another
     *  name, taken away first); how many were written. On the desktop the file is marked as a program to run (and as
     *  trusted, for GNOME), so a double-click starts it rather than opening it. */
    fun writeLinux(exe: String, list: List<DesktopWidgetRules.Shortcut>, dirs: List<File>, icon: (DesktopWidgetRules.Shortcut) -> String?): Int = dirs.sumOf { dir ->
        list.count { s ->
            runCatching {
                dir.mkdirs()
                val text = DesktopWidgetRules.desktopEntry(exe, s, icon(s))
                val exec = text.lines().first { it.startsWith("Exec=") }
                val f = File(dir, DesktopWidgetRules.linuxFile(s))
                ours(dir).filter { it.name != f.name && runCatching { exec in it.readText().lines() }.getOrDefault(false) }.forEach { old ->
                    old.delete()
                    // On the taskbar under its old name: the new one takes its place there
                    if (dir == linuxMenu() && Taskbar.supported) Taskbar.replace(DesktopWidgetRules.Taskbar.id(old.name), DesktopWidgetRules.Taskbar.id(f.name))
                }
                writeAtomic(f, text)
                f.setExecutable(true)
                if (dir != linuxMenu()) runCatching { ProcessBuilder("gio", "set", f.path, "metadata::trusted", "true").start().waitFor() }
                true
            }.getOrDefault(false)
        }
    }

    /** The launchers made here in a folder (only those: they carry the mark). */
    private fun ours(dir: File): List<File> = dir.listFiles()?.filter { f ->
        f.name.startsWith(DesktopWidgetRules.LINUX_PREFIX) && f.name.endsWith(".desktop") &&
            runCatching { DesktopWidgetRules.MARK in f.readText().lines() }.getOrDefault(false)
    }.orEmpty()

    /** Takes away the shortcuts made here (only those: the files carry their mark), off the taskbar too, and empties
     *  the Shortcuts widget; what to tell you. */
    fun removeAll(): String {
        Music.scope.launch(Dispatchers.Main) { Prefs.setText(LIST, "") }
        if (linux) {
            if (Taskbar.supported) runCatching {
                val pinned = Taskbar.pinned()
                ours(linuxMenu()).map { DesktopWidgetRules.Taskbar.id(it.name) }.filter { it in pinned }.forEach { Taskbar.pin(it, false) }
            }
            val gone = removeLinux(listOf(linuxMenu(), linuxDesktop()))
            return if (gone == 0) "Took the shortcuts away" else "Took away $gone shortcut file${if (gone != 1) "s" else ""}"
        }
        if (windows) return if (Startup.ps(DesktopWidgetRules.windowsRemoveScript(listOf(WIN_MENU, WIN_DESKTOP)))) "Took the shortcuts away"
            else "Windows didn't take them away"
        return "Shortcuts are made on Windows and Linux"
    }

    fun removeLinux(dirs: List<File>): Int = dirs.sumOf { dir -> ours(dir).count { it.delete() } }

    /** A shortcut's desktop id (its launcher's name), as the taskbar pins it. */
    fun taskbarId(s: DesktopWidgetRules.Shortcut): String = DesktopWidgetRules.Taskbar.id(DesktopWidgetRules.linuxFile(s))

    /** (blocking) The app's own launcher's desktop id, when the app is installed (null: run from elsewhere). */
    fun appTaskbarId(): String? = if (!linux) null else Startup.linuxExe()?.let { Taskbar.appId(it) }

    /** (off the main thread) Linux: the shortcuts put on the taskbar or taken off it as asked (on: its launcher in the
     *  app menu is made first, if it isn't there, as the taskbar pins launchers), and the app itself (app: null, left
     *  as it is). What to tell you ("" for nothing done). */
    fun taskbar(wanted: Map<DesktopWidgetRules.Shortcut, Boolean>, app: Boolean?, appId: String?): String {
        if (!Taskbar.supported) return ""
        val pinned = Taskbar.pinned()
        var added = 0; var taken = 0; var failed = 0
        val toPin = wanted.filter { (s, on) -> on && taskbarId(s) !in pinned }.keys.toList()
        if (toPin.isNotEmpty()) {
            val missing = toPin.filter { !File(linuxMenu(), DesktopWidgetRules.linuxFile(it)).isFile }
            if (missing.isNotEmpty()) {
                val exe = Startup.linuxExe() ?: return "Couldn't: only the installed app can put shortcuts on the taskbar"
                writeLinux(exe, missing, listOf(linuxMenu())) { org.omarchy.music.ui.ShortcutIcons.png(it, fresh = true)?.path ?: linuxIcon(exe) }
                keep(missing, open = false)
            }
            toPin.forEach { if (Taskbar.pin(taskbarId(it), true)) added++ else failed++ }
        }
        wanted.filter { (s, on) -> !on && taskbarId(s) in pinned }.keys.forEach { if (Taskbar.pin(taskbarId(it), false)) taken++ else failed++ }
        if (app != null && appId != null && (appId in pinned) != app) { if (Taskbar.pin(appId, app)) { if (app) added++ else taken++ } else failed++ }
        return listOfNotNull(if (added > 0) "put $added on the taskbar" else null, if (taken > 0) "took $taken off the taskbar" else null,
            if (failed > 0) "$failed couldn't be changed on the taskbar" else null).joinToString(", ").replaceFirstChar { it.uppercase() }
    }

    /** Windows: the Start menu's folder of the shortcuts, opened (to pin one to the taskbar by hand). */
    fun openStartFolder() {
        if (!windows) return
        val dir = File(System.getenv("APPDATA") ?: return, "Microsoft\\Windows\\Start Menu\\Programs\\Radon Player shortcuts").apply { mkdirs() }
        runCatching { ProcessBuilder("explorer.exe", dir.path).start() }
    }

    /** (at the start, off the main thread) On a Linux desktop that shows no files on the desktop, the shortcuts made
     *  there before (which only opened as text) are taken away. The app menu's made before plain names ("Radon Player:
     *  Lyrics") are made again as they are now (plain name, their own picture) and kept for the Shortcuts widget. */
    fun tidy() {
        if (!linux) return
        if (!desktopWorks) runCatching { removeLinux(listOf(linuxDesktop())) }
        runCatching { upgrade(linuxMenu()) }
    }

    /** The old launchers in a folder, written again the way they're made now (same program, same command). */
    fun upgrade(dir: File): Int {
        val again = ours(dir).mapNotNull { f ->
            val lines = f.readText().lines()
            val words = lines.firstNotNullOfOrNull { DesktopWidgetRules.execWords(it) } ?: return@mapNotNull null
            val name = lines.firstOrNull { it.startsWith("Name=") }?.removePrefix("Name=") ?: return@mapNotNull null
            DesktopWidgetRules.renamed(name, words.drop(1))?.let { words.first() to it }
        }
        if (again.isEmpty()) return 0
        val made = again.groupBy({ it.first }, { it.second }).entries.sumOf { (exe, list) ->
            writeLinux(exe, list, listOf(dir)) { org.omarchy.music.ui.ShortcutIcons.png(it, fresh = true)?.path ?: linuxIcon(exe) }
        }
        keep(again.map { it.second }, open = false)
        return made
    }
}
