package org.omarchy.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.omarchy.music.Music
import java.io.File

// The PC's side of the sheets that are platform-specific: the sign-in, removing files.

/** A sign-in: the page opens in your browser. Spotify's comes back to the app by itself (it listens on 127.0.0.1 for
 *  the address Spotify sends you on to) and Last.fm's is noticed by itself (the app asks Last.fm every few seconds),
 *  as on the Python app; for the others (Tidal), paste the address it ended on (the services send you back to an
 *  address with the sign-in's answer in it). done(url) when isDone says that's the one. */
@Composable
fun WebLogin(url: String, isDone: (String) -> Boolean, desktop: Boolean = false, done: (String?) -> Unit) {
    var pasted by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf("") }
    val spotify = url.startsWith("https://accounts.spotify.com")
    val soundcloud = url.startsWith("https://secure.soundcloud.com")
    val lastfm = url.startsWith("https://www.last.fm/api/auth")
    val by = spotify || soundcloud || lastfm
    val who = when { spotify -> "Spotify"; soundcloud -> "SoundCloud"; else -> "Last.fm" }
    val cookieKey = when {
        url.startsWith("https://accounts.google.com") -> "ytm"
        url.startsWith("https://music.apple.com") -> "apple"
        url.startsWith("https://www.deezer.com") -> "deezer"
        else -> null
    }
    fun open() { org.omarchy.music.Opening.linkOrCopy(if (cookieKey == "ytm") "https://music.youtube.com" else url) }
    val serviceName = when (cookieKey) { "ytm" -> "YouTube Music"; "deezer" -> "Deezer"; else -> "Apple Music" }
    val site = when (cookieKey) { "ytm" -> "music.youtube.com"; "deezer" -> "www.deezer.com"; else -> "music.apple.com" }
    if (cookieKey != null) {
        // YouTube Music and Apple Music: sign in in your browser, then paste its cookies (no web page inside the app here)
        Dialog(onDismissRequest = { done(null) }) {
            Column(Modifier.widthIn(max = 560.dp).background(C.light, R.card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sign in to $serviceName", style = T.title)
                Text(if (cookieKey == "deezer")
                    "1. Open www.deezer.com in your browser and sign in.\n2. Press F12, open Application (Chrome, Edge) or Storage (Firefox) › Cookies › www.deezer.com.\n" +
                        "3. Copy the value of arl.\n4. Paste it here."
                    else if (cookieKey == "ytm")
                    "1. Open music.youtube.com in your browser and sign in.\n2. Press F12, open the Network tab and reload the page.\n" +
                        "3. Click the first request to music.youtube.com and, under Request Headers, copy the whole Cookie value.\n4. Paste it here."
                    else "1. Open music.apple.com in your browser and sign in.\n2. Press F12, open Application (Chrome, Edge) or Storage (Firefox) › Cookies › music.apple.com.\n" +
                        "3. Copy the value of media-user-token.\n4. Paste it here.", style = T.sub)
                Text("It stays on this PC, in Radon Player's settings, and works like the sign-in on the phone. Sign out forgets it.", style = T.sub.copy(color = C.muted))
                Pill("Open $site") { open() }
                Field(pasted, when (cookieKey) { "ytm" -> "The Cookie value"; "deezer" -> "The arl value"; else -> "The media-user-token" }) { pasted = it; wrong = false }
                if (wrong) Text(if (cookieKey == "deezer") "That isn't an arl: copy the arl cookie's value after signing in."
                    else if (cookieKey == "ytm") "That doesn't hold the sign-in (it has no __Secure-3PAPISID): copy the whole Cookie value after signing in."
                    else "That isn't a media-user-token: copy its value after signing in.", style = T.sub.copy(color = C.red))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                    Pill("Cancel", quiet = true) { done(null) }
                    Pill("Sign in") { if (org.omarchy.music.Services.setCookies(cookieKey, pasted)) done("cookies") else wrong = true }
                }
            }
        }
        return
    }
    if (spotify || soundcloud) DisposableEffect(url) {
        // Spotify (SoundCloud) sends the browser on to http://127.0.0.1:8898/callback?code=… (…:8899/soundcloud): answer it here
        val redirect = if (soundcloud) org.omarchy.music.SoundCloud.REDIRECT else org.omarchy.music.Spotify.REDIRECT
        val back = java.net.URI(redirect)
        val server = runCatching {
            com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", back.port), 4).apply {
                createContext(back.path) { ex ->
                    val q = ex.requestURI.rawQuery ?: ""
                    val ok = "code=" in q
                    val msg = (if (ok) "$who is connected to Radon Player. You can close this tab."
                        else "$who didn't connect: ${Regex("error=([^&]*)").find(q)?.groupValues?.get(1) ?: "no code came back"}").toByteArray()
                    ex.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
                    ex.sendResponseHeaders(200, msg.size.toLong()); ex.responseBody.use { it.write(msg) }
                    val full = "$redirect?$q"
                    Music.scope.launch(Dispatchers.Main) { if (ok) done(full) else problem = String(msg) }
                }
                start()
            }
        }.onFailure { problem = "Couldn't listen for $who on port ${back.port} (is another app using it?): close it and try again." }.getOrNull()
        if (server != null) open()
        onDispose { runCatching { server?.stop(0) } }
    }
    if (lastfm) LaunchedEffect(url) {
        open()
        while (true) {
            kotlinx.coroutines.delay(3000)
            val r = kotlinx.coroutines.withContext(Dispatchers.IO) { runCatching { org.omarchy.music.LastFm.approvedYet() } }
            r.onSuccess { if (it) { done("approved"); return@LaunchedEffect } }
                .onFailure { problem = "Last.fm said: ${it.message?.take(80)}"; return@LaunchedEffect }
        }
    }
    // Tidal's (a sign-in that ends on an address to paste): its page opens by itself too, as the others' do and as
    // its own text says. Before, it only opened from the button.
    if (!by) LaunchedEffect(url) { open() }
    Dialog(onDismissRequest = { done(null) }) {
        Column(Modifier.widthIn(max = 520.dp).background(C.light, R.card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sign in", style = T.title)
            if (by) {
                Text("The $who page opened in your browser. ${if (spotify || soundcloud) "Sign in and agree" else "Approve Radon Player"} " +
                    "there: the app notices by itself, nothing to copy.", style = T.sub)
                Pill("Open the page again") { open() }
                if (problem.isNotEmpty()) Text(problem, style = T.sub.copy(color = C.red))
                else Text("Waiting for $who…", style = T.sub.copy(color = C.muted))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                    Pill("Cancel", quiet = true) { done(null) }
                }
            } else {
                Text("The sign-in page opens in your browser. Sign in there; when it's done, the page goes on to another address " +
                    "(it may say it can't be reached: that's fine). Copy that whole address from the address bar and paste it here.", style = T.sub)
                Pill("Open the sign-in page") { open() }
                Field(pasted, "The address it ended on") { pasted = it; wrong = false }
                if (wrong) Text("That isn't the address the sign-in ends on: copy the whole address after signing in.", style = T.sub.copy(color = C.red))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                    Pill("Cancel", quiet = true) { done(null) }
                    Pill("Continue") { val u = pasted.trim(); if (isDone(u)) done(u) else wrong = true }
                }
            }
        }
    }
}

