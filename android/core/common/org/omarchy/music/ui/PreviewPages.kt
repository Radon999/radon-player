package org.omarchy.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.omarchy.music.Music

/** A choice in a Personalization menu shown as several live screens to swipe through, not one picture: Now playing
 *  with its visualizer moving, the My music page, slides of Wrapped, the lyrics card. So you see what you would get
 *  before you put it on. Named tabs under the picture jump to a screen; on the PC (where the picture sits beside the
 *  menu, out of the pointer's way) it turns to the next one by itself. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PreviewPager(key: Any, names: List<String>, w: Dp, h: Dp, page: @Composable (Int) -> Unit) {
    val pager = rememberPagerState { names.size }
    val scope = rememberCoroutineScope()
    if (!onPhone) LaunchedEffect(key) {
        while (true) { delay(2600); pager.animateScrollToPage((pager.currentPage + 1) % names.size) }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, Modifier.size(w, h), pageSpacing = 12.dp) { i ->
            Box(Modifier.size(w, h), contentAlignment = Alignment.Center) { page(i) }
        }
        Row(Modifier.padding(top = 6.dp).width(w).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            names.forEachIndexed { i, n ->
                val on = pager.currentPage == i
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) C.accent.copy(alpha = 0.18f) else C.fg.copy(alpha = 0.05f))
                    .clickable { scope.launch { pager.animateScrollToPage(i) } }.padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(n, style = T.sub.copy(color = if (on) C.accent else C.muted), maxLines = 1)
                }
            }
        }
        if (onPhone) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            names.indices.forEach { i ->
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (pager.currentPage == i) C.accent else C.muted.copy(alpha = 0.4f)))
            }
        }
    }
}

/** The My music page as it would look: in a look's colours (its fade, stars and effects behind), a theme's corners,
 *  type, cards and lines, and a layout's bar of buttons and mini player. Sample songs; nothing of yours is read. */
