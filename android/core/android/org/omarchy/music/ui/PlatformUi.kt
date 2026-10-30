package org.omarchy.music.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import org.omarchy.music.Music

// The phone's side of what the shared screens ask of the platform (the PC has its own, the same names).

/** A picture: a file, a web address, or a song's own cover (FileArt); Coil loads it. */
@Composable
fun AsyncImage(model: Any?, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit,
               alpha: Float = 1f) =
    coil.compose.AsyncImage(model = model, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale, alpha = alpha)

/** A small copy of a picture (drawn big, it's soft: a cheap blur). */
fun smallArt(model: Any?, px: Int): Any? = model?.let { ImageRequest.Builder(Music.ctx).data(it).size(px).build() }

/** (blocking-free) A picture as a bitmap at about px across, or null. */
suspend fun loadImage(model: Any?, px: Int): ImageBitmap? {
    model ?: return null
    val res = Music.ctx.imageLoader.execute(ImageRequest.Builder(Music.ctx).data(model).allowHardware(false).size(px).build())
    return ((res as? SuccessResult)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.asImageBitmap()
}

/** An image's pixels (ARGB), for the cover's colour. */
fun pixelsOf(img: ImageBitmap): Triple<IntArray, Int, Int> {
    val b = img.asAndroidBitmap().let { if (it.config == android.graphics.Bitmap.Config.HARDWARE) it.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else it }
    val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
    return Triple(px, b.width, b.height)
}

/** Picking a file (Android's picker): its content:// address, or null. */
class FilePick(val launch: (Array<String>) -> Unit)
@Composable
fun rememberOpenFile(onResult: (String?) -> Unit): FilePick {
    val l = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { onResult(it?.toString()) }
    return remember(l) { FilePick { l.launch(it) } }
}

/** Saving a file where you pick (Android's picker): its address, or null. */
class FileSave(val launch: (String) -> Unit)
@Composable
fun rememberSaveFile(mime: String, onResult: (String?) -> Unit): FileSave {
    val l = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { onResult(it?.toString()) }
    return remember(l) { FileSave { l.launch(it) } }
}

/** The permissions the screens ask for. */
object Perm {
    const val MIC = Manifest.permission.RECORD_AUDIO
    const val NOTIFY = "android.permission.POST_NOTIFICATIONS"
}
fun hasPermission(p: String) = ContextCompat.checkSelfPermission(Music.ctx, p) == PackageManager.PERMISSION_GRANTED
class PermissionAsk(val launch: (String) -> Unit)
@Composable
fun rememberPermission(onResult: (Boolean) -> Unit): PermissionAsk {
    val l = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult(it) }
    return remember(l) { PermissionAsk { l.launch(it) } }
}

/** Android's back gesture (Esc on the PC). */
@Composable
fun PlatformBack(enabled: Boolean = true, onBack: () -> Unit) = androidx.activity.compose.BackHandler(enabled, onBack)

fun copyText(label: String, text: String) {
    val cm = Music.ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
}

/** Songs to another app (Android's share sheet). */
fun shareFiles(uris: List<String>, title: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).setType("audio/*")
        .putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris.map { android.net.Uri.parse(it) }))
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    Music.ctx.startActivity(android.content.Intent.createChooser(send, title).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** A page of the phone's settings for the app: "app", "notifications" or "allfiles". */
fun openSystemSettings(kind: String) {
    val c = Music.ctx
    val i = when (kind) {
        "notifications" -> android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, c.packageName)
        "allfiles" -> android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            android.net.Uri.parse("package:" + c.packageName))
        else -> android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${c.packageName}"))
    }
    runCatching { c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The activity the screens are in (for a restart, the notification permission's rationale), or null. */
@Composable
fun currentActivity(): Any? {
    var c: android.content.Context? = LocalContext.current
    while (c !is android.app.Activity && c is android.content.ContextWrapper) c = c.baseContext
    return c as? android.app.Activity
}

/** Whether Android would explain a permission again (not "don't ask again"). */
fun shouldExplain(activity: Any?, p: String) = (activity as? android.app.Activity)?.shouldShowRequestPermissionRationale(p) == true

/** Dark icons on the system bars over a light look. */
@Composable
fun SystemBars(light: Boolean) {
    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
}

/** How strongly a blurred cover can show (older Android can't blur, and it would just be a soft picture). */
val blurAlpha: Float get() = if (android.os.Build.VERSION.SDK_INT >= 31) 0.5f else 0.16f

/** The mini player (picture in picture). */
fun enterMiniPlayer(activity: Any?) { (activity as? org.omarchy.music.MainActivity)?.enterMini() }
fun updateMiniPlayer(activity: Any?) { (activity as? org.omarchy.music.MainActivity)?.updateMini() }
val hasMiniPlayer = true

/** The volume you hear: the phone's media volume (0 to 1). */
object SysVolume {
    private val am get() = Music.ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    private val max get() = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    fun get(): Float = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) / max.toFloat()
    fun set(v: Float) = am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (v * max).toInt().coerceIn(0, max), 0)
}

/** The music's spectrum, Spectrum.BANDS levels filled in as it plays (Android's visualizer on the player's own sound,
 *  which needs the microphone permission: nothing is recorded); null when it can't be had. */
