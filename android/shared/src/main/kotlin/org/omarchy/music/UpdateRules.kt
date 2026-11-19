package org.omarchy.music

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Looking for a newer release: the parts that need no app. GitHub is asked in two ways: its list of the release's
 *  files (which it only answers so many times an hour), and, when that fails, the address its "latest release" page
 *  sends you on to, which names the version; the files' own addresses follow from the version. */
object UpdateRules {
    /** The version a "latest release" page sends on to (".../releases/tag/v4.0.4" gives "v4.0.4"); null if it isn't one. */
    fun tagFromLocation(location: String?): String? =
        location?.substringBefore('?')?.trimEnd('/')?.takeIf { "/releases/tag/" in it }?.substringAfterLast('/')?.takeIf { it.any(Char::isDigit) }

    /** Where a release's file is: the same for every release. */
    fun assetUrl(repo: String, tag: String, name: String): String = "https://github.com/$repo/releases/download/$tag/$name"

    /** The release's installer for the PC, by its name: the .msi on Windows, the .deb on Linux; null elsewhere. */
    fun pcInstallerName(tag: String, osName: String): String? {
        val v = tag.removePrefix("v"); val os = osName.lowercase()
        return if (os.contains("win")) "Radon-Player-$v.msi" else if (os.contains("linux")) "radon-player_${v}_amd64.deb" else null
    }

    // ---- Linux: a copy that can update itself ----
    /** Where the app is installed, from its launcher's own path (".../radon-player/bin/Radon Player" gives
     *  ".../radon-player"), when the app can replace it itself: a copy in your own folders (unpacked from the .deb,
     *  as on a Linux that has no .deb installer: Arch, Fedora and the like). null: installed by the system's package
     *  manager (under /opt or /usr), which must do the update, or not a packaged app at all. */
    fun ownCopyRoot(launcher: String?, canWrite: (String) -> Boolean): String? {
        val p = launcher?.trim().orEmpty()
        if (!p.startsWith("/") || !p.endsWith("/bin/Radon Player")) return null
        val root = p.removeSuffix("/bin/Radon Player")
        if (root.isEmpty() || root.startsWith("/opt/") || root.startsWith("/usr/")) return null
        val parent = root.substringBeforeLast('/').ifEmpty { "/" }
        return root.takeIf { canWrite(parent) && canWrite(root) }
    }

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** The script that puts an update in place once the app has closed: it waits for the app (pid) to end, moves the
     *  version in use aside (kept once, as <root>.old), moves the new one (fresh) in, and starts it. If the new one
     *  can't be moved in, the old one is put back and started. */
    fun swapScript(pid: Long, root: String, fresh: String): String = """#!/bin/sh
i=0
while kill -0 $pid 2>/dev/null && [ ${'$'}i -lt 150 ]; do sleep 0.2; i=${'$'}((i+1)); done
rm -rf ${q("$root.old")}
if mv ${q(root)} ${q("$root.old")} && mv ${q(fresh)} ${q(root)}; then :
else [ -d ${q(root)} ] || mv ${q("$root.old")} ${q(root)}
fi
exec ${q("$root/bin/Radon Player")}
"""

    private fun parts(v: String) = Regex("\\d+").findAll(v).take(3).map { it.value.toInt() }.toList() + listOf(0, 0, 0)

    /** Whether version a is newer than b ("4.0.4" than "4.0.3"); a b with no number in it is never older. */
    fun newer(a: String, b: String): Boolean {
        if (Regex("\\d").find(b) == null) return false
        val x = parts(a); val y = parts(b)
        for (i in 0 until 3) if (x[i] != y[i]) return x[i] > y[i]
        return false
    }

    /** (blocking) One question to the web that always ends: an answer or an error within seconds, however the
     *  network behaves (a name that won't resolve, a connection that stalls). */
    fun jsonWithin(url: String, headers: Map<String, String>, seconds: Long = 25): JsonElement {
        val client = HTTP.newBuilder().callTimeout(seconds, TimeUnit.SECONDS).build()
        val req = Request.Builder().url(url).header("User-Agent", UA).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        client.newCall(req).execute().use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) throw HttpError(r.code, text, url)
            return if (text.isBlank()) JsonObject(emptyMap()) else JSON.parseToJsonElement(text)
        }
    }

    /** (blocking) The newest release's version, from where its "latest release" page sends on to; null if it can't
     *  be had. Ends within seconds. */
    fun latestTag(repo: String, seconds: Long = 25): String? {
        val client = HTTP.newBuilder().callTimeout(seconds, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
        val req = Request.Builder().url("https://github.com/$repo/releases/latest").header("User-Agent", UA).build()
        client.newCall(req).execute().use { r -> return tagFromLocation(r.header("Location")) }
    }
}
