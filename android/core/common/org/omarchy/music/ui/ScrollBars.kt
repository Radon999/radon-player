package org.omarchy.music.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll as composeVerticalScroll

// The PC's scroll bars: every list and long screen that scrolls has one on its right edge, to drag like File
// Explorer's (or click above or below it to move a screen at a time). The scroll wheel works as before. The phone
// scrolls by touch and has none. These stand in for Compose's own LazyColumn and verticalScroll in this package.

/** A list, as Compose's LazyColumn, with a scroll bar on the PC. */
@Composable
fun LazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) = androidx.compose.foundation.lazy.LazyColumn(modifier = modifier.scrollBar(state), state = state, contentPadding = contentPadding,
    reverseLayout = reverseLayout, verticalArrangement = verticalArrangement, horizontalAlignment = horizontalAlignment,
    flingBehavior = flingBehavior, userScrollEnabled = userScrollEnabled, content = content)

/** Which screen is in front (a tab and what's open in it, or a page and how deep it is): what a screen's list keeps
 *  its place under. Main says; "" where nothing is kept. */
val LocalPlace = androidx.compose.runtime.compositionLocalOf { "" }

/** Where each screen's list was scrolled to when you left it (the row, how far into it). */
private val placeScroll = HashMap<String, Pair<Int, Int>>()

/** A screen's list that keeps its place: open something from far down it (an album from an artist's page, a song's
 *  artist from a playlist) and Back returns you to where you were, not to the top. The place is kept per screen (see
 *  [LocalPlace]): a page opened afresh starts at its top. A screen fills in again as it loads, so its place is gone
 *  back to once the list is long enough to hold it, unless you've started scrolling yourself meanwhile. */
@Composable
fun PlaceColumn(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    val place = LocalPlace.current
    // Started at its place at once (no jump from the top) when the list is there in full as the screen appears, which
    // it is for a list already in memory; the step below is only for a list that fills in afterwards.
    val state = androidx.compose.runtime.key(place) {
        val at = if (place.isEmpty()) null else placeScroll[place]
        rememberLazyListState(at?.first ?: 0, at?.second ?: 0)
    }
    if (place.isNotEmpty()) {
        val wasAt = remember(place) { placeScroll[place] }
        var returned by remember(place) { mutableStateOf(wasAt == null) }
        androidx.compose.runtime.DisposableEffect(place) {
            onDispose {
                if (placeScroll.size > 300) placeScroll.clear()
                placeScroll[place] = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
            }
        }
        val rows = state.layoutInfo.totalItemsCount
        androidx.compose.runtime.LaunchedEffect(place, rows) {
            if (returned || wasAt == null) return@LaunchedEffect
            if (state.isScrollInProgress || state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0) { returned = true; return@LaunchedEffect }
            if (rows > wasAt.first) { returned = true; state.scrollToItem(wasAt.first, wasAt.second) }
        }
    }
    // Jump to a letter: a list in alphabetical order says where each letter starts (JumpIndex), and a strip of letters
    // down the side goes there
    val jump = remember(place) { JumpIndex() }
    androidx.compose.foundation.layout.Box(modifier) {
        LazyColumn(Modifier.fillMaxWidth(), state = state) { jump.begin(); CountingScope(this, jump).content(); jump.told.trySend(Unit) }
        // The list's rows are worked out as it is measured, which is after this is drawn: so the letters are handed on
        // when the list says it has them (4.0.14 looked once, before there were any, and the strip never showed)
        androidx.compose.runtime.LaunchedEffect(jump) { for (x in jump.told) jump.publish() }
        val letters = jump.shown
        if (letters.isNotEmpty()) LetterStrip(letters, state, Modifier.align(Alignment.CenterEnd))
    }
}

/** Where each letter starts in the list on screen (the letter, the row), as the list's own rows say while they are
 *  laid out. Gathered in a plain list first: the rows are laid out inside a read-only step of drawing, where nothing
 *  the screen watches may be changed, and handed on to [shown] straight after. */
class JumpIndex {
    @Volatile private var found: List<Pair<String, Int>> = emptyList()
    /** Said each time the rows have been gone through: [publish] then. */
    val told = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    var shown by mutableStateOf<List<Pair<String, Int>>>(emptyList())
        private set
    fun begin() { found = emptyList() }
    /** The first list in alphabetical order on the screen has the strip (a screen with two such lists keeps the first's). */
    fun add(anchors: List<Pair<String, Int>>) { if (found.isEmpty()) found = anchors }
    fun publish() { if (shown != found) shown = found }
}

/** The list's rows passed on as they are, counted: so a list can say which row each of its letters starts on. */
class CountingScope(private val inner: LazyListScope, val jump: JumpIndex) : LazyListScope by inner {
    var count = 0
        private set
    override fun item(key: Any?, contentType: Any?, content: @Composable androidx.compose.foundation.lazy.LazyItemScope.() -> Unit) {
        count++; inner.item(key, contentType, content)
    }
    override fun items(count: Int, key: ((index: Int) -> Any)?, contentType: (index: Int) -> Any?, itemContent: @Composable androidx.compose.foundation.lazy.LazyItemScope.(index: Int) -> Unit) {
        this.count += count; inner.items(count, key, contentType, itemContent)
    }
}

