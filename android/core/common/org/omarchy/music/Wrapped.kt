package org.omarchy.music

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.PairSerializer
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Wrapped: your week, month or year in music (or all of it) from your Last.fm history, the desktop's
 *  numbers worked out the same way, with the plays you imported (Spotify, Apple Music, Deezer, YouTube Music, Tidal),
 *  your own songs' genre tags and moods, and the listening diary ([ListenLog]: playlists, outputs, searches). A big year takes a minute the first time (Last.fm hands out
 *  200 scrobbles a request); then it's kept and only the new ones are read. */
object Wrapped {
    private val NOT_GENRES = setOf("seen live", "favorites", "favourites", "favorite", "favourite", "my favorites", "albums i own",
        "female vocalists", "male vocalists", "female vocalist", "male vocalist", "female", "male",
        "under 2000 listeners", "spotify", "beautiful", "awesome", "love", "amazing", "epic", "cool",
        "british", "american", "uk", "usa", "canadian", "australian", "dutch", "german", "french",
        "swedish", "norwegian", "korean", "japanese", "english", "all", "vocal", "singer-songwriter",
        "masterpiece", "best", "good", "great", "favorite songs", "catchy", "sexy", "fun")
    private val GENRE_NAMES = mapOf("edm" to "EDM", "idm" to "IDM", "r&b" to "R&B", "rnb" to "R&B", "dnb" to "DnB",
        "uk garage" to "UK Garage", "lo-fi" to "Lo-fi", "lofi" to "Lo-fi", "k-pop" to "K-pop", "j-pop" to "J-pop",
        "hip-hop" to "Hip-hop", "hip hop" to "Hip-hop", "uk drill" to "UK Drill")
    val MONTHS = "Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec".split(" ")
    val MONTH_NAMES = "January February March April May June July August September October November December".split(" ")
    /** How many each top list shows (songs, albums, artists, genres…). */
    const val TOP = 15
    /** How many more each list keeps past its TOP: what takes the place of one you hide from stats while Wrapped is open. */
    const val SPARE = 10

    /** "What Wrapped counts", at the top of Wrapped: every source and stat it uses, in plain words (heading, what). */
    val COUNTS: List<Pair<String, String>> = listOf(
        "Your plays" to "Every scrobble on your Last.fm, plus every play in the histories you imported from Spotify, Apple Music, " +
            "Deezer, YouTube Music and Tidal, each with its date and time. Without Last.fm, the app's own diary of what you " +
            "played here counts instead (with Last.fm on, those plays are scrobbled already). The same song from different " +
            "places is one song.",
        "Minutes" to "How long Spotify and the other imports say each song played; for the rest, the length of your own copy, " +
            "else Last.fm's, else about three and a half minutes a song.",
        "Top songs, artists and albums" to "Your plays added up: the $TOP you played most. An album and its deluxe edition count as one. " +
            "What you hid from stats isn't in any list: hold a row (right-click on the PC) to hide it, and the next one takes its place.",
        "Top genres" to "The genre tag in your own music files, for every play of a song you have (\"Pop; Rock\" counts for both). " +
            "Without genre tags, your top artists' tags on Last.fm.",
        "Top moods" to "The mood the app finds for each of your own songs (from its sound, its genre tag and Last.fm's tags), " +
            "for every play of it.",
        "Singles and EPs" to "Plays of your songs that sit in Singles or EPs: by their album tag, their folder, or where you put " +
            "them with Tagger › Tab.",
        "When you listen" to "The time of every play: your top hour and weekday, your biggest day and month, your longest run of " +
            "days in a row, your first song of the period, the song you replayed most in one day, and each month's top artist.",
        "Discoveries" to "Songs and artists whose very first play in all your history (Last.fm, imports and plays here) falls " +
            "in the period. Shown when your history goes back further than the period.",
        "Skips and heard out" to "Counted by the app since it started counting: a skip is moving on with more than " +
            "${SongStats.SKIP_LEFT} seconds of a song left; heard out is playing it to the end. Plus the songs you starred.",
        "Milestones" to "Songs and artists that reached 100, 250, 500, 1,000… plays, dated by the play that got them there.",
        "Playlists, devices and searches" to "Counted from version 3.0 on, on this device: how long you listened to each song, " +
            "the playlist, album, artist, search, DJ or radio it was played from, whether that list was shuffled, which output " +
            "the sound went to (the car, earbuds, a speaker, the PC), and the songs you started from search results.",
        "Listener type" to "Mainstream or underground: how many fans your top artists have on Deezer. Shuffler or in order: " +
            "the listening diary above. Loyal or explorer: the share of your plays on your top 10 artists, and how many " +
            "artists were new to you. Night owl or early bird: the hours you play. Skipper or finisher: your skips.",
        "Your library" to "Every song you have (downloads and your music folders): songs, albums, EPs, singles and artists, how " +
            "long it would take to play it all, and how varied its genres and moods are. The variety score (0 to 100) is " +
            "how evenly your songs spread across them: 100 if every genre had as many songs, near 0 if one has nearly all.",
        "Pictures" to "Covers from your own files first, then Last.fm's, then Deezer's; artist photos from Deezer.",
    )

    /** "What the DJ knows", on the DJ view: what the DJ (Dj.kt) uses to pick its sets and lines (heading, what). */
    val DJ_KNOWS: List<Pair<String, String>> = listOf(
        "Your history" to "Every scrobble on your Last.fm, your Spotify, Apple Music, Deezer, YouTube Music and Tidal imports " +
            "(with their dates and times), and the app's own diary of what you played here (only the plays Last.fm hasn't " +
            "got, so nothing counts twice). Read again every ten minutes.",
        "On repeat" to "Your most played songs of the last 30 days.",
        "This time of day" to "What you've played within an hour of now, over the last year.",
        "Throwbacks" to "Each past year's most played songs (years with 30 plays or more), the ones you haven't played in two months.",
        "Rediscover" to "Songs you've played a lot over all time, but not in three months.",
        "Your favourites" to "The songs you starred; the ones you play most come up more often.",
        "Your genre" to "The genre tags in your own music files; without them, your top artists' tags on Last.fm.",
        "Your mood" to "The moods of your own songs (from their sound, genre tags and Last.fm's tags) that you've played in the " +
            "last 30 days, and the ones you play around this hour counted double: songs of yours in that mood.",
        "New for you and More like this" to "Tidal's and YouTube Music's radio of songs you've been playing (or of the song " +
            "playing now), leaving out songs you already know.",
        "Deep cuts" to "The best known songs of your artists of the last 90 days that you haven't played yet.",
        "Artists for you" to "Similar artists from Last.fm (else Deezer's related artists) of the artists you play most, all " +
            "time and this month, leaving out the ones you already play.",
        "Skips" to "Skipping a song never keeps it out of its sets (you may just not be in the mood for it). It counts your skips to tell you about them: your most skipped song, songs you've never skipped, and how much you hear all the way through.",
        "What it tells you" to "How many times (and hours) you've played a song, when you first played it, that you never (or " +
            "always) skip it, the mood you've been in this month, your artist of the month and of all time, the hour you " +
            "listen most, your plays in all, and a past year's number one.",
        "When you ask" to "An artist, a mood (your songs with it), a genre (your files' tags first, then Last.fm's), a decade " +
            "or a vibe like chill or workout; else a Tidal or YouTube Music mix of it.",
        "Playing from a list" to "From a playlist, folder or album: its songs you play most, haven't played yet, haven't played " +
            "in a while, or play at this hour, the ones in the mood you've been in, or a few by one of its artists.",
        "Not used" to "The listening diary (devices, playlists, searches) is for Wrapped only.",
    )
    val WEEKDAYS = "Mondays Tuesdays Wednesdays Thursdays Fridays Saturdays Sundays".split(" ")

    /** The year Wrapped opens on: this one, or last year until March, while this one has hardly begun. */
    fun year(): Int {
        val now = LocalDate.now()
        return if (now.monthValue >= 3) now.year else now.year - 1
    }

    /** What a Wrapped is of: a year, a month, a week (from its Monday) or all time. */
    sealed class Period {
        data class Year(val year: Int) : Period()
        data class Month(val month: YearMonth) : Period()
        data class Week(val monday: LocalDate) : Period()
        data object All : Period()

        val kind: String get() = when (this) { is Year -> "year"; is Month -> "month"; is Week -> "week"; All -> "all" }
        fun start(): LocalDate = when (this) {
            is Year -> LocalDate.of(year, 1, 1); is Month -> month.atDay(1); is Week -> monday; All -> LocalDate.of(1970, 1, 1)
        }
        fun end(): LocalDate = when (this) {
            is Year -> LocalDate.of(year + 1, 1, 1); is Month -> month.plusMonths(1).atDay(1)
            is Week -> monday.plusDays(7); All -> LocalDate.now().plusDays(1)
        }
        /** The one just before (the year, month or week before); none before All time. */
        fun before(): Period? = when (this) { is Year -> Year(year - 1); is Month -> Month(month.minusMonths(1)); is Week -> Week(monday.minusDays(7)); All -> null }
        /** "2026", "October 2026", "Week of Oct 19–25", "All time". */
        val name: String get() = when (this) {
            is Year -> "$year"; is Month -> "${Wrapped.MONTH_NAMES[month.monthValue - 1]} ${month.year}"
            is Week -> "Week of ${Wrapped.weekSpan(monday)}"; All -> "All time"
        }
        /** The big word on the first slide: "2026", "October", "Oct 19–25". */
        val title: String get() = when (this) {
            is Year -> "$year"; is Month -> Wrapped.MONTH_NAMES[month.monthValue - 1]; is Week -> Wrapped.weekSpan(monday); All -> "ALL TIME"
        }
        /** For "your first song of …": "2026", "October", "the week". */
        val of: String get() = when (this) {
            is Year -> "$year"; is Month -> Wrapped.MONTH_NAMES[month.monthValue - 1]; is Week -> "the week"; All -> "all time"
        }
        /** Its kept scrobbles' file name (a year's is the same as before weeks and months). */
        val key: String get() = when (this) { is Year -> "$year"; is Month -> "m$month"; is Week -> "w$monday"; All -> "all" }

