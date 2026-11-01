package org.omarchy.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.omarchy.music.Discord
import org.omarchy.music.Music

/** Under Discord in Settings (the PC): the name Discord shows. Discord shows the name of the application on its developer
 *  portal whose ID the app uses, so making one called "Radon Player" and pasting its ID here makes it say "Listening to
 *  Radon Player". Empty: the built-in one. */
@Composable
fun DiscordAppId() {
    if (!Discord.supported) return
    var open by remember { mutableStateOf(false) }
    var id by remember { mutableStateOf(if (Discord.ownAppId) Discord.appId else "") }
    val links = LocalUriHandler.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.End) {
        Text(if (Discord.ownAppId) "Your Discord application: ${Discord.appId}" else "Discord shows the built-in name (VarMusic)",
            style = T.sub, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Pill(if (open) "Close" else "Change the name", quiet = true) { open = !open }
    }
    if (!open) return
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("Discord shows the name of the Discord application the app uses, and it can only be set on Discord's site. " +
            "To have it say \"Listening to Radon Player\":", style = T.sub)
        Text("1. Open discord.com/developers/applications and sign in with your Discord account.", style = T.sub)
        Text("2. Tap New Application, name it Radon Player and create it.", style = T.sub)
        Text("3. Copy its Application ID (on its General Information page) and paste it here, then Save.", style = T.sub)
        Field(id, "Application ID (a long number)") { id = it.filter { c -> c.isDigit() } }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.End) {
            Pill("Open Discord's site", quiet = true) { runCatching { links.openUri("https://discord.com/developers/applications") } }
            Spacer(Modifier.width(6.dp))
            if (Discord.ownAppId) {
                Pill("Use the built-in one", quiet = true) { Discord.setAppId(""); id = ""; open = false; Music.toast("Using the built-in Discord name") }
                Spacer(Modifier.width(6.dp))
            }
            Pill("Save") {
                if (Discord.setAppId(id)) { open = false; Music.toast(if (id.isEmpty()) "Using the built-in Discord name" else "Saved: Discord shows your application's name") }
                else Music.toast("That isn't an Application ID: it's a number of 17 to 20 digits")
            }
        }
    }
}
