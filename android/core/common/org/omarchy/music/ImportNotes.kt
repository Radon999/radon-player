package org.omarchy.music

/** How each import last went (Spotify, Apple Music, Deezer, YouTube Music and Tidal history; playlists; backups): it
 *  worked and what it added, or it failed and why. Kept with the settings, shown under the import's line until the next. */
object ImportNotes {
    private fun key(kind: String) = "import_note_$kind"

    /** The reason an import failed, in words: its message, else what kind of error it was, with its cause. */
    fun why(err: Throwable): String {
        val main = err.message?.takeIf { it.isNotBlank() } ?: (err::class.simpleName ?: "an unknown error")
        val cause = err.cause?.let { c -> c.message?.takeIf { it.isNotBlank() && it != err.message } ?: c::class.simpleName }
        return if (cause != null) "$main ($cause)" else main
    }

    fun ok(kind: String, text: String) = Prefs.setText(key(kind), "ok|${now()}|$text")
    fun failed(kind: String, err: Throwable) = Prefs.setText(key(kind), "fail|${now()}|${why(err)}")

    /** The last time: whether it worked, when (seconds) and what happened; null if never. */
    fun last(kind: String): Triple<Boolean, Double, String>? {
        val p = Prefs.text(key(kind)).split("|", limit = 3)
        if (p.size < 3) return null
        return Triple(p[0] == "ok", p[1].toDoubleOrNull() ?: 0.0, p[2])
    }
}