@Composable
fun rememberSpectrum(on: Boolean, failed: (String) -> Unit = {}): FloatArray? {
    if (!on || !hasPermission(Perm.MIC) || org.omarchy.music.Remote.active) return null
    val session = ((Music.player as? org.omarchy.music.ExoEngine)?.p as? androidx.media3.exoplayer.ExoPlayer)?.audioSessionId ?: 0
    val target = remember { FloatArray(org.omarchy.music.Spectrum.BANDS) }
    DisposableEffect(session) {
        val vis = runCatching {
            android.media.audiofx.Visualizer(session).apply {
                captureSize = android.media.audiofx.Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                setDataCaptureListener(object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: android.media.audiofx.Visualizer, wave: ByteArray, rate: Int) {}
                    override fun onFftDataCapture(v: android.media.audiofx.Visualizer, fft: ByteArray, rate: Int) {
                        org.omarchy.music.Spectrum.bands(fft, rate / 1000).copyInto(target)   // rate comes in millihertz
                    }
                }, android.media.audiofx.Visualizer.getMaxCaptureRate(), false, true)
                enabled = true
            }
        }.onFailure { failed(it.message ?: "Android's visualizer isn't available") }.getOrNull()
        onDispose { vis?.runCatching { enabled = false; release() } }
    }
    return target
}
/** What the spectrum needs and why, when it isn't allowed yet. */
const val SPECTRUM_ASK = "Android's visualizer reads the music this app plays, and for that it asks for the microphone permission. " +
    "The microphone itself is never used."

/** A file's facts for the Tagger: size, format, length, bitrate, quality. */
fun fileFacts(u: String): List<Pair<String, String>> {
    val ctx = Music.ctx
    val out = ArrayList<Pair<String, String>>()
    runCatching {
        ctx.contentResolver.query(android.net.Uri.parse(u), arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) out += "Size" to "%.1f MB".format(c.getLong(0) / 1e6)
        }
    }
    runCatching {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, android.net.Uri.parse(u))
            r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { out += "Format" to it.substringAfter('/').uppercase() }
            r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                out += "Length" to "%d:%02d".format(it / 60000, it / 1000 % 60)
            }
            r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { out += "Bitrate" to "${it / 1000} kbps" }
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val bits = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                val rate = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toDoubleOrNull()
                if (bits != null && rate != null) out += "Quality" to "$bits-bit/${"%.1f".format(rate / 1000).removeSuffix(".0")} kHz"
            }
        } finally { r.release() }
    }
    return out
}

/** A picture into the phone's Pictures, as name.png. */
fun savePicture(img: ImageBitmap, name: String): Boolean = runCatching {
    val cr = Music.ctx.contentResolver
    val uri = cr.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, android.content.ContentValues().apply {
        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
        put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
    }) ?: return false
    cr.openOutputStream(uri)?.use { plainBitmap(img).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } ?: return false
    true
}.getOrDefault(false)

/** Send the picture to pkg (Instagram, Facebook), or to the app you pick in Android's share sheet. */
fun sharePicture(img: ImageBitmap, name: String, pkg: String? = null): Boolean = runCatching {
    val ctx = Music.ctx
    val dir = java.io.File(ctx.cacheDir, "shared").apply { mkdirs(); listFiles()?.forEach { it.delete() } }   // only the latest one is kept
    val file = java.io.File(dir, name.replace(Regex("[\\\\/:*?\"<>|]"), "") + ".png")
    file.outputStream().use { plainBitmap(img).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("image/png").putExtra(android.content.Intent.EXTRA_STREAM, uri)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = android.content.ClipData.newRawUri(name, uri)
    ctx.startActivity((if (pkg != null) send.setPackage(pkg) else android.content.Intent.createChooser(send, "Share"))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    true
}.getOrDefault(false)

private fun plainBitmap(img: ImageBitmap): android.graphics.Bitmap = img.asAndroidBitmap().let {
    if (it.config == android.graphics.Bitmap.Config.HARDWARE) it.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else it
}

/** Whether an app is on the phone (Instagram, for its story button). */
/** What the share button of a picture says here. */
const val SHARE_PICTURE = "Share…"

fun appInstalled(pkg: String) = runCatching { Music.ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

/** The fonts for drawing text into a picture (Painter). */
fun fontResolver(): androidx.compose.ui.text.font.FontFamily.Resolver = androidx.compose.ui.text.font.createFontFamilyResolver(Music.ctx)

/** (blocking) A picture file's bytes as an image, or null. */
fun decodeImage(b: ByteArray): ImageBitmap? = android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()

/** A dialog over the whole screen, under the system bars too (Wrapped). */
fun fullScreenDialog() = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)

/** A folder of your music as a full path ("Music/Rock" → /storage/emulated/0/Music/Rock). */
fun fullFolderPath(rel: String) = if (rel.startsWith("/")) rel else "/storage/emulated/0/$rel"
/** Android has no file manager to open at a folder for sure. */
const val canOpenFolders = false
fun openFolder(path: String) {}
/** Whether a song file can go to another app (one of the phone's own, not a stream). */
fun sharable(file: String) = file.startsWith("content://")

/** A right-click: none on the phone (a long press is the menu). */
fun androidx.compose.ui.Modifier.rightClick(run: () -> Unit): androidx.compose.ui.Modifier = this

/** Picking a folder anywhere: the phone picks among the music it has (Choose), so none here. */
@Composable
fun rememberFolderPick(done: (String?) -> Unit): (() -> Unit)? = null
