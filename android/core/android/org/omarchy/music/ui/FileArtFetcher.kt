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
        val px = (options.size.width as? coil.size.Dimension.Pixels)?.px ?: 0
        val bmp = embedded(ctx, art.uri, px) ?: runCatching {
            ctx.contentResolver.loadThumbnail(Uri.parse(art.uri), Size(512, 512), null)
        }.getOrNull() ?: art.fallback.takeIf { it.isNotEmpty() }?.let { f ->   // a cover picked before 3.0.0
            runCatching {
                val b = if (f.startsWith("http")) java.net.URL(f).openStream().use { it.readBytes() }
                    else java.io.File(if (f.startsWith("file:")) Uri.parse(f).path ?: f else f).readBytes()
                decode(b, px)
            }.getOrNull()
        } ?: return null
        return DrawableResult(android.graphics.drawable.BitmapDrawable(ctx.resources, bmp), false, DataSource.DISK)
    }

    class Factory : Fetcher.Factory<FileArt> {
        override fun create(data: FileArt, options: Options, imageLoader: ImageLoader) = FileArtFetcher(data, options)
    }

    companion object {
        fun embedded(ctx: Context, uri: String, px: Int = 0): Bitmap? = runCatching {
            org.omarchy.music.embeddedPicture(ctx, uri)?.let { decode(it, px) }
        }.getOrNull()

        /** A picture decoded at about px across (0: full size): a big cover (a re-saved 3000-pixel one) is read halved
         *  as often as it can be, so it doesn't run out of memory or get too big to draw. */
        fun decode(b: ByteArray, px: Int): Bitmap? {
            if (b.isEmpty()) return null
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(b, 0, b.size, o)
            var sample = 1
            val side = maxOf(o.outWidth, o.outHeight)
            val want = if (px > 0) px else 2048   // full size still capped: past this a cover only costs memory
            while (side / (sample * 2) >= want) sample *= 2
            return BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}

/** The cover's colour from an Android bitmap (the home-screen widget's). */
fun Tint.artColor(bmp: Bitmap): Int =
    artColor(bmp.copy(Bitmap.Config.ARGB_8888, false).asImageBitmap())