        companion object {
            fun current(kind: String): Period = when (kind) {
                "week" -> Week(LocalDate.now().with(DayOfWeek.MONDAY)); "month" -> Month(YearMonth.now())
                "year" -> Year(Wrapped.year()); else -> All
            }

            /** What the picker offers for a kind, the newest first: the last 12 weeks or months, or the years. */
            fun choices(kind: String, years: List<Int>): List<Pair<Period, String>> = when (kind) {
                "year" -> years.map { Year(it) to "$it" }
                "month" -> (0L until 12L).map { YearMonth.now().minusMonths(it) }
                    .map { Month(it) to "${Wrapped.MONTH_NAMES[it.monthValue - 1]} ${it.year}" }
                "week" -> (0L until 12L).map { i ->
                    val m = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(i)
                    Week(m) to when (i) { 0L -> "This week"; 1L -> "Last week"; else -> Wrapped.weekSpan(m) }
                }
                else -> emptyList()
            }
        }
    }

    /** "Oct 19–25", or "Dec 28–Jan 3" across two months. */
    fun weekSpan(monday: LocalDate): String {
        val sunday = monday.plusDays(6)
        return if (monday.month == sunday.month) "${MONTHS[monday.monthValue - 1]} ${monday.dayOfMonth}–${sunday.dayOfMonth}"
        else "${MONTHS[monday.monthValue - 1]} ${monday.dayOfMonth}–${MONTHS[sunday.monthValue - 1]} ${sunday.dayOfMonth}"
    }

    /** A song row. file: your own copy of it (its cover is drawn first, read from the file), "" if you haven't got it;
     *  art: the picture when the file has none (a cover saved for it, Last.fm's or Deezer's address). */
    data class Song(val title: String, val artist: String, val plays: Int, val art: String, val first: Long? = null, val file: String = "")
    data class AlbumPlays(val name: String, val artist: String, val plays: Int, val art: String, val file: String = "")
    data class Data(
        val period: Period, val user: String, val years: List<Int>, val since: Int, val empty: Boolean = false,
        val scrobbles: Int = 0, val artistCount: Int = 0, val songCount: Int = 0,
        val artists: List<Pair<String, Int>> = emptyList(), val songs: List<Song> = emptyList(), val albums: List<AlbumPlays> = emptyList(),
        val topArtistSong: Pair<String, Int>? = null, val months: List<Int> = List(12) { 0 }, val hours: List<Int> = List(24) { 0 },
        val weekdays: List<Int> = List(7) { 0 }, val streak: Int = 0, val days: Int = 0, val busiest: LocalDate? = null,
        val busiestN: Int = 0, val first: LastFm.Played? = null, val minutes: Int = 0, val genres: List<String> = emptyList(),
        val artistImage: String = "", val yearsCount: List<Pair<Int, Int>> = emptyList(),
        // weeks and months: plays a day; every period: how many days it has had so far, whether it's
        // still going, and how many scrobbles the same stretch before it had (to compare with)
        val daily: List<Int> = emptyList(), val spanDays: Int = 0, val partial: Boolean = false, val prevCount: Int? = null,
        val milestones: List<Stats.Milestone> = emptyList(),   // songs and artists that reached 100, 250, 500, 1,000… plays in it
        // From your own songs' files: plays by genre tag and by mood, and your singles and EPs played most
        val fileGenres: List<Pair<String, Int>> = emptyList(), val moods: List<Pair<String, Int>> = emptyList(),
        val genreScore: Double = 0.0, val moodScore: Double = 0.0,
        val singles: List<AlbumPlays> = emptyList(), val eps: List<AlbumPlays> = emptyList(),
        // Firsts: songs and artists first played in it (known: there's history from before it to tell by)
        val discovered: List<Song> = emptyList(), val discoveredCount: Int = 0,
        val newArtists: List<Pair<String, Int>> = emptyList(), val newArtistCount: Int = 0, val firstsKnown: Boolean = false,
        val dayRepeat: Repeat? = null,                            // the song played most times in one day
        val dayRepeats: List<Repeat> = emptyList(),               // …and the next ones, to stand in when that one is hidden
        val monthTops: List<Pair<Int, String>> = emptyList(),     // a year's months (0–11) and each one's top artist
        val monthArtists: List<List<String>> = emptyList(),       // each month's top few artists (0–11), for the same
        val artistSongs: Map<String, Pair<String, Int>> = emptyMap(),   // each top artist's most played song in it
        val log: LogStats = LogStats(),                           // from the listening diary (ListenLog)
        val type: Listening.Kind? = null,                         // the listener type
        val library: LibraryStats? = null,                        // your music as it is now
        val loops: RepeatFactsRules.Summary? = null,              // your repeats in it (Repeat one), if any
    )

    /** What Wrapped shows of d: without what you hid from stats (a song, an album, an artist), each list's next one in
     *  its place, and each list cut to its TOP. Worked out again whenever something is hidden, so it changes at once. */
    fun shown(d: Data): Data {
        fun song(title: String, artist: String) = StatHides.any && StatHides.play(title, artist, "")
        fun album(name: String, artist: String) = StatHides.any && (StatHides.album(name, artist) || StatHides.artist(artist))
        fun artist(name: String) = StatHides.any && StatHides.artist(name)
        val artists = d.artists.filter { !artist(it.first) }
        val loops = runCatching {
            val all = d.period == Period.All
            RepeatFactsRules.summary(RepeatLoops.all(), if (all) "" else d.period.start().toString(), if (all) "" else d.period.end().toString(), TOP, loopLength) {
                StatHides.any && StatHides.play(it.track.title, it.track.artist, it.track.album) }
        }.getOrDefault(d.loops)
        // Top albums: albums only. A single or an EP (one of yours tagged so, or one Deezer or Apple Music says is one,
        // as the Top singles and EPs are made; or named "… - Single"/"… - EP") is left to its own list
        val notAlbums = (d.singles + d.eps).map { releaseKey(it.name, it.artist) }.toSet()
        val albumsOnly = d.albums.filter { a -> releaseKey(a.name, a.artist) !in notAlbums && !Regex("(?i)[-–(\\[]\\s*(single|ep)\\s*[)\\]]?\\s*$").containsMatchIn(a.name) }
        if (!StatHides.any) return d.copy(songs = d.songs.take(TOP), albums = albumsOnly.take(TOP), singles = d.singles.take(TOP),
            milestones = d.milestones.take(MILESTONES_SHOWN),
            eps = d.eps.take(TOP), discovered = d.discovered.take(TOP), newArtists = d.newArtists.take(TOP),
            log = d.log.copy(searched = d.log.searched.take(TOP)), loops = loops)
        val top = artists.firstOrNull()?.first
        return d.copy(
            artists = artists,
            songs = d.songs.filter { !song(it.title, it.artist) }.take(TOP),
            albums = albumsOnly.filter { !album(it.name, it.artist) }.take(TOP),
            singles = d.singles.filter { !album(it.name, it.artist) && !song(it.name, it.artist) }.take(TOP),
            eps = d.eps.filter { !album(it.name, it.artist) }.take(TOP),
            discovered = d.discovered.filter { !song(it.title, it.artist) }.take(TOP),
            newArtists = d.newArtists.filter { !artist(it.first) }.take(TOP),
            milestones = d.milestones.filter { if (it.what == "artist") !artist(it.name) else !song(it.name, it.artist) }.take(MILESTONES_SHOWN),
            // the top artist's most played song: not one you hid
            topArtistSong = (if (top == d.artists.firstOrNull()?.first) d.topArtistSong else top?.let { d.artistSongs[it] })
                ?.takeIf { top == null || !song(it.first, top) },
            dayRepeat = (if (d.dayRepeats.isEmpty()) listOfNotNull(d.dayRepeat) else d.dayRepeats).firstOrNull { !song(it.song.title, it.song.artist) },
            monthTops = if (d.monthArtists.isEmpty()) d.monthTops.filter { !artist(it.second) }
                else d.monthArtists.mapIndexedNotNull { m, names -> names.firstOrNull { !artist(it) }?.let { m to it } },
            first = d.first?.takeIf { !StatHides.play(it.title, it.artist, it.album) },
            log = d.log.copy(searched = d.log.searched.filter { !song(it.title, it.artist) }.take(TOP)),
            loops = loops,
        )
    }

    /** A song played n times in one day. */
    data class Repeat(val song: Song, val day: LocalDate)

