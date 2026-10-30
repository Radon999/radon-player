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

/** The app's lasting context (not an Activity's). */
fun appCtx(c: Ctx): Ctx = c.applicationContext

const val platformName = "android"
val deviceName: String get() = android.os.Build.MODEL
fun sdkAtLeast(n: Int) = android.os.Build.VERSION.SDK_INT >= n

/** Writing to an address: a path, or a content:// one (mode "wt": truncate). */
fun Ctx.openOut(uri: String, mode: String = "wt"): java.io.OutputStream? =
    if (uri.startsWith("/")) java.io.File(uri).outputStream() else contentResolver.openOutputStream(android.net.Uri.parse(uri), mode)

/** A content:// address's file name, as the picker or MediaStore says. */
fun Ctx.displayName(uri: String): String? = if (uri.startsWith("/")) java.io.File(uri).name else runCatching {
    contentResolver.query(android.net.Uri.parse(uri), arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull()

fun Ctx.mimeOf(uri: String): String? = contentResolver.getType(android.net.Uri.parse(uri))

/** (type, size in bytes) of a song on the phone, as MediaStore says. */
fun Ctx.typeAndSize(uri: String): Pair<String, Long>? = contentResolver.query(android.net.Uri.parse(uri),
    arrayOf(android.provider.MediaStore.Audio.Media.MIME_TYPE, android.provider.MediaStore.Audio.Media.SIZE), null, null, null)?.use { c ->
    if (!c.moveToFirst()) null else (c.getString(0) ?: "") to c.getLong(1)
}

/** The folder (as "Music/Playlists") of a file picked in Android's picker, for paths relative to it; "" if unknown. */
fun Ctx.folderOf(uri: String): String {
    val u = android.net.Uri.parse(uri)
    val docId = runCatching { android.provider.DocumentsContract.getDocumentId(u) }.getOrNull() ?: ""
    return if (u.authority == "com.android.externalstorage.documents") docId.substringAfter(':').substringBeforeLast('/', "") else ""
}

/** A new song file in the phone's music (MediaStore: Music/…), written by write; its content:// address. */
fun Ctx.newMusicFile(name: String, where: String, write: (java.io.OutputStream) -> Unit): String {
    val cr = contentResolver
    val values = android.content.ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name); put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/flac"); put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, where); put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = cr.insert(android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: error("couldn't make $name in Music/")
    try {
        cr.openOutputStream(uri)!!.buffered().use(write)
        cr.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    } catch (e: Exception) {
        cr.delete(uri, null, null)
        throw e
    }
    return uri.toString()
}

fun notificationsOn(ctx: Ctx): Boolean = androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()

/** Whether the app may write into your own files too (Android 11 and up: All files access). */
fun canWriteOwnFiles(ctx: Ctx) = !sdkAtLeast(30) || android.os.Environment.isExternalStorageManager()

/** The file the switches (Prefs) live in, for backups. */
fun prefsFile(ctx: Ctx, name: String) = java.io.File(ctx.applicationInfo.dataDir, "shared_prefs/$name.xml")

/** After a restore: the app's screens close. */
fun closeApp(activity: Any?) { (activity as? android.app.Activity)?.finishAffinity() }

/** Outputs coming and going (headphones, Bluetooth, USB): run changed. */
fun watchOutputs(ctx: Ctx, changed: () -> Unit) {
    (ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager).registerAudioDeviceCallback(
        object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>) = changed()
            override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>) = changed()
        }, android.os.Handler(android.os.Looper.getMainLooper()))
}

private val HEADPHONE_TYPES = setOf(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
    android.media.AudioDeviceInfo.TYPE_USB_HEADSET, android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_USB_DEVICE)

/** The headphones the sound goes to now (Bluetooth, USB, wired) by name, else "". */
fun headphonesName(ctx: Ctx): String {
    val am = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    val outs = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).filter { it.type in HEADPHONE_TYPES }
    val d = outs.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET }
        ?: outs.firstOrNull()
    return d?.let { it.productName?.toString()?.takeIf { n -> n.isNotBlank() && n != deviceName } ?: if (
        it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET) "Wired headphones" else "" } ?: ""
}

fun xmlParser(): org.xmlpull.v1.XmlPullParser = android.util.Xml.newPullParser()

