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
    var fx by mutableStateOf<Set<String>>(emptySet())    // a look of yours: its extras drawn over the background (LookEffects)
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
     *  apple: laid out as the Light and Dark looks are (plain pages, thin lines between rows), only in other colours;
 *  glow: a neon look (what's on glows). */
    private class P(val accent: Long, val bg: Long, val light: Long, val fg: Long, val muted: Long, val sel: Long,
                    val isLight: Boolean, val grad: List<Long>? = null, val stars: Boolean = false, val apple: Boolean = false,
                    val glow: Boolean = false, val fx: Set<String> = emptySet())

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
        // 3.0's neon looks: 30 dark ones, each a neon colour glowing on its own dark hue. The accent, text and quiet text
        // keep 4.5:1 or more against the background, the cards and the gradient.
        "neon-tokyo" to ("Neon Tokyo nights" to P(0xFFFF2E88, 0xFF060614, 0xFF0E0E25, 0xFFFAEBF1, 0xFFC299AB, 0xFF4F172F, false,
            listOf(0xFF060614, 0xFF220B2D, 0xFF060614), stars = true, apple = true, glow = true)),
        "neon-miami" to ("Neon Miami" to P(0xFF00F0FF, 0xFF140614, 0xFF250E25, 0xFFEBF9FA, 0xFF99BFC2, 0xFF174C4F, false,
            listOf(0xFF140614, 0xFF2D0B1C, 0xFF140614), apple = true, glow = true)),
        "neon-acid" to ("Neon acid" to P(0xFFC8FF00, 0xFF0C1406, 0xFF18250E, 0xFFF7FAEB, 0xFFB9C299, 0xFF434F17, false,
            listOf(0xFF0C1406, 0xFF0B2D16, 0xFF0C1406), apple = true, glow = true)),
        "neon-laser" to ("Neon laser" to P(0xFFFF00E6, 0xFF0A0614, 0xFF160E25, 0xFFFAEBF8, 0xFFC299BE, 0xFF4F174A, false,
            listOf(0xFF0A0614, 0xFF0B112D, 0xFF0A0614), apple = true, glow = true)),
        "neon-arcade" to ("Neon arcade" to P(0xFFFFE600, 0xFF060814, 0xFF0E1225, 0xFFFAF8EB, 0xFFC2BE99, 0xFF4F4A17, false,
            listOf(0xFF060814, 0xFF160B2D, 0xFF060814), apple = true, glow = true)),
        "neon-ultraviolet" to ("Neon ultraviolet" to P(0xFFBD4BFF, 0xFF0D0614, 0xFF190E25, 0xFFF4EBFA, 0xFFB399C2, 0xFF3B174F, false,
            listOf(0xFF0D0614, 0xFF2D0B2D, 0xFF0D0614), apple = true, glow = true)),
        "neon-toxic" to ("Neon toxic" to P(0xFF39FF14, 0xFF061406, 0xFF0E250E, 0xFFEDFAEB, 0xFF9FC299, 0xFF204F17, false,
            listOf(0xFF061406, 0xFF1C2D0B, 0xFF061406), apple = true, glow = true)),
        "neon-plasma" to ("Neon plasma" to P(0xFFFF6A00, 0xFF0F0614, 0xFF1D0E25, 0xFFFAF1EB, 0xFFC2AA99, 0xFF4F2E17, false,
            listOf(0xFF0F0614, 0xFF2D0B22, 0xFF0F0614), apple = true, glow = true)),
        "neon-electric" to ("Neon electric blue" to P(0xFF00A2FF, 0xFF060C14, 0xFF0E1825, 0xFFEBF4FA, 0xFF99B3C2, 0xFF173B4F, false,
            listOf(0xFF060C14, 0xFF0B0B2D, 0xFF060C14), apple = true, glow = true)),
        "neon-ruby" to ("Neon ruby" to P(0xFFFF1744, 0xFF140608, 0xFF250E12, 0xFFFAEBEE, 0xFFC299A1, 0xFF4F1722, false,
            listOf(0xFF140608, 0xFF2D0B22, 0xFF140608), apple = true, glow = true)),
        "neon-grid" to ("Neon grid runner" to P(0xFF00E5FF, 0xFF060F14, 0xFF0E1D25, 0xFFEBF8FA, 0xFF99BEC2, 0xFF17494F, false,
            listOf(0xFF060F14, 0xFF0B142D, 0xFF060F14), stars = true, apple = true, glow = true)),
        "neon-jungle" to ("Neon jungle" to P(0xFF00FF9C, 0xFF06140F, 0xFF0E251D, 0xFFEBFAF4, 0xFF99C2B2, 0xFF174F39, false,
            listOf(0xFF06140F, 0xFF0B2D11, 0xFF06140F), apple = true, glow = true)),
        "neon-hologram" to ("Neon hologram" to P(0xFF7DF9FF, 0xFF061114, 0xFF0E2125, 0xFFEBF9FA, 0xFF99C0C2, 0xFF174C4F, false,
            listOf(0xFF061114, 0xFF1C0B2D, 0xFF061114), apple = true, glow = true)),
        "neon-sunrise" to ("Neon sunrise" to P(0xFFFF9E1F, 0xFF14060D, 0xFF250E1A, 0xFFFAF3EB, 0xFFC2B099, 0xFF4F3717, false,
            listOf(0xFF14060D, 0xFF2D160B, 0xFF14060D), apple = true, glow = true)),
        "neon-magma" to ("Neon magma" to P(0xFFFF3D00, 0xFF140806, 0xFF25120E, 0xFFFAEEEB, 0xFFC2A399, 0xFF4F2417, false,
            listOf(0xFF140806, 0xFF2D0B11, 0xFF140806), apple = true, glow = true)),
        "neon-orchid" to ("Neon orchid" to P(0xFFFF6BFF, 0xFF110614, 0xFF210E25, 0xFFFAEBFA, 0xFFC299C2, 0xFF4F174F, false,
            listOf(0xFF110614, 0xFF160B2D, 0xFF110614), apple = true, glow = true)),
        "neon-ice" to ("Neon ice glow" to P(0xFFA0F0FF, 0xFF060E14, 0xFF0E1B25, 0xFFEBF7FA, 0xFF99BBC2, 0xFF17464F, false,
            listOf(0xFF060E14, 0xFF0B272D, 0xFF060E14), apple = true, glow = true)),
        "neon-lagoon" to ("Neon lagoon" to P(0xFF1DE9B6, 0xFF061414, 0xFF0E2525, 0xFFEBFAF6, 0xFF99C2B8, 0xFF174F41, false,
            listOf(0xFF061414, 0xFF0B222D, 0xFF061414), apple = true, glow = true)),
        "neon-voltage" to ("Neon voltage" to P(0xFFE6FF00, 0xFF080614, 0xFF120E25, 0xFFF8FAEB, 0xFFBEC299, 0xFF4A4F17, false,
            listOf(0xFF080614, 0xFF220B2D, 0xFF080614), apple = true, glow = true)),
        "neon-bloodmoon" to ("Neon blood moon" to P(0xFFFF2D2D, 0xFF140606, 0xFF250E0E, 0xFFFAEBEB, 0xFFC29999, 0xFF4F1717, false,
            listOf(0xFF140606, 0xFF2D140B, 0xFF140606), apple = true, glow = true)),
        "neon-retrowave" to ("Neon retrowave" to P(0xFFFF71CE, 0xFF080614, 0xFF120E25, 0xFFFAEBF5, 0xFFC299B4, 0xFF4F173C, false,
            listOf(0xFF080614, 0xFF2D0B2D, 0xFF080614), stars = true, apple = true, glow = true)),
        "neon-nebula" to ("Neon nebula" to P(0xFF9366FF, 0xFF090614, 0xFF140E25, 0xFFEFEBFA, 0xFFA599C2, 0xFF27174F, false,
            listOf(0xFF090614, 0xFF270B2D, 0xFF090614), stars = true, apple = true, glow = true)),
        "neon-cherry" to ("Neon cherry cola" to P(0xFFFF3B8D, 0xFF14060A, 0xFF250E16, 0xFFFAEBF1, 0xFFC299AA, 0xFF4F172E, false,
            listOf(0xFF14060A, 0xFF2D110B, 0xFF14060A), apple = true, glow = true)),
        "neon-citrus" to ("Neon citrus" to P(0xFFFFB300, 0xFF140F06, 0xFF251D0E, 0xFFFAF5EB, 0xFFC2B699, 0xFF4F3E17, false,
            listOf(0xFF140F06, 0xFF2D160B, 0xFF140F06), apple = true, glow = true)),
        "neon-mintpurple" to ("Neon mint glow" to P(0xFF3DFFB8, 0xFF0D0614, 0xFF190E25, 0xFFEBFAF4, 0xFF99C2B3, 0xFF174F3B, false,
            listOf(0xFF0D0614, 0xFF110B2D, 0xFF0D0614), apple = true, glow = true)),
        "neon-blueflame" to ("Neon blue flame" to P(0xFF4289FF, 0xFF060914, 0xFF0E1425, 0xFFEBF0FA, 0xFF99A8C2, 0xFF172C4F, false,
            listOf(0xFF060914, 0xFF0B222D, 0xFF060914), apple = true, glow = true)),
        "neon-rave" to ("Neon rave" to P(0xFFFF2BB0, 0xFF061411, 0xFF0E2521, 0xFFFAEBF4, 0xFFC299B3, 0xFF4F173A, false,
            listOf(0xFF061411, 0xFF0B272D, 0xFF061411), apple = true, glow = true)),
        "neon-radioactive" to ("Neon radioactive" to P(0xFFAEFF00, 0xFF140614, 0xFF250E25, 0xFFF5FAEB, 0xFFB5C299, 0xFF3D4F17, false,
            listOf(0xFF140614, 0xFF220B2D, 0xFF140614), apple = true, glow = true)),
        "neon-afterhours" to ("Neon after hours" to P(0xFF00FFD0, 0xFF14060D, 0xFF250E1A, 0xFFEBFAF7, 0xFF99C2BA, 0xFF174F45, false,
            listOf(0xFF14060D, 0xFF2D0B2D, 0xFF14060D), apple = true, glow = true)),
        "neon-sodium" to ("Neon sodium lights" to P(0xFFFF8A00, 0xFF060D14, 0xFF0E1925, 0xFFFAF3EB, 0xFFC2AF99, 0xFF4F3517, false,
            listOf(0xFF060D14, 0xFF0B112D, 0xFF060D14), apple = true, glow = true)),
        // 4.0.14: thirty more, each with an effect of its own drawn behind everything
        "stainedglass" to ("Stained glass" to P(0xFFFFD23F, 0xFF0F0B1E, 0xFF1C1535, 0xFFF3EEFF, 0xFF9A8FC0, 0xFF3A2D66, false,
            listOf(0xFF0F0B1E, 0xFF1E1147, 0xFF0B1E3A), apple = true, fx = setOf("mosaic"))),
        "patina" to ("Copper patina" to P(0xFF3FD0B0, 0xFF2A1A12, 0xFF3A251A, 0xFFF6E9DD, 0xFFB89A84, 0xFF5A3A28, false,
            listOf(0xFF2A1A12, 0xFF143A34, 0xFF2A1A12), apple = true, fx = setOf("patina"))),
        "blueprint" to ("Blueprint" to P(0xFFE8F1FF, 0xFF0F3D7A, 0xFF16498F, 0xFFEAF2FF, 0xFF9DB9E0, 0xFF2A62B0, false, apple = true, fx = setOf("draft"))),
        "terrazzo" to ("Terrazzo" to P(0xFFC8553D, 0xFFF4EFE6, 0xFFFFFFFF, 0xFF2E2A26, 0xFF7C746A, 0xFFE6DCCB, true, apple = true, fx = setOf("chips"))),
        "artdeco" to ("Art deco" to P(0xFFE8C25A, 0xFF0C1B1A, 0xFF14302D, 0xFFF5EBD0, 0xFFA89F7E, 0xFF2A4A44, false, apple = true, fx = setOf("fans"))),
        "bauhaus" to ("Bauhaus" to P(0xFFE63329, 0xFFF2EDE0, 0xFFFFFFFF, 0xFF1A1A1A, 0xFF6B6B6B, 0xFFF3E3A0, true, apple = true, fx = setOf("shapes"))),
        "jade" to ("Jade" to P(0xFF9FE8B8, 0xFF0B2A22, 0xFF123A30, 0xFFE8F7EE, 0xFF8DB5A2, 0xFF1F5444, false,
            listOf(0xFF0B2A22, 0xFF14473A, 0xFF0B2A22), apple = true, fx = setOf("swirls"))),
        "coralreef" to ("Coral reef" to P(0xFFFF7A6B, 0xFF06283A, 0xFF0C3A52, 0xFFEAF8FF, 0xFF86B4C8, 0xFF1A5570, false,
            listOf(0xFF06283A, 0xFF0A4458, 0xFF06283A), apple = true, fx = setOf("coral"))),
        "amber" to ("Amber" to P(0xFFFFB000, 0xFF2B1500, 0xFF3D2006, 0xFFFFF3DC, 0xFFC9A36B, 0xFF5E3410, false,
            listOf(0xFF2B1500, 0xFF5A2E00, 0xFF2B1500), apple = true, fx = setOf("drops"))),
        "inkwash" to ("Ink wash" to P(0xFFB3261E, 0xFFF3EFE7, 0xFFFBF8F2, 0xFF1D1D1D, 0xFF77736B, 0xFFDDD6C8, true, apple = true, fx = setOf("blots"))),
        "denim" to ("Denim" to P(0xFFF2A93B, 0xFF1E3557, 0xFF284468, 0xFFEDF2FA, 0xFF9FB2CC, 0xFF3A5A85, false, apple = true, fx = setOf("weave"))),
        "velvet" to ("Velvet theatre" to P(0xFFF2C14E, 0xFF3A0714, 0xFF4E0D1F, 0xFFFFEFE6, 0xFFC99AA0, 0xFF6E1A30, false,
            listOf(0xFF3A0714, 0xFF5C0B22, 0xFF2A0510), apple = true, fx = setOf("drapes"))),
        "glacier" to ("Glacier cave" to P(0xFF7FE7FF, 0xFF061A2E, 0xFF0D2A45, 0xFFEAF8FF, 0xFF8FB6CC, 0xFF1C4A6E, false,
            listOf(0xFF061A2E, 0xFF0E3A5C, 0xFF061A2E), apple = true, fx = setOf("icicles"))),
        "obsidian" to ("Obsidian" to P(0xFFFF5A3C, 0xFF0A0A0D, 0xFF15151B, 0xFFEDEDF2, 0xFF8A8A96, 0xFF2A2A36, false, apple = true, fx = setOf("facets"))),
        "tropical" to ("Tropical punch" to P(0xFFE8336D, 0xFFFFF4D6, 0xFFFFFBEF, 0xFF3A2030, 0xFF8A6A70, 0xFFFFD9A8, true, apple = true, fx = setOf("slices"))),
        "carnival" to ("Carnival" to P(0xFFFFD400, 0xFF1B1464, 0xFF261D80, 0xFFFFF8E8, 0xFFB3ACD9, 0xFF3D32A8, false, apple = true, fx = setOf("bunting"))),
        "origami" to ("Origami" to P(0xFFE8590C, 0xFFF7F5F0, 0xFFFFFFFF, 0xFF26282B, 0xFF7B7F85, 0xFFE5E1D8, true, apple = true, fx = setOf("cranes"))),
        "marble" to ("Marble hall" to P(0xFF8A6D1D, 0xFFEEEDEA, 0xFFFAFAF8, 0xFF222326, 0xFF7A7C82, 0xFFDCDAD4, true, apple = true, fx = setOf("veins"))),
        "moss" to ("Moss and stone" to P(0xFFB5D46A, 0xFF22261F, 0xFF2E3329, 0xFFECEFE4, 0xFF9AA38C, 0xFF444B3A, false, apple = true, fx = setOf("pebbles"))),
        "peacock" to ("Peacock" to P(0xFF2FE0C2, 0xFF0A1440, 0xFF121F5C, 0xFFEEF2FF, 0xFF97A2D6, 0xFF23348A, false,
            listOf(0xFF0A1440, 0xFF0B3A5A, 0xFF1A0F4A), apple = true, fx = setOf("feathers"))),
        "lighthouse" to ("Lighthouse" to P(0xFFFFE28A, 0xFF0B1626, 0xFF13233A, 0xFFEEF3FA, 0xFF8FA3BD, 0xFF21395C, false, apple = true, fx = setOf("beam"))),
        "teahouse" to ("Tea house" to P(0xFF5E7D2B, 0xFFF2EBDD, 0xFFFBF6EC, 0xFF2B2A22, 0xFF7C7766, 0xFFDDD4BD, true, apple = true, fx = setOf("steam"))),
        "crayon" to ("Crayon box" to P(0xFF2F6FED, 0xFFFFFDF7, 0xFFFFFFFF, 0xFF222222, 0xFF707070, 0xFFFFE9A8, true, apple = true, fx = setOf("scribbles"))),
        "lanternfest" to ("Lantern festival" to P(0xFFFF9F45, 0xFF140A2E, 0xFF20123F, 0xFFFFF2E6, 0xFFBFA6C9, 0xFF3A2260, false,
            listOf(0xFF140A2E, 0xFF2E1040, 0xFF140A2E), apple = true, fx = setOf("lanterns"))),
        "cartridge" to ("Game cartridge" to P(0xFF9D8CFF, 0xFF2B2D3A, 0xFF3A3D4D, 0xFFECECF4, 0xFFA2A5B8, 0xFF4D5068, false, apple = true, fx = setOf("pixels"))),
        "glowshroom" to ("Glowshroom" to P(0xFF5CF2E0, 0xFF150B26, 0xFF221238, 0xFFF0EAFF, 0xFFA596C8, 0xFF3A2460, false,
            listOf(0xFF150B26, 0xFF0B2A30, 0xFF150B26), apple = true, fx = setOf("spores"))),
        "opal" to ("Opal" to P(0xFF7A5CE0, 0xFFF1F4F8, 0xFFFFFFFF, 0xFF262A36, 0xFF7C8497, 0xFFDCE3F2, true, apple = true, fx = setOf("shimmer"))),
        "foundry" to ("Rust and steel" to P(0xFFFF7A2F, 0xFF1C1F22, 0xFF2A2E33, 0xFFE8EAEC, 0xFF949BA3, 0xFF3E444B, false, apple = true, fx = setOf("rivets"))),
        "plumwine" to ("Plum wine" to P(0xFFFF8FB8, 0xFF2A0A2E, 0xFF3A1240, 0xFFFCEBFA, 0xFFC29AC6, 0xFF5A2262, false,
            listOf(0xFF2A0A2E, 0xFF48103E, 0xFF2A0A2E), apple = true, fx = setOf("eddy"))),
        "kitesky" to ("Kite sky" to P(0xFFE8452C, 0xFFDDF1FF, 0xFFF4FAFF, 0xFF16324A, 0xFF5E7C96, 0xFFBFE0F7, true, apple = true, fx = setOf("kites"))),
        // 4.5.3: Music scenes, thirty looks each of a kind of music, each with an effect that moves to the music
        "scene-jazz" to ("Jazz club" to P(0xFFE8B04B, 0xFF120C0A, 0xFF201612, 0xFFF5EAD8, 0xFFB29C82, 0xFF3F2C20, false,
            listOf(0xFF120C0A, 0xFF2A1410, 0xFF120C0A), apple = true, fx = setOf("beat-spots"))),
        "scene-blues" to ("Blues bar" to P(0xFF5FAEFF, 0xFF0A1022, 0xFF131C36, 0xFFE6EEFF, 0xFF9AA8CC, 0xFF24345E, false,
            listOf(0xFF0A1022, 0xFF14204A, 0xFF0A1022), apple = true, fx = setOf("beat-wave"))),
        "scene-punk" to ("Punk" to P(0xFFFF2E93, 0xFF0D0D0D, 0xFF1C1C1C, 0xFFF2F2F2, 0xFFA8A8A8, 0xFF3A1A2A, false, apple = true, fx = setOf("beat-tremor"))),
        "scene-metal" to ("Heavy metal" to P(0xFFFF4A2E, 0xFF050505, 0xFF141414, 0xFFEDEDED, 0xFF9A9A9A, 0xFF3A1410, false,
            listOf(0xFF050505, 0xFF1A0806, 0xFF050505), apple = true, fx = setOf("beat-bolt"))),
        "scene-reggae" to ("Reggae" to P(0xFFFFD23F, 0xFF0B1A0F, 0xFF14281A, 0xFFF0F7EC, 0xFFA6BFA8, 0xFF2A4A30, false,
            listOf(0xFF0B1A0F, 0xFF1F2A0B, 0xFF2A0F0B), apple = true, fx = setOf("beat-orbs"))),
        "scene-hiphop" to ("Hip-hop" to P(0xFFFFC21A, 0xFF101014, 0xFF1C1C24, 0xFFF4F4F8, 0xFFA0A0B0, 0xFF34344A, false, apple = true, fx = setOf("beat-speakers"))),
        "scene-lofi" to ("Lo-fi beats" to P(0xFFFFB4A2, 0xFF1E1B2E, 0xFF2A2640, 0xFFF4EEF8, 0xFFB3A9C8, 0xFF403A5E, false,
            listOf(0xFF1E1B2E, 0xFF2E2540, 0xFF3A2A44), apple = true, fx = setOf("beat-stars"))),
        "scene-disco" to ("Disco" to P(0xFFFF4FD8, 0xFF1A0626, 0xFF2A0E3A, 0xFFFFEFFB, 0xFFCFA6D6, 0xFF4A1A60, false,
            listOf(0xFF1A0626, 0xFF3A0A4A, 0xFF1A0626), apple = true, glow = true, fx = setOf("beat-disco"))),
        "scene-funk" to ("Funk" to P(0xFFFF8A1F, 0xFF2A1306, 0xFF3A1E0E, 0xFFFFF1E3, 0xFFD8B494, 0xFF5A3018, false,
            listOf(0xFF2A1306, 0xFF3E1A08, 0xFF2A1306), apple = true, fx = setOf("beat-neon"))),
        "scene-kpop" to ("K-pop" to P(0xFFC2187A, 0xFFFFF0F8, 0xFFFFFFFF, 0xFF2A0E22, 0xFF7A4A68, 0xFFF8CCE6, true,
            listOf(0xFFFFF0F8, 0xFFF0E8FF, 0xFFE8F6FF), apple = true, fx = setOf("beat-confetti"))),
        "scene-country" to ("Country road" to P(0xFF9A3B12, 0xFFF6EBD9, 0xFFFFF8EC, 0xFF2E2014, 0xFF6E5A44, 0xFFE8D3B2, true, apple = true, fx = setOf("beat-ripples"))),
        "scene-grunge" to ("Grunge" to P(0xFFA8C256, 0xFF17150F, 0xFF24211A, 0xFFECE6D6, 0xFFA39C88, 0xFF3E392A, false, apple = true, fx = setOf("beat-glow"))),
        "scene-goth" to ("Goth" to P(0xFFE0306A, 0xFF0B060B, 0xFF180E18, 0xFFF2E8F0, 0xFFA890A4, 0xFF3A1630, false,
            listOf(0xFF0B060B, 0xFF1E0A1A, 0xFF0B060B), apple = true, fx = setOf("beat-heart"))),
        "scene-techno" to ("Techno" to P(0xFF00E5FF, 0xFF050608, 0xFF101317, 0xFFE8F2F5, 0xFF8AA0A8, 0xFF15343C, false, apple = true, glow = true, fx = setOf("beat-flash"))),
        "scene-house" to ("House party" to P(0xFFFFB000, 0xFF0E0820, 0xFF1A1238, 0xFFF4EEFF, 0xFFA89CC8, 0xFF382A6A, false,
            listOf(0xFF0E0820, 0xFF24104A, 0xFF0E0820), apple = true, glow = true, fx = setOf("beat-lasers"))),
        "scene-trance" to ("Trance" to P(0xFF7FD8FF, 0xFF060A1E, 0xFF0E1638, 0xFFE8F2FF, 0xFF94A8CC, 0xFF1E3066, false,
            listOf(0xFF060A1E, 0xFF0E1A4A, 0xFF20104A), stars = true, apple = true, fx = setOf("beat-aurora"))),
        "scene-dnb" to ("Drum and bass" to P(0xFF3DFF8B, 0xFF060E0A, 0xFF0E1C14, 0xFFE6FAEE, 0xFF8FB8A0, 0xFF16402A, false, apple = true, glow = true, fx = setOf("beat-grid"))),
        "scene-dubstep" to ("Dubstep" to P(0xFFB266FF, 0xFF0A0612, 0xFF160E24, 0xFFF0E8FF, 0xFFA898C8, 0xFF30205A, false,
            listOf(0xFF0A0612, 0xFF1E0E3A, 0xFF0A0612), apple = true, glow = true, fx = setOf("beat-rings"))),
        "scene-ambient" to ("Ambient" to P(0xFF9FD9C9, 0xFF0E1A1E, 0xFF16262C, 0xFFE6F4F2, 0xFF94B4B0, 0xFF26403F, false,
            listOf(0xFF0E1A1E, 0xFF14282E, 0xFF1A2238), apple = true, fx = setOf("beat-blobs"))),
        "scene-classical" to ("Concert hall" to P(0xFF7A1E2C, 0xFFF7F2EA, 0xFFFFFFFF, 0xFF241C18, 0xFF6A5E54, 0xFFE6DCCB, true, apple = true, fx = setOf("beat-fireflies"))),
        "scene-opera" to ("Opera house" to P(0xFFF2C14E, 0xFF2A060C, 0xFF3A0C14, 0xFFFFF0E8, 0xFFD6A8A8, 0xFF5A1A24, false,
            listOf(0xFF2A060C, 0xFF40101A, 0xFF2A060C), apple = true, fx = setOf("beat-frame"))),
        "scene-soul" to ("Soul" to P(0xFFFF9E5E, 0xFF1E0E14, 0xFF2C1620, 0xFFFFEEE6, 0xFFCCA8A4, 0xFF4A2630, false, apple = true, fx = setOf("beat-fog"))),
        "scene-gospel" to ("Gospel choir" to P(0xFF6A2BB8, 0xFFFFF8E8, 0xFFFFFFFF, 0xFF2A1E10, 0xFF6E6048, 0xFFF0E2BE, true,
            listOf(0xFFFFF8E8, 0xFFFFF0D0), apple = true, fx = setOf("beat-burst"))),
        "scene-bossa" to ("Bossa nova" to P(0xFF0A7A6A, 0xFFF0F6EC, 0xFFFFFFFF, 0xFF15281F, 0xFF52685C, 0xFFCFE6D2, true, apple = true, fx = setOf("beat-bloom"))),
        "scene-flamenco" to ("Flamenco" to P(0xFFFF3048, 0xFF160608, 0xFF260C10, 0xFFFFEDE8, 0xFFC8A09A, 0xFF4A141C, false,
            listOf(0xFF160608, 0xFF2A0A0C, 0xFF160608), apple = true, fx = setOf("beat-fireworks"))),
        "scene-afrobeat" to ("Afrobeat" to P(0xFFFFC43D, 0xFF1C0F06, 0xFF2C1A0C, 0xFFFFF3E0, 0xFFD2B48C, 0xFF4A2E14, false,
            listOf(0xFF1C0F06, 0xFF2A1A06, 0xFF0F1C0A), apple = true, fx = setOf("beat-fountain"))),
        "scene-salsa" to ("Salsa" to P(0xFFFF6A45, 0xFF1A0610, 0xFF2A0C1A, 0xFFFFEDF2, 0xFFCCA0AE, 0xFF4A1630, false,
            listOf(0xFF1A0610, 0xFF2E0A1E, 0xFF1A0610), apple = true, fx = setOf("beat-comets"))),
        "scene-spacerock" to ("Space rock" to P(0xFF8AB4FF, 0xFF05070F, 0xFF0E1222, 0xFFE6ECFF, 0xFF96A0C0, 0xFF1E2A4E, false,
            listOf(0xFF05070F, 0xFF0E1430, 0xFF05070F), stars = true, apple = true, fx = setOf("beat-radar"))),
        "scene-chiptune" to ("Chiptune" to P(0xFF5CFF7A, 0xFF0A0A18, 0xFF161630, 0xFFECF0FF, 0xFFA0A4C8, 0xFF2A2A5A, false, apple = true, glow = true, fx = setOf("beat-bubbles"))),
        "scene-citypop" to ("City pop" to P(0xFFFF7BAC, 0xFF0E1430, 0xFF182046, 0xFFF0F2FF, 0xFFA0A8D0, 0xFF2A3670, false,
            listOf(0xFF0E1430, 0xFF2A1A4A, 0xFF4A1E4A), stars = true, apple = true, fx = setOf("beat-sparks"))),
    )

    /** The themes in the Look menu after Light, Dark and Now playing, in this order. */
    private val THEMES = listOf("midnight", "sunset", "forest", "ocean", "paper", "mono", "synthwave", "coffee", "rose", "nord",
        "dracula", "solarized", "matrix", "sakura", "arctic", "ember", "lavender", "slate", "goldblack", "contrast",
        "cyberpunk", "vaporwave", "royal", "olive", "lime", "crimson", "deepsea", "cobalt", "terracotta", "chalkboard",
        "mint", "lemonade", "peach", "newsprint", "bubblegum",
        "neon-tokyo", "neon-miami", "neon-acid", "neon-laser", "neon-arcade", "neon-ultraviolet", "neon-toxic", "neon-plasma", "neon-electric", "neon-ruby", "neon-grid", "neon-jungle", "neon-hologram", "neon-sunrise", "neon-magma", "neon-orchid", "neon-ice", "neon-lagoon", "neon-voltage", "neon-bloodmoon", "neon-retrowave", "neon-nebula", "neon-cherry", "neon-citrus", "neon-mintpurple", "neon-blueflame", "neon-rave", "neon-radioactive", "neon-afterhours", "neon-sodium",
        // 4.0.14: thirty more
        "stainedglass", "patina", "blueprint", "terrazzo", "artdeco", "bauhaus", "jade", "coralreef", "amber", "inkwash", "denim", "velvet", "glacier", "obsidian", "tropical", "carnival", "origami", "marble", "moss", "peacock", "lighthouse", "teahouse", "crayon", "lanternfest", "cartridge", "glowshroom", "opal", "foundry", "plumwine", "kitesky",
        // 4.5.3: Music scenes
        "scene-jazz", "scene-blues", "scene-punk", "scene-metal", "scene-reggae", "scene-hiphop", "scene-lofi", "scene-disco", "scene-funk", "scene-kpop", "scene-country", "scene-grunge", "scene-goth", "scene-techno", "scene-house", "scene-trance", "scene-dnb", "scene-dubstep", "scene-ambient", "scene-classical", "scene-opera", "scene-soul", "scene-gospel", "scene-bossa", "scene-flamenco", "scene-afrobeat", "scene-salsa", "scene-spacerock", "scene-chiptune", "scene-citypop")

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
        "neon-tokyo" to "Hot pink neon over a midnight-blue city",
        "neon-miami" to "Cyan neon on deep purple fading to dark pink",
        "neon-acid" to "Acid yellow-green on a near-black green",
        "neon-laser" to "Magenta laser light on dark indigo",
        "neon-arcade" to "Arcade yellow on a dark blue cabinet",
        "neon-ultraviolet" to "Blacklight violet glowing on black-purple",
        "neon-toxic" to "Radioactive green on a dark green-black",
        "neon-plasma" to "Plasma orange on dark violet",
        "neon-electric" to "Electric blue on deep navy",
        "neon-ruby" to "Ruby red neon on dark wine",
        "neon-grid" to "Cyan light lines on a blue-black grid",
        "neon-jungle" to "Emerald neon in a dark jungle green",
        "neon-hologram" to "Pale holographic cyan shifting to violet",
        "neon-sunrise" to "Amber neon on dark magenta, like a sign at dawn",
        "neon-magma" to "Molten orange-red on a black-red",
        "neon-orchid" to "Orchid pink neon on dark plum",
        "neon-ice" to "Icy blue glow on dark steel blue",
        "neon-lagoon" to "Turquoise neon on a dark lagoon",
        "neon-voltage" to "High-voltage yellow on dark indigo",
        "neon-bloodmoon" to "Red neon on almost black",
        "neon-retrowave" to "Soft pink neon on a starry eighties night",
        "neon-nebula" to "Violet neon among dark, starry space",
        "neon-cherry" to "Cherry pink on dark cola brown-red",
        "neon-citrus" to "Tangerine neon on a dark amber-black",
        "neon-mintpurple" to "Mint green neon on dark purple",
        "neon-blueflame" to "Gas-flame blue on blue-black",
        "neon-rave" to "Rave pink on dark teal",
        "neon-radioactive" to "Lime neon on dark purple",
        "neon-afterhours" to "Aqua neon on a dark wine bar",
        "neon-sodium" to "Orange street-light glow on a navy night",
        "stainedglass" to "Amber light on a deep violet night, with panes of coloured glass",
        "patina" to "Old copper brown turning sea-green, with a verdigris accent",
        "blueprint" to "A draughtsman's blue sheet with white lines and measurements",
        "terrazzo" to "Pale stone flecked with coloured chips, and a brick-red accent",
        "artdeco" to "Gold fans on a deep green-black, like a 1920s cinema",
        "bauhaus" to "Off-white with red, blue and yellow circles, squares and triangles",
        "jade" to "Carved jade green with pale green curls of cloud",
        "coralreef" to "Warm coral branching up through clear deep water",
        "amber" to "Honey-gold resin, dark and warm, with slow drops",
        "inkwash" to "Rice paper, black ink bleeding into it, and a red seal for an accent",
        "denim" to "Indigo cloth with orange stitching",
        "velvet" to "A burgundy stage curtain and brass light",
        "glacier" to "Blue ice lit from inside, with icicles overhead",
        "obsidian" to "Black volcanic glass in sharp facets, with a lava-red accent",
        "tropical" to "Sunny yellow with slices of fruit and a hot-pink accent",
        "carnival" to "A fairground night in deep blue, strung with coloured flags",
        "origami" to "White paper, sharp folds and an orange accent",
        "marble" to "Pale veined marble with a brass accent",
        "moss" to "Grey river stones and soft green moss",
        "peacock" to "Royal blue into teal, with the eyes of a peacock's tail",
        "lighthouse" to "A dark sea night with a warm beam sweeping round",
        "teahouse" to "Warm paper screens and matcha green, with steam rising",
        "crayon" to "Drawing paper with bright crayon scribbles",
        "lanternfest" to "Paper lanterns glowing orange as they rise into a violet night",
        "cartridge" to "Console grey and button purple, with falling blocks",
        "glowshroom" to "A dark wood lit by glowing mushrooms and drifting spores",
        "opal" to "Milky white with soft bands of colour sliding across",
        "foundry" to "Riveted steel plate with a rust-orange accent",
        "plumwine" to "Deep plum, slowly swirling, with a rose accent",
        "kitesky" to "A bright blue afternoon with kites drifting on their strings",
        "scene-jazz" to "A smoky late-night club: warm brass on dark wood, stage lights swaying to the music",
        "scene-blues" to "Midnight blue and a slow sound wave rolling across the middle",
        "scene-punk" to "Black and hot pink, the screen shaking with the bass",
        "scene-metal" to "Black steel and blood red, with lightning on the hardest hits",
        "scene-reggae" to "Deep green into red with a sunny yellow accent, soft lights swelling with the bass",
        "scene-hiphop" to "Charcoal and gold, two speakers thumping in the corners",
        "scene-lofi" to "A cosy late-night study room in soft lilac, stars twinkling with the hi-hats",
        "scene-disco" to "Seventies purple and pink, a disco ball turning and flashing on the beat",
        "scene-funk" to "Burnt orange and brown, neon shapes flaring on the beat",
        "scene-kpop" to "Bright pastel pink and lilac, with confetti on the beat",
        "scene-country" to "Sun-faded cream and rust, ripples spreading on each beat",
        "scene-grunge" to "Worn flannel olive and brown, the edges glowing with the bass",
        "scene-goth" to "Black lace and deep rose, a heart monitor beating along the bottom",
        "scene-techno" to "A dark warehouse in cold cyan, flashing on every kick",
        "scene-house" to "Indigo and amber, lasers fanning up from the floor",
        "scene-trance" to "Deep blue night, ribbons of light dancing across the top",
        "scene-dnb" to "Black and acid green, a neon grid racing towards you",
        "scene-dubstep" to "Dark violet, rings blasting out from the middle on every drop",
        "scene-ambient" to "Calm sea-glass greens, soft blobs drifting with the bass",
        "scene-classical" to "Ivory and burgundy, fireflies blinking together on the beat",
        "scene-opera" to "Red velvet and gold, a gilded frame pulsing with the music",
        "scene-soul" to "Warm plum and peach, stage fog rising on the strong beats",
        "scene-gospel" to "Warm gold light and choir purple, rays bursting from the middle",
        "scene-bossa" to "Fresh Rio greens, a flower in the corner opening with the music",
        "scene-flamenco" to "Black and scarlet, fireworks bursting on the strong beats",
        "scene-afrobeat" to "Earth browns and gold, fountains jumping with the rhythm",
        "scene-salsa" to "Hot red and deep rose, comets streaking across on the strong beats",
        "scene-spacerock" to "Deep space navy, a radar sweep picking up the music",
        "scene-chiptune" to "An old handheld game screen, bubbles popping on the beat",
        "scene-citypop" to "Eighties Tokyo at night, sparks rising over the skyline",
    )
    /** The 30 neon looks (key, name, its neon accent, its gradient's middle shade): the Visualizer's colours too. */
    val NEON_LOOKS: List<Triple<String, String, Pair<Color, Color>>> get() = STYLED.filter { it.value.second.glow }.map { (k, v) ->
        Triple(k, v.first, Color(v.second.accent) to Color(v.second.grad?.getOrNull(1) ?: v.second.bg)) }

    /** A look you made (Personalization › Make your own look): its colours as 0xAARRGGBB; grad: a second background colour
     *  the window fades into (0: none); glow: neon, what's on glows; stars: a few stars over it. */
    class CustomLook(val key: String, val name: String, val accent: Long, val bg: Long, val card: Long, val text: Long,
                     val grad: Long = 0, val glow: Boolean = false, val stars: Boolean = false, val fx: Set<String> = emptySet())

    /** Where the looks you made are kept (the app's settings, read as Compose state); the app sets it. */
    var customSource: () -> List<CustomLook> = { emptyList() }
    private var customFrom: List<CustomLook>? = null
    private var customMap: Map<String, Pair<String, P>> = emptyMap()
    /** Your looks, as the built-in ones are kept: muted text, the selection and light or dark worked out from your colours. */
    private fun customs(): Map<String, Pair<String, P>> {
        val l = customSource()
        if (l !== customFrom) {
            customMap = l.associate { c ->
                val bg = Color(c.bg); val fg = Color(c.text)
                val light = bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f > 0.55f
                fun argb(col: Color): Long = (col.toArgbInt().toLong() and 0xFFFFFFFFL)
                c.key to (c.name to P(c.accent, c.bg, c.card, c.text, argb(lerp(fg, bg, 0.42f)), argb(lerp(bg, fg, 0.18f)), light,
                    if (c.grad != 0L) listOf(c.bg, c.grad, c.bg) else null, stars = c.stars, apple = true, glow = c.glow, fx = c.fx))
            }
            customFrom = l
        }
        return customMap
    }
    private fun Color.toArgbInt(): Int = (((alpha * 255f + 0.5f).toInt() and 0xFF) shl 24) or (((red * 255f + 0.5f).toInt() and 0xFF) shl 16) or
        (((green * 255f + 0.5f).toInt() and 0xFF) shl 8) or ((blue * 255f + 0.5f).toInt() and 0xFF)
    private fun styledLook(look: String): P? = STYLED[look]?.second ?: customs()[look]?.second

    /** Whether a look is a light one (bright pages, dark text): the Look menu's sections go by it. */
    fun isLight(look: String): Boolean = styledLook(look)?.isLight ?: false

    /** A look's one-line description ("" for one that has none). */
    fun describe(look: String): String = DESC[look] ?: if (customs().containsKey(look)) "Your own look" else ""

    /** The Look menu: Light, Dark, Now playing (every screen in the colours of the song playing, the way Now playing
     *  is; Light or Dark while nothing is), then the themes. The other looks stay for the Visualizer and Wrapped. */
    private val BUILT: List<Pair<String, String>> = listOf("apple" to "Light", "apple-dark" to "Dark", "nowplaying" to "Now playing") +
        THEMES.map { it to (STYLED[it]?.first ?: it) }
    /** ... and then the looks you made. */
    val ALL: List<Pair<String, String>> get() = BUILT + customs().map { (k, v) -> k to "${v.first} (yours)" }
    /** The Now playing look's key. */
    const val NOW_PLAYING = "nowplaying"
    /** The looks Now playing can fall back to while nothing is playing: every look in the Look menu but Now playing. */
    val FALLBACK: List<Pair<String, String>> get() = ALL.filter { it.first != NOW_PLAYING }
    fun known(look: String) = ALL.any { it.first == look }
    /** A look Now playing can fall back to (a saved one that's no longer known goes back to Dark). */
    fun knownFallback(look: String) = FALLBACK.any { it.first == look }

    /** A look's colours, for its preview in the menu (Now playing: a sample cover's). */
    class LookColors(val bg: Color, val card: Color, val fg: Color, val muted: Color, val accent: Color,
                     val grad: List<Color>?, val stars: Boolean, val glow: Boolean, val fx: Set<String> = emptySet())
    fun colors(look: String): LookColors = styledLook(look)?.let { p ->
        LookColors(Color(p.bg), Color(p.light), Color(p.fg), Color(p.muted), Color(p.accent), p.grad?.map { Color(it) }, p.stars, p.glow, p.fx)
    } ?: NEON[look]?.second?.let { n ->
        LookColors(lerp(Color.Black, n, 0.02f), lerp(Color.Black, n, 0.06f), Color(0xFFF4F4F4), lerp(Color(0xFF8A8A8A), n, 0.25f), n, null, false, true)
    } ?: LookColors(Color(0xFF3A1D33), Color(0xFF52304A), Color.White, Color(0xFFD8C2CF), Color(0xFFFFB46B),
        listOf(Color(0xFF6B2E5A), Color(0xFF2A1426)), false, false)

    /** A look's background and accent, for the little swatch beside its name in the menu (Now playing: a sample). */
    fun swatch(look: String): Pair<Color, Color> = styledLook(look)?.let { Color(it.grad?.getOrNull(1) ?: it.bg) to Color(it.accent) }
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
        val styled = styledLook(look)
        C.isLight = look == "light" || styled?.isLight == true
        C.glow = neon != null || styled?.glow == true
        C.gradient = styled?.grad?.map { Color(it) }
        C.stars = styled?.stars == true
        C.fx = styled?.fx ?: emptySet()
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
        // 4.0.14: thirty more
        "ticket" to AppStyle("Ticket", "Every row a ticket stub: dashed outlines and small corners",
            small = 4.dp, card = 6.dp, big = 8.dp, cards = CardStyle.OUTLINE, dashed = true, border = 1.2f),
        "brutal" to AppStyle("Brutalist", "Thick black-marker outlines, square corners and heavy type",
            small = 0.dp, card = 0.dp, big = 0.dp, pill = 0.dp, weightShift = 300, titleScale = 1.2f, cards = CardStyle.OUTLINE, border = 2.5f, lineWidth = 2.dp),
        "poster" to AppStyle("Poster", "Huge heavy titles, no frames, room to breathe",
            weightShift = 300, titleScale = 1.3f, textScale = 0.98f, space = 1.2f, border = 0f),
        "hairline" to AppStyle("Hairline", "Fine type, fine lines, barely any corners",
            small = 2.dp, card = 4.dp, big = 8.dp, pill = 4.dp, weightShift = -100, border = 0.5f, line = 1.4f),
        "largeprint" to AppStyle("Large print", "Everything bigger: type, titles and buttons, easy to read at arm's length",
            textScale = 1.18f, titleScale = 1.2f, icon = 1.2f, space = 1.2f),
        "pocket" to AppStyle("Pocket", "The smallest of all: tiny type and covers, the most on one screen",
            small = 3.dp, card = 8.dp, big = 12.dp, textScale = 0.85f, titleScale = 0.86f, space = 0.5f, art = 0.65f, icon = 0.8f),
        "gallery" to AppStyle("Gallery", "Big covers hung like pictures, no lines or frames around them",
            small = 2.dp, card = 4.dp, big = 6.dp, art = 1.5f, space = 1.3f, border = 0f, line = 0f, quiet = 0.85f),
        "bubble" to AppStyle("Bubble", "Round raised bubbles for every row and tile",
            small = 22.dp, card = 36.dp, big = 44.dp, cards = CardStyle.RAISED, icon = 1.15f, space = 1.2f),
        "squircle" to AppStyle("Squircle", "Soft squares, like app icons, on soft cards",
            small = 10.dp, card = 20.dp, big = 28.dp, pill = 14.dp, cards = CardStyle.SOFT),
        "cutglass" to AppStyle("Cut glass", "See-through cards with square corners and bright edges",
            small = 0.dp, card = 2.dp, big = 2.dp, pill = 2.dp, cards = CardStyle.GLASS, border = 1.6f),
        "frosted" to AppStyle("Frosted pills", "Frosted see-through pills and light, thin type",
            small = 18.dp, card = 30.dp, big = 38.dp, cards = CardStyle.GLASS, weightShift = -100, space = 1.2f),
        "ledger" to AppStyle("Ledger", "An account book: monospace figures on firmly ruled lines",
            small = 2.dp, card = 2.dp, big = 4.dp, pill = 4.dp, font = FontFamily.Monospace, textScale = 0.92f, titleScale = 0.95f, space = 0.8f, line = 1.8f, lineWidth = 1.dp),
        "console" to AppStyle("Console", "Bold monospace type in outlined boxes",
            small = 4.dp, card = 4.dp, big = 6.dp, pill = 6.dp, font = FontFamily.Monospace, weightShift = 200, textScale = 0.94f, cards = CardStyle.OUTLINE),
        "novel" to AppStyle("Novel", "Serif type throughout, like a printed book",
            font = FontFamily.Serif, textScale = 1.04f, titleScale = 1.06f, space = 1.15f, line = 0.8f),
        "broadsheet" to AppStyle("Broadsheet", "A newspaper's big serif headlines over ruled columns",
            small = 0.dp, card = 0.dp, big = 0.dp, pill = 2.dp, titleFont = FontFamily.Serif, titleScale = 1.25f, weightShift = 100, line = 1.5f, lineWidth = 1.dp),
        "storybook" to AppStyle("Storybook", "Serif type on big soft rounded cards",
            small = 16.dp, card = 26.dp, big = 34.dp, font = FontFamily.Serif, cards = CardStyle.SOFT, space = 1.15f),
        "monoround" to AppStyle("Round mono", "Monospace type on soft round cards: a friendly terminal",
            small = 14.dp, card = 24.dp, big = 32.dp, font = FontFamily.Monospace, textScale = 0.94f, cards = CardStyle.SOFT),
        "airy" to AppStyle("Airy", "The most space of all between things, with quiet small text",
            textScale = 1.02f, space = 1.7f, art = 1.15f, quiet = 0.8f, line = 0.4f),
        "dense" to AppStyle("Dense cards", "Raised cards packed close, smaller covers and type",
            small = 6.dp, card = 10.dp, big = 14.dp, cards = CardStyle.RAISED, textScale = 0.93f, titleScale = 0.95f, space = 0.7f, art = 0.85f),
        "slab" to AppStyle("Slab", "Heavy type on thick raised slabs with tight corners",
            small = 3.dp, card = 4.dp, big = 6.dp, pill = 4.dp, weightShift = 300, cards = CardStyle.RAISED, border = 2f),
        "whisper" to AppStyle("Whisper", "The quietest: thin type, faint small text, hardly a line",
            weightShift = -200, textScale = 0.98f, quiet = 0.65f, border = 0f, line = 0.3f),
        "ruled" to AppStyle("Ruled", "Thick ruled lines between rows, square corners",
            small = 0.dp, card = 0.dp, big = 2.dp, pill = 2.dp, border = 1.5f, line = 2.2f, lineWidth = 1.5.dp, space = 1.05f),
        "dotted" to AppStyle("Dotted", "Round corners and dotted lines between rows",
            small = 8.dp, card = 12.dp, big = 18.dp, dashed = true, line = 1.3f, lineWidth = 1.dp),
        "sticker" to AppStyle("Sticker", "Fat round outlines and bold type, like stickers",
            small = 18.dp, card = 26.dp, big = 34.dp, weightShift = 200, cards = CardStyle.OUTLINE, border = 2.5f, icon = 1.15f),
        "bigbuttons" to AppStyle("Big buttons", "Much bigger icon buttons, with room around them",
            icon = 1.45f, space = 1.25f, art = 1.05f),
        "listfirst" to AppStyle("List first", "Small covers and larger names: the words lead",
            textScale = 1.02f, titleScale = 1.05f, space = 0.8f, art = 0.6f),
        "capsule" to AppStyle("Capsule", "Everything a capsule: fully round ends, thin outlines",
            small = 30.dp, card = 40.dp, big = 50.dp, cards = CardStyle.OUTLINE),
        "notched" to AppStyle("Notched", "Square chips and sheets with round cards between them",
            small = 0.dp, card = 12.dp, big = 0.dp, pill = 0.dp),
        "zine" to AppStyle("Zine", "A photocopied fanzine: serif text, monospace titles, dashed lines",
            small = 2.dp, card = 2.dp, big = 4.dp, font = FontFamily.Serif, titleFont = FontFamily.Monospace, weightShift = 100, dashed = true, lineWidth = 1.dp),
        "calm" to AppStyle("Calm", "Soft cards, lighter type and plenty of space: easy on the eyes",
            small = 14.dp, card = 22.dp, big = 30.dp, weightShift = -100, textScale = 1.02f, cards = CardStyle.SOFT, space = 1.3f, quiet = 0.85f),
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
