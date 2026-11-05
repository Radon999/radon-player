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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.layout.size
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
        "lookmaker" -> LookMaker(ui, ui.makerEdit)
        "layoutmaker" -> LayoutMaker(ui, ui.makerEdit)
        "vismaker" -> VisMaker(ui, ui.makerEdit)
        "wrapmaker" -> WrapMaker(ui, ui.makerEdit)
        "cardmaker" -> CardMaker(ui, ui.makerEdit)
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
        "deezer-login" -> WebLogin("https://www.deezer.com/login", { Services.deezerCookiesReady() }, desktop = true) { url ->
            ui.sheet = "accounts"; if (url != null) Services.deezerSignedIn()
        }
        "spotify-login" -> WebLogin(Services.spotifyLoginUrl(), { it.startsWith(org.omarchy.music.Spotify.REDIRECT) }) { url ->
            ui.settingsOpen += "Accounts"; ui.sheet = "accounts"; if (url != null) Services.spotifyFinish(url)
        }
        "soundcloud-login" -> {
            val loginAt = remember { (Music.sources[org.omarchy.music.SoundCloud.KEY] as? org.omarchy.music.SoundCloud)?.loginUrl() ?: "about:blank" }
            WebLogin(loginAt, { it.startsWith(org.omarchy.music.SoundCloud.REDIRECT) }) { url ->
                ui.settingsOpen += "Accounts"; ui.sheet = "accounts"; if (url != null) org.omarchy.music.SoundCloud.finish(url)
            }
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
    if (ui.changelog) GuideScreen(ui, changelog = true)
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
    ui.songEqFor?.let { tracks -> if (tracks.isNotEmpty()) SongEqDialog(ui, tracks) else ui.songEqFor = null }
    ui.upgradeFor?.let { tracks -> UpgradeDialog(ui, tracks) }
    Music.upgradeKept?.let { kept -> UpgradeKeptDialog(kept) }
    Music.dupePrompt?.let { (new, dupes) -> DupeDownloadDialog(new, dupes) }
    Trasher()
    Exporter(ui)
    ui.askName?.let { ask -> androidx.compose.runtime.key(ask) { val (title, start, done) = ask
        NameDialog(title, start, { ui.askName = null }) { ui.askName = null; done(it) } } }
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
                    Pill("Clear", quiet = true) { ui.confirm = "Clear the crash reports?" to { org.omarchy.music.Crashes.clear() } }
                }
            }
            if (onPhone) NotificationsLine(activity)
            if (org.omarchy.music.Startup.supported) StartupLine()
            }
            Group("Personalization", ui) {
            PersonalizationLines(ui)
            }
            Group("Playback", ui) {
            PlaybackLines(ui)
            }
            Group("Backup", ui) {
            BackupLines(activity)
            }
            Group("Remote & sync", ui) {
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
            SyncLines(ui)
            SyncIgnoreLines()
            if (org.omarchy.music.Discord.supported) DiscordLine()
            }
            Group("Accounts", ui) {
            Text("Radon Player works best with a Tidal subscription and your own song files. Tidal is the only way to stream " +
                "or download in FLAC (lossless) here, and with it everything plays and downloads in the best quality in one place.",
                style = T.body.copy(color = C.accent), modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
            Text("Signing in to Spotify, Apple Music, Deezer or YouTube Music is purely for bringing your data over: your " +
                "library, playlists, favorites and listening info. It doesn't bring their sound, which they lock to their own " +
                "apps: their songs play from Tidal when it's connected, and without Tidal from YouTube at lower quality (about " +
                "128 to 160 kbps, never FLAC), even if you pay for them.\n\n" +
                "Thinking of switching to Tidal? You can bring everything with you first, safely, without losing anything:\n" +
                "• Playlists: sign in to your old service below, open its library, and pick Import all into my playlists (or " +
                "$LONG_PRESS one playlist and pick Import to my playlists). They become your own playlists here and stay after " +
                "your old subscription ends; with Tidal they play in FLAC, and you can download a whole playlist in one go.\n" +
                "• Listening history: import your plays from Spotify, Apple Music, Deezer, YouTube Music or Tidal (under Last.fm & " +
                "your stats) so Most played, Artists and Wrapped keep counting from where you were.\n" +
                "• Songs from Spotify, Apple Music, Deezer or YouTube Music as files: DumpMedia, a paid app for your computer " +
                "(see Downloading from Spotify, Apple Music and others, in Your music), saves them as files you can put in your " +
                "music folders to play here as your own songs. A file can't sound better than what the service streams.\n" +
                "Stream and download quality are in Your music.",
                style = T.sub, modifier = Modifier.padding(bottom = 8.dp))
            ImportNote("playlists")   // how the last playlist import went
            // ---- services ----
            for (key in listOf("ytm", "apple", "deezer")) {
                val src = Music.sources[key]
                if (src?.signedIn == true) Line(SOURCES[key]!!, "Signed in: your library and playlists from here are in the app, and their songs play from Tidal in " +
                    "FLAC where it has them (from YouTube where it hasn't, or without Tidal). With Tidal connected, Sync downloads every song in your favorites and playlists" +
                    if (key == "deezer") ". Deezer's own audio is locked to Deezer's apps, so the songs come from Tidal or YouTube" else "", true) {
                    SyncSwitch(key); Spacer(Modifier.width(6.dp)); Pill("Sign out", quiet = true) { Services.signOut(key) }
                } else Line(SOURCES[key]!!, "Sign in to bring over your library, favorites and playlists from this service " +
                    "(for your data only: its songs play from Tidal in FLAC where it has them, so connect Tidal too)", false) {
                    Pill("Sign in") { ui.sheet = "$key-login" }
                }
            }
            if (Music.tidal.connected) {
                Line("Tidal", "Connected. Songs play in FLAC (lossless), hi-res where Tidal has it, and fall back to " +
                    "lossy only where Tidal has no FLAC. Turn on Sync to download every song in your Tidal favorites and playlists", true) {
                    SyncSwitch("tidal"); Spacer(Modifier.width(6.dp)); Pill("Disconnect", quiet = true) { Music.tidalDisconnect() }
                }
            } else Line("Tidal", "Connect your Tidal account (it needs a paid Tidal subscription) and songs from every " +
                "service play in FLAC, hi-res where Tidal has it. Tidal's own favorites, playlists, search and radio show up too, and " +
                "downloads and Upgrade come from it", false) {
                Pill("Connect") { ui.sheet = "tidal-login" }
            }
            SpotifyLine(ui)
            SoundCloudLine(ui)
            }
            Group("Your music", ui) {
            // ---- your music ----
            val n = Music.local.files.size
            Line("Folders", state = if (Music.local.scanning) "Reading your music folders for songs…" else "$n song${if (n != 1) "s" else ""} found in the folders below, shown in My music. " +
                "Add the folders where your music files are kept and the app reads them in; Remove only stops reading a folder, it deletes nothing", ok = n > 0) {
                FolderPicker()
            }
            Music.folders.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f, style = T.sub, modifier = Modifier.weight(1f))
                    Pill("Remove", quiet = true) { ui.confirm = "Stop reading “$f”? Its songs leave My music; the files stay where they are." to { Music.removeFolder(f) } }
                }
            }
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
            Line("Queues", "On: each folder, album, artist and playlist you play from gets its own queue, kept where " +
                "you left it, so you can go back to any of them later, rename them or reorder them. Off: there's one queue, and " +
                "playing something new replaces it",
                Music.multiQueue) { Switch(Music.multiQueue, { Music.setMultiQueue(it) }, colors = swColors()) }
            Line("Artist playlists include songs you don't have", "Create artist playlist (on an artist's page, or " +
                "$LONG_PRESS an artist) makes a playlist of every song of theirs you have. On: it also gets their songs from the " +
                "streaming service, their top songs and every album's", false) {
                var on by remember { mutableStateOf(org.omarchy.music.Prefs.on(Music.ARTIST_LISTS_ALL)) }
                Switch(on, { on = it; org.omarchy.music.Prefs.set(Music.ARTIST_LISTS_ALL, it) }, colors = swColors())
            }
            StreamQualityLine()
            DownloadQualityLine()
            OtherServicesLine()
            Line("Downloads", "Songs you download are saved to ${Music.downloadFolder()} with full tags, sorted " +
                "into Artist / Albums, EPs or Singles / Album folders. Change picks one of your music folders or a new folder in Music/" +
                if (onPhone) "" else ", or any other folder on this PC", true) {
                Box {
                    var open by remember { mutableStateOf(false) }
                    val other = rememberFolderPick { path -> if (path != null) Music.chooseDownloadDir(path) }
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
                        if (other != null) androidx.compose.material3.DropdownMenuItem({ Text("Another folder…", style = T.body) }, {
                            open = false; other()
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
            Group("Scanner", ui) {
                Text("Scans that find and fix problems in your music. Run one when something looks wrong; nothing changes until you say so.",
                    style = T.sub, modifier = Modifier.padding(vertical = 8.dp))
                ProtectLines()
                SubGroup("Your files", ui) { ToolLines(ui) }
                SubGroup("Lyrics & moods", ui) { LyricsMoodsLines(ui) }
                SubGroup("Last.fm & your stats", ui) {
            run {
                val undecided = remember(Music.artistPicks, org.omarchy.music.Stats.songs) { jointCreditsNow().count { it.third == null } }
                Line("Similar artists", "Finds artists that look like the same one written two ways (cavetown offline and " +
                    "Cavetown), which splits their plays in your stats. You choose to merge them, their songs' plays and all, or keep them apart", false) { Pill("Scan…", quiet = true) { ui.similarArtists = true } }
                Line("Duplicates in Most played", "Finds songs counted twice in your stats under different names (\"N.P.D\" and " +
                    "\"NPD\"), and versions of one song (acoustic, live, remix). You pick which to merge into one, their plays added " +
                    "together, and which to leave alone", false) { Pill("Scan…", quiet = true) { ui.duplicates = true } }
                Line("Joint artists", "A duet's plays go to its main artist (Quinn XCII & Jeremy Zucker counts for Quinn XCII), " +
                    "so your top artists aren't split up. " +
                    if (undecided > 0) "$undecided pairs aren't clear: pick whose plays each one's are" else "Choose… lets you change whose plays a pair's are",
                    undecided == 0) { Pill("Choose…", quiet = undecided == 0) { ui.jointArtists = true } }
            }
                }
                SubGroup("Never show", ui) {
            run {   // Never show, undone for every scan at once
                org.omarchy.music.ScanIgnore.version
                val hid = org.omarchy.music.ScanIgnore.total()
                Line("Reset Never show", if (hid == 0) "Nothing is hidden from the scans. What you mark Never show in a scan is kept " +
                    "here, so that scan leaves it alone for good" else "$hid thing${if (hid != 1) "s are" else " is"} hidden from the scans (Never show). " +
                    "Reset brings them all back, in every scan", false) {
                    if (hid > 0) Pill("Reset", quiet = true) {
                        ui.confirm = "Show everything you marked Never show in the scans again?" to { org.omarchy.music.ScanIgnore.clearAll(); Music.toast("Every scan shows everything again") }
                    }
                }
            }
                }
            }
            Group("Sound quality", ui) {
            SoundQuality(ui)
            }
            Group("Last.fm & your stats", ui) {
            Text("Important: don't import an app's data if you used Last.fm with that app", style = T.body.copy(color = C.red,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
            Text("For instance, if you used Spotify with Last.fm scrobbling at the same time, only connect Last.fm: importing your " +
                "Spotify data too would count those plays twice.\n\n" +
                "Import the data of any app you didn't use Last.fm with, then add Last.fm on top. That won't duplicate anything: " +
                "Last.fm's scrobbles add on to whatever you import.\n\n" +
                "If you imported an app's data by mistake (Spotify's, say, when you used Last.fm with it), Reset plays at the bottom " +
                "of this section clears every imported play, so you can import again the right way alongside Last.fm.\n\n" +
                "Songs you play in this app are never counted twice: while Last.fm is connected, your stats use only Last.fm's " +
                "scrobbles. The app keeps its own count of your plays just as a fallback, used only when Last.fm isn't connected.",
                style = T.sub, modifier = Modifier.padding(bottom = 8.dp))
            LastFmLine(ui)
            TimeChoiceLine()
            SpotifyHistoryLine(ui)
            AppleHistoryLine()
            DeezerHistoryLine()
            ImportLine(org.omarchy.music.YouTubeHistory, "https://takeout.google.com",
                "Bring your YouTube Music listening into Most played, Artists and Wrapped, added alongside your Last.fm scrobbles " +
                    "(importing it again never adds a play twice). To get the file: tap Get the file (takeout.google.com) and sign in, tap Deselect all, " +
                    "tick \"YouTube and YouTube Music\", tap \"All YouTube data included\" and leave only history ticked, tap \"Multiple formats\" " +
                    "and set history to JSON, then Next step › Create export. Google emails you the zip (it can take a while): Import it " +
                    "here (or the watch-history.json inside). Only songs played in YouTube Music are counted, not videos",
                arrayOf("application/zip", "application/json", "application/octet-stream"))
            ImportLine(org.omarchy.music.TidalHistory, "https://tidal.com/privacy",
                "Bring your Tidal listening into Most played, Artists and Wrapped, added alongside your Last.fm scrobbles (importing " +
                    "it again never adds a play twice). To get the file: ask Tidal for a copy of your personal data (Get the file shows how: a " +
                    "data request, from Tidal's privacy page or support). Tidal emails it to you; Import the zip here, or the streaming " +
                    "history file in it (CSV or JSON)",
                arrayOf("application/zip", "text/csv", "text/comma-separated-values", "application/json", "text/plain", "application/octet-stream"))
            Line("Reset plays", "Clears every play you imported (from Spotify, Apple Music, Deezer, YouTube Music and Tidal), for " +
                "when an import doubled your plays because you used Last.fm with that app too. Your Last.fm scrobbles stay (they're " +
                "kept on Last.fm), and you can import again afterwards", false) {
                Pill("Reset plays", quiet = true) {
                    ui.confirm = ("Reset your plays? Every play imported from Spotify, Apple Music, Deezer, YouTube Music and Tidal is " +
                        "cleared from Most played, Artists and Wrapped. Your Last.fm scrobbles stay. You can import again afterwards.") to {
                        Music.bg({ Music.resetImportedPlays() }) { _, err ->
                            Music.toast(if (err != null) "Couldn't reset: ${err.message?.take(80)}" else "Imported plays cleared")
                        }
                    }
                }
            }
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

/** SoundCloud: its own service here. Its songs stream from SoundCloud (AAC, about 160 kbps) and never mix with the others. */
@Composable
private fun SoundCloudLine(ui: Ui) {
    val sc = Music.sources[org.omarchy.music.SoundCloud.KEY] as? org.omarchy.music.SoundCloud
    Text("Mixing services: songs from Tidal, Spotify, Apple Music, Deezer and YouTube Music can all go together with your own " +
        "offline music in the same playlists and queues, since they all play through Tidal (or YouTube) and your files.\n\n" +
        "SoundCloud is different: it's a service of its own in the app. Its songs stream straight from SoundCloud (AAC, about " +
        "160 kbps, close to YouTube's quality), but SoundCloud's rules for apps like this one mean they can't be downloaded or " +
        "kept offline, and can't be mixed with other services or your own music: they play in a SoundCloud queue of their " +
        "own, and can't go in your playlists or favorites (like them on SoundCloud instead).",
        style = T.sub, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    when {
        sc == null -> Line("SoundCloud", "Starting…", false) {}
        sc.signedIn -> Line("SoundCloud", "Connected" + (if (sc.user.isNotEmpty()) " as ${sc.user}" else "") + ": search, your likes " +
            "and playlists and artists are under SoundCloud (☰ › the service chips), streaming from SoundCloud", true) {
            Pill("Disconnect", quiet = true) { org.omarchy.music.SoundCloud.signOut() }
        }
        else -> {
            var cid by remember { mutableStateOf(sc.clientId) }
            var secret by remember { mutableStateOf("") }
            Line("SoundCloud", if (sc.ready) "Your SoundCloud app is set up: search works. Sign in to see your likes and playlists" else
                "SoundCloud needs a free app of your own: tap Make an app, sign in to SoundCloud, register an app with the " +
                "redirect URI below, then paste its Client ID and Client secret here and tap Connect. Redirect URI:", sc.ready) {}
            Text(org.omarchy.music.SoundCloud.REDIRECT, style = T.sub.copy(color = C.fg), modifier = Modifier.padding(bottom = 4.dp))
            Field(cid, "Client ID") { cid = it }
            Field(secret, if (sc.ready) "Client secret (kept: type it only to change it)" else "Client secret", password = true) { secret = it }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Pill("Make an app", quiet = true) { org.omarchy.music.Services.open("https://soundcloud.com/you/apps") }
                Pill(if (sc.ready) "Sign in" else "Connect") {
                    if (secret.isBlank() && sc.ready && cid.trim() == sc.clientId) ui.sheet = "soundcloud-login"
                    else if (sc.setClient(cid, secret)) { Music.sourceReady(org.omarchy.music.SoundCloud.KEY, sc, null); ui.sheet = "soundcloud-login" }
                    else Music.toast("Paste both your app's Client ID and its Client secret")
                }
            }
        }
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
            if (err != null) { org.omarchy.music.ImportNotes.failed("spotify", err)
                Music.toast("Import failed: ${org.omarchy.music.ImportNotes.why(err).take(300)}") }
            else ("Added ${"%,d".format(n ?: 0)} Spotify plays to your Wrapped" + if (n == 0) " (you had them all)" else "").let { org.omarchy.music.ImportNotes.ok("spotify", it); Music.toast("Import worked: $it") }
        }
    }
    val state = h.busy ?: if (h.count > 0) "${"%,d".format(h.count)} Spotify plays added alongside your Last.fm scrobbles in your stats " +
        "and Wrapped. Importing the zip again only adds plays you didn't have yet"
        else "Bring back years of Spotify listening: its plays are added alongside your Last.fm scrobbles in " +
            "your stats and Wrapped. The zip is the data download Spotify gives you: tap Get the zip, ask " +
            "for your data on Spotify's privacy page (the extended streaming history goes furthest back), then Import zip when it " +
            "comes. A play counts from 30 seconds, as Spotify counts streams; podcasts and videos are left out" +
            if (sp?.signedIn == true) ". As Spotify is connected, your latest plays also come in each time the app starts " +
                "(Spotify only keeps the last 50), and Sync fetches them now"
            else ". Connect Spotify under Accounts and your latest plays come in on their own too"
    Line("Spotify history", state, h.count > 0) {}
    ImportNote("spotify")
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Pill("Get the zip", quiet = true) { Services.open("https://www.spotify.com/account/privacy/") }
        if (sp?.signedIn == true) Pill("Sync", quiet = true) {
            Music.bg({ h.sync() }) { n, err ->
                Music.toast(if (err != null) "Couldn't read your Spotify plays: ${err.message?.take(100)}"
                    else "${"%,d".format(n ?: 0)} new Spotify play${if (n == 1) "" else "s"}")
            }
        }
        if (h.count > 0) Pill("Clear", quiet = true) { asking = true }
        Pill("Import zip") { if (h.busy == null) pick.launch(arrayOf("*/*")) }
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
            if (err != null) { org.omarchy.music.ImportNotes.failed("apple", err)
                Music.toast("Import failed: ${org.omarchy.music.ImportNotes.why(err).take(300)}") }
            else ("Added ${"%,d".format(n ?: 0)} Apple Music plays" + if (n == 0) " (you had them all)" else "").let { org.omarchy.music.ImportNotes.ok("apple", it); Music.toast("Import worked: $it") }
            if (err == null) org.omarchy.music.Stats.refresh(force = true)
        }
    }
    val state = h.busy ?: if (h.count > 0) "${"%,d".format(h.count)} Apple Music plays added alongside your Last.fm scrobbles in " +
        "Most played, Artists and Wrapped. Importing again only adds plays you didn't have yet"
        else "Bring your Apple Music listening into Most played, Artists and Wrapped, added alongside your " +
            "Last.fm scrobbles (importing it again never adds a play twice). Tap Get the zip and ask Apple's privacy page for your data (Get a copy " +
            "of your data › Apple Media Services), then Import the zip when it comes. Or import a library exported from the Music " +
            "app on a computer (File › Library › Export Library…)"
    Line("Apple Music history", state, h.count > 0) {}
    ImportNote("apple")
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Pill("Get the zip", quiet = true) { Services.open("https://privacy.apple.com/") }
        if (h.count > 0) Pill("Clear", quiet = true) { asking = true }
        Pill("Import") { if (h.busy == null) pick.launch(arrayOf("*/*")) }
    }
    if (asking) AlertDialog(onDismissRequest = { asking = false }, containerColor = C.light,
        title = { Text("Clear your Apple Music history?", style = T.title) },
        text = { Text("Its plays leave Most played, Artists and Wrapped. They come back if you import it again.", style = T.sub) },
        confirmButton = { TextButton({ asking = false; Music.bg({ h.clear(); org.omarchy.music.Stats.refresh(force = true) }) }) { Text("Clear", color = C.accent) } },
        dismissButton = { TextButton({ asking = false }) { Text("Cancel", color = C.muted) } })
}

/** Your Deezer plays for Most played, Artists and Wrapped: the .xlsx from Deezer's privacy settings (My personal data). */
/** An import of plays from a service's own data file (YouTube Music's, Tidal's): how to get it, Import, Clear. */
@Composable
private fun ImportLine(h: org.omarchy.music.PlaysImport, getAt: String, howTo: String, types: Array<String>) {
    var asking by remember { mutableStateOf(false) }
    val pick = rememberOpenFile { uri ->
        if (uri != null) Music.bg({ h.importFile(Music.ctx, uri) }) { n, err ->
            if (err != null) { org.omarchy.music.ImportNotes.failed(h.label, err)
                Music.toast("Import failed: ${org.omarchy.music.ImportNotes.why(err).take(300)}") }
            else ("Added ${"%,d".format(n ?: 0)} ${h.label} plays" + if (n == 0) " (you had them all)" else "").let { org.omarchy.music.ImportNotes.ok(h.label, it); Music.toast("Import worked: $it") }
            if (err == null) org.omarchy.music.Stats.refresh(force = true)
        }
    }
    val state = h.busy ?: if (h.count > 0) "${"%,d".format(h.count)} ${h.label} plays added alongside your Last.fm scrobbles in Most " +
        "played, Artists and Wrapped. Importing again only adds plays you didn't have yet" else howTo
    Line("${h.label} history", state, h.count > 0) {}
    ImportNote(h.label)
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Pill("Get the file", quiet = true) { Services.open(getAt) }
        if (h.count > 0) Pill("Clear", quiet = true) { asking = true }
        Pill("Import") { if (h.busy == null) pick.launch(types) }
    }
    if (asking) AlertDialog(onDismissRequest = { asking = false }, containerColor = C.light,
        title = { Text("Clear your ${h.label} history?", style = T.title) },
        text = { Text("Its plays leave Most played, Artists and Wrapped. They come back if you import it again.", style = T.sub) },
        confirmButton = { TextButton({ asking = false; Music.bg({ h.clear(); org.omarchy.music.Stats.refresh(force = true) }) }) { Text("Clear", color = C.accent) } },
        dismissButton = { TextButton({ asking = false }) { Text("Cancel", color = C.muted) } })
}

@Composable
private fun DeezerHistoryLine() {
    val h = org.omarchy.music.DeezerHistory
    var asking by remember { mutableStateOf(false) }
    val pick = rememberOpenFile { uri ->
        if (uri != null) Music.bg({ h.importFile(Music.ctx, uri) }) { n, err ->
            if (err != null) { org.omarchy.music.ImportNotes.failed("deezer", err)
                Music.toast("Import failed: ${org.omarchy.music.ImportNotes.why(err).take(300)}") }
            else ("Added ${"%,d".format(n ?: 0)} Deezer plays" + if (n == 0) " (you had them all)" else "").let { org.omarchy.music.ImportNotes.ok("deezer", it); Music.toast("Import worked: $it") }
            if (err == null) org.omarchy.music.Stats.refresh(force = true)
        }
    }
    val state = h.busy ?: if (h.count > 0) "${"%,d".format(h.count)} Deezer plays added alongside your Last.fm scrobbles in Most " +
        "played, Artists and Wrapped. Importing again only adds plays you didn't have yet"
        else "Bring your Deezer listening into Most played, Artists and Wrapped, added alongside your " +
            "Last.fm scrobbles (importing it again never adds a play twice). Tap Get the file and download your data from Deezer's privacy settings " +
            "(My account › Privacy settings › My personal data), then Import the .xlsx spreadsheet with your listening history"
    Line("Deezer history", state, h.count > 0) {}
    ImportNote("deezer")
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        Pill("Get the file", quiet = true) { Services.open("https://www.deezer.com/account/privacy") }
        if (h.count > 0) Pill("Clear", quiet = true) { asking = true }
        // Any file: phones label Deezer's spreadsheet in different ways (from Drive, Gmail, Files), and a type it
        // didn't list was greyed out, so tapping it did nothing; the file is checked once it's picked
        Pill("Import") { if (h.busy == null) pick.launch(arrayOf("*/*")) }
    }
    if (asking) AlertDialog(onDismissRequest = { asking = false }, containerColor = C.light,
        title = { Text("Clear your Deezer history?", style = T.title) },
        text = { Text("Its plays leave Most played, Artists and Wrapped. They come back if you import it again.", style = T.sub) },
        confirmButton = { TextButton({ asking = false; Music.bg({ h.clear(); org.omarchy.music.Stats.refresh(force = true) }) }) { Text("Clear", color = C.accent) } },
        dismissButton = { TextButton({ asking = false }) { Text("Cancel", color = C.muted) } })
}

/** How the last import of this kind went: it worked and what it added, or it failed and why (kept till the next). */
@Composable
private fun ImportNote(kind: String) {
    val (ok, at, text) = org.omarchy.music.ImportNotes.last(kind) ?: return
    val ago = org.omarchy.music.Sync.ago(at.toLong())
    Text((if (ok) "\u2713 Last import worked ($ago): " else "\u2717 Last import failed ($ago): ") + text,
        style = T.sub.copy(color = if (ok) C.green else C.red), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
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

/** Settings › Scanner, at the top: Prevent auto mood scans and Prevent album cover changes. */
@Composable
private fun ProtectLines() {
    val P = org.omarchy.music.Prefs
    var moods by remember { mutableStateOf(P.on(org.omarchy.music.Moods.PREVENT)) }
    Line("Prevent auto mood scans", "On: the moods stop being edited. Every mood already in your files stays exactly as " +
        "it is and still shows, but no song is scanned (a new song you add waits) and nothing more is written into any " +
        "file's moods, nor taken from your other device. Turn it off to scan again", moods) {
        Switch(moods, { moods = it; P.set(org.omarchy.music.Moods.PREVENT, it); org.omarchy.music.Moods.version++ }, colors = swColors())
    }
    var covers by remember { mutableStateOf(P.on(org.omarchy.music.CoverGuard.PREVENT)) }
    Line("Prevent album cover changes", "On: no sync, scan or auto tagger changes the cover inside a song's file, and none " +
        "is put in or taken off. Only a cover you pick yourself (the Tagger's Cover) changes it. A scan that could change " +
        "covers says so while this is on", covers) {
        Switch(covers, { covers = it; P.set(org.omarchy.music.CoverGuard.PREVENT, it) }, colors = swColors())
    }
}

/** Settings › Your music: the tools that fix your songs' files (on the phone, the app's own downloads). */
@Composable
private fun ToolLines(ui: Ui) {
    // The file scans are the PC's only (they didn't work well on the phone); Songs without covers is on both.
    if (!onPhone) Line("Fix albums and artists", "Finds albums split up by featured artists (“Quinn XCII & Jeremy Zucker”), albums sitting " +
        "beside their own deluxe, extended or anniversary edition, and singles of songs that are on an album you have. They're " +
        "fixed in the files' tags so they group properly (song titles are never changed)" + (if (org.omarchy.music.FileTags.canMove) ", and the files can then be moved to the right folders if you like" else "") +
        ". You check each change before it's made" + (if (onPhone) ". On the phone it works on the songs the app downloaded" else ""), false) {
        Pill("Scan…", quiet = true) { ui.tool = "fixup" }
    }
    val n = org.omarchy.music.MusicTools.skip.size
    if (!onPhone) Line("Folders to leave alone", "Folders that no scan here ever touches or even looks in (Fix albums and artists, Soundtrack notes in names, " +
        "Empty folders, Live in song names): their songs are never retagged or moved, and no songs are moved into them. " +
        "Handy for unreleased songs, sessions and anything you've arranged by hand. " +
        if (n > 0) "$n folder${if (n != 1) "s" else ""} left alone now" else "None chosen yet", false) {
        Pill("Choose…", quiet = true) { ui.tool = "skip" }
    }
    if (!onPhone && org.omarchy.music.FileTags.canListFolders) Line("Empty folders", "Finds folders in your music folders that have no songs left " +
        "in them, only pictures or lyrics files, often left behind after songs are moved. You see each one, its size and what's " +
        "in it, and pick which to delete", false) {
        Pill("Clean up…", quiet = true) { ui.tool = "empty" }
    }
    if (!onPhone) Line("Live in song names", "Finds songs named as live recordings (“Song (Live)”, “Song - Live at…”) that you'd rather " +
        "have under their plain name. It takes the live part off the name and keeps the rest, and you check each one first", false) {
        Pill("Scan…", quiet = true) { ui.tool = "live" }
    }
    if (!onPhone) Line("Soundtrack notes in names", "Finds songs whose names carry a note like “(From the Original Motion Picture …)”, " +
        "“[From “Barbie”]” or “- From the Netflix Series …” and takes it off, keeping the rest. You choose each one", false) {
        Pill("Scan…", quiet = true) { ui.tool = "movie" }
    }
    Line("Fix genre names", "Finds genres spelt more than one way (“Hip-Hop/Rap” and “Hip-Hop-Rap”, “R&B” and “R and B”) " +
        "and puts their songs under one name, written into the files' tags. You pick the name for each, or type your own" +
        (if (onPhone) ". On the phone it works on the songs the app downloaded" else ""), false) {
        Pill("Scan…", quiet = true) { ui.tool = "genres" }
    }
    Line("Songs without covers", "Finds the albums whose songs show no cover anywhere, so you can pick one for each " +
        "(searched for you, or a picture of your own)", false) {
        Pill("Scan…", quiet = true) { ui.tool = "nocover" }
    }
    Line("Save covers into folders", "Saves one picture of each album, EP and single in its folder, beside its songs " +
        "(cover.jpg; a folder holding several releases gets one for each), from a song's own cover, so a cover lost from the " +
        "files can always be put back. A release with a picture there already is left as it is" +
        (if (onPhone) ". On the phone it needs All files access" else ""), false) {
        Pill("Scan…", quiet = true) { ui.tool = "savecovers" }
    }
    Line("Covers from folders", "Puts the cover picture in each song's folder (its album's, EP's or single's) into every song " +
        "there that has no cover in its file, so they all have it again. Songs with a cover already are left as they are", false) {
        Pill("Scan…", quiet = true) { ui.tool = "applycovers" }
    }
}

/** Scanner › Lyrics & moods: the lyrics scanner (lyrics into the files, then the songs it found none for), reading
 *  moods from songs' words, and the optional AI reading with your own Anthropic key. */
@Composable
private fun LyricsMoodsLines(ui: Ui) {
    val ls = org.omarchy.music.LyricsScan
    val moods = org.omarchy.music.Moods
    LaunchedEffect(ls.version) { ls.recount() }
    val c = ls.counts
    Line("Lyrics", "Finds every song's lyrics and writes them into its file (timed lines when there are some), so any player " +
        "shows them" + (if (onPhone) ". On the phone that's the songs the app downloaded; the others keep theirs in the app" else "") +
        ". Songs with lyrics in their file already are skipped; the ones with none found are listed after, to leave alone, " +
        "type in or search for" + (c?.let { ". Lyrics in files: %,d · kept in the app: %,d · still to find: %,d".format(it.inFiles, it.inApp, it.toFind) } ?: ""),
        ls.progress != null) {
        Pill(if (ls.progress != null) "Finding…" else "Show…", quiet = true) { ui.tool = "lyrics" }
    }
    Line("Moods from lyrics", "Scan moods (the Moods tab) reads each song's words as well as hearing its sound: on this " +
        "$DEVICE, reading whole lines and sayings (\"wash my blues away\" is relief, not sadness), or by Claude with an Anthropic " +
        "key saved. Both always count for every mood: the words weigh more for Sad, Romantic and Dark, the sound more for " +
        "Energetic, Party, Chill and Focus. Only once a song's sound and words are both done are its moods written into its file",
        moods.reading != null) {
        if (moods.reading != null) Pill("Stop", quiet = true) { moods.stop() } else Pill("Scan moods", quiet = true) { moods.scan() }
    }
    // Starting again: the moods worked out afresh from what was heard (quick), or everything forgotten to scan again
    Line("Reset mood and lyrics scans", "Moods look wrong after a sync, or songs only have one mood? Reset moods works every " +
        "song's moods out again from what the scan already heard and read (nothing heard again), dropping moods a sync, a " +
        "backup or another app brought; the next Scan moods writes them into your files without hearing anything again. Reset sound scan has every song heard again (the lyrics readings kept). Clear my moods forgets the ones you picked. Remove all moods takes every mood out of every file and forgets all the scans found (the sound, the lyrics readings) so Scan moods " +
        "starts again from the very beginning, your picked moods included. Every reset only ever " +
        "changes the MOOD tags: artist, album, title and all other tags stay as they are. Each asks before it does anything", false) {}
    // The buttons under it, not beside it (beside, they squeezed the words into a thin column)
    moods.removing?.let { (d, n) -> Text("Removing moods from your files… %,d of %,d".format(d, n), style = T.sub.copy(color = C.accent)) }
    run {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Reset moods", quiet = true) {
                Ui.current?.confirm = ("Are you sure? This works every song's moods out again from what the scans already heard and read. Moods " +
                    "from a sync, a backup or another app are dropped. Nothing is heard again; tap Scan moods on the Moods tab " +
                    "afterwards to write the new moods into your files. Continue?") to {
                    moods.resetAll(keepScans = true) { n -> Music.toast("Moods worked out again for %,d songs: Scan moods writes them into the files".format(n)) } }
            }
            Pill("Reset sound scan", quiet = true) {
                Ui.current?.confirm = ("Are you sure? This forgets what the sound scans heard for all your songs, so every song " +
                    "is heard again: run Scan moods on the Moods tab afterwards (it takes as long as the first time). The lyrics " +
                    "readings and moods you picked yourself stay. Continue?") to {
                    moods.reset(org.omarchy.music.Moods.RESET_SOUND) { n -> Music.toast("Sound scan reset for %,d songs: run Scan moods on the Moods tab".format(n)) } }
            }
            Pill("Clear my moods", quiet = true) {
                Ui.current?.confirm = ("Are you sure? This clears every mood you picked yourself in the Tagger, and what your " +
                    "fixes taught the scans, so those songs' moods are worked out from their sound and words like the rest. The " +
                    "next Scan moods rewrites only the MOOD tag of their files (nothing else in them changes). Continue?") to {
                    moods.clearPicked { n -> Music.toast("Your moods cleared from %,d songs".format(n)) } }
            }
            Pill("Remove all moods", quiet = true) {
                Ui.current?.confirm = ("Are you sure? This takes every mood off every song, for a completely fresh start: the MOOD " +
                    "tag and the words' reading (MOOD_LYRICS) are removed from all your files (only those two: the artist, album, " +
                    "title and every other tag stay exactly as they are), and everything the scans found and every mood you " +
                    "picked yourself is forgotten. Then run Scan moods on the Moods tab to give " +
                    "every song its moods again from its sound and words; that takes as long as the first time. Continue?") to {
                    moods.removeAll { changed, left, stuck ->
                        if (stuck == 0) Music.toast("All moods removed (%,d files changed%s): run Scan moods on the Moods tab"
                            .format(changed, if (left > 0) ", %,d this ${DEVICE} may not change or the playing song".format(left) else ""))
                        else Music.toast("Moods removed from %,d files, but %,d still have one (Settings › Problems says which and why)%s"
                            .format(changed, stuck, if (left > 0) "; %,d this ${DEVICE} may not change or the playing song".format(left) else "")) } }
            }
        }
    }
    var on by remember { mutableStateOf(org.omarchy.music.Moods.aiSwitch()) }
    var key by remember { mutableStateOf(org.omarchy.music.Prefs.text(org.omarchy.music.Moods.AI_KEY)) }
    Line("AI reading of lyrics (optional)", "With your own Anthropic API key, the words are read by Claude (claude-haiku-4-5) " +
        "instead of on this $DEVICE (on by itself once a key is saved): it scores every mood and reads what the singer means " +
        "(irony, a sad story told cheerfully, sayings), using the title and artist too. A song the AI has read is never read again. Each song's title, " +
        "artist and words are sent to Anthropic, and it costs a " +
        "little on your Anthropic account for each song (well under a cent). The key stays on this $DEVICE: it isn't in backups " +
        "or sync, and goes nowhere but Anthropic", on && key.isNotBlank()) {
        Switch(on, { on = it; org.omarchy.music.Prefs.set(org.omarchy.music.Moods.AI_ON, it) }, colors = swColors())
    }
    if (on) {
        // How to get a key, step by step (and where it goes)
        Text("How to get your key:\n" +
            "1. Tap Get a key below (or go to platform.claude.com) and sign up or log in. It's separate from a Claude.ai " +
            "subscription.\n" +
            "2. Go to Settings › Billing and add credits. The smallest amount ($5) is plenty: reading 3,000 songs costs " +
            "about $1–3, once.\n" +
            "3. Go to API keys and tap Create key. Name it \"Radon Player\".\n" +
            "4. Copy the key straight away (it starts with sk-ant-). It's only shown once.\n" +
            "Credits must be added first (step 2), or Anthropic refuses every request. \"Test the key\" below checks both.\n" +
            "5. Paste it in the box below and tap Save.\n" +
            "6. Tap Scan moods on the Moods tab: it reads each song's words with the AI as it hears its sound. Songs the AI " +
            "has read aren't read again.\n" +
            "Keep the key to yourself: anyone with it can spend your credits. To stop, clear the box and tap Save, or delete " +
            "the key at platform.claude.com.",
            style = T.sub, modifier = Modifier.padding(vertical = 4.dp))
        var testing by remember { mutableStateOf(false) }
        var tested by remember { mutableStateOf("") }
        Field(key, "Anthropic API key", password = true) { key = it }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            Pill("Get a key", quiet = true) { Services.open("https://platform.claude.com/settings/keys") }
            Pill("Save") {
                org.omarchy.music.Prefs.setText(org.omarchy.music.Moods.AI_KEY, org.omarchy.music.Moods.cleanKey(key))
                // A key saved: the AI reading is on (the switch shows it); you can still turn it off.
                if (key.isNotBlank()) { org.omarchy.music.Prefs.set(org.omarchy.music.Moods.AI_ON, true); on = true }
                Music.toast(if (key.isBlank()) "Key taken off" else "Key saved: lyrics are read by the AI from now on")
            }
            // One tiny request (a fraction of a cent): says whether the key and the account's credits work
            Pill(if (testing) "Testing…" else "Test the key", quiet = true) {
                if (!testing) {
                    if (org.omarchy.music.Moods.cleanKey(key) != org.omarchy.music.Moods.cleanKey(org.omarchy.music.Prefs.text(org.omarchy.music.Moods.AI_KEY)))
                        org.omarchy.music.Prefs.setText(org.omarchy.music.Moods.AI_KEY, org.omarchy.music.Moods.cleanKey(key))  // tests the key in the box
                    testing = true; tested = ""
                    org.omarchy.music.Moods.testAi { why ->
                        testing = false
                        tested = if (why.isEmpty()) "✓ The key works: the AI reads the lyrics" else "✗ The key didn't work: $why"
                        Music.toast(tested)
                    }
                }
            }
        }
        // The answer right here (a toast can hide behind this sheet)
        if (testing) Text("Testing the key with Anthropic…", style = T.sub)
        else if (tested.isNotEmpty()) Text(org.omarchy.music.Moods.hideKey(tested),
            style = T.sub.copy(color = if (tested.startsWith("✓")) C.accent else C.red))
        val status = org.omarchy.music.Moods.aiStatus
        if (status.isNotEmpty()) Text("Last AI problem: ${org.omarchy.music.Moods.hideKey(status)}", style = T.sub.copy(color = C.red))
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
    if (d.on) DiscordAppId()
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

/** Sync with phone/PC (Sync.kt): once paired, the two apps swap their data and merge it, both ways, in one tap. */
@Composable
private fun SyncLines(ui: Ui) {
    val s = org.omarchy.music.Sync
    val host = org.omarchy.music.RemoteHost
    val pc = host.supported
    val other = if (pc) "phone" else "PC"
    if (if (pc) !(host.running && host.phones.isNotEmpty()) else !Remote.paired) return
    val last = if (s.lastAt > 0) "Last synced ${s.ago(s.lastAt)}" + (if (s.lastWith.isNotEmpty()) " with ${s.lastWith}" else "") +
        ": " + s.lastText.removePrefix("Synced: ") else "Not synced yet"
    val note = s.busy ?: if (pc && s.waiting) "Waiting for the phone: open Radon Player on it (on the same network) and it syncs " +
        "within 20 seconds" else last
    Line("Sync with $other", "Syncs only when you tap Sync now (here or on the $other): never by itself. " +
        "The app's own stats go across so both show the same: plays, skips, songs heard out and " +
        "the listening diary (time listened, playlists, outputs, searches), each counted once however often you sync. Imports " +
        "and Last.fm aren't sent: import them on each device. Songs downloaded on one are downloaded on the other (each once), " +
        "and tag changes you make on one are made to the same songs on the other. " +
        "Synced playlists (⇄) are kept exactly the same on both; other playlists stay on their device. It also swaps playlist folders, favorites, pins, moods, " +
        "Sound presets, headphone corrections and each Bluetooth, USB or car output's EQ, the lists of things you said never to " +
        "show again, sorts and tabs, the DJ's and your other settings with the $other's, both ways, keeping the newest change " +
        "of each. Your music folders, download folder, queue and the settings only this $DEVICE has stay as they are",
        false, note = note) {
        when {
            s.busy != null -> Unit
            pc && s.waiting -> Pill("Cancel", quiet = true) { s.cancelAsk() }
            else -> Pill("Sync now") { if (pc) s.ask() else s.now() }
        }
    }
    Line("Songs' own volumes and EQs", "Syncs the volumes and Custom EQs you gave songs too. They're made for the headphones " +
        "you use with each device, so they stay on the device they were made on unless this is on, on both", s.songsOn) {
        Switch(s.songsOn, { s.setSongsOn(it) }, colors = swColors())
    }
    TagMatchLine(other)
    if (s.undoAt > 0) Line("Undo last sync", "Puts this $DEVICE's data back as it was just before the sync ${s.ago(s.undoAt)} " +
        "(changes made here since go too). The $other keeps its own: undo there too if you want", false) {
        Pill("Undo", quiet = true) { ui.confirm = "Put this $DEVICE's playlists, favorites and settings back as they were before the last sync?" to { s.undo() } }
    }
}

/** Folders sync leaves alone: their songs never take tags or covers from the other device, nor send theirs. */
@Composable
private fun SyncIgnoreLines() {
    val ig = org.omarchy.music.SyncIgnore
    var typed by remember { mutableStateOf("") }
    val pick = rememberFolderPick { path -> if (path != null) ig.add(path) }
    Line("Folders sync leaves alone", "Put your unreleased music (and any songs only you have the right tags for) in a folder " +
        "listed here, so a sync never changes their tags or album art. Without it, a sync can take an unreleased song for " +
        "another song of the same name and give it that song's tags and cover. Add the folder's full path, or just its name " +
        "(such as Unreleased) to leave alone every folder of that name. This list is kept on this $DEVICE only: add the " +
        "folder on your other device too. A folder named Unreleased is always left alone on both, listed or not. Their " +
        "moods still sync", ig.all.isNotEmpty()) {
        if (pick != null) Pill("Choose\u2026", quiet = true) { pick() }
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { Field(typed, "A folder, e.g. Unreleased") { typed = it } }
        Pill("Add") { if (typed.isNotBlank()) { ig.add(typed); typed = "" } }
    }
    ig.all.forEach { f ->
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(f, style = T.sub, modifier = Modifier.weight(1f))
            Pill("Remove", quiet = true) { ig.remove(f) }
        }
    }
}

/** Tags & covers match: the songs on both devices kept with the same tags and album art, the main device's. */
@Composable
private fun TagMatchLine(other: String) {
    val m = org.omarchy.music.TagMatch
    val P = org.omarchy.music.Prefs
    var asking by remember { mutableStateOf(false) }
    val on = P.on(m.ON)
    val mainName = if (m.main == "pc") "the PC" else "the phone"
    val receiver = if (m.main == "pc") "the phone" else "the PC"
    Line("Tags match", "Fills in what the songs on both devices are missing: their tags (title, artist, album, " +
        "album artist, genre, year, track; moods sync on their own). The main device sends, the other takes: at each sync $receiver's " +
        "copies get what $mainName's have and they don't. A tag that's filled in is never changed by it, and covers are never " +
        "synced: a song without one gets it from Songs without covers. Tag edits you make in the Tagger or Edit tags on either still go to both. A song is matched by its ISRC, " +
        "its title and artist, or its file's name, when the lengths agree. On the phone only the songs the app downloaded can be " +
        "changed (Android lets an app change only its own files)", on,
        note = when {
            !on -> ""
            m.isMain -> "Main device: this $DEVICE. It sends, the $other takes"
            else -> "Main device: the $other. It sends, this $DEVICE takes"
        }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (on) Pill("Main: ${if (m.main == "pc") "PC" else "phone"}", quiet = true) { asking = true }
            Switch(on, { v -> if (v) asking = true else P.set(m.ON, false) }, colors = swColors())
        }
    }
    if (asking) AlertDialog(onDismissRequest = { asking = false }, containerColor = C.light,
        title = { Text("Which device leads?", style = T.title) },
        text = { Text("The main device's tags and album art are put on the other's copies of the same songs at each sync. " +
            "Pick the one where you edit your tags.", style = T.sub) },
        confirmButton = { TextButton({ asking = false; P.setText(m.MAIN, "pc"); P.set(m.ON, true); m.refreshSoon(); org.omarchy.music.Sync.soon() }) {
            Text("The PC", color = C.accent) } },
        dismissButton = { TextButton({ asking = false; P.setText(m.MAIN, "phone"); P.set(m.ON, true); m.refreshSoon(); org.omarchy.music.Sync.soon() }) {
            Text("The phone", color = C.accent) } })
}

