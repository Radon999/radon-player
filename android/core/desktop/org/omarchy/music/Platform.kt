package org.omarchy.music

import kotlinx.coroutines.launch

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

fun appCtx(c: Ctx): Ctx = c

const val platformName = "pc"
val deviceName: String get() = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrDefault("PC")
fun sdkAtLeast(n: Int) = true

fun Ctx.openOut(uri: String, mode: String = "wt"): java.io.OutputStream? =
    File(pathOf(uri)).apply { parentFile?.mkdirs() }.outputStream()

fun Ctx.displayName(uri: String): String? = File(pathOf(uri)).name

fun Ctx.mimeOf(uri: String): String? = when (File(pathOf(uri)).extension.lowercase()) {
    "flac" -> "audio/flac"; "mp3" -> "audio/mpeg"; "m4a", "aac", "alac" -> "audio/mp4"; "ogg", "opus" -> "audio/ogg"
    "wav" -> "audio/wav"; "aiff", "aif" -> "audio/aiff"; else -> null
}

fun Ctx.typeAndSize(uri: String): Pair<String, Long>? = File(pathOf(uri)).takeIf { it.isFile }?.let { (mimeOf(uri) ?: "") to it.length() }

/** A file's folder, for the paths a playlist names relative to it. */
fun Ctx.folderOf(uri: String): String = File(pathOf(uri)).parent ?: ""

/** Where downloads go on the PC: your Music folder (RADON_MUSIC's first folder if set). */
val musicRoot: File get() = System.getenv("RADON_MUSIC")?.split(';')?.firstOrNull { it.isNotBlank() }?.let(::File)
    ?: File(System.getProperty("user.home"), "Music")

/** A new song file under your Music folder (where: "Music/Radon Player/…"), written by write; its path. */
fun Ctx.newMusicFile(name: String, where: String, write: (java.io.OutputStream) -> Unit): String {
    // A folder of yours anywhere (C:\\Songs, a music folder you added), else one under your Music folder
    val dir = (File(where).takeIf { it.isAbsolute } ?: File(musicRoot, where.removePrefix("Music/"))).apply { mkdirs() }
    val f = File(dir, name)
    val tmp = File(dir, "$name.part")
    try {
        tmp.outputStream().buffered().use(write)
        if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
    } catch (e: Exception) {
        tmp.delete(); throw e
    }
    return f.path
}

fun notificationsOn(ctx: Ctx) = true
fun canWriteOwnFiles(ctx: Ctx) = true
fun prefsFile(ctx: Ctx, name: String) = File(ctx.filesDir, "$name.json")
fun closeApp(activity: Any?) {}

/** Outputs coming and going: the PC looks again every few seconds (Windows has no simple event for it here). */
fun watchOutputs(ctx: Ctx, changed: () -> Unit) {
    // The PC says nothing when the default output changes: look every few seconds (as the Python app does)
    Thread({
        var was = Outputs.now(ctx).key
        while (true) {
            Thread.sleep(3000)
            val k = runCatching { Outputs.refresh().key }.getOrDefault(was)
            if (k != was) { was = k; Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { changed() } }
        }
    }, "outputs").apply { isDaemon = true }.start()
}
fun headphonesName(ctx: Ctx): String = Outputs.now(ctx).takeIf { it.kind != "speaker" }?.label?.substringBefore(" (") ?: ""

fun xmlParser(): org.xmlpull.v1.XmlPullParser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()

fun toJpeg(b: ByteArray): ByteArray? {
    val img = runCatching { javax.imageio.ImageIO.read(b.inputStream()) }.getOrNull() ?: return null
    val rgb = java.awt.image.BufferedImage(img.width, img.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
    rgb.createGraphics().apply { drawImage(img, 0, 0, null); dispose() }
    return java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(rgb, "jpg", it) }.toByteArray()
}

fun imageMinSide(b: ByteArray): Int = runCatching { javax.imageio.ImageIO.read(b.inputStream())?.let { minOf(it.width, it.height) } }.getOrNull() ?: 0

fun embeddedPicture(ctx: Ctx, uri: String): ByteArray? = runCatching {
    org.jaudiotagger.audio.AudioFileIO.read(File(pathOf(uri))).tag?.firstArtwork?.binaryData
}.getOrNull()

/** The PC is the one desktops are found from: nothing to look for here. */
fun discoverDesktops(ctx: Ctx, found: (String, String) -> Unit) {}

/** A picture that needs a login header: fetched as bytes on the PC. */
fun imageWithAuth(ctx: Ctx, url: String, auth: String): Any = AuthImage(url, auth)

/** A picture that needs a header to fetch (the phone-as-remote's covers). */
data class AuthImage(val url: String, val auth: String)

/** The switches' storage on the PC: a small JSON file. */
class PrefsStore(ctx: Ctx, name: String) {
    private val file = File(ctx.filesDir, "$name.json")
    private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()

    fun load(): Map<String, Any> {
        val o = runCatching { JSON.parseToJsonElement(file.readText()) as kotlinx.serialization.json.JsonObject }.getOrNull() ?: return emptyMap()
        for ((k, v) in o) {
            val p = v as? kotlinx.serialization.json.JsonPrimitive ?: continue
            values[k] = when {
                p.isString -> p.content
                p.content == "true" || p.content == "false" -> p.content == "true"
                else -> p.content.toFloatOrNull() ?: continue
            }
        }
        return values.toMap()
    }

    fun put(key: String, v: Any) {
        values[key] = v
        val o = kotlinx.serialization.json.JsonObject(values.mapValues { (_, x) ->
            when (x) { is Boolean -> kotlinx.serialization.json.JsonPrimitive(x); is Number -> kotlinx.serialization.json.JsonPrimitive(x)
                else -> kotlinx.serialization.json.JsonPrimitive(x.toString()) }
        })
        writeAtomic(file, o.toString())
    }
}
