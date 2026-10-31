package org.omarchy.music

/** Start with Windows (Settings › General, the PC): Radon Player starts in the tray when you sign in (the Run key). */
object Startup {
    private const val KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NAME = "Radon Player Next"
    val supported = System.getProperty("os.name").lowercase().contains("win")

    /** The program to start: the installed launcher (Radon Player Next.exe), not the Java inside it. It's the program
     *  this is running as; else the .exe beside the app's own Java (the install folder). */
    private fun exe(): String? {
        fun ok(c: String?) = c?.lowercase()?.let { it.endsWith(".exe") && !it.endsWith("java.exe") && !it.endsWith("javaw.exe") } == true
        ProcessHandle.current().info().command().orElse(null)?.takeIf { ok(it) }?.let { return it }
        val home = System.getProperty("java.home")?.let { java.io.File(it) } ?: return null   // <install>\runtime
        val dir = home.parentFile ?: return null
        return dir.listFiles()?.filter { ok(it.path) && "uninstall" !in it.name.lowercase() }?.maxByOrNull { if ("radon" in it.name.lowercase()) 1 else 0 }?.path
    }

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

    fun isOn(): Boolean = supported && NAME in reg("query", KEY, "/v", NAME)

    fun set(on: Boolean): Boolean {
        if (!supported) return false
        if (!on) { reg("delete", KEY, "/v", NAME, "/f"); return true }
        val e = exe() ?: return false
        // Before, the program's path went to reg.exe with quotes inside one argument, which Windows split apart: the
        // setting never took. PowerShell writes it whole.
        val value = "\"$e\" --tray".replace("'", "''")
        ps("New-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Run' -Name '$NAME' -Value '$value' -PropertyType String -Force | Out-Null")
        return isOn()
    }
}
