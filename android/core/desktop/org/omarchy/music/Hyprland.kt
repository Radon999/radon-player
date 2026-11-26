package org.omarchy.music

import org.omarchy.music.DesktopWidgetRules.Hypr

/** Hyprland (a Linux desktop that places windows itself): a floating workspace's rule gives every new window its own
 *  size and place, so the desktop widgets are put back where they were kept, at their size, through hyprctl once they're
 *  on the screen; where Hyprland put one (after a drag) is read back the same way. Anywhere else this does nothing.
 *  Hyprland's own commands are written two ways (its Lua configuration's, and the older one's): the Lua one is tried
 *  first. The rules (what hyprctl says, the sums) are [DesktopWidgetRules.Hypr]'s. */
object Hyprland {
    val on: Boolean = !System.getenv("HYPRLAND_INSTANCE_SIGNATURE").isNullOrBlank()
    private val pid = ProcessHandle.current().pid()
    @Volatile private var lua: Boolean? = null

    private fun ctl(vararg a: String): String? = runCatching {
        val p = ProcessBuilder(listOf("hyprctl") + a).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS); out
    }.getOrNull()

    fun clients(): List<Hypr.Client> = Hypr.clients(ctl("clients", "-j").orEmpty())
    fun monitors(): List<Hypr.Monitor> = Hypr.monitors(ctl("monitors", "-j").orEmpty())

    /** Our window of that title, as Hyprland has it (null: not on the screen yet). */
    fun mine(title: String): Hypr.Client? = Hypr.find(clients(), pid, title)

    /** Hyprland's commands, run (the Lua way first; the older way if Hyprland doesn't take that). */
    private fun dispatch(c: Hypr.Client, cmds: List<String>) {
        if (lua != false) {
            val ok = cmds.all { ctl("dispatch", Hypr.lua(it, c.pinned)).orEmpty().trim().startsWith("ok") }
            if (ok) { lua = true; return }
            if (lua == true) return
            lua = false
        }
        cmds.forEach { cmd -> ctl("dispatch", *cmd.split(' ', limit = 2).toTypedArray()) }
    }

    /** (blocking; any thread) A widget window just shown: waited for (3 s at most), then floated, sized (dp, as Java
     *  draws them at javaScale) and put where it was kept (x, y: Hyprland's; null, or on a monitor gone since: its first
     *  place, i its place in the list), pinned or not. False: it never showed. */
    fun place(title: String, x: Int?, y: Int?, wDp: Int, hDp: Int, i: Int, javaScale: Double, onTop: Boolean): Boolean {
        var c: Hypr.Client? = null
        val end = System.currentTimeMillis() + 3000
        while (c == null && System.currentTimeMillis() < end) { c = mine(title); if (c == null) Thread.sleep(80) }
        if (c == null) return false
        val mons = monitors()
        val kept = x != null && y != null && Hypr.onSomeMonitor(x, y, mons)
        val mon = (if (kept) mons.firstOrNull { x!! >= it.x && x < it.x + it.logicalW && y!! >= it.y && y < it.y + it.logicalH } else null)
            ?: mons.firstOrNull { it.id == c.monitor } ?: mons.firstOrNull { it.focused } ?: mons.firstOrNull() ?: return false
        val w = Hypr.logical(wDp, javaScale, mon.scale); val h = Hypr.logical(hDp, javaScale, mon.scale)
        val (px, py) = if (kept) x!! to y!! else Hypr.firstPlace(mon, w, h, i)
        dispatch(c, Hypr.dispatches(c, px, py, w, h, onTop))
        return true
    }

    /** (blocking) Pinned (above the others, on every workspace) or not. */
    fun pin(title: String, on: Boolean) { val c = mine(title) ?: return; if (c.pinned != on) dispatch(c, listOf("pin address:${c.address}")) }

    /** (blocking) Where Hyprland has the window now (its layout's x and y), to keep. */
    fun where(title: String): Pair<Int, Int>? = mine(title)?.let { it.x to it.y }
}
