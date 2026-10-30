package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/** New releases on GitHub, for the PC (as the Python app): it looks at start, every 12 hours and when Settings opens;
 *  Update downloads the release's installer, runs it and closes the app so it can be replaced (else the release's
 *  page opens). */
object Updater {
    private const val REPO = "Radon999/radon-player"
    var latest by mutableStateOf<String?>(null)
        private set
    var state by mutableStateOf("")
    var checking by mutableStateOf(false)
        private set
    var checkError by mutableStateOf("")
        private set
    private var checkedAt = 0L
    private var page: String? = null
    private var installer: String? = null   // the release's .msi

    /** Look now (quietly) and every 12 hours. */
    fun startTimer() {
        Thread({
            Thread.sleep(20_000)
            while (true) { Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { check(announce = true) }; Thread.sleep(12 * 3600_000L) }
        }, "update-check").apply { isDaemon = true }.start()
    }

    val version: String get() = appVersion

    private fun parts(v: String) = Regex("\\d+").findAll(v).take(3).map { it.value.toInt() }.toList() + listOf(0, 0, 0)
    private fun newer(a: String, b: String): Boolean {
        if (Regex("\\d").find(b) == null) return false   // a build of the code as it is ("next"): nothing to compare
        val x = parts(a); val y = parts(b)
        for (i in 0 until 3) if (x[i] != y[i]) return x[i] > y[i]
        return false
    }

    fun check(quiet: Boolean = true, announce: Boolean = false) {
        if (checking) return
        checking = true
        Music.bg({
            httpJson("https://api.github.com/repos/$REPO/releases/latest",
                mapOf("User-Agent" to "radon-player-pc/$version", "Accept" to "application/vnd.github+json"))
        }) { rel, err ->
            checking = false
            checkedAt = System.currentTimeMillis()
            checkError = err?.let { "couldn't check: ${it.message?.take(60)}" } ?: ""
            Music.accountsVersion++
            val tag = rel["tag_name"].str ?: return@bg
            if (!newer(tag, version)) { if (!quiet) Music.toast("Radon Player $version is the newest"); return@bg }
            latest = tag.removePrefix("v"); page = rel["html_url"].str
            installer = rel["assets"].list.firstOrNull { (it["name"].str ?: "").endsWith(".msi", true) }?.get("browser_download_url").str
            if (announce) Music.toast("Radon Player ${latest} is out: Settings › Update")
            Music.accountsVersion++
        }
    }

    fun checkSoon() { if (System.currentTimeMillis() - checkedAt > 600_000) check() }

    fun install() {
        val msi = installer
        val windows = System.getProperty("os.name").lowercase().contains("win")
        if (msi == null || !windows) {
            val url = page ?: return
            runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
            Music.toast("The release's page is open: run its installer (close Radon Player first)")
            return
        }
        state = "Downloading the update…"
        Music.bg({
            val f = java.io.File(System.getProperty("java.io.tmpdir"), "Radon-Player-${latest ?: "update"}.msi")
            java.net.URL(msi).openStream().use { inp -> f.outputStream().use { inp.copyTo(it) } }
            f
        }) { f, err ->
            if (f == null) { state = ""; Music.toast("Couldn't download the update: ${err?.message?.take(80)}"); return@bg }
            state = "Installing…"
            // The installer replaces the app; Radon Player closes so it can (start it again afterwards)
            runCatching { ProcessBuilder("msiexec", "/i", f.path, "/passive").start() }
                .onFailure { state = ""; Music.toast("Couldn't start the installer: ${it.message?.take(80)}"); return@bg }
            closeApp(null)
        }
    }

    fun askAgain(activity: Any?) = install()
}
