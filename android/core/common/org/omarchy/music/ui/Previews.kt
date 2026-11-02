package org.omarchy.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.coerceAtLeast

/** Little pictures of what a look, a theme or a layout looks like: shown in Personalization's menus beside each one,
 *  and big at the top of the menu for the one the pointer is over (or the one in use). Drawn, so they always match the
 *  app as it is, in your colours. */

/** A look: its background (and gradient and stars), a card with a song on it, its accent on a button and a seek bar. */
@Composable
fun LookPreview(key: String, width: Dp = 200.dp, height: Dp = 120.dp) {
    val c = remember(key) { Looks.colors(key) }
    val back = c.grad?.takeIf { it.size > 1 }?.let { Brush.linearGradient(it) } ?: Brush.linearGradient(listOf(c.bg, c.bg))
    Box(Modifier.size(width, height).clip(RoundedCornerShape(10.dp)).background(back).border(1.dp, C.line, RoundedCornerShape(10.dp))) {
        if (c.stars) Canvas(Modifier.fillMaxSize()) {
            val r = java.util.Random(3)
            repeat(28) { drawCircle(Color.White.copy(alpha = 0.3f + r.nextFloat() * 0.6f), (0.5f + r.nextFloat()) * density,
                Offset(r.nextFloat() * size.width, r.nextFloat() * size.height)) }
        }
        if (c.fx.isNotEmpty()) LookEffects(c.fx)
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.card.copy(alpha = 0.85f)).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(Brush.linearGradient(listOf(c.accent, c.accent.copy(alpha = 0.4f)))))
                Column(Modifier.padding(start = 6.dp)) {
                    Text("Song title", style = TextStyle(fontSize = 9.sp, color = c.fg))
                    Text("Artist", style = TextStyle(fontSize = 8.sp, color = c.muted))
                }
            }
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.fg.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxWidth(0.6f).height(3.dp).background(c.accent))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(c.accent).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("Play", style = TextStyle(fontSize = 8.sp, color = c.bg))
                }
                Text(if (c.glow) "neon" else "", style = TextStyle(fontSize = 8.sp, color = c.accent))
            }
        }
    }
}

/** A theme, in the look in use: two rows with their covers, a line between, a chip and a title in its type. */
@Composable
fun ThemePreview(key: String, width: Dp = 200.dp, height: Dp = 120.dp) {
    val s = Themes.ALL[key] ?: return
    val cardBack = when (s.cards) {
        CardStyle.NONE -> Color.Transparent; CardStyle.SOFT -> C.fg.copy(alpha = 0.06f); CardStyle.GLASS -> Color.White.copy(alpha = 0.08f)
        CardStyle.OUTLINE -> Color.Transparent; CardStyle.RAISED -> C.light
    }
    Column(Modifier.size(width, height).clip(RoundedCornerShape(10.dp)).background(C.bg).border(1.dp, C.line, RoundedCornerShape(10.dp))
        .padding(8.dp * s.space.coerceIn(0.6f, 1.4f)), verticalArrangement = Arrangement.spacedBy(4.dp * s.space.coerceIn(0.5f, 1.5f))) {
        Text("Albums", style = TextStyle(fontSize = 12.sp * s.titleScale, fontFamily = s.titleFont, fontWeight = s.weight(600), color = C.fg))
        repeat(2) { i ->
            Row(Modifier.fillMaxWidth().clip(s.cardShape).background(cardBack)
                .then(if (s.cards == CardStyle.OUTLINE || s.cards == CardStyle.GLASS) Modifier.border((1.dp * s.border).coerceAtLeast(0.5.dp), C.line, s.cardShape) else Modifier)
                .padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp * s.art).clip(s.smallShape).background(C.accent.copy(alpha = if (i == 0) 0.9f else 0.5f)))
                Column(Modifier.padding(start = 5.dp)) {
                    Text(if (i == 0) "Song title" else "Another song", style = TextStyle(fontSize = 9.sp * s.textScale, fontFamily = s.font,
                        fontWeight = s.weight(500), color = C.fg))
                    Text("Artist", style = TextStyle(fontSize = 8.sp * s.textScale, fontFamily = s.font, fontWeight = s.weight(400),
                        color = C.muted.copy(alpha = s.quiet)))
                }
            }
            if (i == 0 && s.line > 0f) Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                drawLine(C.line.copy(alpha = (0.6f * s.line).coerceIn(0f, 1f)), Offset(0f, 0f), Offset(size.width, 0f), s.lineWidth.toPx(),
                    pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null)
            }
        }
        Box(Modifier.clip(s.pillShape).then(if (s.border > 0f) Modifier.border(1.dp * s.border, C.accent, s.pillShape) else Modifier)
            .background(C.accent.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text("Shuffle", style = TextStyle(fontSize = 8.sp, fontFamily = s.font, fontWeight = s.weight(500), color = C.accent))
        }
    }
}

