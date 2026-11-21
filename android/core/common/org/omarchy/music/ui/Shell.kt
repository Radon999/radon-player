package org.omarchy.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.omarchy.music.Prefs

// Layouts (Settings › Personalization › Layout): where things are on the screen, apart from the colours (Look) and the
// drawing (Theme), so any layout goes with any look and theme. A layout picks how you get around (the ☰ menu on the
// left or right, a bar of buttons along the bottom, a rail down the side, or tabs along the top), which buttons that
// bar holds (slots), how the mini player looks and where it sits, and how Now playing is arranged. The ☰ menu is in
// every layout (a bar always has its ☰ button), so every tab and feature can be reached whichever is picked.

/** How you get around: the ☰ menu only, a bar along the bottom, a rail down the side, or tabs along the top. */
enum class NavKind(val label: String) { DRAWER("☰ menu only"), BOTTOM("Bottom bar"), RAIL("Side rail"), TOP("Top tabs") }

/** The mini player on the lists: a floating card, a flat bar along the bottom, a small pill, or along the top. */
enum class MiniKind(val label: String) { FLOAT("Floating card"), BAR("Flat bar"), PILL("Small pill"), TOP("At the top"), NONE("None") }

/** Now playing's arrangement. */
enum class NpKind(val label: String, val desc: String) {
    CLASSIC("Classic", "A big cover, the song under it, then the controls"),
    CENTERED("Centred", "A smaller cover in the middle with everything centred under it"),
    SPLIT("Side by side", "The cover on one side and the song and controls on the other (stacked on a narrow phone)"),
    INFO_TOP("Song on top", "The song's name first, the cover under it"),
    BLEED("Full cover", "The cover edge to edge across the top, no frame"),
    HEADLINE("Headline", "The song's name in big type with a smaller cover beside it"),
}

/** A button a bar or rail can hold. */
class Slot(val key: String, val label: String, val ico: Ico, val isOn: (Ui) -> Boolean, val open: (Ui) -> Unit)

