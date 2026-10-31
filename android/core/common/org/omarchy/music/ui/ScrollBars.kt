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
