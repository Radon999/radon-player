package org.omarchy.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The look: Apple Music's (Light or Dark), or Now playing's: the cover's colours on every screen.
// A sans-serif font, rounded corners, the accent for what's playing and what's on.
object C {
    var accent by mutableStateOf(Color(0xFF7AA2F7))
    var selection by mutableStateOf(Color(0xFF414868))
    var muted by mutableStateOf(Color(0xFF787C99))
    var bg by mutableStateOf(Color(0xFF1A1B26))
    var dark by mutableStateOf(Color(0xFF16161E))
    var darker by mutableStateOf(Color(0xFF111118))
    var light by mutableStateOf(Color(0xFF1F2335))
    var fg by mutableStateOf(Color(0xFFC0CAF5))
    var red by mutableStateOf(Color(0xFFF7768E))
    var green by mutableStateOf(Color(0xFF9ECE6A))
    var isLight by mutableStateOf(false)
    var glow by mutableStateOf(false)   // a neon look: what's on glows
    var gradient by mutableStateOf<List<Color>?>(null)   // a colourful look: the window is this gradient
    var stars by mutableStateOf(false)                   // the galaxy looks: a few stars over it
    var apple by mutableStateOf(false)                   // the Apple Music looks: plain pages, thin lines between rows
    /** Lines between rows and quiet borders (a theme can make them fainter, or take them away). */
    val line get() = selection.copy(alpha = 0.45f * Themes.current.line)
    /** A soft glow under accent text, in a neon look. */
    val shadow get() = if (glow) Shadow(accent.copy(alpha = 0.8f), blurRadius = 14f) else null
}

/** The looks: Light, Dark, Now playing, the themes, and a neon one per colour (for Wrapped). */
object Looks {
    val NEON = linkedMapOf("red" to ("Red" to Color(0xFFFF1F3D)), "blue" to ("Blue" to Color(0xFF2F7BFF)),
        "cyan" to ("Cyan" to Color(0xFF14E4FF)), "green" to ("Green" to Color(0xFF1FFF6E)), "lime" to ("Lime" to Color(0xFFB6FF1A)),
        "gold" to ("Gold" to Color(0xFFFFC21A)), "orange" to ("Orange" to Color(0xFFFF7A1A)), "pink" to ("Pink" to Color(0xFFFF2FA8)),
        "purple" to ("Purple" to Color(0xFFA24DFF)), "white" to ("White" to Color(0xFFF4F4FF)))
    /** A look by its colours: accent, background, cards, text, quiet text, selection; light or not; a gradient; stars;
     *  apple: laid out as the Light and Dark looks are (plain pages, thin lines between rows), only in other colours. */
    private class P(val accent: Long, val bg: Long, val light: Long, val fg: Long, val muted: Long, val sel: Long,
                    val isLight: Boolean, val grad: List<Long>? = null, val stars: Boolean = false, val apple: Boolean = false)