/** A layout. slots: the bar's, rail's or tabs' buttons in order (☰ is added when missing). */
data class UiLayout(
    val key: String, val name: String, val desc: String,
    val nav: NavKind = NavKind.DRAWER, val menuRight: Boolean = false, val slots: List<String> = emptyList(),
    val labels: Boolean = true, val mini: MiniKind = MiniKind.FLOAT, val np: NpKind = NpKind.CLASSIC,
    val toolsTop: Boolean = false, val titleCenter: Boolean = false,
    /** More of your choices (Make your own layout › More), by name: see [LayoutExtras]. */
    val extras: Map<String, String> = emptyMap(),
) {
    /** One of the extras, else its standard value. */
    fun x(key: String): String = extras[key] ?: LayoutExtras.ALL.firstOrNull { it.key == key }?.options?.first()?.first.orEmpty()

    /** The bar's buttons, known ones only, with ☰ always among them (so the whole menu is always a tap away). */
    val navSlots: List<Slot> get() {
        val keys = slots.filter { k -> UiLayouts.SLOTS.any { it.key == k } }.distinct().let { if ("menu" in it) it else it + "menu" }
        return keys.mapNotNull { k -> UiLayouts.SLOTS.firstOrNull { it.key == k } }
    }
    /** Where this layout puts things, in words: how you get around, the mini player, the titles and Now playing. */
    fun explain(): String {
        val side = if (menuRight) "right" else "left"
        val buttons = navSlots.filter { it.key != "menu" }.take(7).joinToString(", ") { it.label }
        val around = when (nav) {
            NavKind.DRAWER -> "Get around with the \u2630 menu, its button at the top $side; it slides in from the $side."
            NavKind.BOTTOM -> "A bar of buttons along the bottom${if (labels) ", with their names" else ", icons only"}: $buttons, and \u2630 for everything else."
            NavKind.RAIL -> "A rail of buttons down the left side${if (labels) ", with their names" else ", icons only"}: $buttons, and \u2630 for everything else."
            NavKind.TOP -> "Tabs along the top: $buttons, and \u2630 for everything else."
        }
        val miniText = when (mini) {
            MiniKind.FLOAT -> "The mini player floats as a card at the bottom."
            MiniKind.BAR -> "The mini player is a flat bar across the bottom."
            MiniKind.PILL -> "The mini player is a small pill at the bottom, with a ring for the song's progress."
            MiniKind.TOP -> "The mini player sits at the top, under the page's title."
            MiniKind.NONE -> "No mini player: open Now playing from the menu or the bar."
        }
        val npText = when (np) {
            NpKind.CLASSIC -> "Now playing: the cover on top, the song's name and the controls under it."
            NpKind.CENTERED -> "Now playing: a smaller cover in the middle, everything centred under it."
            NpKind.SPLIT -> "Now playing: the cover and the controls side by side (on a phone, the cover smaller above them)."
            NpKind.INFO_TOP -> "Now playing: the song's name above the cover, the controls under it."
            NpKind.BLEED -> "Now playing: the cover edge to edge across the top, the song and controls under it."
            NpKind.HEADLINE -> "Now playing: the song's name in big type beside a smaller cover."
        }
        val extra = listOfNotNull(if (toolsTop) "Queue, lyrics, visualizer and equalizer buttons above the song." else null,
            if (titleCenter) "Page titles centred." else null)
        val more = LayoutExtras.ALL.mapNotNull { e -> extras[e.key]?.takeIf { it != e.options.first().first }?.let { v ->
            "${e.name}: ${e.options.firstOrNull { it.first == v }?.second ?: v}." } }
        return (listOf(desc + ".", around, miniText, npText) + extra + more).joinToString(" ").replace("..", ".")
    }

    /** ☰ shows in the screens' headers: when there's no bar holding it. */
    val headerMenu: Boolean get() = nav == NavKind.DRAWER

    /** One line for saving a layout of your own: key|name|nav|menuRight|slots|labels|mini|np|toolsTop|titleCenter. */
    fun encode(): String = listOf(key, name.replace('|', '/').replace('\n', ' '), nav.name, menuRight, slots.joinToString(","),
        labels, mini.name, np.name, toolsTop, titleCenter,
        extras.entries.joinToString(";") { (k, v) -> "$k=${v.replace(Regex("[|;=\n]"), "")}" }).joinToString("|")

    companion object {
        fun decode(line: String): UiLayout? = runCatching {
            val p = line.split("|")
            UiLayout(p[0], p[1], "Made by you", NavKind.valueOf(p[2]), p[3].toBoolean(), p[4].split(",").filter { it.isNotBlank() },
                p[5].toBoolean(), MiniKind.valueOf(p[6]), NpKind.valueOf(p[7]), p[8].toBoolean(), p[9].toBoolean(),
                p.getOrNull(10).orEmpty().split(";").filter { "=" in it }.associate { it.substringBefore('=') to it.substringAfter('=') })
        }.getOrNull()
    }
}

object UiLayouts {
    const val PREF = "ui_layout"          // the layout in use
    const val MINE = "ui_layouts_mine"    // layouts you made, one encoded line each
    const val CLASSIC = "classic"