/** Time choice: Most played's Songs time, Albums time and Artists time in hours or in minutes. */
@Composable
private fun TimeChoiceLine() {
    val names = linkedMapOf("hours" to "Hours", "minutes" to "Minutes")
    Line("Time choice", "How Most played shows listening time (Songs time, Albums time and Artists time): in hours (and " +
        "minutes), or all in minutes", false) {
        Box {
            var open by remember { mutableStateOf(false) }
            Pill("${names[TimeChoice.unit] ?: "Hours"} ▾", quiet = true) { open = true }
            androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = C.light) {
                names.forEach { (k, l) ->
                    androidx.compose.material3.DropdownMenuItem({ Text(l, style = T.body.copy(color = if (k == TimeChoice.unit) C.accent else C.fg)) },
                        { open = false; TimeChoice.choose(k) })
                }
            }
        }
    }
}

/** Stream quality: what songs play in when they aren't on this device (FLAC 24-bit, FLAC 16-bit or 320 kbps), for
 *  every service's songs, as they all stream from Tidal; YouTube's (without Tidal, or songs Tidal hasn't got) stay
 *  YouTube's best. */
@Composable
private fun StreamQualityLine() {
    val names = linkedMapOf("aac" to "320 kbps", "flac16" to "FLAC 16-bit", "flac24" to "FLAC 24-bit")
    Line("Stream quality", "What songs play in when they aren't on this $DEVICE, whichever service they're from (Spotify's, " +
        "Apple Music's, Deezer's and YouTube Music's songs all play from Tidal): FLAC 24-bit is hi-res where Tidal has it (else " +
        "16-bit), FLAC 16-bit is CD quality, 320 kbps is Tidal's AAC (as good as MP3 320, and uses the least data). It needs " +
        "Tidal: without it, and for songs Tidal hasn't got, songs stream from YouTube at its best (Opus 160 kbps, or AAC 256 kbps where YouTube offers it), " +
        "whatever is picked here" + (if (!Music.tidal.connected) ". Tidal isn't connected now, so songs stream from YouTube" else ""), true) {
        Box {
            var open by remember { mutableStateOf(false) }
            Pill("${names[Music.streamQuality] ?: "FLAC 24-bit"} ▾", quiet = true) { open = true }
            androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = C.light) {
                names.forEach { (k, l) ->
                    androidx.compose.material3.DropdownMenuItem({ Text(l, style = T.body.copy(color = if (k == Music.streamQuality) C.accent else C.fg)) },
                        { open = false; Music.chooseStreamQuality(k) })
                }
            }
        }
    }
}

