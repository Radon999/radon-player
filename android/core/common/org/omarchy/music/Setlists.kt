package org.omarchy.music

/** Setlists ([SetlistRules]): an artist's recent shows and the songs played at them, asked of setlist.fm with your own
 *  key. Setlists rarely change once written, so an artist's answer is kept for twelve hours, in memory and on disk (an
 *  artist's page opened again, or the app started again, doesn't ask again), and setlist.fm is asked at most twice a
 *  second, as it wants. The key is this device's setting and goes nowhere but to setlist.fm, with the question.
 *  Each song of a show is found as yours first (your file or download), else on Tidal (YouTube Music without it). */
object Setlists {
    const val KEY = "setlistfm_key"
    fun key(): String = Prefs.text(KEY).trim()
    val on: Boolean get() = key().isNotEmpty()

    private const val KEEP_MS = 12 * 3600_000L
    private val kept = HashMap<String, Pair<Long, List<SetlistRules.Setlist>>>()
    private val cacheDir get() = java.io.File(Music.ctx.cacheDir, "setlists")

    /** (blocking) The artist's recent shows, the newest first. Throws, with why in words, when setlist.fm can't be asked. */
    fun find(artist: String): List<SetlistRules.Setlist> {
        val k = norm(artist) + "|" + key().hashCode()
        synchronized(kept) { kept[k]?.takeIf { System.currentTimeMillis() - it.first < KEEP_MS }?.let { return it.second } }
        val file = java.io.File(cacheDir, Integer.toHexString(norm(artist).hashCode()) + ".json")
        val saved = file.takeIf { it.isFile && System.currentTimeMillis() - it.lastModified() < KEEP_MS }?.let { f -> runCatching { f.readText() }.getOrNull() }
        val text = saved ?: ask(artist).also { t -> runCatching { cacheDir.mkdirs(); file.writeText(t) } }
        val got = SetlistRules.parse(runCatching { JSON.parseToJsonElement(text) }.getOrNull(), artist)
        return got.also { synchronized(kept) { if (kept.size > 200) kept.clear(); kept[k] = System.currentTimeMillis() to it } }
    }

    /** One question to setlist.fm, half a second at least after the last (a busy moment is waited out once). */
    private fun ask(artist: String): String {
        for (round in 0..1) {
            pace()
            try { return http(SetlistRules.url(artist), SetlistRules.headers(key())) }
            catch (e: HttpError) {
                if (e.code == 404) return "{}"   // setlist.fm's way of saying it has no shows of theirs
                if (e.code == 429 && round == 0) { Thread.sleep(1500); continue }
                throw RuntimeException(SetlistRules.why(e.code))
            }
            catch (e: Exception) { throw RuntimeException(SetlistRules.why(0)) }
        }
        throw RuntimeException(SetlistRules.why(429))
    }

    private var lastAsked = 0L
    @Synchronized private fun pace() {
        val wait = lastAsked + 550 - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        lastAsked = System.currentTimeMillis()
    }

    /** (blocking) A show's song as a song to play: your own copy first, else the best match on Tidal (YouTube Music
     *  without it); for a song of someone else's they played, the original's when theirs can't be found. Null when
     *  nothing matches. */
    fun songOf(artist: String, s: SetlistRules.Song): Track? {
        val src = Music.sources["tidal"]?.takeIf { it.signedIn } ?: Music.sources["ytm"]
        for (by in listOf(artist, s.cover).filter { it.isNotBlank() }.distinct()) {
            val asked = Track("setlist:" + norm(by) + "|" + norm(s.name), source = "setlist", title = s.name, artist = by)
            val mine = Music.localOf(asked)
            if (mine.id != asked.id) return mine
            val hit = src?.let { runCatching { Music.matchSong(it, by, s.name, 0).first }.getOrNull() }
            if (hit != null) return Music.localOf(hit)
        }
        return null
    }

    /** Whether a song found is yours (a file or a download) rather than streamed. */
    fun yours(t: Track): Boolean = t.isFile || Music.lib.has(t.id)
}
