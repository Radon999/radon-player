package org.omarchy.music

// The phone's side of what the shared code asks of the platform (the PC has its own, the same names).

/** Where the app keeps its files: the phone's app context. */
typealias Ctx = android.content.Context

/** A song's or picture's address read: a path, or a content:// address (Android's file picker, MediaStore). */
fun Ctx.openIn(uri: String): java.io.InputStream? =
    if (uri.startsWith("/")) java.io.File(uri).inputStream() else contentResolver.openInputStream(android.net.Uri.parse(uri))

/** Whether an address still opens: songs deleted by hand drop out. */
fun Ctx.uriExists(uri: String): Boolean = try {
    if (uri.startsWith("/")) java.io.File(uri).exists()
    else contentResolver.openFileDescriptor(android.net.Uri.parse(uri), "r")?.use { true } ?: false
} catch (_: Exception) {
    false
}

/** A file deleted, by path or content:// address. */
fun Ctx.deleteUri(uri: String) {
    if (uri.startsWith("/")) java.io.File(uri).delete() else contentResolver.delete(android.net.Uri.parse(uri), null, null)
}

fun logWarn(what: String, e: Throwable? = null) { android.util.Log.w("radon-player", what, e) }

/** The keys for Tidal, built in (BuildConfig, from tools/tidal-keys.sh). */
object Keys {
    val tidalId: String get() = BuildConfig.TIDAL_CLIENT_ID
    val tidalSecret: String get() = BuildConfig.TIDAL_CLIENT_SECRET
}

val appVersion: String get() = BuildConfig.VERSION_NAME