/** Download quality: MP3 320 kbps, FLAC 16-bit or FLAC 24-bit, for every download from then on. They come from Tidal
 *  (it needs a Tidal subscription); a song Tidal hasn't got, or every song without Tidal, comes from YouTube instead, at
 *  YouTube's best (it has no FLAC). */
@Composable
private fun DownloadQualityLine() {
    val names = linkedMapOf("mp3" to (if (org.omarchy.music.CAN_MP3) "MP3 320 kbps" else "320 kbps (AAC)"), "flac16" to "FLAC 16-bit", "flac24" to "FLAC 24-bit")
    Line("Download quality", "Every song you download from now on comes in this quality, from Tidal: FLAC 24-bit is hi-res " +
        "where Tidal has it (else 16-bit), FLAC 16-bit is CD quality, " + (if (org.omarchy.music.CAN_MP3) "MP3 320 kbps is made from Tidal's FLAC. "
        else "and 320 kbps is Tidal's own (AAC, as good as MP3 320 and as small; the phone has no MP3 maker, the PC makes MP3s). ") +
        "Without a Tidal subscription, downloads come from YouTube instead, at YouTube's best (Opus 160 kbps, or AAC 256 kbps where YouTube offers it; YouTube has " +
        "no FLAC), whatever is picked here. Songs Tidal hasn't got come from YouTube too" +
        (if (!Music.tidal.connected) ". Tidal isn't connected now, so downloads come from YouTube" else ""), true) {
        Box {
            var open by remember { mutableStateOf(false) }
            Pill("${names[Music.downloadQuality] ?: "FLAC 24-bit"} ▾", quiet = true) { open = true }
            androidx.compose.material3.DropdownMenu(open, { open = false }, containerColor = C.light) {
                names.forEach { (k, l) ->
                    androidx.compose.material3.DropdownMenuItem({ Text(l, style = T.body.copy(color = if (k == Music.downloadQuality) C.accent else C.fg)) },
                        { open = false; Music.chooseDownloadQuality(k) })
                }
            }
        }
    }
}

