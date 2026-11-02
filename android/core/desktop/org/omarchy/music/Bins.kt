package org.omarchy.music

import java.io.File

/** The programs the PC app runs (mpv plays, ffmpeg checks and draws the spectrum), found in this order: RADON_MPV /
 *  RADON_FFMPEG; the app's own (the installer brings them, in its resources folder); the Python app's (its installer's
 *  bin folder); then whatever is on the PATH. */
object Bins {
    private val windows = System.getProperty("os.name").lowercase().contains("win")

    private fun find(name: String, env: String): String {
        System.getenv(env)?.takeIf { it.isNotBlank() }?.let { return it }
        val exe = if (windows) "$name.exe" else name
        val places = listOfNotNull(
            System.getProperty("compose.application.resources.dir")?.let { File(it, exe) },
            System.getProperty("compose.application.resources.dir")?.let { File(File(it, "bin"), exe) },
            if (windows) System.getenv("LOCALAPPDATA")?.let { File("$it\\Programs\\Omarchy Music\\bin\\$exe") } else null,
        )
        return places.firstOrNull { it.isFile }?.path ?: name
    }

    /** A helper program the installer brings (in its resources folder), or null. */
    fun helper(name: String): String? {
        val exe = if (windows) "$name.exe" else name
        return System.getProperty("compose.application.resources.dir")?.let { File(it, exe) }?.takeIf { it.isFile }?.path
    }

    val mpv: String by lazy { find("mpv", "RADON_MPV") }
    val ffmpeg: String by lazy { find("ffmpeg", "RADON_FFMPEG") }

    /** Whether the app brought its own (false: it's using the Python app's or the PATH's). */
    val bundled: Boolean get() = System.getProperty("compose.application.resources.dir")?.let { mpv.startsWith(it) } ?: false
}
