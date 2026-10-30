package org.omarchy.music

/** The services on the PC: YouTube Music and Apple Music to search (signing in to them comes with the PC's own
 *  sign-in pages, later), Spotify with its own login, Tidal with its keys. */
object Services {
    private var started = false
    private lateinit var ctx: Ctx

    fun start(c: Ctx) {
        if (started) return
        started = true
        ctx = c
        connect("ytm"); connect("apple"); connect("spotify")
    }

    private fun connect(key: String) {
        Music.bg({
            when (key) {
                "ytm" -> YouTubeMusic(null)
                "apple" -> AppleMusic(ctx, null)
                else -> Spotify(ctx)
            } as Source
        }) { src, err -> Music.sourceReady(key, src, err) }
    }

    fun ytmCookiesReady() = false
    fun appleCookiesReady() = false
    fun ytmSignedIn() = connect("ytm")
    fun appleSignedIn() = connect("apple")
    fun signOut(key: String) = connect(key)

    // ---- Spotify ----
    private val spotify get() = Music.sources["spotify"] as? Spotify

    fun spotifyLoginUrl(): String = spotify?.loginUrl() ?: "about:blank"

    fun spotifySetClient(id: String): Boolean {
        val sp = spotify ?: return false.also { Music.toast("Spotify isn't ready yet") }
        if (!sp.setClient(id)) { Music.toast("That isn't a Spotify Client ID: 32 letters and digits"); return false }
        return true
    }

    fun spotifyFinish(redirected: String) {
        val sp = spotify ?: return
        Music.bg({ sp.finishLogin(redirected) }) { _, err ->
            if (err != null) { Music.toast("Spotify didn't connect: ${err.message?.take(80)}"); return@bg }
            Music.toast("Spotify connected" + if (sp.user.isNotEmpty()) " as ${sp.user}" else "")
            Music.sourceReady("spotify", sp, null)
        }
    }

    fun spotifyDisconnect() {
        val sp = spotify ?: return
        sp.disconnect()
        Music.sourceReady("spotify", sp, null)
    }

    /** A page in your browser. */
    fun open(url: String) {
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
    }
}