/** The strip of letters down a list's right side: tap one, or run a finger (or the pointer, held) down them. */
@Composable
private fun LetterStrip(letters: List<Pair<String, Int>>, state: LazyListState, modifier: Modifier) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var height by remember { mutableStateOf(1) }
    fun go(y: Float) {
        val i = ((y / height) * letters.size).toInt().coerceIn(0, letters.size - 1)
        scope.launch { state.scrollToItem(letters[i].second) }
    }
    androidx.compose.foundation.layout.Column(modifier.padding(end = if (onPhone) 2.dp else 14.dp)
        .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(C.light.copy(alpha = 0.55f))
        .onSizeChanged { height = it.height.coerceAtLeast(1) }
        .pointerInput(letters) { awaitEachGesture {
            val down = awaitFirstDown(); down.consume(); go(down.position.y)
            while (true) { val e = awaitPointerEvent(); val ch = e.changes.firstOrNull() ?: break; if (!ch.pressed) break; ch.consume(); go(ch.position.y) }
        } }
        .padding(horizontal = 4.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        letters.forEach { (l, _) -> androidx.compose.material3.Text(l, style = T.sub.copy(fontSize = if (letters.size > 20) 9.sp else 10.sp, lineHeight = 12.sp, color = C.accent)) }
    }
}

/** Scrolling up and down, as Compose's verticalScroll, with a scroll bar on the PC. */
fun Modifier.verticalScroll(state: ScrollState, enabled: Boolean = true, flingBehavior: FlingBehavior? = null,
                            reverseScrolling: Boolean = false): Modifier =
    scrollBar(state).composeVerticalScroll(state, enabled, flingBehavior, reverseScrolling)

/** How far down, how long it all is and how much shows, in pixels (lists: estimated from the rows showing). */
private class Extent(val offset: Float, val total: Float, val shown: Float)

private fun extent(state: ScrollableState, height: Float): Extent? = when (state) {
    is ScrollState -> Extent(state.value.toFloat(), height + state.maxValue, height)
    is LazyListState -> {
        val info = state.layoutInfo
        val rows = info.visibleItemsInfo
        if (rows.isEmpty()) null else {
            val each = (rows.sumOf { it.size } + info.mainAxisItemSpacing * (rows.size - 1)).toFloat() / rows.size
            val shown = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            val total = maxOf(each * info.totalItemsCount + info.beforeContentPadding + info.afterContentPadding, shown)
            Extent(state.firstVisibleItemIndex * each + state.firstVisibleItemScrollOffset, total, shown)
        }
    }
    else -> null
}

/** The bar's thumb: its top and height on a track height tall. */
private fun thumb(e: Extent, height: Float, least: Float): Pair<Float, Float> {
    val h = maxOf(height * e.shown / e.total, least).coerceAtMost(height)
    val frac = if (e.total > e.shown) (e.offset / (e.total - e.shown)).coerceIn(0f, 1f) else 0f
    return (height - h) * frac to h
}

/** A scroll bar for state on the right edge (the PC only): shown while there's more than fits, drawn over the
 *  content; drag it, or click the track above or below it to go a screen up or down. */
fun Modifier.scrollBar(state: ScrollableState): Modifier = if (onPhone) this else composed {
    var lit by remember { mutableStateOf(false) }   // hovered or dragging: brighter
    this.drawWithContent {
        drawContent()
        if (!state.canScrollForward && !state.canScrollBackward) return@drawWithContent
        val e = extent(state, size.height) ?: return@drawWithContent
        val (top, h) = thumb(e, size.height, 28.dp.toPx())
        val w = (if (lit) 8 else 6).dp.toPx()
        drawRoundRect(C.fg.copy(alpha = if (lit) 0.55f else 0.3f), Offset(size.width - w - 3.dp.toPx(), top), Size(w, h), CornerRadius(w / 2))
    }.pointerInput(state) {
        awaitPointerEventScope {
            while (true) {   // brighter while the pointer is over the bar
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val x = ev.changes.firstOrNull()?.position?.x ?: continue
                if (ev.type == PointerEventType.Move || ev.type == PointerEventType.Enter || ev.type == PointerEventType.Exit)
                    lit = ev.type != PointerEventType.Exit && x >= size.width - 12.dp.toPx() && (state.canScrollForward || state.canScrollBackward)
            }
        }
    }.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.x < size.width - 12.dp.toPx() || !(state.canScrollForward || state.canScrollBackward)) return@awaitEachGesture
            val e = extent(state, size.height.toFloat()) ?: return@awaitEachGesture
            down.consume()
            val (top, h) = thumb(e, size.height.toFloat(), 28.dp.toPx())
            if (down.position.y < top || down.position.y > top + h) {   // the track: a screen up or down
                state.dispatchRawDelta(if (down.position.y < top) -e.shown * 0.9f else e.shown * 0.9f)
                while (true) { val ev = awaitPointerEvent(PointerEventPass.Initial); ev.changes.forEach { it.consume() }; if (ev.changes.none { it.pressed }) break }
                return@awaitEachGesture
            }
            lit = true
            var last = down.position.y
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                c.consume()
                if (!c.pressed) break
                val now = extent(state, size.height.toFloat()) ?: break
                val track = size.height - thumb(now, size.height.toFloat(), 28.dp.toPx()).second
                if (track > 0 && now.total > now.shown) state.dispatchRawDelta((c.position.y - last) * (now.total - now.shown) / track)
                last = c.position.y
            }
            lit = false
        }
    }
}
