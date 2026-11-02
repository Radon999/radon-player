package org.omarchy.music.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.omarchy.music.Music

/** Whether the app may read the music on the phone, and a way to ask. Once Android stops
 *  showing its prompt (asked twice and refused), asking opens the app's settings instead;
 *  coming back from there is noticed. */
class MusicAccess(val granted: Boolean, val ask: () -> Unit)

private val AUDIO = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

private fun Context.activity(): Activity? = this as? Activity ?: (this as? ContextWrapper)?.baseContext?.activity()

@Composable
fun rememberMusicAccess(): MusicAccess {
    val ctx = LocalContext.current
    fun has() = ContextCompat.checkSelfPermission(ctx, AUDIO) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(has()) }
    var refused by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        refused = !ok
        if (ok) Music.rescan()
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watch = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && !granted && has()) { granted = true; Music.rescan() }
        }
        owner.lifecycle.addObserver(watch)
        onDispose { owner.lifecycle.removeObserver(watch) }
    }
    return MusicAccess(granted) {
        val act = ctx.activity()
        if (refused && act != null && !act.shouldShowRequestPermissionRationale(AUDIO)) {
            Music.toast("Turn on Music and audio under Permissions")
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        } else launcher.launch(AUDIO)
    }
}