    /** Stellio's own (light and dark), and colourful ones that still keep it simple. */
    private val STYLED = linkedMapOf(
        "apple" to ("Apple Music" to P(0xFFFA2D48, 0xFFFFFFFF, 0xFFF2F2F7, 0xFF1D1D1F, 0xFF86868B, 0xFFE5E5EA, true, apple = true)),
        "apple-dark" to ("Apple Music dark" to P(0xFFFA2D48, 0xFF000000, 0xFF1C1C1E, 0xFFF5F5F7, 0xFF98989D, 0xFF38383A, false, apple = true)),
        "stellio" to ("Stellio" to P(0xFF4A86F7, 0xFFEEF0F6, 0xFFFFFFFF, 0xFF2B3141, 0xFF8A90A6, 0xFFC9D3EA, true)),
        "stellio-dark" to ("Stellio dark" to P(0xFF5B8DEF, 0xFF1C2030, 0xFF262B3D, 0xFFE6E9F5, 0xFF8A90A8, 0xFF343B54, false)),
        "galaxy" to ("Galaxy" to P(0xFFB57BFF, 0xFF0B0820, 0xFF1C1544, 0xFFEDE7FF, 0xFF9A8FC4, 0xFF3A2C70, false,
            listOf(0xFF0B0820, 0xFF1E0F4A, 0xFF3A1260, 0xFF0B1A3F), stars = true)),
        "aurora" to ("Aurora" to P(0xFF3CF0B0, 0xFF07141A, 0xFF10292F, 0xFFE6FFF6, 0xFF7FB5A8, 0xFF1E4A48, false,
            listOf(0xFF061A1F, 0xFF0B3B3A, 0xFF2A1E5C, 0xFF07141A), stars = true)),
        "candy" to ("Candy" to P(0xFF7C5CFF, 0xFFF5F2FF, 0xFFFFFFFF, 0xFF2A2440, 0xFF8E88A8, 0xFFDCD3FF, true,
            listOf(0xFFFDE2F3, 0xFFE9E4FF, 0xFFDDF5FF))),
        // 3.0's themes (Settings › Look): the Light and Dark looks' layout in other colours. Each pair of text and
        // background keeps at least 4.5:1 contrast (quiet text and the accent too, on every part of a gradient).
        "midnight" to ("Midnight" to P(0xFF8AB4FF, 0xFF0A0F1F, 0xFF141B2E, 0xFFE6ECFF, 0xFF8D97B5, 0xFF26304D, false,
            listOf(0xFF0A0F1F, 0xFF101A35, 0xFF0A0F1F), stars = true, apple = true)),
        "sunset" to ("Sunset" to P(0xFFFF9A70, 0xFF2A0E2E, 0xFF3D1636, 0xFFFFEDE6, 0xFFDDB4BE, 0xFF5A2440, false,
            listOf(0xFF2A0E2E, 0xFF4A1640, 0xFF6E2440, 0xFF7A3430), apple = true)),
        "forest" to ("Forest" to P(0xFF7BD389, 0xFF0E1A14, 0xFF18291F, 0xFFE8F5EC, 0xFF8FAE98, 0xFF24402F, false,
            listOf(0xFF0E1A14, 0xFF15301F, 0xFF0E1A14), apple = true)),
        "ocean" to ("Ocean" to P(0xFF4FD0F8, 0xFF06182B, 0xFF0E2A45, 0xFFE3F6FF, 0xFF93BCD6, 0xFF15405F, false,
            listOf(0xFF041526, 0xFF073056, 0xFF0A4C6E, 0xFF06263F), apple = true)),
        "paper" to ("Paper" to P(0xFFA8431F, 0xFFF5F0E6, 0xFFFFFCF5, 0xFF2B2620, 0xFF6F665A, 0xFFE3D9C6, true, apple = true)),
        "mono" to ("Mono" to P(0xFFFFFFFF, 0xFF121212, 0xFF1E1E1E, 0xFFEDEDED, 0xFF9A9A9A, 0xFF333333, false, apple = true)),
        "synthwave" to ("Synthwave" to P(0xFFFF4FD8, 0xFF1A0B2E, 0xFF2A1248, 0xFFF5E6FF, 0xFFB08FD0, 0xFF4A1F70, false,
            listOf(0xFF1A0B2E, 0xFF2D0B4A, 0xFF4A0E4E, 0xFF1A0B2E), stars = true, apple = true)),
        "coffee" to ("Coffee" to P(0xFFD9A066, 0xFF1F1611, 0xFF2C211A, 0xFFF2E6D8, 0xFFA8927E, 0xFF4A3628, false, apple = true)),
        "rose" to ("Rose" to P(0xFFB0154F, 0xFFFFF5F7, 0xFFFFFFFF, 0xFF3B1F2B, 0xFF86606F, 0xFFF5CCD8, true,
            listOf(0xFFFFF5F7, 0xFFFFE6EE), apple = true)),
        "nord" to ("Nord" to P(0xFF88C0D0, 0xFF2E3440, 0xFF3B4252, 0xFFECEFF4, 0xFFA3ACBD, 0xFF434C5E, false, apple = true)),
        "dracula" to ("Vampire" to P(0xFFBD93F9, 0xFF282A36, 0xFF343746, 0xFFF8F8F2, 0xFFA2ABCB, 0xFF44475A, false, apple = true)),
        "solarized" to ("Solar" to P(0xFF1F77B4, 0xFFFDF6E3, 0xFFFFFBEF, 0xFF073642, 0xFF586E75, 0xFFEEE8D5, true, apple = true)),
        "matrix" to ("Matrix" to P(0xFF00FF66, 0xFF000A04, 0xFF06180D, 0xFFB8FFD0, 0xFF4FA86E, 0xFF0F3A1E, false, apple = true)),
        "sakura" to ("Sakura" to P(0xFFFF8FB8, 0xFF24141E, 0xFF33202C, 0xFFFFE8F0, 0xFFC096AA, 0xFF4D2B3E, false,
            listOf(0xFF24141E, 0xFF3A1A2E, 0xFF24141E), apple = true)),
        "arctic" to ("Arctic" to P(0xFF006AA3, 0xFFF0F7FB, 0xFFFFFFFF, 0xFF0D2B3E, 0xFF557286, 0xFFCDE5F2, true,
            listOf(0xFFF5FBFF, 0xFFE3F1FA, 0xFFEEF2FF), apple = true)),
        "ember" to ("Ember" to P(0xFFFF6A2B, 0xFF140A08, 0xFF22120E, 0xFFFFE9DF, 0xFFB48A7C, 0xFF4A1E14, false,
            listOf(0xFF140A08, 0xFF2A0F08, 0xFF140A08), apple = true)),
        "lavender" to ("Lavender" to P(0xFF6E3FD0, 0xFFF6F2FD, 0xFFFFFFFF, 0xFF2A2140, 0xFF726890, 0xFFE2D8F7, true, apple = true)),
        "slate" to ("Slate" to P(0xFF60A5FA, 0xFF1E293B, 0xFF273449, 0xFFE2E8F0, 0xFF94A3B8, 0xFF334155, false, apple = true)),
        "goldblack" to ("Gold and black" to P(0xFFE6B84C, 0xFF000000, 0xFF141210, 0xFFF5EBD2, 0xFFA39577, 0xFF3A3020, false, apple = true)),
        "contrast" to ("High contrast" to P(0xFFFFE600, 0xFF000000, 0xFF111111, 0xFFFFFFFF, 0xFFD0D0D0, 0xFF444444, false, apple = true)),
        // 3.0's second batch: 15 more, each its own hue family. Text, quiet text and the accent keep 4.5:1 or more against
        // the background, the cards and every part of the gradient.
        "cyberpunk" to ("Cyberpunk" to P(0xFFFCEE0A, 0xFF0A0A12, 0xFF1A1424, 0xFFF4F0FF, 0xFFC79BC2, 0xFF52154A, false,
            listOf(0xFF0A0A12, 0xFF1E0A26, 0xFF3A0A3A, 0xFF0A0A12), apple = true)),
        "vaporwave" to ("Vaporwave" to P(0xFF5FF2E6, 0xFF2B1B4A, 0xFF3A2560, 0xFFFFF0FA, 0xFFD3B4E0, 0xFF5A3C86, false,
            listOf(0xFF2B1B4A, 0xFF4A2466, 0xFF6B2E78, 0xFF2B1B4A), apple = true)),
        "royal" to ("Royal" to P(0xFFF2C14E, 0xFF160A33, 0xFF241446, 0xFFF3ECFF, 0xFFB3A3D6, 0xFF3A2470, false,
            listOf(0xFF160A33, 0xFF2A1258, 0xFF160A33), apple = true)),
        "olive" to ("Olive" to P(0xFFD8C27A, 0xFF1E2214, 0xFF2A2F1D, 0xFFEEF0DF, 0xFFA3A88A, 0xFF3C4328, false, apple = true)),
        "lime" to ("Neon lime" to P(0xFFC6FF3D, 0xFF1B1D20, 0xFF25282C, 0xFFEDEFF2, 0xFF9AA0A8, 0xFF383D44, false, apple = true)),
        "crimson" to ("Crimson" to P(0xFFFF3355, 0xFF0A0405, 0xFF1A0B0E, 0xFFFFE9EC, 0xFFC0959C, 0xFF4A121C, false,
            listOf(0xFF0A0405, 0xFF22070C, 0xFF0A0405), apple = true)),
        "deepsea" to ("Deep sea" to P(0xFF2FE6C8, 0xFF021616, 0xFF0A2626, 0xFFDDFBF5, 0xFF7FB3AA, 0xFF0F3E3B, false,
            listOf(0xFF021616, 0xFF03262A, 0xFF052F3A, 0xFF021616), stars = true, apple = true)),
        "cobalt" to ("Cobalt" to P(0xFFFFD23F, 0xFF0D2B6B, 0xFF163B85, 0xFFF2F6FF, 0xFFB4C4EA, 0xFF2A50A0, false, apple = true)),
        "terracotta" to ("Terracotta" to P(0xFFFFD7A3, 0xFF4A1C12, 0xFF5C251A, 0xFFFFF1E8, 0xFFE0B4A2, 0xFF7A3424, false,
            listOf(0xFF4A1C12, 0xFF6A2A18, 0xFF4A1C12), apple = true)),
        "chalkboard" to ("Chalkboard" to P(0xFFF7E37A, 0xFF233A31, 0xFF2C463B, 0xFFF2F5EF, 0xFFA9BDB2, 0xFF3E5C4F, false, apple = true)),
        "mint" to ("Mint" to P(0xFF0A7A4A, 0xFFE9F8F0, 0xFFFFFFFF, 0xFF10332A, 0xFF4E6F63, 0xFFC4EBD8, true, apple = true)),
        "lemonade" to ("Lemonade" to P(0xFF3B3BB3, 0xFFFFF9D9, 0xFFFFFDF0, 0xFF2A2614, 0xFF6B6440, 0xFFF2E7A6, true, apple = true)),
        "peach" to ("Peach" to P(0xFF00796B, 0xFFFFEDE3, 0xFFFFF8F3, 0xFF3A2218, 0xFF7A5A4C, 0xFFF7D2C0, true, apple = true)),
        "newsprint" to ("Newsprint" to P(0xFFC8102E, 0xFFEDEDEA, 0xFFFAFAF8, 0xFF111111, 0xFF5E5E5A, 0xFFD6D6D0, true, apple = true)),
        "bubblegum" to ("Bubblegum" to P(0xFF8A1FB0, 0xFFFFD9EE, 0xFFFFEEF7, 0xFF3A0E2E, 0xFF7E4A6A, 0xFFF7B8DA, true,
            listOf(0xFFFFE3F2, 0xFFFFD0EA, 0xFFFFE6F6), apple = true)),
    )

