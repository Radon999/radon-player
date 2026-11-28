package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate

/** New releases of the artists in your songs, from Deezer's catalogue (free, no account; it says
 *  when each came out and whether it's an album, a single or an EP), as the desktop does it.
 *  Nothing is checked on its own: Scan looks, for the last week, month or year, when you press it. */
object NewReleases {
    @Serializable
    data class Release(val title: String, val artist: String, val date: String, val type: String, val cover: String = "", val link: String = "",
                       val aid: Long = 0L)   // aid: the Deezer artist it's by (0: found before this was kept)

    @Serializable
    private data class Saved(val artists: List<String> = emptyList(), val ids: Map<String, Long> = emptyMap(),
                             val found: List<Release> = emptyList(), val scanned: Double = 0.0, val range: String = "month",
                             val told: List<Release> = emptyList(), val unseen: Int = 0, val favUnseen: Int = 0)

    val RANGES = linkedMapOf("week" to 7L, "month" to 30L, "year" to 365L)
    var artists by mutableStateOf(listOf<String>()); private set
    var found by mutableStateOf(listOf<Release>()); private set
    var scanned by mutableStateOf(0.0); private set
    var range by mutableStateOf("month"); private set
    var status by mutableStateOf<String?>(null); private set   // the scan's progress (null: not scanning)
    var kind by mutableStateOf("album")   // the tab: album, single, ep, artists
    // The bell: what the app's own daily look found that it hadn't told you about, newest first (the last 300), and how
    // many of them came since the bell was last opened. (Until 4.0.17 these were a notification on the phone.)
    var told by mutableStateOf(listOf<Release>()); private set
    var unseen by mutableStateOf(0); private set
    var bell by mutableStateOf(false); private set
    /** The favorites' own bell: how many of what came since it was last opened are your favorite artists'. */
    var favUnseen by mutableStateOf(0); private set
    /** The bell opened (its count is then seen: the favorites' in Favorites, else the whole one's) or closed. */
    fun openBell(on: Boolean) {
        bell = on
        if (!on) return
        if (favView()) { if (favUnseen > 0) { favUnseen = 0; Music.bg({ save() }) } }
        else if (unseen > 0) { unseen = 0; Music.bg({ save() }) }
    }
    fun clearBell() { told = emptyList(); unseen = 0; favUnseen = 0; Music.bg({ save() }) }
    /** The count on the bell in the view shown (Favorites: only theirs). */
    fun bellCount(): Int = if (favView()) favUnseen else unseen

    // ---- Favorites: a shorter list of artists you really want to hear about (beside all you follow) ----
    private const val FAV_VIEW = "new_releases_fav_view"
    /** The favorite artists for New releases, as written. Read in Compose. */
    fun favorites(): List<String> = FollowRules.parse(Prefs.text(FollowRules.FAVORITES))
    fun isFavorite(name: String): Boolean = FollowRules.key(name) in favorites().map { FollowRules.key(it) }
    /** Favorites added (and followed, so their releases are looked for) or taken away (still followed: they stay in the
     *  whole list). */
    fun setFavorite(names: List<String>, add: Boolean) {
        Prefs.setText(FollowRules.FAVORITES, FollowRules.write(FollowRules.favorites(favorites(), names, add)))
        if (add) Follows.follow(names, quiet = true)
        Music.toast(when {
            add && names.size == 1 -> "${names[0]} is a favorite artist: in New releases' Favorites"
            add -> "${names.size} favorite artists added"
            names.size == 1 -> "${names[0]} is no longer a favorite (still in the whole list)"
            else -> "${names.size} taken out of the favorites (still in the whole list)"
        })
    }
    /** New releases showing only your favorites (and their own bell), or every artist you follow. */
    fun favView(): Boolean = Prefs.on(FAV_VIEW)
    fun setFavView(on: Boolean) { Prefs.set(FAV_VIEW, on); bell = false }
    /** In the view shown: everyone's, or (Favorites) a favorite's. Read in Compose. */
    fun inView(r: Release): Boolean = !favView() || isFavorite(r.artist)
    @Volatile var stop = false
    private val ids = HashMap<String, Long>()
    private lateinit var file: File

