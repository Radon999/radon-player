package org.omarchy.music

/** A cover's colour as something drawn in it shows (the visualizer's "Album cover's colour"). A cover's main colour
 *  can be very dark (a black sleeve, a night photo); bars drawn in it wouldn't be seen on a dark screen. So it is
 *  kept as it is when it's bright enough, and lifted, its hue kept, when it's too dark; black becomes a plain grey. */
object CoverColourRules {
    private const val ENOUGH = 105   // how bright (0..255, as the eye weighs red, green and blue) it has to be

    /** rgb (0xRRGGBB, any alpha) as it is drawn: opaque, and bright enough to see. */
    fun shown(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF; val g = (rgb shr 8) and 0xFF; val b = rgb and 0xFF
        val lum = (299 * r + 587 * g + 114 * b) / 1000
        if (lum >= ENOUGH) return opaque(r, g, b)
        if (maxOf(r, g, b) < 12) return opaque(128, 128, 128)   // black: no hue to keep
        // Brighter by the same factor on each channel (its hue kept); what one channel can't take goes towards white
        val k = ENOUGH.toDouble() / maxOf(lum, 1)
        var nr = r * k; var ng = g * k; var nb = b * k
        val over = maxOf(nr, ng, nb)
        if (over > 255) { val t = (over - 255) / over; nr = nr + (255 - nr) * t; ng = ng + (255 - ng) * t; nb = nb + (255 - nb) * t }
        return opaque(nr.toInt().coerceIn(0, 255), ng.toInt().coerceIn(0, 255), nb.toInt().coerceIn(0, 255))
    }

    private fun opaque(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