    /** The themes in the Look menu after Light, Dark and Now playing, in this order. */
    private val THEMES = listOf("midnight", "sunset", "forest", "ocean", "paper", "mono", "synthwave", "coffee", "rose", "nord",
        "dracula", "solarized", "matrix", "sakura", "arctic", "ember", "lavender", "slate", "goldblack", "contrast",
        "cyberpunk", "vaporwave", "royal", "olive", "lime", "crimson", "deepsea", "cobalt", "terracotta", "chalkboard",
        "mint", "lemonade", "peach", "newsprint", "bubblegum")

    /** What each look in the Look menu is, in a line (shown under Look in Settings). */
    private val DESC = mapOf(
        "apple" to "Clean and bright: white pages, thin lines between rows, a red accent",
        "apple-dark" to "Clean and dark: black pages, thin lines between rows, a red accent",
        "nowplaying" to "Every screen takes the colours of the cover of the song that's playing, and changes with each song",
        "midnight" to "Deep night blue with a soft blue accent and a few stars",
        "sunset" to "Warm plum fading to dusky red, with a peach accent",
        "forest" to "Dark pine green with a fresh leaf-green accent",
        "ocean" to "Deep sea blue with a bright cyan accent",
        "paper" to "Warm cream pages and dark ink, with a rust accent",
        "mono" to "Black, white and greys only: nothing but the music",
        "synthwave" to "Eighties neon: hot pink on deep purple, with stars",
        "coffee" to "Dark roast brown with a caramel accent",
        "rose" to "Soft blush pink pages with a deep raspberry accent",
        "nord" to "Cool arctic greys with an icy blue accent",
        "dracula" to "Dark grey with a vivid violet accent",
        "solarized" to "Light warm cream with a calm blue accent",
        "matrix" to "Green code on black",
        "sakura" to "Dark plum with a cherry-blossom pink accent",
        "arctic" to "Icy white and pale blue with a deep blue accent",
        "ember" to "Charcoal black with a glowing orange accent",
        "lavender" to "Light lilac pages with a rich purple accent",
        "slate" to "Blue-grey slate with a sky-blue accent",
        "goldblack" to "Pure black with a gold accent",
        "contrast" to "Black, white and bright yellow: the easiest to read",
        "cyberpunk" to "Black with electric yellow and a magenta glow",
        "vaporwave" to "Pastel purple and pink with an aqua accent",
        "royal" to "Deep royal purple with a gold accent",
        "olive" to "Muted olive drab with a khaki accent",
        "lime" to "Neutral charcoal grey with a neon lime accent",
        "crimson" to "Near-black with a bold crimson red accent",
        "deepsea" to "The dark of the deep sea: a teal glow and specks of light",
        "cobalt" to "Bright cobalt blue with a sunny yellow accent",
        "terracotta" to "Warm baked-clay red with a soft sand accent",
        "chalkboard" to "School-board green with chalk-white text and a yellow accent",
        "mint" to "Light fresh mint with a deep green accent",
        "lemonade" to "Pale lemon yellow with an indigo accent",
        "peach" to "Soft peach pages with a teal accent",
        "newsprint" to "Grey newspaper and black ink, with a red headline accent",
        "bubblegum" to "Sweet candy pink with a grape-purple accent",
    )
    /** A look's one-line description ("" for one that has none). */
    fun describe(look: String): String = DESC[look].orEmpty()