/** For songs from Spotify, Apple Music and the rest, which can't be downloaded here: DumpMedia, a paid app that saves
 *  them as files, which then play here as your own songs (Folders). */
@Composable
private fun OtherServicesLine() {
    Line("Downloading from Spotify, Apple Music and others", "Their songs can't be downloaded here (only Tidal's and YouTube's " +
        "can). If downloads don't work for you, DumpMedia is an app for your computer that saves songs from Spotify, Apple " +
        "Music, Amazon Music and others as files, FLAC included. You need your subscription to that service (you most likely " +
        "have it already). DumpMedia itself is paid: monthly, or once for a lifetime licence, a small price for the convenience. " +
        "Put the files it makes in one of your music folders (Folders, above) and they play here as your own songs. A file " +
        "can't sound better than what the service streams", false) {}
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.End) {
        Pill("Open DumpMedia's website", quiet = true) { Services.open("https://www.dumpmedia.com/") }
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
        "in one file, for moving to a new ${DEVICE} or after reinstalling. Your songs and sign-ins aren't included. A backup of " +
        "the phone's restores on the PC and the other way (this ${DEVICE}'s folders, sound output and the volumes and Custom EQs " +
        "you gave songs stay its own, and your songs there are matched to the same songs here). Restoring keeps what's already here alongside the backup, just in case", false) {}
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
    IgnoreBtLines(ui)
    Line("Smart shuffle", "On: shuffle favors songs you love (ones you play a lot) that you haven't heard in a " +
        "while, so they come up more often. Off: shuffle is pure chance, every song as likely as the next",
        P.on(P.SMART_SHUFFLE)) {
        Switch(P.on(P.SMART_SHUFFLE), { P.set(P.SMART_SHUFFLE, it) }, colors = swColors())
    }
}


