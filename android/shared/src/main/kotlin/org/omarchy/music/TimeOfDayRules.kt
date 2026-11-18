package org.omarchy.music

/** Presets by time of day (Settings › Personalization): a Personalization preset of your choosing for the morning,
 *  the day, the evening and the night, put on by itself when that part of the day begins (and when the app opens in
 *  it). A part with no preset chosen leaves things as they are. */
object TimeOfDayRules {
    /** The parts of the day, in order: its key, its name, and the hours it runs (from, up to but not including). */
    val PARTS = listOf(Triple("morning", "Morning", 6 to 11), Triple("day", "Day", 11 to 17), Triple("evening", "Evening", 17 to 22), Triple("night", "Night", 22 to 6))

    /** The part of the day an hour (0 to 23) is in. */
    fun part(hour: Int): String = when (((hour % 24) + 24) % 24) { in 6..10 -> "morning"; in 11..16 -> "day"; in 17..21 -> "evening"; else -> "night" }

    /** What it is called with its hours: "Morning (6:00 to 11:00)". */
    fun label(key: String): String = PARTS.firstOrNull { it.first == key }?.let { "${it.second} (${it.third.first}:00 to ${it.third.second}:00)" } ?: key

    /** Whether to put a preset on now: it is on, this part has a preset, and it wasn't already put on for this part
     *  today (last: "part@day of the year", as kept when it last did). */
    fun due(on: Boolean, preset: String, part: String, dayOfYear: Int, last: String): Boolean = on && preset.isNotBlank() && last != "$part@$dayOfYear"
}
