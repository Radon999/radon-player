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

    /** A page in your browser. */
    fun open(url: String) {
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
    }
}
