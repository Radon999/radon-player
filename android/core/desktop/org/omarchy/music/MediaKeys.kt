package org.omarchy.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Windows: the keyboard's play/pause, next, previous and stop keys, taken system-wide (RegisterHotKey) on a thread
 *  of their own, as the Python app does. A key another app already holds stays with it. */
object MediaKeys {
    private val KEYS = mapOf(0xB3 to "toggle", 0xB0 to "next", 0xB1 to "previous", 0xB2 to "stop")

    fun start() {
        if (!System.getProperty("os.name").lowercase().contains("win") || System.getenv("OMARCHY_MUSIC_MPRIS") == "0") return
        Thread({ listen() }, "media-keys").apply { isDaemon = true }.start()
    }

    private fun listen() = runCatching {
        val u = com.sun.jna.platform.win32.User32.INSTANCE
        val ids = HashMap<Int, String>()
        for ((i, e) in KEYS.entries.withIndex())
            if (u.RegisterHotKey(null, i + 1, 0x4000 /* MOD_NOREPEAT */, e.key)) ids[i + 1] = e.value
        val msg = com.sun.jna.platform.win32.WinUser.MSG()
        while (u.GetMessage(msg, null, 0, 0) > 0) {
            if (msg.message == 0x0312 /* WM_HOTKEY */) {
                val act = ids[msg.wParam.toInt()] ?: continue
                Music.scope.launch(Dispatchers.Main) {
                    when (act) {
                        "toggle" -> Music.togglePause()
                        "next" -> Music.next()
                        "previous" -> Music.previous()
                        "stop" -> if (!Music.paused) Music.togglePause()
                    }
                }
            }
        }
    }
}
