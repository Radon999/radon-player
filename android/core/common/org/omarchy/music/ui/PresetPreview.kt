package org.omarchy.music.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.omarchy.music.Music

/** What a Personalization preset looks like, in pictures to swipe through: Now playing (its look, effects and
 *  visualizer, both of Dual's), one slide of Wrapped in its Wrapped style and its lyrics card. On the PC (where the
 *  picture sits beside the menu, out of the pointer's way) it turns to the next one by itself. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PresetPreview(key: String) {
    val p = MyPresets.of(key) ?: return
    val names = listOf("Now playing", "Wrapped", "Lyrics card")
    val pager = rememberPagerState { names.size }
    val scope = rememberCoroutineScope()
    if (!onPhone) LaunchedEffect(key) {
        while (true) { delay(2600); pager.animateScrollToPage((pager.currentPage + 1) % names.size) }
    }
    val pw = 240.dp; val ph = 300.dp
    val fx = remember(p.key, p.fx, p.own) { if (p.own) null else p.fx.split(';').map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, Modifier.size(pw, ph), pageSpacing = 12.dp) { i ->
            Box(Modifier.size(pw, ph), contentAlignment = Alignment.Center) {
                when (i) {
                    0 -> LookPreview(p.look, wide = false, fx = fx, visStyle = p.npVis.ifEmpty { Vis.nowStyle() },
                        bottom = if (p.dual) p.npBottom.takeIf { it.isNotEmpty() } else null,
                        visColor = p.visColor.ifEmpty { Vis.color() }, pw = pw, ph = ph)
                    1 -> WrapPresetSlide(p.wrapped.ifEmpty { Music.wrappedStyle }, p.look, Modifier.size(pw, ph))
                    else -> PresetCard(p.card.ifEmpty { LyricsCards.picked() })
                }
            }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEachIndexed { i, n ->
                val on = pager.currentPage == i
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) C.accent.copy(alpha = 0.18f) else C.fg.copy(alpha = 0.05f))
                    .clickable { scope.launch { pager.animateScrollToPage(i) } }.padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(n, style = T.sub.copy(color = if (on) C.accent else C.muted))
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

/** A preset's lyrics card, with sample words (and the playing song's name and cover, when one plays). */
@Composable
internal fun PresetCard(card: String) {
    val song = Music.current() ?: org.omarchy.music.Track("sample", title = "Song title", artist = "Artist")
    val bmp = remember(card, song.id) {
        lyricsCard(listOf("Your words go here", "on the card you share", "in this preset's style"), song,
            LyricsCards.of(card), card == LyricsCards.NOW)
    }
    Image(bmp, "Lyrics card", Modifier.size(240.dp, 300.dp).clip(RoundedCornerShape(12.dp)))
}
