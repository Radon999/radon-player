package org.omarchy.music

import org.omarchy.music.DesktopWidgetRules.Hypr

/** Hyprland (a Linux desktop that places windows itself): a floating workspace's rule gives every new window its own
 *  size and place, so the desktop widgets (and the app's windows in a saved layout) are put back where they were kept,
 *  at their size and on their workspace: a window rule for the title, set just before the window opens, opens it
 *  straight there (no jump from the size the workspace gives), switched off again once it's there; anything Hyprland
 *  didn't take is then put right through hyprctl. Where Hyprland has one (after a drag) is read back the same way.
 *  Anywhere else this does nothing. Hyprland's own commands are written two ways (its Lua configuration's, and the
 *  older one's): the Lua one is tried first. The rules (what hyprctl says, the sums) are [DesktopWidgetRules.Hypr]'s. */
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

    /** (blocking) The Lua run in Hyprland (hyprctl eval); false where it can't (an older Hyprland). */
    private fun eval(code: String): Boolean = ctl("eval", code).orEmpty().trim().startsWith("ok")

    /** (blocking; any thread) Just before a window opens: where it goes (its kept place, else its first place; javaScale:
     *  dp to real pixels, for a size kept in dp), and a window rule that opens it there (focus: taking the keyboard as it
     *  opens, or not). null: Hyprland said nothing. */
    fun prepare(rule: String, title: String, p: DesktopWidgetRules.Place, i: Int, javaScale: Double, focus: Boolean = true): Hypr.Target? {
        val t = Hypr.target(p, monitors(), javaScale, i) ?: return null
        eval(Hypr.ruleLua(rule, title, t, focus))
        return t
    }

    /** (blocking) Once the window's open (waited for, 3 s at most): put right where the rule didn't take (or there was
     *  none), pinned or not, and the rule switched off. False: it never showed. */
    fun settle(rule: String, title: String, t: Hypr.Target, onTop: Boolean): Boolean {
        var c: Hypr.Client? = null
        val end = System.currentTimeMillis() + 3000
        while (c == null && System.currentTimeMillis() < end) { c = mine(title); if (c == null) Thread.sleep(50) }
        eval(Hypr.ruleOffLua(rule))
        if (c == null) return false
        val cmds = if (Hypr.at(c, t) && c.floating) listOfNotNull(if (onTop != c.pinned) "pin address:${c.address}" else null)
            else Hypr.dispatches(c, t.x, t.y, t.w, t.h, onTop, t.ws)
        if (cmds.isNotEmpty()) dispatch(c, cmds)
        return true
    }

    /** (blocking) A window that's open put at a kept place now (Put widgets back). */
    fun moveTo(title: String, p: DesktopWidgetRules.Place, i: Int, javaScale: Double, onTop: Boolean) {
        val c = mine(title) ?: return
        val t = Hypr.target(p, monitors(), javaScale, i) ?: return
        if (!Hypr.at(c, t) || !c.floating || onTop != c.pinned) dispatch(c, Hypr.dispatches(c, t.x, t.y, t.w, t.h, onTop, t.ws))
    }

    /** (blocking) Pinned (above the others, on every workspace) or not. */
    fun pin(title: String, on: Boolean) { val c = mine(title) ?: return; if (c.pinned != on) dispatch(c, listOf("pin address:${c.address}")) }

    /** (blocking) The window as Hyprland has it now, to keep: p with its place, its size in Hyprland's pixels, its
     *  workspace and its monitor. null: not on the screen. */
    fun kept(title: String, p: DesktopWidgetRules.Place): DesktopWidgetRules.Place? {
        val c = mine(title) ?: return null
        val mon = monitors().firstOrNull { it.id == c.monitor }?.name.orEmpty()
        return p.copy(x = c.x, y = c.y, hw = c.w, hh = c.h, ws = c.workspace, mon = mon, hypr = true)
    }
}
