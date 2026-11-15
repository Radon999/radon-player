package org.omarchy.music

/** The Refresh button in a tab's header: what it reads again there, or that the tab has nothing to read again. */
object RefreshRules {
    /** Your song files read again (new ones come in, tags changed elsewhere show). */
    const val FILES = "files"
    /** Your listening added up again (Most played, Skips, Repeat…: new plays count). */
    const val STATS = "stats"
    const val DOWNLOADS = "downloads"
    /** Smart playlists worked out again. */
    const val SMART = "smart"
    /** Mood lists taken again from your songs' moods. */
    const val MOODLISTS = "moodlists"
    const val DUPES = "dupes"
    /** A list the app keeps itself (playlists, pinned, discovery): drawn again from what is kept. */
    const val LISTS = "lists"

    private val FILE_TABS = setOf("albums", "eps", "singles", "artists", "genres", "moods", "folders", "unreleased", "flac24", "flac32")
    private val STAT_VIEWS = setOf("top", "skips", "favorites", "unfavorited", "repeat", "recent", "dj")

    /** What Refresh does on the tab (and, in Music, its view), or null where there is nothing to read again (Now
     *  playing, Search, the queue, lyrics, the visualizer), where the tab has its own Refresh that asks first (New
     *  releases), or further in (inside: an album, a playlist, a folder open; an album's page has its own). local:
     *  the Library tab is showing your own music, not a service's. */
    fun kind(tab: String, view: String, inside: Boolean, local: Boolean = true): String? {
        if (inside) return null
        return when (tab) {
            in FILE_TABS -> FILES
            "downloads" -> DOWNLOADS
            "library" -> if (local) FILES else null
            "music" -> when (view) {
                in STAT_VIEWS -> STATS
                "all" -> FILES
                "smartlists" -> SMART
                "moodlists" -> MOODLISTS
                "dupes" -> DUPES
                "playlists", "pinned", "discovery" -> LISTS
                else -> null
            }
            else -> null
        }
    }

    /** What it says it is doing. */
    fun text(kind: String): String = when (kind) {
        FILES -> "Reading your music again…"
        STATS -> "Adding up your listening again…"
        SMART -> "Working out your smart playlists again…"
        MOODLISTS -> "Taking your mood lists again…"
        DUPES -> "Looking for duplicates again…"
        LISTS -> "Up to date"
        else -> ""
    }
}
