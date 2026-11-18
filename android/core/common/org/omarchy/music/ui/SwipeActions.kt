package org.omarchy.music.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.omarchy.music.ListenLater
import org.omarchy.music.Music
import org.omarchy.music.Prefs
import org.omarchy.music.Track
import kotlin.math.roundToInt

/** Swipe a song (the phone; Settings › Playback › Swipe a song): drag a song's row to the right or to the left and
 *  let go, and it does what you chose for that side: add it to the queue, play it next, put it on Listen later,
 *  favorite it, or add it to a playlist. Both sides start on Nothing, so no list behaves differently until you
 *  choose. A short drag that doesn't reach the mark does nothing, and scrolling the list up and down is untouched. */
object SwipeActions {
    const val RIGHT = "swipe_right"
    const val LEFT = "swipe_left"
    val CHOICES = listOf("" to "Nothing", "queue" to "Add to the queue", "next" to "Play next", "later" to "Add to Listen later",
        "favorite" to "Favorite or unfavorite", "playlist" to "Add to a playlist…")
    private var version by mutableStateOf(0)
    fun right(): String = version.let { Prefs.text(RIGHT) }
    fun left(): String = version.let { Prefs.text(LEFT) }
    fun set(side: String, what: String) { Prefs.setText(side, what); version++ }

    fun run(what: String, t: Track, ui: Ui) {
        when (what) {
            "queue" -> Music.enqueue(listOf(t))
            "next" -> Music.enqueue(listOf(t), "next")
            "later" -> ListenLater.add(listOf(t))
            "favorite" -> Music.setFavorite(listOf(t))
            "playlist" -> ui.pickPlaylistFor = listOf(t)
        }
    }
}

/** The row follows the finger a little way; let go past the mark and that side's action is done. */
fun Modifier.swipeToAct(t: Track, ui: Ui): Modifier = composed {
    val r = SwipeActions.right(); val l = SwipeActions.left()
    if (!onPhone || (r.isEmpty() && l.isEmpty()) || ui.selecting) return@composed this
    val mark = with(LocalDensity.current) { 72.dp.toPx() }
    var dx by remember(t.id) { mutableStateOf(0f) }
    this.offset { IntOffset(dx.roundToInt(), 0) }.pointerInput(t.id, r, l) {
        detectHorizontalDragGestures(
            onDragEnd = { val d = dx; dx = 0f; if (d >= mark && r.isNotEmpty()) SwipeActions.run(r, t, ui) else if (d <= -mark && l.isNotEmpty()) SwipeActions.run(l, t, ui) },
            onDragCancel = { dx = 0f }) { change, amount ->
            val nx = (dx + amount).coerceIn(if (l.isEmpty()) 0f else -mark * 1.3f, if (r.isEmpty()) 0f else mark * 1.3f)
            if (nx != dx) { change.consume(); dx = nx }
        }
    }
}
