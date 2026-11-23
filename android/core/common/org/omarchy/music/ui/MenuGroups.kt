package org.omarchy.music.ui

/** The Personalization menus hold a lot (Looks alone are over ninety), so each is shown in a few sections that fold
 *  open and shut: only the section the one in use is in starts open. This says which section each choice is in. The
 *  choices themselves are all still there; nothing is taken out. Your favourites and the ones you made get sections
 *  of their own (★ Favourites is put together by the menu itself). */
object MenuGroups {
    const val YOURS = "Yours"
    private fun yours(k: String) = k.startsWith("my") || k.startsWith("mine-")

    /** The thirty looks (and their blends, Wrapped styles and so on) added in 4.0.14. */
    val NEW = setOf("stainedglass", "patina", "blueprint", "terrazzo", "artdeco", "bauhaus", "jade", "coralreef", "amber", "inkwash",
        "denim", "velvet", "glacier", "obsidian", "tropical", "carnival", "origami", "marble", "moss", "peacock", "lighthouse", "teahouse",
        "crayon", "lanternfest", "cartridge", "glowshroom", "opal", "foundry", "plumwine", "kitesky")

    fun look(k: String): String = when {
        yours(k) -> YOURS
        k == "apple" || k == "apple-dark" || k == Looks.NOW_PLAYING -> "Basics"
        k.startsWith("neon-") -> "Neon"
        k in NEW -> if (Looks.isLight(k)) "Materials and places: light" else "Materials and places: dark"
        Looks.isLight(k) -> "Light"
        else -> "Dark"
    }

    fun theme(k: String): String {
        val s = Themes.ALL[k] ?: return "Lines, corners and weight"
        val sans = androidx.compose.ui.text.font.FontFamily.SansSerif
        return when {
            s.font != sans || s.titleFont != sans -> "Serif and monospace type"
            s.cards != CardStyle.NONE -> "Cards, glass and outlines"
            s.space != 1f || s.textScale != 1f || s.art != 1f || s.icon != 1f -> "Size and spacing"
            else -> "Lines, corners and weight"
        }
    }

    fun layout(k: String): String = if (yours(k)) YOURS else UiLayouts.all().firstOrNull { it.key == k }?.nav?.label ?: "☰ menu only"

    private val SKY = setOf("stars", "twinkle", "shooting", "aurora", "nebula", "constellation", "comets", "meteors", "moon", "planet", "spiral", "warp")
    private val NATURE = setOf("rain", "snow", "fireflies", "bubbles", "waves", "mountains", "petals", "leaves", "embers", "lightning", "clouds", "fog",
        "sea", "dunes", "pines", "coral", "pebbles", "feathers", "spores", "swirls", "steam", "icicles")
    private val PATTERNS = setOf("grid", "halftone", "stripes", "hexagons", "graph", "polka", "checker", "plaid", "circuit", "topo", "weave", "chips",
        "mosaic", "facets", "veins", "rivets", "draft", "shapes", "fans")
    private val THINGS = setOf("skyline", "confetti", "hearts", "notes", "drops", "blots", "drapes", "slices", "bunting", "cranes", "scribbles",
        "lanterns", "pixels", "kites")
    fun effect(k: String): String = when {
        k.startsWith("beat-") -> "Moves to the music"
        else -> effectKind(k)
    }
    private fun effectKind(k: String): String = when (k) {
        in SKY -> "Sky and space"
        in NATURE -> "Weather and nature"
        in PATTERNS -> "Patterns and textures"
        in THINGS -> "Things"
        else -> "Light and screen"
    }

    private val NEW_BOTTOM = MORE_VIS_BOTTOM.map { it.first }.toSet()
    private val NEW_CENTRED = MORE_VIS_CENTRED.map { it.first }.toSet()
    fun vis(k: String): String = when {
        yours(k) -> YOURS
        k == "slim" || k == "off" || k == "" -> "Basics"
        k in NEW_BOTTOM -> "Along the bottom: things"
        k in NEW_CENTRED -> "Around the middle: things"
        k in Vis.BUILT_CENTRED -> "Around the middle"
        else -> "Along the bottom"
    }

    fun visColour(k: String): String = when {
        yours(k) -> YOURS
        k == "auto" || k == "cover" || k == "" -> "Basics"
        k in Looks.NEON -> "Plain colours"
        k.startsWith("neon-") -> "Neon looks' blends"
        k in NEW -> "Materials and places"
        else -> "Blends"
    }

    fun wrapped(k: String): String = when {
        yours(k) -> YOURS
        k.startsWith("wrap-") -> "Materials and places"
        k in Looks.NEON -> "Neon"
        else -> "Basics"
    }

    /** Lyrics cards and presets: the first thirty are scenes (Midnight drive, Thunderstorm…), the next thirty are
     *  materials and places (Stained glass, Tea house…). */
    private fun numbered(k: String, prefix: String): String? = k.removePrefix(prefix).takeIf { it != k }?.toIntOrNull()?.let { if (it <= 30) "Scenes" else "Materials and places" }
    fun card(k: String): String = if (yours(k)) YOURS else numbered(k, "card-") ?: "Basics"
    fun preset(k: String): String = if (yours(k)) YOURS else numbered(k, "preset-") ?: "Basics"
}