    /** The Look menu: Light, Dark, Now playing (every screen in the colours of the song playing, the way Now playing
     *  is; Light or Dark while nothing is), then the themes. The other looks stay for the Visualizer and Wrapped. */
    val ALL: List<Pair<String, String>> = listOf("apple" to "Light", "apple-dark" to "Dark", "nowplaying" to "Now playing") +
        THEMES.map { it to (STYLED[it]?.first ?: it) }
    /** The Now playing look's key. */
    const val NOW_PLAYING = "nowplaying"
    /** The looks Now playing can fall back to while nothing is playing: every look in the Look menu but Now playing. */
    val FALLBACK: List<Pair<String, String>> = ALL.filter { it.first != NOW_PLAYING }
    fun known(look: String) = ALL.any { it.first == look }
    /** A look Now playing can fall back to (a saved one that's no longer known goes back to Dark). */
    fun knownFallback(look: String) = FALLBACK.any { it.first == look }

    /** A look's background and accent, for the little swatch beside its name in the menu (Now playing: a sample). */
    fun swatch(look: String): Pair<Color, Color> = STYLED[look]?.second?.let { Color(it.grad?.getOrNull(1) ?: it.bg) to Color(it.accent) }
        ?: (Color(0xFF6B2E5A) to Color(0xFFFFB46B))

