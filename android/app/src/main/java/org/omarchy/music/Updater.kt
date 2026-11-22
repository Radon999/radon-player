package org.omarchy.music

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File

/** New releases on GitHub. It looks at start, every 12 hours and when you open the accounts
 *  menu (Check looks now); Update downloads the release's APK and hands it to Android's
 *  installer, which asks you. Android shows that question only while the app is on screen,
 *  so one that came while you were away is kept, and asked when you come back (or Install). */
@SuppressLint("StaticFieldLeak")
object Updater {
    private const val REPO = "Radon999/radon-player"   // updates come from this one's releases
    private lateinit var ctx: Context
    var latest by mutableStateOf<String?>(null)   // the newer release ("1.2.0"), once there is one
        private set
    var state by mutableStateOf("")               // "", "working", "confirm" (Android's question is waiting), or what went wrong
    var checking by mutableStateOf(false)
        private set
    var checkError by mutableStateOf("")
        private set
    private var checkedAt = 0.0
    private var apk: String? = null
    private var confirm: Intent? = null           // Android's "install this update?", not shown yet
    private const val TAG = "omarchy-music"

    /** The version installed, as Android has it. Not BuildConfig.VERSION_NAME: Kotlin copies that
     *  into the code that reads it, and a build that doesn't recompile that code keeps the old one
     *  (1.1.1 went out calling itself 1.1.0, so it offered itself as an update forever). */
    val version: String by lazy { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0" }

    fun init(c: Context) {
        if (::ctx.isInitialized) return
        ctx = c.applicationContext
        // At start: always says how it went (an update, or that this is the newest); then quietly every 12 hours
        Music.scope.launch { delay(4_000); check(quiet = false); while (true) { delay(12 * 3600_000L); check() } }
    }

    private fun parts(v: String) = Regex("\\d+").findAll(v).take(3).map { it.value.toInt() }.toList() + listOf(0, 0, 0)
    private fun newer(a: String, b: String): Boolean {
        val x = parts(a); val y = parts(b)
        for (i in 0 until 3) if (x[i] != y[i]) return x[i] > y[i]
        return false
    }

    private val APK_NAMES = listOf("RadonPlayer.apk", "OmarchyMusic.apk")
    /** The release's file for Radon Player Next, the app kept on the phone: every release has it from 3.2.19. */
    private const val NEXT_NAME = "RadonPlayer-Next.apk"
    /** The newest Radon Player Next build (the test app), built from the same code as each release. */
    private const val NEXT_APK = "https://raw.githubusercontent.com/$REPO/android-work/ci/next/RadonPlayer-Next.apk"

    /** Look for a newer release (quiet: no message when there's none, as at start). */
    private var checkNo = 0   // which look this is: an answer to an older one (given up on) is dropped

    /** (blocking) The newest release now: its version and this app's file in it (null when it isn't up yet), or what went
     *  wrong. Radon Player Next (org.omarchy.music.next) takes only its own file: the release's RadonPlayer-Next.apk;
     *  else RadonPlayer.apk (from the update after 3.0.0), or its old name OmarchyMusic.apk. */
    private fun lookUp(): Triple<String?, String?, Throwable?> {
        val names = if (ctx.packageName.endsWith(".next")) listOf(NEXT_NAME) + APK_NAMES else APK_NAMES
        val agent = "omarchy-music-android/$version"
        return try {
            val rel = UpdateRules.jsonWithin("https://api.github.com/repos/$REPO/releases/latest",
                mapOf("User-Agent" to agent, "Accept" to "application/vnd.github+json"))
            val assets = rel["assets"].list
            Triple(rel["tag_name"].str, names.firstNotNullOfOrNull { n -> assets.firstOrNull { it["name"].str == n }?.get("browser_download_url").str }, null)
        } catch (e: Throwable) {
            // GitHub's list of the release's files wasn't had (it answers only so many times an hour): the version
            // from where its "latest release" page sends on to, and the app's file where every release has it
            runCatching { UpdateRules.latestTag(REPO) }.getOrNull()?.let { t -> Triple(t, UpdateRules.assetUrl(REPO, t, names[0]), null) }
                ?: Triple(null, null, e)
        }
    }

    /** Radon Player Next updates from the release's RadonPlayer-Next.apk; a release from before 3.2.19 has only the
     *  other app (org.omarchy.music), which Android won't put in its place, so then the newest Next build. */
    private fun forThisApp(url: String) = if (ctx.packageName.endsWith(".next") && !url.endsWith("/$NEXT_NAME")) NEXT_APK else url

    fun check(quiet: Boolean = true) {
        if (checking) return
        checking = true
        val mine = ++checkNo
        // On a thread of its own, and with an end to it. Before, it waited its turn among the app's background work
        // (all of it busy during a scan or a sync) with no time limit of its own, and "checking for a newer version…"
        // could stay up for good.
        Thread({
            val (t, u, e) = lookUp()
            Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { if (mine == checkNo) checked(quiet, t, u, e) }
        }, "update-check").apply { isDaemon = true }.start()
        // Whatever the network does, the looking ends
        Music.scope.launch {
            delay(75_000)
            if (checking && mine == checkNo) { checkNo++; checked(quiet, null, null, java.io.IOException("no answer from GitHub")) }
        }
    }

    private fun checked(quiet: Boolean, tag: String?, url: String?, err: Throwable?) {
        run {
            checking = false
            checkedAt = now()
            checkError = err?.let { "couldn't check: ${it.message?.take(60)}" } ?: ""
            Music.accountsVersion++
            if (err != null && !quiet) Music.toast("Couldn't check for an update (no internet?)")
            if (tag == null) return
            if (!newer(tag, version)) { if (!quiet) Music.toast("No update: Radon Player $version is the newest"); return }
            if (url == null) {   // the release is still being put up: its APK comes a few minutes after
                if (!quiet) Music.toast("Radon Player ${tag.removePrefix("v")} is on its way: its Android app isn't up yet")
                Music.scope.launch { delay(5 * 60_000L); check() }
                return
            }
            if (latest != tag.removePrefix("v")) Music.toast("Radon Player ${tag.removePrefix("v")} is out: update it in Settings (⚙)")
            latest = tag.removePrefix("v")
            apk = forThisApp(url)
            Music.accountsVersion++
        }
    }

    /** Work on a thread of its own (the app's shared background workers can all be busy, and an update must not wait
     *  for them); what came of it handed back on the app's own thread. */
    private fun <T> alone(name: String, work: () -> T, done: (T?, Throwable?) -> Unit) {
        Thread({
            val r = runCatching(work)
            Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { done(r.getOrNull(), r.exceptionOrNull()) }
        }, name).apply { isDaemon = true }.start()
    }

    /** The Settings opened: look again if the last look is over ten minutes old. */
    fun checkSoon() { if (now() - checkedAt > 600) check() }

    /** The app is on screen again: ask Android's question that came while it wasn't. */
    fun resumed(activity: android.app.Activity) {
        val c = confirm ?: return
        confirm = null
        runCatching { activity.startActivity(c) }.onFailure { confirm = c; android.util.Log.w(TAG, "update: can't ask", it) }
    }

    /** Install, for an update whose question is waiting. */
    fun askAgain(activity: Any?) {
        if (confirm == null) { state = ""; install(); return }
        (activity as? android.app.Activity)?.let { resumed(it) }
    }

    fun install() {
        val url = apk ?: return
        if (state == "working") return
        android.util.Log.i(TAG, "update: installing $url over $version")
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            // Android asks once whether this app may install apps; then pick Update again.
            Music.toast("Allow Radon Player to install apps, then pick Update again")
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        state = "working"
        val known = latest?.let { v -> v to url }
        var took: String? = null
        alone("update-install", {
            // Looked for again as you tap Update: the newest release now, not the one the last look found (that can be
            // hours old, and a newer one out since)
            val fresh = runCatching { lookUp() }.getOrNull()?.let { (t, u, _) -> if (t != null && u != null) t.removePrefix("v") to forThisApp(u) else null }
            val (v, get) = UpdateRules.toInstall(known, fresh) ?: (version to url)
            if (v != latest) took = v
            android.util.Log.i(TAG, "update: installing $v from $get")
            val file = File(ctx.cacheDir, "update.apk")
            HTTP.newCall(Request.Builder().url(get).build()).execute().use { r ->
                if (!r.isSuccessful) throw HttpError(r.code, "", get)
                file.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
            }
            // Only ever this same app (same name, same package), so it installs over it and keeps your settings and
            // stats: a file for the other app (Radon Player vs Radon Player Next) is turned down here, not installed
            // beside it.
            val got = ctx.packageManager.getPackageArchiveInfo(file.path, 0)?.packageName
            if (got != null && got != ctx.packageName) { file.delete(); throw IllegalStateException("that file is for $got, not this app (${ctx.packageName}); nothing was changed") }
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply { setAppPackageName(ctx.packageName) }
            val id = installer.createSession(params)
            installer.openSession(id).use { s ->
                s.openWrite("omarchy-music.apk", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; s.fsync(out) }
                val done = PendingIntent.getBroadcast(ctx, id, Intent(ctx, Answer::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                s.commit(done.intentSender)
            }
            file.delete()
        }) { _, err ->
            took?.let { latest = it; Music.accountsVersion++ }
            if (err != null) { android.util.Log.w(TAG, "update failed", err); state = "Couldn't update: ${err.message?.take(100)}" }
        }
    }

    /** Android's installer answers here: it wants you to confirm, or it's done, or it failed. */
    class Answer : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            android.util.Log.i(TAG, "update: installer says $status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""}")
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val ask = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
                    // Kept until it's answered: Android drops it when the app isn't on screen.
                    confirm = ask
                    state = "confirm"
                    if (MainActivity.visible) runCatching { context.startActivity(ask); confirm = null }
                        .onFailure { android.util.Log.w(TAG, "update: can't ask now", it) }
                }
                PackageInstaller.STATUS_SUCCESS -> state = ""
                PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                    state = "This copy is signed differently (built by hand?): uninstall it once, then install the release"
                PackageInstaller.STATUS_FAILURE_ABORTED -> { state = ""; confirm = null }
                else -> state = "Couldn't update: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "the installer said no"}"
            }
        }
    }
}