@Composable
fun MusicPagePreview(look: String, theme: String = org.omarchy.music.Prefs.text(Themes.PREF, Themes.STANDARD), layout: UiLayout = UiLayouts.current,
                     fx: Set<String>? = null, w: Dp = 240.dp, h: Dp = 300.dp, albums: Boolean = false) {
    val c = remember(look) { Looks.colors(look) }
    val s = Themes.ALL[theme] ?: Themes.ALL.getValue(Themes.STANDARD)
    val back = c.grad?.takeIf { it.size > 1 }?.let { Brush.linearGradient(it) } ?: Brush.linearGradient(listOf(c.bg, c.bg))
    val shape = RoundedCornerShape(12.dp)
    val effects = fx ?: c.fx
    val line = c.fg.copy(alpha = 0.14f)
    val cardBack = when (s.cards) {
        CardStyle.NONE, CardStyle.OUTLINE -> Color.Transparent; CardStyle.SOFT -> c.fg.copy(alpha = 0.07f)
        CardStyle.GLASS -> Color.White.copy(alpha = 0.09f); CardStyle.RAISED -> c.card
    }
    fun text(size: Float, weight: Int, col: Color, title: Boolean = false) = TextStyle(fontSize = (size * (if (title) s.titleScale else s.textScale)).sp,
        fontFamily = if (title) s.titleFont else s.font, fontWeight = s.weight(weight), color = col)
    val gap = (4.dp * s.space.coerceIn(0.5f, 1.5f))
    val songs = listOf("Song title" to "Artist", "Another song" to "Someone else", "A third one" to "A band", "And one more" to "Artist", "The last one" to "Another band")
    val tabs = listOf("Now", "Music", "Albums", "Lists")
    @Composable fun bar(down: Boolean) {
        if (down) Column(Modifier.fillMaxHeight().width(26.dp).background(c.card.copy(alpha = 0.6f)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tabs.forEachIndexed { i, _ -> Box(Modifier.size(12.dp).clip(s.smallShape).background(if (i == 1) c.accent else c.muted.copy(alpha = 0.5f))) }
        } else Row(Modifier.fillMaxWidth().background(c.card.copy(alpha = 0.6f)).padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            tabs.forEachIndexed { i, n -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(10.dp).clip(s.smallShape).background(if (i == 1) c.accent else c.muted.copy(alpha = 0.5f)))
                if (layout.labels) Text(n, style = text(7f, 500, if (i == 1) c.accent else c.muted))
            } }
        }
    }
    @Composable fun mini() {
        val m = when (layout.mini) { MiniKind.NONE -> return; MiniKind.PILL -> Modifier.padding(horizontal = 50.dp).clip(RoundedCornerShape(50))
            MiniKind.FLOAT -> Modifier.padding(horizontal = 6.dp).clip(s.cardShape); else -> Modifier }
        Row(m.fillMaxWidth().background(c.card).padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).clip(s.smallShape).background(Brush.linearGradient(listOf(c.accent, c.card))))
            if (layout.mini != MiniKind.PILL) Text("Song title", style = text(9f, 600, c.fg), modifier = Modifier.padding(start = 6.dp).weight(1f), maxLines = 1)
            else Spacer(Modifier.weight(1f))
            Box(Modifier.size(12.dp).clip(CircleShape).background(c.accent))
        }
    }
    Box(Modifier.size(w, h).clip(shape).background(back).border(1.dp, C.line, shape)) {
        if (c.stars) Stars()
        if (effects.isNotEmpty()) LookEffects(effects, accent = c.accent, fg = c.fg, bg = c.bg)
        Row(Modifier.fillMaxSize()) {
            if (layout.nav == NavKind.RAIL) bar(down = true)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (layout.mini == MiniKind.TOP) mini()
                Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (layout.titleCenter) Arrangement.Center else Arrangement.Start) {
                    if (layout.nav == NavKind.DRAWER && !layout.menuRight) Text("☰  ", style = text(13f, 500, c.fg))
                    Text(if (albums) "Albums" else "My music", style = text(15f, 700, c.fg, title = true))
                    if (layout.nav == NavKind.DRAWER && layout.menuRight) { Spacer(Modifier.weight(1f)); Text("☰", style = text(13f, 500, c.fg)) }
                }
                if (layout.nav == NavKind.TOP) Row(Modifier.padding(horizontal = 10.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEachIndexed { i, n -> Text(n, style = text(9f, if (i == 1) 700 else 500, if (i == 1) c.accent else c.muted)) }
                }
                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Shuffle" to true, "Sort" to false).forEach { (n, on) ->
                        Box(Modifier.clip(s.pillShape).then(if (s.border > 0f) Modifier.border(1.dp * s.border, if (on) c.accent else line, s.pillShape) else Modifier)
                            .background(if (on) c.accent.copy(alpha = 0.16f) else Color.Transparent).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text(n, style = text(9f, 500, if (on) c.accent else c.muted)) }
                    }
                }
                if (albums) Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(gap * 1.5f)) {
                    repeat(2) { r -> Row(horizontalArrangement = Arrangement.spacedBy(gap * 2)) {
                        repeat(3) { i -> Column(Modifier.weight(1f).clip(s.cardShape).background(cardBack)
                            .then(if (s.cards == CardStyle.OUTLINE || s.cards == CardStyle.GLASS) Modifier.border((1.dp * s.border).coerceAtLeast(0.5.dp), line, s.cardShape) else Modifier).padding(3.dp)) {
                            Box(Modifier.fillMaxWidth().height(44.dp * s.art.coerceIn(0.7f, 1.3f)).clip(s.cardShape)
                                .background(Brush.linearGradient(listOf(c.accent.copy(alpha = 0.9f - (r * 3 + i) * 0.1f), c.card))))
                            Text(listOf("An album", "Another", "Live", "Deluxe", "Singles", "Early")[r * 3 + i], style = text(9f, 600, c.fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Artist", style = text(7.5f, 400, c.muted.copy(alpha = s.quiet)), maxLines = 1)
                        } }
                    } }
                }
                else Column(Modifier.weight(1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
                    songs.forEachIndexed { i, (name, who) ->
                        Row(Modifier.fillMaxWidth().clip(s.cardShape).background(cardBack)
                            .then(if (s.cards == CardStyle.OUTLINE || s.cards == CardStyle.GLASS) Modifier.border((1.dp * s.border).coerceAtLeast(0.5.dp), line, s.cardShape) else Modifier)
                            .padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(22.dp * s.art.coerceIn(0.6f, 1.4f)).clip(s.smallShape).background(Brush.linearGradient(listOf(c.accent.copy(alpha = 0.95f - i * 0.12f), c.card))))
                            Column(Modifier.padding(start = 6.dp).weight(1f)) {
                                Text(name, style = text(10.5f, 500, if (i == 1) c.accent else c.fg), maxLines = 1)
                                Text(who, style = text(8.5f, 400, c.muted.copy(alpha = s.quiet)), maxLines = 1)
                            }
                            Text(listOf("3:21", "4:02", "2:48", "3:37", "5:10")[i], style = text(8f, 400, c.muted.copy(alpha = s.quiet)))
                        }
                        if (i < songs.size - 1 && s.line > 0f && s.cards == CardStyle.NONE) Canvas(Modifier.fillMaxWidth().height(1.dp)) {
                            drawLine(line.copy(alpha = (0.14f * s.line).coerceIn(0f, 1f)), Offset(0f, 0f), Offset(size.width, 0f), s.lineWidth.toPx(),
                                pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null)
                        }
                    }
                }
                if (layout.mini != MiniKind.TOP) mini()
                if (layout.nav == NavKind.BOTTOM) bar(down = false)
            }
        }
        if (c.glow) Box(Modifier.fillMaxSize().border(1.5.dp, c.accent.copy(alpha = 0.7f), shape))
    }
}

