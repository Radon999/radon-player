package org.omarchy.music.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

/** Drawing a picture to save or share (the collage, the share card, the lyrics card), the same on the phone and the
 *  PC: Compose's own canvas, sizes in pixels. */
class Painter(val w: Int, val h: Int) {
    val img = ImageBitmap(w, h, ImageBitmapConfig.Argb8888)
    private val canvas = Canvas(img)
    private val scope = CanvasDrawScope()
    private val measurer = TextMeasurer(fontResolver(), Density(1f), LayoutDirection.Ltr, 0)

    fun draw(block: DrawScope.() -> Unit) =
        scope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat()), block)

    fun fill(argb: Int) = draw { drawRect(Color(argb)) }

    fun rect(x: Float, y: Float, x2: Float, y2: Float, argb: Int) =
        draw { drawRect(Color(argb), Offset(x, y), Size(x2 - x, y2 - y)) }

    /** A rectangle shading from top's colour (at y) to bottom's (at y2). */
    fun gradient(x: Float, y: Float, x2: Float, y2: Float, top: Int, bottom: Int) = draw {
        drawRect(Brush.verticalGradient(listOf(Color(top), Color(bottom)), startY = y, endY = y2), Offset(x, y), Size(x2 - x, y2 - y))
    }

    /** A picture filling the square (its middle kept: one that isn't square is cut, not squashed), corners rounded by r;
     *  empty (or null: nothing) where there's no picture. */
    fun picture(b: ImageBitmap?, x: Float, y: Float, side: Float, r: Float = 0f, empty: Int? = null) = draw {
        val body: DrawScope.() -> Unit = {
            if (b != null) {
                val m = minOf(b.width, b.height)
                drawImage(b, IntOffset((b.width - m) / 2, (b.height - m) / 2), IntSize(m, m),
                    IntOffset(x.toInt(), y.toInt()), IntSize(side.toInt(), side.toInt()), filterQuality = FilterQuality.High)
            } else if (empty != null) drawRect(Color(empty), Offset(x, y), Size(side, side))
        }
        if (r > 0f) clipPath(Path().apply { addRoundRect(RoundRect(x, y, x + side, y + side, CornerRadius(r, r))) }) { body() }
        else body()
    }

    private fun style(size: Float, bold: Boolean, lineHeight: Float? = null) = TextStyle(fontSize = size.sp,
        fontFamily = FontFamily.SansSerif, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        lineHeight = lineHeight?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified)

    private fun measure(t: String, size: Float, bold: Boolean, width: Float, maxLines: Int, lineHeight: Float? = null): TextLayoutResult =
        measurer.measure(t, style = style(size, bold, lineHeight), overflow = TextOverflow.Ellipsis, softWrap = true, maxLines = maxLines,
            constraints = Constraints(maxWidth = width.toInt().coerceAtLeast(1)))

    /** One line, cut with … to fit width; y is the baseline (right: ending at x + width). */
    fun text(t: String, size: Float, bold: Boolean, argb: Int, x: Float, y: Float, width: Float, right: Boolean = false) {
        val l = measure(t, size, bold, width, 1)
        val left = if (right) x + width - l.size.width else x
        draw { drawText(l, Color(argb), Offset(left, y - l.firstBaseline)) }
    }

    /** Lines wrapped to width from the top at y, extra px between lines; the height used. */
    fun block(t: String, size: Float, bold: Boolean, argb: Int, x: Float, y: Float, width: Float, spacing: Float = 0f,
              maxLines: Int = Int.MAX_VALUE): Float {
        val l = layoutBlock(t, size, bold, width, spacing, maxLines)
        draw { drawText(l, Color(argb), Offset(x, y)) }
        return l.size.height.toFloat()
    }

    /** How tall block() would be. */
    fun blockHeight(t: String, size: Float, bold: Boolean, width: Float, spacing: Float = 0f, maxLines: Int = Int.MAX_VALUE) =
        layoutBlock(t, size, bold, width, spacing, maxLines).size.height.toFloat()

    private fun layoutBlock(t: String, size: Float, bold: Boolean, width: Float, spacing: Float, maxLines: Int) =
        measure(t, size, bold, width, maxLines, if (spacing > 0f) size * 1.17f + spacing else null)
}
