package org.omarchy.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.omarchy.music.DESKTOP_UA
import org.omarchy.music.Eq
import org.omarchy.music.LastFm
import org.omarchy.music.Music
import org.omarchy.music.Remote
import org.omarchy.music.Updater
import androidx.compose.runtime.LaunchedEffect
import org.omarchy.music.SOURCES
import org.omarchy.music.Services
import org.omarchy.music.Tidal
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun Sheets(ui: Ui) {
    when (ui.sheet) {
        "accounts" -> AccountsSheet(ui)
        "eq" -> EqSheet(ui)
        "tidal-login" -> WebLogin(Music.tidal.loginUrl(), { it.startsWith(Tidal.REDIRECT) }) { url ->
            ui.sheet = "accounts"; if (url != null) Music.tidalLoggedIn(url)
        }
        "ytm-login" -> WebLogin("https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=" +
            "https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F",
            { it.startsWith("https://music.youtube.com") && Services.ytmCookiesReady() }) { url ->
            ui.sheet = "accounts"; if (url != null) Services.ytmSignedIn()
        }
        "apple-login" -> WebLogin("https://music.apple.com/login", { Services.appleCookiesReady() }, desktop = true) { url ->
            ui.sheet = "accounts"; if (url != null) Services.appleSignedIn()
        }
        "spotify-login" -> WebLogin(Services.spotifyLoginUrl(), { it.startsWith(org.omarchy.music.Spotify.REDIRECT) }) { url ->
            ui.settingsOpen += "Accounts"; ui.sheet = "accounts"; if (url != null) Services.spotifyFinish(url)
        }
        "lastfm-login" -> WebLogin(LastFm.authUrl(), { LastFm.authDone(it) }) { url ->
            ui.sheet = "accounts"; LastFm.finishLogin(quiet = url == null)
        }
        "wrapped" -> WrappedScreen { ui.sheet = null }
    }
    ui.pickPlaylistFor?.let { tracks -> PlaylistPicker(ui, tracks) }
    ui.folderFor?.let { item -> FolderPicker(ui, item) }
    ui.editTagsFor?.let { tracks -> TagsDialog(ui, tracks) }
    ui.taggerFor?.let { tracks -> TaggerSheet(ui, tracks) }
    if (ui.tagScan) TagScanScreen(ui)
    ToolScreens(ui)
    if (ui.guide) GuideScreen(ui)
    if (ui.jointArtists) JointArtistsScreen(ui)
    if (ui.similarArtists) SimilarArtistsScreen(ui)
    if (ui.albumPlays) AlbumPlaysScreen(ui)
    if (ui.singleScan) SingleScanScreen(ui)
    if (ui.duplicates) DuplicatesScreen(ui)
    ui.collageFor?.let { kind -> CollageSheet(ui, kind) }
    ui.cardFor?.let { kind -> CardSheet(ui, kind) }
    DeleteDialog(ui)
    Music.importReport?.let { ImportReportDialog(it) }
    ui.coverFor?.let { albums -> CoverDialog(ui, albums) }
    ui.artistPictureFor?.let { name -> ArtistPictureDialog(ui, name) }
    ui.mergeArtist?.let { name -> MergeArtistDialog(ui, name) }
    ui.speedFor?.let { tracks -> if (tracks.isNotEmpty()) SpeedDialog(ui, tracks) else ui.speedFor = null }
    ui.volumeFor?.let { tracks -> if (tracks.isNotEmpty()) VolumeDialog(ui, tracks) else ui.volumeFor = null }
    ui.upgradeFor?.let { tracks -> UpgradeDialog(ui, tracks) }
    Music.upgradeKept?.let { kept -> UpgradeKeptDialog(kept) }
    Music.dupePrompt?.let { (new, dupes) -> DupeDownloadDialog(new, dupes) }
    Trasher()
    Exporter(ui)
    ui.askName?.let { (title, start, done) -> NameDialog(title, start, { ui.askName = null }) { ui.askName = null; done(it) } }
    ui.confirm?.let { (text, act) ->
        AlertDialog(onDismissRequest = { ui.confirm = null }, containerColor = C.light,
            text = { Text(text, style = T.body) },
            confirmButton = { TextButton({ ui.confirm = null; act() }) { Text("Yes", color = C.red) } },
            dismissButton = { TextButton({ ui.confirm = null }) { Text("Cancel", color = C.muted) } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountsSheet(ui: Ui) {
    Music.accountsVersion
    FullScreen("Settings", { ui.sheet = null }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            LaunchedEffect(Unit) { Updater.checkSoon() }
            val activity = currentActivity()   // the sheet's context wraps the activity's
            Group("General", ui) {
            val v = Updater.latest
            if (v != null) Line("Update", when (Updater.state) {
                "" -> "A newer version, Radon Player $v, is out (you have ${Updater.version}). Tap Update to get it"
                "working" -> "Downloading Radon Player $v…"
                "confirm" -> "Radon Player $v is downloaded. Tap Install to put it in place of this version"
                else -> Updater.state
            }, false) {
                when (Updater.state) {
                    "working" -> Unit
                    "confirm" -> Pill("Install") { Updater.askAgain(activity) }
                    else -> Pill("Update") { Updater.install() }
                }
            } else Line("Version", if (Updater.checking) "${Updater.version} · checking for a newer version…"
                else Updater.checkError.ifEmpty { "You have ${Updater.version}, the latest version. Tap Check to look again for a newer one" }.let { if (it.startsWith("couldn't")) "${Updater.version} · $it" else it }, false) {
                if (!Updater.checking) Pill("Check", quiet = true) { Updater.check(quiet = false) }
            }
            // What went wrong lately (the app closing, or errors it lived through): copy it to send it on.
            val problems = org.omarchy.music.Crashes.last
            if (problems.isNotEmpty()) {
                Line("Problems", problems.lineSequence().first().split(" · ").let { "${it.last()} · ${it.first()}" }, false) {
                    Pill("Copy", quiet = true) {
                        copyText("Radon Player problems", problems)
                        Music.toast("Copied what went wrong")
                    }
                    Pill("Clear", quiet = true) { org.omarchy.music.Crashes.clear() }
                }
            }
            if (onPhone) NotificationsLine(activity)
            if (org.omarchy.music.Startup.supported) StartupLine()
            }
            Group("Playback", ui) {
            PlaybackLines(ui)
            }
            Group("Backup", ui) {
            BackupLines(activity)
            }
            Group("Play on", ui) {
            if (org.omarchy.music.RemoteHost.supported) PhoneRemoteLines()
            // ---- play on ----
            else Line("Play on", if (Remote.active) "Music plays on the desktop (${Remote.name}), and this phone is its remote" +
                if (!Remote.reachable) " · it can't be reached right now: check the PC and the app are on, on the same network" else ""
                else "Music plays here, on this $DEVICE. Once you've paired with the desktop app (below), switch this on to play " +
                    "on the PC instead, with this $DEVICE as its remote", Remote.active) {
                if (Remote.paired) Switch(Remote.on, { Remote.setOn(it) }, colors = swColors())
            }
            if (org.omarchy.music.RemoteHost.supported) Unit
            else if (!Remote.paired) PairDesktop()
            else Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paired with ${Remote.host}", style = T.sub, modifier = Modifier.weight(1f))
                Pill("Forget", quiet = true) { Remote.forget() }
            }
            if (org.omarchy.music.Discord.supported) DiscordLine()
            }
            Group("Accounts", ui) {
            // ---- services ----
            for (key in listOf("ytm", "apple")) {
                val src = Music.sources[key]
                if (src?.signedIn == true) Line(SOURCES[key]!!, "Signed in: your library and playlists from here are in the app, and their songs play from Tidal in " +
                    "FLAC where it has them. With Tidal connected, Sync downloads every song in your favorites and playlists in FLAC", true) {
                    SyncSwitch(key); Spacer(Modifier.width(6.dp)); Pill("Sign out", quiet = true) { Services.signOut(key) }
                } else Line(SOURCES[key]!!, "Sign in with your account to browse your own library, favorites and playlists from this service " +
                    "inside the app. Their songs play from Tidal in FLAC where it has them, so connect Tidal too", false) {
                    Pill("Sign in") { ui.sheet = "$key-login" }
                }
            }
            if (Music.tidal.connected) {
                Line("Tidal", "Connected. Songs play in FLAC (lossless), hi-res where Tidal has it, and fall back to " +
                    "lossy only where Tidal has no FLAC. Turn on Sync to download every song in your Tidal favorites and playlists", true) {
                    SyncSwitch("tidal"); Spacer(Modifier.width(6.dp)); Pill("Disconnect", quiet = true) { Music.tidalDisconnect() }
                }
                Line("Keep", "On: every song you play is also saved in FLAC to your downloads folder, so it's there " +
                    "offline next time. Off: songs stream from Tidal and are saved only when you pick Download. Songs Tidal " +
                    "has no FLAC of are never saved, and songs you already have follow the Copies setting",
                    Music.keepPlayed) { Switch(Music.keepPlayed, { Music.setKeepPlayed(it) }, colors = swColors()) }
            } else Line("Tidal", "Connect your Tidal account (it needs a paid Tidal subscription) and songs from every " +
                "service play in FLAC, hi-res where Tidal has it. Tidal's own favorites, playlists, search and radio show up too, and " +
                "downloads and Upgrade come from it", false) {
                Pill("Connect") { ui.sheet = "tidal-login" }
            }
            SpotifyLine(ui)
            }
            Group("Your music", ui) {
            // ---- your music ----
            val n = Music.local.files.size
            Line("Folders", if (Music.local.scanning) "Reading your music folders for songs…" else "$n song${if (n != 1) "s" else ""} found in the folders below, shown in My music. " +
                "Add the folders where your music files are kept and the app reads them in; Remove only stops reading a folder, it deletes nothing", n > 0) {
                FolderPicker()
            }
            Music.folders.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f, style = T.sub, modifier = Modifier.weight(1f))
                    Pill("Remove", quiet = true) { Music.removeFolder(f) }
                }
            }
            ToolLines(ui)
            Line("Lyrics", "Lyrics show on Now playing in place of the cover when you tap the lyrics button. Genius shows " +
                "the words as written, with Genius's notes on the marked lines. Synced shows lines timed to the music that light up as " +
                "they're sung; tap a line to jump to that point", false) {
                Chip("Genius", Music.lyricsMode == "genius") { Music.setLyricsMode("genius") }
                Spacer(Modifier.width(4.dp))
                Chip("Synced", Music.lyricsMode == "synced") { Music.setLyricsMode("synced") }
            }
            Line("Lyrics into files", if (!Music.lyricsToFiles) "Off: the lyrics the app finds are kept in the app only (they still work offline), and your " +
                "files are left as they are. Switch on to write them into the files too"
                else if (Music.canWriteOwnFiles()) "Once a song has finished playing, the lyrics found for it are written into its " +
                "file's tags (FLAC and MP3), so they go with the song to other players and devices"
                else "For now, lyrics go only into the songs the app downloaded. To write them into your own " +
                "music files too, the app needs All files access: tap Allow", Music.lyricsToFiles) {
                if (Music.lyricsToFiles && !Music.canWriteOwnFiles()) {
                    Pill("Allow", quiet = true) { openSystemSettings("allfiles") }
                    Spacer(Modifier.width(6.dp))
                }
                Switch(Music.lyricsToFiles, { Music.setLyricsToFiles(it) }, colors = swColors())
            }
            run {
                val tg = org.omarchy.music.Tagger
                val untagged = remember(Music.savedVersion, tg.busy) { tg.untagged() }
                val saved = remember(Music.savedVersion) { Music.lib.items.values.count { it.format == "flac" } }
                Line("Tagger", tg.busy ?: if (untagged.isNotEmpty()) "${untagged.size} downloads are missing extra tags: genre, composer, label, " +
                    "lyrics and Genius's notes. Tag now… fills them in after showing you a preview of the changes" else if (saved > 0) "All $saved downloads have their extra tags. Scan… checks them " +
                    "against Deezer's catalogue, one album at a time, for missing or wrong tags and missing or small covers, and you " +
                    "choose Replace or Keep for each" + (if (org.omarchy.music.TagScan.Done.count > 0) ". Scan… looks only at songs that are new or changed since " +
                    "the last scan (${"%,d".format(org.omarchy.music.TagScan.Done.count)} checked before); Scan all again checks every one" else "")
                    else "Checks the songs you've downloaded against Deezer's catalogue, one album at a time, for " +
                    "missing or wrong tags and missing or small covers. You choose Replace or Keep for each change",
                    tg.busy == null && untagged.isEmpty()) {
                    if (tg.busy == null) {
                        if (untagged.isNotEmpty()) Pill("Tag now…") { ui.tagPreview = untagged to false }
                        Pill("Scan…", quiet = untagged.isNotEmpty()) { ui.tagScan = true }
                        // The scan skips the songs it checked before that haven't changed since: this checks every one again
                        if (org.omarchy.music.TagScan.Done.count > 0) Pill("Scan all again", quiet = true) { ui.tagScanFresh = true; ui.tagScan = true }
                    }
                }
            }
            Line("Queues", "On: each folder, album, artist and playlist you play from gets its own queue, kept where " +
                "you left it, so you can go back to any of them later, rename them or reorder them. Off: there's one queue, and " +
                "playing something new replaces it",
                Music.multiQueue) { Switch(Music.multiQueue, { Music.setMultiQueue(it) }, colors = swColors()) }
            Line("Look", "How the app is coloured: Light, Dark, or Now playing, which colours every screen to match " +
                "the cover of the song that's playing, the way the Now playing screen is", false) {
                Box {
                    var open by remember { mutableStateOf(false) }
                    Pill("${Looks.ALL.firstOrNull { it.first == Music.look }?.second ?: "Light"} ▾", quiet = true) { open = true }
                    androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = C.light) {
                        Looks.ALL.forEach { (k, name) ->
                            androidx.compose.material3.DropdownMenuItem({ Text(name, style = T.body) }, { open = false; Music.chooseLook(k) })
                        }
                    }
                }
            }
            Line("Fallback for Now playing look", "The Now playing look takes its colours from the song that's playing. " +
                "This picks the look it uses instead while nothing is playing, such as when you first open the app", false) {
                Box {
                    var open by remember { mutableStateOf(false) }
                    Pill("${Looks.FALLBACK.firstOrNull { it.first == Music.npFallback }?.second ?: "Dark"} ▾", quiet = true) { open = true }
                    androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = C.light) {
                        Looks.FALLBACK.forEach { (k, name) ->
                            androidx.compose.material3.DropdownMenuItem({ Text(name, style = T.body) }, { open = false; Music.chooseNpFallback(k) })
                        }
                    }
                }
            }
            Line("Downloads", "Songs you download are saved to ${Music.downloadFolder()} in FLAC with full tags, sorted " +
                "into Artist / Albums, EPs or Singles / Album folders. Change picks one of your music folders or a new folder in Music/", true) {
                Box {
                    var open by remember { mutableStateOf(false) }
                    Pill("Change", quiet = true) { open = true }
                    androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = C.light) {
                        Music.folders.forEach { f ->
                            androidx.compose.material3.DropdownMenuItem({ Text(f, style = T.body) }, { open = false; Music.chooseDownloadDir(f) })
                        }
                        androidx.compose.material3.DropdownMenuItem({ Text("A new folder in Music/…", style = T.body) }, {
                            open = false
                            ui.askName = Triple("A folder in Music/ for downloads", "") { n ->
                                Music.chooseDownloadDir("Music/" + org.omarchy.music.pathPart(n, "Downloads") + "/")
                            }
                        })
                    }
                }
            }
            Line("Copies", "What happens when you download a song you already have. Ask shows you the copies you have " +
                "and lets you choose each time; Keep both downloads it and keeps yours too; Replace downloads it and puts your old copy " +
                "away (your own files go to $TRASH)", false) {}
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("ask" to "Ask", "keep" to "Keep both", "replace" to "Replace").forEach { (k, l) ->
                    Chip(l, Music.dupeAction == k) { Music.chooseDupeAction(k) }
                }
            }
            if (Music.local.files.isNotEmpty() || Music.upgrading != null) Line("Upgrade", Music.upgrading
                ?: "Looks your songs up on Tidal and swaps them for its FLAC where Tidal's is " +
                "better: MP3s and other lossy files are replaced, and 16-bit ones only when Tidal has them in 24-bit hi-res. Playlists " +
                "and the queue keep working, and the old files go to $TRASH so you can get them back",
                Music.upgrading != null) {
                if (Music.upgrading != null) Pill("Stop", quiet = true) { Music.upgradeStop = true }
                else Pill("Upgrade…") { ui.upgradeFor = emptyList() }
            }
            }
            Group("Sound quality", ui) {
            SoundQuality(ui)
            }
            Group("Last.fm & your stats", ui) {
            LastFmLine(ui)
            SpotifyHistoryLine(ui)
            AppleHistoryLine()
            DeezerHistoryLine()
            run {
                val undecided = remember(Music.artistPicks, org.omarchy.music.Stats.songs) { jointCreditsNow().count { it.third == null } }
                Line("Similar artists", "Finds artists that look like the same one written two ways (cavetown offline and " +
                    "Cavetown), which splits their plays in your stats. You choose to merge them, their songs' plays and all, or keep them apart", false) { Pill("Scan…", quiet = true) { ui.similarArtists = true } }
                Line("Duplicates in Most played", "Finds songs counted twice in your stats under different names (\"N.P.D\" and " +
                    "\"NPD\"), and versions of one song (acoustic, live, remix). You pick which to merge into one, their plays added " +
                    "together, and which to leave alone", false) { Pill("Scan…", quiet = true) { ui.duplicates = true } }
                Line("Plays on singles and albums", "When a song's plays are split between its single, an EP and its album (in your Last.fm " +
                    "scrobbles and Spotify, Apple Music or Deezer imports), this moves them all onto the album, its deluxe or extended " +
                    "edition when there is one. You check each one, and can pick another album or leave it", false) {
                    Pill("Scan…", quiet = true) { ui.albumPlays = true } }
                Line("Joint artists", "A duet's plays go to its main artist (Quinn XCII & Jeremy Zucker counts for Quinn XCII), " +
                    "so your top artists aren't split up. " +
                    if (undecided > 0) "$undecided pairs aren't clear: pick whose plays each one's are" else "Choose… lets you change whose plays a pair's are",
                    undecided == 0) { Pill("Choose…", quiet = undecided == 0) { ui.jointArtists = true } }
            }
            }
            Group("More", ui) {
            SettingsLine()
            }
        }
    }
}

