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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll as composeVerticalScroll

// The PC's scroll bars: every list and long screen that scrolls has one on its right edge, to drag like File
// Explorer's (or click above or below it to move a screen at a time). The scroll wheel works as before. The phone
// scrolls by touch and has none. These stand in for Compose's own LazyColumn and verticalScroll in this package.

/** A list, as Compose's LazyColumn, with a scroll bar on the PC, and ↑ Top / ↓ Back (see [ListJumps]) on every
 *  list of the app, the phone's and the PC's. jumps: false leaves them off (a list with its own). */
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
    jumps: Boolean = !reverseLayout && userScrollEnabled,
    content: LazyListScope.() -> Unit,
) {
    if (!jumps) {
        androidx.compose.foundation.lazy.LazyColumn(modifier = modifier.scrollBar(state), state = state, contentPadding = contentPadding,
            reverseLayout = reverseLayout, verticalArrangement = verticalArrangement, horizontalAlignment = horizontalAlignment,
            flingBehavior = flingBehavior, userScrollEnabled = userScrollEnabled, content = content)
        return
    }
    // The list fills the place given to it (the box passes its size on), and the buttons sit over its bottom right
    androidx.compose.foundation.layout.Box(modifier, propagateMinConstraints = true) {
        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.scrollBar(state), state = state, contentPadding = contentPadding,
            reverseLayout = reverseLayout, verticalArrangement = verticalArrangement, horizontalAlignment = horizontalAlignment,
            flingBehavior = flingBehavior, userScrollEnabled = userScrollEnabled, content = content)
        ListJumps(state)
    }
}

/** ↑ Top: back to the top of a list, once it's scrolled down from there. ↓ Back: then straight back to where you were
 *  (sort the songs at the top of a queue, then carry on down it where you left off); it stays until you scroll back
 *  down there yourself, or use it. */
@Composable
fun androidx.compose.foundation.layout.BoxScope.ListJumps(state: LazyListState) {
    val down by remember(state) { androidx.compose.runtime.derivedStateOf { state.firstVisibleItemIndex > 0 } }
    // Where you were, for this screen only: another tab or page (one list shared by several screens) starts with none.
    // It had been kept across tabs: ↓ Back took you down a list you'd never been down, and then ↑ Top lost its place.
    val place = LocalPlace.current
    val wasState = remember(state, place) { mutableStateOf<Pair<Int, Int>?>(null) }
    var was by wasState
    val above by remember(state, place) { androidx.compose.runtime.derivedStateOf { wasState.value?.let { state.firstVisibleItemIndex < it.first } ?: false } }
    // Gone once you've been above it and then scrolled back down to it yourself (only then: the moment ↑ Top keeps a
    // new place, you're still at it, and that had wiped it out again)
    val left = remember(state, place) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(state, place) {
        androidx.compose.runtime.snapshotFlow { state.firstVisibleItemIndex }.collect { i ->
            val w = wasState.value ?: return@collect
            if (i < w.first) left.value = true else if (left.value) { wasState.value = null; left.value = false }
        }
    }
    // (made before the buttons can go: ↓ Back clears where you were, and with the list at its top both buttons go at
    // once. Made after this line, the scope went with them and took the scroll it had just started with it: ↓ Back did
    // nothing)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (!down && !above) return
    @Composable fun pill(ico: Ico, text: String, desc: String, go: () -> Unit) =
        androidx.compose.foundation.layout.Row(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)).clickable(onClick = go)
            .semantics { contentDescription = desc }.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(ico, androidx.compose.ui.graphics.Color.White, 15.dp)
            androidx.compose.material3.Text(" $text", style = T.sub.copy(color = androidx.compose.ui.graphics.Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
        }
    androidx.compose.foundation.layout.Column(Modifier.align(Alignment.BottomEnd).padding(end = if (onPhone) 10.dp else 18.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (above) was?.let { w -> pill(Ico.DOWN, "Back", "Back to where you were") {
            was = null; left.value = false
            scope.launch { state.animateScrollToItem(w.first, w.second) }
        } }
        if (down) pill(Ico.UP, "Top", "Back to the top") {
            was = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset; left.value = false
            scope.launch { state.animateScrollToItem(0) }
        }
    }
}

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
        // (how many rows there are, not everything about the list's layout: read whole, it redrew the screen's list at
        // every frame of a scroll)
        val rows by remember(state) { androidx.compose.runtime.derivedStateOf { state.layoutInfo.totalItemsCount } }
        androidx.compose.runtime.LaunchedEffect(place, rows) {
            if (returned || wasAt == null) return@LaunchedEffect
            if (state.isScrollInProgress || state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0) { returned = true; return@LaunchedEffect }
            if (rows > wasAt.first) { returned = true; state.scrollToItem(wasAt.first, wasAt.second) }
        }
    }
    // (4.0.14 to 4.0.33 had a strip of letters down the right side, to jump to a letter: taken out. It made long
    // lists slow, the 24-bit FLACs most of all, and was hardly used. The lists hand their rows straight on, so nothing
    // works out where the letters start any more.)
    LazyColumn(modifier, state = state, content = content)
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
