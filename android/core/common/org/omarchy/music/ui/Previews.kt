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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.coerceAtLeast

/** Little pictures of what a look, a theme or a layout looks like: shown in Personalization's menus beside each one,
 *  and big at the top of the menu for the one the pointer is over (or the one in use). Drawn, so they always match the
 *  app as it is, in your colours. */

/** A look as Now playing shows it: its background (gradient, stars and effects), the cover, the song's name and buttons and
 *  seek bar in the look's colour (made to stand out, as Now playing does) and the visualizer in the look's own colour.
 *  Wide like the PC's window on the PC, tall like a phone's screen on the phone. */
@Composable
fun LookPreview(key: String, wide: Boolean = !onPhone) {
    val c = remember(key) { Looks.colors(key) }
    val backs = listOf(c.bg) + (c.grad ?: emptyList())
    val word = remember(key) { readableOn(c.accent, backs, 3.2f) }
    val soft = remember(key) { readableOn(c.muted, backs, 2.6f) }
    val vis = remember(key) { visColourFor(c.accent, backs, c.bg.luminance() > 0.5f) }
    val back = c.grad?.takeIf { it.size > 1 }?.let { Brush.linearGradient(it) } ?: Brush.linearGradient(listOf(c.bg, c.bg))
    val shape = RoundedCornerShape(12.dp)
    val w = if (wide) 360.dp else 200.dp; val h = if (wide) 220.dp else 340.dp
    Box(Modifier.size(w, h).clip(shape).background(back).border(1.dp, C.line, shape)) {
        if (c.stars) Canvas(Modifier.fillMaxSize()) {
            val r = java.util.Random(3)
            repeat(40) { drawCircle(Color.White.copy(alpha = 0.3f + r.nextFloat() * 0.6f), (0.5f + r.nextFloat()) * density,
                Offset(r.nextFloat() * size.width, r.nextFloat() * size.height)) }
        }
        if (c.fx.isNotEmpty()) LookEffects(c.fx)
        // The Now playing visualizer you use, in this look's own visualizer colour, behind the song as on Now playing
        NowVisSample(Vis.nowStyle(), vis, Modifier.fillMaxSize())
        val title = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = word,
            shadow = androidx.compose.ui.graphics.Shadow(c.bg.copy(alpha = 0.9f), blurRadius = 8f))
        val sub = TextStyle(fontSize = 11.sp, color = soft)
        val cover: @Composable (Dp) -> Unit = { s ->
            Box(Modifier.size(s).clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(c.accent, c.card, c.accent.copy(alpha = 0.5f)))))
        }
        val song: @Composable () -> Unit = {
            Text("Song title", style = title, maxLines = 1)
            Text("Artist · Album", style = sub, maxLines = 1)
        }
        val seekAndButtons: @Composable () -> Unit = {
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(word.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(0.42f).height(4.dp).background(word))
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1:24", style = TextStyle(fontSize = 9.sp, color = soft)); Text("3:21", style = TextStyle(fontSize = 9.sp, color = soft))
            }
            Canvas(Modifier.padding(top = 6.dp).fillMaxWidth().height(30.dp)) {
                val cx = size.width / 2; val cy = size.height / 2; val u = size.height / 6
                fun tri(x: Float, left: Boolean, col: Color) {
                    val p = androidx.compose.ui.graphics.Path()
                    if (left) { p.moveTo(x + u * 1.6f, cy - u * 1.6f); p.lineTo(x - u * 0.6f, cy); p.lineTo(x + u * 1.6f, cy + u * 1.6f) }
                    else { p.moveTo(x - u * 1.6f, cy - u * 1.6f); p.lineTo(x + u * 0.6f, cy); p.lineTo(x - u * 1.6f, cy + u * 1.6f) }
                    p.close(); drawPath(p, col)
                }
                tri(cx - u * 7, true, word); tri(cx + u * 7, false, word)
                drawCircle(word, u * 3, Offset(cx, cy))
                drawRect(c.bg, Offset(cx - u * 1.1f, cy - u * 1.2f), Size(u * 0.7f, u * 2.4f))
                drawRect(c.bg, Offset(cx + u * 0.4f, cy - u * 1.2f), Size(u * 0.7f, u * 2.4f))
            }
        }
        if (wide) Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            cover(128.dp)
            Column(Modifier.padding(start = 16.dp).weight(1f)) { song(); seekAndButtons() }
        } else Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(10.dp))
            cover(150.dp)
            Column(Modifier.padding(top = 14.dp).fillMaxWidth()) { song(); seekAndButtons() }
        }
        if (c.glow) Box(Modifier.fillMaxSize().border(1.5.dp, c.accent.copy(alpha = 0.7f), shape))
    }
}

