package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/** One Radon Player at a time, as the Python app: starting it again (or opening a song file with it, or a command
 *  like `"Radon Player.exe" next`) hands what was asked to the one already running, over a local socket that
 *  only this computer can reach, with a secret only this account can read.
 *
 *  Commands (the Python app's): see [HELP]; and song files to play. */
object Instance {
    // A test build with a folder of its own (RADON_DATA) answers on a port of its own, beside the installed app
    private val PORT = if (System.getenv("RADON_DATA").isNullOrBlank()) 8896 else 8906
    private val dir: File get() = Ctx.pc.filesDir
    private val secretFile get() = File(dir, "instance.key")

    /** Hand args to a running one; true if one took them (this one should then end). */
    fun handOff(args: Array<String>): Boolean = runCatching {
        val key = secretFile.takeIf { it.isFile }?.readText()?.trim() ?: return false
        Socket(InetAddress.getLoopbackAddress(), PORT).use { s ->
            s.soTimeout = 3000
            val out = s.getOutputStream().bufferedWriter()
            out.write(key + "\n"); args.forEach { out.write(it.replace("\n", " ") + "\n") }; out.write("\n"); out.flush()
            val r = s.getInputStream().bufferedReader()
            while (true) {   // what it answers (status, help), then "ok"
                val l = r.readLine() ?: return@use false
                if (l == "ok") return@use true
                println(l)
            }
            @Suppress("UNREACHABLE_CODE") false
        }
    }.getOrDefault(false)