    /** Time from one list (a playlist): seconds, songs, and seconds of it shuffled. */
    data class ListTime(val name: String, val secs: Double, val plays: Int, val shuffledSecs: Double)
    /** Time on one output (the car, earbuds, a speaker, the PC). */
    data class Device(val name: String, val kind: String, val secs: Double, val plays: Int)
    /** What the listening diary says about a period: time in it, the playlists, where songs were played from, the
     *  outputs, the songs started from search results, and the share of list time shuffled (null: too little to say). */
    data class LogStats(val since: Long = 0, val secs: Double = 0.0, val playlists: List<ListTime> = emptyList(),
                        val kinds: List<Pair<String, Double>> = emptyList(), val devices: List<Device> = emptyList(),
                        val searched: List<Song> = emptyList(), val shuffle: Double? = null, val session: Double = 0.0)
    /** Your music now: how many songs, releases and artists, how long it all plays, its genres and moods (song counts). */
    data class LibraryStats(val songs: Int, val albums: Int, val eps: Int, val singles: Int, val artists: Int, val secs: Double,
                            val genres: List<Pair<String, Int>>, val genreScore: Double, val moods: List<Pair<String, Int>>, val moodScore: Double)

    @Serializable
    data class Row(val t: Long, val artist: String, val title: String, val album: String, val image: String,
                           val ms: Long = 0)   // how long it played, for Spotify's plays
    @Serializable
    private data class Saved(val rows: List<Row> = emptyList(), val until: Long = 0)

    /** A release by its name and lead artist, as the top lists compare them ("Yesterday - Single" is "Yesterday"). */
    private fun releaseKey(name: String, artist: String) =
        norm(cleanTitle(bareRelease(name))) + "|" + norm(splitArtists(artist).firstOrNull()?.ifEmpty { null } ?: artist)

    /** Wrapped's milestones: six shown, from a few more kept (so a hidden one's place is taken). */
    const val MILESTONES_SHOWN = 6
    private const val MILESTONES_KEPT = 30

    /** A repeated song's length: your copy's (a repeat found in your Last.fm history kept none). */
    private val loopLength: (Track) -> Double = { t -> runCatching { Music.mineOf(t).duration }.getOrDefault(0.0) }

    private fun genreName(tag: String): String {
        val t = tag.lowercase().trim()
        return GENRE_NAMES[t] ?: t.split(" ").joinToString(" ") { w -> if (w == w.uppercase()) w else w.replaceFirstChar { it.uppercase() } }
    }

    private fun isGenre(tag: String, artist: String): Boolean {
        val t = tag.lowercase().trim()
        return t !in NOT_GENRES && !notAGenre(t) && t != artist.lowercase() && t.length <= 28 && !Regex("\\d+s?|\\d{2,4}s").matches(t) &&
            !Regex("seen|favou?rite|vocalist|listeners|albums? i|my |best of").containsMatchIn(t)
    }

    private fun picture(url: String?) = if (url.isNullOrEmpty() || LASTFM_BLANK in url || "/artist//" in url) "" else url
    private fun bigLastfm(url: String) = url.replace("/300x300/", "/770x0/").replace("/174s/", "/770x0/")

    private fun deezerArtistImage(name: String): String? {
        val data = httpJson("https://api.deezer.com/search/artist?q=${enc(name)}&limit=5")["data"].list
            .sortedByDescending { similar(norm(it["name"].str), norm(name)) }
        val best = data.firstOrNull() ?: return null
        return if (similar(norm(best["name"].str), norm(name)) > 0.85) best["picture_xl"].str ?: best["picture_big"].str else null
    }

