package org.omarchy.music

import java.io.File

/** A web page in your browser, or a file or folder in the system's own program for it. On Linux through the
 *  desktop's own opener (xdg-open, else gio): the packaged Java's own way needs GNOME libraries that often aren't
 *  there, and then nothing opened and nothing said so (the Tidal sign-in page never came up). On Windows, as before. */
object Opening {
    private val linux = System.getProperty("os.name").lowercase().contains("linux")

    /** Opens the page; false when nothing could be started for it. */
    fun link(url: String): Boolean = if (linux) viaDesktop(url) || awt { it.browse(java.net.URI(url)) } else awt { it.browse(java.net.URI(url)) }

    /** Opens the file or folder; false when nothing could be started for it. */
    fun path(f: File): Boolean = if (linux) viaDesktop(f.path) || awt { it.open(f) } else awt { it.open(f) }

    /** The page, or if no browser could be started, its address copied with a word about it (so a sign-in can still
     *  be finished by pasting it into a browser). */
    fun linkOrCopy(url: String) {
        if (link(url)) return
        runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(url), null) }
        Music.toast("Couldn't open your browser: the link is copied, paste it into a browser")
    }

    private fun viaDesktop(what: String): Boolean = OpeningRules.LINUX_OPENERS.any { cmd ->
        runCatching {
            ProcessBuilder(cmd + what).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            true
        }.getOrDefault(false)
    }

    private fun awt(f: (java.awt.Desktop) -> Unit): Boolean = runCatching { f(java.awt.Desktop.getDesktop()); true }.getOrDefault(false)
}

/** Drawing the window without the graphics card (Linux, Settings › General): for a PC or a virtual machine where the
 *  window flickers or stays black with the usual way (OpenGL). Read once as the app starts, before any window: a
 *  file in the app's own folder says it's on (or RADON_RENDER=software for one start). Slower, so off unless needed. */
object SoftwareDrawing {
    val supported = System.getProperty("os.name").lowercase().contains("linux")
    private val file get() = File(Ctx.pc.filesDir, "software-drawing")
    fun isOn(): Boolean = supported && runCatching { file.isFile }.getOrDefault(false)
    fun set(on: Boolean): Boolean = runCatching {
        if (on) { file.parentFile?.mkdirs(); file.writeText("on\n") } else file.delete()
        true
    }.getOrDefault(false)

    /** At the very start (before the first window): tells the drawing library which way to draw. */
    fun applyAtStart() {
        if (!supported) return
        if (OpeningRules.softwareDrawing(System.getenv("RADON_RENDER"), isOn())) System.setProperty("skiko.renderApi", "SOFTWARE")
    }
}
