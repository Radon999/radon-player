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

/** A sign-in: the page opens in your browser; once you've signed in, paste the address it ended on (the
 *  services send you back to an address with the sign-in's answer in it). done(url) when isDone says that's the one. */
@Composable
fun WebLogin(url: String, isDone: (String) -> Boolean, desktop: Boolean = false, done: (String?) -> Unit) {
    var pasted by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { done(null) }) {
        Column(Modifier.widthIn(max = 520.dp).background(C.light, R.card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sign in", style = T.title)
            Text("The sign-in page opens in your browser. Sign in there; when it's done, the page goes on to another address " +
                "(it may say it can't be reached: that's fine). Copy that whole address from the address bar and paste it here.", style = T.sub)
            Pill("Open the sign-in page") { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) } }
            Field(pasted, "The address it ended on") { pasted = it; wrong = false }
            if (wrong) Text("That isn't the address the sign-in ends on: copy the whole address after signing in.", style = T.sub.copy(color = C.red))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                Pill("Cancel", quiet = true) { done(null) }
                Pill("Continue") { val u = pasted.trim(); if (isDone(u)) done(u) else wrong = true }
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
