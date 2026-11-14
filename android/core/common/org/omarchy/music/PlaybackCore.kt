package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The queue and what's playing: the playback service (docs/rebuild/RADON_REBUILD_PLAN.md, Phase 9), taken out of
 *  [Music] piece by piece with nothing behaving differently. It owns the queue's own state; [Music] keeps every name
 *  the rest of the app uses and hands on to here. The rules themselves (what comes after a song, what counts as a
 *  play, when a song fades into the next) are in the shared code, each tested alone: [QueueRules], [PlayRules],
 *  [EndWatch], [CrossfadeRules].
 *
 *  Everything here runs on the app's main thread, as [Music] does. */
object PlaybackCore {
    // ---- the queue's own state (what the screens show: Compose state, so a change redraws) ----
    var queue by mutableStateOf(listOf<Track>())
    var index by mutableStateOf(-1)
    // Multiple queues: playing from another list (a folder, an album, an artist, a playlist…) starts a queue
    // of its own; the one you were in is kept, where you were in it, to go back to.
    var queues by mutableStateOf(listOf<SavedQueue>())   // the other queues, the last one used first
    var queueName by mutableStateOf("")
    var queueId = ""
    var repeat by mutableStateOf("off")
    var shuffled = false           // the queue was shuffled: repeat-all shuffles it again
    var radioFrom: String? = null  // the song the queue's radio (∞) grew from

    fun current(): Track? = queue.getOrNull(index)
    fun isRadio() = radioFrom != null
}