    fun init(c: Ctx) {
        file = File(c.filesDir, "new-releases.json")
        Music.bg({ readText(file)?.let { runCatching { JSON.decodeFromString(Saved.serializer(), it) }.getOrNull() } }) { s, _ ->
            if (s != null) { artists = s.artists; found = s.found; scanned = s.scanned; range = s.range; ids.putAll(s.ids); told = s.told; unseen = s.unseen; favUnseen = s.favUnseen }
        }
    }

    private fun save() = writeAtomic(file, JSON.encodeToString(Saved.serializer(), Saved(artists, HashMap(ids), found, scanned, range, told, unseen, favUnseen)))

    /** The artists to look for: the lead artist of every song of yours, the ones you have most songs of first. */
    fun readArtists(songs: List<Track>): Int {
        val counted = songs.mapNotNull { splitArtists(it.artist).firstOrNull()?.trim() }
            .filter { it.isNotEmpty() && norm(it) !in setOf("unknown artist", "various artists") }
            .groupingBy { it }.eachCount()
        songCounts = counted
        artists = counted.entries.sortedByDescending { it.value }.map { it.key }
        Music.bg({ save() })
        return artists.size
    }
    /** How many songs of each artist you have, as last read (for the order a look goes in). */
    @Volatile private var songCounts: Map<String, Int> = emptyMap()

    private fun kindOf(t: String?) = when (t?.lowercase()) { "ep" -> "ep"; "single" -> "single"; else -> "album" }

    /** The same artist, written either way: "half•alive" and "half alive", "K.Flay" and "KFlay". */
    fun sameName(a: String?, b: String?) = bare(a).let { it.isNotEmpty() && it == bare(b) }

    /** New releases' Select: what's picked (across Albums, Singles, EPs and Artists): key -> a release, or an artist's name. */
    var selecting by mutableStateOf(false)
    val picked = androidx.compose.runtime.mutableStateMapOf<String, Pair<Release?, String>>()
    fun key(r: Release) = "r:" + norm(r.artist) + "|" + r.link.ifEmpty { norm(r.title) }
    fun artistKey(name: String) = "a:" + norm(name)
    fun toggle(key: String, what: Pair<Release?, String>) { if (picked.remove(key) == null) picked[key] = what }

    /** The releases picked (an artist picked: all of theirs found), each once. */
    fun pickedReleases(): List<Release> {
        val out = LinkedHashMap<String, Release>()
        for ((r, artist) in picked.values) (if (r != null) listOf(r) else found.filter { it.artist == artist }).forEach { out.putIfAbsent(key(it), it) }
        return out.values.toList()
    }

    private fun bare(a: String?) = norm((a ?: "").replace(Regex("^the ", RegexOption.IGNORE_CASE), "")).replace(Regex("[\\W_]+"), "")

    /** Deezer's answer; when it says too many asks at once, wait and ask again. */
    private fun deezer(url: String): kotlinx.serialization.json.JsonElement {
        var got = httpJson(url)
        for (wait in listOf(5_000L, 10_000L)) {
            if (got["error"]["code"].long != 4L) return got   // 4: quota (50 asks every 5 seconds)
            Thread.sleep(wait)
            got = httpJson(url)
        }
        return got
    }

