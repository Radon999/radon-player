package org.omarchy.music.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.omarchy.music.AuthImage
import org.omarchy.music.Music
import java.io.File

// The PC's side of what the shared screens ask of the platform (the phone has its own, the same names).

/** A picture to load small: drawn big, it's soft (a cheap blur). */
data class Sized(val model: Any, val px: Int)

private object Images {
    private val cache = object : LinkedHashMap<Pair<Any, Int>, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<Pair<Any, Int>, ImageBitmap>?) = size > 300
    }
    private val failed = HashSet<Pair<Any, Int>>()

    fun cached(model: Any, px: Int): ImageBitmap? = synchronized(cache) { cache[model to px] }

    suspend fun load(model: Any?, px: Int): ImageBitmap? {
        model ?: return null
        if (model is Sized) return load(model.model, model.px)
        val key = model to px
        synchronized(cache) { cache[key]?.let { return it }; if (key in failed) return null }
        val img = withContext(Dispatchers.IO) { runCatching { decode(bytes(model) ?: return@runCatching null, px) }.getOrNull() }
        synchronized(cache) { if (img != null) cache[key] = img else failed += key }
        return img
    }

    private fun bytes(model: Any): ByteArray? = when (model) {
        is File -> model.takeIf { it.isFile }?.readBytes()
        is FileArt -> org.omarchy.music.embeddedPicture(Music.ctx, model.uri)
            ?: File(java.net.URI(model.uri.takeIf { it.startsWith("file:") } ?: File(model.uri).toURI().toString()).path)
                .parentFile?.listFiles()?.firstOrNull { it.name.lowercase() in COVERS }?.readBytes()
        is AuthImage -> fetch(model.url, model.auth)
        is String -> when {
            model.startsWith("http://") || model.startsWith("https://") -> fetch(model, null)
            model.startsWith("file:") -> File(java.net.URI(model)).takeIf { it.isFile }?.readBytes()
            else -> File(model).takeIf { it.isFile }?.readBytes()
        }
        else -> null
    }

    private val COVERS = setOf("cover.jpg", "cover.png", "folder.jpg", "folder.png", "front.jpg", "front.png", "album.jpg")

    private fun fetch(url: String, auth: String?): ByteArray? {
        val c = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "RadonPlayer")
        auth?.let { c.setRequestProperty("Authorization", it) }
        return try { if (c.responseCode in 200..299) c.inputStream.use { it.readBytes() } else null } finally { c.disconnect() }
    }

    /** Decoded, and made about px across when it's much bigger (Skia reads JPEG, PNG and WebP). */
    private fun decode(b: ByteArray, px: Int): ImageBitmap? {
        val img = org.jetbrains.skia.Image.makeFromEncoded(b)
        val side = maxOf(img.width, img.height)
        if (px <= 0 || side <= px * 1.5) return img.toComposeImageBitmap()
        val k = px.toFloat() / side
        val w = (img.width * k).toInt().coerceAtLeast(1); val h = (img.height * k).toInt().coerceAtLeast(1)
        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawImageRect(img, org.jetbrains.skia.Rect.makeWH(img.width.toFloat(), img.height.toFloat()),
            org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()), org.jetbrains.skia.SamplingMode.LINEAR, null, true)
        return surface.makeImageSnapshot().toComposeImageBitmap()
    }
}

/** A picture: a file, a web address, or a song's own cover (FileArt). */
@Composable
fun AsyncImage(model: Any?, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit,
               alpha: Float = 1f) {
    val px = (model as? Sized)?.px ?: 600
    val inner = (model as? Sized)?.model ?: model
    var img by remember(inner, px) { mutableStateOf(inner?.let { Images.cached(it, px) }) }
    LaunchedEffect(inner, px) { if (img == null) img = Images.load(inner, px) }
    val i = img
    if (i != null) Image(i, contentDescription, modifier, contentScale = contentScale, alpha = alpha)
    else Box(modifier)
}

fun smallArt(model: Any?, px: Int): Any? = model?.let { Sized(it, px) }

suspend fun loadImage(model: Any?, px: Int): ImageBitmap? = Images.load(model, px)