    /** Listen for others (after the app has started); act on what they ask. */
    fun listen(onArgs: (List<String>) -> Unit) {
        val server = runCatching { ServerSocket(PORT, 8, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        val key = java.util.UUID.randomUUID().toString()
        runCatching { writeAtomic(secretFile, key) }
        Thread({
            while (true) {
                val s = runCatching { server.accept() }.getOrNull() ?: continue
                runCatching {
                    s.use {
                        it.soTimeout = 3000
                        val r = it.getInputStream().bufferedReader()
                        if (r.readLine()?.trim() != key) return@use
                        val got = generateSequence { r.readLine()?.takeIf { l -> l.isNotEmpty() } }.toList()
                        val answer = when (got.firstOrNull { a -> !a.startsWith("--") }?.lowercase()) {
                            "status" -> status(); "help", "-h" -> HELP; else -> ""
                        }
                        it.getOutputStream().write((answer + (if (answer.isEmpty()) "" else "\n") + "ok\n").toByteArray()); it.getOutputStream().flush()
                        Music.scope.launch(Dispatchers.Main) { onArgs(got) }
                    }
                }
            }
        }, "instance").apply { isDaemon = true }.start()
    }

    const val HELP = """Radon Player: what you can ask from a command line (the app starts if it isn't running)
  Radon Player                      open it (or bring it up)
  play SONG...                      play now: a search, a link or a song file
  play playlist NAME [shuffle]      play one of your playlists, from the top or shuffled
  next SONG...                      play right after the current song
  queue SONG...                     add to the queue, after the songs you queued before
  play | pause | play-pause | skip | prev    without a song: the transport
  shuffle | repeat                  shuffle the queue; repeat goes off, all, one in turn
  lyrics [genius|synced|card]       show the lyrics of what's playing (as written, or timed), or make a lyrics card of them
  search WORDS...                   open on a search
  show [TAB]                        open on now, search, library, music, albums, eps, singles, artists, genres,
                                    playlists, folders, recent, top, skips, favorites, repeat, queue, lyrics, pinned, dj
                                    or new releases
  show pin NAME                     open something you pinned (an artist, an album, a playlist…)
  show wrapped [week|month|all|YEAR]   Wrapped for this week, this month, every year or a year (this year without one)
  accounts                          Settings: sign-ins, folders, Last.fm, the tagger
  wrapped [YEAR|month|week|all]     your year (month, week or every year) in music
  source youtube|tidal|apple|spotify|mine   switch the library you browse and search
  tag [all]                         tag the saved songs not tagged yet (or all of them) now
  dj [on|off|new|mine|switch]       start the DJ, stop it, start it with new songs too or with only yours, or a new set now
  widget NAME [off]                 open (or close) a desktop widget: queue, playlists, dj, playlist, pinned, top, wrapped,
                                    lyrics, releases, visualizer or shortcuts
  widgets save | widgets back       save the widgets' layout as they are now, or put them back as it was saved
  status                            what's playing and what's next
  hide | quit                       hide the window, or stop the music and quit
  --tidal-login | --lastfm-login | --spotify-login   open that sign-in"""

    /** What's playing and what's next (the status command). */
    private fun status(): String = runCatching {
        val t = Music.current()
        buildString {
            append(if (t == null) "Nothing playing" else "${if (Music.paused) "Paused" else "Playing"}: ${t.title} · ${t.artist}" +
                (if (t.album.isNotEmpty()) " · ${t.album}" else "") + " (${fmtTime(Music.position)} of ${fmtTime(Music.duration)})")
            Music.queue.drop(Music.index + 1).take(5).forEachIndexed { i, n -> append("\n${if (i == 0) "Next" else "    "}: ${n.title} · ${n.artist}") }
            if (Music.dj.on) append("\nDJ: on" + (Music.dj.sets[Music.dj.shown]?.let { " · ${Dj.kindName(it.kind) ?: it.kind} · ${it.line}" } ?: ""))
        }
    }.getOrDefault("Nothing playing")

    /** A song from words: your own songs first, else the service you search; a link plays from its service. */
    private fun findSongs(q: String, done: (List<Track>) -> Unit) {
        val link = linkSource(q)
        if (link == null) localMatches(Music.mySongs(), q).songs.takeIf { it.isNotEmpty() }?.let { done(it.take(1)); return }
        val key = link ?: Music.searchIn.takeIf { it != "local" } ?: "ytm"
        val src = Music.sources[key] ?: Music.sources["ytm"] ?: run { Music.toast("Still connecting to the services: try again in a moment"); return }
        Music.bg({ if (link != null) src.search(q) else src.find(q).songs }) { res, err ->
            val songs = res.orEmpty()
            if (songs.isEmpty()) Music.toast("Nothing found for “$q”" + (err?.message?.let { ": ${it.take(60)}" } ?: ""))
            else done(if (link != null) songs else songs.take(1))
        }
    }

    /** The main window, as the app's start (desktop Main) hands it over: bring it up, whether it's up now, hide it,
     *  quit. Commands and the desktop widgets reach the window through it. */
    class MainWindow(val show: () -> Unit, val isShown: () -> Boolean, val hide: () -> Unit, val quit: () -> Unit)
    var window = MainWindow({}, { true }, {}, {})

    /** Something to do on the main window's screens, with the window brought up first. A window that was closed opens
     *  with screens of its own (the closed one's are gone), so this waits for them (3 s at most). Before, a command
     *  sent while the window was in the tray only opened it: what it asked for went to the closed window's screens. */
    fun inWindow(block: (org.omarchy.music.ui.Ui) -> Unit) {
        val before = org.omarchy.music.ui.Ui.current
        val up = window.isShown()
        window.show()
        if (up && before != null) { block(before); return }
        Music.scope.launch(Dispatchers.Main) {
            val ui = kotlinx.coroutines.withTimeoutOrNull(3000) {
                while (org.omarchy.music.ui.Ui.current.let { it == null || it === before }) kotlinx.coroutines.delay(30)
                org.omarchy.music.ui.Ui.current
            } ?: org.omarchy.music.ui.Ui.current ?: return@launch
            block(ui)
        }
    }

    /** A Wrapped period from a command's word: week, month, all or a year; null (no word): this year. */
    private fun wrappedPeriod(w: String): Wrapped.Period? {
        val now = java.time.LocalDate.now()
        return when {
            w.equals("month", true) -> Wrapped.Period.Month(java.time.YearMonth.from(now))
            w.equals("week", true) -> Wrapped.Period.Week(now.with(java.time.DayOfWeek.MONDAY))
            w.equals("all", true) -> Wrapped.Period.All
            w.toIntOrNull() != null -> Wrapped.Period.Year(w.toInt())
            else -> null
        }
    }

    /** Wrapped opened on a period. Already open on another: shut and opened again, so it starts on the one asked. */
    private fun openWrapped(ui: org.omarchy.music.ui.Ui, w: String) {
        org.omarchy.music.ui.WrappedAsk.period = wrappedPeriod(w)
        if (ui.sheet != "wrapped") { ui.sheet = "wrapped"; return }
        ui.sheet = null
        Music.scope.launch(Dispatchers.Main) { kotlinx.coroutines.delay(80); ui.sheet = "wrapped" }
    }

    /** play playlist NAME [shuffle]: one of your playlists (a smart one too), from the top or shuffled. */
    fun playPlaylist(words: String) {
        val lists = Music.lists.all() + Music.lists.smart()
        val (name, shuffled) = DesktopWidgetRules.playlistAsked(words, lists.map { it.name })
            ?: run { Music.toast("No playlist called “${words.trim()}”"); return }
        val p = lists.first { it.name == name }
        val songs = if (p.smart != null) SmartLists.songs(p).orEmpty() else p.tracks
        if (songs.isEmpty()) Music.toast("“${p.name}” has no songs") else Music.playList(songs, shuffled)
    }

    /** lyrics card: Lyrics card… for the song playing, in the main window (its lyrics found first). */
    fun lyricsCard() {
        val t = Music.current() ?: run { Music.toast("Nothing is playing"); return }
        Music.bg({ Lyrics.get(Music.ctx, t) }) { g, _ ->
            if (g == null || (g.plain == null && g.synced == null)) { Music.toast("No lyrics found for “${t.title}”"); return@bg }
            inWindow { ui -> org.omarchy.music.ui.goTo(ui, "player"); ui.pane = "lyrics"; ui.lyricsCard = t to g }
        }
    }

    /** What a command line asks: the commands, else song files to play. */
    fun act(args: List<String>) {
        val show = window.show
        when {
            "--tidal-login" in args -> { inWindow { it.sheet = "tidal-login" }; return }
            "--lastfm-login" in args -> { inWindow { it.openSettings("Last.fm & your stats") }; return }
            "--spotify-login" in args -> { inWindow { it.openSettings("Accounts") }; return }
        }
        val words = args.filter { !it.startsWith("--") }
        val files = words.mapNotNull { a -> Music.local.trackOf(a.removePrefix("file://")) }
        if (files.isNotEmpty()) { Music.playList(files); show(); return }
        val verb = words.firstOrNull()?.lowercase()
        val rest = words.drop(1).joinToString(" ").trim()
        when (verb) {
            null, "show", "open", "back" -> {
                if ("--tray" in args) return
                if (rest.isEmpty()) show() else inWindow { ui -> showIn(ui, rest) }
            }
            "play" -> if (rest.isEmpty()) { if (Music.paused) Music.togglePause() }
                else if (rest.lowercase().startsWith("playlist ")) playPlaylist(rest.drop(9))
                else findSongs(rest) { Music.playList(it) }
            "next" -> if (rest.isEmpty()) Music.next() else findSongs(rest) { Music.enqueue(it, "next") }
            "queue", "add" -> if (rest.isNotEmpty()) findSongs(rest) { Music.enqueue(it, "queue"); Music.toast("Queued: ${it[0].title}") }
            "pause" -> if (!Music.paused) Music.togglePause()
            "play-pause", "toggle" -> Music.togglePause()
            "skip" -> Music.next()
            "prev", "previous" -> Music.previous()
            "shuffle" -> Music.shuffle()
            "repeat" -> Music.cycleRepeat()
            "lyrics" -> if (rest.equals("card", true)) lyricsCard() else {
                if (rest == "genius" || rest == "synced") Music.setLyricsMode(rest)
                inWindow { org.omarchy.music.ui.goTo(it, "player"); it.pane = "lyrics" }
            }
            "search" -> inWindow { u -> u.query = rest; if (rest.isNotEmpty()) org.omarchy.music.ui.runSearch(u) else org.omarchy.music.ui.goTo(u, "search") }
            "accounts", "settings" -> inWindow { it.openSettings("Accounts") }
            "wrapped" -> inWindow { openWrapped(it, rest) }
            "source" -> {
                val key = when (rest.lowercase()) { "youtube", "ytm", "youtube-music" -> "ytm"; "tidal" -> "tidal"; "apple" -> "apple"
                    "spotify" -> "spotify"; "mine", "local", "my-music" -> "local"; else -> null }
                if (key == null) Music.toast("Sources: youtube, tidal, apple, spotify or mine")
                else if (key == "local") { Music.browseLocal = true; Music.toast("Browsing your own music") }
                else { Music.browseLocal = false; Music.setSource(key); Music.toast("Browsing ${SOURCES[key]}") }
            }
            "tag" -> { Tagger.start(full = rest.equals("all", true)); Music.toast("Tagging your saved songs…") }
            // The DJ: on (as its button), off, a new set now (Switch it up), or started with new songs too or only yours
            // (as its Only my songs / New songs too switch)
            "dj" -> when (rest.lowercase()) {
                "", "on", "start" -> if (!Music.dj.on) Music.djStart()
                "off", "stop" -> Music.djStop()
                "switch", "next" -> Music.djSwitch()
                "new" -> { Music.chooseDjNew(true); if (!Music.dj.on) Music.djStart() }
                "mine", "only", "my" -> { Music.chooseDjNew(false); if (!Music.dj.on) Music.djStart() }
                else -> Music.toast("dj, dj off, dj new, dj mine or dj switch")
            }
            // A desktop widget opened (or closed: widget lyrics off)
            "widget", "widgets" -> {
                // The saved layout: kept now, or put back
                when (rest.lowercase()) {
                    "save" -> { Music.bg({ org.omarchy.music.ui.DesktopWidgets.saveLayout() }) { m, _ -> m?.let { Music.toast(it) } }; return }
                    "back", "restore" -> { org.omarchy.music.ui.DesktopWidgets.putBackLayout(); return }
                }
                val off = rest.lowercase().endsWith(" off")
                val w = if (off) rest.dropLast(4) else rest
                val kinds = DesktopWidgetRules.KINDS
                val kind = DesktopWidgetRules.named(w, kinds.map { it.first })
                    ?: DesktopWidgetRules.named(w, kinds.map { it.second })?.let { n -> kinds.first { it.second == n }.first }
                if (kind == null) Music.toast("Widgets: " + kinds.joinToString(", ") { it.first })
                else org.omarchy.music.ui.DesktopWidgets.open(kind, !off)
            }
            "hide" -> window.hide()
            "quit" -> window.quit()
        }
    }

    /** show …: the place asked for, on the main window's screens. */
    private fun showIn(ui: org.omarchy.music.ui.Ui, rest: String) {
        val low = rest.lowercase()
        // "show settings Playback": Settings with that section open (its name as Settings shows it)
        if (low.startsWith("settings ")) { ui.openSettings(rest.drop(9).trim()); return }
        // "show preset preset-31": that Personalization preset put on, with Now playing in front (the checks picture each)
        if (low.startsWith("preset ")) {
            org.omarchy.music.ui.MyPresets.apply(rest.drop(7).trim())
            ui.sheet = null   // (Settings, if it is open, would be in the way)
            org.omarchy.music.ui.goTo(ui, "player"); ui.pane = "art"; return
        }
        // "show now 70": Size of Now playing set to that (Settings › General), with Now playing in front
        if (low.startsWith("now ") || low.startsWith("nowplaying ")) {
            val pc = rest.substringAfter(' ').trim().removeSuffix("%")
            org.omarchy.music.Prefs.setText(org.omarchy.music.NowSizeRules.PREF, org.omarchy.music.NowSizeRules.percent(pc).toString())
            ui.sheet = null
            org.omarchy.music.ui.goTo(ui, "player"); ui.pane = "art"; return
        }
        // "show vis blocks": that visualizer alone along Now playing's bottom, at full strength, with Now playing in
        // front (the checks film it for the website)
        if (low.startsWith("vis ")) {
            org.omarchy.music.Prefs.setText(org.omarchy.music.ui.Vis.NOW, rest.drop(4).trim())
            org.omarchy.music.Prefs.set(org.omarchy.music.ui.Vis.NOW_DUAL, false)
            org.omarchy.music.Prefs.setNum(org.omarchy.music.ui.Vis.NOW_STRENGTH, 1f)
            org.omarchy.music.ui.MyPresets.applied++
            ui.sheet = null
            org.omarchy.music.ui.goTo(ui, "player"); ui.pane = "art"; return
        }
        // "show wrapped month": Wrapped on this week, this month, every year or a year
        if (low == "wrapped" || low.startsWith("wrapped ")) { openWrapped(ui, rest.drop(7).trim()); return }
        // "show pin Road trip": something pinned, opened where it lives (the Pinned widget's rows)
        if (low.startsWith("pin ")) {
            val name = DesktopWidgetRules.named(rest.drop(4), Music.pins.map { it.title })
            val p = Music.pins.firstOrNull { it.title == name }
            if (p == null) Music.toast("Nothing pinned called “${rest.drop(4).trim()}”") else { ui.sheet = null; org.omarchy.music.ui.openPin(ui, p) }
            return
        }
        when (low) {
            "now", "player", "nowplaying" -> { org.omarchy.music.ui.goTo(ui, "player"); ui.pane = "art" }
            "lyrics" -> { org.omarchy.music.ui.goTo(ui, "player"); ui.pane = "lyrics" }
            "queue" -> org.omarchy.music.ui.goTo(ui, "queue")
            "search", "library", "albums", "eps", "singles", "artists", "genres", "moods", "smartlists", "moodlists", "folders", "unreleased", "downloads", "flac24", "flac32", "type-flac", "type-mp3", "type-m4a", "type-wav", "type-ogg", "type-other", "visualizer" -> org.omarchy.music.ui.goTo(ui, low)
            "music", "all", "mine" -> org.omarchy.music.ui.goTo(ui, "music", "all")
            "playlists", "recent", "top", "skips", "favorites", "unfavorited", "repeat", "dj", "discovery", "newreleases", "dupes", "pinned" ->
                org.omarchy.music.ui.goTo(ui, "music", low)
            "added" -> { Music.musicSort = "added"; org.omarchy.music.ui.goTo(ui, "music", "all") }
            "new releases", "new-releases", "releases" -> org.omarchy.music.ui.goTo(ui, "music", "newreleases")
            "settings", "accounts" -> ui.openSettings()
            "eq", "equalizer" -> ui.sheet = "eq"
        }
    }
}
