package org.omarchy.music.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import androidx.compose.ui.graphics.asImageBitmap
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options

/** Coil's loader for a song's own cover (FileArt): the picture inside the file, or Android's thumbnail of it. */
class FileArtFetcher(private val art: FileArt, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val ctx = options.context
        val bmp = embedded(ctx, art.uri) ?: runCatching {
            ctx.contentResolver.loadThumbnail(Uri.parse(art.uri), Size(512, 512), null)
        }.getOrNull() ?: art.fallback.takeIf { it.isNotEmpty() }?.let { f ->   // a cover picked before 3.0.0
            runCatching {
                val b = if (f.startsWith("http")) java.net.URL(f).openStream().use { it.readBytes() } else java.io.File(f).readBytes()
                BitmapFactory.decodeByteArray(b, 0, b.size)
            }.getOrNull()
        } ?: return null
        return DrawableResult(android.graphics.drawable.BitmapDrawable(ctx.resources, bmp), false, DataSource.DISK)
    }

    class Factory : Fetcher.Factory<FileArt> {
        override fun create(data: FileArt, options: Options, imageLoader: ImageLoader) = FileArtFetcher(data, options)
    }

    companion object {
        fun embedded(ctx: Context, uri: String): Bitmap? = runCatching {
            org.omarchy.music.embeddedPicture(ctx, uri)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        }.getOrNull()
    }
}

/** The cover's colour from an Android bitmap (the home-screen widget's). */
fun Tint.artColor(bmp: Bitmap): Int =
    artColor(bmp.copy(Bitmap.Config.ARGB_8888, false).asImageBitmap())
