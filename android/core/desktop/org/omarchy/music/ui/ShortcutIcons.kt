package org.omarchy.music.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import org.omarchy.music.Ctx
import org.omarchy.music.DesktopWidgetRules
import org.omarchy.music.Music
import java.io.File

/** The pictures of the one-click shortcuts (their launchers, the Shortcuts widget's tiles): a rounded square in a colour
 *  of its own for what it does, with the app's own white icon on it (Wrapped's say which period too); a playlist's is its
 *  cover, with a small playlist (or shuffle) mark. Drawn here, kept as PNG (Linux, the widget) and .ico (Windows) in the
 *  app's folder. Anything going wrong: null, and the app's own picture stands in. */
object ShortcutIcons {
    private const val SIZE = 256
    /** The pictures' look: changed when they're drawn differently, so ones kept from before are drawn again. */
    private const val LOOK = 1
    private val dir: File get() = File(Ctx.pc.filesDir, "shortcut-icons").apply { mkdirs() }

    private val COLOURS = mapOf(
        "play-pause" to 0xFF2D3A4F, "next" to 0xFF2D3A4F, "prev" to 0xFF2D3A4F,
        "dj" to 0xFF6A3FA0, "dj-off" to 0xFF4A3A5C, "dj-new" to 0xFF8A3FA0, "dj-mine" to 0xFF5A3FA0,
        "top" to 0xFF9B2E5A, "pinned" to 0xFFB07A1E, "lyrics" to 0xFF4A4FB0, "releases" to 0xFF1F6FB0,
        "wrapped" to 0xFF1F7A70, "wrapped-week" to 0xFF1F7A70, "wrapped-month" to 0xFF1F7A70, "wrapped-all" to 0xFF1F7A70,
        "playlist" to 0xFF2E8B57, "playlist-shuffle" to 0xFF2E8B57, "app" to 0xFF3A3F4A)
    private val GLYPHS = mapOf("play-pause" to Ico.PLAY, "next" to Ico.NEXT, "prev" to Ico.PREV, "dj" to Ico.HEADPHONES,
        "dj-off" to Ico.HEADPHONES, "dj-new" to Ico.SPARKLE, "dj-mine" to Ico.LIBRARY, "top" to Ico.CHART, "pinned" to Ico.PIN,
        "lyrics" to Ico.LYRICS, "releases" to Ico.BELL, "wrapped" to Ico.STAR, "wrapped-week" to Ico.STAR, "wrapped-month" to Ico.STAR,
        "wrapped-all" to Ico.STAR, "playlist" to Ico.PLAYLIST, "playlist-shuffle" to Ico.SHUFFLE, "app" to Ico.NOTE)
    private val WORDS = mapOf("wrapped-week" to "WEEK", "wrapped-month" to "MONTH", "wrapped-all" to "ALL TIME", "dj-new" to "NEW", "dj-mine" to "MINE")

    /** (blocking) Its PNG: drawn the first time (fresh: drawn again, a playlist's cover may have changed). */
    fun png(s: DesktopWidgetRules.Shortcut, fresh: Boolean = false): File? = runCatching {
        val f = File(dir, DesktopWidgetRules.fileBase(s) + "-$LOOK.png")
        if (fresh || !f.isFile) File(f.path + ".tmp").let { tmp -> tmp.writeBytes(draw(s)); if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) } }
        f
    }.getOrNull()

    /** (blocking) Its .ico (Windows' shortcuts), from the PNG. */
    fun ico(s: DesktopWidgetRules.Shortcut, fresh: Boolean = false): File? = runCatching {
        val png = png(s, fresh) ?: return null
        File(dir, DesktopWidgetRules.fileBase(s) + "-$LOOK.ico").also { it.writeBytes(DesktopWidgetRules.icoOf(png.readBytes(), SIZE)) }
    }.getOrNull()

    /** The picture, as PNG bytes. */
    fun draw(s: DesktopWidgetRules.Shortcut): ByteArray {
        val kind = DesktopWidgetRules.iconKind(s.args)
        // A playlist's cover, when it has one
        val cover = if (kind.startsWith("playlist")) runCatching {
            val name = s.args.getOrNull(2)
            val p = (Music.lists.all() + Music.lists.smart()).firstOrNull { it.name == name }
            val songs = p?.let { if (it.smart != null) org.omarchy.music.SmartLists.songs(it).orEmpty() else it.tracks }.orEmpty()
            songs.firstOrNull { it.thumb.isNotEmpty() || it.isFile }?.let { t -> kotlinx.coroutines.runBlocking { loadImage(artModel(t, SIZE), SIZE) } }
        }.getOrNull() else null
        val bmp = ImageBitmap(SIZE, SIZE)
        val size = IntSize(SIZE, SIZE).toSize()
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), size) {
            val r = CornerRadius(SIZE * 0.22f)
            val square = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), r)) }
            if (cover != null) {
                clipPath(square) { drawImage(cover, srcSize = IntSize(cover.width, cover.height), dstSize = IntSize(SIZE, SIZE)) }
                // the mark: a small round badge at the bottom right
                val c = Offset(SIZE * 0.78f, SIZE * 0.78f)
                drawCircle(Color(COLOURS.getValue(kind)), SIZE * 0.17f, c)
                drawCircle(Color.White, SIZE * 0.17f, c, style = Stroke(SIZE * 0.025f))
                glyph(GLYPHS.getValue(kind), c, SIZE * 0.2f)
            } else {
                drawRoundRect(Color(COLOURS[kind] ?: COLOURS.getValue("app")), cornerRadius = r)
                val words = WORDS[kind]
                val g = GLYPHS[kind] ?: Ico.NOTE
                if (words == null) glyph(g, Offset(SIZE / 2f, SIZE / 2f), SIZE * 0.56f)
                else glyph(g, Offset(SIZE / 2f, SIZE * 0.42f), SIZE * 0.46f)
                if (kind == "dj-off") drawLine(Color.White, Offset(SIZE * 0.24f, SIZE * 0.24f), Offset(SIZE * 0.76f, SIZE * 0.76f), SIZE * 0.05f)
            }
            // A faint white edge, so it stands out on a background of its own colour (a purple cover's widget)
            val e = SIZE * 0.02f
            drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(e / 2, e / 2), androidx.compose.ui.geometry.Size(SIZE - e, SIZE - e),
                CornerRadius(SIZE * 0.22f - e / 2), style = Stroke(e))
        }
        // Wrapped's period (and the DJ's kind) in words under the icon
        WORDS[DesktopWidgetRules.iconKind(s.args)]?.takeIf { cover == null }?.let { w ->
            val sk = org.jetbrains.skia.Canvas(bmp.asSkiaBitmap())
            val tf = org.jetbrains.skia.FontMgr.default.matchFamilyStyle(null, org.jetbrains.skia.FontStyle.BOLD)
            val font = org.jetbrains.skia.Font(tf, if (w.length > 5) 30f else 36f)
            val paint = org.jetbrains.skia.Paint().apply { color = 0xFFFFFFFF.toInt(); isAntiAlias = true }
            val width = font.measureTextWidth(w, paint)
            sk.drawString(w, (SIZE - width) / 2f, SIZE * 0.86f, font, paint)
        }
        return org.jetbrains.skia.Image.makeFromBitmap(bmp.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes
    }

    private fun DrawScope.glyph(ico: Ico, centre: Offset, side: Float) {
        inset(centre.x - side / 2, centre.y - side / 2, size.width - centre.x - side / 2, size.height - centre.y - side / 2) {
            drawIco(ico, Color.White)
        }
    }
}
