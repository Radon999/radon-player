package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/** One Radon Player at a time, as the Python app: starting it again (or opening a song file with it, or a command
 *  like `"Radon Player Next.exe" next`) hands what was asked to the one already running, over a local socket that
 *  only this computer can reach, with a secret only this account can read.
 *
 *  Commands (the Python app's): see [HELP]; and song files to play. */
object Instance {
    private const val PORT = 8896
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
  next SONG...                      play right after the current song
  queue SONG...                     add to the queue, after the songs you queued before
  play | pause | play-pause | skip | prev    without a song: the transport
  lyrics [genius|synced]            show the lyrics of what's playing (as written, or timed)
  search WORDS...                   open on a search
  show [TAB]                        open on now, search, library, music, albums, eps, singles, artists, genres,
                                    playlists, folders, recent, top, skips, favorites, repeat, queue, lyrics or dj
  accounts                          Settings: sign-ins, folders, Last.fm, the tagger
  wrapped [YEAR|month|week|all]     your year (month, week or every year) in music
  source youtube|tidal|apple|spotify|mine   switch the library you browse and search
  tag [all]                         tag the saved songs not tagged yet (or all of them) now
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

    /** What a command line asks: the commands, else song files to play. show: bring the window up. */
    fun act(args: List<String>, show: () -> Unit, hide: () -> Unit, quit: () -> Unit) {
        val ui = org.omarchy.music.ui.Ui.current
        fun sheet(s: String) { show(); ui?.sheet = s }
        when {
            "--tidal-login" in args -> { sheet("tidal-login"); return }
            "--lastfm-login" in args -> { show(); ui?.openSettings("Last.fm & your stats"); return }
            "--spotify-login" in args -> { show(); ui?.openSettings("Accounts"); return }
        }
        val words = args.filter { !it.startsWith("--") }
        val files = words.mapNotNull { a -> Music.local.trackOf(a.removePrefix("file://")) }
        if (files.isNotEmpty()) { Music.playList(files); show(); return }
        val verb = words.firstOrNull()?.lowercase()
        val rest = words.drop(1).joinToString(" ").trim()
        when (verb) {
            null, "show", "open", "back" -> {
                if ("--tray" in args) return
                show()
                if (ui != null && rest.isNotEmpty()) when (rest.lowercase()) {
                    "now", "player", "nowplaying" -> { org.omarchy.music.ui.goTo(ui, "player"); ui.pane = "art" }
                    "lyrics" -> { org.omarchy.music.ui.goTo(ui, "player"); ui.pane = "lyrics" }
                    "queue" -> org.omarchy.music.ui.goTo(ui, "queue")
                    "search", "library", "albums", "eps", "singles", "artists", "genres", "folders", "flac24", "flac32", "visualizer" -> org.omarchy.music.ui.goTo(ui, rest.lowercase())
                    "music", "all", "mine" -> org.omarchy.music.ui.goTo(ui, "music", "all")
                    "playlists", "recent", "top", "skips", "favorites", "unfavorited", "repeat", "dj", "discovery", "newreleases", "dupes", "pinned" ->
                        org.omarchy.music.ui.goTo(ui, "music", rest.lowercase())
                    "added" -> { Music.musicSort = "added"; org.omarchy.music.ui.goTo(ui, "music", "all") }
                    "wrapped" -> ui.sheet = "wrapped"
                    "settings", "accounts" -> ui.openSettings()
                    "eq", "equalizer" -> ui.sheet = "eq"
                }
            }
            "play" -> if (rest.isEmpty()) { if (Music.paused) Music.togglePause() } else findSongs(rest) { Music.playList(it) }
            "next" -> if (rest.isEmpty()) Music.next() else findSongs(rest) { Music.enqueue(it, "next") }
            "queue", "add" -> if (rest.isNotEmpty()) findSongs(rest) { Music.enqueue(it, "queue"); Music.toast("Queued: ${it[0].title}") }
            "pause" -> if (!Music.paused) Music.togglePause()
            "play-pause", "toggle" -> Music.togglePause()
            "skip" -> Music.next()
            "prev", "previous" -> Music.previous()
            "lyrics" -> {
                show()
                if (rest == "genius" || rest == "synced") Music.setLyricsMode(rest)
                ui?.let { org.omarchy.music.ui.goTo(it, "player"); it.pane = "lyrics" }
            }
            "search" -> {
                show()
                ui?.let { u -> u.query = rest; if (rest.isNotEmpty()) org.omarchy.music.ui.runSearch(u) else org.omarchy.music.ui.goTo(u, "search") }
            }
            "accounts", "settings" -> { show(); ui?.openSettings("Accounts") }
            "wrapped" -> {
                val now = java.time.LocalDate.now()
                org.omarchy.music.ui.WrappedAsk.period = when {
                    rest.equals("month", true) -> Wrapped.Period.Month(java.time.YearMonth.from(now))
                    rest.equals("week", true) -> Wrapped.Period.Week(now.with(java.time.DayOfWeek.MONDAY))
                    rest.equals("all", true) -> Wrapped.Period.All
                    rest.toIntOrNull() != null -> Wrapped.Period.Year(rest.toInt())
                    else -> null
                }
                sheet("wrapped")
            }
            "source" -> {
                val key = when (rest.lowercase()) { "youtube", "ytm", "youtube-music" -> "ytm"; "tidal" -> "tidal"; "apple" -> "apple"
                    "spotify" -> "spotify"; "mine", "local", "my-music" -> "local"; else -> null }
                if (key == null) Music.toast("Sources: youtube, tidal, apple, spotify or mine")
                else if (key == "local") { Music.browseLocal = true; Music.toast("Browsing your own music") }
                else { Music.browseLocal = false; Music.setSource(key); Music.toast("Browsing ${SOURCES[key]}") }
            }
            "tag" -> { Tagger.start(full = rest.equals("all", true)); Music.toast("Tagging your saved songs…") }
            "hide" -> hide()
            "quit" -> quit()
        }
    }
}