fun pixelsOf(img: ImageBitmap): Triple<IntArray, Int, Int> {
    val px = IntArray(img.width * img.height)
    img.readPixels(px)
    return Triple(px, img.width, img.height)
}

/** Picking a file (the PC's file dialog): its path, or null. */
class FilePick(val launch: (Array<String>) -> Unit)
@Composable
fun rememberOpenFile(onResult: (String?) -> Unit): FilePick = remember {
    FilePick { mimes -> Dialogs.open(mimes) { onResult(it) } }
}

class FileSave(val launch: (String) -> Unit)
@Composable
fun rememberSaveFile(mime: String, onResult: (String?) -> Unit): FileSave = remember {
    FileSave { name -> Dialogs.save(name) { onResult(it) } }
}

private object Dialogs {
    private fun ext(mimes: Array<String>): List<String> = mimes.flatMap {
        when {
            it.contains("json") -> listOf("json"); it.contains("zip") -> listOf("zip")
            it.contains("mpegurl") || it.contains("m3u") -> listOf("m3u", "m3u8")
            it.startsWith("text/csv") -> listOf("csv"); it.startsWith("image") -> listOf("jpg", "jpeg", "png", "webp")
            it.startsWith("audio") -> listOf("flac", "mp3", "m4a", "ogg", "opus", "wav")
            else -> emptyList()
        }
    }

    fun open(mimes: Array<String>, done: (String?) -> Unit) = java.awt.EventQueue.invokeLater {
        val d = java.awt.FileDialog(null as java.awt.Frame?, "Open", java.awt.FileDialog.LOAD)
        val exts = ext(mimes)
        if (exts.isNotEmpty() && "*/*" !in mimes) d.setFilenameFilter { _, n -> exts.any { n.lowercase().endsWith(".$it") } }
        d.isVisible = true
        val f = d.file?.let { File(d.directory, it).path }
        org.omarchy.music.Music.scope.launchMain { done(f) }
    }

    fun save(name: String, done: (String?) -> Unit) = java.awt.EventQueue.invokeLater {
        val d = java.awt.FileDialog(null as java.awt.Frame?, "Save", java.awt.FileDialog.SAVE)
        d.file = name
        d.isVisible = true
        val f = d.file?.let { File(d.directory, it).path }
        org.omarchy.music.Music.scope.launchMain { done(f) }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchMain(run: () -> Unit) {
    launch(Dispatchers.Main) { run() }
}

/** The permissions the screens ask for: nothing to ask on the PC. */
object Perm {
    const val MIC = "mic"
    const val NOTIFY = "notify"
}
fun hasPermission(p: String) = true
class PermissionAsk(val launch: (String) -> Unit)
@Composable
fun rememberPermission(onResult: (Boolean) -> Unit): PermissionAsk = remember { PermissionAsk { onResult(true) } }

/** Back: Esc on the PC (the window passes it here). */
@Composable
fun PlatformBack(enabled: Boolean = true, onBack: () -> Unit) {
    val cb = androidx.compose.runtime.rememberUpdatedState(onBack)
    androidx.compose.runtime.DisposableEffect(enabled) {
        val h: () -> Unit = { cb.value() }
        if (enabled) Back.stack.add(h)
        onDispose { Back.stack.remove(h) }
    }
}

/** What Esc closes: the newest open thing's back. */
object Back {
    val stack = ArrayList<() -> Unit>()
    /** Esc pressed: true if something closed. */
    fun press(): Boolean { val h = stack.lastOrNull() ?: return false; h(); return true }
}

fun copyText(label: String, text: String) {
    runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null) }
}

/** Songs to another app: on the PC, their folder opens with them in it. */
fun shareFiles(uris: List<String>, title: String) {
    val f = uris.firstOrNull()?.let { File(if (it.startsWith("file:")) java.net.URI(it).path else it) } ?: return
    runCatching {
        if (System.getProperty("os.name").lowercase().contains("win")) ProcessBuilder("explorer.exe", "/select,", f.path).start()
        else java.awt.Desktop.getDesktop().open(f.parentFile)
    }
}

fun openSystemSettings(kind: String) {}

@Composable
fun currentActivity(): Any? = null

fun shouldExplain(activity: Any?, p: String) = false

@Composable
fun SystemBars(light: Boolean) {}

