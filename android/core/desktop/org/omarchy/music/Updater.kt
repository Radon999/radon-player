package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** New releases on GitHub, for the PC: Check looks (and opening Settings, if the last look is old); Update opens the
 *  release's page, where the PC's installer is. */
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

    val version: String get() = appVersion

    private fun parts(v: String) = Regex("\\d+").findAll(v).take(3).map { it.value.toInt() }.toList() + listOf(0, 0, 0)
    private fun newer(a: String, b: String): Boolean {
        if (Regex("\\d").find(b) == null) return false   // a build of the code as it is ("next"): nothing to compare
        val x = parts(a); val y = parts(b)
        for (i in 0 until 3) if (x[i] != y[i]) return x[i] > y[i]
        return false
    }

    fun check(quiet: Boolean = true) {
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
            Music.accountsVersion++
        }
    }

    fun checkSoon() { if (System.currentTimeMillis() - checkedAt > 600_000) check() }

    fun install() {
        val url = page ?: return
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
        Music.toast("The release's page is open: run its installer (close Radon Player first)")
    }

    fun askAgain(activity: Any?) = install()
}
