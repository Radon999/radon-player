package org.omarchy.music

/** Start with Windows (Settings › General, the PC): Radon Player starts in the tray when you sign in (the Run key). */
object Startup {
    private const val KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NAME = "Radon Player Next"
    val supported = System.getProperty("os.name").lowercase().contains("win")

    /** The program to start: the installed launcher (Radon Player Next.exe), not the Java inside it. */
    private fun exe(): String? = ProcessHandle.current().info().command().orElse(null)?.takeIf { it.lowercase().endsWith(".exe") && !it.lowercase().endsWith("java.exe") && !it.lowercase().endsWith("javaw.exe") }

    private fun reg(vararg a: String): String = runCatching {
        val p = ProcessBuilder("reg", *a).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText().also { p.waitFor() }
    }.getOrDefault("")

    fun isOn(): Boolean = supported && NAME in reg("query", KEY, "/v", NAME)

    fun set(on: Boolean): Boolean {
        if (!supported) return false
        if (!on) { reg("delete", KEY, "/v", NAME, "/f"); return true }
        val e = exe() ?: return false
        reg("add", KEY, "/v", NAME, "/t", "REG_SZ", "/d", "\"$e\" --tray", "/f")
        return isOn()
    }
}