@Composable
private fun PairDesktop() {
    var addr by remember { mutableStateOf(Remote.found.firstOrNull()?.second ?: "") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Text("To control the desktop app from here, switch on Phone remote in its Settings, then enter the code it shows.",
        style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
    Remote.found.forEach { (name, a) ->
        Text("◉ $name  ($a)", style = T.body.copy(color = if (a == addr) C.accent else C.fg),
            modifier = Modifier.fillMaxWidth().clickable { addr = a }.padding(vertical = 6.dp))
    }
    Field(addr, "Desktop address, like 192.168.1.20") { addr = it }
    Field(code, "Pairing code") { code = it }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.End) {
        Pill(if (busy) "Pairing…" else "Pair") {
            if (addr.isBlank() || code.isBlank() || busy) return@Pill
            busy = true
            Remote.pair(addr, code) { err -> busy = false; Music.toast(if (err == null) "Paired: playing on the desktop" else "Couldn't pair: ${err.message?.take(80)}") }
        }
    }
}

@Composable
private fun SpotifyLine(ui: Ui) {
    val sp = Music.sources["spotify"] as? org.omarchy.music.Spotify
    if (sp?.signedIn == true) {
        Line("Spotify", if (sp.user.isNotEmpty()) "Connected as ${sp.user}: your liked songs, playlists and artists' albums are in the app, " +
            "playing from Tidal in FLAC" else "Connected: your liked songs, playlists and artists' albums are in the app, playing from Tidal in FLAC", true) {
            SyncSwitch("spotify"); Spacer(Modifier.width(6.dp)); Pill("Disconnect", quiet = true) { Services.spotifyDisconnect() }
        }
        return
    }
    var cid by remember { mutableStateOf(sp?.clientId ?: "") }
    Line("Spotify", "To connect, Spotify needs a free developer app of your own (the account that makes it needs " +
        "Premium). Tap Make an app, give it any name, tick Web API and add the redirect URI below, then paste its Client ID here " +
        "and tap Connect. You get your liked songs, playlists, search and artists' albums, playing from Tidal in FLAC. Redirect URI:", false) {}
    Text(org.omarchy.music.Spotify.REDIRECT, style = T.sub.copy(color = C.fg), modifier = Modifier.padding(bottom = 4.dp))
    Field(cid, "Client ID") { cid = it }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Pill("Make an app", quiet = true) { Services.open("https://developer.spotify.com/dashboard/create") }
        Pill("Connect") { if (Services.spotifySetClient(cid)) ui.sheet = "spotify-login" }
    }
}

@Composable
private fun LastFmLine(ui: Ui) {
    if (LastFm.connected) {
        Line("Last.fm", "Connected as ${LastFm.user}: every song you play is scrobbled (logged) to your Last.fm, even " +
            "offline ones, which send later. All your scrobbles from all time are the base of your stats and Wrapped", true) { Pill("Disconnect", quiet = true) { LastFm.disconnect() } }
        ActionRow("✦  Your ${org.omarchy.music.Wrapped.year()} Wrapped  ›") { ui.sheet = "wrapped" }
        return
    }
    var key by remember { mutableStateOf(LastFm.key) }
    var secret by remember { mutableStateOf(LastFm.secret) }
    Line("Last.fm", "Connect Last.fm and every song you play is scrobbled (logged) to your account, and all your " +
        "scrobbles from all time become the base of your stats and Wrapped. Last.fm needs a free API account of yours: tap Make an " +
        "API account, then paste its key and shared secret below and tap Connect. Without it, the plays counted in the app are used.", false) {}
    Field(key, "API key") { key = it }
    Field(secret, "Shared secret") { secret = it }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Pill("Make an API account", quiet = true) { Services.open("https://www.last.fm/api/account/create") }
        Pill("Connect") { LastFm.begin(key, secret) { ok -> if (ok) ui.sheet = "lastfm-login" } }
    }
}

/** Your Spotify plays for Wrapped: the zip from Spotify's privacy page, and your last plays
 *  through the connection, picked up when the app starts. */
