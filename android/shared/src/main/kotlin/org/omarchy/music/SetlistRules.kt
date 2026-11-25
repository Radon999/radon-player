package org.omarchy.music

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Setlists on an artist's page: the songs they played at their recent shows, from setlist.fm, asked for with a key
 *  of your own (free from setlist.fm; entered in Settings › Remote & sync › Setlists). setlist.fm's search by name
 *  also brings bands whose names only look alike, so only the shows of the artist with this very name are kept. A
 *  show's songs come in the order they were played, set by set, the encores last and marked; songs played from a
 *  tape (an intro before the band walks on) are shown but not played or put in a playlist. */
object SetlistRules {
    /** A song of a show. part: the set's name as setlist.fm gives it, "Encore" (or "Encore 2"…) for an encore, empty
     *  for the main set. cover: whose song it is when they played someone else's; with: who joined them for it.
     *  played: in "what they've been playing", at how many of the shows it was played. */
    data class Song(val name: String, val part: String = "", val encore: Int = 0, val cover: String = "", val with: String = "",
                    val info: String = "", val tape: Boolean = false, val played: Int = 0)

    /** A show and its songs (date as 2027-03-14). shows: how many shows a "what they've been playing" list is made of
     *  (1 for one show). */
    data class Setlist(val id: String, val date: String, val artist: String, val venue: String, val city: String, val country: String,
                       val tour: String, val url: String, val songs: List<Song>, val shows: Int = 1) {
        /** The songs to play or put in a playlist: all but those played from a tape. */
        val playable: List<Song> get() = songs.filter { !it.tape }
    }

    /** The address to ask for an artist's latest shows (newest first; setlist.fm gives twenty at a time). */
    fun url(artist: String): String =
        "https://api.setlist.fm/rest/1.0/search/setlists?artistName=" + java.net.URLEncoder.encode(artist.trim(), "UTF-8") + "&p=1"

    /** What goes with the question: the key (sent to setlist.fm only) and the kind of answer wanted. */
    fun headers(key: String): Map<String, String> = mapOf("x-api-key" to key.trim(), "Accept" to "application/json")