private val PW get() = if (onPhone) 240.dp else 260.dp
private val PH get() = if (onPhone) 300.dp else 320.dp

/** A look in the Look menu: Now playing (its visualizer moving), My music, Albums, and a slide of Wrapped in Your look. */
@Composable
fun LookPages(key: String) = PreviewPager(key, listOf("Now playing", "My music", "Albums", "Wrapped"), PW, PH) { i ->
    when (i) {
        0 -> LookPreview(key, wide = false, pw = PW, ph = PH)
        1 -> MusicPagePreview(key, w = PW, h = PH)
        2 -> MusicPagePreview(key, w = PW, h = PH, albums = true)
        else -> WrapPresetSlide("look", key, Modifier.size(PW, PH))
    }
}

/** A theme in the Theme menu, in the look in use: its lists, its tiles, and Now playing with it. */
@Composable
fun ThemePages(key: String) = PreviewPager(key, listOf("My music", "Albums", "Rows"), PW, PH) { i ->
    when (i) {
        0 -> MusicPagePreview(Music.look, theme = key, w = PW, h = PH)
        1 -> MusicPagePreview(Music.look, theme = key, w = PW, h = PH, albums = true)
        else -> ThemePreview(key, PW, PH)
    }
}

/** A layout in the Layout menu, in the look in use: the drawn plan of its two screens, then My music and Albums with
 *  its bar of buttons and mini player where they would be. */
@Composable
fun LayoutPages(lay: UiLayout) = PreviewPager(lay.key, listOf("Plan", "My music", "Albums"), PW, PH) { i ->
    when (i) {
        0 -> LayoutPreview(lay, height = PH * 0.8f)
        1 -> MusicPagePreview(Music.look, layout = lay, w = PW, h = PH)
        else -> MusicPagePreview(Music.look, layout = lay, w = PW, h = PH, albums = true)
    }
}

/** An effect in the Effects menu, moving: by itself, behind Now playing, and behind My music, in the look in use. With
 *  [with] (the effects you have on), the one shown is drawn together with them, so you see how they all look at once,
 *  as they would be with it picked. */
@Composable
fun FxPages(key: String, with: Set<String> = emptySet()) = PreviewPager(key, listOf("By itself", "Now playing", "My music"), PW, PH) { i ->
    // The effects on, each with its own colour ("rain@ff4fd8ff"); the one shown added if it isn't on yet
    val all = if (LookFx.has(with, key)) with else with + key
    val drawn = all.filter { LookFx.keyOf(it) != "stars" }.toSet()   // the stars are drawn on their own
    when (i) {
        0 -> Box(Modifier.size(PW, PH).clip(RoundedCornerShape(12.dp))
            .background(C.gradient?.takeIf { it.size > 1 }?.let { Brush.linearGradient(it) } ?: Brush.linearGradient(listOf(C.bg, C.bg))).border(1.dp, C.line, RoundedCornerShape(12.dp))) {
            if (LookFx.has(all, "stars")) Stars()
            if (drawn.isNotEmpty()) LookEffects(drawn) }
        1 -> LookPreview(Music.look, wide = false, fx = drawn, pw = PW, ph = PH)
        else -> MusicPagePreview(Music.look, fx = drawn, w = PW, h = PH)
    }
}

/** A visualizer style in its menu: Now playing with it moving, and by itself over the look's background. */
@Composable
fun VisPages(style: String) = PreviewPager(style, listOf("Now playing", "By itself"), PW, PH) { i ->
    if (i == 0) LookPreview(Music.look, wide = false, visStyle = style, pw = PW, ph = PH)
    else Box(Modifier.size(PW, PH).clip(RoundedCornerShape(12.dp))
        .background(C.gradient?.takeIf { it.size > 1 }?.let { Brush.linearGradient(it) } ?: Brush.linearGradient(listOf(C.bg, C.bg))).border(1.dp, C.line, RoundedCornerShape(12.dp))) {
        NowVisSample(style, VIS_LOOK, Modifier.fillMaxSize()) }
}

/** A Wrapped style in its menu: three different slides in it. */
@Composable
fun WrapPages(style: String) = PreviewPager(style, listOf("Top songs", "Top artist", "Your year"), PW, PH) { i ->
    when (i) {
        0 -> WrapPresetSlide(style, Music.look, Modifier.size(PW, PH))
        1 -> WrapPresetSlide2(style, Music.look, Modifier.size(PW, PH), artist = true)
        else -> WrapPresetSlide2(style, Music.look, Modifier.size(PW, PH), artist = false)
    }
}