/** A layout: the app's main screen and Now playing side by side, as small phone screens in the look in use, showing where
 *  the ☰ menu or the bar of buttons is, the mini player, the titles and how Now playing is arranged. */
@Composable
fun LayoutPreview(lay: UiLayout, height: Dp = 130.dp) {
    val w = height * 0.56f
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(w, height)) { drawMainScreen(lay) }
        Canvas(Modifier.size(w, height)) { drawNowPlaying(lay) }
    }
}

/** The tiny version beside a layout's name in the menu: just the main screen. */
@Composable
fun LayoutThumb(lay: UiLayout) = Canvas(Modifier.size(22.dp, 38.dp)) { drawMainScreen(lay, tiny = true) }

private fun DrawScope.frame() {
    drawRoundRect(C.bg, cornerRadius = CornerRadius(6.dp.toPx()))
    drawRoundRect(C.line, cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
}

private fun DrawScope.drawMainScreen(lay: UiLayout, tiny: Boolean = false) {
    frame()
    val W = size.width; val H = size.height
    val u = W / 20f   // a unit
    val fg = C.fg.copy(alpha = 0.55f); val quiet = C.fg.copy(alpha = 0.22f); val acc = C.accent
    var top = u * 1.5f; var bottom = H - u; var left = u; var right = W - u
    // How you get around
    when (lay.nav) {
        NavKind.DRAWER -> {   // the ☰ button, left or right
            val x = if (lay.menuRight) right - u * 3 else left
            repeat(3) { i -> drawRect(fg, Offset(x, top + i * u * 0.8f), Size(u * 3, u * 0.4f)) }
            top += u * 3
        }
        NavKind.TOP -> {      // tabs along the top
            val n = lay.navSlots.size.coerceIn(3, 6)
            val tw = (right - left) / n
            repeat(n) { i -> drawRoundRect(if (i == 0) acc else quiet, Offset(left + i * tw + u * 0.3f, top), Size(tw - u * 0.6f, u * 1.4f), CornerRadius(u)) }
            top += u * 3
        }
        NavKind.BOTTOM -> {   // a bar of buttons along the bottom
            val n = lay.navSlots.size.coerceIn(3, 8)
            drawRect(C.light, Offset(0f, H - u * 4), Size(W, u * 4))
            val tw = W / n
            repeat(n) { i -> drawCircle(if (i == 0) acc else fg, u * 0.8f, Offset(tw * i + tw / 2, H - u * (if (lay.labels) 2.4f else 2f))) }
            if (lay.labels) repeat(n) { i -> drawRect(quiet, Offset(tw * i + tw / 2 - u * 0.8f, H - u * 1.1f), Size(u * 1.6f, u * 0.35f)) }
            bottom = H - u * 4.5f
        }
        NavKind.RAIL -> {     // a rail down the left
            drawRect(C.light, Offset(0f, 0f), Size(u * (if (lay.labels) 5f else 4f), H))
            val n = lay.navSlots.size.coerceIn(3, 8)
            repeat(n) { i -> drawCircle(if (i == 0) acc else fg, u * 0.8f, Offset(u * 2, u * 3 + i * u * 3)) }
            left += u * (if (lay.labels) 5.5f else 4.5f)
        }
    }
    // The mini player
    when (lay.mini) {
        MiniKind.NONE -> Unit
        MiniKind.TOP -> {
            drawRoundRect(C.light, Offset(left, top), Size(right - left, u * 3), CornerRadius(u * 0.6f))
            drawRect(acc, Offset(left + u * 0.5f, top + u * 0.5f), Size(u * 2, u * 2))
            top += u * 3.8f
        }
        MiniKind.FLOAT -> {
            drawRoundRect(C.light, Offset(left, bottom - u * 3.2f), Size(right - left, u * 3), CornerRadius(u))
            drawRect(acc, Offset(left + u * 0.5f, bottom - u * 2.7f), Size(u * 2, u * 2))
            bottom -= u * 3.8f
        }
        MiniKind.BAR -> {
            drawRect(C.light, Offset(if (lay.nav == NavKind.RAIL) left - u * 0.5f else 0f, bottom - u * 3), Size(W, u * 3))
            drawRect(acc, Offset(left, bottom - u * 2.5f), Size(u * 2, u * 2))
            bottom -= u * 3.5f
        }
        MiniKind.PILL -> {
            val pw = (right - left) * 0.6f
            drawRoundRect(C.light, Offset((left + right) / 2 - pw / 2, bottom - u * 2.6f), Size(pw, u * 2.4f), CornerRadius(u * 1.2f))
            drawCircle(acc, u * 0.9f, Offset((left + right) / 2 - pw / 2 + u * 1.3f, bottom - u * 1.4f))
            bottom -= u * 3.2f
        }
    }
    // The page's title (centred or not), then rows of songs
    if (!tiny) {
        val tw = u * 7
        drawRect(C.fg.copy(alpha = 0.8f), Offset(if (lay.titleCenter) (left + right) / 2 - tw / 2 else left, top), Size(tw, u * 1.1f))
        top += u * 2.4f
    }
    var y = top
    while (y + u * 2 < bottom) {
        drawRect(quiet, Offset(left, y), Size(u * 2, u * 2))
        drawRect(fg, Offset(left + u * 2.6f, y + u * 0.3f), Size((right - left) * 0.5f, u * 0.6f))
        drawRect(quiet, Offset(left + u * 2.6f, y + u * 1.2f), Size((right - left) * 0.3f, u * 0.5f))
        y += u * 2.8f
    }
}

private fun DrawScope.drawNowPlaying(lay: UiLayout) {
    frame()
    val W = size.width; val H = size.height
    val u = W / 20f
    val acc = C.accent; val fg = C.fg.copy(alpha = 0.7f); val quiet = C.fg.copy(alpha = 0.25f)
    val art = Brush.linearGradient(listOf(acc, acc.copy(alpha = 0.35f)))
    fun title(x: Float, y: Float, big: Boolean, centred: Boolean) {
        val tw = if (big) u * 14 else u * 9
        val tx = if (centred) W / 2 - tw / 2 else x
        drawRect(C.fg.copy(alpha = 0.9f), Offset(tx, y), Size(tw, if (big) u * 1.8f else u * 1.1f))
        drawRect(quiet, Offset(if (centred) W / 2 - u * 3 else x, y + (if (big) u * 2.4f else u * 1.6f)), Size(u * 6, u * 0.7f))
    }
    fun controls(y: Float) {
        drawRect(quiet, Offset(u * 2, y), Size(W - u * 4, u * 0.4f))
        drawRect(acc, Offset(u * 2, y), Size((W - u * 4) * 0.4f, u * 0.4f))
        drawCircle(fg, u * 1f, Offset(W / 2 - u * 4.5f, y + u * 3)); drawCircle(acc, u * 1.6f, Offset(W / 2, y + u * 3))
        drawCircle(fg, u * 1f, Offset(W / 2 + u * 4.5f, y + u * 3))
    }
    fun tools(y: Float) = repeat(4) { i -> drawCircle(quiet, u * 0.8f, Offset(W / 2 - u * 4.5f + i * u * 3, y)) }
    var y = u * 2
    if (lay.toolsTop) { tools(y + u); y += u * 3 }
    when (lay.np) {
        NpKind.BLEED -> {
            drawRect(art, Offset(0f, 0f), Size(W, W)); y = W + u
            title(u * 2, y, false, false); y += u * 3.5f
        }
        NpKind.INFO_TOP -> {
            title(u * 2, y, false, lay.titleCenter); y += u * 3.5f
            drawRoundRect(art, Offset(u * 2, y), Size(W - u * 4, W - u * 4), CornerRadius(u)); y += W - u * 3
        }
        NpKind.HEADLINE -> {
            drawRoundRect(art, Offset(u * 2, y), Size(u * 7, u * 7), CornerRadius(u * 0.6f))
            drawRect(C.fg.copy(alpha = 0.9f), Offset(u * 10, y + u * 0.5f), Size(u * 8, u * 2.2f))
            drawRect(C.fg.copy(alpha = 0.9f), Offset(u * 10, y + u * 3.2f), Size(u * 6, u * 2.2f))
            y += u * 9
        }
        NpKind.SPLIT -> {   // side by side on a wide screen: here the cover small beside the song
            drawRoundRect(art, Offset(u * 2, y), Size(u * 8, u * 8), CornerRadius(u * 0.6f))
            title(u * 11, y + u * 2, false, false); y += u * 10
        }
        NpKind.CENTERED -> {
            val s = W * 0.62f
            drawRoundRect(art, Offset(W / 2 - s / 2, y), Size(s, s), CornerRadius(u)); y += s + u
            title(0f, y, false, true); y += u * 3.5f
        }
        NpKind.CLASSIC -> {
            drawRoundRect(art, Offset(u * 2, y), Size(W - u * 4, W - u * 4), CornerRadius(u)); y += W - u * 3
            title(u * 2, y, false, lay.titleCenter); y += u * 3.5f
        }
    }
    controls(y.coerceAtMost(H - u * 9))
    if (!lay.toolsTop) tools((y + u * 7).coerceAtMost(H - u * 2))
}
