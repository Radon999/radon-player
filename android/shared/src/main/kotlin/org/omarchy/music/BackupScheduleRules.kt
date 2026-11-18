package org.omarchy.music

/** Back up by itself (Settings › Backup): every day or every week the app saves a backup of its own accord, in its own
 *  folder, and keeps the newest five. It is the same backup as Back up… makes, so any of them restores the same way;
 *  Save a copy puts the newest one wherever you choose (the phone's own folder goes when the app is uninstalled). */
object BackupScheduleRules {
    const val KEEP = 5
    val CHOICES = listOf("off" to "Off", "daily" to "Every day", "weekly" to "Every week")

    /** Whether one is due. every: "off", "daily" or "weekly"; lastAt, now: seconds (lastAt 0: never yet). */
    fun due(every: String, lastAt: Long, now: Long): Boolean = when (every) {
        "daily" -> now - lastAt >= 86_400L
        "weekly" -> now - lastAt >= 7 * 86_400L
        else -> false
    }

    /** A backup's file name for a day: "Radon Player backup 2026-11-18.zip" (one a day; a second that day replaces it). */
    fun name(day: String) = "Radon Player backup $day.zip"

    /** Of the backups there are (their names), the ones to delete: all but the newest [KEEP] (the names sort by date). */
    fun old(names: List<String>): List<String> = names.filter { it.startsWith("Radon Player backup ") && it.endsWith(".zip") }.sortedDescending().drop(KEEP)
}
