package org.omarchy.music

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Whether the app is on the screen (the phone: its window started, the mini player too; off once you go to the home
 *  screen, another app, or the screen goes off). Timers that only serve what's on the screen wait while it isn't, and
 *  carry on the moment it's back, so nothing you see changes and the phone sleeps more. The PC: always on (a minimised
 *  window still plays, and nothing there waits on it). */
object AppVisible {
    private val state = MutableStateFlow(true)
    /** For whatever wants to follow it as it changes (start and stop something). */
    val flow: StateFlow<Boolean> get() = state
    val on: Boolean get() = state.value

    /** (the main thread) The window came on the screen, or left it. */
    fun set(shown: Boolean) { state.value = shown }

    /** Waits until the app is on the screen (at once when it is). */
    suspend fun awaitShown() { if (!state.value) state.first { it } }

    /** A timer's wait: shownMs while the app is on the screen; hiddenMs while it isn't, cut short the moment it comes
     *  back, so nothing on the screen ever waits for the longer one. */
    suspend fun nap(shownMs: Long, hiddenMs: Long) {
        if (state.value) delay(shownMs) else withTimeoutOrNull(hiddenMs) { state.first { it } }
    }
}
