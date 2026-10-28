package org.omarchy.music

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import org.omarchy.music.ui.App
import org.omarchy.music.ui.OmarchyTheme

/** The window. Connecting to the playback service starts it; the music itself lives in [Music]. */
class MainActivity : ComponentActivity() {
    private var controller: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        shared(intent)
        androidx.core.content.ContextCompat.registerReceiver(this, miniButtons, android.content.IntentFilter(MINI),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent { OmarchyTheme { if (mini) org.omarchy.music.ui.MiniPlayer() else App() } }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(miniButtons) }
        super.onDestroy()
    }

    // ---- the mini player: Android's picture in picture, with previous, play/pause and next ----
    private val miniButtons = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: Intent) {
            when (i.getStringExtra("do")) {
                "prev" -> Music.previous()
                "play" -> Music.togglePause()
                "next" -> Music.next()
            }
        }
    }

    private fun miniParams(): android.app.PictureInPictureParams {
        fun act(icon: Int, title: String, what: String, code: Int) = android.app.RemoteAction(
            android.graphics.drawable.Icon.createWithResource(this, icon), title, title,
            android.app.PendingIntent.getBroadcast(this, code, Intent(MINI).setPackage(packageName).putExtra("do", what),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT))
        return android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(1, 1)).setActions(listOf(
            act(android.R.drawable.ic_media_previous, "Previous", "prev", 1),
            if (Music.paused) act(android.R.drawable.ic_media_play, "Play", "play", 2) else act(android.R.drawable.ic_media_pause, "Pause", "play", 2),
            act(android.R.drawable.ic_media_next, "Next", "next", 3))).build()
    }

    /** Into the mini player (from the menu). */
    fun enterMini() {
        runCatching { enterPictureInPictureMode(miniParams()) }.onFailure { Music.toast("This phone doesn't offer a mini player (picture in picture)") }
    }

    /** Its play/pause button, as things stand. */
    fun updateMini() { if (mini) runCatching { setPictureInPictureParams(miniParams()) } }

    override fun onPictureInPictureModeChanged(isIn: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isIn, newConfig)
        mini = isIn
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        shared(intent)
    }

    private fun shared(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) i.getStringExtra(Intent.EXTRA_TEXT)?.let { Music.shared = it }
    }

    /** The volume buttons, while the phone is the desktop's remote: the desktop's volume, playing or not
     *  (Android itself hands them to the desktop only while it plays). */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val up = event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP
        if ((up || event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) && Remote.hasVolume) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) Remote.stepVolume(up)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        visible = true
        Updater.resumed(this)
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    companion object {
        @Volatile var visible = false   // on screen: Android lets it open the installer's question
        var mini by androidx.compose.runtime.mutableStateOf(false)   // in the mini player (picture in picture)
        const val MINI = "org.omarchy.music.MINI"
    }

    override fun onStart() {
        super.onStart()
        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, MusicService::class.java))).buildAsync()
    }

    override fun onStop() {
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }
}