/** Settings › Personalization (also the Now playing look pane): Look, Theme, the Now playing look's fallback, and Now
 *  playing's background and visualizer. */
@Composable
fun PersonalizationLines(ui: Ui) {
    // A preset put on: every line below starts again from the new choices
    androidx.compose.runtime.key(MyPresets.applied) { PersonalizationBody(ui) }
}

@Composable
private fun PersonalizationBody(ui: Ui) {
            PresetShuffleLines()
            // Every theme and look of the app's screens in one place (the Visualizer's own looks stay on the Visualizer).
            Text("Look picks the colours, Theme how things are drawn (corners, type, spacing) and Layout where they are: any of " +
                "each goes with any other. The Now playing look takes the colours of the song's cover; every other look keeps its " +
                "own, and Now playing's song name, buttons, seek bar and lyrics are in the look's colour.", style = T.sub, modifier = Modifier.padding(top = 8.dp))
            PresetLines(ui)
            Line("Look", "How the app is coloured: Light, Dark, Now playing (every screen matches the cover of the song " +
                "that's playing, the way the Now playing screen is), or one of the others, from Midnight to Bubblegum",
                false, note = Looks.describe(Music.look)) {
                // Right-click (or hold) one: ★ Favourite (to the top), or Delete one you made
                ChoicePill(Favs.sorted(Favs.LOOKS, Looks.ALL), Music.look, swatch = { k -> Looks.swatch(k).let { it.first to SolidColor(it.second) } },
                    describe = { lookDesc(it) }, preview = { LookPreview(it) }, menu = { k, l -> Favs.menu(Favs.LOOKS, k, l) }) { Music.chooseLook(it) }
            }
            // The app's style, apart from its colours: kept with the other settings (so backups and Sync carry it too).
            val theme = org.omarchy.music.Prefs.text(Themes.PREF, Themes.STANDARD).takeIf { Themes.known(it) } ?: Themes.STANDARD
            Line("Theme", "How the app is drawn, whatever its colours: its corners, type, spacing, cards and lines",
                false, note = Themes.ALL[theme]?.desc.orEmpty()) {
                ChoicePill(Favs.sorted(Favs.THEMES, Themes.MENU), theme, describe = { Themes.ALL[it]?.desc.orEmpty() }, preview = { ThemePreview(it) },
                    menu = { k, l -> Favs.menu(Favs.THEMES, k, l) }) {
                    org.omarchy.music.Prefs.setText(Themes.PREF, it) }
            }
            // Where things are on the screen, apart from colours and drawing: any layout goes with any look and theme
            val lay = UiLayouts.current
            Line("Layout", "Where things are on the screen: how you get around (the ☰ menu, a bar along the bottom, a rail " +
                "down the side or tabs along the top), the mini player and how Now playing is arranged. Every layout has every " +
                "feature: what isn't on its bar is in the ☰ menu", false, note = lay.explain()) {
                fun of(k: String) = UiLayouts.all().firstOrNull { it.key == k }
                ChoicePill(Favs.sorted(Favs.LAYOUTS, UiLayouts.MENU), lay.key, describe = { of(it)?.explain().orEmpty() },
                    preview = { k -> of(k)?.let { LayoutPreview(it) } }, thumb = { k -> of(k)?.let { LayoutThumb(it) } },
                    later = true, menu = { k, l -> Favs.menu(Favs.LAYOUTS, k, l) }) { UiLayouts.choose(it) }
            }
            EffectsLines()
            NowPlayingLookLines()
            MakerLines(ui)
}