/** A theme, in the look in use: two rows with their covers, a line between, a chip and a title in its type. */
@Composable
fun ThemePreview(key: String, width: Dp = if (onPhone) 220.dp else 340.dp, height: Dp = if (onPhone) 250.dp else 220.dp) {
    val s = Themes.ALL[key] ?: return
    val cardBack = when (s.cards) {
        CardStyle.NONE -> Color.Transparent; CardStyle.SOFT -> C.fg.copy(alpha = 0.06f); CardStyle.GLASS -> Color.White.copy(alpha = 0.08f)
        CardStyle.OUTLINE -> Color.Transparent; CardStyle.RAISED -> C.light
    }
    Column(Modifier.size(width, height).clip(RoundedCornerShape(10.dp)).background(C.bg).border(1.dp, C.line, RoundedCornerShape(10.dp))
        .padding(8.dp * s.space.coerceIn(0.6f, 1.4f)), verticalArrangement = Arrangement.spacedBy(4.dp * s.space.coerceIn(0.5f, 1.5f))) {
        Text("Albums", style = TextStyle(fontSize = 16.sp * s.titleScale, fontFamily = s.titleFont, fontWeight = s.weight(600), color = C.fg))
        Box(Modifier.clip(s.pillShape).then(if (s.border > 0f) Modifier.border(1.dp * s.border, C.accent, s.pillShape) else Modifier)
            .background(C.accent.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text("Shuffle", style = TextStyle(fontSize = 11.sp, fontFamily = s.font, fontWeight = s.weight(500), color = C.accent))
        }
        repeat(4) { i ->
            Row(Modifier.fillMaxWidth().clip(s.cardShape).background(cardBack)
                .then(if (s.cards == CardStyle.OUTLINE || s.cards == CardStyle.GLASS) Modifier.border((1.dp * s.border).coerceAtLeast(0.5.dp), C.line, s.cardShape) else Modifier)
                .padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp * s.art).clip(s.smallShape).background(C.accent.copy(alpha = listOf(0.9f, 0.6f, 0.45f, 0.75f)[i])))
                Column(Modifier.padding(start = 5.dp)) {
                    Text(listOf("Song title", "Another song", "A third one", "And one more")[i], style = TextStyle(fontSize = 12.sp * s.textScale, fontFamily = s.font,
                        fontWeight = s.weight(500), color = C.fg))
                    Text("Artist", style = TextStyle(fontSize = 10.sp * s.textScale, fontFamily = s.font, fontWeight = s.weight(400),
                        color = C.muted.copy(alpha = s.quiet)))
                }
            }
            if (i < 3 && s.line > 0f) Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                drawLine(C.line.copy(alpha = (0.6f * s.line).coerceIn(0f, 1f)), Offset(0f, 0f), Offset(size.width, 0f), s.lineWidth.toPx(),
                    pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null)
            }
        }
    }
}

/** A layout: the app's main screen and Now playing side by side, as small phone screens in the look in use, showing where
 *  the ☰ menu or the bar of buttons is, the mini player, the titles and how Now playing is arranged. */
@Composable
fun LayoutPreview(lay: UiLayout, height: Dp = if (onPhone) 190.dp else 230.dp, look: Boolean = false) {
    val w = height * 0.56f
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!look) {
            Canvas(Modifier.size(w, height)) { drawMainScreen(lay) }
            Canvas(Modifier.size(w, height)) { drawNowPlaying(lay) }
        } else {
            // In the look's own background too: its fade, stars and effects behind both screens
            val back = C.gradient?.takeIf { it.size > 1 }?.let { Brush.linearGradient(it) } ?: Brush.linearGradient(listOf(C.bg, C.bg))
            listOf(true, false).forEach { main ->
                Box(Modifier.size(w, height).clip(RoundedCornerShape(6.dp)).background(back)) {
                    if (C.stars) Stars()
                    LookEffects()
                    Canvas(Modifier.fillMaxSize()) { if (main) drawMainScreen(lay, fill = false) else drawNowPlaying(lay, fill = false) }
                }
            }
        }
    }
}

/** The tiny version beside a layout's name in the menu: just the main screen. */
@Composable
fun LayoutThumb(lay: UiLayout) = Canvas(Modifier.size(22.dp, 38.dp)) { drawMainScreen(lay, tiny = true) }