    /** Every button a bar can hold: each goes where the ☰ menu's entry for it goes. */
    val SLOTS: List<Slot> = listOf(
        Slot("player", "Now playing", Ico.PLAY, { it.tab == "player" && it.pages.isEmpty() }) { goTo(it, "player"); it.pane = "art" },
        Slot("search", "Search", Ico.SEARCH, { it.tab == "search" }) { goTo(it, "search") },
        Slot("music", "My music", Ico.NOTE, { it.tab == "music" && it.musicView == "all" }) { goTo(it, "music", "all") },
        Slot("albums", "Albums", Ico.DISC, { it.tab == "albums" }) { goTo(it, "albums") },
        Slot("artists", "Artists", Ico.PERSON, { it.tab == "artists" }) { goTo(it, "artists") },
        Slot("playlists", "Playlists", Ico.PLAYLIST, { it.tab == "music" && it.musicView == "playlists" }) { goTo(it, "music", "playlists") },
        Slot("moods", "Moods", Ico.MOOD, { it.tab == "moods" }) { goTo(it, "moods") },
        Slot("genres", "Genres", Ico.GENRE, { it.tab == "genres" }) { goTo(it, "genres") },
        Slot("favorites", "Favorites", Ico.STAR, { it.tab == "music" && it.musicView == "favorites" }) { goTo(it, "music", "favorites") },
        Slot("top", "Most played", Ico.CHART, { it.tab == "music" && it.musicView == "top" }) { goTo(it, "music", "top") },
        Slot("recent", "Recent", Ico.CLOCK, { it.tab == "music" && it.musicView == "recent" }) { goTo(it, "music", "recent") },
        Slot("queue", "Up next", Ico.QUEUE, { it.tab == "queue" }) { goTo(it, "queue") },
        Slot("dj", "DJ", Ico.DISC, { it.tab == "music" && it.musicView == "dj" }) { goTo(it, "music", "dj") },
        Slot("visualizer", "Visualizer", Ico.VIS, { it.tab == "visualizer" }) { goTo(it, "visualizer") },
        Slot("wrapped", "Wrapped", Ico.SPARKLE, { false }) { it.drawer = false; it.sheet = "wrapped" },
        // The rest of the ☰ menu, so a bar of your own can hold anything the menu has (Discovery, Listen later,
        // Downloads…): every entry of the menu is a button you can put on the bar
        Slot("pinned", "Pinned", Ico.PIN, { it.tab == "music" && it.musicView == "pinned" }) { goTo(it, "music", "pinned") },
        Slot("eps", "EPs", Ico.DISC, { it.tab == "eps" }) { goTo(it, "eps") },
        Slot("singles", "Singles", Ico.DISC, { it.tab == "singles" }) { goTo(it, "singles") },
        Slot("smartlists", "Smart playlists", Ico.SPARKLE, { it.tab == "music" && it.musicView == "smartlists" }) { goTo(it, "music", "smartlists") },
        // (Mood lists had a button here until 4.0.17: the Moods tab already opens each mood's songs. A bar that held it leaves it out.)
        Slot("folders", "Folders", Ico.FOLDER, { it.tab == "folders" }) { goTo(it, "folders") },
        Slot("unreleased", "Unreleased", Ico.FOLDER, { it.tab == "unreleased" }) { goTo(it, "unreleased") },
        Slot("downloads", "Downloads", Ico.DOWNLOAD, { it.tab == "downloads" }) { goTo(it, "downloads") },
        Slot("flac24", "24-bit FLACs", Ico.NOTE, { it.tab == "flac24" }) { goTo(it, "flac24") },
        Slot("flac32", "32-bit FLACs", Ico.NOTE, { it.tab == "flac32" }) { goTo(it, "flac32") },
        Slot("type-flac", "FLACs", Ico.NOTE, { it.tab == "type-flac" }) { goTo(it, "type-flac") },
        Slot("type-mp3", "MP3s", Ico.NOTE, { it.tab == "type-mp3" }) { goTo(it, "type-mp3") },
        Slot("type-m4a", "M4As and AACs", Ico.NOTE, { it.tab == "type-m4a" }) { goTo(it, "type-m4a") },
        Slot("type-wav", "WAVs", Ico.NOTE, { it.tab == "type-wav" }) { goTo(it, "type-wav") },
        Slot("type-ogg", "Ogg and Opus", Ico.NOTE, { it.tab == "type-ogg" }) { goTo(it, "type-ogg") },
        Slot("type-other", "Other files", Ico.NOTE, { it.tab == "type-other" }) { goTo(it, "type-other") },
        Slot("library", "Service library", Ico.CLOUD, { it.tab == "library" }) { goTo(it, "library") },
        Slot("newreleases", "New releases", Ico.SPARKLE, { it.tab == "music" && it.musicView == "newreleases" }) { goTo(it, "music", "newreleases") },
        Slot("discovery", "Discovery", Ico.COMPASS, { it.tab == "music" && it.musicView == "discovery" }) { goTo(it, "music", "discovery") },
        Slot("theirs", "Other device's songs", Ico.CLOUD, { it.tab == "music" && it.musicView == "theirs" }) { goTo(it, "music", "theirs") },
        Slot("later", "Listen later", Ico.CLOCK, { it.tab == "music" && it.musicView == "later" }) { goTo(it, "music", "later") },
        Slot("skips", "Skips", Ico.SKIP, { it.tab == "music" && it.musicView == "skips" }) { goTo(it, "music", "skips") },
        Slot("unfavorited", "Not favorited", Ico.STAR_LINE, { it.tab == "music" && it.musicView == "unfavorited" }) { goTo(it, "music", "unfavorited") },
        Slot("repeat", "On repeat", Ico.REPEAT, { it.tab == "music" && it.musicView == "repeat" }) { goTo(it, "music", "repeat") },
        Slot("repeats", "Repeats", Ico.REPEAT_ONE, { it.tab == "music" && it.musicView == "repeats" }) { goTo(it, "music", "repeats") },
        Slot("neglected", "Unplayed and neglected", Ico.CLOCK, { it.tab == "music" && it.musicView == "neglected" }) { goTo(it, "music", "neglected") },
        Slot("dupes", "Duplicates", Ico.COPY, { it.tab == "music" && it.musicView == "dupes" }) { goTo(it, "music", "dupes") },
        Slot("lyrics", "Lyrics", Ico.LYRICS, { it.tab == "lyrics" }) { goTo(it, "lyrics") },
        Slot("eq", "Equalizer", Ico.EQ, { false }) { it.drawer = false; it.sheet = "eq" },
        Slot("settings", "Settings", Ico.SETTINGS, { false }) { it.drawer = false; it.openSettings() },
        Slot("menu", "Menu", Ico.MENU, { it.drawer }) { it.openDrawer() },
    )