/** Settings › Personalization › Effects: the looks' own effects, or any of them with any look or theme. */
@Composable
private fun EffectsLines() {
    var own by remember { mutableStateOf(LookFx.ownOn()) }
    var picked by remember { mutableStateOf(LookFx.picked()) }
    Line("Look's own effects", "On: each look keeps its own effects (the galaxy looks' stars, a look of yours with its " +
        "effects). Off: no look's own effects; the ones you pick below go with any look or theme instead", own) {
        Switch(own, { own = it; LookFx.setOwn(it) }, colors = swColors())
    }
    if (!own) Line("Effects", "Drawn over the background, behind everything else, with the ready-made looks, any theme " +
        "and layout (a look you made keeps its own effects). Pick as many as you like; point at one (on the PC) to see it", false) {
        ChoicePill(Favs.sorted(Favs.EFFECTS, LookFx.MENU), "", describe = { LookFx.describe(it) }, preview = { FxPreview(it) }, chosen = LookFx.keys(picked),
            menu = { k, l -> Favs.menu(Favs.EFFECTS, k, l) }) {
            LookFx.toggle(it); picked = LookFx.picked()
        }
    }
    // Each effect picked: its own colour, if you like
    if (!own && LookFx.keys(picked).any { it != "stars" }) FxColors(picked) { p ->
        org.omarchy.music.Prefs.setText(LookFx.PICK, p.joinToString(";")); picked = LookFx.picked(); org.omarchy.music.Music.applyLook()
    }
}

/** A look's description for its menu (the Now playing look's own, and yours). */
private fun lookDesc(k: String): String = Looks.describe(k).ifEmpty {
    if (k == Looks.NOW_PLAYING) "Every screen takes the colours of the cover of the song that's playing" else "" }

/** Settings › Look for Now playing: its background, and the visualizer behind it. */
@Composable
private fun NowPlayingLookLines() {
    Line("Now playing visualizer", "What moves with the music on Now playing: the slim bars, nothing, or any of the " +
        "Visualizer's styles. Round ones (Circle, Ring, Particles, Pulse, Starfield, Sunburst, Galaxy, Flower, Vinyl, XY scope, Kaleidoscope, Tunnel) play in the middle of the cover, bright, the rest along the bottom. It takes the " +
        "colour picked in the Visualizer (Look's colour is the cover's here). The button at the far right of Now playing's controls changes it there", false) {
        var now by remember { mutableStateOf(Vis.nowStyle()) }
        ChoicePill(Favs.sorted(Favs.VIS, NP_VIS_STYLES), now, preview = { NowVisPreview(it) },
            menu = { k, l -> if (k == "slim" || k == "off") emptyList() else Favs.menu(Favs.VIS, k, l) }) { org.omarchy.music.Prefs.setText(Vis.NOW, it); now = it }
    }
    // Dual: a style in the middle and one along the bottom at once (also on Now playing's visualizer button)
    var dual by remember { mutableStateOf(Vis.nowDual()) }
    Line("Dual visualizer", "Two at once on Now playing: the one above in the middle (around the cover) and another along " +
        "the bottom", dual) {
        Switch(dual, { dual = it; Vis.setNowDual(it) }, colors = swColors())
    }
    if (dual) Line("Along the bottom", "Dual's style along the bottom of Now playing", false) {
        var bottom by remember { mutableStateOf(Vis.nowBottom()) }
        ChoicePill(Vis.BOTTOM_STYLES, bottom, preview = { NowVisPreview(it) }) { org.omarchy.music.Prefs.setText(Vis.NOW_BOTTOM, it); bottom = it }
    }
    // Wrapped's look and the lyrics card's style, here too (they stay in Wrapped and the Lyrics card), so a preset keeps them
    Line("Wrapped style", "How Wrapped's slides look (the same as Style in Wrapped): the ready-made ones, Your look, and yours", false) {
        ChoicePill(Favs.sorted(Favs.WRAPPED, WS.STYLES), Music.wrappedStyle, menu = { k, l -> Favs.menu(Favs.WRAPPED, k, l) },
            preview = { k -> WrapPresetSlide(k, Music.look, Modifier.size(220.dp, 290.dp)) }) { Music.chooseWrappedStyle(it) }
    }
    Line("Lyrics card style", "How lyrics cards look (the same as Style in the Lyrics card box): Your look, Now playing, 30 " +
        "ready-made styles and yours", false) {
        var card by remember { mutableStateOf(LyricsCards.picked()) }
        ChoicePill(Favs.sorted(Favs.CARDS, LyricsCards.menu()), card, describe = { LyricsCards.describe(it) },
            preview = { k -> PresetCard(k) },
            menu = { k, l -> Favs.menu(Favs.CARDS, k, l) }) { LyricsCards.pick(it); card = it }
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

/** Ignore Bluetooth devices: the switch (also Now playing's Bluetooth-with-an-X button), and the devices seen before to
 *  pick from (tap one to ignore it or not, × to take it off the list). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IgnoreBtLines(ui: Ui) {
    val ign = org.omarchy.music.IgnoreBt
    val on = ign.on
    val picked = ign.ignored()
    val names = (ign.seen() + picked).distinct()
    Line("Ignore Bluetooth devices", when {
        !on -> "When a Bluetooth device you pick (your car) connects, the music stays where it was (your headphones) instead " +
            "of going to it. The device still connects for everything else" + if (onPhone) " (calls, Android Auto's maps)" else ""
        picked.isEmpty() -> "Pick the devices to ignore below"
        ign.here.isNotEmpty() && ign.keptOn.isNotEmpty() -> "${ign.here} is connected: the music is kept on ${ign.keptOn}"
        ign.here.isNotEmpty() && ign.why.isNotEmpty() -> "${ign.here} is connected and has the music: ${ign.why}"
        else -> "On: the music stays where it is when ${picked.joinToString(", ")} connect" + if (picked.size == 1) "s" else ""
    }, on && picked.isNotEmpty()) {
        Switch(on, { ign.setOn(it) }, colors = swColors())
    }
    androidx.compose.foundation.layout.FlowRow(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        names.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(name, name in picked) { ign.pick(name, name !in picked) }
                Text("×", style = T.sub, modifier = Modifier.clip(R.pill)
                    .clickable { ui.confirm = "Take “$name” off the list of Bluetooth devices?" to { ign.forget(name) } }
                    .padding(horizontal = 6.dp, vertical = 4.dp))
            }
        }
        if (names.isEmpty()) Text("No Bluetooth devices seen yet: connect one (your car) once and it shows up here to pick", style = T.sub)
    }
    Text(if (onPhone) "Phones decide how Bluetooth works, so this may not work on every phone. Many play to only one Bluetooth " +
        "device at a time: once the car takes over, your headphones drop out and there's nowhere else to keep the music, so it's " +
        "left as it is. It never moves the music to the phone's own speaker. When the car is connected through Android Auto, " +
        "Android Auto's own sound can't be moved"
        else "The PC decides its default output; the app looks every few seconds, so the device may have the sound for a moment " +
        "first. The music goes back to the output it was on, as long as that's still there",
        style = T.sub, modifier = Modifier.padding(bottom = 8.dp))
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
            "music: no EQ, no song volume, no speed. It turns itself off when you close the window, quit or unplug a sound device, so other apps are heard again" },
        Music.bitPerfect) {
        Switch(Music.bitPerfect, { Music.chooseBitPerfect(it) }, colors = swColors())
    }
    val P = org.omarchy.music.Prefs
    Line("Clip guard", "When a song's volume or the EQ would push the loudest moments (peaks) past the " +
        "maximum level, a limiter gently holds them down instead of letting them crackle. It only ever touches a peak that would " +
        "clip", P.on(P.CLIP_GUARD)) {
        Switch(P.on(P.CLIP_GUARD), { Music.setOpt(P.CLIP_GUARD, it) }, colors = swColors())
    }
    Line("Smooth distortion on loud songs", if (onPhone) "Some songs are mastered so loud that their peaks are cut off, " +
        "which can sound grainy. This rebuilds those peaks and leaves 3 dB of room so nothing clips. Everything plays a little " +
        "quieter while it's on (turn the volume up a notch). Off in bit-perfect"
        else "Some songs are mastered so loud that their peaks are cut off, which can sound grainy, and the EQ or a song's " +
        "volume makes it worse. This leaves 3 dB of room so nothing clips on top, and a limiter gently holds any peak that's left. " +
        "Everything plays a little quieter while it's on (turn the volume up a notch). Off in bit-perfect", P.on(P.SMOOTH_PEAKS)) {
        Switch(P.on(P.SMOOTH_PEAKS), { Music.setOpt(P.SMOOTH_PEAKS, it) }, colors = swColors())
    }
    Line("Dither", "When 16-bit (CD quality) music goes through the EQ, it's rounded off with a whisper of shaped " +
        "noise, so quiet fades stay smooth instead of grainy. Hi-res music is always dithered as it comes down to 16-bit (only bit-perfect to a USB DAC keeps every bit)", P.on(P.DITHER)) {
        Switch(P.on(P.DITHER), { Music.setOpt(P.DITHER, it) }, colors = swColors())
    }
    Line("High-quality resampling", if (onPhone) "When a CD-quality song's sample rate isn't the phone's own (44.1 kHz music on a " +
        "48 kHz phone), the app converts it with a long, sharp filter instead of Android's quicker one: cleaner highs. Songs already at the " +
        "phone's rate, and bit-perfect, are left untouched, so it's only used where it helps. From the next song"
        else "When a song's sample rate doesn't match your sound card's (44.1 kHz " +
        "music on a 48 kHz card), it's converted with a longer, sharper filter. You get cleaner highs for a little more work from the PC", P.on(P.HQ_RESAMPLE)) {
        Switch(P.on(P.HQ_RESAMPLE), { Music.setOpt(P.HQ_RESAMPLE, it) }, colors = swColors())
    }
    Line("Loudness compensation", "At low volume our ears lose bass and treble first, so music sounds thin. This lifts them " +
        "back by as much as the volume is turned down, and does nothing at full volume", P.on(P.LOUDNESS)) {
        Switch(P.on(P.LOUDNESS), { Music.setOpt(P.LOUDNESS, it) }, colors = swColors())
    }
    Line("Detail", "Brings forward background sounds, bass lines and small details: the stereo gets a touch wider, " +
        "presence and air (the upper mids and highs) lift a little, and the level drops to match so nothing clips", P.on(P.DETAIL)) {
        Switch(P.on(P.DETAIL), { Music.setOpt(P.DETAIL, it) }, colors = swColors())
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
internal fun swColors() = SwitchDefaults.colors(checkedThumbColor = C.darker, checkedTrackColor = C.accent,
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

/** A fold inside a Settings group (Scanner › Your files…): opens and closes on its own. */
@Composable
private fun SubGroup(title: String, ui: Ui, content: @Composable () -> Unit) {
    val key = "sub:$title"
    val open = key in ui.settingsOpen
    Row(Modifier.fillMaxWidth().padding(top = 4.dp).clip(R.card).background(C.accent.copy(alpha = 0.08f))
        .clickable { if (open) ui.settingsOpen -= key else ui.settingsOpen += key }
        .padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = T.body.copy(fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), modifier = Modifier.weight(1f))
        Text(if (open) "▾" else "▸", style = T.sub)
    }
    if (open) Column(Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 4.dp)) { content() }
}