@Composable
private fun SpotifyHistoryLine(ui: Ui) {
    val h = org.omarchy.music.SpotifyHistory
    val sp = Music.sources["spotify"] as? org.omarchy.music.Spotify
    var asking by remember { mutableStateOf(false) }
    val pick = rememberOpenFile { uri ->
        if (uri != null) Music.bg({ h.importFile(Music.ctx, uri) }) { n, err ->
            Music.toast(if (err != null) "Couldn't import it: ${err.message?.take(140)}"
                else "Added ${"%,d".format(n ?: 0)} Spotify plays to your Wrapped" + if (n == 0) " (you had them all)" else "")
        }
    }
    val state = h.busy ?: if (h.count > 0) "${"%,d".format(h.count)} Spotify plays added alongside your Last.fm scrobbles in your stats " +
        "and Wrapped, each play counted once. Importing the zip again only adds plays you didn't have yet"
        else "Bring back years of Spotify listening: its plays are added alongside your Last.fm scrobbles in " +
            "your stats and Wrapped, each play counted once. The zip is the data download Spotify gives you: tap Get the zip, ask " +
            "for your data on Spotify's privacy page (the extended streaming history goes furthest back), then Import zip when it " +
            "comes. A play counts from 30 seconds, as Spotify counts streams; podcasts and videos are left out" +
            if (sp?.signedIn == true) ". As Spotify is connected, your latest plays also come in each time the app starts " +
                "(Spotify only keeps the last 50), and Sync fetches them now"
            else ". Connect Spotify under Accounts and your latest plays come in on their own too"
    Line("History", state, h.count > 0) {}
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Pill("Get the zip", quiet = true) { Services.open("https://www.spotify.com/account/privacy/") }
        if (sp?.signedIn == true) Pill("Sync", quiet = true) {
            Music.bg({ h.sync() }) { n, err ->
                Music.toast(if (err != null) "Couldn't read your Spotify plays: ${err.message?.take(100)}"
                    else "${"%,d".format(n ?: 0)} new Spotify play${if (n == 1) "" else "s"}")
            }
        }
        if (h.count > 0) Pill("Clear", quiet = true) { asking = true }
        Pill("Import zip") { if (h.busy == null) pick.launch(arrayOf("application/zip", "application/json", "application/octet-stream")) }
    }
    if (h.count > 0 && !LastFm.connected) ActionRow("✦  Your ${org.omarchy.music.Wrapped.year()} Wrapped  ›") { ui.sheet = "wrapped" }
    if (asking) AlertDialog(onDismissRequest = { asking = false }, containerColor = C.light,
        title = { Text("Clear your Spotify history?", style = T.title) },
        text = { Text("Wrapped goes back to your Last.fm scrobbles. Plays from the zip come back if you import it again; " +
            "the last plays picked up from Spotify don't.", style = T.sub) },
        confirmButton = { TextButton({ asking = false; Music.bg({ h.clear() }) }) { Text("Clear", color = C.accent) } },
        dismissButton = { TextButton({ asking = false }) { Text("Cancel", color = C.muted) } })
}

/** Your Apple Music plays for Most played, Artists and Wrapped: the zip from Apple's privacy page,
 *  or a library exported from the Music app on a computer. */
@Composable
private fun AppleHistoryLine() {
    val h = org.omarchy.music.AppleHistory
    var asking by remember { mutableStateOf(false) }
    val pick = rememberOpenFile { uri ->
        if (uri != null) Music.bg({ h.importFile(Music.ctx, uri) }) { n, err ->
            Music.toast(if (err != null) "Couldn't import it: ${err.message?.take(160)}"
                else "Added ${"%,d".format(n ?: 0)} Apple Music plays" + if (n == 0) " (you had them all)" else "")
            if (err == null) org.omarchy.music.Stats.refresh(force = true)
        }
    }
    val state = h.busy ?: if (h.count > 0) "${"%,d".format(h.count)} Apple Music plays added alongside your Last.fm scrobbles in " +
        "Most played, Artists and Wrapped, each play counted once. Importing again only adds plays you didn't have yet"
        else "Bring your Apple Music listening into Most played, Artists and Wrapped, added alongside your " +
            "Last.fm scrobbles with each play counted once. Tap Get the zip and ask Apple's privacy page for your data (Get a copy " +
            "of your data › Apple Media Services), then Import the zip when it comes. Or import a library exported from the Music " +
            "app on a computer (File › Library › Export Library…)"
    Line("Apple Music history", state, h.count > 0) {}
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Pill("Get the zip", quiet = true) { Services.open("https://privacy.apple.com/") }
        if (h.count > 0) Pill("Clear", quiet = true) { asking = true }
        Pill("Import") { if (h.busy == null) pick.launch(arrayOf("application/zip", "text/csv", "text/comma-separated-values",
            "text/xml", "application/xml", "application/octet-stream")) }
    }
    if (asking) AlertDialog(onDismissRequest = { asking = false }, containerColor = C.light,
        title = { Text("Clear your Apple Music history?", style = T.title) },
        text = { Text("Its plays leave Most played, Artists and Wrapped. They come back if you import it again.", style = T.sub) },
        confirmButton = { TextButton({ asking = false; Music.bg({ h.clear(); org.omarchy.music.Stats.refresh(force = true) }) }) { Text("Clear", color = C.accent) } },
        dismissButton = { TextButton({ asking = false }) { Text("Cancel", color = C.muted) } })
}

/** Your Deezer plays for Most played, Artists and Wrapped: the .xlsx from Deezer's privacy settings (My personal data). */
@Composable
private fun DeezerHistoryLine() {
    val h = org.omarchy.music.DeezerHistory
    var asking by remember { mutableStateOf(false) }
    val pick = rememberOpenFile { uri ->
        if (uri != null) Music.bg({ h.importFile(Music.ctx, uri) }) { n, err ->
            Music.toast(if (err != null) "Couldn't import it: ${err.message?.take(160)}"
                else "Added ${"%,d".format(n ?: 0)} Deezer plays" + if (n == 0) " (you had them all)" else "")
            if (err == null) org.omarchy.music.Stats.refresh(force = true)
        }
    }
    val state = h.busy ?: if (h.count > 0) "${"%,d".format(h.count)} Deezer plays added alongside your Last.fm scrobbles in Most " +
        "played, Artists and Wrapped, each play counted once. Importing again only adds plays you didn't have yet"
        else "Bring your Deezer listening into Most played, Artists and Wrapped, added alongside your " +
            "Last.fm scrobbles with each play counted once. Tap Get the file and download your data from Deezer's privacy settings " +
            "(My account › Privacy settings › My personal data), then Import the .xlsx spreadsheet with your listening history"
    Line("Deezer history", state, h.count > 0) {}
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Pill("Get the file", quiet = true) { Services.open("https://www.deezer.com/account/privacy") }
        if (h.count > 0) Pill("Clear", quiet = true) { asking = true }
        Pill("Import") { if (h.busy == null) pick.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/zip", "application/octet-stream")) }
    }
    if (asking) AlertDialog(onDismissRequest = { asking = false }, containerColor = C.light,
        title = { Text("Clear your Deezer history?", style = T.title) },
        text = { Text("Its plays leave Most played, Artists and Wrapped. They come back if you import it again.", style = T.sub) },
        confirmButton = { TextButton({ asking = false; Music.bg({ h.clear(); org.omarchy.music.Stats.refresh(force = true) }) }) { Text("Clear", color = C.accent) } },
        dismissButton = { TextButton({ asking = false }) { Text("Cancel", color = C.muted) } })
}

/** Loudness check (the PC: ffmpeg looks at each song once). */
@Composable
private fun LoudnessCheckLine(ui: Ui) {
    val P = org.omarchy.music.Prefs
    val q = org.omarchy.music.QualityCheck
    q.version
    val on = P.on(P.LOUDNESS_CHECK)
    val flagged = if (on) q.flagged() else emptyList()
    val clipped = flagged.count { it.second.clipped > 0 }; val fake = flagged.count { it.second.fake.isNotEmpty() }
    Line("Loudness check", when {
        q.busy != null -> "Checking your songs · ${q.busy}"
        on && q.checked > 0 -> "${"%,d".format(q.checked)} songs checked: $clipped clip (distort from being too loud) and $fake " +
            "aren't the hi-res they claim to be. Show… lists the worst first"
        else -> "Checks each of your songs once, quietly in the background, for clipping (distortion from " +
            "being mastered too loud) and for files that aren't the hi-res they claim (upsampled from lower quality, or 16-bit " +
            "inside a 24-bit file). Show… then lists the worst first, so you know which copies are worth replacing" + org.omarchy.music.AudioTools.WHEN
    }, on) {
        if (flagged.isNotEmpty()) { Pill("Show…", quiet = true) { ui.tool = "quality" }; Spacer(Modifier.width(6.dp)) }
        Switch(on, { Music.setOpt(P.LOUDNESS_CHECK, it); if (it) q.scan() }, colors = swColors())
    }
}

/** Settings › Your music: the tools that fix your songs' files (on the phone, the app's own downloads). */
@Composable
private fun ToolLines(ui: Ui) {
    Line("Fix albums and artists", "Finds albums split up by featured artists (“Quinn XCII & Jeremy Zucker”), albums sitting " +
        "beside their own deluxe, extended or anniversary edition, and singles of songs that are on an album you have. They're " +
        "fixed in the files' tags so they group properly (song titles are never changed)" + (if (org.omarchy.music.FileTags.canMove) ", and the files can then be moved to the right folders if you like" else "") +
        ". You check each change before it's made" + (if (onPhone) ". On the phone it works on the songs the app downloaded" else ""), false) {
        Pill("Scan…", quiet = true) { ui.tool = "fixup" }
    }
    val n = org.omarchy.music.MusicTools.skip.size
    Line("Folders to leave alone", "Folders that no scan here ever touches or even looks in (Fix albums and artists, the tag " +
        "scanner, Empty folders, Live in song names): their songs are never retagged or moved, and no songs are moved into them. " +
        "Handy for unreleased songs, sessions and anything you've arranged by hand. " +
        if (n > 0) "$n folder${if (n != 1) "s" else ""} left alone now" else "None chosen yet", false) {
        Pill("Choose…", quiet = true) { ui.tool = "skip" }
    }
    if (org.omarchy.music.FileTags.canListFolders) Line("Empty folders", "Finds folders in your music folders that have no songs left " +
        "in them, only pictures or lyrics files, often left behind after songs are moved. You see each one, its size and what's " +
        "in it, and pick which to delete", false) {
        Pill("Clean up…", quiet = true) { ui.tool = "empty" }
    }
    Line("Live in song names", "Finds songs named as live recordings (“Song (Live)”, “Song - Live at…”) that you'd rather " +
        "have under their plain name. It takes the live part off the name and keeps the rest, and you check each one first", false) {
        Pill("Scan…", quiet = true) { ui.tool = "live" }
    }
}

/** Start with Windows (the PC): in the tray when you sign in, ready for the media keys and the phone. */
@Composable
private fun StartupLine() {
    var on by remember { mutableStateOf(org.omarchy.music.Startup.isOn()) }
    Line("Start with Windows", if (on) "On: Radon Player starts quietly in the system tray each time you sign in to " +
        "Windows, so the media keys and the phone remote work straight away. Click its tray icon to open the window"
        else "Starts Radon Player in the system tray (by the clock) as soon as you sign in to Windows, " +
        "without opening its window, ready for your keyboard's media keys and the phone remote", on) {
        Switch(on, { want -> if (org.omarchy.music.Startup.set(want)) on = org.omarchy.music.Startup.isOn()
            else Music.toast("Couldn't: only the installed app can start with Windows") }, colors = swColors())
    }
}

