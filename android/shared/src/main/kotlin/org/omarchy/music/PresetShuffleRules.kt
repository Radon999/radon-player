package org.omarchy.music

/** What Personalization Preset Shuffle changes. A preset is a whole set of choices (a look, a theme, a layout, effects,
 *  visualizers…); the shuffle puts on a favourite preset every few minutes. Each kind of choice can be left out of the
 *  shuffle, so it stays as you set it while the others change: shuffle the visualizers, say, and keep your look. */
object PresetShuffleRules {
    /** The kinds of choice a preset holds (each a dropdown of the Personalization tab), and what each is called. */
    val PARTS = linkedMapOf(
        "look" to "Look", "theme" to "Theme", "layout" to "Layout", "effects" to "Effects",
        "nowplaying" to "Now playing visual", "visualizer" to "Visualizer", "viscolor" to "Visualizer colour",
        "wrapped" to "Wrapped style", "card" to "Lyrics card")

    /** The kinds left out, as they're kept in the settings (their keys, comma-separated). */
    fun read(text: String?): Set<String> = text.orEmpty().split(',').map { it.trim() }.filterTo(LinkedHashSet()) { it in PARTS }
    fun write(skip: Set<String>): String = PARTS.keys.filter { it in skip }.joinToString(",")

    /** One kind left out, or taken back in. */
    fun toggled(skip: Set<String>, part: String): Set<String> = if (part !in PARTS) skip else if (part in skip) skip - part else skip + part

    /** What the settings say about it. */
    fun note(skip: Set<String>): String = when {
        skip.isEmpty() -> "Nothing left out: the shuffle changes everything a preset holds"
        skip.size == PARTS.size -> "Everything is left out: the shuffle changes nothing"
        else -> "Left as you set them: " + PARTS.filterKeys { it in skip }.values.joinToString(", ")
    }
}