    /** The Now playing look: from a cover's main colour (null: none), else the fallback look. */
    fun applyCover(rgb: Int?, fallback: String) {
        if (rgb == null) { apply(fallback); return }
        apply("apple-dark")
        val hsl = FloatArray(3)
        colorToHsl(rgb, hsl)
        val grey = hsl[1] <= 0.08f
        val s = if (grey) hsl[1] else hsl[1].coerceIn(0.25f, 0.75f)
        fun shade(l: Float, sat: Float = s) = Color(hslToColor(hsl[0], sat, l))
        C.isLight = false; C.apple = true
        C.accent = if (grey) Color(0xFFFA2D48) else shade(0.74f, kotlin.math.min(1f, s + 0.15f))
        C.selection = shade(0.30f); C.bg = shade(0.20f); C.dark = shade(0.14f); C.darker = shade(0.10f)
        C.light = shade(0.26f); C.fg = Color(0xFFF5F5F7); C.muted = lerp(Color(0xFFF5F5F7), shade(0.12f), 0.42f)
        C.gradient = listOf(shade(0.34f), shade(0.12f))
    }

    fun apply(look: String) {
        val neon = NEON[look]?.second
        val styled = STYLED[look]?.second
        C.isLight = look == "light" || styled?.isLight == true
        C.glow = neon != null
        C.gradient = styled?.grad?.map { Color(it) }
        C.stars = styled?.stars == true
        C.apple = styled?.apple ?: look.startsWith("apple")
        when {
            styled != null -> {
                val p = styled
                C.accent = Color(p.accent); C.bg = Color(p.bg); C.light = Color(p.light); C.fg = Color(p.fg)
                C.muted = Color(p.muted); C.selection = Color(p.sel)
                C.dark = lerp(Color(p.bg), if (p.isLight) Color.Black else Color.Black, if (p.isLight) 0.03f else 0.25f)
                C.darker = lerp(Color(p.bg), Color.Black, if (p.isLight) 0.07f else 0.45f)
                C.red = Color(0xFFE5484D); C.green = Color(0xFF3FB97A)
            }
            neon != null -> {
                fun tint(t: Float) = lerp(Color.Black, neon, t)
                C.accent = neon; C.selection = tint(0.22f); C.muted = lerp(Color(0xFF8A8A8A), neon, 0.25f)
                C.bg = tint(0.02f); C.dark = tint(0.01f); C.darker = Color.Black; C.light = tint(0.06f)
                C.fg = lerp(Color(0xFFF4F4F4), neon, 0.08f); C.red = Color(0xFFFF3B4D); C.green = lerp(neon, Color.White, 0.2f)
            }
            look == "light" -> {
                C.accent = Color(0xFF2E5BD8); C.selection = Color(0xFFC6CBE0); C.muted = Color(0xFF6A6F85)
                C.bg = Color(0xFFF4F5F9); C.dark = Color(0xFFE9EBF2); C.darker = Color(0xFFDFE2EB); C.light = Color(0xFFFFFFFF)
                C.fg = Color(0xFF1F2335); C.red = Color(0xFFC6334F); C.green = Color(0xFF3F7D1A)
            }
            look == "dark" -> {
                C.accent = Color(0xFF7AA2F7); C.selection = Color(0xFF2A2D3E); C.muted = Color(0xFF6B7089)
                C.bg = Color(0xFF0B0B10); C.dark = Color(0xFF07070A); C.darker = Color.Black; C.light = Color(0xFF14151D)
                C.fg = Color(0xFFD4D8F0); C.red = Color(0xFFF7768E); C.green = Color(0xFF9ECE6A)
            }
            else -> {
                C.accent = Color(0xFF7AA2F7); C.selection = Color(0xFF414868); C.muted = Color(0xFF787C99)
                C.bg = Color(0xFF1A1B26); C.dark = Color(0xFF16161E); C.darker = Color(0xFF111118); C.light = Color(0xFF1F2335)
                C.fg = Color(0xFFC0CAF5); C.red = Color(0xFFF7768E); C.green = Color(0xFF9ECE6A)
            }
        }
    }
}

