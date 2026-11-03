package org.omarchy.music.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.omarchy.music.Prefs

/** A lyrics card's style: its background (a fade from top to bottom), the card behind the words (its colour, how see-through,
 *  its corners), the words', the song name's, the soft text's and the "Radon Player" mark's colours, the stars and effects
 *  behind (each effect in its own colour, see [LookFx]), the type (bold or not, its size) and the cover (square or round). */
data class MyCard(val key: String, val name: String, val top: Long, val bottom: Long, val panel: Long, val panelAlpha: Float,
                  val words: Long, val title: Long, val soft: Long, val brand: Long, val fx: Set<String> = emptySet(),
                  val stars: Boolean = false, val corners: Float = 44f, val heavy: Boolean = false, val roundCover: Boolean = false,
                  val textScale: Float = 1f) {
    fun encode() = listOf(key, name.replace('|', '/'), top.toString(16), bottom.toString(16), panel.toString(16), panelAlpha,
        words.toString(16), title.toString(16), soft.toString(16), brand.toString(16), fx.joinToString(","), stars, corners, heavy,
        roundCover, textScale).joinToString("|")
}

/** Lyrics card styles: Your look (the card in the app's look, as it always was), 30 ready-made ones, and the ones you make
 *  (Settings › Personalization › Make your own › New lyrics card), kept with the app's settings so Sync and backups carry
 *  them. The Lyrics card box picks one; the last one picked stays. */
object LyricsCards {
    const val PREF = "my_lyric_cards"      // yours, one a line (see MyCard.encode)
    const val PICKED = "lyric_card_style"  // the one in use
    const val LOOK = "look"                // Your look
    const val NOW = "nowplaying"           // Now playing: as the Now playing screen looks (the cover's colours), whatever the look

    private fun p(n: Int, name: String, top: Long, bottom: Long, panel: Long, alpha: Float, words: Long, title: Long, soft: Long,
                  fx: String = "", stars: Boolean = false, corners: Float = 44f, heavy: Boolean = false, round: Boolean = false,
                  scale: Float = 1f, brand: Long = title) =
        MyCard("card-$n", name, top, bottom, panel, alpha, words, title, soft, brand,
            fx.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(), stars, corners, heavy, round, scale)

