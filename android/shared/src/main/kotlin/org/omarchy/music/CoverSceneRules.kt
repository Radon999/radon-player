package org.omarchy.music

/** The Now playing preset ("Follows the cover"): with it on, not only the colours come from the cover of the song
 *  playing (the Now playing look does that) but a whole scene chosen to go with them: effects behind everything and two
 *  visualizers (one round the cover, one along the bottom), all drawn in the cover's colour. So every song changes the
 *  app, and there is a scene ready for any cover: twelve by colour, three for covers with no colour to speak of
 *  (black-and-white, grey, misty), one for very dark covers and one for very pale ones. The same cover always gives
 *  the same scene. */
object CoverSceneRules {
    data class Scene(val name: String, val fx: List<String>, val vis: String, val bottom: String, val stars: Boolean = false)

    val NOIR = Scene("Noir", listOf("grain", "vignette", "spotlight"), "lissajous", "echoes")
    val SILVER = Scene("Silver", listOf("scanlines", "sheen"), "clockwork", "dotbars")
    val MIST = Scene("Mist", listOf("fog", "clouds"), "ripples", "curtain")
    val NIGHT = Scene("Night", listOf("twinkle", "constellation", "moon"), "starfield", "echoes", stars = true)
    val DAYLIGHT = Scene("Daylight", listOf("bokeh", "sunbeams"), "flower", "ribbon")

    /** By colour, round the wheel from red: each a slice of 30 degrees (red is the slice either side of 0). */
    val BY_HUE: List<Scene> = listOf(
        Scene("Embers", listOf("embers", "lava"), "vortex", "flames"),
        Scene("Lantern light", listOf("lanterns", "sunbeams"), "sunburst", "hills"),
        Scene("Fireflies", listOf("fireflies", "drops"), "sunflower", "grass"),
        Scene("Meadow", listOf("spores", "pebbles"), "lotus", "toadstools"),
        Scene("Forest", listOf("pines", "fog"), "mandala", "coralbars"),
        Scene("Jungle pool", listOf("leaves", "waves"), "iris", "tides"),
        Scene("Reef", listOf("coral", "bubbles"), "anemone", "tides"),
        Scene("Open sky", listOf("clouds", "kites"), "kite", "ribbon"),
        Scene("Rain", listOf("rain", "clouds"), "pulse", "rain"),
        Scene("Galaxy", listOf("nebula", "spiral", "twinkle"), "galaxy", "echoes", stars = true),
        Scene("Dream", listOf("orbs", "drift", "sparkle"), "orbits", "bubbles"),
        Scene("Blossom", listOf("petals", "hearts"), "heart", "bounce"),
    )
    val ALL: List<Scene> = listOf(NOIR, SILVER, MIST, NIGHT, DAYLIGHT) + BY_HUE

    /** A colour's hue (0 to 360), saturation and lightness (0 to 1). */
    fun hsl(rgb: Int): Triple<Float, Float, Float> {
        val r = ((rgb shr 16) and 0xFF) / 255f; val g = ((rgb shr 8) and 0xFF) / 255f; val b = (rgb and 0xFF) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min; val l = (max + min) / 2f
        if (d < 0.0001f) return Triple(0f, 0f, l)
        val s = d / (1f - kotlin.math.abs(2f * l - 1f)).coerceAtLeast(0.0001f)
        val h = when (max) { r -> 60f * (((g - b) / d) % 6f); g -> 60f * ((b - r) / d + 2f); else -> 60f * ((r - g) / d + 4f) }
        return Triple(if (h < 0) h + 360f else h, s.coerceIn(0f, 1f), l)
    }

    /** A colour from hue (0 to 360), saturation and lightness (0 to 1), as 0xRRGGBB. */
    fun rgb(h: Float, s: Float, l: Float): Int {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s; val hh = ((h % 360f) + 360f) % 360f / 60f
        val x = c * (1f - kotlin.math.abs(hh % 2f - 1f)); val m = l - c / 2f
        val (r, g, b) = when (hh.toInt()) { 0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x); 3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x) }
        fun ch(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** The whole app's background with this preset: the cover as if blurred right out behind everything, its own
     *  colours running down the window (bands: the cover's colour at its top, middle and bottom). Each keeps its hue,
     *  with its brightness brought into the range white text reads on and its colour calmed a little, so a cover that
     *  is teal above, red in the middle and navy below gives a window that is too. Fewer than two bands: null. */
    fun backdrop(bands: List<Int>): List<Int>? {
        if (bands.size < 2) return null
        return bands.map { val (h, s, l) = hsl(it); rgb(h, s.coerceAtMost(0.7f), l.coerceIn(0.15f, 0.36f)) }
    }

    /** The scene for a cover's main colour (0xRRGGBB). null (no cover, nothing playing): none, the app as you set it. */
    fun scene(rgb: Int?): Scene? {
        if (rgb == null) return null
        val (h, s, l) = hsl(rgb)
        return when {
            s < 0.12f -> if (l < 0.25f) NOIR else if (l > 0.72f) MIST else SILVER
            l < 0.14f -> NIGHT
            l > 0.82f -> DAYLIGHT
            else -> BY_HUE[(((h + 15f) % 360f) / 30f).toInt().coerceIn(0, 11)]
        }
    }
}