    /** Which artist of this name on Deezer is yours ([ArtistMatchRules]): each of that very name looked at, and the one
     *  whose releases and songs share titles with yours (mine: your titles and albums by that name, made plain) taken;
     *  0 if none is yours, or none is on Deezer. Kept once worked out ("2|": the ones kept before this was checked,
     *  by the name alone, are worked out again). */
    private fun artistId(name: String, mine: Set<String>): Long {
        val k = "2|" + norm(name)
        // (a kept "none is yours" from before 5.3 is looked at again: it could have come from one failed request, and
        // then that artist's releases never showed again)
        ids[k]?.takeIf { it != 0L }?.let { return it }
        if (k in noneNow) return 0L
        val got = deezer("https://api.deezer.com/search/artist?q=${enc(name)}&limit=10")
        val same = got["data"].list.filter { bare(it["name"].str).let { b -> b.isNotEmpty() && b == bare(name) } }.take(4)
        val notMine = notMineIds()
        if (same.isEmpty()) return 0L   // not on Deezer (yet): looked for again next time
        var unsure = false   // one's titles couldn't be read (the connection, Deezer's limit): nothing is concluded from that
        val candidates = same.map { a ->
            val id = a["id"].long ?: 0L
            id to (if (mine.isEmpty() || same.size == 1 && mine.size < ArtistMatchRules.ENOUGH) emptySet()
                else titlesOf(id) ?: emptySet<String>().also { unsure = true })
        }.filter { it.first != 0L && it.first !in notMine }   // (never one you said isn't yours)
        val pick = ArtistMatchRules.pick(candidates, mine)
        // Kept for good only when one is yours. "None is yours" is for this run only (asked again at the next start: your
        // titles may not have been in what Deezer answered), and not even that when a request failed
        if (pick != 0L) ids[k] = pick else { ids.remove(k); if (!unsure) noneNow += k }
        return pick
    }
    private val noneNow = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /** Not my artist: the Deezer artists you said aren't the one of that name you follow (one with your name, but someone
     *  else), by id. Their releases are never shown and they're never picked for the name again; the name itself stays
     *  followed (your own artist of that name goes on showing). Kept with the settings, so both devices know. */
    const val NOT_MINE = "new_releases_not_mine"
    private fun notMineIds(): Set<Long> = Prefs.text(NOT_MINE).lines().mapNotNull { it.substringBefore('|').trim().toLongOrNull() }.toSet()
    /** The Deezer artist a release is by: kept with it, or (found before that was kept) the one its name stands for now. */
    private fun artistOf(r: Release): Long = r.aid.takeIf { it != 0L } ?: ids["2|" + norm(r.artist)] ?: 0L
    /** Whether "Not my …" can be offered for it (which artist it's by is known). */
    fun canSayNotMine(r: Release): Boolean = artistOf(r) != 0L

    /** This release's artist isn't yours (someone else of the same name): their releases go now and never come again,
     *  and the next look finds yours of that name, if Deezer has them. */
    fun notMine(r: Release) {
        val id = artistOf(r)
        if (id == 0L) { Music.toast("Refresh New releases once, then try again (which ${r.artist} it is isn't known yet)"); return }
        Prefs.setText(NOT_MINE, (Prefs.text(NOT_MINE).lines().filter { it.isNotBlank() } + "$id|${r.artist}").distinct().joinToString("\n"))
        ids.remove("2|" + norm(r.artist))   // worked out again, without them
        fun keep(x: Release) = artistOf(x) != id && !(x.aid == 0L && x == r)
        found = found.filter(::keep); told = told.filter(::keep)
        Music.bg({ save() })
        Music.toast("Hidden: that ${r.artist} isn't yours. Yours stays followed")
    }

    /** (blocking) An artist's release and song titles on Deezer, made plain (as [plain]); null when either couldn't be
     *  read (an empty answer said "none of these is yours" for good). */
    private fun titlesOf(id: Long): Set<String>? {
        val out = HashSet<String>()
        val a = runCatching { deezer("https://api.deezer.com/artist/$id/albums?limit=100")["data"].list.forEach { a -> a["title"].str?.let { out += plain(it) } } }
        Thread.sleep(120)
        val b = runCatching { deezer("https://api.deezer.com/artist/$id/top?limit=50")["data"].list.forEach { t ->
            t["title"].str?.let { out += plain(it) }; t["album"]["title"].str?.let { out += plain(it) } } }
        Thread.sleep(120)
        return if (a.isFailure || b.isFailure) null else out
    }

    /** A title made plain, to tell the same release or song written two ways ("Warm (Deluxe)" = "warm"). */
    private fun plain(t: String): String = norm(cleanTitle(t))

    /** Your titles and albums by each artist (by [FollowRules.key] of the lead artist), made plain: what tells which
     *  artist of a name is yours. */
    private fun mineByArtist(songs: List<Track>): Map<String, Set<String>> {
        val out = HashMap<String, MutableSet<String>>()
        for (t in songs) {
            val a = splitArtists(t.artist).firstOrNull()?.trim().orEmpty()
            if (a.isEmpty()) continue
            val set = out.getOrPut(FollowRules.key(a)) { HashSet() }
            if (t.title.isNotBlank()) set += plain(t.title)
            if (t.album.isNotBlank()) set += plain(t.album)
        }
        return out
    }