private fun DrawScope.frame(fill: Boolean = true) {
    if (fill) drawRoundRect(C.bg, cornerRadius = CornerRadius(6.dp.toPx()))
    drawRoundRect(C.line, cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
}

private fun DrawScope.drawMainScreen(lay: UiLayout, tiny: Boolean = false, fill: Boolean = true) {
    frame(fill)
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

private fun DrawScope.drawNowPlaying(lay: UiLayout, fill: Boolean = true) {
    frame(fill)
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


// ───────────────────────────── Moving samples ─────────────────────────────

/** A visualizer moving to made-up music (a steady beat, the bass loudest), for the pictures in the menus, so a style or
 *  colour can be seen without anything playing. */
@Composable
internal fun rememberSampleVis(): VisState {
    val s = remember { VisState() }
    val target = remember { FloatArray(org.omarchy.music.Spectrum.BANDS) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var last = 0L; var t = 0f
        while (true) androidx.compose.runtime.withFrameNanos { now ->
            val dt = if (last == 0L) 1f / 60f else ((now - last) / 1e9f).coerceIn(0.001f, 0.1f)
            last = now; t += dt
            val kick = kotlin.math.exp(-((t * 2f) % 1f) * 6f)   // 120 beats a minute
            for (b in target.indices) {
                val f = b.toFloat() / target.size
                val wave = (kotlin.math.sin(t * 3f + b * 0.6f) + 1f) / 2f
                target[b] = ((0.5f * (1 - f) + 0.12f + 0.3f * wave * (0.6f + 0.4f * f) + 0.5f * kick * (1 - f) * (1 - f)) *
                    (0.8f + 0.2f * kotlin.math.sin(t * 0.7f + b))).coerceIn(0f, 1f)
            }
            if (s.step(target, false, dt)) s.frame++
        }
    }
    return s
}

/** A Now playing visualizer style ("slim", "off" or one of the Visualizer's) as Now playing draws it, in col (with the
 *  Visualizer's colour setting, as there): round ones in the middle, the rest along the bottom. Off: faint slim bars, so the
 *  colour still shows. */
@Composable
internal fun NowVisSample(style: String, col: Color, modifier: Modifier) {
    val s = rememberSampleVis()
    val colorKey = Vis.color()
    Canvas(modifier) {
        if (s.frame < 0) return@Canvas   // read, so it's drawn again each frame
        val round = style in Vis.CENTRED
        val h = if (round) size.height else size.height * 0.34f
        translate(top = size.height - h) {
            clipRect(0f, 0f, size.width, h) {
                when (style) {
                    "off" -> drawSlimIn(s.levels, col.copy(alpha = 0.35f), size.width, h)
                    "slim" -> drawSlimIn(s.levels, col, size.width, h)
                    else -> drawVisIn(style, s, VisPalette.of(style, colorKey, col, s.phase), size.width, h)
                }
            }
        }
    }
}

private fun DrawScope.drawSlimIn(levels: FloatArray, col: Color, w: Float, h: Float) =
    withSize(w, h) { drawSlim(levels, col, 2f) }

private fun DrawScope.drawVisIn(style: String, s: VisState, pal: VisPalette, w: Float, h: Float) =
    withSize(w, h) { drawVis(style, s, pal, Color.White, backdrop = true) }

/** Draws into a w × h area (size reads as that inside). */
private fun DrawScope.withSize(w: Float, h: Float, block: DrawScope.() -> Unit) {
    val scope = androidx.compose.ui.graphics.drawscope.CanvasDrawScope()
    val canvas = drawContext.canvas
    scope.draw(this, layoutDirection, canvas, Size(w, h), block)
}

/** Now playing with a visualizer style, in the look in use: its background, a cover and the style moving over it. */
@Composable
fun NowVisPreview(style: String) {
    val wide = !onPhone
    val shape = RoundedCornerShape(12.dp)
    val back = C.gradient?.takeIf { it.size > 1 }?.let { Brush.linearGradient(it) } ?: Brush.linearGradient(listOf(C.bg, C.bg))
    Box(Modifier.size(if (wide) 360.dp else 220.dp, if (wide) 220.dp else 280.dp).clip(shape).background(back).border(1.dp, C.line, shape)) {
        if (C.stars) Stars()
        LookEffects()
        Box(Modifier.align(Alignment.Center).padding(bottom = 30.dp).size(if (wide) 120.dp else 130.dp).clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(C.accent, C.light, C.accent.copy(alpha = 0.5f)))))
        NowVisSample(style, VIS_LOOK, Modifier.fillMaxSize())
    }
}

/** An effect (or the stars) over the look in use's background. */
@Composable
fun FxPreview(key: String) {
    val wide = !onPhone
    val shape = RoundedCornerShape(12.dp)
    val back = C.gradient?.takeIf { it.size > 1 }?.let { Brush.linearGradient(it) } ?: Brush.linearGradient(listOf(C.bg, C.bg))
    Box(Modifier.size(if (wide) 360.dp else 220.dp, if (wide) 200.dp else 160.dp).clip(shape).background(back).border(1.dp, C.line, shape)) {
        if (key == "stars") Stars() else LookEffects(setOf(key))
    }
}