val Mono = FontFamily.Monospace
/** The type: a clean sans-serif, like Poweramp's, or the theme's own (Retro's monospace…). */
val Sans: FontFamily get() = Themes.current.font

object T {
    /** A text style in the theme's type: its font (titles can have their own), weight and size. */
    private fun style(size: Float, weight: Int = 400, color: Color = Color.Unspecified, title: Boolean = false,
                      spacing: TextUnit = TextUnit.Unspecified): TextStyle {
        val th = Themes.current
        return TextStyle(fontFamily = if (title) th.titleFont else th.font, fontWeight = th.weight(weight),
            fontSize = (size * if (title) th.titleScale else th.textScale).sp, letterSpacing = spacing, color = color)
    }
    /** Quiet text: the look's muted colour, fainter still in a theme that wants it (Minimal). */
    private val quiet: Color get() = Themes.current.quiet.let { q -> if (q >= 1f) C.muted else C.muted.copy(alpha = C.muted.alpha * q) }

    val title get() = style(15f, 600, C.fg, title = true)
    val sub get() = style(13f, color = quiet)
    val body get() = style(14f, color = C.fg)
    val section get() = style(11f, 700, C.muted, spacing = 1.2.sp)
    val brand get() = style(22f, 800, C.fg, title = true)
    val tab get() = style(13f, 500)
    val time get() = style(12f, color = quiet)
    /** The player's song title. */
    val big get() = style(22f, 700, C.fg, title = true)
    /** A screen's name in its header. */
    val head get() = style(20f, 700, C.fg, title = true)
}

/** Corners: rounded, Poweramp-like, or the theme's (Sharp's square ones, Playful's round ones). Used as R.card etc.;
 *  the object itself can't be called R, since an Android library's build leaves out every class named R (it takes them
 *  for resource classes): the phone app then crashed at start (NoClassDefFoundError: org.omarchy.music.ui.R). */
typealias R = Corners

object Corners {
    val small: RoundedCornerShape get() = Themes.current.smallShape
    val card: RoundedCornerShape get() = Themes.current.cardShape
    val big: RoundedCornerShape get() = Themes.current.bigShape
    val pill: RoundedCornerShape get() = Themes.current.pillShape
}

/** How a theme draws a row or a tile: nothing behind it, a soft fill, frosted glass, an outline, or a raised card. */
enum class CardStyle { NONE, SOFT, GLASS, OUTLINE, RAISED }

/** A theme: the app's style (corners, type, spacing, cards and lines), not its colours, so it goes with any look.
 *  The numbers scale the standard ones: space the gaps around rows and tiles, art the covers, icon the icon buttons;
 *  weight is added to every font weight; border scales the frames of chips and pills (0: none), line the lines
 *  between rows (0: none); quiet fades the quiet text. */
class AppStyle(
    val name: String, val desc: String,
    small: Dp = 6.dp, card: Dp = 14.dp, big: Dp = 22.dp, pill: Dp? = null,
    val font: FontFamily = FontFamily.SansSerif, val titleFont: FontFamily = font,
    val weightShift: Int = 0, val textScale: Float = 1f, val titleScale: Float = 1f,
    val space: Float = 1f, val art: Float = 1f, val icon: Float = 1f,
    val cards: CardStyle = CardStyle.NONE, val border: Float = 1f, val line: Float = 1f, val lineWidth: Dp = 0.5.dp,
    val dashed: Boolean = false, val quiet: Float = 1f,
) {
    val smallShape = RoundedCornerShape(small)
    val cardShape = RoundedCornerShape(card)
    val bigShape = RoundedCornerShape(big)
    val pillShape = if (pill == null) RoundedCornerShape(50) else RoundedCornerShape(pill)
    /** A font weight moved by the theme (kept between light and black, so text never gets too thin to read). */
    fun weight(base: Int): FontWeight = FontWeight((base + weightShift).coerceIn(300, 900))
}

/** The themes (Settings › Personalization › Theme). The app tells source where the chosen one is kept (its settings,
 *  read as Compose state, so picking one redraws everything that uses R, T, C.line or the helpers below). */
object Themes {
    const val PREF = "ui_theme"
    const val STANDARD = "standard"
    var source: () -> String = { STANDARD }

