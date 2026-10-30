package org.omarchy.music

/** The PC app starting: the same as the phone's MusicApp, then the engine. */
object PcApp {
    @Volatile private var started = false

    fun start(): Ctx {
        val c = Ctx.pc
        if (started) return c
        started = true
        log(c)
        Crashes.init(c)
        val imported = runCatching { PythonImport.run(c) }.getOrDefault(emptyList())   // the Python app's data, once, before anything reads its files
        Music.init(c)
        if (imported.isNotEmpty()) Music.toast("Brought over from the old app: " + imported.joinToString(" · "))
        LastFm.init(c)
        SpotifyHistory.init(c)
        DeezerHistory.init(c)
        YouTubeHistory.init(c)
        TidalHistory.init(c)
        AppleHistory.init(c)
        NewReleases.init(c)
        Stats.init(c)
        Pictures.init(c)
        Services.start(c)
        RemoteHost.init(c)   // the phone remote, if it was on
        MediaKeys.start()
        Discord.init()
        MediaSession.start()   // Windows' media controls: the flyout, widgets and wallpapers see what's playing
        Updater.startTimer()   // a new release: looked for at start and every 12 hours
        MpvEngine.setLevel(Prefs.num("pc_volume", 1f), save = false)   // the volume you left it at
        Music.player = MpvEngine()
        Music.playerReady()
        return c
    }

    /** On Windows the app has no console: what it would print goes to log.txt in its folder (the last runs, kept
     *  small), with a note of everything it's doing whenever the window stops answering for 10 seconds. */
    private fun log(c: Ctx) {
        if (!System.getProperty("os.name").lowercase().contains("win")) return
        runCatching {
            val f = java.io.File(c.filesDir, "log.txt")
            if (f.length() > 1_000_000) f.delete()
            val ps = java.io.PrintStream(java.io.FileOutputStream(f, true), true, "UTF-8")
            System.setErr(ps); System.setOut(ps)
            ps.println("--- ${java.time.LocalDateTime.now()} · Radon Player $appVersion · Java ${System.getProperty("java.version")} · ${System.getProperty("os.name")}")
        }
        Thread({
            var told = false
            while (true) {
                Thread.sleep(5000)
                val answered = java.util.concurrent.CountDownLatch(1)
                javax.swing.SwingUtilities.invokeLater { answered.countDown() }
                val ok = answered.await(10, java.util.concurrent.TimeUnit.SECONDS)
                if (!ok && !told) {
                    told = true
                    System.err.println("The window stopped answering (${java.time.LocalTime.now()}). What everything was doing:")
                    for ((t, st) in Thread.getAllStackTraces()) {
                        System.err.println("  ${t.name} (${t.state})")
                        st.take(25).forEach { System.err.println("      at $it") }
                    }
                } else if (ok) told = false
            }
        }, "watchdog").apply { isDaemon = true }.start()
    }
}