/** Removing your files: trash puts them in the Recycle Bin (where the PC has one), else they're deleted. done(true) if
 *  they all went. */
@Composable
fun rememberFileRemoval(trash: Boolean, done: (Boolean) -> Unit): (List<String>) -> Unit = remember {
    { uris ->
        Music.scope.launch(Dispatchers.IO) {
            val ok = uris.all { u ->
                val f = File(if (u.startsWith("file:")) java.net.URI(u).path else u)
                runCatching {
                    if (trash && java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH))
                        java.awt.Desktop.getDesktop().moveToTrash(f)
                    else f.delete()
                }.getOrDefault(false)
            }
            launch(Dispatchers.Main) { done(ok) }
        }
    }
}

const val canRemoveFiles = true

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun screenHeightDp(): Int = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp().value.toInt() }

/** Settings › General on the PC: the desktop widgets (each opened or closed here, as from the tray icon's Widgets) and
 *  Make desktop shortcuts…. line: Settings' own row (its name, what it is, lit or not, its buttons). */
@Composable
fun PcWidgetLines(ui: Ui, line: @Composable (String, String, Boolean, @Composable () -> Unit) -> Unit) {
    line("Widgets", "Small windows of their own on your desktop, like the phone's widgets, in the colour of the cover playing. " +
        "Drag one by its body to move it and by its bottom corner to size it; its ⚙ keeps it above your other windows or not. " +
        "They come back where you left them when the app starts. Shortcuts shows the shortcuts you made as buttons. Also in the " +
        "tray icon's menu", DesktopWidgets.anyOpen) {}
    org.omarchy.music.DesktopWidgetRules.KINDS.forEach { (kind, name) ->
        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(name, style = T.body, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(DesktopWidgets.isOpen(kind), { DesktopWidgets.open(kind, it) }, colors = swColors())
        }
    }
    if (!org.omarchy.music.Shortcuts.supported) return
    var making by remember { mutableStateOf(false) }
    line("Desktop shortcuts", "One-click shortcuts, each with its own picture, in the ${org.omarchy.music.Shortcuts.menuName}" +
        (if (org.omarchy.music.Shortcuts.desktopWorks) ", on your desktop" else "") + " and on the Shortcuts widget: play a " +
        "playlist (in order or shuffled), start or stop the DJ, open Most played, Lyrics, New releases or Wrapped, play or pause, skip", false) {
        Pill("Make desktop shortcuts…", quiet = true) { making = true }
    }
    if (making) ShortcutsDialog { making = false }
}

