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
            Thread.sleep(5_000)
            // At start: always says how it went (an update, or that this is the newest); then quietly every 12 hours
            var first = true
            while (true) { val q = !first; first = false
                Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { check(quiet = q, announce = true) }; Thread.sleep(12 * 3600_000L) }
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
        val mine = ++checkNo
        val agent = "radon-player-pc/$version"
        val os = System.getProperty("os.name")
        // On a thread of its own, and with an end to it (as on the phone): it never waits for the app's other
        // background work, and a network that stalls can't leave "checking…" up for good
        Thread({
            var err: Throwable? = null
            var tag: String? = null; var pageUrl: String? = null; var file: String? = null
            try {
                val rel = UpdateRules.jsonWithin("https://api.github.com/repos/$REPO/releases/latest",
                    mapOf("User-Agent" to agent, "Accept" to "application/vnd.github+json"))
                tag = rel["tag_name"].str; pageUrl = rel["html_url"].str
                // This system's installer among the release's files: the .msi on Windows, the .deb on Linux
                val name = StartupRules.installer(rel["assets"].list.mapNotNull { it["name"].str }, os)
                file = rel["assets"].list.firstOrNull { name != null && it["name"].str == name }?.get("browser_download_url").str
            } catch (e: Throwable) {
                err = e
                // GitHub's list wasn't had (it answers only so many times an hour): the version from where its "latest
                // release" page sends on to, and the installer where every release has it
                runCatching { UpdateRules.latestTag(REPO) }.getOrNull()?.let { t ->
                    tag = t; pageUrl = "https://github.com/$REPO/releases/tag/$t"
                    file = UpdateRules.pcInstallerName(t, os)?.let { UpdateRules.assetUrl(REPO, t, it) }; err = null
                }
            }
            val t = tag; val p = pageUrl; val f = file; val e = err
            Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { if (mine == checkNo) checked(quiet, announce, t, p, f, e) }
        }, "update-look").apply { isDaemon = true }.start()
        Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            kotlinx.coroutines.delay(75_000)
            if (checking && mine == checkNo) { checkNo++; checked(quiet, announce, null, null, null, java.io.IOException("no answer from GitHub")) }
        }
    }

    private var checkNo = 0   // which look this is: an answer to an older one (given up on) is dropped

    private fun checked(quiet: Boolean, announce: Boolean, tag: String?, pageUrl: String?, file: String?, err: Throwable?) {
        checking = false
        checkedAt = System.currentTimeMillis()
        checkError = err?.let { "couldn't check: ${it.message?.take(60)}" } ?: ""
        Music.accountsVersion++
        if (err != null && !quiet) Music.toast("Couldn't check for an update (no internet?)")
        if (tag == null) return
        if (!newer(tag, version)) { if (!quiet) Music.toast("No update: Radon Player $version is the newest"); return }
        latest = tag.removePrefix("v"); page = pageUrl
        installer = file
        if (announce) Music.toast("Radon Player ${latest} is out: Settings › Update")
        Music.accountsVersion++
    }

    fun checkSoon() { if (System.currentTimeMillis() - checkedAt > 600_000) check() }

    fun install() {
        val msi = installer
        val os = System.getProperty("os.name").lowercase()
        val windows = os.contains("win")
        if (msi != null && os.contains("linux")) { installOnLinux(msi); return }
        if (msi == null || !windows) {
            val url = page ?: return
            Opening.link(url)
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

    /** Linux: the release's .deb is downloaded to your Downloads folder and opened with the system's own installer
     *  (it asks for your password; the app can't install over itself there). If nothing opens it, its folder is shown. */
    private fun installOnLinux(url: String) {
        // A copy of the app in your own folders (unpacked from the .deb, as on a Linux with no .deb installer) is
        // updated by the app itself: the new version unpacked beside it, swapped in as the app closes, and started.
        val root = UpdateRules.ownCopyRoot(runCatching { ProcessHandle.current().info().command().orElse(null) }.getOrNull()) { runCatching { java.io.File(it).canWrite() }.getOrDefault(false) }
        if (root != null) { updateOwnCopy(url, root); return }
        state = "Downloading the update…"
        Music.bg({
            val dir = java.io.File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory } ?: java.io.File(System.getProperty("java.io.tmpdir"))
            val f = java.io.File(dir, "radon-player_${latest ?: "update"}_amd64.deb")
            java.net.URI(url).toURL().openStream().use { inp -> f.outputStream().use { inp.copyTo(it) } }
            f
        }) { f, err ->
            state = ""
            if (f == null) { Music.toast("Couldn't download the update: ${err?.message?.take(80)}"); return@bg }
            val opened = Opening.path(f)
            if (!opened) Opening.path(f.parentFile)
            Music.toast("The update is in ${f.parentFile.name}: install ${f.name} (close Radon Player first)")
        }
    }

    /** (Linux, a copy in your own folders) Download the .deb, unpack it beside the app, and hand over to a small script
     *  that swaps it in once the app has closed and starts it again. The version in use is kept once, as <folder>.old.
     *  Nothing is touched until the new version is unpacked whole. */
    private fun updateOwnCopy(url: String, root: String) {
        state = "Downloading the update…"
        Music.bg({
            val work = java.io.File("$root.update-work").apply { deleteRecursively(); mkdirs() }
            val fresh = java.io.File("$root.update").apply { deleteRecursively() }
            val deb = java.io.File(work, "update.deb")
            java.net.URI(url).toURL().openStream().use { inp -> deb.outputStream().use { inp.copyTo(it) } }
            fun run(vararg cmd: String): Boolean = runCatching {
                val p = ProcessBuilder(*cmd).directory(work).redirectErrorStream(true).start()
                p.inputStream.readBytes()
                p.waitFor(180, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0
            }.getOrDefault(false)
            // A .deb is an archive holding data.tar.*: with bsdtar (libarchive), else with ar and tar
            val opened = run("bsdtar", "-xf", "update.deb") || run("ar", "x", "update.deb")
            val data = work.listFiles()?.firstOrNull { it.name.startsWith("data.tar") }
            if (!opened || data == null) error("this system can't unpack the update (it needs bsdtar, or ar and tar)")
            java.io.File(work, "data").mkdirs()
            if (!(run("bsdtar", "-xf", data.name, "-C", "data") || run("tar", "-xf", data.name, "-C", "data"))) error("the update couldn't be unpacked")
            val got = java.io.File(work, "data/opt/radon-player")
            if (!java.io.File(got, "bin/Radon Player").isFile) error("the update isn't what was expected")
            if (!got.renameTo(fresh)) error("the update couldn't be put beside the app")
            val script = java.io.File(work, "swap.sh")
            script.writeText(UpdateRules.swapScript(ProcessHandle.current().pid(), root, fresh.path))
            script
        }) { script, err ->
            if (script == null) { state = ""; Music.toast("Couldn't update: ${err?.message?.take(120)}"); return@bg }
            state = "Installing…"
            runCatching { ProcessBuilder("sh", script.path).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start() }
                .onFailure { state = ""; Music.toast("Couldn't start the update: ${it.message?.take(80)}"); return@bg }
            closeApp(null)
        }
    }

    fun askAgain(activity: Any?) = install()
}
