package org.omarchy.music

/** The wake-up timer ([WakeUpRules]): at the time set, the playlist chosen starts playing (or a hundred of your songs
 *  shuffled, when none is chosen). A timer inside the app: it goes off only while the app is running. */
object WakeUp {
    const val ON = "wake_on"
    const val TIME = "wake_time"       // "7:30"
    const val WHAT = "wake_playlist"   // a playlist of yours (its id); "": your songs, shuffled
    private const val FIRED = "wake_fired_day"

    /** Called every so often while the app is open. */
    fun tick() {
        val now = java.time.LocalDateTime.now()
        if (!WakeUpRules.due(Prefs.on(ON), WakeUpRules.minutes(Prefs.text(TIME)), now.hour * 60 + now.minute, now.dayOfYear,
                Prefs.text(FIRED).toIntOrNull() ?: -1)) return
        Prefs.setText(FIRED, now.dayOfYear.toString())
        go()
    }

    private fun go() {
        val pl = Prefs.text(WHAT).takeIf { it.isNotEmpty() }?.let { Music.lists.get(it) }
        Music.bg({ (pl?.tracks?.takeIf { it.isNotEmpty() } ?: Music.mySongs().shuffled().take(100)).map { Music.mineOf(it) } }) { songs, _ ->
            if (songs.isNullOrEmpty()) { Music.toast("Wake-up timer: there was nothing to play"); return@bg }
            Music.playFrom(songs, 0, false)
            Music.toast("Wake-up timer: playing " + (pl?.name?.let { "“$it”" } ?: "your songs, shuffled"))
        }
    }
}
