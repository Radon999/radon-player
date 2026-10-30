package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/** One Radon Player at a time, as the Python app: starting it again (or opening a song file with it, or a command
 *  like `"Radon Player Next.exe" next`) hands what was asked to the one already running, over a local socket that
 *  only this computer can reach, with a secret only this account can read.
 *
 *  Commands: play, pause, play-pause (toggle), next, skip, prev / previous, show, hide, quit, and song files to play. */
object Instance {
    private const val PORT = 8896
    private val dir: File get() = Ctx.pc.filesDir
    private val secretFile get() = File(dir, "instance.key")

    /** Hand args to a running one; true if one took them (this one should then end). */
    fun handOff(args: Array<String>): Boolean = runCatching {
        val key = secretFile.takeIf { it.isFile }?.readText()?.trim() ?: return false
        Socket(InetAddress.getLoopbackAddress(), PORT).use { s ->
            s.soTimeout = 3000
            val out = s.getOutputStream().bufferedWriter()
            out.write(key + "\n"); args.forEach { out.write(it.replace("\n", " ") + "\n") }; out.write("\n"); out.flush()
            s.getInputStream().bufferedReader().readLine() == "ok"
        }
    }.getOrDefault(false)

    /** Listen for others (after the app has started); act on what they ask. */
    fun listen(onArgs: (List<String>) -> Unit) {
        val server = runCatching { ServerSocket(PORT, 8, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        val key = java.util.UUID.randomUUID().toString()
        runCatching { writeAtomic(secretFile, key) }
        Thread({
            while (true) {
                val s = runCatching { server.accept() }.getOrNull() ?: continue
                runCatching {
                    s.use {
                        it.soTimeout = 3000
                        val r = it.getInputStream().bufferedReader()
                        if (r.readLine()?.trim() != key) return@use
                        val got = generateSequence { r.readLine()?.takeIf { l -> l.isNotEmpty() } }.toList()
                        it.getOutputStream().write("ok\n".toByteArray()); it.getOutputStream().flush()
                        Music.scope.launch(Dispatchers.Main) { onArgs(got) }
                    }
                }
            }
        }, "instance").apply { isDaemon = true }.start()
    }

    /** What a command line asks: the commands, else song files to play. show: bring the window up. */
    fun act(args: List<String>, show: () -> Unit, hide: () -> Unit, quit: () -> Unit) {
        val words = args.filter { !it.startsWith("--") }
        val files = words.mapNotNull { a -> Music.local.trackOf(a.removePrefix("file://")) }
        if (files.isNotEmpty()) { Music.playList(files); show(); return }
        when (words.firstOrNull()?.lowercase()) {
            null, "show" -> if ("--tray" !in args) show()
            "play" -> if (Music.paused) Music.togglePause()
            "pause" -> if (!Music.paused) Music.togglePause()
            "play-pause", "toggle" -> Music.togglePause()
            "next", "skip" -> Music.next()
            "prev", "previous" -> Music.previous()
            "hide" -> hide()
            "quit" -> quit()
        }
    }
}
