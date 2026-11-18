package org.omarchy.music

/** The subject of each thing the DJ can tell you between songs, by its key ("skips-week", "hours:<song>"…): the
 *  subjects take turns ([NoRepeat.pickTurn]), so it doesn't talk about skips (or moods, or one artist) again and
 *  again while other things wait to be said. */
object FactTopics {
    private val BY_KIND = mapOf(
        "skips" to listOf("most-skipped", "never-skipped", "skips-all", "skips-week", "skipped-week", "heard-out", "skipped", "c-never"),
        "artist" to listOf("artist-since", "artist-new", "artist-rank", "top-artist-month", "second-artist-month", "top-artist-all",
            "artists-count", "new-artist-month", "artist-songs"),
        "time-spent" to listOf("hours", "minutes"),
        "song-history" to listOf("first", "crush", "year-ago", "streak", "c-crush", "c-old", "last-played"),
        "top-songs" to listOf("top-ten", "top-song-month", "c-top", "songs-count"),
        "genre" to listOf("top-genre-month", "genre-song", "library-genre", "library-genres"),
        "when" to listOf("peak-hour", "night-share", "morning-share"),
        "totals" to listOf("month-plays", "total-plays", "since"),
        "years" to listOf("year-top", "busiest-year", "year-plays", "year-so-far"),
        "collection" to listOf("library-size", "library-hours", "library-artist", "library-longest", "library-albums", "library-unplayed", "library-added"),
        "album" to listOf("album-have"),
        "favorites" to listOf("favorites-count", "favorite"),
        "playlists" to listOf("playlists"),
        "milestone" to listOf("milestone"),
        "repeats" to listOf("c-loop", "c-looprun", "c-loopback", "c-loopartist"),
    ).flatMap { (topic, kinds) -> kinds.map { it to topic } }.toMap()

    /** A key's kind: what comes before its first colon ("hours:<song>" is "hours"). */
    fun kind(key: String): String = key.substringBefore(':')

    /** The subject a fact is about; every "mood-…" fact is about moods; a kind not listed is its own subject. */
    fun of(key: String): String = kind(key).let { k -> BY_KIND[k] ?: if (k.startsWith("mood-")) "moods" else k }

    /** Every kind listed, with its subject (for the test that no kind is left out). */
    val listed: Map<String, String> get() = BY_KIND
}
