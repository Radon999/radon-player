package org.omarchy.music

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Concert dates on an artist's page: their coming shows, from Ticketmaster's listings (its Discovery API), asked
 *  for with a key of your own (free from developer.ticketmaster.com; entered in Settings › Remote & sync › Concert dates).
 *  With a city set, only shows there; without, all it lists, the soonest first. Only shows the artist is billed in
 *  are kept: a search by name also brings tribute acts and festivals that merely mention them. */
object ConcertRules {
    data class Concert(val date: String, val name: String, val venue: String, val city: String, val country: String, val url: String)

    private fun obj(e: JsonElement?) = e as? JsonObject
    private fun arr(e: JsonElement?) = (e as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    /** The address to ask (the key is the last thing on it; nothing else of yours is sent). */
    fun url(artist: String, key: String, city: String): String {
        fun enc(s: String) = java.net.URLEncoder.encode(s.trim(), "UTF-8")
        return "https://app.ticketmaster.com/discovery/v2/events.json?classificationName=music&sort=date,asc&size=50&keyword=" + enc(artist) +
            (if (city.isNotBlank()) "&city=" + enc(city) else "") + "&apikey=" + enc(key)
    }

    /** The shows in Ticketmaster's answer that bill this artist, the soonest first, each once. */
    fun parse(answer: JsonElement?, artist: String): List<Concert> {
        val want = norm(artist)
        val out = ArrayList<Concert>()
        for (e in arr(obj(obj(answer)?.get("_embedded"))?.get("events"))) {
            val acts = arr(obj(e["_embedded"])?.get("attractions")).mapNotNull { it["name"].str }
            val billed = acts.any { norm(it) == want } || (acts.isEmpty() && norm(e["name"].str ?: "").let { it == want || it.startsWith("$want ") })
            if (!billed) continue
            val v = arr(obj(e["_embedded"])?.get("venues")).firstOrNull()
            out += Concert(obj(obj(e["dates"])?.get("start"))?.get("localDate").str ?: "", e["name"].str ?: artist, v?.get("name").str ?: "",
                obj(v?.get("city"))?.get("name").str ?: "", obj(v?.get("country"))?.get("name").str ?: "", e["url"].str ?: "")
        }
        return out.filter { it.date.isNotEmpty() }.distinctBy { it.date + "|" + it.venue }.sortedBy { it.date }
    }

    /** A show in a line: "Sat 14 Mar 2027 · The Fillmore, San Francisco". */
    fun line(c: Concert): String {
        val day = runCatching { java.time.LocalDate.parse(c.date).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy", java.util.Locale.ENGLISH)) }.getOrDefault(c.date)
        return day + " · " + listOf(c.venue, c.city).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { c.country }
    }

    /** Why nothing could be asked or shown, in words. */
    fun why(code: Int): String = when (code) {
        401, 403 -> "Ticketmaster didn't accept the key: check it in Settings › Remote & sync › Concert dates"
        429 -> "Ticketmaster says the key has been used too much for now: try again later"
        else -> "Ticketmaster couldn't be reached"
    }
}
