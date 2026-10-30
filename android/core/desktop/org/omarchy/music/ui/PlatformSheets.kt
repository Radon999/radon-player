package org.omarchy.music.ui

import androidx.compose.foundation.background
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
    val lastfm = url.startsWith("https://www.last.fm/api/auth")
    val by = spotify || lastfm
    val cookieKey = when {
        url.startsWith("https://accounts.google.com") -> "ytm"
        url.startsWith("https://music.apple.com") -> "apple"
        else -> null
    }
    fun open() { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(if (cookieKey == "ytm") "https://music.youtube.com" else url)) } }
    if (cookieKey != null) {
        // YouTube Music and Apple Music: sign in in your browser, then paste its cookies (no web page inside the app here)
        Dialog(onDismissRequest = { done(null) }) {
            Column(Modifier.widthIn(max = 560.dp).background(C.light, R.card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sign in to ${if (cookieKey == "ytm") "YouTube Music" else "Apple Music"}", style = T.title)
                Text(if (cookieKey == "ytm")
                    "1. Open music.youtube.com in your browser and sign in.\n2. Press F12, open the Network tab and reload the page.\n" +
                        "3. Click the first request to music.youtube.com and, under Request Headers, copy the whole Cookie value.\n4. Paste it here."
                    else "1. Open music.apple.com in your browser and sign in.\n2. Press F12, open Application (Chrome, Edge) or Storage (Firefox) › Cookies › music.apple.com.\n" +
                        "3. Copy the value of media-user-token.\n4. Paste it here.", style = T.sub)
                Text("It stays on this PC, in Radon Player's settings, and works like the sign-in on the phone. Sign out forgets it.", style = T.sub.copy(color = C.muted))
                Pill("Open ${if (cookieKey == "ytm") "music.youtube.com" else "music.apple.com"}") { open() }
                Field(pasted, if (cookieKey == "ytm") "The Cookie value" else "The media-user-token") { pasted = it; wrong = false }
                if (wrong) Text(if (cookieKey == "ytm") "That doesn't hold the sign-in (it has no __Secure-3PAPISID): copy the whole Cookie value after signing in."
                    else "That isn't a media-user-token: copy its value after signing in.", style = T.sub.copy(color = C.red))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                    Pill("Cancel", quiet = true) { done(null) }
                    Pill("Sign in") { if (org.omarchy.music.Services.setCookies(cookieKey, pasted)) done("cookies") else wrong = true }
                }
            }
        }
        return
    }
    if (spotify) DisposableEffect(url) {
        // Spotify sends the browser on to http://127.0.0.1:8898/callback?code=…: answer it here
        val back = java.net.URI(org.omarchy.music.Spotify.REDIRECT)
        val server = runCatching {
            com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", back.port), 4).apply {
                createContext(back.path) { ex ->
                    val q = ex.requestURI.rawQuery ?: ""
                    val ok = "code=" in q
                    val msg = (if (ok) "Spotify is connected to Radon Player. You can close this tab."
                        else "Spotify didn't connect: ${Regex("error=([^&]*)").find(q)?.groupValues?.get(1) ?: "no code came back"}").toByteArray()
                    ex.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
                    ex.sendResponseHeaders(200, msg.size.toLong()); ex.responseBody.use { it.write(msg) }
                    val full = "${org.omarchy.music.Spotify.REDIRECT}?$q"
                    Music.scope.launch(Dispatchers.Main) { if (ok) done(full) else problem = String(msg) }
                }
                start()
            }
        }.onFailure { problem = "Couldn't listen for Spotify on port ${back.port} (is the old app signing in to Spotify?): close it and try again." }.getOrNull()
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
    Dialog(onDismissRequest = { done(null) }) {
        Column(Modifier.widthIn(max = 520.dp).background(C.light, R.card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sign in", style = T.title)
            if (by) {
                Text("The ${if (spotify) "Spotify" else "Last.fm"} page opened in your browser. ${if (spotify) "Sign in and agree" else "Approve Radon Player"} " +
                    "there: the app notices by itself, nothing to copy.", style = T.sub)
                Pill("Open the page again") { open() }
                if (problem.isNotEmpty()) Text(problem, style = T.sub.copy(color = C.red))
                else Text("Waiting for ${if (spotify) "Spotify" else "Last.fm"}…", style = T.sub.copy(color = C.muted))
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