    private fun obj(e: JsonElement?) = e as? JsonObject
    private fun arr(e: JsonElement?) = (e as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    private fun text(e: JsonElement?) = (e as? JsonPrimitive)?.content?.trim().orEmpty()
    private fun flag(e: JsonElement?) = (e as? JsonPrimitive)?.content == "true"

    /** setlist.fm's day ("14-03-2027") as 2027-03-14 (empty when it can't be read). */
    fun day(eventDate: String): String {
        val p = eventDate.trim().split('-')
        return if (p.size == 3 && p[2].length == 4) "${p[2]}-${p[1].padStart(2, '0')}-${p[0].padStart(2, '0')}" else ""
    }

    /** The shows in setlist.fm's answer that are this artist's, the newest first, each once. When two bands share the
     *  name (setlist.fm tells them apart), the one with the most shows listed is taken. */
    fun parse(answer: JsonElement?, artist: String): List<Setlist> {
        val want = norm(artist)
        val theirs = arr(obj(answer)?.get("setlist")).filter { norm(text(obj(it["artist"])?.get("name"))) == want }
        val band = theirs.groupingBy { text(obj(it["artist"])?.get("mbid")) }.eachCount().maxByOrNull { it.value }?.key
        val out = ArrayList<Setlist>()
        for (e in theirs) {
            if (text(obj(e["artist"])?.get("mbid")) != band) continue
            val v = obj(e["venue"]); val c = obj(v?.get("city"))
            val songs = ArrayList<Song>()
            for (set in arr(obj(e["sets"])?.get("set"))) {
                val encore = text(set["encore"]).toIntOrNull() ?: 0
                val part = text(set["name"]).ifEmpty { if (encore == 1) "Encore" else if (encore > 1) "Encore $encore" else "" }
                for (s in arr(set["song"])) {
                    val name = text(s["name"])
                    if (name.isEmpty()) continue   // a song nobody could name ("unknown"): nothing to look for
                    songs += Song(name, part, encore, text(obj(s["cover"])?.get("name")), text(obj(s["with"])?.get("name")), text(s["info"]), flag(s["tape"]))
                }
            }
            out += Setlist(text(e["id"]), day(text(e["eventDate"])), text(obj(e["artist"])?.get("name")).ifEmpty { artist }, text(v?.get("name")),
                text(c?.get("name")), text(obj(c?.get("country"))?.get("name")), text(obj(e["tour"])?.get("name")), text(e["url"]), songs)
        }
        return out.filter { it.date.isNotEmpty() }.distinctBy { it.id }.sortedByDescending { it.date }
    }

    /** What they've been playing lately: the songs of their latest shows with songs listed (the newest show's tour
     *  only, when it names one; five shows at most), each once, those played at a third of the shows or more, in the
     *  order they usually come (the main set first, then what is usually an encore), each with at how many it was
     *  played. Null when no show has songs listed. */
    fun lately(setlists: List<Setlist>, most: Int = 5): Setlist? {
        val listed = setlists.filter { it.playable.isNotEmpty() }.sortedByDescending { it.date }
        val newest = listed.firstOrNull() ?: return null
        val shows = (if (newest.tour.isNotEmpty()) listed.filter { it.tour == newest.tour } else listed).take(most)
        class Seen(val song: Song) { var times = 0; var at = 0.0; var encores = 0 }
        val seen = LinkedHashMap<String, Seen>()
        for (s in shows) {
            val songs = s.playable.distinctBy { norm(it.name) }
            for ((i, song) in songs.withIndex()) {
                val k = seen.getOrPut(norm(song.name)) { Seen(song) }
                k.times++; k.at += if (songs.size > 1) i.toDouble() / (songs.size - 1) else 0.0
                if (song.encore > 0) k.encores++
            }
        }
        val kept = seen.values.filter { it.times * 3 >= shows.size }
            .sortedWith(compareBy<Seen> { if (it.encores * 2 > it.times) 1 else 0 }.thenBy { it.at / it.times })
            .map { val encore = it.encores * 2 > it.times
                it.song.copy(part = if (encore) "Encore" else "", encore = if (encore) 1 else 0, info = "", with = "", played = it.times) }
        return Setlist("lately:" + newest.id, newest.date, newest.artist, "", "", "", newest.tour, "", kept, shows = shows.size)
    }

    /** What to open for a show of theirs on [date] (2027-03-14; [today] the same way): once it has happened, the
     *  setlist of that very show (a day either side, as places' days differ) when setlist.fm has its songs; before
     *  then (or without it), what they've been playing lately. */
    fun forShow(date: String, today: String, setlists: List<Setlist>): Setlist? {
        if (date.isNotEmpty() && date <= today) {
            val d = runCatching { java.time.LocalDate.parse(date) }.getOrNull()
            if (d != null) setlists.firstOrNull { s ->
                s.playable.isNotEmpty() && runCatching { Math.abs(java.time.temporal.ChronoUnit.DAYS.between(d, java.time.LocalDate.parse(s.date))) <= 1 }.getOrDefault(false)
            }?.let { return it }
        }
        return lately(setlists)
    }

    private fun shortDay(date: String, pattern: String) =
        runCatching { java.time.LocalDate.parse(date).format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.ENGLISH)) }.getOrDefault(date)

    /** A show in a line: "Sun 14 Mar 2027 · The Fillmore, San Francisco". */
    fun line(s: Setlist): String =
        shortDay(s.date, "EEE d MMM yyyy") + " · " + listOf(s.venue, s.city).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { s.country }

    /** Under it: the tour and how many songs ("no songs listed yet" for a show setlist.fm has only the date of). */
    fun sub(s: Setlist): String {
        val n = s.playable.size
        return listOf(s.tour, if (n == 0) "no songs listed yet" else "%,d song%s".format(n, if (n != 1) "s" else ""), s.country)
            .filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** A playlist made of a show's songs: "Artist · Venue, City · 14 Mar 2027". */
    fun playlistName(artist: String, venue: String, city: String, date: String): String =
        listOf(artist.trim(), listOf(venue, city).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", "),
            if (date.isEmpty()) "" else shortDay(date, "d MMM yyyy")).filter { it.isNotEmpty() }.joinToString(" · ")

    /** Why nothing could be asked or shown, in words. */
    fun why(code: Int): String = when (code) {
        401, 403 -> "setlist.fm didn't accept the key: check it in Settings › Remote & sync › Setlists"
        429 -> "setlist.fm was asked too much at once: try again in a moment"
        else -> "setlist.fm couldn't be reached"
    }
}
