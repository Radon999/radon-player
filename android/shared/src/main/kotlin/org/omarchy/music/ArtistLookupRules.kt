package org.omarchy.music

/** The rules of looking an artist up: which names are tried, which services are asked and in what order. */
object ArtistLookupRules {
    /** The names tried for an artist's page: the name itself, then (for a pair, "A & B") the first of the two. */
    fun names(name: String): List<String> = listOf(name) + (if (" & " in name) listOf(name.split(" & ")[0]) else emptyList())

    /** The services asked for an artist's page, in order, each once: the one asked for, the one you browse, then
     *  YouTube Music, Tidal, Apple Music and Spotify. */
    fun order(prefer: String?, browsing: String): List<String> =
        listOfNotNull(prefer, browsing, "ytm", "tidal", "apple", "spotify").distinct()

    /** Whether a service is asked: Tidal and Spotify only when you are signed in to them. */
    fun asked(key: String, signedIn: Boolean): Boolean = key !in setOf("tidal", "spotify") || signedIn

    /** Of the names, the ones to look up: each artist once (same is how two names are told to be one artist). */
    fun <K> once(names: List<String>, same: (String) -> K): List<String> = names.distinctBy(same)
}