/** Picture bytes as a JPEG; null if they aren't a picture. */
fun toJpeg(b: ByteArray): ByteArray? {
    val bmp = android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size) ?: return null
    return java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
}

/** A picture's shorter side, in pixels (0 if it isn't one). */
fun imageMinSide(b: ByteArray): Int {
    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size, o)
    return minOf(o.outWidth, o.outHeight).coerceAtLeast(0)
}

/** A song file's own cover, or null. */
fun embeddedPicture(ctx: Ctx, uri: String): ByteArray? {
    val r = android.media.MediaMetadataRetriever()
    return try { r.setDataSource(ctx, android.net.Uri.parse(uri)); r.embeddedPicture } finally { r.release() }
}

/** Desktops on this network with the remote on (they announce _omarchy-music._tcp): found(name, address). */
fun discoverDesktops(ctx: Ctx, found: (String, String) -> Unit) {
    val nsd = ctx.getSystemService(android.net.nsd.NsdManager::class.java) ?: return
    nsd.discoverServices("_omarchy-music._tcp", android.net.nsd.NsdManager.PROTOCOL_DNS_SD, object : android.net.nsd.NsdManager.DiscoveryListener {
        override fun onServiceFound(info: android.net.nsd.NsdServiceInfo) {
            @Suppress("DEPRECATION")
            nsd.resolveService(info, object : android.net.nsd.NsdManager.ResolveListener {
                override fun onResolveFailed(i: android.net.nsd.NsdServiceInfo, code: Int) {}
                override fun onServiceResolved(i: android.net.nsd.NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    found(i.serviceName, "${i.host.hostAddress}:${i.port}")
                }
            })
        }
        override fun onServiceLost(info: android.net.nsd.NsdServiceInfo) {}
        override fun onDiscoveryStarted(type: String) {}
        override fun onDiscoveryStopped(type: String) {}
        override fun onStartDiscoveryFailed(type: String, code: Int) {}
        override fun onStopDiscoveryFailed(type: String, code: Int) {}
    })
}

/** A picture that needs a login header: what the picture loader (Coil) takes. */
fun imageWithAuth(ctx: Ctx, url: String, auth: String): Any =
    coil.request.ImageRequest.Builder(ctx).data(url).addHeader("Authorization", auth).build()

/** The switches' storage: Android's shared preferences. */
class PrefsStore(ctx: Ctx, name: String) {
    private val sp = ctx.getSharedPreferences(name, android.content.Context.MODE_PRIVATE)
    fun load(): Map<String, Any> = sp.all.filterValues { it != null }.mapValues { it.value!! }
    fun put(key: String, v: Any) {
        val e = sp.edit()
        when (v) { is Boolean -> e.putBoolean(key, v); is Float -> e.putFloat(key, v); is String -> e.putString(key, v) }
        e.apply()
    }
}

/** The bits of a PCM encoding (media3's C.ENCODING_PCM_…). */
fun pcmBits(e: Int) = when (e) {
    androidx.media3.common.C.ENCODING_PCM_16BIT -> 16
    androidx.media3.common.C.ENCODING_PCM_24BIT -> 24
    androidx.media3.common.C.ENCODING_PCM_32BIT, androidx.media3.common.C.ENCODING_PCM_FLOAT -> 32
    else -> 0
}

/** The PC's phone-remote server: the phone is the remote, not the one played. */
object RemoteHost {
    val supported = false
    val running = false
    val code = ""
    val phones = emptyMap<String, String>()
    fun setOn(on: Boolean) {}
    fun forget(token: String) {}
}

/** Discord's Rich Presence talks to the Discord app on a computer: the PC's. */
object Discord {
    val supported = false
    val on = false
    val status = ""
    fun setOn(v: Boolean) {}
}

/** ffmpeg's checks (Loudness check, Identify by sound) are the PC's. */
object AudioTools {
    val supported = false
    fun quality(path: String, rate: Int, bits: Int): QualityResult = QualityResult()
    fun fingerprint(path: String, secs: Int): Pair<String, Int> = error("not on the phone")
}

/** Starting with the computer is the PC's. */
object Startup {
    val supported = false
    fun isOn() = false
    fun set(on: Boolean) = false
}