/** Discord (the PC): what you play on your Discord profile. */
@Composable
private fun DiscordLine() {
    val d = org.omarchy.music.Discord
    Line("Discord", when {
        !d.on -> "Shows the song you're playing on your Discord profile, as Spotify does, and clears it " +
            "when you pause. It works through the Discord app on this PC and sends nothing anywhere else"
        d.status == "on" -> "Showing what you play on your Discord profile; it clears while you're paused"
        d.status.isEmpty() || d.status == "Discord isn't open" -> "On, but Discord isn't open: open the Discord app on this PC and it shows up"
        else -> "On · ${d.status}"
    }, d.on && d.status == "on") {
        Switch(d.on, { d.setOn(it) }, colors = swColors())
    }
}

/** The PC's Phone remote: the phone app plays music here, pairs with the code shown, finds the PC by itself. */
@Composable
private fun PhoneRemoteLines() {
    val h = org.omarchy.music.RemoteHost
    Line("Phone remote", if (h.running) "On: in the phone app, go to Settings › Play on, pick this PC and enter the code " +
        "${h.code}. The phone can then play, queue and skip music here, set the volume, run the DJ and show what's playing. " +
        "Songs stored on the phone stream to this PC over your home network"
        else "Lets the Radon Player phone app control music on this PC from across the room: play, queue, " +
        "skip, the volume and the DJ. Switch on to get a pairing code; the phone finds this PC on your home network by itself", h.running) {
        Switch(h.running, { h.setOn(it) }, colors = swColors())
    }
    if (h.running) Text(h.code, style = T.title.copy(fontSize = 28.sp, color = C.accent), modifier = Modifier.padding(bottom = 6.dp))
    for ((tok, name) in h.phones) Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Paired: $name", style = T.sub, modifier = Modifier.weight(1f))
        Pill("Forget", quiet = true) { h.forget(tok) }
    }
}

/** Back up… / Restore…: the app's own files (playlists, favorites, plays, settings, imports) in a zip of your choosing. */
@Composable
private fun BackupLines(activity: Any?) {
    val ctx = Music.ctx
    var asking by remember { mutableStateOf<String?>(null) }
    val save = rememberSaveFile("application/zip") { uri ->
        if (uri != null) Music.bg({ org.omarchy.music.Backup.make(ctx, uri) }) { n, err ->
            Music.toast(if (err != null) "Couldn't back up: ${err.message?.take(100)}" else "Backed up ($n files)")
        }
    }
    val open = rememberOpenFile { uri -> asking = uri }
    Line("Back up and restore", "Saves your playlists, favorites, plays, queues, settings, equalizer and imported histories " +
        "in one file, for moving to a new ${DEVICE} or after reinstalling. Your songs and sign-ins aren't included. Restoring " +
        "keeps what's already here alongside the backup, just in case", false) {}
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Pill("Restore…", quiet = true) { open.launch(arrayOf("application/zip", "application/octet-stream")) }
        Pill("Back up…") { save.launch("Radon Player backup ${java.time.LocalDate.now()}.zip") }
    }
    asking?.let { uri ->
        AlertDialog(onDismissRequest = { asking = null }, containerColor = C.light,
            title = { Text("Restore this backup?", style = T.title) },
            text = { Text("Your playlists, favorites, plays, settings and imported histories become the backup's (what's here now is kept " +
                "beside them, in case). The app closes after: open it again.", style = T.sub) },
            confirmButton = { TextButton({
                asking = null
                Music.restoring = true
                Music.bg({ org.omarchy.music.Backup.restore(ctx, uri) }) { names, err ->
                    if (err != null) { Music.restoring = false; Music.toast("Couldn't restore: ${err.message?.take(120)}") }
                    else { Music.toast("Restored ${names?.size ?: 0} files: open the app again"); Music.scope.launch {
                        kotlinx.coroutines.delay(1500); org.omarchy.music.Backup.close(activity) } }
                }
            }) { Text("Restore", color = C.red) } },
            dismissButton = { TextButton({ asking = null }) { Text("Cancel", color = C.muted) } })
    }
}

/** Notifications: the player in the notification shade needs them. On: Android's question (or its settings page,
 *  once it won't ask again); off: its settings page (only Android can turn them off). */
@Composable
private fun NotificationsLine(activity: Any?) {
    Music.resumed
    val on = Music.notificationsOn()
    val ask = rememberPermission { ok ->
        Music.resumed++
        if (!ok) openSystemSettings("notifications")
    }
    Line("Notifications", if (on) "On: the player sits in your notifications and on the lock screen, with its " +
        "controls and shuffle and repeat buttons, so you can control music without opening the app"
        else "Off: the player isn't in your notifications or on the lock screen. Switch on and " +
        "Android asks you to allow them (only Android's own settings can turn them off again)", on) {
        Switch(on, { want ->
            if (want && org.omarchy.music.sdkAtLeast(33) && activity != null &&
                (shouldExplain(activity, Perm.NOTIFY) || !org.omarchy.music.Prefs.on("notif_asked"))) {
                org.omarchy.music.Prefs.set("notif_asked", true)
                ask.launch(Perm.NOTIFY)
            } else openSystemSettings("notifications")
        }, colors = swColors())
    }
}

/** 2.0's playback switches: the lock screen player, carrying on across the PC and the phone, auto mono, smart shuffle. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackLines(ui: Ui) {
    val ctx = Music.ctx
    val P = org.omarchy.music.Prefs
    Music.resumed
    val lock = P.on(P.LOCK_SCREEN)
    val allowed = org.omarchy.music.LockScreen.allowed(ctx)
    if (onPhone) Line("Lock screen player", when {
        lock && !allowed -> "Before this can work, Android needs you to allow the app full-screen notifications: tap Allow"
        lock -> "On: when the screen turns on while music plays, the full Now playing appears over the lock " +
            "screen, with the queue and lyrics. Tap ⌄ to go on to the normal lock screen"
        else -> "Shows the full Now playing, with the queue and lyrics, over the lock screen whenever the " +
            "screen turns on while music plays, so you can see and control it without unlocking"
    }, lock && allowed) {
        if (lock && !allowed) { Pill("Allow", quiet = true) { org.omarchy.music.LockScreen.askAllowed(ctx) }; Spacer(Modifier.width(6.dp)) }
        Switch(lock, { P.set(P.LOCK_SCREEN, it); if (it && !allowed) org.omarchy.music.LockScreen.askAllowed(ctx) }, colors = swColors())
    }
    Line("Continue where you left off", "Stop on the PC and open the phone, or the other way round, and the app offers to carry on " +
        "with the same song at the same spot. It needs the phone paired with the PC (Play on) and both on the same home network", P.on(P.CONTINUE)) {
        Switch(P.on(P.CONTINUE), { P.set(P.CONTINUE, it) }, colors = swColors())
    }
    if (onPhone) AutoMonoLines()
    if (org.omarchy.music.AudioTools.supported) LoudnessCheckLine(ui)
    Line("Smart shuffle", "On: shuffle favors songs you love (ones you play a lot) that you haven't heard in a " +
        "while, so they come up more often. Off: shuffle is pure chance, every song as likely as the next",
        P.on(P.SMART_SHUFFLE)) {
        Switch(P.on(P.SMART_SHUFFLE), { P.set(P.SMART_SHUFFLE, it) }, colors = swColors())
    }
}

/** Auto mono (the phone's: it goes by the Bluetooth earbuds connected to it). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoMonoLines() {
    val P = org.omarchy.music.Prefs
    val auto = P.on(P.AUTO_MONO)
    val picked = P.text(P.AUTO_MONO_DEVICE)
    Line("Auto mono", when {
        !auto -> "Turns mono on by itself (both sides of the music in each ear, for when you only have one " +
            "earbud in) while the Bluetooth earbuds you pick are connected, and off again when they disconnect. Now playing's Mono " +
            "button still works by hand"
        picked.isEmpty() -> "Connect your Bluetooth earbuds, then pick them below so mono turns on whenever they're connected"
        org.omarchy.music.AutoMono.active -> "$picked is connected, so mono is on"
        else -> "Mono turns on whenever $picked connects, and off again when it disconnects"
    }, auto) {
        Switch(auto, { Music.setOpt(P.AUTO_MONO, it) }, colors = swColors())
    }
    if (auto) {
        val now = remember(auto, org.omarchy.music.AutoMono.active) { org.omarchy.music.AutoMono.connected() }
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (now + listOf(picked)).filter { it.isNotEmpty() }.distinct().forEach { name ->
                Chip(name, name == picked) { P.setText(P.AUTO_MONO_DEVICE, name); org.omarchy.music.AutoMono.check() }
            }
            if (now.isEmpty() && picked.isEmpty()) Text("No Bluetooth earbuds connected right now", style = T.sub)
        }
    }
}

/** The Equalizer's crossfeed, crossfade (and its length) and smart transitions: each off until you turn it on. */
@Composable
fun TransitionSwitches() {
    val P = org.omarchy.music.Prefs
    @Composable
    fun row(name: String, about: String, key: String) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(name, style = T.body)
                Text(about, style = T.sub)
            }
            Switch(P.on(key), { Music.setOpt(key, it) }, colors = swColors())
        }
    }
    row("Headphone crossfeed", "Feeds a little of each side into the other ear, the way you'd hear speakers across a " +
        "room. It's easier on the ears with old records that put instruments hard left or right. Choose Light, Medium or Strong", P.CROSSFEED)
    if (P.on(P.CROSSFEED)) Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val k = P.num(P.CROSSFEED_K, 0.3f)
        listOf(0.2f to "Light", 0.3f to "Medium", 0.45f to "Strong").forEach { (v, l) ->
            Chip(l, kotlin.math.abs(k - v) < 0.01f) { Music.setSoundNum(P.CROSSFEED_K, v) }
        }
    }
    row("Song crossfade", "Each song fades out as the next fades in, with no gap, over the length you set (1 to 12 " +
        "seconds)" + if (Music.bitPerfect) ". It can't while bit-perfect is on" else "", P.CROSSFADE)
    if (P.on(P.CROSSFADE)) {
        val secs = P.num(P.CROSSFADE_SECS, 6f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Length", style = T.sub, modifier = Modifier.width(60.dp))
            Slider(secs, { P.setNum(P.CROSSFADE_SECS, it.roundToInt().toFloat()) }, valueRange = 1f..12f, steps = 10,
                colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.fg.copy(alpha = 0.1f)),
                modifier = Modifier.weight(1f))
            Text("${secs.roundToInt()} s", style = T.time, modifier = Modifier.width(44.dp))
        }
    }
    row("Smart transitions", "Skips the crossfade between songs from the same album, so albums that flow from one " +
        "track into the next (live albums, mixes) stay seamless, while other songs still crossfade",
        P.SMART_TRANSITIONS)
}

