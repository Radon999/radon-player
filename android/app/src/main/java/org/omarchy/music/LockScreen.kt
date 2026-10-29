package org.omarchy.music

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import org.omarchy.music.ui.ApplePlayer
import org.omarchy.music.ui.OmarchyTheme
import org.omarchy.music.ui.Sheets
import org.omarchy.music.ui.Ui

/** The lock screen player (Settings › Playback › Lock screen player): when the screen turns on while music plays
 *  and the phone is locked, Now playing shows over the lock screen, the queue and lyrics and all; ⌄ goes on to the
 *  phone's own lock screen. Android lets an app put a screen up then only through a full-screen notification,
 *  which newer Androids let you allow per app (Settings asks, once). */
object LockScreen {
    private const val CHANNEL = "lock-player"
    private const val ID = 7301

    fun allowed(ctx: Context): Boolean = Build.VERSION.SDK_INT < 34 ||
        (ctx.getSystemService(NotificationManager::class.java)).canUseFullScreenIntent()

    /** Android's page where the app is allowed full-screen notifications (Android 14 and up). */
    fun askAllowed(ctx: Context) {
        val i = if (Build.VERSION.SDK_INT >= 34) Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            android.net.Uri.parse("package:" + ctx.packageName)) else Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Listens for the screen turning on (registered by the music's service while it runs). */
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) screenOn(ctx)
            else if (intent.action == Intent.ACTION_USER_PRESENT) cancel(ctx)
        }
    }

    fun register(ctx: Context) {
        runCatching { androidx.core.content.ContextCompat.registerReceiver(ctx, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) }, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED) }
    }

    fun unregister(ctx: Context) { runCatching { ctx.unregisterReceiver(receiver) } }

    private fun screenOn(ctx: Context) {
        if (!Prefs.on(Prefs.LOCK_SCREEN) || Music.paused || Music.current() == null) return
        val km = ctx.getSystemService(KeyguardManager::class.java)
        if (!km.isKeyguardLocked) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Lock screen player", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shows Now playing over the lock screen when the screen turns on"
                setSound(null, null); enableVibration(false); setShowBadge(false)
            })
        val pi = PendingIntent.getActivity(ctx, 3, Intent(ctx, LockScreenActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.media3_notification_small_icon)
            .setContentTitle(Music.current()?.title ?: "Radon Player").setCategory(Notification.CATEGORY_TRANSPORT)
            .setFullScreenIntent(pi, true).setAutoCancel(true).setTimeoutAfter(4000).build()
        runCatching { nm.notify(ID, n) }
    }

    fun cancel(ctx: Context) { runCatching { ctx.getSystemService(NotificationManager::class.java).cancel(ID) } }
}

class LockScreenActivity : ComponentActivity() {
    private var closer: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        LockScreen.cancel(this)
        enableEdgeToEdge()
        // Unlocked (by fingerprint, say): out of the way, the phone opens as it was.
        closer = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) = finish() }
        runCatching { androidx.core.content.ContextCompat.registerReceiver(this, closer, IntentFilter(Intent.ACTION_USER_PRESENT),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED) }
        setContent {
            OmarchyTheme {
                val ui = remember { Ui() }
                ApplePlayer(ui, lock = { finish() })
                Sheets(ui)
            }
        }
    }

    override fun onDestroy() {
        closer?.let { runCatching { unregisterReceiver(it) } }
        super.onDestroy()
    }
}