    /** The 45 layouts that come with the app. Each keeps every feature: what isn't on its bar is in the ☰ menu. */
    val BUILT_IN: List<UiLayout> = listOf(
        UiLayout(CLASSIC, "Classic", "The app as it comes: ☰ at the top left, a floating mini player, the big cover"),
        UiLayout("tabbar", "Tab bar", "A bar of buttons along the bottom, like most phone apps; the mini player sits just above it",
            nav = NavKind.BOTTOM, slots = listOf("player", "search", "music", "playlists", "menu"), mini = MiniKind.BAR),
        UiLayout("rail", "Side rail", "A slim rail of buttons down the left side; Now playing side by side on a wide screen",
            nav = NavKind.RAIL, slots = listOf("player", "search", "music", "albums", "artists", "playlists", "menu"),
            labels = false, np = NpKind.SPLIT),
        UiLayout("toptabs", "Top tabs", "Tabs along the top to swipe through; a flat mini player along the bottom",
            nav = NavKind.TOP, slots = listOf("player", "music", "albums", "artists", "playlists", "moods", "search", "menu"),
            mini = MiniKind.BAR, np = NpKind.CENTERED),
        UiLayout("lefty", "Right-handed", "The ☰ menu slides in from the right and its button sits on the right, under your thumb",
            menuRight = true),
        UiLayout("minimal", "Minimal", "Only what's needed: a small pill mini player and a centred Now playing",
            mini = MiniKind.PILL, np = NpKind.CENTERED, titleCenter = true),
        UiLayout("cover", "Full cover", "The cover edge to edge across the top of Now playing",
            np = NpKind.BLEED),
        UiLayout("dock", "Dock", "Icons only along the bottom, a small pill player above them",
            nav = NavKind.BOTTOM, slots = listOf("player", "search", "music", "moods", "menu"), labels = false,
            mini = MiniKind.PILL, np = NpKind.CENTERED),
        UiLayout("studio", "Studio", "A labelled rail down the side for the big screen, the cover beside the controls",
            nav = NavKind.RAIL, slots = listOf("player", "search", "music", "albums", "artists", "playlists", "top", "visualizer", "settings", "menu"),
            mini = MiniKind.BAR, np = NpKind.SPLIT),
        UiLayout("thumb", "Thumb reach", "Everything low: a bottom bar and Now playing's buttons just above it",
            nav = NavKind.BOTTOM, slots = listOf("player", "music", "search", "queue", "menu"), np = NpKind.INFO_TOP),
        UiLayout("headline", "Headline", "Top tabs and the song's name in big type beside its cover",
            nav = NavKind.TOP, slots = listOf("player", "music", "playlists", "favorites", "top", "menu"),
            np = NpKind.HEADLINE, titleCenter = true),
        UiLayout("magazine", "Magazine", "Centred titles, the mini player at the top and the song's name over the cover",
            mini = MiniKind.TOP, np = NpKind.INFO_TOP, titleCenter = true),
        UiLayout("compactrail", "Compact rail", "Icons only down the side, the song in big type",
            nav = NavKind.RAIL, slots = listOf("player", "search", "music", "playlists", "moods", "menu"), labels = false,
            mini = MiniKind.PILL, np = NpKind.HEADLINE),
        UiLayout("stage", "Stage", "Now playing's tools above the song, a bar along the bottom with the DJ and Wrapped",
            nav = NavKind.BOTTOM, slots = listOf("player", "music", "dj", "wrapped", "menu"), np = NpKind.BLEED, toolsTop = true),
        UiLayout("zen", "Zen", "The ☰ menu on the right, a centred cover and centred titles, nothing else in the way",
            menuRight = true, mini = MiniKind.PILL, np = NpKind.CENTERED, titleCenter = true, toolsTop = true),
        // 4.0.14: thirty more
        UiLayout("bottomclassic", "Library bar", "Your library along the bottom (songs, albums, artists), the classic floating player and big cover",
            nav = NavKind.BOTTOM, slots = listOf("player", "music", "albums", "artists", "menu")),
        UiLayout("albumsfirst", "Albums first", "Albums and artists lead the bottom bar; the cover edge to edge on Now playing",
            nav = NavKind.BOTTOM, slots = listOf("albums", "artists", "music", "search", "menu"), mini = MiniKind.BAR, np = NpKind.BLEED),
        UiLayout("playlistbar", "Playlist bar", "Playlists, favorites and Listen later one tap away, a small pill player",
            nav = NavKind.BOTTOM, slots = listOf("playlists", "favorites", "later", "search", "menu"), mini = MiniKind.PILL, np = NpKind.CENTERED),
        UiLayout("djbooth", "DJ booth", "The DJ, the queue and moods along the bottom; the cover beside the controls, tools on top",
            nav = NavKind.BOTTOM, slots = listOf("player", "dj", "queue", "moods", "menu"), np = NpKind.SPLIT, toolsTop = true),
        UiLayout("explorer", "Explorer", "For finding new music: Discovery, New releases, Search and Listen later along the bottom",
            nav = NavKind.BOTTOM, slots = listOf("discovery", "newreleases", "search", "later", "menu"), mini = MiniKind.BAR, np = NpKind.INFO_TOP),
        UiLayout("statsdesk", "Stats desk", "Top tabs for your numbers: Most played, Recent, Skips, Repeat and Wrapped",
            nav = NavKind.TOP, slots = listOf("top", "recent", "skips", "repeat", "wrapped", "menu"), np = NpKind.HEADLINE),
        UiLayout("librarytabs", "Library tabs", "Songs, albums, EPs, singles, artists and genres as tabs along the top",
            nav = NavKind.TOP, slots = listOf("music", "albums", "eps", "singles", "artists", "genres", "menu")),
        UiLayout("moodtabs", "Mood tabs", "Moods and smart playlists as top tabs, everything centred",
            nav = NavKind.TOP, slots = listOf("moods", "smartlists", "playlists", "menu"), mini = MiniKind.PILL, np = NpKind.CENTERED, titleCenter = true),
        UiLayout("railpill", "Rail and pill", "A short labelled rail down the side and a small pill player",
            nav = NavKind.RAIL, slots = listOf("player", "music", "playlists", "queue", "menu"), mini = MiniKind.PILL, np = NpKind.CENTERED),
        UiLayout("collector", "Collector", "A rail for the collection: albums, EPs, singles, 24-bit FLACs, downloads and folders",
            nav = NavKind.RAIL, slots = listOf("albums", "eps", "singles", "flac24", "downloads", "folders", "menu"), mini = MiniKind.BAR, np = NpKind.BLEED),
        UiLayout("statsrail", "Stats rail", "Icons down the side for Most played, Recent, Favorites, Wrapped and the Visualizer",
            nav = NavKind.RAIL, slots = listOf("top", "recent", "favorites", "wrapped", "visualizer", "menu"), labels = false, np = NpKind.HEADLINE),
        UiLayout("nomini", "No mini player", "The lists to themselves: no mini player on them at all (Now playing is in the ☰ menu)",
            mini = MiniKind.NONE),
        UiLayout("baronly", "Bar only", "A bottom bar and nothing floating over the lists",
            nav = NavKind.BOTTOM, slots = listOf("player", "search", "music", "playlists", "menu"), mini = MiniKind.NONE, np = NpKind.CENTERED),
        UiLayout("playerontop", "Player on top", "The mini player along the top of every list, the cover edge to edge",
            mini = MiniKind.TOP, np = NpKind.BLEED),
        UiLayout("topdeck", "Top deck", "Tabs and the mini player both at the top, the bottom of the screen left clear",
            nav = NavKind.TOP, slots = listOf("player", "search", "music", "playlists", "queue", "menu"), mini = MiniKind.TOP, np = NpKind.SPLIT),
        UiLayout("rightpill", "Right pill", "The ☰ menu on the right, a pill player, the song's name above its cover",
            menuRight = true, mini = MiniKind.PILL, np = NpKind.INFO_TOP),
        UiLayout("rightbar", "Right bar", "The ☰ menu on the right, a flat player bar, the song in big type",
            menuRight = true, mini = MiniKind.BAR, np = NpKind.HEADLINE, titleCenter = true),
        UiLayout("bigtype", "Big type", "The song's name in big type on Now playing, its tools above it",
            np = NpKind.HEADLINE, toolsTop = true),
        UiLayout("songfirst", "Song first", "The song's name first and the cover under it, a flat player bar, centred titles",
            mini = MiniKind.BAR, np = NpKind.INFO_TOP, titleCenter = true),
        UiLayout("sidebyside", "Side by side", "Just the ☰ menu, with the cover beside the controls on Now playing",
            mini = MiniKind.BAR, np = NpKind.SPLIT),
        UiLayout("centrestage", "Centre stage", "Icons for the queue, lyrics and visualizer along the bottom, the cover centred, nothing over the lists",
            nav = NavKind.BOTTOM, slots = listOf("player", "queue", "lyrics", "visualizer", "menu"), labels = false, mini = MiniKind.NONE, np = NpKind.CENTERED, toolsTop = true),
        UiLayout("singalong", "Sing along", "Lyrics and the queue on the bottom bar, the song's name over the cover",
            nav = NavKind.BOTTOM, slots = listOf("player", "lyrics", "queue", "search", "menu"), np = NpKind.INFO_TOP),
        UiLayout("sounddesk", "Sound desk", "A rail with the Equalizer, the Visualizer, the queue and Settings to hand",
            nav = NavKind.RAIL, slots = listOf("player", "eq", "visualizer", "queue", "settings", "menu"), mini = MiniKind.PILL, np = NpKind.SPLIT),
        UiLayout("filecabinet", "File cabinet", "Folders, Unreleased and Downloads along the bottom: your files first",
            nav = NavKind.BOTTOM, slots = listOf("folders", "unreleased", "downloads", "search", "menu"), mini = MiniKind.BAR),
        UiLayout("searchfirst", "Search first", "Search leads the bottom bar; a pill player and the cover edge to edge",
            nav = NavKind.BOTTOM, slots = listOf("search", "player", "music", "menu"), mini = MiniKind.PILL, np = NpKind.BLEED),
        UiLayout("twobuttons", "Two buttons", "Only Now playing and the menu on the bar: as little as there can be",
            nav = NavKind.BOTTOM, slots = listOf("player", "menu"), mini = MiniKind.BAR, np = NpKind.CENTERED, titleCenter = true),
        UiLayout("everything", "Everything bar", "A long row of icons along the top for nearly every screen, to swipe through",
            nav = NavKind.TOP, slots = listOf("player", "search", "music", "albums", "artists", "playlists", "moods", "genres", "favorites", "top", "queue", "menu"),
            labels = false, mini = MiniKind.BAR),
        UiLayout("pinnedbar", "Pinned", "Pinned, Favorites and Recent along the bottom, the song in big type",
            nav = NavKind.BOTTOM, slots = listOf("pinned", "favorites", "recent", "search", "menu"), np = NpKind.HEADLINE),
        UiLayout("nightstand", "Nightstand", "For a phone on its stand: nothing over the lists, the menu on the right, a centred cover with its tools on top",
            menuRight = true, mini = MiniKind.NONE, np = NpKind.CENTERED, toolsTop = true, titleCenter = true),
        UiLayout("widescreen", "Widescreen", "For a big window: a long labelled rail, the player along the top, the cover beside the controls",
            nav = NavKind.RAIL, slots = listOf("player", "search", "music", "albums", "artists", "genres", "moods", "playlists", "queue", "menu"),
            mini = MiniKind.TOP, np = NpKind.SPLIT, toolsTop = true),
    )