/** Bit-perfect to a USB DAC, and ReplayGain. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundQuality(ui: Ui) {
    Line("Bit-perfect", Music.dacNote.ifEmpty {
        if (onPhone) "Sends each song to your USB DAC (an external sound card) exactly as it is, at its own " +
            "sample rate, for the purest sound. Nothing changes it: no EQ, no song volume, no speed. Needs Android 14 and a USB DAC"
        else "Sends each song to your sound card or DAC exactly as it is, at its own sample rate, for the " +
            "purest sound. It uses exclusive mode, so no other sound on the PC is heard while it plays, and nothing changes the " +
            "music: no EQ, no song volume, no speed" },
        Music.bitPerfect) {
        Switch(Music.bitPerfect, { Music.chooseBitPerfect(it) }, colors = swColors())
    }
    val P = org.omarchy.music.Prefs
    Line("Clip guard", "When a song's volume, ReplayGain or the EQ would push the loudest moments (peaks) past the " +
        "maximum level, a limiter gently holds them down instead of letting them crackle. It only ever touches a peak that would " +
        "clip. It also lets ReplayGain make quiet songs louder", P.on(P.CLIP_GUARD)) {
        Switch(P.on(P.CLIP_GUARD), { Music.setOpt(P.CLIP_GUARD, it) }, colors = swColors())
    }
    Line("Dither", "When 16-bit (CD quality) music goes through the EQ, it's rounded off with a whisper of shaped " +
        "noise, so quiet fades stay smooth instead of grainy. Hi-res music stays 32-bit and needs none", P.on(P.DITHER)) {
        Switch(P.on(P.DITHER), { Music.setOpt(P.DITHER, it) }, colors = swColors())
    }
    if (!onPhone) Line("High-quality resampling", "When a song's sample rate doesn't match your sound card's (44.1 kHz " +
        "music on a 48 kHz card), it's converted with a longer, sharper filter. You get cleaner highs for a little more work from the PC", P.on(P.HQ_RESAMPLE)) {
        Switch(P.on(P.HQ_RESAMPLE), { Music.setOpt(P.HQ_RESAMPLE, it) }, colors = swColors())
    }
    Line("Loudness compensation", "At low volume our ears lose bass and treble first, so music sounds thin. This lifts them " +
        "back by as much as the volume is turned down, and does nothing at full volume", P.on(P.LOUDNESS)) {
        Switch(P.on(P.LOUDNESS), { Music.setOpt(P.LOUDNESS, it) }, colors = swColors())
    }
    val bal = P.num(P.BALANCE, 0f)
    Line("Balance", when {
        bal < -0.005f -> "Leaning left: the right side is turned down ${"%.1f".format(-20 * kotlin.math.log10(1.0 + bal))} dB. Centre evens it out again"
        bal > 0.005f -> "Leaning right: the left side is turned down ${"%.1f".format(-20 * kotlin.math.log10(1.0 - bal))} dB. Centre evens it out again"
        else -> "Centre. Slide to move the sound left or right: for in-ear monitors (IEMs) that are a little louder on one side, or if one ear hears less"
    }, bal != 0f) {
        if (bal != 0f) Pill("Centre", quiet = true) { Music.setSoundNum(P.BALANCE, 0f) }
    }
    Slider(bal, { Music.setSoundNum(P.BALANCE, (kotlin.math.round(it * 50) / 50f)) }, valueRange = -0.5f..0.5f,
        colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.fg.copy(alpha = 0.1f)))
    Line("Detail", "Brings forward background sounds, bass lines and small details: the stereo gets a touch wider, " +
        "presence and air (the upper mids and highs) lift a little, and the level drops to match so nothing clips", P.on(P.DETAIL)) {
        Switch(P.on(P.DETAIL), { Music.setOpt(P.DETAIL, it) }, colors = swColors())
    }
    val tilt = P.num(P.TILT, 0f)
    Line("Tilt", when {
        tilt > 0.01f -> "Brighter: everything below 1 kHz is turned down ${"%.1f".format(tilt)} dB"
        tilt < -0.01f -> "Warmer: everything above 1 kHz is turned down ${"%.1f".format(-tilt)} dB"
        else -> "Neutral. Slide to lean the whole sound warmer (fuller bass) or brighter (crisper treble), up to 4 dB. It only ever turns things down, so it can't clip"
    }, tilt != 0f) {
        if (tilt != 0f) Pill("Neutral", quiet = true) { Music.setSoundNum(P.TILT, 0f) }
    }
    Slider(tilt, { Music.setSoundNum(P.TILT, kotlin.math.round(it * 2) / 2f) }, valueRange = -4f..4f,
        colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.fg.copy(alpha = 0.1f)))
    Line("ReplayGain", if (Music.bitPerfect) "Off while bit-perfect is on, because it would change the music"
        else "Evens out loudness so songs don't jump louder or quieter, using ReplayGain (loudness tags " +
            "in your files). By song levels every song; By album keeps an album's quiet and loud tracks as they were meant. The " +
            "levelling target below sets how loud: louder than Standard needs Clip guard on (it limits the peaks); without it, " +
            "songs are never made louder than they are", Music.replayGain != "off") {}
    Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("off" to "Off", "track" to "By song", "album" to "By album").forEach { (k, l) ->
            Chip(l, Music.replayGain == k) { if (!Music.bitPerfect) Music.chooseReplayGain(k) }
        }
    }
    val target = P.num(P.RG_TARGET, -18f)
    Text("Levelling target", style = T.sub)
    androidx.compose.foundation.layout.FlowRow(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(-23f to "Quiet −23", -18f to "Standard −18", -16f to "Louder −16", -14f to "Loud −14").forEach { (v, l) ->
            Chip(l, target == v) { Music.setSoundNum(P.RG_TARGET, v); Music.toast("Levelling to $v LUFS, from the next song") }
        }
    }
}

/** The keys in API_KEYS: each one you fill in is kept with the rest of the app's state. */
@Composable
private fun SettingsLine() {
    var open by remember { mutableStateOf(false) }
    val keys = remember { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { putAll(Music.apiKeys) } }
    val have = org.omarchy.music.API_KEYS.filterKeys { Music.apiKeys[it] != null }.values.map { it.first }
    Line("Settings", if (have.isNotEmpty()) "Using your own " + have.joinToString(", ")
        else "Optional keys of your own for Google's pictures (covers and " +
        "artist pictures) and an AutoEq server (headphone corrections). None is needed: everything works without them", have.isNotEmpty()) {
        Pill(if (open) "Close" else "Edit", quiet = true) { open = !open }
    }
    if (open) {
        org.omarchy.music.API_KEYS.forEach { (k, v) ->
            Text(v.second, style = T.sub, modifier = Modifier.padding(top = 4.dp))
            Field(keys[k] ?: "", v.first, password = k == "google_key" || k == "chartmetric") { keys[k] = it }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.End) {
            Pill("Save") { Music.saveApiKeys(keys.toMap()); open = false; Music.toast("Settings saved") }
        }
    }
}

@Composable
private fun FolderPicker() {
    var open by remember { mutableStateOf(false) }
    val pickFolder = rememberFolderPick { path -> if (path != null) Music.setFolders(Music.folders + path) }
    if (pickFolder != null) Pill("Add a folder…", quiet = true) { pickFolder() }
    Spacer(Modifier.width(6.dp))
    Pill("Choose") { open = true }
    if (open) FolderBrowser { open = false }
}

@Composable
private fun SyncSwitch(key: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Sync ", style = T.sub)
        Switch(Music.syncSources[key] == true, { Music.setSyncSource(key, it) }, colors = swColors())
    }
}

@Composable
private fun swColors() = SwitchDefaults.colors(checkedThumbColor = C.darker, checkedTrackColor = C.accent,
    uncheckedThumbColor = C.muted, uncheckedTrackColor = C.dark, uncheckedBorderColor = C.line)

/** A section of Settings: a row with its name and an arrow that folds it open or shut (all shut when Settings opens). */
@Composable
private fun Group(title: String, ui: Ui, content: @Composable () -> Unit) {
    val open = title in ui.settingsOpen
    val bg = if (C.apple && C.isLight) androidx.compose.ui.graphics.Color(0xFFF2F2F7) else C.light
    Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(R.card).background(bg)
        .clickable { if (open) ui.settingsOpen -= title else ui.settingsOpen += title }
        .padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = T.body.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), modifier = Modifier.weight(1f))
        Text(if (open) "▾" else "▸", style = T.sub)
    }
    if (open) Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(R.card).background(bg)
        .padding(horizontal = 14.dp, vertical = 4.dp)) { content() }
}

/** A row of Settings: its name and what it is (or what it's doing) on the left, its switch or buttons on the right. */
@Composable
private fun Line(name: String, state: String, ok: Boolean, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(name, style = T.body.copy(fontSize = 16.sp))
            if (state.isNotEmpty()) Text(state, style = T.sub.copy(color = if (ok) C.accent else C.muted), modifier = Modifier.padding(top = 2.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = C.line, modifier = Modifier.padding(vertical = 6.dp))

/** A screen over everything, with ‹ and a name: Settings, the Equalizer. */
@Composable
fun FullScreen(title: String, close: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    PlatformBack { close() }
    Column(Modifier.fillMaxSize().background(C.bg).then(C.gradient?.let { Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(it)) } ?: Modifier)
        .pointerInput(Unit) { detectTapGestures { } }.statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(Ico.BACK, onClick = close)
            Text(title, style = T.head.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), modifier = Modifier.weight(1f).padding(start = 4.dp))
            actions()
        }
        content()
    }
}

/** The equalizer: each output (the speaker, headphones, your car, earbuds, USB) has its own, used as soon
 *  as the sound goes there; 3, 5, 10, 15 or 31 bands; a correction for your headphones; bass and treble. */
