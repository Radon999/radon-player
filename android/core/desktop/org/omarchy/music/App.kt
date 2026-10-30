package org.omarchy.music

/** The PC app starting: the same as the phone's MusicApp, then the engine. */
object PcApp {
    @Volatile private var started = false

    fun start(): Ctx {
        val c = Ctx.pc
        if (started) return c
        started = true
        Crashes.init(c)
        Music.init(c)
        LastFm.init(c)
        SpotifyHistory.init(c)
        DeezerHistory.init(c)
        AppleHistory.init(c)
        NewReleases.init(c)
        Stats.init(c)
        Pictures.init(c)
        Services.start(c)
        RemoteHost.init(c)   // the phone remote, if it was on
        MpvEngine.setLevel(Prefs.num("pc_volume", 1f), save = false)   // the volume you left it at
        Music.player = MpvEngine()
        Music.playerReady()
        return c
    }
}