    /** Look at each artist's releases from the last week, month or year (in the background). */
    // ---- Look by itself: once a day while the app is open, and say when there is something new ----
    const val AUTO = "new_releases_auto"
    private const val AUTO_AT = "new_releases_auto_at"
    private const val SEEN = "new_releases_seen"
    /** Called every so often while the app is open: a look at the last week, once a day, in the background. */
    fun tick() {
        if (!Prefs.on(AUTO) || status != null) return
        val t = System.currentTimeMillis() / 1000
        if (t - (Prefs.text(AUTO_AT).toLongOrNull() ?: 0L) < 86_400L) return
        Prefs.setText(AUTO_AT, t.toString())
        scan("week", quiet = true)
    }
    /** After a look by itself: the releases not told about before go under the bell on the New releases screen, which
     *  shows how many are new. No notification is sent. */
    private fun announce(res: List<Release>) {
        val seen = Prefs.text(SEEN).split('\n').filter { it.isNotBlank() }
        val new = NewReleaseRules.fresh(res.map { key(it) to (it.title to it.artist) }, seen.toSet())
        if (new.isEmpty()) return
        Prefs.setText(SEEN, (new.map { it.first } + seen).take(600).joinToString("\n"))
        val keys = new.map { it.first }.toSet()
        told = (res.filter { key(it) in keys } + told).distinctBy { key(it) }.sortedByDescending { it.date }.take(300)
        unseen = if (bell && !favView()) 0 else unseen + new.size
        val favNew = FollowRules.favoriteCount(new.map { it.second.second }, favorites())
        favUnseen = if (bell && favView()) 0 else favUnseen + favNew
        Music.bg({ save() })
    }

    /** Hide Deezer-only (the New releases tab and its widget, one switch for both): the releases no service you can play
     *  from has (only on Deezer so far) aren't shown. Off at first: seeing them tells you there's something new to get
     *  some other way. */
    const val HIDE_DEEZER = "new_releases_hide_deezer"
    fun hideDeezer(): Boolean = Prefs.on(HIDE_DEEZER)
    fun setHideDeezer(on: Boolean) = Prefs.set(HIDE_DEEZER, on)
    /** Whether a release shows as the switch says (one not looked up yet shows until it is). Read in Compose. */
    fun shown(r: Release): Boolean { ReleaseLookup.version; return !hideDeezer() || ReleaseLookup.where(r) != "none" }

    /** Releases of these artists taken out of what was found (they were unfollowed). */
    fun dropArtists(names: List<String>) {
        val out = names.mapTo(HashSet()) { FollowRules.key(it) }
        fun keep(r: Release) = FollowRules.key(r.artist) !in out
        found = found.filter(::keep); told = told.filter(::keep)
        Music.bg({ save() })
    }

    /** Whose releases are looked for: the artists of the songs you have as files (yours and downloads) and of the songs in
     *  the playlists you keep in Radon Player (streamed ones too). A playlist that only exists on a streaming service, and
     *  the playlists the app makes itself (discovery, New releases, smart ones), don't count. */
    private fun yours(): List<Track> = Music.mySongs() +
        runCatching { Music.lists.all().filter { it.smart == null && it.discovery == null }.flatMap { it.tracks } }.getOrDefault(emptyList())