@Composable
private fun EqSheet(ui: Ui) {
    val eq = Music.eq
    var tones by remember { mutableStateOf(false) }
    var outs by remember { mutableStateOf(false) }
    // Compact: the bands fill the screen, every one in view at once; the rest is one row.
    var compact by remember { mutableStateOf(false) }
    val screenH = screenHeightDp()
    FullScreen("Equalizer", { ui.sheet = null }, actions = {
        Chip("Compact", compact) { compact = !compact }
        Spacer(Modifier.width(6.dp))
        Switch(eq.on, { Music.setEqOn(it) }, colors = swColors())
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
          if (compact) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    var counts by remember { mutableStateOf(false) }
                    Chip("${eq.count} bands ▾", true) { counts = true }
                    androidx.compose.material3.DropdownMenu(counts, { counts = false }, containerColor = C.light) {
                        org.omarchy.music.Eq.COUNTS.forEach { n ->
                            androidx.compose.material3.DropdownMenuItem({ Text("$n bands", style = T.body) }, { counts = false; Music.setEqCount(n) })
                        }
                    }
                }
                Box {
                    var presets by remember { mutableStateOf(false) }
                    Chip("${Eq.PRESETS[eq.preset]?.first ?: "Custom"} ▾", false) { presets = true }
                    androidx.compose.material3.DropdownMenu(presets, { presets = false }, containerColor = C.light) {
                        Eq.PRESETS.forEach { (k, v) ->
                            androidx.compose.material3.DropdownMenuItem({ Text(v.first, style = T.body) }, { presets = false; Music.setEqPreset(k) })
                        }
                    }
                }
                Box {
                    var phones by remember { mutableStateOf(false) }
                    Chip("${Eq.headphones(eq)[eq.headphones]?.first ?: "None"} ▾", false) { phones = true }
                    androidx.compose.material3.DropdownMenu(phones, { phones = false }, containerColor = C.light) {
                        Eq.headphones(eq).forEach { (k, v) ->
                            androidx.compose.material3.DropdownMenuItem({ Text(v.first, style = T.body) }, { phones = false; Music.setEqHeadphones(k) })
                        }
                    }
                }
            }
            val freqs = Eq.bandsFor(eq.count)
            val rowH = ((screenH - 150) / freqs.size).coerceIn(18, 48).dp
            freqs.forEachIndexed { i, hz ->
                val g = eq.bands.getOrElse(i) { 0.0 }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(rowH)) {
                    Text(Eq.label(hz), style = T.time, modifier = Modifier.width(40.dp))
                    Slider(g.toFloat(), { Music.setEqBand(i, it.toDouble()) }, valueRange = -12f..12f,
                        colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.fg.copy(alpha = 0.1f)),
                        modifier = Modifier.weight(1f))
                    Text(Eq.fmtDb(g), style = T.time, modifier = Modifier.width(40.dp).clickable { Music.setEqBand(i, 0.0) })
                }
            }
          } else {
            if (Music.bitPerfect) Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(R.card).background(C.light)
                .border(1.dp, C.red, R.card).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Bit-perfect is on, so the equalizer and each song's volume are bypassed and your DAC gets " +
                    "the music untouched. Switch bit-perfect off to hear the settings here.", style = T.sub, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Pill("Turn off", quiet = true) { Music.chooseBitPerfect(false) }
            }
            // The output these settings are for.
            val key = eq.output.ifEmpty { Music.outputNow.key }
            val label = eq.outputs[key]?.label?.ifEmpty { null } ?: if (key == Music.outputNow.key) Music.outputNow.label else key.substringAfter(':')
            Box {
                Row(Modifier.fillMaxWidth().clip(R.card).background(C.light).border(1.dp, C.line, R.card).clickable { outs = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(outIcon(org.omarchy.music.Outputs.kindOf(key)), C.accent, 22.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(label, style = T.title.copy(color = C.accent), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (!eq.perOutput) "One equalizer for every output: these settings are used wherever the sound goes"
                            else if (key == Music.outputNow.key) "Playing through this now: changes are heard straight away"
                            else "Not playing through this now: these settings switch in when the sound goes here", style = T.sub)
                    }
                    Icon(Ico.DOWN, C.muted, 16.dp)
                }
                androidx.compose.material3.DropdownMenu(outs, { outs = false }, containerColor = C.light) {
                    val known = (eq.outputs.keys + Music.outputNow.key + "speaker").distinct()
                    known.forEach { k ->
                        val l = eq.outputs[k]?.label?.ifEmpty { null } ?: if (k == Music.outputNow.key) Music.outputNow.label
                            else if (k == "speaker") "Built-in speaker" else k.substringAfter(':')
                        androidx.compose.material3.DropdownMenuItem(
                            leadingIcon = { Icon(outIcon(org.omarchy.music.Outputs.kindOf(k)), if (k == key) C.accent else C.muted, 20.dp) },
                            text = { Text(l + if (k == Music.outputNow.key) "  · now" else "", style = T.body.copy(color = if (k == key) C.accent else C.fg)) },
                            onClick = { outs = false; Music.editEqOutput(k) },
                            trailingIcon = if (k != key && k != Music.outputNow.key && k != "speaker") ({
                                IconButton(Ico.CLOSE, C.muted, 14.dp, 6.dp, desc = "Forget") { Music.forgetEqOutput(k) }
                            }) else null)
                    }
                }
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("A separate equalizer for each output (speaker, headphones, earbuds, car…), switched in by itself when the sound goes there", style = T.sub, modifier = Modifier.weight(1f))
                Switch(eq.perOutput, { Music.setEqPerOutput(it) }, colors = swColors())
            }
            // How many bands, and bass & treble.
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    var counts by remember { mutableStateOf(false) }
                    Row(Modifier.clip(R.pill).border(1.dp, C.accent.copy(alpha = 0.5f), R.pill).clickable { counts = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ico.EQ, C.accent, 18.dp)
                        Text("  ${eq.count} bands", style = T.tab.copy(color = C.accent))
                    }
                    androidx.compose.material3.DropdownMenu(counts, { counts = false }, containerColor = C.light) {
                        org.omarchy.music.Eq.COUNTS.forEach { n ->
                            androidx.compose.material3.DropdownMenuItem({ Text("$n bands", style = T.body.copy(color = if (n == eq.count) C.accent else C.fg)) },
                                { counts = false; Music.setEqCount(n) })
                        }
                    }
                }
                Chip("Bass and treble", tones) { tones = !tones }
            }
            if (tones) listOf("bass" to "Bass", "treble" to "Treble").forEach { (k, l) ->
                val g = if (k == "bass") eq.bass else eq.treble
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(l, style = T.sub, modifier = Modifier.width(60.dp))
                    Slider(g.toFloat(), { Music.setEqTone(k, it.toDouble()) }, valueRange = -12f..12f,
                        colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.fg.copy(alpha = 0.1f)),
                        modifier = Modifier.weight(1f))
                    Text(Eq.fmtDb(g), style = T.time, modifier = Modifier.width(44.dp).clickable { Music.setEqTone(k, 0.0) })
                }
            }
            BoostFader(eq)
            Text("Sound", style = T.section, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            ChipRow(Eq.PRESETS.map { (k, v) -> k to v.first }, eq.preset) { Music.setEqPreset(it) }
            // The bands: one line each (31 fit this way), the frequency, the slider, the gain.
            val freqs = Eq.bandsFor(eq.count)
            Column(Modifier.padding(top = 10.dp).clip(R.card).background(C.light.copy(alpha = if (C.isLight) 1f else 0.6f)).padding(vertical = 6.dp)) {
                freqs.forEachIndexed { i, hz ->
                    val g = eq.bands.getOrElse(i) { 0.0 }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp).height(if (eq.count > 15) 34.dp else 42.dp)) {
                        Text(Eq.label(hz), style = T.time, modifier = Modifier.width(44.dp))
                        Slider(g.toFloat(), { Music.setEqBand(i, it.toDouble()) }, valueRange = -12f..12f,
                            colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.fg.copy(alpha = 0.1f)),
                            modifier = Modifier.weight(1f))
                        Text(Eq.fmtDb(g), style = T.time, modifier = Modifier.width(44.dp).clickable { Music.setEqBand(i, 0.0) })
                    }
                }
            }
            Text("Headphones correction", style = T.section, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            ChipRow(Eq.headphones(eq).map { (k, v) -> k to v.first }, eq.headphones) { Music.setEqHeadphones(it) }
            AutoEqFinder()
            Text("Between songs, and on headphones", style = T.section, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            TransitionSwitches()
            Text(if (eq.on) "Pre-gain ${Eq.fmtDb(Eq.preamp(Eq.filters(eq)))} dB: the level is lowered this much by itself, so the EQ's boosts don't clip."
                else "The equalizer is off, so the music plays untouched. Switch it on at the top to hear your settings.",
                style = T.sub, modifier = Modifier.padding(top = 8.dp))
          }
        }
    }
}

/** Boost: a fader from normal (the bottom) to 12 dB louder, on everything, for this output. A line marks how far it
 *  goes cleanly (the EQ's own pre-cut back, and a little the limiter does unheard); above it the limiter squashes peaks. */
@Composable
private fun BoostFader(eq: org.omarchy.music.EqSettings) {
    val clean = Eq.boostClean(eq)
    val b = eq.boost
    val over = b > clean
    val red = MONO_RED
    Text("Boost", style = T.section, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        val h = 170.dp
        val inset = 10.dp   // the thumb's room at each end
        Box(Modifier.width(56.dp).height(h)) {
            Slider(b.toFloat(), { Music.setEqBoost(it.toDouble()) }, valueRange = 0f..Eq.BOOST_MAX.toFloat(),
                colors = SliderDefaults.colors(thumbColor = if (over) red else C.accent, activeTrackColor = if (over) red else C.accent,
                    inactiveTrackColor = C.fg.copy(alpha = 0.1f)),
                modifier = Modifier.align(Alignment.Center).requiredWidth(h).graphicsLayer { rotationZ = -90f })
            // the clean line, across the fader
            val y = inset + (h - inset * 2) * (1f - (clean / Eq.BOOST_MAX).toFloat())
            Box(Modifier.offset(y = y - 1.dp).fillMaxWidth().height(2.dp).background(red.copy(alpha = 0.8f)))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(if (b == 0.0) "Off: normal volume" else "+${"%.1f".format(b).removeSuffix(".0")} dB louder",
                style = T.title.copy(color = if (over) red else C.fg))
            Text(when {
                b == 0.0 -> "Makes everything you play louder, up to 12 dB, for quiet headphones, and only on this " +
                    "output. It stays clean up to the red line (+${"%.1f".format(clean).removeSuffix(".0")} dB here); past it, a " +
                    "limiter squashes the peaks so they don't distort"
                over -> "Past the red line: a limiter is squashing the loudest peaks so they don't distort. Fine " +
                    "for noisy places; for the best sound stay at or under +${"%.1f".format(clean).removeSuffix(".0")} dB"
                else -> "Clean: you're under the red line (+${"%.1f".format(clean).removeSuffix(".0")} dB here, " +
                    "and higher when the EQ turns things down), so nothing is squashed"
            }, style = T.sub)
            if (b > 0) Pill("Off", quiet = true) { Music.setEqBoost(0.0) }
        }
    }
}

private fun outIcon(kind: String) = when (kind) {
    "speaker" -> Ico.VIS; "wired" -> Ico.HEADPHONES; "usb" -> Ico.DOWNLOAD; "car" -> Ico.CAR; else -> Ico.BLUETOOTH
}

@Composable
private fun VerticalSlider(value: Float, onChange: (Float) -> Unit) {
    // A slider stood on its end: drawn and touched turned a quarter (the same on the phone and the PC)
    Box(Modifier.width(48.dp).height(160.dp), contentAlignment = Alignment.Center) {
        Slider(value, onChange, valueRange = -12f..12f,
            colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.fg.copy(alpha = 0.1f)),
            modifier = Modifier.requiredWidth(160.dp).graphicsLayer { rotationZ = -90f })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(items: List<Pair<String, String>>, on: String, pick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (k, l) -> Chip(l, k == on) { pick(k) } }
    }
}

