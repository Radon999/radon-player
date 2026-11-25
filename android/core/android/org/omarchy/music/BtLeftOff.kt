package org.omarchy.music

/** What Ignore Bluetooth devices: no stutter at all (Shizuku; taken out in 5.0.1) may have left switched off on the
 *  phone: the truck's Media audio and Calls connections (Android's Bluetooth settings keep those), and Android Auto
 *  stopped (it can't start by itself until it's opened once). The app can't switch them back without Shizuku, so the
 *  first time it runs after the update a notification says what to do, and tapping it opens Bluetooth settings. Once:
 *  what it noted is forgotten as soon as the notification is shown. Main thread (BtRoute.check). */
object BtLeftOff {
    private const val CHANNEL = "truck"
    private const val NOTE_ID = 4711

    fun check() {
        val keys = BtIgnoreRules.parse(Prefs.text(Prefs.IGNORE_BT_MEDIA_OFF))
        val autoStopped = Prefs.on(Prefs.IGNORE_BT_AUTO_STOPPED)
        if (keys.isEmpty() && !autoStopped && !Prefs.on(Prefs.IGNORE_BT_SHIZUKU) && Prefs.text(Prefs.IGNORE_BT_AUTO).isEmpty()) return
        // Kept as "name" (Media audio) or "name|profile" (calls, phonebook, messages)
        val names = keys.map { k -> k.substringBeforeLast('|').takeIf { k.substringAfterLast('|', "").toIntOrNull() != null } ?: k }.distinct()
        Prefs.setText(Prefs.IGNORE_BT_MEDIA_OFF, ""); Prefs.set(Prefs.IGNORE_BT_AUTO_STOPPED, false)
        Prefs.set(Prefs.IGNORE_BT_SHIZUKU, false); Prefs.setText(Prefs.IGNORE_BT_AUTO, "")
        if (names.isEmpty() && !autoStopped) return
        val truck = names.joinToString(", ").ifEmpty { "your car" }
        val text = listOfNotNull(
            "In Bluetooth settings tap ⚙ by $truck and switch Calls and Audio (Media audio) on.".takeIf { names.isNotEmpty() },
            "Open Android Auto once, so it can start by itself again.".takeIf { autoStopped }).joinToString(" ")
        Crashes.note("Ignore Bluetooth devices: no stutter at all is gone (Shizuku). It may have left switched off: $text")
        Music.toast("Switch $truck back on: $text")
        val c = Music.ctx
        val nm = c.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(android.app.NotificationChannel(CHANNEL, "The car (Ignore Bluetooth devices)",
                android.app.NotificationManager.IMPORTANCE_DEFAULT))
        val open = android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = android.app.PendingIntent.getActivity(c, NOTE_ID, open, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        val n = android.app.Notification.Builder(c, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Switch $truck back on")
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText("Ignore Bluetooth devices: no stutter at all (Shizuku) is gone; " +
                "Ignore Bluetooth devices carries on as before. $text Tap here for Bluetooth settings. Shizuku isn't needed any more."))
            .setContentIntent(pi).setAutoCancel(true).build()
        runCatching { nm.notify(NOTE_ID, n) }
    }
}
