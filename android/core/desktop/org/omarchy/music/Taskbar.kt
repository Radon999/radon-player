package org.omarchy.music

import org.omarchy.music.DesktopWidgetRules.Taskbar as Rules
import java.io.File

/** The taskbar on Linux (Make desktop shortcuts…' "Add to the taskbar"): Omarchy's dock, through its omarchy-dock
 *  command, or GNOME's dash, through gsettings (org.gnome.shell favorite-apps). A launcher is pinned by its desktop id,
 *  so the shortcut's launcher in the app menu has to be there first. Ours are added at the end and only ours are ever
 *  taken away: the order and everyone else's pins stay. Windows doesn't let an app pin itself: there the Start menu's
 *  shortcuts are pinned by hand (the dialog opens their folder). The rules are [DesktopWidgetRules.Taskbar]'s. */
object Taskbar {
    private val linux = System.getProperty("os.name").lowercase().contains("linux")

    /** omarchy-dock, on the PATH, or where Omarchy keeps it (a desktop launcher may start the app with a shorter PATH). */
    private val dock: File? by lazy {
        (System.getenv("PATH").orEmpty().split(File.pathSeparator) + "/usr/share/omarchy/bin")
            .map { File(it, "omarchy-dock") }.firstOrNull { it.isFile && it.canExecute() }
    }

    private fun run(vararg cmd: String): String? = runCatching {
        val pb = ProcessBuilder(*cmd).redirectErrorStream(true)
        // omarchy-dock finds its helpers beside it, on the PATH
        dock?.parentFile?.let { pb.environment()["PATH"] = it.path + File.pathSeparator + System.getenv("PATH").orEmpty() }
        val p = pb.start()
        val out = p.inputStream.bufferedReader().readText()
        if (p.waitFor() == 0) out else null
    }.getOrNull()

    /** Which taskbar this desktop has (null: none the app can add to). */
    val kind: String? by lazy {
        if (!linux) null
        else Rules.kind(dock != null, run("gsettings", "get", "org.gnome.shell", "favorite-apps") != null)
    }
    val supported: Boolean get() = kind != null

    /** (blocking) The desktop ids pinned now, in order. */
    fun pinned(): List<String> = when (kind) {
        Rules.OMARCHY -> run(dock!!.path, "pinned")?.let { Rules.parseOmarchy(it) }.orEmpty()
        Rules.GNOME -> run("gsettings", "get", "org.gnome.shell", "favorite-apps")?.let { Rules.parseGnome(it) }.orEmpty()
        else -> emptyList()
    }

    /** (blocking) Pinned at the end, or unpinned; true if it took. */
    fun pin(id: String, on: Boolean): Boolean = when (kind) {
        Rules.OMARCHY -> run(dock!!.path, if (on) "pin" else "unpin", id) != null
        Rules.GNOME -> pinned().let { now -> setGnome(if (on) Rules.withPin(now, id) else Rules.withoutPin(now, id)) }
        else -> false
    }

    /** (blocking) One of ours made again under a new id: the new one pinned in the old one's place. */
    fun replace(old: String, new: String) {
        val now = pinned()
        if (old !in now) return
        val next = Rules.replaced(now, old, new)
        when (kind) {
            Rules.OMARCHY -> run(dock!!.path, "pinned", *next.toTypedArray())
            Rules.GNOME -> setGnome(next)
        }
    }

    private fun setGnome(ids: List<String>) = run("gsettings", "set", "org.gnome.shell", "favorite-apps", Rules.gnomeValue(ids)) != null

    /** (blocking) The app's own launcher's desktop id (the one the installer made), null when it has none here. */
    fun appId(exe: String): String? {
        val home = System.getProperty("user.home") ?: ""
        val dirs = (listOf(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share") +
            System.getenv("XDG_DATA_DIRS").orEmpty().ifBlank { "/usr/local/share:/usr/share" }.split(':')).map { File(it, "applications") }
        return dirs.firstNotNullOfOrNull { d ->
            Rules.appEntry(d.listFiles()?.filter { it.name.endsWith(".desktop") }?.mapNotNull { f -> runCatching { f.name to f.readText() }.getOrNull() }.orEmpty(), exe)
        }
    }
}
