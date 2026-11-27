package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/** Where a new release is on the streaming services (Tidal, YouTube Music; never your files), for New releases:
 *  opening one, and a discovery playlist of its songs. [Music] keeps the names the screens call and hands on. */
object ReleaseLookup {
    /** Releases found already, by [NewReleases.key]. */
    private val found = java.util.concurrent.ConcurrentHashMap<String, Triple<String, Album?, List<Track>>>()

    /** A release's songs found already (an album's asked for once), by [NewReleases.key]: a second play is at once. */
    private val songsOf = java.util.concurrent.ConcurrentHashMap<String, List<Track>>()
    /** Where each release looked up plays from, by [NewReleases.key]: "tidal", "ytm", or "none" (only on Deezer so far). */
    private val whereOf = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** Bumps as releases are looked up (the screens showing where each plays from follow it). */
    var version by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    private fun found(ck: String, where: String) { whereOf[ck] = where; Music.scope.launch { version++ } }

    /** Where a release plays from, if it's been looked up: "tidal", "ytm" or "none"; null: not yet. */
    fun where(r: NewReleases.Release): String? = whereOf[NewReleases.key(r)]
    /** The same, said: "Tidal", "YouTube Music", "Deezer only: can't play yet", or "" before it's looked up. New
     *  releases are found on Deezer; they play from Tidal first, else YouTube Music. */
    fun whereLabel(r: NewReleases.Release): String = NewReleaseRules.whereLabel(where(r))

    /** Why a release couldn't be found, in plain words, by [NewReleases.key] (see [whyNot]). */
    private val why = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** In a test: what was found is forgotten. */
    internal fun forget() { found.clear(); songsOf.clear(); why.clear(); whereOf.clear() }

    private fun wanted(r: NewReleases.Release) = norm(cleanTitle(r.title))

    /** Of what a service found: the album (or single, or EP) named as the release is, by its artist (the album's first
     *  artist written either way, or the artist's name anywhere in its line); null if none. */
    fun albumIn(res: Found, r: NewReleases.Release): Album? = wanted(r).let { want ->
        res.albums.firstOrNull { al ->
            norm(cleanTitle(al.title)) == want && (NewReleases.sameName(r.artist, al.sub.substringBefore(" · ").substringBefore(",")) ||
                norm(r.artist) in norm(al.sub))
        }
    }

    /** Of what a service found: the song named as the release is, with its artist first in its credit; null if none. */
    fun songIn(res: Found, r: NewReleases.Release): Track? = wanted(r).let { want ->
        res.songs.firstOrNull { t -> norm(cleanTitle(t.title)) == want && NewReleases.sameName(r.artist, splitArtists(t.artist).firstOrNull() ?: "") }
    }

    /** (blocking) Where the release is: (service, its album, []) for the album (or single, or EP) by its name and
     *  artist, else (service, null, [the song]) for a single, else null. The service you browse is asked first, then
     *  Tidal (signed in), then YouTube Music; an album on any of them comes before a lone song on an earlier one. */
    fun find(r: NewReleases.Release): Triple<String, Album?, List<Track>>? {
        val ck = NewReleases.key(r)
        found[ck]?.let { return it }
        // Tidal always first (signed in), then YouTube Music: whichever service you browse with
        var single: Triple<String, Album?, List<Track>>? = null
        var last: Throwable? = null
        val looked = ArrayList<String>(); val failed = ArrayList<String>(); var tidalOff = false
        for (key in listOf("tidal", "ytm")) {
            val src = Music.sources[key]
            if (key == "tidal" && (src == null || !src.signedIn)) { tidalOff = true; continue }
            if (src == null || key !in setOf("tidal", "ytm")) continue
            val name = if (key == "tidal") "Tidal" else "YouTube Music"
            val res = try { src.find("${r.artist} ${r.title}") } catch (e: Exception) { last = e; failed += "$name (${e.message?.take(60) ?: "no answer"})"; continue }
            looked += name
            albumIn(res, r)?.let { al -> return Triple(key, al, emptyList<Track>()).also { found[ck] = it; found(ck, key) } }
            if (single == null) songIn(res, r)?.let { single = Triple(key, null, listOf(it)) }
        }
        single?.let { found[ck] = it; found(ck, it.first); return it }
        last?.let { logWarn("release ${r.title}", it) }
        why[ck] = NewReleaseRules.whyNot(r.title, r.artist, looked, failed, tidalOff)
        if (failed.isEmpty()) found(ck, "none")   // (not when a service couldn't be reached: it may well be there)
        return null
    }

    /** Why a release couldn't be played, in plain words (after [find] or [tracks] came back with nothing). */
    fun whyNot(r: NewReleases.Release): String = why[NewReleases.key(r)]
        ?: "“${r.title}” by ${r.artist} couldn't be found on your services"

    /** (blocking, in the background) The first few releases looked up ahead, one at a time, so playing one is quick. */
    fun warm(rs: List<NewReleases.Release>, n: Int = 8) {
        for (r in rs.take(n)) {
            val ck = NewReleases.key(r)
            if (songsOf.containsKey(ck) || why.containsKey(ck)) continue
            runCatching { tracks(r) }
        }
    }

    /** (blocking) The release's songs, from the services (see [find]); a song with no album gets the release's name. */
    fun tracks(r: NewReleases.Release): List<Track> {
        val ck = NewReleases.key(r)
        songsOf[ck]?.let { return it }
        val (key, al, songs) = find(r) ?: return emptyList()
        val got = if (al != null) Music.sources[key]?.album(al.id).orEmpty() else songs
        if (got.isEmpty()) why[ck] = "“${r.title}” by ${r.artist} was found, but ${if (key == "tidal") "Tidal" else "YouTube Music"} gave no songs for it (it may not be out there yet)"
        return got.map { if (it.album.isEmpty()) it.copy(album = r.title) else it }.also { if (it.isNotEmpty()) songsOf[ck] = it }
    }
}