    val PRESETS: List<MyCard> = listOf(
        p(1, "Synthwave", 0xFF0F0524, 0xFF2A0845, 0xFF000000, 0.35f, 0xFFFFFFFF, 0xFFFF2FA8, 0xFFC9A7FF, "grid@ffff2fa8,sun@ffffc21a", corners = 28f),
        p(2, "Aurora night", 0xFF041B2D, 0xFF0A0F1F, 0xFF000000, 0.30f, 0xFFE8FFF8, 0xFF1FFF6E, 0xFF8FD8C8, "aurora,twinkle", stars = true),
        p(3, "Golden hour", 0xFF5A330C, 0xFF1A0E05, 0xFF000000, 0.25f, 0xFFFFF4DF, 0xFFFFC21A, 0xFFE0B878, "sunbeams@ffffd27a,embers@ffffa23a", heavy = true),
        p(4, "Cherry blossom", 0xFFFCE4EC, 0xFFF8BBD0, 0xFFFFFFFF, 0.55f, 0xFF4A1F2E, 0xFFD81B60, 0xFF8E5A6B, "petals@ffff8fb1", corners = 64f, round = true),
        p(5, "Ocean deep", 0xFF02203A, 0xFF010B16, 0xFF000000, 0.30f, 0xFFE3F4FF, 0xFF14E4FF, 0xFF7FB3D0, "sea,bubbles@ff8fe8ff,moon"),
        p(6, "Misty pines", 0xFF13261A, 0xFF07120B, 0xFF000000, 0.30f, 0xFFEAF5E6, 0xFF9BE37A, 0xFF9BB59A, "pines,fog"),
        p(7, "Desert dusk", 0xFF7A3B1E, 0xFF2B1408, 0xFF000000, 0.25f, 0xFFFFF1E3, 0xFFFF9F43, 0xFFE2B08A, "dunes,sun@ffff7a1a"),
        p(8, "Old TV", 0xFF161616, 0xFF262626, 0xFF000000, 0.45f, 0xFFD8FFD8, 0xFF1FFF6E, 0xFF7FBF7F, "crt,scanlines", corners = 8f, heavy = true),
        p(9, "Code rain", 0xFF000000, 0xFF001A08, 0xFF000000, 0.50f, 0xFFB6FFB0, 0xFF1FFF6E, 0xFF5FBF5F, "digital@ff1fff6e", corners = 4f),
        p(10, "Cotton candy", 0xFFFFD6F5, 0xFFC9E7FF, 0xFFFFFFFF, 0.60f, 0xFF3A2E4A, 0xFFA24DFF, 0xFF7A6A8E, "bubbles@ffffffff,sparkle@ffa24dff", corners = 72f, round = true),
        p(11, "Thunderstorm", 0xFF1B2330, 0xFF0B0F16, 0xFF000000, 0.35f, 0xFFEEF3FA, 0xFF8EC5FF, 0xFF9AA7BA, "rain,lightning,clouds"),
        p(12, "First snow", 0xFFDDEBF7, 0xFFAFC8DE, 0xFFFFFFFF, 0.50f, 0xFF1E2B3A, 0xFF2F7BFF, 0xFF5E7389, "snow@ffffffff", corners = 40f),
        p(13, "Galaxy", 0xFF0A0418, 0xFF1B0B33, 0xFF000000, 0.30f, 0xFFF4EEFF, 0xFFC084FC, 0xFFA99BC8, "spiral,nebula,twinkle", stars = true),
        p(14, "Lava lamp", 0xFF2A0505, 0xFF120202, 0xFF000000, 0.30f, 0xFFFFE9E2, 0xFFFF4D1A, 0xFFE09A88, "lava@ffff4d1a,embers"),
        p(15, "Notebook", 0xFFF5F2EA, 0xFFE9E3D3, 0xFFFFFFFF, 0.70f, 0xFF222222, 0xFF111111, 0xFF6B665C, "graph@ffcfc7b4,grain", corners = 12f, heavy = true),
        p(16, "Comic book", 0xFFFFE14D, 0xFFFFC21A, 0xFFFFFFFF, 0.90f, 0xFF111111, 0xFFE53935, 0xFF444444, "halftone@ffe0a800,burst@ffffffff", corners = 10f, heavy = true, scale = 1.1f),
        p(17, "Vaporwave", 0xFF3A1C71, 0xFFD76D77, 0xFF000000, 0.25f, 0xFFFFFFFF, 0xFF01CDFE, 0xFFFFB3E6, "grid@ff01cdfe,prism"),
        p(18, "Firefly meadow", 0xFF0F1F12, 0xFF050A06, 0xFF000000, 0.30f, 0xFFF4FFE8, 0xFFE6FF5C, 0xFFA8C090, "fireflies@ffe6ff5c,pines"),
        p(19, "Love letter", 0xFF3A0A1A, 0xFF1A050C, 0xFF000000, 0.30f, 0xFFFFE8EE, 0xFFFF5C8A, 0xFFD98FA3, "hearts@ffff5c8a,bokeh", corners = 60f, round = true),
        p(20, "Concert", 0xFF050505, 0xFF15051F, 0xFF000000, 0.35f, 0xFFFFFFFF, 0xFFFF2FA8, 0xFFBBBBBB, "spotlight,rays,confetti", heavy = true),
        p(21, "Circuit", 0xFF06141A, 0xFF020809, 0xFF000000, 0.40f, 0xFFD8FBFF, 0xFF14E4FF, 0xFF6FB7C2, "circuit@ff14e4ff,glitch", corners = 6f),
        p(22, "Autumn", 0xFF4A2511, 0xFF1F0E06, 0xFF000000, 0.28f, 0xFFFFF0E0, 0xFFFF8A3D, 0xFFD9A47E, "leaves"),
        p(23, "Moonlight", 0xFF0E1430, 0xFF05081A, 0xFF000000, 0.30f, 0xFFEEF1FF, 0xFFDDE3FF, 0xFF9EA6C8, "moon,clouds,twinkle", stars = true),
        p(24, "Hyperspace", 0xFF000000, 0xFF0A0A14, 0xFFFFFFFF, 0.06f, 0xFFE8E8FF, 0xFFFFFFFF, 0xFF9A9AB8, "warp,comets"),
        p(25, "Cabin plaid", 0xFF3B1414, 0xFF1E0A0A, 0xFF000000, 0.35f, 0xFFFFF3E8, 0xFFFFC97A, 0xFFD4A98A, "plaid,embers", corners = 18f),
        p(26, "Polka pop", 0xFFFFF3B0, 0xFFFFD1DC, 0xFFFFFFFF, 0.75f, 0xFF2A2A2A, 0xFFFF3D7F, 0xFF6A6A6A, "polka@ffff9ab8", heavy = true, round = true, corners = 56f),
        p(27, "Neon frame", 0xFF07070F, 0xFF0F0F1E, 0xFF000000, 0.30f, 0xFFF0F0FF, 0xFFB6FF1A, 0xFF9AA0B0, "frame@ffb6ff1a,hexagons@ff2a3a10"),
        p(28, "Contours", 0xFF1E2A22, 0xFF0E1510, 0xFF000000, 0.30f, 0xFFEAF2E6, 0xFFC9E4A3, 0xFF9AAE94, "topo@ff4d6b55", corners = 24f),
        p(29, "Meteor night", 0xFF0B0B1A, 0xFF1A0B0B, 0xFF000000, 0.30f, 0xFFFFEDE0, 0xFFFFB347, 0xFFC8A890, "meteors,constellation", stars = true),
        p(30, "Dream orbs", 0xFF1A1033, 0xFF33104A, 0xFF000000, 0.25f, 0xFFFBF4FF, 0xFFFFC2F2, 0xFFC9B4DA, "orbs,drift,sparkle", round = true, corners = 52f),
    )