    private fun deezerCover(artist: String, title: String): String? =
        httpJson("https://api.deezer.com/search?q=${enc("artist:\"$artist\" track:\"$title\"")}&limit=1")["data"][0].path("album", "cover_xl").str

    private fun local(t: Long) = LocalDateTime.ofInstant(Instant.ofEpochSecond(t), ZoneId.systemDefault())
    private fun startOf(y: Int) = LocalDate.of(y, 1, 1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
    private fun epoch(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toEpochSecond()

    /** Your own copy of a played song, by its name and artist: the same song (name and lead artist), else, when the
     *  play names an artist you've no songs by and just one of your songs has that name, that one (Last.fm, or a
     *  service, had put the play under the wrong artist: "What I Live For" by Juice WRLD shown as Lil Mboiz's, with its
     *  cover). Wrapped shows what's on your device, and only what isn't here as the history has it. */
    private class Local {
        private val byKey = HashMap<Pair<String, String>, Track>()
        private val byTitle = HashMap<String, MutableList<Track>>()
        private val artists = HashSet<String>()
        init {
            for (t in runCatching { Music.mySongs() }.getOrDefault(emptyList())) {
                val k = Stats.statsKey(t.title, t.artist)
                byKey.putIfAbsent(k, t); artists += k.second
                val l = byTitle.getOrPut(k.first) { ArrayList() }
                if (l.none { Stats.statsKey(it.title, it.artist) == k }) l += t
            }
        }
        val empty get() = byKey.isEmpty()
        fun of(title: String, artist: String): Track? {
            val k = Stats.statsKey(title, artist)
            byKey[k]?.let { return it }
            if (k.second in artists) return null   // an artist you have: a song of theirs you haven't got
            return byTitle[k.first]?.singleOrNull()
        }
    }

    /** Plays put under your own copy's name and artist (see Local). */
    private fun localRows(rows: List<Row>, local: Local): List<Row> = if (local.empty) rows else rows.map { r ->
        val t = local.of(r.title, r.artist) ?: return@map r
        if (t.title == r.title && t.artist == r.artist) r else r.copy(title = t.title, artist = t.artist, album = t.album.ifBlank { r.album })
    }

    /** The top songs as your own copies (see Local): the same song counted once, its plays added up, its file kept. */
    private fun localSongs(songs: List<Song>, local: Local): List<Song> {
        if (local.empty) return songs
        val out = LinkedHashMap<Pair<String, String>, Song>()
        for (s in songs) {
            val t = local.of(s.title, s.artist)
            val m = if (t != null) s.copy(title = t.title, artist = t.artist, file = t.file ?: s.file,
                art = if (Stats.statsKey(t.title, t.artist) == Stats.statsKey(s.title, s.artist)) s.art else "") else s
            val k = Stats.statsKey(m.title, m.artist)
            out[k] = out[k]?.let { it.copy(plays = it.plays + m.plays, first = listOfNotNull(it.first, m.first).minOrNull()) } ?: m
        }
        return out.values.sortedByDescending { it.plays }
    }

    /** undated: plays known only as a count (a library's all-time totals, no dates): they count for the songs, artists
     *  and albums and for how many plays there were, and for nothing that needs a day (months, hours, streaks…). */
    private fun yearStats(base: Data, rows: List<Row>, undated: List<Row> = emptyList()): Data {
        val artists = LinkedHashMap<String, Int>(); val songs = LinkedHashMap<Pair<String, String>, Int>()
        val albums = LinkedHashMap<Pair<String, String>, Int>()
        val artistName = HashMap<String, String>(); val songName = HashMap<Pair<String, String>, Pair<String, String>>()
        val albumName = HashMap<Pair<String, String>, Pair<String, String>>()
        val art = HashMap<Pair<String, String>, String>(); val albumArt = HashMap<Pair<String, String>, String>()
        val firstPlay = HashMap<Pair<String, String>, Long>()
        val months = IntArray(12); val hours = IntArray(24); val weekdays = IntArray(7); val days = LinkedHashMap<LocalDate, Int>()
        val monthArtists = Array(12) { HashMap<String, Int>() }   // each month's artists, for its top one
        val daySongs = HashMap<Pair<LocalDate, Pair<String, String>>, Int>()   // a song's plays in one day
        for ((n, r) in (rows.asSequence().map { 0 to it } + undated.asSequence().map { 1 to it })) {
            val dated = n == 0
            // The same song from Last.fm, Spotify and Apple Music counts as one (as in Most played).
            val ks = Stats.statsKey(r.title, r.artist); val ka = ks.second.ifEmpty { r.artist.lowercase() }
            artists.merge(ka, 1, Int::plus); songs.merge(ks, 1, Int::plus)
            val lead = splitArtists(r.artist).firstOrNull()?.ifEmpty { null } ?: r.artist
            val had = artistName[ka]   // named as they are on their own ("Quinn XCII", not "Quinn XCII & …")
            if (had == null || (soloName(mapOf(had to 0, lead to 0)) == lead && had != lead)) artistName[ka] = lead
            songName.putIfAbsent(ks, r.title to r.artist); if (dated) firstPlay.putIfAbsent(ks, r.t)
            val img = picture(r.image)
            if (img.isNotEmpty()) art[ks] = img
            if (r.album.isNotEmpty()) {
                val (base, edition) = albumEdition(r.album)   // an album and its deluxe edition are one, the edition's name
                val kb = ka to norm(cleanTitle(base))
                albums.merge(kb, 1, Int::plus)
                val was = albumName[kb]
                if (was == null || (edition.isNotEmpty() && albumEdition(was.first).second.isEmpty())) albumName[kb] = r.album to lead
                if (img.isNotEmpty()) albumArt[kb] = img
            }
            if (!dated) continue
            val d = local(r.t)
            months[d.monthValue - 1]++; hours[d.hour]++; weekdays[d.dayOfWeek.value - 1]++
            days.merge(d.toLocalDate(), 1, Int::plus)
            monthArtists[d.monthValue - 1].merge(ka, 1, Int::plus)
            daySongs.merge(d.toLocalDate() to ks, 1, Int::plus)
        }
        var streak = 0; var run = 0; var prev: LocalDate? = null
        for (day in days.keys.sorted()) {
            run = if (prev != null && prev.plusDays(1) == day) run + 1 else 1
            streak = maxOf(streak, run); prev = day
        }
        // Most common first; ties keep the order first seen, like Python's Counter.
        fun <K> common(m: Map<K, Int>) = m.entries.sortedByDescending { it.value }
        val busiest = common(days).firstOrNull()   // (none when every play is undated)
        val topArtist = common(artists).first().key
        val byTop = common(songs).firstOrNull { it.key.second == topArtist }
        val t0 = rows.firstOrNull()
        // The song played most in one day, and the next few (to stand in when it is hidden from stats)
        val reps = daySongs.entries.filter { it.value >= 3 }.sortedByDescending { it.value }.distinctBy { it.key.second }.take(5).map { (k, n) ->
            val (title, artist) = songName[k.second]!!
            Repeat(Song(title, artist, n, art[k.second] ?: ""), k.first)
        }
        val monthTops = (0 until 12).mapNotNull { m -> common(monthArtists[m]).firstOrNull()?.let { m to artistName[it.key]!! } }
        val topArtists = common(artists).take(30)
        val artistSongs = HashMap<String, Pair<String, Int>>()   // each top artist's most played song
        val topKeys = topArtists.map { it.key }.toSet()
        for ((k, n) in common(songs)) if (k.second in topKeys) artistSongs.putIfAbsent(artistName[k.second]!!, songName[k]!!.first to n)
        return base.copy(dayRepeat = reps.firstOrNull(), dayRepeats = reps, monthTops = monthTops,
            monthArtists = (0 until 12).map { m -> common(monthArtists[m]).take(5).map { artistName[it.key]!! } }, artistSongs = artistSongs,
            scrobbles = rows.size + undated.size, artistCount = artists.size, songCount = songs.size,
            artists = topArtists.map { artistName[it.key]!! to it.value },
            songs = common(songs).take(TOP + SPARE).map { (k, n) -> Song(songName[k]!!.first, songName[k]!!.second, n, art[k] ?: "", firstPlay[k]) },
            albums = common(albums).take(TOP + SPARE).map { (k, n) -> AlbumPlays(albumName[k]!!.first, albumName[k]!!.second, n, albumArt[k] ?: "") },
            topArtistSong = byTop?.let { songName[it.key]!!.first to it.value },
            months = months.toList(), hours = hours.toList(), weekdays = weekdays.toList(), streak = streak, days = days.size,
            busiest = busiest?.key ?: base.busiest, busiestN = busiest?.value ?: 0,
            first = t0?.let { LastFm.Played(it.t, it.artist, it.title, it.album, it.image) },
        )
    }

    private val durSer = MapSerializer(String.serializer(), Double.serializer())
    private val tagSer = MapSerializer(String.serializer(), ListSerializer(PairSerializer(String.serializer(), Int.serializer())))

    /** Minutes of music in rows: how long Spotify says it played, else each song's length (yours,
     *  else Last.fm's), the typical length for the few nobody knows. */
    private fun minutes(dir: File, all: List<Row>): Int {
        val timed = all.sumOf { it.ms } / 1000.0
        val rows = all.filter { it.ms == 0L }
        val known = HashMap<String, Double>()
        // your songs' own lengths: your files' and the downloads', by the lead artist (it had been the downloads' only)
        fun lead(a: String) = norm(a.split(",")[0])
        for (e in runCatching { Music.mySongs() }.getOrDefault(emptyList())) if (e.duration > 0)
            known.putIfAbsent("${lead(e.artist)}|${norm(cleanTitle(e.title))}", e.duration)
        for (e in Music.lib.items.values.toList()) if (e.duration > 0)
            known.putIfAbsent("${lead(e.artist)}|${norm(cleanTitle(e.title))}", e.duration)
        val file = File(dir, "durations.json")
        val cache = readText(file)?.let { runCatching { JSON.decodeFromString(durSer, it) }.getOrNull() }?.toMutableMap() ?: mutableMapOf()
        val plays = rows.groupingBy { it.artist to it.title }.eachCount()
        val lengths = HashMap<Pair<String, String>, Double>()
        var asked = 0
        for ((k, _) in plays.entries.sortedByDescending { it.value }) {
            val key = "${norm(k.first)}|${norm(cleanTitle(k.second))}"
            // (the play's lead artist: a play of "A, B" is your file's of A)
            var sec = known["${lead(k.first)}|${norm(cleanTitle(k.second))}"] ?: known[key] ?: cache[key]
            if (sec == null && asked < 80 && LastFm.key.isNotEmpty() && !lastFmDown) {   // Last.fm, for your most played; one request each
                asked++
                sec = runCatching { LastFm.duration(k.first, k.second) }.getOrDefault(0.0)
                cache[key] = sec
            }
            if (sec != null && sec > 0) lengths[k] = sec
        }
        writeAtomic(file, JSON.encodeToString(durSer, cache))
        val typical = lengths.values.sorted().let { if (it.isEmpty()) 210.0 else it[it.size / 2] }
        return Math.round((timed + plays.entries.sumOf { (k, n) -> n * (lengths[k] ?: typical) }) / 60).toInt()
    }

    /** Your top genres: each top artist's Last.fm tags, weighted by how much you played them. */
    private fun genres(dir: File, artists: List<Pair<String, Int>>): List<String> {
        if (LastFm.key.isEmpty()) return emptyList()   // the tags come from Last.fm
        val file = File(dir, "tags.json")
        val cache = readText(file)?.let { runCatching { JSON.decodeFromString(tagSer, it) }.getOrNull() }?.toMutableMap() ?: mutableMapOf()
        val score = LinkedHashMap<String, Double>()
        for ((name, plays) in artists.take(30)) {
            val all = cache[name.lowercase()] ?: if (lastFmDown) continue else runCatching { LastFm.artistTags(name) }.getOrDefault(emptyList()).also { cache[name.lowercase()] = it }
            val tags = all.filter { isGenre(it.first, name) }.take(4)
            val total = tags.sumOf { it.second }.takeIf { it > 0 } ?: 1
            for ((t, c) in tags) score.merge(t.lowercase(), plays.toDouble() * c / total, Double::plus)
        }
        writeAtomic(file, JSON.encodeToString(tagSer, cache))
        return score.entries.sortedByDescending { it.value }.take(5).map { genreName(it.key) }
    }

    /** A period's scrobbles: kept, and only the new ones read from Last.fm. */
    /** Every scrobble kept here (Most played reads them all once): no asking Last.fm. */
    fun keptScrobbles(ctx: Ctx): List<Row> {
        if (!LastFm.connected) return emptyList()
        val dir = File(ctx.filesDir, "wrapped")
        val me = safeName(LastFm.user.lowercase())
        return dir.listFiles { f -> Regex("${Regex.escape(me)}-\\d{4}\\.json").matches(f.name) }.orEmpty().flatMap { f ->
            readText(f)?.let { runCatching { JSON.decodeFromString(Saved.serializer(), it).rows }.getOrNull() }.orEmpty()
        }
    }

    private fun scrobbles(dir: File, period: Period, progress: (String) -> Unit): List<Row> {
        val start = epoch(period.start())
        val end = minOf(epoch(period.end()), now().toLong())
        if (end <= start) return emptyList()
        val file = File(dir, "${safeName(LastFm.user.lowercase())}-${period.key}.json")
        val saved = readText(file)?.let { runCatching { JSON.decodeFromString(Saved.serializer(), it) }.getOrNull() } ?: Saved()
        var rows = saved.rows
        val since = if (saved.until > 0) maxOf(start, saved.until - 14 * 86_400L) else start   // Last.fm takes scrobbles two weeks late
        if (saved.until < end && !lastFmDown) {
            // Last.fm not answering: the scrobbles kept here (all but the newest), rather than no Wrapped at all
            val new = try { LastFm.recent(since, end) { p, n -> progress("Reading your scrobbles · ${period.name} · page $p of $n") } }
                catch (e: Exception) { if (e is LastFmError && e.code !in setOf(8, 11, 16, 29)) throw e; lastFmDown = true; return rows }
            val have = rows.map { it.t to it.title }.toHashSet()
            rows = (rows + new.filter { (it.time to it.title) !in have }.map { Row(it.time, it.artist, it.title, it.album, it.image) })
                .sortedBy { it.t }
            writeAtomic(file, JSON.encodeToString(Saved.serializer(), Saved(rows, end)))
        }
        return rows
    }

    /** Your scrobbles in year, as (time, title), from the cache Wrapped keeps (blocking: what's
     *  new is asked of Last.fm). For [Stats], to tell which Spotify plays Last.fm has too. */
    fun scrobblePlays(ctx: Ctx, period: Period): List<SpotifyHistory.Play> =
        scrobbles(File(ctx.filesDir, "wrapped").apply { mkdirs() }, period) {}.map { SpotifyHistory.Play(it.t, it.artist, it.title, it.album) }

    /** (blocking) Every scrobble you have on Last.fm, a year at a time (kept: after the first time only the
     *  new ones are read). From the year you joined, and further back while there are fewer than Last.fm's
     *  count (scrobbles imported from before). For [Stats]. */
    fun allScrobbles(ctx: Ctx, progress: (String) -> Unit = {}): List<Row> {
        val dir = File(ctx.filesDir, "wrapped").apply { mkdirs() }
        val me = LastFm.info()
        val now = LocalDate.now().year
        val first = me.registered.takeIf { it > 0 }?.let { local(it).year } ?: now
        val rows = ArrayList<Row>()
        for (y in first..now) rows += scrobbles(dir, Period.Year(y), progress)
        var y = first - 1
        while (rows.size < me.playcount - 10 && y >= 2002) { rows += scrobbles(dir, Period.Year(y), progress); y-- }
        return rows
    }

    /** The plays here (the app's own diary) when Last.fm isn't connected: with it, they're scrobbled to Last.fm already. */
    private fun herePlays(): List<SpotifyHistory.Play> = if (LastFm.connected) emptyList() else
        runCatching { Music.plays.items.values.toList().flatMap { e -> e.at.map { SpotifyHistory.Play(it, e.track.artist, e.track.title, e.track.album) } } }
            .getOrDefault(emptyList())

    /** Every play with a time from your imports (Spotify, Deezer, YouTube Music, Tidal, Apple Music), and without
     *  Last.fm the plays here. */
    private fun imported(): List<SpotifyHistory.Play> {
        // A song a history has under someone who isn't on your file of it: counted as your file, as Most played does
        val asYours = runCatching { YourCopies.renamer() }.getOrNull()
        val all = SpotifyHistory.plays + OtherPlays.all + AppleHistory.timed() + herePlays()
        return if (asYours == null) all else all.map { p -> asYours(p.title, p.artist)?.let { p.copy(title = it.first, artist = it.second) } ?: p }
    }

    private var allTimeMilestones: List<Stats.Milestone>? = null

    /** The highest milestone (100, 250, 500, 1,000…) each song and artist has reached in these plays, biggest first,
     *  artists before songs: counted the way [yearStats] counts its songs and artists, each dated by the play that got
     *  it there (0 when plays without a date did). */
    internal fun milestonesOf(rows: List<Row>, undated: List<Row> = emptyList(), n: Int = 6): List<Stats.Milestone> {
        class Acc(var name: String, val artist: String) { var loose = 0; val times = ArrayList<Long>() }
        val songs = LinkedHashMap<Pair<String, String>, Acc>(); val artists = LinkedHashMap<String, Acc>()
        fun add(r: Row, dated: Boolean) {
            val ks = Stats.statsKey(r.title, r.artist); val ka = ks.second.ifEmpty { r.artist.lowercase() }
            val lead = splitArtists(r.artist).firstOrNull()?.ifEmpty { null } ?: r.artist
            val s = songs.getOrPut(ks) { Acc(r.title, r.artist) }; val a = artists.getOrPut(ka) { Acc(lead, "") }
            if (a.name != lead && soloName(mapOf(a.name to 0, lead to 0)) == lead) a.name = lead
            if (dated) { s.times += r.t; a.times += r.t } else { s.loose++; a.loose++ }
        }
        rows.forEach { add(it, true) }; undated.forEach { add(it, false) }
        val out = ArrayList<Stats.Milestone>()
        fun top(what: String, key: String, a: Acc) {
            val total = a.loose + a.times.size
            val level = Stats.MILESTONES.lastOrNull { it <= total } ?: return
            val at = if (level <= a.loose) 0L else a.times.sorted()[level - a.loose - 1]
            out += Stats.Milestone(what, key, a.name, a.artist, level, at)
        }
        artists.forEach { (k, a) -> top("artist", k, a) }
        songs.forEach { (k, a) -> top("song", "${k.first}|${k.second}", a) }
        return out.sortedWith(compareBy({ -it.plays }, { it.what != "artist" }, { -it.at })).take(n)
    }

    /** The plays an Apple Music library export knows only as a song's all-time count: one row a play, no date. Only
     *  what is past the dated Apple plays of the same song (the library's count has those in it), as Most played does;
     *  named as your own file is, and without what you hid from stats. */
    private fun undatedTotals(): List<Row> {
        val totals = AppleHistory.rows.filter { it.t <= 0 && it.count > 0 }
        if (totals.isEmpty()) return emptyList()
        val asYours = runCatching { YourCopies.renamer() }.getOrNull()
        val dated = AppleHistory.timed().groupingBy { Stats.statsKey(it.title, it.artist) }.eachCount()
        val out = ArrayList<Row>()
        for (r in totals) {
            val extra = r.count - (dated[Stats.statsKey(r.title, r.artist)] ?: 0)
            if (extra <= 0) continue
            val (title, artist) = asYours?.invoke(r.title, r.artist) ?: (r.title to r.artist)
            if (StatHides.any && StatHides.play(title, artist, r.album)) continue
            val row = Row(0, artist, title, r.album, "", 0)
            repeat(minOf(extra, 100_000)) { out += row }
        }
        return out
    }

    /** rows with your Spotify and Apple Music plays from start to end, added up (every play in them counts). */
    private fun withSpotify(rows: List<Row>, start: Long, end: Long): List<Row> {
        val plays = imported().filter { it.t in start until end }
        // What you hid from stats (a song, an album, an artist) is left out of Wrapped altogether: its plays aren't in it
        val all = if (plays.isEmpty()) rows else (rows + plays.map { Row(it.t, it.artist, it.title, it.album, "", it.ms) }).sortedBy { it.t }
        return if (!StatHides.any) all else all.filter { !StatHides.play(it.title, it.artist, it.album) }
    }

    /** Last.fm didn't answer while Wrapped was loading (it showed what's kept here). */
    @Volatile private var lastFmDown = false

    /** Your Last.fm profile, kept so Wrapped opens (from your saved scrobbles) while Last.fm is down. */
    @kotlinx.serialization.Serializable
    private data class KeptProfile(val name: String, val playcount: Int, val registered: Long, val image: String)

    private fun profile(dir: File): LastFm.Profile {
        val file = File(dir, "${safeName(LastFm.user.lowercase())}-profile.json")
        return try {
            if (lastFmDown) throw java.io.IOException("Last.fm isn't answering")
            LastFm.info().also { p -> runCatching { writeAtomic(file, JSON.encodeToString(KeptProfile.serializer(), KeptProfile(p.name, p.playcount, p.registered, p.image))) } }
        } catch (e: Exception) {
            if (e is LastFmError && e.code !in setOf(8, 11, 16, 29)) throw e
            val k = readText(file)?.let { runCatching { JSON.decodeFromString(KeptProfile.serializer(), it) }.getOrNull() }
            if (k == null && keptScrobbles(Music.ctx).isEmpty()) throw e
            lastFmDown = true
            k?.let { LastFm.Profile(it.name, it.playcount, it.registered, it.image) } ?: LastFm.Profile(LastFm.user, 0, 0, "")
        }
    }

    /** Everything Wrapped shows for a period. It blocks. */
    fun load(ctx: Ctx, period: Period, progress: (String) -> Unit): Data {
        lastFmDown = false
        val data = try { loadNow(ctx, period, progress) } catch (e: Exception) {
            // Last.fm gave out partway (its totals and top lists for All time): again, from what's kept here
            val busy = (e is HttpError && (e.code >= 500 || e.code == 429)) || (e is LastFmError && e.code in setOf(8, 11, 16, 29)) ||
                (e is java.io.IOException && e !is HttpError)
            if (!busy || !LastFm.connected || lastFmDown) throw e
            lastFmDown = true
            loadNow(ctx, period, progress)
        }
        if (lastFmDown) Music.toast("Last.fm isn't answering right now, so Wrapped shows the scrobbles saved on this device and your imports. Try again later for the newest.")
        return data
    }

    private fun loadNow(ctx: Ctx, period: Period, progress: (String) -> Unit): Data {
        val dir = File(ctx.filesDir, "wrapped").apply { mkdirs() }
        val fm = LastFm.connected
        val spotify = imported().sortedBy { it.t }   // Spotify's, Deezer's, YouTube Music's, Tidal's and Apple Music's plays (and here's)
        // An Apple Music library's all-time counts (no dates): in All time's songs, artists, albums, plays and minutes
        val undated = if (period == Period.All) runCatching { undatedTotals() }.getOrDefault(emptyList()) else emptyList()
        if (!fm && spotify.isEmpty() && undated.isEmpty()) error("Connect Last.fm or import your Spotify, Apple Music, Deezer, YouTube Music or Tidal history in Settings (⚙), or play some music here first.")
        val me = if (fm) { progress("Opening your Last.fm…"); profile(dir) } else null
        val thisYear = LocalDate.now().year
        var firstYear = me?.registered?.takeIf { it > 0 }?.let { local(it).year } ?: thisYear
        spotify.firstOrNull()?.let { firstYear = minOf(firstYear, local(it.t).year) }
        val years = (thisYear downTo firstYear).toList()
        val user = me?.name ?: (Music.sources["spotify"] as? Spotify)?.user?.ifEmpty { null } ?: "You"
        val base = Data(period, user, years, firstYear)
        var data: Data
        var rows: List<Row>
        val local = Local()
        if (period == Period.All && spotify.isEmpty() && undated.isEmpty() && fm && !lastFmDown) {   // Last.fm adds it all up: no need to read every scrobble
            val counts = years.reversed().map { y ->
                progress("Counting your scrobbles · $y")
                y to LastFm.count(startOf(y), startOf(y + 1))
            }
            progress("Your all-time favourites…")
            if (me!!.playcount == 0) return base.copy(empty = true)
            data = base.copy(scrobbles = me.playcount, yearsCount = counts,
                artists = LastFm.top("artist", 30).map { it.name to it.plays },
                songs = LastFm.top("track", TOP + SPARE).map { Song(it.name, it.artist, it.plays, it.image) },
                albums = LastFm.top("album", TOP + SPARE).map { AlbumPlays(it.name, it.artist, it.plays, it.image) },
                minutes = Math.round(me.playcount * 3.5).toInt())   // about three and a half minutes a song
            rows = localRows(keptScrobbles(ctx).sortedBy { it.t }, local)   // what's kept here already, for the pages worked out from plays
                // (less what you hid from your stats, as every other count here)
                .let { rs -> if (StatHides.any) rs.filter { !StatHides.play(it.title, it.artist, it.album) } else rs }
            if (rows.isNotEmpty()) {
                val y = yearStats(base, rows)
                data = data.copy(hours = y.hours, weekdays = y.weekdays, streak = y.streak, days = y.days, busiest = y.busiest,
                    busiestN = y.busiestN, first = y.first, dayRepeat = y.dayRepeat, dayRepeats = y.dayRepeats, artistCount = y.artistCount, songCount = y.songCount)
            }
        } else {
            rows = if (period == Period.All)
                // (every scrobble, as Most played counts them: from before the year you joined too, the ones imported
                // into Last.fm; it had started at that year, and All time came out lower than Most played)
                withSpotify(if (fm) allScrobbles(ctx, progress).sortedBy { it.t } else emptyList(), Long.MIN_VALUE, Long.MAX_VALUE)
            else withSpotify(if (fm) scrobbles(dir, period, progress) else emptyList(), epoch(period.start()), epoch(period.end()))
            if (rows.isEmpty() && undated.isEmpty()) return base.copy(empty = true)
            rows = localRows(rows, local)
            data = yearStats(base, rows, undated)
            if (period != Period.All) {
                val start = epoch(period.start()); val naturalEnd = epoch(period.end())
                val end = minOf(naturalEnd, now().toLong())
                val first = period.start()
                val daily = if (period is Period.Year) emptyList() else {   // day by day, for a week or a month
                    val counts = IntArray(ChronoUnit.DAYS.between(first, period.end()).toInt())
                    for (r in rows) {
                        val i = ChronoUnit.DAYS.between(first, local(r.t).toLocalDate()).toInt()
                        if (i in counts.indices) counts[i]++
                    }
                    counts.toList()
                }
                progress("Comparing with before…")
                // The month (year, week) before itself, as far into it as this one has gone: it had looked back this one's
                // length (February set beside Jan 4 – Feb 1, March beside Jan 29 – Mar 1)
                // (the time before counted as this one is: Last.fm's plays and your imports', so like is set beside like.
                // It was Last.fm's alone, beside a count that had the imports in it.)
                val before = period.before() ?: period
                val prevFrom = epoch(before.start()); val prevTo = minOf(epoch(before.end()), prevFrom + (end - start))
                val prevImported = imported().count { it.t in prevFrom until prevTo }
                val prev = if (fm && !lastFmDown) runCatching { LastFm.count(prevFrom, prevTo) }.getOrNull()?.let { it + prevImported }
                    else if (!fm && prevImported > 0) prevImported else null
                data = data.copy(daily = daily, spanDays = maxOf(1, ((end - start + 86399) / 86400).toInt()),
                    partial = end < naturalEnd - 60, prevCount = prev)
            }
            if (period == Period.All) {
                val perYear = rows.groupingBy { local(it.t).year }.eachCount()
                data = data.copy(yearsCount = years.reversed().map { it to (perYear[it] ?: 0) })
            }
            progress("Adding up your minutes…")
            data = data.copy(minutes = minutes(dir, rows + undated))
            if (period == Period.All) allTimeMilestones = runCatching { milestonesOf(rows, undated, MILESTONES_KEPT) }.getOrNull()
        }
        data = data.copy(milestones = runCatching {
            // All time: from the very plays this Wrapped counted, so a milestone is never behind the play counts shown
            // beside it (the owner's phone said 1,000 plays of an artist its own pages gave 12,325)
            // (more than the six shown: one you hid is left out when it's shown, and the next takes its place)
            if (period == Period.All) allTimeMilestones.also { allTimeMilestones = null } ?: Stats.periodMilestones(0, Long.MAX_VALUE, MILESTONES_KEPT)
            else Stats.periodMilestones(epoch(period.start()), epoch(period.end()), MILESTONES_KEPT)
        }.getOrDefault(emptyList()))
        progress("Finding your genres…")
        data = data.copy(genres = genres(dir, data.artists))
        progress("Reading your songs' genres and moods…")
        data = runCatching { mineStats(data, rows) }.getOrDefault(data)
        if (period != Period.All) {
            progress("Finding your discoveries…")
            data = runCatching { firsts(ctx, data, rows, epoch(period.start())) }.getOrDefault(data)
        }
        val (from, to) = if (period == Period.All) 0L to Long.MAX_VALUE else epoch(period.start()) to epoch(period.end())
        data = data.copy(log = runCatching {
            val diary = ListenLog.all()
            logStats(diary.filter { it.t in from until to }, diary.firstOrNull()?.t ?: 0L)
        }.getOrDefault(LogStats()))
        progress("Working out what kind of listener you are…")
        data = data.copy(type = runCatching { listenerType(dir, data, from, to) }.getOrNull())
        progress("Looking through your library…")
        data = data.copy(library = runCatching { library() }.getOrNull())
        data = data.copy(loops = runCatching {
            val all = period == Period.All
            RepeatFactsRules.summary(RepeatLoops.all(), if (all) "" else period.start().toString(), if (all) "" else period.end().toString(), TOP, loopLength)
        }.getOrNull())
        data = data.copy(songs = localSongs(data.songs, local))
        progress("Getting the pictures…")
        // Every song and album row: the cover inside your own file of it first (read from the file as it is now, so a
        // cover you re-saved shows), then a cover saved for it, Last.fm's, then Deezer's (looked up only when needed).
        val mine = myCovers()
        runCatching { dir.listFiles { f -> f.name.startsWith("mine-") }?.forEach { it.delete() } }   // the old copies of covers (went stale)
        val hasOwn = HashMap<String, Boolean>()
        fun ownCover(file: String) = file.isNotEmpty() &&
            hasOwn.getOrPut(file) { runCatching { embeddedPicture(ctx, file) != null }.getOrDefault(false) }
        fun song(s: Song, big: Boolean): Song {
            // (a row that already has your file still gets the cover saved or picked for it: a file whose own picture
            // can't be read here, as some M4As, shows that instead of nothing)
            val m = if (s.file.isNotEmpty()) Mine(s.file, runCatching { mine(s.artist, s.title, false).saved }.getOrDefault("")) else mine(s.artist, s.title, false)
            val web = picture(s.art).let { if (big && it.isNotEmpty()) bigLastfm(it) else it }
            val art = m.saved.ifEmpty { web }.ifEmpty {
                if (ownCover(m.file)) "" else runCatching { Pictures.deezerPace(); deezerCover(s.artist, s.title) }.getOrNull() ?: ""
            }.ifEmpty {
                // Not on Deezer either (an old EP, a demo): the app's own song cover finder (the service, Last.fm,
                // iTunes), as Most played uses; else the artist's picture, so the row isn't left blank.
                if (ownCover(m.file)) "" else runCatching {
                    SongCovers.look(Track(id = "", source = "lastfm", title = s.title, artist = s.artist))
                        .ifEmpty { SongCovers.fallback(s.title, s.artist) }
                }.getOrNull() ?: ""
            }
            return s.copy(file = m.file, art = art)
        }
        fun album(a: AlbumPlays, big: Boolean): AlbumPlays {
            val m = if (a.file.isNotEmpty()) Mine(a.file, mine(a.artist, a.name, true).saved) else mine(a.artist, a.name, true)
            val web = picture(a.art).let { if (big && it.isNotEmpty()) bigLastfm(it) else it }
            val art = m.saved.ifEmpty { web }.ifEmpty {
                if (ownCover(m.file)) "" else runCatching { Pictures.deezerPace(); deezerAlbumCover(a.artist, bareRelease(a.name)) }.getOrNull() ?: ""
            }.ifEmpty {   // …by its name without a featured artist ("daisies (feat. Flowars)" as daisies)
                if (ownCover(m.file) || bareTitle(a.name) == bareRelease(a.name)) "" else runCatching { Pictures.deezerPace(); deezerAlbumCover(a.artist, bareTitle(a.name)) }.getOrNull() ?: ""
            }.ifEmpty {   // a single is a song: the app's song cover finder (Deezer's songs, the service, Last.fm, iTunes, Genius)
                if (ownCover(m.file)) "" else runCatching { SongCovers.look(Track(id = "", source = "lastfm", title = a.name, artist = a.artist)) }.getOrNull() ?: ""
            }.ifEmpty {   // nothing has it: the artist's picture, so the row isn't left blank
                if (ownCover(m.file)) "" else runCatching { Pictures.known(a.artist)?.let { p -> p.saved.takeIf { it.isNotEmpty() && File(it).exists() } ?: p.image } }.getOrNull() ?: ""
            }
            return a.copy(file = m.file, art = art)
        }
        data = data.copy(
            artistImage = data.artists.firstOrNull()?.let { runCatching { deezerArtistImage(it.first) }.getOrNull() } ?: "",
            songs = data.songs.mapIndexed { i, s -> runCatching { song(s, i == 0) }.getOrDefault(s) },
            albums = data.albums.mapIndexed { i, a -> runCatching { album(a, i == 0) }.getOrDefault(a) },
            singles = data.singles.mapIndexed { i, a -> runCatching { album(a, i == 0) }.getOrDefault(a) },
            eps = data.eps.mapIndexed { i, a -> runCatching { album(a, i == 0) }.getOrDefault(a) },
            discovered = data.discovered.map { s -> runCatching { song(s, false) }.getOrDefault(s) },
            log = data.log.copy(searched = data.log.searched.mapIndexed { i, s -> runCatching { song(s, i == 0) }.getOrDefault(s) }),
        )
        return data
    }

    // ---- the pages worked out from your own songs, your whole history and the listening diary ----

    /** A release's name without its kind on the end: "Talk - Single" -> "Talk", "Hold On (EP)" -> "Hold On". */
    internal fun bareRelease(name: String): String =
        name.replace(Regex("(?:\\s+[-–]\\s+|\\s*[(\\[]|\\s+)(?:single|ep)[)\\]]?\\s*$", RegexOption.IGNORE_CASE), "").trim().ifEmpty { name }

    /** A song's mood (Moods), or null. */
    private fun moodOf(t: Track): String? = runCatching { Moods.of(t) }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Plays by your songs' genre tags and moods, and your singles and EPs played most: each play matched to your own
     *  copy of the song (the plays of songs you haven't got count for none of these). */
    private fun mineStats(d: Data, rows: List<Row>): Data {
        if (rows.isEmpty()) return d
        val mine = HashMap<Pair<String, String>, Track>()
        for (t in Music.mySongs()) mine.putIfAbsent(Stats.statsKey(t.title, t.artist), t)
        if (mine.isEmpty()) return d
        val genres = HashMap<String, Int>(); val genreName = HashMap<String, String>()
        val moods = HashMap<String, Int>()
        class Rel(val name: String, val artist: String, val t: Track) { var n = 0 }
        val singles = LinkedHashMap<String, Rel>(); val eps = LinkedHashMap<String, Rel>()
        val genreCache = HashMap<String, List<String>>(); val moodCache = HashMap<String, String>(); val tabCache = HashMap<String, String>()
        for (r in rows) {
            val t = mine[Stats.statsKey(r.title, r.artist)] ?: continue
            for (g in genreCache.getOrPut(t.id) { countedGenres(Music.genreOf(t)) }) {
                val k = g.lowercase(); genres.merge(k, 1, Int::plus); genreName.putIfAbsent(k, g)
            }
            moodCache.getOrPut(t.id) { moodOf(t) ?: "" }.takeIf { it.isNotEmpty() }?.let { moods.merge(it, 1, Int::plus) }
            val kind = tabCache.getOrPut(t.id) { runCatching { Music.tabOf(t) }.getOrNull() ?: "" }
            if (kind == "single" || kind == "ep") {
                val lead = splitArtists(t.artist).firstOrNull()?.ifEmpty { null } ?: t.artist
                val name = bareRelease(if (kind == "single") t.album.ifBlank { t.title } else t.album.ifBlank { t.title })
                val key = norm(cleanTitle(name)) + "|" + norm(lead)
                (if (kind == "single") singles else eps).getOrPut(key) { Rel(name, lead, t) }.n++
            }
        }
        // A release's tag can call it a single when it's really an EP (or the other way round): the most played of each
        // checked against what Deezer or Apple Music says it is (looked up once, then remembered), and moved over
        for ((from, to, kind) in listOf(Triple(singles, eps, "ep"), Triple(eps, singles, "single"))) {
            for ((key, r) in from.entries.sortedByDescending { it.value.n }.take(TOP * 2).map { it.key to it.value }) {
                val truth = runCatching { AlbumTruth.kindKnown(r.t.album, r.t.artist) }.getOrNull() ?: continue
                if (truth == kind) { from.remove(key); val name = bareRelease(r.t.album.ifBlank { r.name })
                    to.getOrPut(key) { Rel(name, r.artist, r.t) }.n += r.n }
                else if (truth == "album") from.remove(key)
            }
        }
        fun top(m: Map<String, Rel>) = m.values.sortedByDescending { it.n }.take(TOP + SPARE).map { r ->
            // its picture: the file's own cover (load's last step): a file in your folders, or one of the app's downloads
            AlbumPlays(r.name, r.artist, r.n, "", r.t.file ?: runCatching { Music.lib.items[r.t.id]?.file }.getOrNull() ?: "")
        }
        return d.copy(fileGenres = genres.entries.sortedByDescending { it.value }.take(TOP).map { genreName.getValue(it.key) to it.value },
            moods = moods.entries.sortedByDescending { it.value }.take(TOP).map { it.key to it.value },
            genreScore = Listening.diversity(genres.values), moodScore = Listening.diversity(moods.values),
            singles = top(singles), eps = top(eps))
    }

    /** Songs and artists first played in the period (from start): every play in your history (Last.fm's kept here,
     *  your imports, the plays here) tells when you first played each. Only when that history goes back further. */
    private fun firsts(ctx: Ctx, d: Data, rows: List<Row>, start: Long): Data {
        val firstSong = HashMap<Pair<String, String>, Long>(); val firstArtist = HashMap<String, Long>()
        // Under the names the period's plays have (your own copy's: a song a history has under someone else), so a song
        // you played years ago under another name isn't "new to you"
        val asYours = runCatching { YourCopies.renamer() }.getOrNull()
        fun see(t: Long, title0: String, artist0: String) {
            if (t <= 0) return
            val (title, artist) = asYours?.let { runCatching { it(title0, artist0) }.getOrNull() } ?: (title0 to artist0)
            val ks = Stats.statsKey(title, artist); val ka = ks.second.ifEmpty { artist.lowercase() }
            if (t < (firstSong[ks] ?: Long.MAX_VALUE)) firstSong[ks] = t
            if (t < (firstArtist[ka] ?: Long.MAX_VALUE)) firstArtist[ka] = t
        }
        keptScrobbles(ctx).forEach { see(it.t, it.title, it.artist) }
        (SpotifyHistory.plays + OtherPlays.all + AppleHistory.timed()).forEach { see(it.t, it.title, it.artist) }
        runCatching { Music.plays.items.values.toList() }.getOrDefault(emptyList()).forEach { e -> e.at.forEach { see(it, e.track.title, e.track.artist) } }
        rows.forEach { see(it.t, it.title, it.artist) }
        val earliest = firstSong.values.minOrNull() ?: return d
        if (earliest > start - 30 * 86_400L) return d   // no history from before it: everything would look new
        val songPlays = LinkedHashMap<Pair<String, String>, Int>(); val names = HashMap<Pair<String, String>, Pair<String, String>>()
        val artistPlays = LinkedHashMap<String, Int>(); val artistName = HashMap<String, String>()
        for (r in rows) {
            val ks = Stats.statsKey(r.title, r.artist); val ka = ks.second.ifEmpty { r.artist.lowercase() }
            if ((firstSong[ks] ?: 0L) >= start) { songPlays.merge(ks, 1, Int::plus); names.putIfAbsent(ks, r.title to r.artist) }
            if ((firstArtist[ka] ?: 0L) >= start) {
                artistPlays.merge(ka, 1, Int::plus)
                artistName.putIfAbsent(ka, splitArtists(r.artist).firstOrNull()?.ifEmpty { null } ?: r.artist)
            }
        }
        return d.copy(firstsKnown = true, discoveredCount = songPlays.size, newArtistCount = artistPlays.size,
            discovered = songPlays.entries.sortedByDescending { it.value }.take(TOP + SPARE).map { (k, n) ->
                Song(names.getValue(k).first, names.getValue(k).second, n, "", firstSong[k]) },
            newArtists = artistPlays.entries.sortedByDescending { it.value }.take(TOP + SPARE).map { artistName.getValue(it.key) to it.value })
    }

    /** What the listening diary says about a period's entries (since: when the diary began). */
    internal fun logStats(es: List<ListenLog.Entry>, since: Long): LogStats {
        if (es.isEmpty()) return LogStats(since = since)
        val names = runCatching { Music.lists.all().associate { it.id to it.name } }.getOrDefault(emptyMap())
        val playlists = es.filter { it.kind == "playlist" }.groupBy { it.list.ifEmpty { it.from } }.map { (k, l) ->
            ListTime(names[k] ?: l.last().from, l.sumOf { it.secs }, l.size, l.filter { it.shuffled }.sumOf { it.secs })
        }.sortedByDescending { it.secs }.take(TOP)
        val kinds = es.groupBy { it.kind.ifEmpty { "other" } }.map { (k, l) -> k to l.sumOf { it.secs } }.sortedByDescending { it.second }
        val devices = es.groupBy { it.out.ifEmpty { "Unknown output" } }.map { (k, l) ->
            Device(k, l.groupingBy { it.outKind }.eachCount().maxByOrNull { it.value }?.key ?: "", l.sumOf { it.secs }, l.size)
        }.sortedByDescending { it.secs }
        // Most searched: every play of a song you took from search, tapped there or put in the queue (or Play next) from
        // it, however it got into the queue (it had to be 4 times in a week to count, so it mostly showed nothing)
        val searched = es.filter { it.search }.groupBy { Stats.statsKey(it.title, it.artist) }.values.map { l ->
            val e = l.first(); Song(e.title, e.artist, l.size, "", e.t)   // pictures: load's last step
        }.sortedByDescending { it.plays }.take(TOP + SPARE)
        // Shuffled or in order: listening from your playlists, albums and other lists (not the DJ, a radio or a search)
        val listed = es.filter { it.kind in setOf("playlist", "album", "artist", "other") }
        val listedSecs = listed.sumOf { it.secs }
        val shuffle = if (listedSecs >= 1800) listed.filter { it.shuffled }.sumOf { it.secs } / listedSecs else null
        // The longest session: songs one after another, less than five minutes between them
        var session = 0.0; var runStart = es.first().t; var runEnd = es.first().t + es.first().secs
        for (e in es.drop(1)) {
            if (e.t - runEnd > 300) { session = maxOf(session, runEnd - runStart); runStart = e.t }
            runEnd = maxOf(runEnd, e.t + e.secs)
        }
        session = maxOf(session, runEnd - runStart)
        return LogStats(since, es.sumOf { it.secs }, playlists, kinds, devices, searched, shuffle, session)
    }

    @Serializable
    private data class Fans(val n: Long, val at: Long)
    private val fanSer = MapSerializer(String.serializer(), Fans.serializer())

    /** (blocking) How many fans each artist has on Deezer (nb_fan), kept for three months in artist-fans.json. */
    private fun fans(dir: File, names: List<String>): Map<String, Long> {
        val file = File(dir, "artist-fans.json")
        val cache = readText(file)?.let { runCatching { JSON.decodeFromString(fanSer, it) }.getOrNull() }?.toMutableMap() ?: mutableMapOf()
        val nowS = now().toLong()
        val out = HashMap<String, Long>()
        var asked = false
        for (name in names) {
            val k = norm(name)
            val kept = cache[k]?.takeIf { nowS - it.at < 90 * 86_400L }
            val n = kept?.n ?: runCatching {
                Pictures.deezerPace()
                asked = true
                val best = httpJson("https://api.deezer.com/search/artist?q=${enc(name)}&limit=5")["data"].list
                    .maxByOrNull { similar(norm(it["name"].str), k) }
                if (best != null && similar(norm(best["name"].str), k) > 0.85) best["nb_fan"].long ?: 0L else 0L
            }.getOrNull()?.also { cache[k] = Fans(it, nowS) }
            if (n != null && n > 0) out[name] = n
        }
        if (asked) runCatching { writeAtomic(file, JSON.encodeToString(fanSer, cache)) }
        return out
    }

    /** The listener type, from the period's plays, its skips, the diary and Deezer's fan counts. */
    private fun listenerType(dir: File, d: Data, from: Long, to: Long): Listening.Kind {
        val top = d.artists.take(30)
        val fans = runCatching { fans(dir, top.map { it.first }) }.getOrDefault(emptyMap())
        val plays = d.scrobbles.coerceAtLeast(1).toDouble()
        val hoursKnown = d.hours.sum() > 0
        val h = runCatching { Music.songStats.habits(from.toDouble(), minOf(to, Long.MAX_VALUE / 2).toDouble(), null) }.getOrNull()
        return Listening.type(Listening.Traits(
            fans = Listening.weightedMedian(top.mapNotNull { (a, n) -> fans[a]?.let { it to n } }),
            shuffle = d.log.shuffle,
            top10 = if (d.scrobbles >= 50) d.artists.take(10).sumOf { it.second } / plays else null,
            newArtists = if (d.firstsKnown && d.artistCount > 0) d.newArtistCount.toDouble() / d.artistCount else null,
            night = if (hoursKnown) listOf(22, 23, 0, 1, 2, 3).sumOf { d.hours[it] } / d.hours.sum().toDouble() else 0.0,
            morning = if (hoursKnown) (5 until 10).sumOf { d.hours[it] } / d.hours.sum().toDouble() else 0.0,
            skips = h?.takeIf { it.skips + it.full >= 20 }?.let { it.skips.toDouble() / (it.skips + it.full) },
        ))
    }

    /** (blocking) Your music as it is now: songs, albums, EPs, singles, artists, how long it all plays, and how varied. */
    fun library(): LibraryStats {
        val songs = Music.mySongs()
        var albums = 0; var eps = 0; var singles = 0
        for ((a, ts) in Music.albums()) {
            if (ts.all { it.album.isBlank() }) { singles += ts.size; continue }   // no album at all: each one a single
            when (runCatching { Music.releaseKind(a.title, ts) }.getOrDefault("album")) { "ep" -> eps++; "single" -> singles++; else -> albums++ }
        }
        val artists = songs.map { norm(splitArtists(it.artist).firstOrNull() ?: it.artist) }.filter { it.isNotEmpty() }.toSet().size
        val genres = HashMap<String, Int>(); val genreName = HashMap<String, String>(); val moods = HashMap<String, Int>()
        for (t in songs) {
            for (g in countedGenres(Music.genreOf(t))) { val k = g.lowercase(); genres.merge(k, 1, Int::plus); genreName.putIfAbsent(k, g) }
            moodOf(t)?.let { moods.merge(it, 1, Int::plus) }
        }
        return LibraryStats(songs.size, albums, eps, singles, artists, songs.sumOf { it.duration.coerceAtLeast(0.0) },
            genres.entries.sortedByDescending { it.value }.map { genreName.getValue(it.key) to it.value }, Listening.diversity(genres.values),
            moods.entries.sortedByDescending { it.value }.map { it.key to it.value }, Listening.diversity(moods.values))
    }

    private fun deezerAlbumCover(artist: String, album: String): String? =
        httpJson("https://api.deezer.com/search/album?q=${enc("artist:\"$artist\" album:\"$album\"")}&limit=1")["data"][0]["cover_xl"].str

    /** Your own copy of a song or album: its file ("" if you haven't got it) and a cover saved for it ("" if none). */
    private class Mine(val file: String, val saved: String)

    /** A finder of your own copies: (artist, title or album, album?) -> [Mine]. Songs are matched as Most played matches
     *  them (the same title and artist, an artist of it, or the same album; your best file of it); albums by name (an
     *  album and its deluxe edition are one, "- Single"/"EP" ends ignored) and one of their artists. */
    private fun myCovers(): (String, String, Boolean) -> Mine {
        val index = Music.songIndex()   // also readies Music.fmSong's lookups
        fun albumKey(name: String) = norm(cleanTitle(albumEdition(bareRelease(name)).first))
        val albums = HashMap<String, MutableList<Track>>()
        for (t in Music.mySongs()) if (t.file != null && t.album.isNotBlank()) albums.getOrPut(albumKey(t.album)) { ArrayList() } += t
        val byKey = HashMap<Pair<String, String>, Track>()
        val byLoose = HashMap<Pair<String, String>, Track>()
        for (t in Music.mySongs()) if (!t.file.isNullOrEmpty()) {
            byKey.putIfAbsent(Stats.statsKey(t.title, t.artist), t)
            byLoose.putIfAbsent(looseTitle(t.title) to Stats.statsKey(t.title, t.artist).second, t)
        }
        fun saved(t: Track): String {
            Music.lib.items[t.id]?.art?.takeIf { it.isNotEmpty() && File(it).exists() }?.let { return it }
            val p = Pictures.cover(t.artist, t.album.ifEmpty { t.title })
            return if (p.startsWith("/") || (p.length > 2 && p[1] == ':')) p.takeIf { File(it).exists() } ?: "" else p
        }
        return { artist, name, album ->
            val t: Track? = if (album) {
                val theirs = splitArtists(artist).map { norm(it) }.filter { it.isNotEmpty() }.toSet() + norm(artist)
                albums[albumKey(name)].orEmpty().firstOrNull { c -> norm(c.artist) in theirs || splitArtists(c.artist).any { norm(it) in theirs } }
            } else runCatching { Music.fmSong(name, artist, "", index) }.getOrNull()?.takeIf { !it.file.isNullOrEmpty() }
                ?: byKey[Stats.statsKey(name, artist)]   // the same song under a slightly different title ("(LBP2 Version)")
                ?: byLoose[looseTitle(name) to Stats.statsKey(name, artist).second]
            val file = t?.file
            if (t == null || file.isNullOrEmpty()) Mine("", "") else Mine(file, saved(t))
        }
    }
}
