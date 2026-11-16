package org.omarchy.music

/** Start with Windows or Linux (Settings › General, the PC): Radon Player starts in the tray when you sign in. On
 *  Windows it's the Run key; on Linux a file in your autostart folder (~/.config/autostart), which the desktops read. */
object Startup {
    private const val KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NAME = "Radon Player"
    private const val OLD = "Radon Player Next"   // the test builds' name, before 3.0
    private val os = System.getProperty("os.name").lowercase()
    private val windows = os.contains("win")
    private val linux = os.contains("linux")
    val supported = windows || linux
    /** What you sign in to, as the setting names it: "Windows" or "Linux". */
    val system = if (windows) "Windows" else if (linux) "Linux" else ""

    /** The program to start: the installed launcher (Radon Player.exe), not the Java inside it. It's the program
     *  this is running as; else the .exe beside the app's own Java (the install folder). */
    private fun exe(): String? {
        fun ok(c: String?) = c?.lowercase()?.let { it.endsWith(".exe") && !it.endsWith("java.exe") && !it.endsWith("javaw.exe") } == true
        ProcessHandle.current().info().command().orElse(null)?.takeIf { ok(it) }?.let { return it }
        val home = System.getProperty("java.home")?.let { java.io.File(it) } ?: return null   // <install>\runtime
        val dir = home.parentFile ?: return null
        return dir.listFiles()?.filter { ok(it.path) && "uninstall" !in it.name.lowercase() }?.maxByOrNull { if ("radon" in it.name.lowercase()) 1 else 0 }?.path
    }

    /** On Linux: the installed launcher (/opt/radon-player/bin/radon-player), not the Java inside it: the program this
     *  is running as, else the one in the bin folder beside the app's own Java (<install>/lib/runtime). */
    private fun linuxExe(): String? {
        ProcessHandle.current().info().command().orElse(null)?.takeIf { StartupRules.launcher(it) && java.io.File(it).canExecute() }?.let { return it }
        val install = System.getProperty("java.home")?.let { java.io.File(it) }?.parentFile?.parentFile ?: return null
        return java.io.File(install, "bin").listFiles()?.filter { it.isFile && it.canExecute() }?.maxByOrNull { if ("radon" in it.name.lowercase()) 1 else 0 }?.path
    }

    private val autostart: java.io.File get() =
        java.io.File(StartupRules.autostartDir(System.getenv("XDG_CONFIG_HOME"), System.getProperty("user.home") ?: ""), "radon-player.desktop")

    /** A PowerShell command run (sent encoded, so paths with spaces and quotes reach it as they are). */
    private fun ps(cmd: String): Boolean = runCatching {
        val enc = java.util.Base64.getEncoder().encodeToString(cmd.toByteArray(Charsets.UTF_16LE))
        val p = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", enc).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText(); p.waitFor() == 0
    }.getOrDefault(false)

    private fun reg(vararg a: String): String = runCatching {
        val p = ProcessBuilder("reg", *a).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText().also { p.waitFor() }
    }.getOrDefault("")

    fun isOn(): Boolean = if (windows) NAME in reg("query", KEY, "/v", NAME) else linux && runCatching { autostart.isFile }.getOrDefault(false)

    /** Started with Windows under the test builds' name: moved to this one (its program is gone after the update). */
    fun moveOld() {
        if (!windows || OLD !in reg("query", KEY, "/v", OLD)) return
        reg("delete", KEY, "/v", OLD, "/f")
        set(true)
    }

    fun set(on: Boolean): Boolean {
        if (!supported) return false
        if (linux) return runCatching {
            if (!on) { autostart.delete(); return@runCatching true }
            val e = linuxExe() ?: return@runCatching false
            autostart.parentFile?.mkdirs()
            autostart.writeText(StartupRules.desktopEntry(e))
            isOn()
        }.getOrDefault(false)
        if (!on) { reg("delete", KEY, "/v", NAME, "/f"); return true }
        val e = exe() ?: return false
        // Before, the program's path went to reg.exe with quotes inside one argument, which Windows split apart: the
        // setting never took. PowerShell writes it whole.
        val value = "\"$e\" --tray".replace("'", "''")
        ps("New-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' -Name '$NAME' -Value '$value' -PropertyType String -Force | Out-Null")
        return isOn()
    }
}