/** A row of Settings: its name and what it is (or what it's doing) on the left, its switch or buttons on the right. */
@Composable
private fun Line(name: String, state: String, ok: Boolean, note: String = "", actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(name, style = T.body.copy(fontSize = 16.sp))
            if (note.isNotEmpty()) Text(note, style = T.sub.copy(color = C.fg), modifier = Modifier.padding(top = 2.dp))
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
        // Above the phone's own buttons too (Back, Home), so nothing at the bottom is under them
        .pointerInput(Unit) { detectTapGestures { } }.statusBarsPadding().navigationBarsPadding()) {
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
                    Chip("${Eq.presets(eq)[eq.preset]?.first ?: "Custom"} ▾", false) { presets = true }
                    androidx.compose.material3.DropdownMenu(presets, { presets = false }, containerColor = C.light) {
                        Eq.presets(eq).forEach { (k, v) ->
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
            val label = eq.outputs[key]?.label?.ifEmpty { null } ?: if (key == Music.outputNow.key) Music.outputNow.label else Music.outLabel(key)
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
                    val known = (eq.outputs.keys + Music.outputNow.key + "speaker" + (if (onPhone) listOf(Music.CAR) else emptyList())).distinct()
                    known.forEach { k ->
                        val l = eq.outputs[k]?.label?.ifEmpty { null } ?: if (k == Music.outputNow.key) Music.outputNow.label
                            else Music.outLabel(k)
                        androidx.compose.material3.DropdownMenuItem(
                            leadingIcon = { Icon(outIcon(org.omarchy.music.Outputs.kindOf(k)), if (k == key) C.accent else C.muted, 20.dp) },
                            text = { Text(l + if (k == Music.outputNow.key) "  · now" else "", style = T.body.copy(color = if (k == key) C.accent else C.fg)) },
                            onClick = { outs = false; Music.editEqOutput(k) },
                            trailingIcon = if (k != key && k != Music.outputNow.key && k != "speaker" && k != Music.CAR) ({
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
            ChipRow(Eq.presets(eq).map { (k, v) -> k to v.first }, eq.preset) { Music.setEqPreset(it) }
            PresetTools(ui, eq)
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
            // Settings › Sound quality's switches too, so every sound option is here as well (Now playing's speaker opens this)
            Text("Sound quality", style = T.section, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            SoundQuality(ui)
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
    // Dual volume: the Boost and a song's own volume (Volume…) together, or the song's own alone
    var dual by remember { mutableStateOf(Music.dualVolume()) }
    val ownNow = Music.current()?.let { Music.gainNow(it) != 0.0 } == true
    Line("Dual volume", if (dual) "On: a song with its own volume (Volume… in its menu) plays at that volume with the Boost on " +
        "top, as every other song has the Boost" else "Off: a song with its own volume (Volume… in its menu) plays at just that " +
        "volume, without the Boost; every other song keeps the Boost" + if (ownNow && b > 0) ". The song playing has its own volume, so the Boost is left out now" else "",
        dual) {
        Switch(dual, { dual = it; Music.setDualVolume(it) }, colors = swColors())
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

/** Your own Sound presets: Save as preset… (the bands as they are now), Import (a GraphicEQ or parametric file), Export
 *  (the Sound as a GraphicEQ file), and Delete for one of yours that's picked. */
@Composable
private fun PresetTools(ui: Ui, eq: org.omarchy.music.EqSettings) {
    var naming by remember { mutableStateOf<String?>(null) }
    val importer = rememberOpenFile { uri -> if (uri != null) Music.importPresetFile(uri) }
    val exporter = rememberSaveFile("text/plain") { uri -> if (uri != null) Music.exportPreset(uri) }
    val label = org.omarchy.music.Eq.presets(eq)[eq.preset]?.first ?: "Sound"
    Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("Save as preset…", quiet = true) { naming = if (eq.preset == "custom" || eq.preset.startsWith("my:")) "" else "$label (mine)" }
        Pill("Import", quiet = true) { importer.launch(arrayOf("text/*", "application/octet-stream")) }
        Pill("Export", quiet = true) { exporter.launch("${label.replace(Regex("[^\\w .()-]"), "")} GraphicEQ.txt") }
        if (eq.preset.startsWith("my:")) Pill("Delete “$label”", quiet = true) { ui.confirm = "Delete your preset “$label”?" to { Music.deletePreset(eq.preset) } }
    }
    naming?.let { start ->
        var name by remember { mutableStateOf(start) }
        AlertDialog(onDismissRequest = { naming = null }, containerColor = C.light,
            title = { Text("Save as a Sound preset", style = T.title) },
            text = { Column {
                Text("The bands as they are now, kept among your Sound presets (in your backups too).", style = T.sub)
                Spacer(Modifier.height(8.dp))
                Field(name, "Its name") { name = it }
            } },
            confirmButton = { TextButton({ naming = null; Music.savePreset(name) }) { Text("Save", color = C.accent) } },
            dismissButton = { TextButton({ naming = null }) { Text("Cancel", color = C.muted) } })
    }
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
    val exporter = rememberSaveFile("text/plain") { uri -> if (uri != null) Music.exportCorrection(uri) }
    Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Collect: every source AutoEq has for the headphones connected (or the ones typed), all added to compare
        Pill("Collect all sources", quiet = true) {
            val dev = q.trim().ifEmpty { org.omarchy.music.AutoEq.output(Music.ctx) }
            if (dev.isEmpty()) { note = "Connect your headphones, or type their name, then Collect"; return@Pill }
            note = "Collecting every correction for $dev…"
            Music.collectCorrections(dev) { n -> note = if (n == 0) "AutoEq has nothing for $dev" else ""; if (n > 0) q = "" }
        }
        Pill("Export", quiet = true) {
            val name = org.omarchy.music.Eq.headphones(Music.eq)[Music.eq.headphones]?.first ?: "Correction"
            exporter.launch("${name.replace(Regex("[^\\w .()-]"), "")} ParametricEQ.txt")
        }
    }
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
    // Pictures of the artist too: an unreleased song with no cover anywhere can wear one of them
    val lead = t.artist.split(",")[0].split(Regex(" & | x | feat\\.? | ft\\.? ", RegexOption.IGNORE_CASE))[0].trim()
    var artistPics by remember(songs) { mutableStateOf<List<org.omarchy.music.Pictures.Choice>?>(null) }
    LaunchedEffect(songs) {
        if (lead.isEmpty()) { artistPics = emptyList(); return@LaunchedEffect }
        Music.bg({ org.omarchy.music.Pictures.artistChoices(lead) }) { c, _ -> artistPics = c ?: emptyList() }
    }
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
                    MicButton { said -> query = said; asked = said }
                    IconButton(Ico.SEARCH, C.accent, 18.dp, 8.dp) { asked = query }
                }
                Text(when {
                    busy -> "Putting it on the songs…"
                    c == null -> "Looking on Google, Genius and Wikipedia…"
                    c.isEmpty() -> "No cover found: unreleased songs often have none. Use a picture of $lead below, pick a picture of yours, or skip it."
                    else -> "${t.artist}${if (t.album.isNotEmpty()) " · goes on every song of the album" else ""}"
                }, style = T.sub, modifier = Modifier.padding(bottom = 8.dp))
                val pics = artistPics
                if (!busy && (!c.isNullOrEmpty() || !pics.isNullOrEmpty() || lead.isNotEmpty())) LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    fun grid(list: List<org.omarchy.music.Pictures.Choice>) = items((list.size + 2) / 3) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                            for (ch in list.drop(row * 3).take(3)) Column(Modifier.clickable { use(ch.url) }) {
                                ArtImage(ch.thumb, 84.dp)
                                Text(ch.from, style = T.time)
                            }
                        }
                    }
                    if (!c.isNullOrEmpty()) grid(c)
                    if (lead.isNotEmpty()) {
                        item { Text("PICTURES OF ${lead.uppercase()}", style = T.time, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)) }
                        if (pics == null) item { Text("Looking for pictures of $lead…", style = T.sub) }
                        else if (pics.isEmpty()) item { Text("No pictures of $lead found", style = T.sub) }
                        else grid(pics)
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

/** Volume…: a song's own volume (quiet unreleased songs), from -12 to +12 dB, kept for it: on the output it's playing
 *  through now (headphones a song is quiet on, so it doesn't blast in the car), or on every output. */
@Composable
private fun VolumeDialog(ui: Ui, tracks: List<org.omarchy.music.Track>) {
    val here = Music.outputNow.key
    val first = tracks[0]
    // Where this change goes: this output (its own change, if it has one, or none yet) or every output
    var scope by remember(tracks) { mutableStateOf<String?>(if (Music.gains.saved(first, here) == null && Music.gains.saved(first, null) != null) null else here) }
    var db by remember(tracks, scope) { mutableStateOf((Music.gains.saved(first, scope) ?: 0.0).toFloat()) }
    val close = { ui.volumeFor = null }
    val done = { v: Double -> close(); Music.setGain(tracks, v, scope); ui.afterOption() }
    AlertDialog(onDismissRequest = close, containerColor = C.light,
        title = { Text(if (tracks.size == 1) "Volume of “${first.title}”" else "Volume of ${tracks.size} songs", style = T.title,
            maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("On ${Music.outputNow.label}", scope == here) { scope = here }
                    Chip("On every output", scope == null) { scope = null }
                }
                Text("%+.1f dB".format(db), style = T.title.copy(color = C.accent))
                Slider(db, { db = Math.round(it * 2) / 2f }, valueRange = -12f..12f, steps = 47,
                    colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.fg.copy(alpha = 0.1f)))
                Text((if (scope == null) "Plays this much louder (or quieter) everywhere, unless an output has its own change for it. "
                    else "Plays this much louder (or quieter) only while the sound goes to ${Music.outputNow.label}: the car or the speaker keep " +
                        "their own. ") + "+6 dB is about twice as loud; very loud songs may distort above 0.", style = T.sub)
                if (tracks.size == 1) {   // what else this song has
                    val others = Music.gains.outputsOf(first).filterKeys { it != scope } +
                        (if (scope != null) Music.gains.saved(first, null)?.let { mapOf("" to it) } ?: emptyMap() else emptyMap())
                    if (others.isNotEmpty()) Text("Also: " + others.entries.joinToString(" · ") { (k, v) ->
                        (if (k.isEmpty()) "every output" else Music.eq.outputs[k]?.label?.ifEmpty { null } ?: Music.outLabel(k)) +
                            " %+.1f dB".format(v) }, style = T.sub, modifier = Modifier.padding(top = 6.dp))
                }
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

/** Custom EQ…: a song's own EQ on the output playing now (its Headphones correction, Sound and Boost), used instead of
 *  that output's EQ whenever this song plays there; everywhere else, and every other song, keeps the usual one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongEqDialog(ui: Ui, tracks: List<org.omarchy.music.Track>) {
    val E = org.omarchy.music.Eq
    val first = tracks[0]
    val had = Music.songEqs.get(first, Music.outputNow.key)
    val base = had ?: Music.eq.toOutput(Music.outputNow.label)
    var hp by remember(tracks) { mutableStateOf(base.headphones) }
    var preset by remember(tracks) { mutableStateOf(base.preset) }
    val count = base.count
    var bands by remember(tracks) { mutableStateOf(base.bands.takeIf { it.size == count } ?: List(count) { 0.0 }) }
    var boost by remember(tracks) { mutableStateOf(base.boost) }
    val close = { ui.songEqFor = null }
    AlertDialog(onDismissRequest = close, containerColor = C.light,
        title = { Text(if (tracks.size == 1) "Custom EQ for “${first.title}”" else "Custom EQ for ${tracks.size} songs", style = T.title,
            maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("On ${Music.outputNow.label} only: whenever ${if (tracks.size == 1) "this song plays" else "these songs play"} there, " +
                    "this EQ is used instead of the usual one (C EQ lights up red in Now playing). Everywhere else, and every other " +
                    "song, keeps the usual EQ. Bass and treble, dither and the other sound settings stay as they are.", style = T.sub)
                Text("Boost: " + if (boost == 0.0) "off" else "+%.1f dB".format(boost), style = T.title.copy(color = C.accent),
                    modifier = Modifier.padding(top = 10.dp))
                Slider(boost.toFloat(), { boost = Math.round(it * 2) / 2.0 }, valueRange = 0f..E.BOOST_MAX.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.fg.copy(alpha = 0.1f)))
                Text("Sound", style = T.time, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    E.presets(Music.eq).forEach { (k, v) ->
                        Chip(v.first, preset == k) { preset = k; E.presetOf(Music.eq, k, count)?.let { bands = it } }
                    }
                }
                E.bandsFor(count).forEachIndexed { i, hz ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(E.label(hz), style = T.time, modifier = Modifier.width(40.dp))
                        Slider(bands[i].toFloat(), { v -> bands = bands.toMutableList().also { it[i] = E.clamp(v.toDouble()) }; preset = "custom" },
                            valueRange = -12f..12f, modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.fg.copy(alpha = 0.1f)))
                        Text(E.fmtDb(bands[i]), style = T.time, modifier = Modifier.width(36.dp))
                    }
                }
                Text("Headphones correction", style = T.time, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    E.headphones(Music.eq).forEach { (k, v) -> Chip(v.first, hp == k) { hp = k } }
                }
            }
        },
        confirmButton = { TextButton({
            close()
            Music.setSongEq(tracks, base.copy(on = true, headphones = hp, preset = preset, bands = bands, custom = bands, count = count, boost = boost))
            ui.afterOption()
        }) { Text("Save", color = C.accent) } },
        dismissButton = {
            Row {
                if (tracks.any { Music.songEqs.get(it, Music.outputNow.key) != null })
                    TextButton({ ui.confirm = "Remove the volume you set for ${if (tracks.size == 1) "this song" else "these songs"}?" to { close(); Music.setSongEq(tracks, null); ui.afterOption() } }) { Text("Remove", color = C.red) }
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
    val ask = rememberFileRemoval(trash = !onPhone) { ok ->   // the PC's go to the Recycle Bin, as the Python app's
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
                if (files.isNotEmpty()) "${if (n == 1) "Its file" else "${files.size} files"} on the $DEVICE will be deleted" + if (onPhone) ": Android asks you once more." else " (into the Recycle Bin)." else null,
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
    val songs = remember(tracks) { tracks.filter { org.omarchy.music.Tags.editableAny(it) != null } }
    var have by remember { mutableStateOf<Map<String, String?>?>(null) }
    val typed = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    LaunchedEffect(songs) {
        Music.bg({ org.omarchy.music.Tags.read(Music.ctx, songs.mapNotNull { org.omarchy.music.Tags.editableAny(it) }) }) { h, _ -> have = h ?: emptyMap() }
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

/** Settings › Personalization, at the top: Personalization Preset Shuffle, a different favourite preset every 5, 10, 15 or 30 minutes. */
@Composable
private fun PresetShuffleLines() {
    var on by remember { mutableStateOf(MyPresets.shuffleOn()) }
    var every by remember { mutableStateOf(MyPresets.shuffleMinutes()) }
    val favs = MyPresets.shufflePool().size
    Line("Personalization Preset Shuffle", "On: puts on a different Personalization preset every few minutes. It only " +
        "shuffles your favourite presets (★: in Personalization presets below, hold one or tap its \u22EF, then Favourite)", on,
        note = when { !on -> ""; favs == 0 -> "No favourite presets yet: favourite some below and it starts going through them"
            favs == 1 -> "Only one favourite preset: favourite more below to shuffle between them"
            else -> "Shuffling your $favs favourite presets every $every minutes" }) {
        Switch(on, { on = it; MyPresets.setShuffle(it) }, colors = swColors())
    }
    if (on) Line("Change every", "How often Personalization Preset Shuffle puts on the next favourite preset", false) {
        ChoicePill(MyPresets.SHUFFLE_TIMES.map { it.toString() to "$it minutes" }, every.toString()) {
            every = it.toInt(); MyPresets.setShuffleMinutes(every) }
    }
}

/** Settings › Personalization: the presets menu and Save. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetLines(ui: Ui) {
    val presets = MyPresets.all()
    val editing = MyPresets.editing?.let { MyPresets.of(it) }
    Line("Personalization presets", "Everything on this tab at once (look, theme, layout, effects and their colours, Now " +
        "playing's visualizer and Dual, Wrapped's style and the lyrics card's style) saved under a name: pick one to put all of it back. Tap one to see " +
        "its pictures (Now playing, Wrapped and its lyrics card: swipe through them), tap it again to put it on. Hold one or tap its \u22EF " +
        "(right-click on the PC) to edit, favourite or delete it", false,
        note = if (presets.isEmpty()) "None yet: set the tab up the way you like it, then Save" else "") {
        if (presets.isNotEmpty()) {
            // The one that matches what's picked now, if one does
            val here = MyPresets.now("")
            val current = presets.firstOrNull { p -> p.look == here.look && p.theme == here.theme && p.layout == here.layout &&
                p.own == here.own && (p.own || p.fx == here.fx) && p.npVis == here.npVis && p.dual == here.dual &&
                (!p.dual || p.npBottom == here.npBottom) && (p.wrapped.isEmpty() || p.wrapped == here.wrapped) &&
                (p.card.isEmpty() || p.card == here.card) && (p.visColor.isEmpty() || p.visColor == here.visColor) }?.key.orEmpty()
            val opts = Favs.sorted(Favs.PRESETS, presets.map { it.key to MyPresets.label(it) })
            ChoicePill(if (current.isEmpty()) listOf("" to "Pick one") + opts else opts, current,
                describe = { MyPresets.describe(it) }, twice = true, preview = { k -> if (k.isNotEmpty()) PresetPreview(k) },
                menu = { k, l -> if (k.isEmpty()) emptyList() else Favs.menu(Favs.PRESETS, k, l) }) { if (it.isNotEmpty()) MyPresets.apply(it) }
        }
    }
    androidx.compose.foundation.layout.FlowRow(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (editing != null) {
            Pill("Save changes to “${editing.name}”") {
                ui.askName = Triple("Name this preset", editing.name) { n: String ->
                    MyPresets.save(n, editing.key); MyPresets.editing = null; Music.toast("Saved “${n.trim()}”") }
            }
            Pill("Save as new") {
                ui.askName = Triple("Name this preset", editing.name + " 2") { n: String ->
                    MyPresets.save(n); MyPresets.editing = null; Music.toast("Saved “${n.trim()}”") }
            }
            Pill("Cancel", quiet = true) { MyPresets.editing = null }
        } else Pill("Save as a preset") {
            ui.askName = Triple("Name this preset", "My preset ${MyPresets.mine().size + 1}") { n: String ->
                MyPresets.save(n); Music.toast("Saved “${n.trim()}”: it's in Personalization presets") }
        }
    }
    if (editing != null) Text("Editing “${editing.name}”: change anything on this tab, then Save changes", style = T.sub,
        modifier = Modifier.padding(bottom = 8.dp))
}