    fun scan(period: String, quiet: Boolean = false) {
        if (status != null) return
        val mine = yours()
        readArtists(mine)   // read again each time (how many songs of each: the order they're looked at in)
        val titles = mineByArtist(mine)   // which artist of a name is yours
        // Whose releases: the artists you follow (yours followed by themselves, with Follow artists by themselves on;
        // anyone you followed; never anyone you unfollowed)
        Follows.refreshNow()
        artists = Follows.list().sortedByDescending { a -> songCounts[a] ?: songCounts.entries.firstOrNull { FollowRules.key(it.key) == FollowRules.key(a) }?.value ?: 0 }
        status = "Looking…"
        stop = false
        val since = LocalDate.now().minusDays(RANGES[period] ?: 30).toString()
        val songsOf = artists.associateWith { songCounts[it] ?: 0 }   // (most songs first, as read)
        Music.bg({
            // The artists you play most, and the ones you've lately fallen for, are looked at first: a look stopped
            // halfway (or the daily one) has found theirs
            val names = byTaste(songsOf)
            val out = ArrayList<Release>()
            val failed = HashSet<String>()   // the artists that couldn't be asked about (no connection, Deezer's limit)
            var asked = 0
            for ((i, name) in names.withIndex()) {
                if (stop) break
                val line = "Looking at $name · ${i + 1} of ${names.size}"
                Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { status = line }
                asked++
                val aid = runCatching { artistId(name, titles[FollowRules.key(name)].orEmpty()) }.getOrElse { failed += FollowRules.key(name); 0L }
                if (aid == 0L || aid in notMineIds()) continue
                val got = runCatching { deezer("https://api.deezer.com/artist/$aid/albums?limit=100") }.getOrNull() ?: run { failed += FollowRules.key(name); null } ?: continue
                for (al in got["data"].list) {
                    val date = al["release_date"].str ?: ""
                    if (date >= since) out += Release(al["title"].str ?: "", name, date, kindOf(al["record_type"].str),
                        al["cover_medium"].str ?: al["cover"].str ?: "", al["link"].str ?: "", aid)
                }
                Thread.sleep(120)   // Deezer takes 50 asks every 5 seconds
            }
            // Nobody could be asked (no connection): not a look at all, and what was found before stays
            if (NewReleaseRules.lookFailed(asked, failed.size)) error("the connection (Deezer couldn't be reached)")
            Triple(out.sortedByDescending { it.date }, failed as Set<String>, since)
        }) { got, err ->
            status = null
            if (got == null) {
                if (!quiet) Music.toast("Couldn't look: ${err?.message?.take(100)}")
                else Prefs.setText(AUTO_AT, (System.currentTimeMillis() / 1000 - 86_400L + 3600).toString())   // the daily look: again in an hour
                return@bg
            }
            val (res, failedNow, sinceNow) = got
            // What stays of the list from before: the releases of the artists who couldn't be asked this time, and, after
            // the daily look (a week back), the older ones of the month or year you last looked at: it had replaced them
            val wider = quiet && (RANGES[range] ?: 0) > (RANGES[period] ?: 0)
            val kept = NewReleaseRules.keptFromBefore(found.map { Triple(key(it), FollowRules.key(it.artist), it.date) }, res.map { key(it) }.toSet(),
                failedNow, sinceNow, if (wider) LocalDate.now().minusDays(RANGES[range] ?: 30).toString() else null)
            found = (res + found.filter { key(it) in kept }).distinctBy { key(it) }.sortedByDescending { it.date }
            scanned = now(); if (!wider) range = period
            Music.bg({ save() })
            // (a look you made yourself: you've seen these, so the daily look doesn't put them under the bell as new)
            if (!quiet) Prefs.setText(SEEN, (res.map { key(it) } + Prefs.text(SEEN).split('\n').filter { it.isNotBlank() }).distinct().take(600).joinToString("\n"))
            if (quiet) announce(res) else Music.toast("${res.size} new release${if (res.size != 1) "s" else ""}" +
                if (failedNow.isNotEmpty()) " (${failedNow.size} artist${if (failedNow.size != 1) "s" else ""} couldn't be asked: theirs are as before)" else "")
        }
    }

    /** (blocking) The artists (each with how many songs of theirs you have) in the order to look: [NewMusicRules.releaseOrder]
     *  by your plays of each and their phase in your listening, as the last picture of it has them (none yet: as given). */
    private fun byTaste(songsOf: Map<String, Int>): List<String> {
        val taste = TasteService.current ?: return songsOf.keys.toList()
        val all = taste.facts.artistAll
        val byNorm = HashMap<String, Int>().also { m -> all.forEach { (a, n) -> m.merge(norm(a), n, Int::plus) } }
        val phase = taste.habits?.phase.orEmpty()
        return NewMusicRules.releaseOrder(songsOf.toList(), { a -> all[a] ?: byNorm[norm(a)] ?: 0 }, { a -> phase[statsKey("", a).second] })
    }
}
