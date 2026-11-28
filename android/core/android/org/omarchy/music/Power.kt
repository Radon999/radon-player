package org.omarchy.music

/** The phone's power: plugged in or not, and Android's own battery saver. */
object Power {
    fun pluggedIn(): Boolean =
        (Music.ctx.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager).isCharging
    fun systemSaver(): Boolean =
        (Music.ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode
}
