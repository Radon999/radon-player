package org.omarchy.music

import java.io.File

/** The PC's power: whether it runs from the mains (a desktop PC always does; a laptop says). It has no battery saver
 *  of its own that the app follows: only the app's switch. */
object Power {
    private val windows = System.getProperty("os.name").lowercase().contains("win")

    private interface K32 : com.sun.jna.Library { fun GetSystemPowerStatus(status: com.sun.jna.Pointer): Boolean }
    private val k32: K32 by lazy { com.sun.jna.Native.load("kernel32", K32::class.java) }

    fun pluggedIn(): Boolean = runCatching {
        if (windows) {
            // GetSystemPowerStatus fills 12 bytes; the first: 0 on battery, 1 on the mains, 255 not known
            val st = com.sun.jna.Memory(12)
            if (!k32.GetSystemPowerStatus(st)) return true
            st.getByte(0).toInt() != 0
        } else {
            // Linux: a battery that says it is running down
            File("/sys/class/power_supply").listFiles().orEmpty()
                .none { runCatching { File(it, "status").readText().trim() }.getOrDefault("") == "Discharging" }
        }
    }.getOrDefault(true)

    fun systemSaver(): Boolean = false
}