    val ALL: Map<String, AppStyle> = linkedMapOf(
        STANDARD to AppStyle("Standard", "The app as it comes: soft corners, clean sans-serif type, even spacing"),
        "rounded" to AppStyle("Rounded", "Big soft corners, and every row and tile on a soft pill-shaped card",
            small = 12.dp, card = 24.dp, big = 32.dp, space = 1.08f, cards = CardStyle.SOFT),
        "sharp" to AppStyle("Sharp", "Square corners and thin, crisp lines",
            small = 0.dp, card = 2.dp, big = 2.dp, pill = 2.dp, border = 0.75f, lineWidth = 0.5.dp),
        "compact" to AppStyle("Compact", "Tighter spacing, smaller covers and type: more fits on the screen",
            small = 4.dp, card = 10.dp, big = 16.dp, textScale = 0.92f, titleScale = 0.92f, space = 0.65f, art = 0.8f, icon = 0.9f),
        "roomy" to AppStyle("Roomy", "More space around everything, bigger covers and type",
            small = 8.dp, card = 18.dp, big = 26.dp, textScale = 1.06f, titleScale = 1.08f, space = 1.45f, art = 1.25f, icon = 1.1f),
        "glass" to AppStyle("Glass", "See-through frosted cards with fine light borders",
            small = 10.dp, card = 18.dp, big = 26.dp, cards = CardStyle.GLASS, border = 1.2f, space = 1.1f),
        "outline" to AppStyle("Outline", "No fills: every row, tile, chip and button is drawn as an outline",
            cards = CardStyle.OUTLINE, border = 1.5f, space = 1.08f),
        "flat" to AppStyle("Flat", "Plain and flat: no borders, no shadows, small corners",
            small = 3.dp, card = 6.dp, big = 10.dp, border = 0f, line = 0.6f),
        "bold" to AppStyle("Bold", "Heavier type and bigger titles that stand out",
            weightShift = 200, titleScale = 1.12f, textScale = 1.03f),
        "lighttype" to AppStyle("Light type", "Thin, airy type throughout",
            weightShift = -200, textScale = 1.02f, titleScale = 1.04f),
        "retro" to AppStyle("Retro", "A typewriter's monospace type, square corners and dashed lines, like an old terminal",
            small = 0.dp, card = 0.dp, big = 0.dp, pill = 0.dp, font = FontFamily.Monospace, textScale = 0.94f,
            titleScale = 0.96f, dashed = true, line = 1.6f, lineWidth = 1.dp),
        "elegant" to AppStyle("Elegant", "Serif titles, like a magazine, with a little more air",
            small = 4.dp, card = 10.dp, big = 16.dp, titleFont = FontFamily.Serif, titleScale = 1.08f, space = 1.12f),
        "playful" to AppStyle("Playful", "Extra-round corners, bigger icons and covers, a bit of bounce",
            small = 16.dp, card = 28.dp, big = 36.dp, weightShift = 100, titleScale = 1.05f, art = 1.1f, icon = 1.25f,
            space = 1.12f, cards = CardStyle.SOFT),
        "minimal" to AppStyle("Minimal", "No lines or frames, quieter small text and lots of space",
            border = 0f, line = 0f, quiet = 0.75f, space = 1.35f),
        "cards" to AppStyle("Cards", "Every row and tile is its own raised card",
            small = 8.dp, card = 16.dp, big = 22.dp, cards = CardStyle.RAISED, space = 1.15f),
    )
    /** The Theme menu: key to name. */
    val MENU: List<Pair<String, String>> = ALL.map { (k, v) -> k to v.name }
    fun known(key: String) = key in ALL
    /** The theme in use (Standard for one that's not known). */
    val current: AppStyle get() = ALL[source()] ?: ALL.getValue(STANDARD)

    /** A gap (padding around a row or tile) in the theme's spacing. */
    fun gap(d: Dp): Dp = d * current.space
    /** A button's or chip's padding: follows the spacing, but less, so they stay easy to tap. */
    fun pad(d: Dp): Dp = d * current.space.coerceIn(0.8f, 1.25f)
    /** A cover's size in the theme's. */
    fun art(d: Dp): Dp = d * current.art
    /** An icon button's icon in the theme's size. */
    fun icon(d: Dp): Dp = d * current.icon
    /** A font size (for text that sets its own) in the theme's type scale. */
    fun sp(size: Float, title: Boolean = false): TextUnit = (size * if (title) current.titleScale else current.textScale).sp
    /** The theme draws lines between rows (not when it has none, or puts each row on its own card). */
    val rowLines: Boolean get() = current.let { it.line > 0f && it.cards == CardStyle.NONE }
}

