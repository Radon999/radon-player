package org.omarchy.music

/** Concert dates ([ConcertRules]): an artist's coming shows, asked of Ticketmaster with your own key, and kept for
 *  six hours so an artist's page opened again doesn't ask again. The key and city are this device's settings; the key
 *  goes nowhere but to Ticketmaster, as part of the question. */
object Concerts {
    const val KEY = "concerts_key"
    const val CITY = "concerts_city"
    fun key(): String = Prefs.text(KEY).trim()
    fun city(): String = Prefs.text(CITY).trim()
    val on: Boolean get() = key().isNotEmpty()

    private val kept = HashMap<String, Pair<Long, List<ConcertRules.Concert>>>()

    /** (blocking) The artist's coming shows. Throws, with why in words, when Ticketmaster can't be asked. */
    fun find(artist: String): List<ConcertRules.Concert> {
        val k = norm(artist) + "|" + norm(city()) + "|" + key().hashCode()
        synchronized(kept) { kept[k]?.takeIf { System.currentTimeMillis() - it.first < 6 * 3600_000L }?.let { return it.second } }
        val got = try { httpJson(ConcertRules.url(artist, key(), city())) }
            catch (e: HttpError) { throw RuntimeException(ConcertRules.why(e.code)) }
            catch (e: Exception) { throw RuntimeException(ConcertRules.why(0)) }
        return ConcertRules.parse(got, artist).also { synchronized(kept) { if (kept.size > 200) kept.clear(); kept[k] = System.currentTimeMillis() to it } }
    }
}