    /** Layouts you made (Personalization › Make your own layout), after the built-in ones. */
    fun mine(): List<UiLayout> = Prefs.text(MINE).lines().mapNotNull { UiLayout.decode(it) }
    fun all(): List<UiLayout> = BUILT_IN + mine()
    val MENU: List<Pair<String, String>> get() = all().map { it.key to (if (it.key.startsWith("mine-")) "${it.name} (yours)" else it.name) }

    /** The layout in use (Classic for one that's gone). */
    val current: UiLayout get() = Prefs.text(PREF, CLASSIC).let { k -> all().firstOrNull { it.key == k } } ?: BUILT_IN.first()
    fun choose(key: String) = Prefs.setText(PREF, key)

    /** Saves one of yours (a new one gets its own key) and picks it. */
    fun save(l: UiLayout): UiLayout {
        val keyed = if (l.key.startsWith("mine-")) l else l.copy(key = "mine-" + System.currentTimeMillis().toString(36))
        val rest = mine().filter { it.key != keyed.key }
        Prefs.setText(MINE, (rest + keyed).joinToString("\n") { it.encode() })
        choose(keyed.key)
        return keyed
    }
    fun delete(key: String) {
        MadeSync.forget(key)
        Prefs.setText(MINE, mine().filter { it.key != key }.joinToString("\n") { it.encode() })
        if (Prefs.text(PREF) == key) choose(CLASSIC)
    }
}