@Composable
private fun PlaylistPicker(ui: Ui, tracks: List<org.omarchy.music.Track>) {
    AlertDialog(onDismissRequest = { ui.pickPlaylistFor = null }, containerColor = C.light,
        title = { Text(if (tracks.size == 1) "Add “${tracks[0].title}” to…" else "Add ${tracks.size} songs to…", style = T.title,
            maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn {
                item {
                    Text("＋  New playlist", style = T.body.copy(color = C.accent), modifier = Modifier.fillMaxWidth().clickable {
                        ui.pickPlaylistFor = null
                        ui.askName = Triple("New playlist", "") { n -> Music.addToPlaylist(Music.newPlaylist(n), tracks) }
                    }.padding(vertical = 10.dp))
                }
                val lists = Music.lists.all()
                items(lists.size) { i ->
                    val pl = lists[i]
                    Text("${pl.name}  ·  ${pl.tracks.size}", style = T.body, modifier = Modifier.fillMaxWidth().clickable {
                        ui.pickPlaylistFor = null; Music.addToPlaylist(pl.id, tracks)
                    }.padding(vertical = 10.dp))
                }
            }
        },
        confirmButton = { TextButton({ ui.pickPlaylistFor = null }) { Text("Cancel", color = C.muted) } })
}

/** Long-press on a playlist: which folder to put it in (or a new one). */
@Composable
private fun FolderPicker(ui: Ui, item: org.omarchy.music.FolderItem) {
    AlertDialog(onDismissRequest = { ui.folderFor = null }, containerColor = C.light,
        title = { Text("Put “${item.title}” in a folder", style = T.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn {
                item {
                    Text("＋  New folder", style = T.body.copy(color = C.accent), modifier = Modifier.fillMaxWidth().clickable {
                        ui.folderFor = null
                        ui.askName = Triple("New folder", "") { n -> Music.newFolder(n, item) }
                    }.padding(vertical = 10.dp))
                }
                val folders = Music.playlistFolders.all()
                items(folders.size) { i ->
                    val f = folders[i]
                    Text("${f.name}  ·  ${f.playlists.size}", style = T.body, modifier = Modifier.fillMaxWidth().clickable {
                        ui.folderFor = null; Music.addToFolder(f.id, item)
                    }.padding(vertical = 10.dp))
                }
            }
        },
        confirmButton = { TextButton({ ui.folderFor = null }) { Text("Cancel", color = C.muted) } })
}

/** Auto EQ: find any headphones in AutoEq's results as you type, or Detect the ones connected;
 *  their correction is added to Headphones and used (and comes back whenever they connect). */
@Composable
private fun AutoEqFinder() {
    var q by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<org.omarchy.music.AutoEq.Entry>>(emptyList()) }
    var note by remember { mutableStateOf("") }
    val api = Music.apiKeys["autoeq_api"].orEmpty()
    LaunchedEffect(q) {
        if (q.trim().length < 2) { found = emptyList(); note = ""; return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        Music.bg({ if (api.isNotEmpty()) org.omarchy.music.AutoEq.apiSearch(api, q) else org.omarchy.music.AutoEq.search(org.omarchy.music.AutoEq.index(Music.ctx), q) }) { r, err ->
            found = r ?: emptyList()
            note = if (err != null) "AutoEq couldn't be reached: ${err.message?.take(80)}" else if (found.isEmpty()) "Nothing in AutoEq by that name" else ""
        }
    }
    val importer = rememberOpenFile { uri -> if (uri != null) Music.importEqFile(uri) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Box(Modifier.weight(1f)) { Field(q, "Auto EQ: find your headphones") { q = it } }
        Spacer(Modifier.width(6.dp))
        Pill("Import", quiet = true) { importer.launch(arrayOf("text/*", "application/octet-stream")) }
        Spacer(Modifier.width(6.dp))
        Pill("Detect", quiet = true) {
            val dev = org.omarchy.music.AutoEq.output(Music.ctx)
            if (dev.isEmpty()) { note = "No headphones connected"; return@Pill }
            note = "Looking…"
            Music.bg({ org.omarchy.music.AutoEq.matchDevice(listOf(dev), org.omarchy.music.AutoEq.index(Music.ctx)) }) { m, err ->
                found = listOfNotNull(m)
                note = when {
                    err != null -> "AutoEq couldn't be reached: ${err.message?.take(80)}"
                    m != null -> "$dev is connected: AutoEq has"
                    else -> "$dev is connected: AutoEq has nothing by that name. Look yours up."
                }
            }
        }
    }
    if (note.isNotEmpty()) Text(note, style = T.sub, modifier = Modifier.padding(top = 4.dp))
    found.take(8).forEach { e ->
        Text("${e.name} · ${e.source}", style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clickable {
                note = "Getting ${e.name}…"
                Music.bg({ org.omarchy.music.AutoEq.profile(e, api) }) { f, err ->
                    if (f != null) { found = emptyList(); note = ""; q = ""; Music.addAutoEq(e, f) }
                    else note = "Couldn't get it: ${err?.message?.take(80)}"
                }
            }.padding(vertical = 8.dp))
    }
}

/** Cover…: covers of the first album left to pick from (Google's, Genius's, Wikipedia's), or a
 *  picture of yours; then the next album. Unreleased songs often have none: skip, or pick a file. */
@Composable
private fun CoverDialog(ui: Ui, albums: List<List<org.omarchy.music.Track>>) {
    val songs = albums[0]
    val t = songs[0]
    var choices by remember(songs) { mutableStateOf<List<org.omarchy.music.Pictures.Choice>?>(null) }
    var busy by remember(songs) { mutableStateOf(false) }
    var wholeAlbum by remember(songs) { mutableStateOf(true) }
    var allPicked by remember(albums.size) { mutableStateOf(true) }   // several albums: one cover for every song picked
    val several = albums.size > 1
    val start = "${t.artist.split(",")[0].trim()} - ${t.album.ifEmpty { t.title }}"
    var query by remember(songs) { mutableStateOf(start) }
    var asked by remember(songs) { mutableStateOf(start) }
    val next = { ui.coverFor = albums.drop(1).ifEmpty { null } }
    val use = { src: String ->
        busy = true
        if (several && allPicked) Music.setCover(albums.flatten(), src, true) { ui.coverFor = null }
        else Music.setCover(songs, src, wholeAlbum) { next() }
    }
    val pick = rememberOpenFile { uri -> if (uri != null) use(uri) }
    LaunchedEffect(songs, asked) {
        choices = null
        val (a, al) = if (" - " in asked) asked.substringBefore(" - ").trim() to asked.substringAfter(" - ").trim() else "" to asked.trim()
        Music.bg({ org.omarchy.music.Pictures.coverChoices(a, al) }) { c, _ -> choices = c ?: emptyList() }
    }
    AlertDialog(onDismissRequest = { ui.coverFor = null }, containerColor = C.light,
        title = { Text("Cover of “${t.album.ifEmpty { t.title }}”" + if (albums.size > 1) " · ${albums.size} left" else "", style = T.title,
            maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            val c = choices
            Column {
                // Search by hand: "Artist - Album", or anything.
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(R.pill).background(C.bg), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ico.SEARCH, C.muted, 18.dp, Modifier.padding(start = 12.dp))
                    androidx.compose.foundation.text.BasicTextField(query, { query = it }, singleLine = true, textStyle = T.body,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(C.accent),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { asked = query }),
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 11.dp).typing())
                    if (query.isNotEmpty()) IconButton(Ico.CLOSE, C.muted, 14.dp, 8.dp) { query = "" }
                    IconButton(Ico.SEARCH, C.accent, 18.dp, 8.dp) { asked = query }
                }
                Text(when {
                    busy -> "Putting it on the songs…"
                    c == null -> "Looking on Google, Genius and Wikipedia…"
                    c.isEmpty() -> "Nothing found: unreleased songs often have none. Pick a picture of yours, or skip it."
                    else -> "${t.artist}${if (t.album.isNotEmpty()) " · goes on every song of the album" else ""}"
                }, style = T.sub, modifier = Modifier.padding(bottom = 8.dp))
                if (!c.isNullOrEmpty() && !busy) LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items((c.size + 2) / 3) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                            for (ch in c.drop(row * 3).take(3)) Column(Modifier.clickable { use(ch.url) }) {
                                ArtImage(ch.thumb, 84.dp)
                                Text(ch.from, style = T.time)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (several) Row(Modifier.clickable { allPicked = !allPicked }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(allPicked, { allPicked = it },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = C.accent))
                    Text("Use this cover for all ${albums.sumOf { it.size }} songs picked, not one album at a time", style = T.sub)
                }
                if (t.album.isNotEmpty() && !(several && allPicked)) Row(Modifier.clickable { wholeAlbum = !wholeAlbum }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(wholeAlbum, { wholeAlbum = it },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = C.accent))
                    Text("Apply the cover to all the songs on this album", style = T.sub)
                }
                Row {
                    TextButton(next, enabled = !busy) { Text(if (albums.size > 1) "Skip" else "Cancel", color = C.muted) }
                    TextButton({ pick.launch(arrayOf("image/*")) }, enabled = !busy) { Text(if (onPhone) "From gallery…" else "From a file…", color = C.accent) }
                }
            }
        })
}

/** Change picture…: pictures of the artist to pick from (Google's, Genius's, Wikipedia's), or one of yours. */
@Composable
private fun ArtistPictureDialog(ui: Ui, name: String) {
    var choices by remember(name) { mutableStateOf<List<org.omarchy.music.Pictures.Choice>?>(null) }
    val close = { ui.artistPictureFor = null }
    val use = { src: String -> close(); Music.setArtistPicture(name, src) }
    val pick = rememberOpenFile { uri -> if (uri != null) use(uri) }
    LaunchedEffect(name) { Music.bg({ org.omarchy.music.Pictures.artistChoices(name) }) { c, _ -> choices = c ?: emptyList() } }
    AlertDialog(onDismissRequest = close, containerColor = C.light,
        title = { Text("A picture of $name", style = T.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            val c = choices
            Column {
                Text(when {
                    c == null -> "Looking on Google, Genius and Wikipedia…"
                    c.isEmpty() -> "Nothing found. Pick a picture of yours."
                    else -> "Kept for their tile and their page"
                }, style = T.sub, modifier = Modifier.padding(bottom = 8.dp))
                if (!c.isNullOrEmpty()) LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items((c.size + 2) / 3) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                            for (ch in c.drop(row * 3).take(3)) Column(Modifier.clickable { use(ch.url) }) {
                                ArtImage(ch.thumb, 84.dp)
                                Text(ch.from, style = T.time)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ pick.launch(arrayOf("image/*")) }) { Text("Pick a picture…", color = C.accent) } },
        dismissButton = { TextButton(close) { Text("Cancel", color = C.muted) } })
}

/** Speed…: how fast the songs play, from half to twice their speed. */
@Composable
private fun SpeedDialog(ui: Ui, tracks: List<org.omarchy.music.Track>) {
    val start = remember(tracks) { Music.speeds.get(tracks[0]) }
    var speed by remember(tracks) { mutableStateOf(start.speed.toFloat()) }
    var pitch by remember(tracks) { mutableStateOf(start.pitch) }
    val close = { ui.speedFor = null }
    val done = { s: Double -> close(); Music.setSpeed(tracks, s, pitch); ui.afterOption() }
    AlertDialog(onDismissRequest = close, containerColor = C.light,
        title = { Text(if (tracks.size == 1) "Speed of “${tracks[0].title}”" else "Speed of ${tracks.size} songs", style = T.title,
            maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(org.omarchy.music.fmtSpeed(Math.round(speed * 20) / 20.0), style = T.title.copy(color = C.accent))
                Slider(speed, { speed = Math.round(it * 20) / 20f }, valueRange = 0.5f..2f, steps = 29,
                    colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.fg.copy(alpha = 0.1f)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pitch moves with it (a sped-up edit)", style = T.sub, modifier = Modifier.weight(1f))
                    Switch(pitch, { pitch = it }, colors = swColors())
                }
            }
        },
        confirmButton = { TextButton({ done(Math.round(speed * 20) / 20.0) }) { Text("Save", color = C.accent) } },
        dismissButton = {
            Row {
                TextButton({ done(1.0) }) { Text("Normal", color = C.fg) }
                TextButton(close) { Text("Cancel", color = C.muted) }
            }
        })
}

/** Volume…: a song's own boost (quiet unreleased songs), from -12 to +12 dB, kept for it. */
@Composable
private fun VolumeDialog(ui: Ui, tracks: List<org.omarchy.music.Track>) {
    var db by remember(tracks) { mutableStateOf(Music.gains.get(tracks[0]).toFloat()) }
    val close = { ui.volumeFor = null }
    val done = { v: Double -> close(); Music.setGain(tracks, v); ui.afterOption() }
    AlertDialog(onDismissRequest = close, containerColor = C.light,
        title = { Text(if (tracks.size == 1) "Volume of “${tracks[0].title}”" else "Volume of ${tracks.size} songs", style = T.title,
            maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text("%+.1f dB".format(db), style = T.title.copy(color = C.accent))
                Slider(db, { db = Math.round(it * 2) / 2f }, valueRange = -12f..12f, steps = 47,
                    colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.fg.copy(alpha = 0.1f)))
                Text("Plays this much louder (or quieter) from now on. +6 dB is about twice as loud; very loud songs may distort above 0.",
                    style = T.sub)
            }
        },
        confirmButton = { TextButton({ done(Math.round(db * 2) / 2.0) }) { Text("Save", color = C.accent) } },
        dismissButton = {
            Row {
                TextButton({ done(0.0) }) { Text("Normal", color = C.fg) }
                TextButton(close) { Text("Cancel", color = C.muted) }
            }
        })
}

