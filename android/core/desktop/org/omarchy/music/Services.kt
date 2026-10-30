package org.omarchy.music

/** The services on the PC: YouTube Music and Apple Music (signed in with the cookies you copy from your browser
 *  after signing in there: the PC has no web page inside the app to read them from), Spotify with its own login,
 *  Tidal with its keys. */
object Services {
    private var started = false
    private lateinit var ctx: Ctx

    fun start(c: Ctx) {
        if (started) return
        started = true
        ctx = c
        connect("ytm"); connect("apple"); connect("spotify"); connect("deezer")
    }

    private fun connect(key: String) {
        Music.bg({
            when (key) {
                "ytm" -> YouTubeMusic(Prefs.text(YTM_KEY).takeIf { "__Secure-3PAPISID=" in it })
                "apple" -> AppleMusic(ctx, Prefs.text(APPLE_KEY).takeIf { "media-user-token=" in it })
                "deezer" -> Deezer(Prefs.text(DEEZER_KEY).removePrefix("arl=").takeIf { it.length > 40 })
                else -> Spotify(ctx)
            } as Source
        }) { src, err -> Music.sourceReady(key, src, err) }
    }

    private const val YTM_KEY = "ytm_cookie"
    private const val APPLE_KEY = "apple_cookie"
    private const val DEEZER_KEY = "deezer_cookie"
    fun deezerCookiesReady() = Prefs.text(DEEZER_KEY).removePrefix("arl=").length > 40
    fun deezerSignedIn() { connect("deezer"); Music.toast("Signed in to Deezer") }

    fun ytmCookiesReady() = "__Secure-3PAPISID=" in Prefs.text(YTM_KEY)
    fun appleCookiesReady() = "media-user-token=" in Prefs.text(APPLE_KEY)

    /** The cookies you pasted (the Cookie line from your browser, or for Apple just the media-user-token): kept if
     *  they hold the sign-in. */
    fun setCookies(key: String, pasted: String): Boolean {
        var c = pasted.trim().removePrefix("Cookie:").removePrefix("cookie:").trim()
        if (key == "deezer") {   // the arl cookie's value (or a Cookie line with it in)
            val arl = c.split(";").map { it.trim() }.firstOrNull { it.startsWith("arl=") }?.removePrefix("arl=") ?: c.takeIf { "=" !in it && ";" !in it }
            if (arl == null || arl.length <= 40) return false
            Prefs.setText(DEEZER_KEY, arl)
            return true
        }
        if (key == "apple" && "=" !in c && c.length > 20) c = "media-user-token=$c"
        val ok = if (key == "ytm") "__Secure-3PAPISID=" in c else "media-user-token=" in c
        if (ok) Prefs.setText(if (key == "ytm") YTM_KEY else APPLE_KEY, c)
        return ok
    }

    fun ytmSignedIn() { connect("ytm"); Music.toast("Signed in to YouTube Music") }
    fun appleSignedIn() { connect("apple"); Music.toast("Signed in to Apple Music") }
    fun signOut(key: String) {
        Prefs.setText(if (key == "ytm") YTM_KEY else if (key == "deezer") DEEZER_KEY else APPLE_KEY, "")
        connect(key)
        Music.toast("Signed out of ${SOURCES[key]}")
    }

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
