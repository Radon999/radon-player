package org.omarchy.music

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/** Covers for the car (Android Auto) and anything else outside the app that shows what's playing. Android Auto can't
 *  open the app's own files (a file:// cover shows as a blank square there), so a cover kept in the app's folders is
 *  handed out as content://<app>.art/<path>, read-only, and only from those folders (nothing else of the phone's). */
class ArtProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") throw SecurityException("read only")
        val ctx = context ?: return null
        val f = File(uri.path ?: return null).canonicalFile
        if (!allowed(ctx, f) || !f.isFile) throw java.io.FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = when (uri.path.orEmpty().substringAfterLast('.').lowercase()) {
        "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/jpeg"
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        private val PICTURES = setOf("jpg", "jpeg", "png", "webp")
        private fun roots(ctx: android.content.Context) = listOfNotNull(ctx.filesDir, ctx.cacheDir, ctx.externalCacheDir, ctx.getExternalFilesDir(null))
            .map { it.canonicalFile }
        private fun allowed(ctx: android.content.Context, f: File) =
            f.extension.lowercase() in PICTURES && roots(ctx).any { r -> f.path.startsWith(r.path + File.separator) }

        /** A cover's address for outside the app: content:// for a picture in the app's folders, else as it was. */
        fun uri(ctx: android.content.Context, art: String?): Uri? {
            if (art.isNullOrEmpty()) return null
            if (!art.startsWith("/")) return Uri.parse(art)
            val f = runCatching { File(art).canonicalFile }.getOrNull() ?: return Uri.fromFile(File(art))
            return if (allowed(ctx, f)) Uri.Builder().scheme("content").authority(ctx.packageName + ".art").path(f.path).build()
                else Uri.fromFile(f)
        }
    }
}
