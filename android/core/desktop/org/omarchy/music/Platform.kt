package org.omarchy.music

import java.io.File

// The PC's side of what the shared code asks of the platform (the phone has its own, the same names).

/** Where the app keeps its files on the PC: ~/.local/share/radon-player (and a cache beside it). */
class Ctx(val filesDir: File, val cacheDir: File) {
    companion object {
        val pc: Ctx by lazy {
            val home = File(System.getProperty("user.home"))
            val base = System.getenv("APPDATA")?.let { File(it, "Radon Player") } ?: File(home, ".local/share/radon-player")
            Ctx(base.apply { mkdirs() }, File(base, "cache").apply { mkdirs() })
        }
    }
}

private fun pathOf(uri: String) = if (uri.startsWith("file://")) java.net.URI(uri).path else uri

fun Ctx.openIn(uri: String): java.io.InputStream? = File(pathOf(uri)).takeIf { it.isFile }?.inputStream()

fun Ctx.uriExists(uri: String): Boolean = File(pathOf(uri)).exists()

fun Ctx.deleteUri(uri: String) { File(pathOf(uri)).delete() }

fun logWarn(what: String, e: Throwable? = null) { System.err.println("radon-player: $what${e?.let { ": $it" } ?: ""}") }

/** The keys for Tidal: from the environment (RADON_TIDAL_ID, RADON_TIDAL_SECRET) on the PC for now. */
object Keys {
    val tidalId: String get() = System.getenv("RADON_TIDAL_ID") ?: ""
    val tidalSecret: String get() = System.getenv("RADON_TIDAL_SECRET") ?: ""
}

val appVersion: String get() = System.getProperty("radon.version") ?: "next"
