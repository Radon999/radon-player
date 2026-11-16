package org.omarchy.music.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.omarchy.music.Music

/** Everything the app needs Android's leave for, asked for as the app starts, at every start until it is allowed, so
 *  each thing works the first time it is used (the visualizer on Now playing among them: Android gives an app the
 *  sound to draw only with the microphone permission).
 *
 *  First Android's own questions, one after another, for what it still asks about: your music, the microphone (the
 *  visualizer), notifications. Then, for what is left (All files access, which Android only gives on its own page; or
 *  one you refused twice, which Android no longer asks about), a box that says what is missing and why, and opens the
 *  page to turn it on. "Not now" closes the box until the next start. */
@Composable
fun StartupPermissions() {
    val ctx = LocalContext.current
    val audio = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    // What Android asks about itself: (the permission, what it is for)
    fun missing(): List<Pair<String, String>> = buildList {
        if (!has(audio)) add(audio to "Music and audio: to see the songs on this phone")
        if (!has(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO to
            "Microphone: for the visualizer (Android lets an app draw the sound playing only with this; nothing is recorded) and for speaking a search")
        if (Build.VERSION.SDK_INT >= 33 && !has(Perm.NOTIFY)) add(Perm.NOTIFY to "Notifications: the player in your notifications and on the lock screen")
    }
    var asked by remember { mutableStateOf(false) }   // Android's own questions have been put (or there were none to put)
    var later by remember { mutableStateOf(false) }   // Not now: until the next start
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { got ->
        asked = true
        if (got[audio] == true) Music.rescan()
        Music.resumed++   // what waited for a permission (the visualizer) looks again
    }
    LaunchedEffect(Unit) {
        val m = missing().map { it.first }
        if (m.isNotEmpty()) launcher.launch(m.toTypedArray()) else asked = true
    }
    val resumed = Music.resumed   // back from Android's settings: looked at again
    val left = remember(resumed, asked) { missing() }
    val files = remember(resumed, asked) { runCatching { !Music.canWriteOwnFiles() }.getOrDefault(false) }
    if (!asked || later || (left.isEmpty() && !files)) return
    AlertDialog(onDismissRequest = { later = true }, containerColor = C.light,
        title = { Text("Radon Player still needs your leave", style = T.head) },
        text = {
            Column {
                Text("So that everything works from the start, turn these on:", style = T.body, modifier = Modifier.padding(bottom = 6.dp))
                left.forEach { (_, why) -> Text("• $why", style = T.sub, modifier = Modifier.padding(vertical = 2.dp)) }
                if (files) Text("• All files access: to change your own songs' tags and covers, and to show the cover kept in a song's folder",
                    style = T.sub, modifier = Modifier.padding(vertical = 2.dp))
                Text(if (left.isNotEmpty()) "${TAP.cap()} Allow, open Permissions, and turn them on. Your music plays without them; you'll be asked again next time the app starts."
                    else "${TAP.cap()} Allow and turn it on for Radon Player. Your music plays without it; you'll be asked again next time the app starts.",
                    style = T.sub, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton({
            later = true
            if (left.isNotEmpty()) runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            else openSystemSettings("allfiles")
        }) { Text("Allow", color = C.accent) } },
        dismissButton = { TextButton({ later = true }) { Text("Not now", color = C.muted) } })
}
