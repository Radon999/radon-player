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

/** What a Personalization preset looks like, in live screens to swipe through ([PreviewPager]): Now playing (its look, effects and
 *  visualizer, both of Dual's), My music and Albums in its look, theme and layout, three slides of Wrapped in its
 *  Wrapped style, and its lyrics card. On the PC (where the
 *  picture sits beside the menu, out of the pointer's way) it turns to the next one by itself. */
@Composable
fun PresetPreview(key: String) {
    val p = MyPresets.of(key) ?: return
    val pw = 240.dp; val ph = 300.dp
    val fx = remember(p.key, p.fx, p.own) { if (p.own) null else p.fx.split(';').map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
    val lay = remember(p.layout) { UiLayouts.all().firstOrNull { it.key == p.layout } ?: UiLayouts.BUILT_IN.first() }
    val wrap = p.wrapped.ifEmpty { Music.wrappedStyle }
    // Every screen the preset changes, live: Now playing with its visualizers moving, the lists in its look, theme and
    // layout with its effects behind them, three slides of Wrapped in its style, and its lyrics card
    PreviewPager(key, listOf("Now playing", "My music", "Albums", "Wrapped", "Top artist", "Your year", "Lyrics card"), pw, ph) { i ->
        when (i) {
            0 -> LookPreview(p.look, wide = false, fx = fx, visStyle = p.npVis.ifEmpty { Vis.nowStyle() },
                bottom = if (p.dual) p.npBottom.takeIf { it.isNotEmpty() } else null,
                visColor = p.visColor.ifEmpty { Vis.color() }, pw = pw, ph = ph)
            1 -> MusicPagePreview(p.look, p.theme, lay, fx, pw, ph)
            2 -> MusicPagePreview(p.look, p.theme, lay, fx, pw, ph, albums = true)
            3 -> WrapPresetSlide(wrap, p.look, Modifier.size(pw, ph))
            4 -> WrapPresetSlide2(wrap, p.look, Modifier.size(pw, ph), artist = true)
            5 -> WrapPresetSlide2(wrap, p.look, Modifier.size(pw, ph), artist = false)
            else -> PresetCard(p.card.ifEmpty { LyricsCards.picked() })
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
