package org.omarchy.music

import java.io.File

/** The PC's power: whether it runs from the mains (a desktop PC always does; a laptop says). It has no battery saver
 *  of its own that the app follows: only the app's switch. */
object Power {
    private val windows = System.getProperty("os.name").lowercase().contains("win")

    fun pluggedIn(): Boolean = runCatching {
        if (windows) {
            val st = com.sun.jna.platform.win32.WinBase.SYSTEM_POWER_STATUS()
            if (!com.sun.jna.platform.win32.Kernel32.INSTANCE.GetSystemPowerStatus(st)) return true
            st.ACLineStatus.toInt() != 0   // 0: on battery; 1: mains; 255: not known
        } else {
            // Linux: a battery that says it is running down
            File("/sys/class/power_supply").listFiles().orEmpty()
                .none { runCatching { File(it, "status").readText().trim() }.getOrDefault("") == "Discharging" }
        }
    }.getOrDefault(true)

    fun systemSaver(): Boolean = false
}