val blurAlpha: Float get() = 0.5f

/** The PC's windows the screens can open: the mini player (a small window, always on top). */
object PcWindows {
    var mini by mutableStateOf(false)
}
fun enterMiniPlayer(activity: Any?) { PcWindows.mini = true }
fun updateMiniPlayer(activity: Any?) {}
val hasMiniPlayer = true

/** The volume you hear: the app's own on the PC (0 to 1). */
object SysVolume {
    fun get(): Float = org.omarchy.music.MpvEngine.level
    fun set(v: Float) { org.omarchy.music.MpvEngine.setLevel(v) }
}

/** The music's spectrum: not read on the PC yet (the visualizer draws its idle look). */
@Composable
fun rememberSpectrum(on: Boolean, failed: (String) -> Unit = {}): FloatArray? = null
const val SPECTRUM_ASK = "The visualizer reads the music this app plays."

/** A file's facts for the Tagger: size, format, length, bitrate, quality. */
fun fileFacts(u: String): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    val f = File(if (u.startsWith("file:")) java.net.URI(u).path else u)
    if (f.isFile) out += "Size" to "%.1f MB".format(f.length() / 1e6)
    runCatching {
        val h = org.jaudiotagger.audio.AudioFileIO.read(f).audioHeader
        out += "Format" to (h.format ?: f.extension).uppercase().substringBefore(' ')
        val sec = h.trackLength
        out += "Length" to "%d:%02d".format(sec / 60, sec % 60)
        h.bitRateAsNumber.takeIf { it > 0 }?.let { out += "Bitrate" to "$it kbps" }
        val bits = h.bitsPerSample; val rate = h.sampleRateAsNumber
        if (bits > 0 && rate > 0) out += "Quality" to "$bits-bit/${"%.1f".format(rate / 1000.0).removeSuffix(".0")} kHz"
    }
    return out
}

private val picturesDir: File get() = File(System.getProperty("user.home"), "Pictures/Radon Player").apply { mkdirs() }

private fun pictureFile(name: String) = File(picturesDir, name.replace(Regex("[\\\\/:*?\"<>|]"), "") + ".png")

/** A picture into Pictures/Radon Player, as name.png. */
fun savePicture(img: ImageBitmap, name: String): Boolean = runCatching {
    javax.imageio.ImageIO.write(img.toAwtImage(), "png", pictureFile(name))
}.getOrDefault(false)

/** A picture to another app: saved, then shown in its folder. */
fun sharePicture(img: ImageBitmap, name: String, pkg: String? = null): Boolean {
    if (!savePicture(img, name)) return false
    shareFiles(listOf(pictureFile(name).path), name)
    return true
}

fun appInstalled(pkg: String) = false

/** The fonts for drawing text into a picture (Painter). */
fun fontResolver(): androidx.compose.ui.text.font.FontFamily.Resolver = androidx.compose.ui.text.font.createFontFamilyResolver()

/** (blocking) A picture file's bytes as an image, or null. */
fun decodeImage(b: ByteArray): ImageBitmap? = runCatching { org.jetbrains.skia.Image.makeFromEncoded(b).toComposeImageBitmap() }.getOrNull()

/** A dialog over the whole window (Wrapped). */
fun fullScreenDialog() = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)

/** A folder of your music as a full path ("Music/Rock" → your home's Music/Rock). */
fun fullFolderPath(rel: String) = File(rel).takeIf { it.isAbsolute }?.path ?: File(System.getProperty("user.home"), rel).path
const val canOpenFolders = true
fun openFolder(path: String) { runCatching { java.awt.Desktop.getDesktop().open(File(path)) } }
fun sharable(file: String) = !file.startsWith("http")

/** Playing on a cast device is the phone's (Google Cast); on the PC, Play on goes to the phone. */
@Composable
fun CastPicker(ui: Ui) {
    LaunchedEffect(Unit) { Music.toast("Casting is on the phone: pick a speaker or TV there"); ui.castPick = false }
}

/** A right-click: the menu, as a long press is on the phone. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun androidx.compose.ui.Modifier.rightClick(run: () -> Unit): androidx.compose.ui.Modifier =
    this.onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press) { e ->
        if (e.buttons.isSecondaryPressed) run()
    }