// ───────────────────────────── The bars ─────────────────────────────

/** A bar of buttons along the bottom (labels under the icons, or icons only). */
@Composable
fun BottomNav(ui: Ui, l: UiLayout) {
    val float = l.x("barStyle") == "float"
    val fill = barFill(l, if (C.isLight) 0.96f else 0.9f)
    val row = if (float) Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)
        .shadow(10.dp, R.big).clip(R.big).background(fill).padding(horizontal = 4.dp, vertical = 2.dp)
    else Modifier.fillMaxWidth().background(fill).navigationBarsPadding()
    Row(row.padding(horizontal = 4.dp, vertical = if (l.labels) 4.dp else 6.dp), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically) {
        l.navSlots.forEach { s -> NavButton(ui, s, l, Modifier.weight(1f)) }
    }
}

/** The bar's or rail's background: solid, see-through or none (Make your own layout › More). */
private fun barFill(l: UiLayout, solid: Float): androidx.compose.ui.graphics.Color = C.light.copy(alpha = when (l.x("barFill")) {
    "clear" -> solid * 0.45f; "none" -> 0f; else -> solid })

/** A rail down the side: icons (with their names when labels is on). */
@Composable
fun SideRail(ui: Ui, l: UiLayout) {
    Column(Modifier.fillMaxHeight().width(if (l.labels) 112.dp else 64.dp).background(barFill(l, if (C.isLight) 0.96f else 0.7f))
        .statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        l.navSlots.forEach { s -> NavButton(ui, s, l, Modifier.fillMaxWidth()) }
    }
}