    private var from = "\u0000"; private var cache: List<MyCard> = emptyList()
    /** The ones you made. */
    fun mine(): List<MyCard> {
        val text = Prefs.text(PREF)
        if (text != from) {
            cache = text.lines().mapNotNull { l -> runCatching { val f = l.split("|")
                MyCard(f[0], f[1], f[2].toLong(16), f[3].toLong(16), f[4].toLong(16), f[5].toFloat(), f[6].toLong(16), f[7].toLong(16),
                    f[8].toLong(16), f[9].toLong(16), f[10].split(",").filter { it.isNotBlank() }.toSet(), f[11].toBoolean(), f[12].toFloat(),
                    f[13].toBoolean(), f[14].toBoolean(), f[15].toFloat()) }.getOrNull() }
            from = text
        }
        return cache
    }
    fun all(): List<MyCard> = PRESETS + mine()
    fun of(key: String): MyCard? = (listOfNotNull(draft) + all()).firstOrNull { it.key == key }

    /** The Style menu: Your look, the ready-made ones, then yours. */
    fun menu(): List<Pair<String, String>> = listOf(LOOK to "Your look", NOW to "Now playing") + PRESETS.map { it.key to it.name } +
        mine().map { it.key to "${it.name} (yours)" }

    /** The style in use (Your look when the one picked is gone). */
    fun picked(): String = Prefs.text(PICKED, LOOK).takeIf { it == LOOK || it == NOW || of(it) != null } ?: LOOK
    fun pick(key: String) = Prefs.setText(PICKED, key)

    fun save(c: MyCard): MyCard {
        val keyed = if (c.key.startsWith("mycard-")) c else c.copy(key = "mycard-" + System.currentTimeMillis().toString(36))
        Prefs.setText(PREF, (mine().filter { it.key != keyed.key } + keyed).joinToString("\n") { it.encode() })
        return keyed
    }
    fun delete(key: String) {
        MadeSync.forget(key)
        Prefs.setText(PREF, mine().filter { it.key != key }.joinToString("\n") { it.encode() })
        if (Prefs.text(PICKED) == key) pick(LOOK)
    }
    /** One style's few words for its menu. */
    fun describe(key: String): String {
        if (key == LOOK) return "The card in the app's look in use: its colours, stars and effects"
        if (key == NOW) return "The card as the Now playing screen looks with the Now playing look: the cover's colours, with your effects"
        val c = of(key) ?: return ""
        val fx = LookFx.keys(c.fx).mapNotNull { k -> LookFx.ALL.firstOrNull { it.key == k }?.name }
        return (if (fx.isEmpty()) "No effects" else fx.joinToString(", ")) + (if (c.stars) ", stars" else "") +
            (if (c.roundCover) ", round cover" else "") + (if (c.heavy) ", bold words" else "")
    }
    /** The one being made: the maker's preview draws it. */
    var draft by mutableStateOf<MyCard?>(null)
}
