package org.omarchy.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import org.omarchy.music.FrameLogRules

/** Record slow frames (Settings › Problems): while it is on, every frame the app draws is timed, and the ones that
 *  took too long are kept with the screen they were on, to copy and send. Off unless you turn it on, and off again by
 *  itself after ten minutes: timing every frame keeps the screen drawing, which costs battery. */
object FrameWatch {
    var on by mutableStateOf(false)
    /** What's recorded (shown in Settings, refreshed about once a second). */
    var log by mutableStateOf(FrameLogRules.Log())
        private set

    fun clear() { log = FrameLogRules.Log() }

    /** At the root of the app: times the frames while recording. where: the screen on show. */
    @Composable
    fun Watch(where: () -> String) {
        if (!on) return
        LaunchedEffect(Unit) {
            var l = log
            var last = 0L
            var shownAt = 0L
            val began = System.currentTimeMillis()
            while (on) {
                val t = withFrameNanos { it }
                if (last != 0L) l = l.plus(((t - last) / 1_000_000).toInt(), where())
                last = t
                val now = System.currentTimeMillis()
                if (now - shownAt > 1000) { shownAt = now; log = l }
                if (now - began > FrameLogRules.RUN_MS) { on = false; org.omarchy.music.Music.toast("Recording slow frames stopped after ten minutes") }
            }
            log = l
        }
    }
}