/** Make desktop shortcuts…: tick the ones you want (the app's, and each playlist's In order or Shuffled), and where; and
 *  (Linux, a taskbar the app can add to) 📌 puts one on the taskbar or takes it off, the app itself too. On Windows, which
 *  lets no app pin itself, it says how to pin one by hand and opens their folder. */
@Composable
private fun ShortcutsDialog(close: () -> Unit) {
    val Rules = org.omarchy.music.DesktopWidgetRules
    val S = org.omarchy.music.Shortcuts
    val picked = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }   // by each one's key (its command)
    var menu by remember { mutableStateOf(true) }
    var desktop by remember { mutableStateOf(S.desktopWorks) }
    var widget by remember { mutableStateOf(!S.desktopWorks) }
    var busy by remember { mutableStateOf(false) }
    val lists = remember { Music.lists.all() + Music.lists.smart() }
    val all = remember(lists) { Rules.SHORTCUTS + lists.flatMap { listOf(Rules.playlistShortcut(it.name, false), Rules.playlistShortcut(it.name, true)) } }
    // The taskbar: whether there's one to add to, what's on it now (read back), and what you've asked for
    var pinnedNow by remember { mutableStateOf<Set<String>?>(null) }   // null: no taskbar the app can add to (or not read yet)
    var appId by remember { mutableStateOf<String?>(null) }
    val pinWant = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }   // by key; the app's: "app"
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            if (org.omarchy.music.Taskbar.supported) { appId = S.appTaskbarId(); pinnedNow = org.omarchy.music.Taskbar.pinned().toSet() }
        }
    }
    val boxColors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = C.accent, uncheckedColor = C.muted, checkmarkColor = C.darker)
    @Composable fun Tick(text: String, on: Boolean, modifier: Modifier = Modifier, change: (Boolean) -> Unit) {
        Row(modifier.then(Modifier.clickable { change(!on) }), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(on, change, colors = boxColors)
            Text(text, style = T.body)
        }
    }
    /** 📌: on the taskbar (lit) or not; a click turns it the other way, done with Make. */
    @Composable fun Pin(key: String, id: String) {
        val now = pinnedNow ?: return
        val on = pinWant[key] ?: (id in now)
        IconButton(Ico.PIN, if (on) C.accent else C.muted.copy(alpha = 0.45f), 16.dp, 6.dp, desc = if (on) "On the taskbar" else "Add to the taskbar") { pinWant[key] = !on }
    }
    Dialog(onDismissRequest = close) {
        Column(Modifier.widthIn(max = 600.dp).heightIn(max = 700.dp).background(C.light, R.card).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Make desktop shortcuts", style = T.title)
            Text("Each shortcut starts Radon Player with what it says, or tells the one already running to: one click and it's done. " +
                "Tick the ones you want." + if (pinnedNow != null) " The pin puts one on the taskbar (lit: it's there); click it again to take it off." else "", style = T.sub)
            androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f, fill = false)) {
                appId?.let { id -> if (pinnedNow != null) item {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Radon Player itself", style = T.body, modifier = Modifier.weight(1f).padding(start = 12.dp, top = 6.dp, bottom = 6.dp))
                        Pin("app", id)
                    }
                } }
                item { Text("THE APP", style = T.section, modifier = Modifier.padding(top = 6.dp)) }
                items(Rules.SHORTCUTS.size) { i ->
                    val s = Rules.SHORTCUTS[i]
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Tick(s.name, picked[s.key] == true, Modifier.weight(1f)) { picked[s.key] = it }
                        Pin(s.key, S.taskbarId(s))
                    }
                }
                if (lists.isNotEmpty()) item { Text("YOUR PLAYLISTS", style = T.section, modifier = Modifier.padding(top = 10.dp)) }
                items(lists.size) { i ->
                    val p = lists[i]
                    val inOrder = Rules.playlistShortcut(p.name, false); val shuffled = Rules.playlistShortcut(p.name, true)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(p.name, style = T.body, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        Tick("In order", picked[inOrder.key] == true) { picked[inOrder.key] = it }
                        Pin(inOrder.key, S.taskbarId(inOrder))
                        Tick("Shuffled", picked[shuffled.key] == true) { picked[shuffled.key] = it }
                        Pin(shuffled.key, S.taskbarId(shuffled))
                    }
                }
            }
            Text("WHERE", style = T.section, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Tick("In the ${S.menuName}", menu) { menu = it }
                // (Hyprland and the like show nothing on the desktop: the Shortcuts widget puts them on the screen instead)
                if (S.desktopWorks) Tick("On the desktop", desktop) { desktop = it }
            }
            Tick("Show them on the desktop as a Shortcuts widget", widget) { widget = it }
            // Windows lets no app pin itself to the taskbar: done by hand, from the Start menu's folder of them
            if (S.menuName == "Start menu") Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("On the taskbar: right-click a shortcut › Pin to taskbar, or drag it onto the taskbar.", style = T.sub, modifier = Modifier.weight(1f))
                Pill("Open the shortcuts folder", quiet = true) { S.openStartFolder() }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                Pill("Take away the ones made here", quiet = true) {
                    Music.bg({ S.removeAll() }) { msg, err ->
                        Music.toast(msg ?: "Couldn't: ${err?.message?.take(80)}")
                        pinWant.clear(); Music.bg({ org.omarchy.music.Taskbar.pinned().toSet() }) { p, _ -> if (p != null && pinnedNow != null) pinnedNow = p }
                    }
                }
                Pill("Cancel", quiet = true) { close() }
                Pill(if (busy) "Making…" else "Make") {
                    if (busy) return@Pill
                    val chosen = all.filter { picked[it.key] == true }
                    val pins = all.filter { pinWant.containsKey(it.key) }.associateWith { pinWant.getValue(it.key) }
                    val app = pinWant["app"]
                    if (chosen.isEmpty() && pins.isEmpty() && app == null) { Music.toast("Pick at least one shortcut"); return@Pill }
                    busy = true
                    val id = appId
                    Music.bg({
                        val made = if (chosen.isNotEmpty()) S.make(chosen, menu, desktop, widget) else ""
                        val bar = S.taskbar(pins, app, id)
                        made to bar
                    }) { res, err ->
                        busy = false
                        val (made, bar) = res ?: ("Couldn't: ${err?.message?.take(80)}" to "")
                        Music.toast(listOf(made, bar).filter { it.isNotEmpty() }.joinToString(". ").ifEmpty { "Nothing to change" })
                        if (made.startsWith("Made") || (made.isEmpty() && bar.isNotEmpty() && !bar.startsWith("Couldn't"))) close()
                    }
                }
            }
        }
    }
}