/** Tabs along the top, swiped sideways when they don't all fit. */
@Composable
fun TopTabs(ui: Ui, l: UiLayout) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        l.navSlots.forEach { s ->
            val on = s.isOn(ui)
            if (s.key == "menu" || !l.labels) IconButton(s.ico, if (on) C.accent else C.fg, 22.dp, 8.dp, desc = s.label) { s.open(ui) }
            else Text(s.label, style = T.tab.copy(color = if (on) C.accent else C.muted, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp, shadow = if (on) C.shadow else null),
                modifier = Modifier.clip(R.pill).background(if (on) C.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { s.open(ui) }.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun NavButton(ui: Ui, s: Slot, l: UiLayout, modifier: Modifier) {
    val on = s.isOn(ui)
    val active = l.x("active")
    val labels = l.labels && (l.x("labelsOn") != "active" || on)
    val icon = when (l.x("iconSize")) { "small" -> 18.dp; "large" -> 27.dp; else -> 22.dp }
    val clear = androidx.compose.ui.graphics.Color.Transparent
    Column(modifier.clip(R.card).clickable { s.open(ui) }.semantics { contentDescription = s.label }
        .padding(vertical = if (l.labels) 6.dp else 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (active == "line") Box(Modifier.width(22.dp).height(3.dp).clip(R.pill).background(if (on) C.accent else clear))
        Box(Modifier.clip(R.pill).background(if (on && active == "pill") C.accent.copy(alpha = 0.16f) else clear)
            .padding(horizontal = 14.dp, vertical = 4.dp)) {
            Icon(s.ico, if (on) C.accent else C.fg.copy(alpha = 0.8f), Themes.icon(icon))
        }
        if (active == "dot") Box(Modifier.size(5.dp).clip(R.pill).background(if (on) C.accent else clear))
        if (labels) Text(s.label, style = T.time.copy(color = if (on) C.accent else C.muted, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}


/** Make your own layout › More: each a choice with its options (the first is the standard). Read with UiLayout.x. */
object LayoutExtras {
    class Extra(val key: String, val group: String, val name: String, val desc: String, val options: List<Pair<String, String>>)
    val ALL = listOf(
        Extra("railSide", "Getting around", "Rail's side", "Which side the rail of buttons is on",
            listOf("left" to "Left", "right" to "Right")),
        Extra("barStyle", "Getting around", "Bar's shape", "The bar along the bottom: across the screen, or a floating rounded bar",
            listOf("full" to "Across the screen", "float" to "Floating")),
        Extra("barFill", "Getting around", "Bar's background", "Behind the bar or rail of buttons",
            listOf("solid" to "Solid", "clear" to "See-through", "none" to "None")),
        Extra("active", "Getting around", "The screen you're on", "How its button shows it",
            listOf("pill" to "A pill behind it", "color" to "Just its colour", "dot" to "A dot under it", "line" to "A line over it")),
        Extra("labelsOn", "Getting around", "Names", "With names under the icons: on every button, or only on the one you're on",
            listOf("all" to "All", "active" to "Only the one you're on")),
        Extra("iconSize", "Getting around", "Icon size", "The bar's and rail's icons",
            listOf("normal" to "Normal", "small" to "Small", "large" to "Large")),
        Extra("titleSize", "Pages", "Page titles", "Each screen's name at the top",
            listOf("normal" to "Normal", "big" to "Big", "small" to "Small")),
        Extra("artShape", "Now playing", "Cover's shape", "The big cover on Now playing",
            listOf("rounded" to "Rounded", "square" to "Square", "soft" to "Very round", "circle" to "Circle (like a record)")),
        Extra("artSize", "Now playing", "Cover's size", "How much room the big cover takes",
            listOf("100" to "Full", "90" to "90%", "80" to "80%", "70" to "70%", "60" to "60%")),
        Extra("artShadow", "Now playing", "Cover's shadow", "The shadow under the big cover",
            listOf("on" to "On", "off" to "Off")),
        Extra("seek", "Now playing", "Seek bar", "How thick the seek bar is",
            listOf("normal" to "Normal", "thin" to "Thin", "thick" to "Thick")),
        Extra("times", "Now playing", "Times", "The time played and left under the seek bar",
            listOf("on" to "Shown", "off" to "Hidden")),
        Extra("swipe", "Now playing", "Swipe the cover", "Swiping the cover sideways skips songs",
            listOf("on" to "On", "off" to "Off")),
    )
}