/** Upgrade…: these songs (none: every song of yours) for Tidal's FLAC where it's better. */
@Composable
private fun UpgradeDialog(ui: Ui, tracks: List<org.omarchy.music.Track>) {
    val all = tracks.isEmpty()
    val n = if (all) Music.local.files.size else tracks.size
    var every by remember { mutableStateOf(false) }
    val close = { ui.upgradeFor = null }
    AlertDialog(onDismissRequest = close, containerColor = C.light,
        title = { Text(if (!all && n == 1) "Upgrade “${tracks[0].title}”?" else if (all) "Upgrade your $n songs?" else "Upgrade these $n songs?",
            style = T.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text((if (n == 1) "The song is" else "Each song is") + " looked up on Tidal. Where Tidal has it better, the FLAC is downloaded and " +
                    "takes the old file's place: in your playlists and the queue.\n\n" +
                    "• MP3, AAC and other lossy files: replaced with FLAC\n" +
                    "• 16-bit FLAC/WAV: replaced only when Tidal has it in 24-bit\n" +
                    "• 24-bit files, and songs Tidal doesn't have: left as they are (you're asked about the ones kept as good already, at the end)\n\n" +
                    "The old files go to $TRASH" + (if (onPhone) " (it asks you first)" else "") + ", so you can get them back. The new ones go to " +
                    Music.downloadFolder() + ". It takes a while: you can keep listening, and Stop it in Settings.",
                    style = T.body)
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Replace every song Tidal has, even ones yours is as good as or better than", style = T.sub, modifier = Modifier.weight(1f))
                    Switch(every, { every = it }, colors = swColors())
                }
            }
        },
        confirmButton = { TextButton({ close(); Music.upgradeSongs(if (all) null else tracks, every); ui.afterOption() }) { Text("Upgrade", color = C.accent) } },
        dismissButton = { TextButton(close) { Text("Cancel", color = C.muted) } })
}

/** After Upgrade: the songs it left because yours are as good or better. Replace them anyway? */
@Composable
private fun UpgradeKeptDialog(kept: List<Pair<org.omarchy.music.Track, String>>) {
    val close = { Music.upgradeKept = null }
    val n = kept.size
    AlertDialog(onDismissRequest = close, containerColor = C.light,
        title = { Text("$n song${if (n != 1) "s" else ""} kept as ${if (n != 1) "they are" else "it is"}", style = T.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(if (n != 1) "Yours are as good as Tidal's or better, so they weren't replaced:" else "Yours is as good as Tidal's or better, so it wasn't replaced:",
                    style = T.body)
                kept.take(8).forEach { (t, why) -> Text("• ${t.title} — ${t.artist}\n   $why", style = T.sub, modifier = Modifier.padding(top = 4.dp)) }
                if (n > 8) Text("…and ${n - 8} more", style = T.sub)
                Text("Replace them with Tidal's anyway? The new copies may be lower quality than yours (yours go to $TRASH).",
                    style = T.body, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton({ close(); Music.upgradeSongs(kept.map { it.first }, force = true) }) { Text("Replace anyway", color = C.red) } },
        dismissButton = { TextButton(close) { Text("Keep mine", color = C.muted) } })
}

/** Before downloading songs you may have: which ones, and what you have of them. */
@Composable
private fun DupeDownloadDialog(new: List<org.omarchy.music.Track>, dupes: List<Pair<org.omarchy.music.Track, List<org.omarchy.music.Track>>>) {
    val n = dupes.size
    AlertDialog(onDismissRequest = { Music.answerDupes("cancel") }, containerColor = C.light,
        title = { Text(if (n == 1) "You may have this song already" else "You may have $n of these songs already", style = T.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                dupes.take(6).forEach { (t, copies) ->
                    Text("“${t.title}” — ${t.artist}", style = T.body, modifier = Modifier.padding(top = 6.dp))
                    Text("you have:", style = T.sub)
                    copies.take(3).forEach { c ->
                        Text("• ${c.title} — ${c.artist}" + (if (c.album.isNotEmpty()) " (${c.album})" else "") +
                            "\n   " + listOf(Music.copyInfo(c), org.omarchy.music.fmtTime(c.duration), Music.whereIs(c)).filter { it.isNotEmpty() }.joinToString(" · "),
                            style = T.sub)
                    }
                }
                if (n > 6) Text("…and ${n - 6} more", style = T.sub)
                Text("These look like the same song (the name, artist or album match closely). Download anyway?", style = T.body,
                    modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton({ Music.answerDupes("replace") }) { Text("Download and replace", color = C.accent) }
                TextButton({ Music.answerDupes("keep") }) { Text("Download, keep both", color = C.fg) }
                if (new.size > n) TextButton({ Music.answerDupes("skip") }) { Text("Only the new ones", color = C.fg) }
                TextButton({ Music.answerDupes("cancel") }) { Text("Cancel", color = C.muted) }
            }
        })
}

/** Your files Android is asked to put in its trash (Upgrade, Replace, Duplicates): it asks you, then they go. */
@Composable
private fun Trasher() {
    var asked by remember { mutableStateOf<Music.TrashJob?>(null) }
    val ask = rememberFileRemoval(trash = true) { ok ->
        val job = asked
        asked = null
        if (job != null) Music.trashed(job, ok)
    }
    val job = Music.trashJob
    LaunchedEffect(job) {
        if (job == null || asked != null) return@LaunchedEffect
        val uris = job.tracks.mapNotNull { Music.phoneFile(it) }
        if (uris.isEmpty() || !canRemoveFiles) Music.trashed(job, false)
        else { asked = job; ask(uris) }
    }
}

/** Export: a playlist as an .m3u8 file, where you pick. */
@Composable
private fun Exporter(ui: Ui) {
    var pending by remember { mutableStateOf<List<org.omarchy.music.Track>?>(null) }
    val save = rememberSaveFile("audio/x-mpegurl") { uri ->
        val t = pending
        pending = null
        if (uri != null && t != null) Music.exportM3u8(uri, t)
    }
    val job = ui.exportFor
    LaunchedEffect(job) {
        if (job == null) return@LaunchedEffect
        ui.exportFor = null
        pending = job.second
        save.launch(org.omarchy.music.pathPart(job.first, "Playlist") + ".m3u8")
    }
}

/** Delete: are you sure? Downloads go at once; the phone's own songs (other apps' files) only once
 *  Android asks too and you say yes there. */
@Composable
private fun DeleteDialog(ui: Ui) {
    var phone by remember { mutableStateOf<List<org.omarchy.music.Track>>(emptyList()) }
    val ask = rememberFileRemoval(trash = false) { ok ->
        if (ok) Music.phoneFilesDeleted(phone)
        phone = emptyList()
    }
    val tracks = ui.deleteFor ?: return
    val downloads = tracks.filter { Music.lib.has(it.id) }
    val files = tracks.filter { Music.phoneFile(it) != null }
    val n = downloads.size + files.size
    val close = { ui.deleteFor = null }
    AlertDialog(onDismissRequest = close, containerColor = C.light,
        title = { Text(if (n == 1) "Delete “${(downloads + files)[0].title}”?" else "Delete $n songs?", style = T.title,
            maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Text(listOfNotNull(
                if (downloads.isNotEmpty()) "${if (n == 1) "Its" else "${downloads.size}"} downloaded FLAC${if (downloads.size > 1) "s" else ""} will be deleted for good (you can download them again)." else null,
                if (files.isNotEmpty()) "${if (n == 1) "Its file" else "${files.size} files"} on the $DEVICE will be deleted" + if (onPhone) ": Android asks you once more." else "." else null,
                (tracks.size - n).takeIf { it > 0 }?.let { left ->
                    if (left == 1) "1 of the songs you picked has no file here: it stays." else "$left of the songs you picked have no file here: they stay."
                },
            ).joinToString("\n\n"), style = T.body)
        },
        confirmButton = {
            TextButton({
                close(); ui.afterOption()
                Music.deleteDownloads(downloads)
                val playing = Music.current()?.id
                val todo = files.filter { it.id != playing }
                if (todo.size < files.size) Music.toast("The playing song is left as it is: skip it first")
                if (todo.isNotEmpty() && canRemoveFiles) {
                    phone = todo
                    ask(todo.mapNotNull { Music.phoneFile(it) })
                }
            }, enabled = n > 0) { Text("Delete", color = C.red) }
        },
        dismissButton = { TextButton(close) { Text("Cancel", color = C.muted) } })
}

/** Edit tags…: the downloaded FLACs' tags. With several songs, a field they disagree on is left
 *  as it is in each unless you type in it. */
@Composable
private fun TagsDialog(ui: Ui, tracks: List<org.omarchy.music.Track>) {
    val songs = remember(tracks) { tracks.filter { org.omarchy.music.Tags.editable(it) != null } }
    var have by remember { mutableStateOf<Map<String, String?>?>(null) }
    val typed = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    LaunchedEffect(songs) {
        Music.bg({ org.omarchy.music.Tags.read(Music.ctx, songs.mapNotNull { org.omarchy.music.Tags.editable(it) }) }) { h, _ -> have = h ?: emptyMap() }
    }
    val close = { ui.editTagsFor = null }
    AlertDialog(onDismissRequest = close, containerColor = C.light,
        title = { Text(if (songs.size == 1) "Edit tags" else "Edit tags · ${songs.size} songs", style = T.title) },
        text = {
            val h = have
            if (h == null) Text("Reading the tags…", style = T.sub)
            else LazyColumn {
                if (songs.size < tracks.size) item {
                    Text("Only songs downloaded here can be edited: ${tracks.size - songs.size} of them are left out.", style = T.sub,
                        modifier = Modifier.padding(bottom = 6.dp))
                }
                items(org.omarchy.music.Tags.FIELDS.size) { i ->
                    val (k, label) = org.omarchy.music.Tags.FIELDS[i]
                    Text(label, style = T.sub, modifier = Modifier.padding(top = 4.dp))
                    Field(typed[k] ?: h[k] ?: "", if (h.containsKey(k) && h[k] == null) "Several (left as they are)" else "") { typed[k] = it }
                }
            }
        },
        confirmButton = {
            TextButton({ close(); Music.editTags(songs, typed.toMap()); ui.afterOption() }, enabled = have != null) {
                Text("Save", color = C.accent)
            }
        },
        dismissButton = { TextButton(close) { Text("Cancel", color = C.muted) } })
}

@Composable
private fun NameDialog(title: String, start: String, dismiss: () -> Unit, done: (String) -> Unit) {
    var name by remember { mutableStateOf(start) }
    AlertDialog(onDismissRequest = dismiss, containerColor = C.light, title = { Text(title, style = T.title) },
        text = { Field(name, "Name") { name = it } },
        confirmButton = { TextButton({ if (name.isNotBlank()) done(name.trim()) }) { Text("OK", color = C.accent) } },
        dismissButton = { TextButton(dismiss) { Text("Cancel", color = C.muted) } })
}

