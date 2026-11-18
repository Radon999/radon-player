package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** Repeats ([RepeatLoopRules]): each time a song plays again by itself with Repeat one on, kept in a file of this
 *  device's (repeats.json, beside the app's other data). The Repeats tab shows them. */
object RepeatLoops {
    /** Goes up when the count changes, so the screens showing it draw again. */
    var version by mutableStateOf(0)
        private set
    private var loaded: RepeatLoopRules.Saved? = null
    private fun file(): File? = runCatching { File(Music.ctx.filesDir, "repeats.json") }.getOrNull()

    @Synchronized fun saved(): RepeatLoopRules.Saved = loaded ?: (runCatching {
        file()?.takeIf { it.isFile }?.readText()?.let { JSON.decodeFromString(RepeatLoopRules.Saved.serializer(), it) } }.getOrNull() ?: RepeatLoopRules.Saved()).also { loaded = it }

    @Synchronized private fun keep(s: RepeatLoopRules.Saved) {
        loaded = s
        runCatching { file()?.writeText(JSON.encodeToString(RepeatLoopRules.Saved.serializer(), s)) }
    }

    private fun day(at: Long = System.currentTimeMillis()): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(at))
    /** The day a number of days ago, as the counts keep days ("": ever). */
    fun since(days: Int): String = if (days <= 0) "" else day(System.currentTimeMillis() - (days - 1) * 86_400_000L)

    /** The song has just started over by itself (Repeat one). */
    fun again(t: Track) { runCatching { keep(RepeatLoopRules.add(saved(), t, day(), System.currentTimeMillis())) }; version++ }
    /** Another song began: the run in one go is over. */
    fun moved() { val s = saved(); if (s.run != 0) keep(RepeatLoopRules.broke(s)) }

    /** How often this song was repeated, ever. */
    fun of(t: Track): Int = saved().songs[RepeatLoopRules.key(t)]?.n ?: 0
}