/** A row's or tile's card as the theme draws it. Put it before the row's clip, so a raised card's shadow shows. */
fun Modifier.themeCard(shape: Shape): Modifier = when (Themes.current.cards) {
    CardStyle.NONE -> this
    CardStyle.SOFT -> this.background(C.fg.copy(alpha = 0.05f), shape)
    CardStyle.GLASS -> this.background(C.light.copy(alpha = 0.45f), shape).border(1.dp, C.fg.copy(alpha = 0.18f), shape)
    CardStyle.OUTLINE -> this.border(1.dp, C.fg.copy(alpha = 0.3f), shape)
    CardStyle.RAISED -> this.shadow(4.dp, shape, clip = false).background(C.light, shape)
        .then(if (C.isLight) Modifier else Modifier.border(1.dp, C.fg.copy(alpha = 0.08f), shape))   // shadows hardly show on dark
}

/** A frame the theme can thicken, thin or take away (a 0 width would still draw a hairline, so then none is drawn). */
fun Modifier.themeBorder(width: Dp, color: Color, shape: Shape): Modifier =
    Themes.current.border.let { b -> if (b <= 0f) this else this.border(width * b, color, shape) }

@Composable
fun OmarchyTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val dark = darkColorScheme(
        primary = C.accent, onPrimary = C.darker, background = C.bg, onBackground = C.fg, surface = C.light,
        onSurface = C.fg, surfaceVariant = C.light, onSurfaceVariant = C.muted, secondary = C.accent,
        surfaceContainer = C.light, surfaceContainerHigh = C.light, surfaceContainerHighest = C.light,
        surfaceContainerLow = C.dark, outline = C.line, error = C.red,
    )
    val light = lightColorScheme(
        primary = C.accent, onPrimary = C.light, background = C.bg, onBackground = C.fg, surface = C.light,
        onSurface = C.fg, surfaceVariant = C.light, onSurfaceVariant = C.muted, secondary = C.accent,
        surfaceContainer = C.light, surfaceContainerHigh = C.light, surfaceContainerHighest = C.light,
        surfaceContainerLow = C.dark, outline = C.line, error = C.red,
    )
    MaterialTheme(
        colorScheme = if (C.isLight) light else dark,
        typography = Typography(
            bodyLarge = base.bodyLarge.copy(fontFamily = Sans), bodyMedium = base.bodyMedium.copy(fontFamily = Sans),
            bodySmall = base.bodySmall.copy(fontFamily = Sans), titleLarge = base.titleLarge.copy(fontFamily = Sans),
            titleMedium = base.titleMedium.copy(fontFamily = Sans), titleSmall = base.titleSmall.copy(fontFamily = Sans),
            labelLarge = base.labelLarge.copy(fontFamily = Sans), labelMedium = base.labelMedium.copy(fontFamily = Sans),
            labelSmall = base.labelSmall.copy(fontFamily = Sans), headlineSmall = base.headlineSmall.copy(fontFamily = Sans),
        ),
        shapes = androidx.compose.material3.Shapes(extraSmall = R.small, small = R.small, medium = R.card, large = R.big, extraLarge = R.big),
        content = content,
    )
}

/** A colour (0xRRGGBB) as hue (0..360), saturation and lightness (0..1) into out: androidx's ColorUtils.colorToHSL,
 *  here so the PC has it too. */
fun colorToHsl(rgb: Int, out: FloatArray) {
    val r = ((rgb shr 16) and 0xFF) / 255f; val g = ((rgb shr 8) and 0xFF) / 255f; val b = (rgb and 0xFF) / 255f
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
    val l = (max + min) / 2f
    var h: Float; val s: Float
    if (max == min) { h = 0f; s = 0f } else {
        h = when (max) { r -> ((g - b) / d) % 6f; g -> (b - r) / d + 2f; else -> (r - g) / d + 4f }
        s = d / (1f - kotlin.math.abs(2f * l - 1f))
    }
    h = (h * 60f) % 360f
    if (h < 0) h += 360f
    out[0] = h; out[1] = s.coerceIn(0f, 1f); out[2] = l.coerceIn(0f, 1f)
}

/** Hue, saturation and lightness back to an opaque colour (0xFFRRGGBB): ColorUtils.HSLToColor. */
fun hslToColor(h: Float, s: Float, l: Float): Int {
    val c = (1f - kotlin.math.abs(2 * l - 1f)) * s
    val m = l - 0.5f * c
    val x = c * (1f - kotlin.math.abs((h / 60f % 2f) - 1f))
    val hs = (h.toInt() / 60) % 6
    val (r, g, b) = when (hs) {
        0 -> Triple(c + m, x + m, m); 1 -> Triple(x + m, c + m, m); 2 -> Triple(m, c + m, x + m)
        3 -> Triple(m, x + m, c + m); 4 -> Triple(x + m, m, c + m); else -> Triple(c + m, m, x + m)
    }
    fun ch(v: Float) = kotlin.math.round(v * 255f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}
