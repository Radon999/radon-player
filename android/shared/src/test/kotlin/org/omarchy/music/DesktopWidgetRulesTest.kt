package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The PC's widgets (their places kept) and its one-click shortcuts (the playlist a command means, the files made). */
class DesktopWidgetRulesTest {
    private val R = DesktopWidgetRules

    @Test fun aPlaceComesBackAsItWasKept() {
        val p = DesktopWidgetRules.Place(open = true, x = -1200, y = 40, w = 330, h = 410, onTop = true, choice = "pl;id=7 choice=x", shuffled = true)
        assertEquals(p, R.decode("playlist", R.encode(p)))
        val unmoved = DesktopWidgetRules.Place(open = true, w = 300, h = 200)
        assertEquals("a widget never moved stays unplaced", unmoved, R.decode("dj", R.encode(unmoved)))
        val hypr = DesktopWidgetRules.Place(open = true, x = 3500, y = 60, w = 300, h = 200, hypr = true)
        assertEquals("a place Hyprland gave is kept as Hyprland's", hypr, R.decode("dj", R.encode(hypr)))
    }

    @Test fun nothingKeptOrGarbledTakesTheFirstTimePlace() {
        assertEquals(R.start("lyrics"), R.decode("lyrics", ""))
        val g = R.decode("queue", "open=1;w=abc;h=5;top=")
        assertTrue(g.open)
        assertEquals("a size that isn't a number: the first-time one", R.startSize("queue").first, g.w)
        assertEquals("never smaller than the buttons need", R.MIN_H, g.h)
        assertFalse(g.onTop)
        assertFalse("not above other windows unless switched on", R.decode("dj", "open=1").onTop)
        assertFalse(R.start("shortcuts").onTop)
    }

    @Test fun aCommandFindsThePlaylistAndWhetherShuffled() {
        val names = listOf("Road trip", "Rock classics", "Mix shuffle", "Chill")
        assertEquals("Road trip" to false, R.playlistAsked("Road trip", names))
        assertEquals("Road trip" to true, R.playlistAsked("road trip shuffle", names))
        assertEquals("Chill" to true, R.playlistAsked("Chill shuffled", names))
        assertEquals("a last word that is the playlist's own", "Mix shuffle" to false, R.playlistAsked("mix shuffle", names))
        assertEquals("the start of a name", "Rock classics" to false, R.playlistAsked("rock", names))
        assertEquals("the same name before one it starts", "Chill", R.named("chill", listOf("Chill out", "Chill")))
        assertNull(R.playlistAsked("Jazz", names))
        assertNull(R.playlistAsked("  ", names))
    }

    @Test fun theLinuxShortcutStartsTheAppWithItsCommand() {
        val s = R.playlistShortcut("Road trip", shuffled = true)
        val text = R.desktopEntry("/opt/radon-player/bin/Radon Player", s, "/home/me/.local/share/radon-player/shortcut-icons/x.png")
        assertTrue(text.startsWith("[Desktop Entry]\nType=Application\n"))
        assertTrue(text, text.contains("\nExec=\"/opt/radon-player/bin/Radon Player\" play playlist \"Road trip\" shuffle\n"))
        assertTrue("named plainly", text.contains("\nName=Road trip (shuffled)\n"))
        assertTrue(text.contains("\nIcon=/home/me/.local/share/radon-player/shortcut-icons/x.png\n"))
        assertTrue(text.contains("\n${R.MARK}\n"))
        assertTrue(R.linuxFile(s).let { it.startsWith("radon-player-shortcut-play-playlist-road-trip-shuffle-") && it.endsWith(".desktop") })
        assertEquals("Road trip", R.playlistShortcut("Road trip", shuffled = false).name)
    }

    @Test fun twoShortcutsNeverShareAFile() {
        val a = R.playlistShortcut("Road trip", false); val b = R.playlistShortcut("Road-trip", false)
        val pinnedList = R.playlistShortcut("Pinned", false); val pinned = R.SHORTCUTS.first { it.name == "Pinned" }
        val all = listOf(a, b, pinnedList, pinned) + R.SHORTCUTS
        assertEquals("every command its own file", all.distinctBy { it.key }.size, all.distinctBy { R.linuxFile(it) }.size)
        assertEquals("the same command: the same file each time", R.fileBase(a), R.fileBase(R.playlistShortcut("Road trip", false)))
        assertEquals("Windows: a name taken gets (2)", listOf("Pinned.lnk", "Pinned (2).lnk"), R.windowsFiles(listOf(pinnedList, pinned)))
    }

    @Test fun eachShortcutGetsAPictureOfWhatItDoes() {
        assertEquals("dj-mine", R.iconKind(listOf("dj", "mine")))
        assertEquals("dj-off", R.iconKind(listOf("dj", "off")))
        assertEquals("dj", R.iconKind(listOf("dj", "on")))
        assertEquals("playlist-shuffle", R.iconKind(listOf("play", "playlist", "Road trip", "shuffle")))
        assertEquals("a playlist named Shuffle, in order", "playlist", R.iconKind(listOf("play", "playlist", "Shuffle")))
        assertEquals("wrapped-all", R.iconKind(listOf("show", "wrapped", "all")))
        assertEquals("releases", R.iconKind(listOf("show", "new releases")))
        assertEquals("next", R.iconKind(listOf("skip")))
        assertEquals("app", R.iconKind(listOf("tag", "all")))
        assertTrue("every offered one has its own kind", R.SHORTCUTS.none { R.iconKind(it.args) == "app" })
    }

    @Test fun theShortcutsMadeAreKeptAndMergedByCommand() {
        val old = listOf(R.SHORTCUTS[0], R.playlistShortcut("Road trip", false))
        val again = listOf(DesktopWidgetRules.Shortcut("Road trip, renamed", listOf("play", "playlist", "Road trip")), R.SHORTCUTS[1])
        val m = R.merged(old, again)
        assertEquals(listOf("Play or pause", "Road trip, renamed", "Next song"), m.map { it.name })
        assertEquals("kept and read back as they were", m, R.decodeList(R.encodeList(m)))
        assertEquals("a tab in a name doesn't split it", listOf(DesktopWidgetRules.Shortcut("A B", listOf("play", "playlist", "A B"))),
            R.decodeList(R.encodeList(listOf(DesktopWidgetRules.Shortcut("A\tB", listOf("play", "playlist", "A\tB"))))))
        assertTrue(R.decodeList("").isEmpty())
    }

    @Test fun desktopsWithNoDesktopIconsAreKnown() {
        assertFalse(R.desktopIconsWork(mapOf("HYPRLAND_INSTANCE_SIGNATURE" to "abc_123", "XDG_CURRENT_DESKTOP" to "Hyprland")))
        assertFalse(R.desktopIconsWork(mapOf("SWAYSOCK" to "/run/user/1000/sway.sock")))
        assertFalse(R.desktopIconsWork(mapOf("XDG_CURRENT_DESKTOP" to "niri")))
        assertTrue(R.desktopIconsWork(mapOf("XDG_CURRENT_DESKTOP" to "ubuntu:GNOME")))
        assertTrue(R.desktopIconsWork(mapOf("XDG_CURRENT_DESKTOP" to "KDE")))
    }

    @Test fun anIcoHoldsThePngWhole() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
        val ico = R.icoOf(png, 256)
        assertEquals(listOf(0, 0, 1, 0, 1, 0), ico.take(6).map { it.toInt() and 0xFF })
        assertEquals("256 is written as 0", 0, ico[6].toInt())
        assertEquals("its length", png.size, (ico[14].toInt() and 0xFF) or ((ico[15].toInt() and 0xFF) shl 8))
        assertEquals("where it starts", 22, ico[18].toInt())
        assertEquals(png.toList(), ico.drop(22))
    }

    // What this PC's Hyprland says (two monitors: DP-1 scaled 1.6 at x 1920, HDMI-A-1 scaled 2 at x 0, y 270)
    private val monitorsJson = """[{"id":1,"name":"DP-1","x":1920,"y":0,"width":3840,"height":2160,"scale":1.6,"reserved":[0,22,0,40],"focused":true},
        {"id":0,"name":"HDMI-A-1","x":0,"y":270,"width":3840,"height":2160,"scale":2,"reserved":[0,0,0,0],"focused":false}]"""
    private val clientsJson = """[{"address":"0x560d687df270","pid":341605,"title":"Radon Player · DJ","at":[2618,343],"size":[1100,760],"monitor":1,"floating":true,"pinned":false},
        {"address":"0x560d68926360","pid":4242,"title":"Radon Player · DJ","at":[2594,319],"size":[1100,760],"monitor":1,"floating":false,"pinned":false}]"""

    @Test fun hyprlandsWindowsAndMonitorsAreReadAndOursFound() {
        val H = DesktopWidgetRules.Hypr
        val mons = H.monitors(monitorsJson)
        assertEquals(listOf(1, 0), mons.map { it.id })
        assertEquals("DP-1 is 2400 wide in Hyprland's layout", 2400, mons[0].logicalW)
        assertEquals(listOf(0, 22, 0, 40), mons[0].reserved)
        val c = H.find(H.clients(clientsJson), 4242, "Radon Player · DJ")!!
        assertEquals("ours, not the installed app's of the same title", "0x560d68926360", c.address)
        assertEquals(1100, c.w); assertFalse(c.floating)
        assertTrue(H.clients("not json").isEmpty())
    }

    @Test fun aWidgetIsSizedAndPlacedInHyprlandsPixels() {
        val H = DesktopWidgetRules.Hypr
        val dp1 = H.monitors(monitorsJson)[0]
        // Java draws at 2 here: 320 dp are 640 real pixels, 400 of Hyprland's on DP-1 (1.6), 320 on HDMI-A-1 (2)
        assertEquals(400, H.logical(320, 2.0, 1.6)); assertEquals(320, H.logical(320, 2.0, 2.0))
        assertEquals(320, H.dp(400, 2.0, 1.6))
        assertEquals("top right of DP-1, clear of its bar", 1920 + 2400 - 400 - 24 to 22 + 24, H.firstPlace(dp1, 400, 300, 0))
        assertTrue(H.onSomeMonitor(2600, 300, H.monitors(monitorsJson)))
        assertFalse("a monitor unplugged since", H.onSomeMonitor(9000, 300, H.monitors(monitorsJson)))
        val c = H.clients(clientsJson)[1]
        assertEquals(listOf("setfloating address:0x560d68926360", "resizewindowpixel exact 400 300,address:0x560d68926360",
            "movewindowpixel exact 2500 100,address:0x560d68926360"), H.dispatches(c, 2500, 100, 400, 300, onTop = false))
        assertEquals("pin address:0x560d68926360", H.dispatches(c, 0, 0, 1, 1, onTop = true).last())
        assertEquals("hl.dsp.window.resize({ x = 400, y = 300, relative = false, window = \"address:0x560d68926360\" })",
            H.lua("resizewindowpixel exact 400 300,address:0x560d68926360", false))
        assertEquals("hl.dsp.window.pin({ action = \"off\", window = \"address:0x1\" })", H.lua("pin address:0x1", pinned = true))
    }

    @Test fun theTaskbarIsOmarchysDockOrGnomesDashOrNone() {
        val T = DesktopWidgetRules.Taskbar
        assertEquals(T.OMARCHY, T.kind(hasOmarchyDock = true, gnomeFavourites = true))
        assertEquals(T.GNOME, T.kind(hasOmarchyDock = false, gnomeFavourites = true))
        assertNull(T.kind(hasOmarchyDock = false, gnomeFavourites = false))
    }

    @Test fun ourPinGoesAtTheEndAndEveryoneElsesStayInOrder() {
        val T = DesktopWidgetRules.Taskbar
        // What omarchy-dock pinned says on this PC
        val owners = T.parseOmarchy("ompchy\ndev.desktopdesign.media.music\n")
        assertEquals(listOf("ompchy", "dev.desktopdesign.media.music"), owners)
        val ours = T.id(R.linuxFile(R.SHORTCUTS[0]))
        val pinned = T.withPin(owners, ours)
        assertEquals("at the end", owners + ours, pinned)
        assertEquals("pinned twice: still once, in its place", pinned, T.withPin(pinned, ours))
        assertEquals("taken off: the others exactly as before", owners, T.withoutPin(pinned, ours))
        assertEquals("one not there: nothing changes", owners, T.withoutPin(owners, ours))
        assertEquals("made again under a new name: in the old one's place",
            listOf("ompchy", "new", "dev.desktopdesign.media.music"), T.replaced(listOf("ompchy", "old", "dev.desktopdesign.media.music"), "old", "new"))
        assertEquals(owners, T.replaced(owners, "old", "new"))
    }

    @Test fun gnomesFavouritesAreReadAndWrittenWhole() {
        val T = DesktopWidgetRules.Taskbar
        assertEquals(listOf("org.gnome.Nautilus", "firefox", "it's"), T.parseGnome("['org.gnome.Nautilus.desktop', 'firefox.desktop', 'it\\'s.desktop']"))
        assertTrue(T.parseGnome("@as []").isEmpty())
        val value = T.gnomeValue(listOf("org.gnome.Nautilus", "it's", "radon-player-shortcut-skip-1a2b3c"))
        assertEquals("['org.gnome.Nautilus.desktop', 'it\\'s.desktop', 'radon-player-shortcut-skip-1a2b3c.desktop']", value)
        assertEquals("read back as written", listOf("org.gnome.Nautilus", "it's", "radon-player-shortcut-skip-1a2b3c"), T.parseGnome(value))
    }

    @Test fun theAppsOwnLauncherIsFoundNotAShortcut() {
        val T = DesktopWidgetRules.Taskbar
        val exe = "/home/me/.local/opt/radon-player/bin/Radon Player"
        val entries = listOf(
            R.linuxFile(R.SHORTCUTS[0]) to R.desktopEntry(exe, R.SHORTCUTS[0], null),
            "other.desktop" to "[Desktop Entry]\nName=Other\nExec=/usr/bin/other\n",
            "radon-player.desktop" to "[Desktop Entry]\nName=Radon Player\nExec=\"$exe\"\nIcon=x.png\n")
        assertEquals("radon-player", T.appEntry(entries, exe))
        assertNull("not installed here", T.appEntry(entries.take(2), exe))
    }

    @Test fun anExecLineIsReadBackAsTheDesktopReadsIt() {
        val exe = "/home/me/.local/opt/radon-player/bin/Radon Player"
        for (name in listOf("Road trip", "Rock & Roll's \"best\" \$5 100% C:\\x", "a`b", "")) {
            val s = R.playlistShortcut(name, shuffled = true)
            val line = R.desktopEntry(exe, s, null).lines().first { it.startsWith("Exec=") }
            assertEquals(name, listOf(exe) + s.args, R.execWords(line))
        }
        assertEquals(listOf(exe, "dj", "mine"), R.execWords("Exec=\"$exe\" dj mine"))
        assertNull(R.execWords("Name=x"))
    }

    @Test fun aShortcutMadeBeforePlainNamesGetsItsPlainName() {
        assertEquals("DJ with only my songs", R.renamed("Radon Player: DJ with only my songs", listOf("dj", "mine"))?.name)
        assertEquals("Road trip (shuffled)", R.renamed("Radon Player: Shuffle Road trip", listOf("play", "playlist", "Road trip", "shuffle"))?.name)
        assertEquals("Something", R.renamed("Radon Player: Something", listOf("tag"))?.name)
        assertNull("one made now is left as it is", R.renamed("Lyrics", listOf("show", "lyrics")))
    }

    @Test fun oddSignsReachTheAppAsTheyAre() {
        // In quotes: " ` $ \ behind a backslash; then every backslash doubled for the file, and % doubled
        assertEquals("\"Rock & Roll's \\\\\"best\\\\\" \\\\\$5 100%%\"", R.execArg("Rock & Roll's \"best\" \$5 100%"))
        assertEquals("\"a\\\\\\\\b\"", R.execArg("a\\b"))
        assertEquals("skip", R.execArg("skip"))
        assertEquals("\"\"", R.execArg(""))
    }

    @Test fun theNewestReleasesComeFirstEachOnce() {
        data class Rel(val key: String, val date: String)
        val got = R.newest(listOf(Rel("a", "2026-10-02"), Rel("b", ""), Rel("c", "2026-11-20"), Rel("a", "2026-10-02"), Rel("d", "2025-12-31")),
            { it.key }, { it.date }, 3)
        assertEquals("newest first, the one told twice once, none past three", listOf("c", "a", "d"), got.map { it.key })
        assertEquals("one with no date after the dated ones", "b", R.newest(listOf(Rel("b", ""), Rel("a", "2020-01-01")), { it.key }, { it.date }, 5).last().key)
    }

    @Test fun whenAReleaseCameOutIsSaidAsAPersonWould() {
        val today = java.time.LocalDate.of(2026, 11, 25)
        assertEquals("Today", R.whenOut("2026-11-25", today))
        assertEquals("Yesterday", R.whenOut("2026-11-24", today))
        assertEquals("6 days ago", R.whenOut("2026-11-19", today))
        assertEquals("Last week", R.whenOut("2026-11-15", today))
        assertEquals("3 weeks ago", R.whenOut("2026-11-01", today))
        assertEquals("Sep 2", R.whenOut("2026-09-02", today))
        assertEquals("another year's has its year", "Dec 31, 2025", R.whenOut("2025-12-31", today))
        assertEquals("one still to come", "Out Dec 4", R.whenOut("2026-12-04", today))
        assertEquals("not a date: as it is", "soon", R.whenOut("soon", today))
    }

    @Test fun theTwoNewWidgetsAreListedOnce() {
        val keys = R.KINDS.map { it.first }
        assertEquals(keys.distinct(), keys)
        assertTrue("releases" in keys && "visualizer" in keys)
        assertEquals("the visualizer starts wide", true, R.startSize("visualizer").let { (w, h) -> w > h })
    }

    @Test fun theDesktopFolderIsTheOneTheUserNamed() {
        assertEquals("/home/me/Desktop", R.desktopDir(null, "/home/me"))
        assertEquals("/home/me/Bureau", R.desktopDir("# made by xdg\nXDG_DESKTOP_DIR=\"\$HOME/Bureau\"\n", "/home/me"))
        assertEquals("/data/desk", R.desktopDir("XDG_DESKTOP_DIR=\"/data/desk\"", "/home/me"))
        assertEquals("no desktop folder of its own: ~/Desktop", "/home/me/Desktop", R.desktopDir("XDG_DESKTOP_DIR=\"\$HOME/\"", "/home/me"))
    }

    @Test fun theWindowsShortcutKeepsEachWordWhole() {
        assertEquals("play playlist \"Road trip\" shuffle", R.windowsArgs(listOf("play", "playlist", "Road trip", "shuffle")))
        assertEquals("\"say \\\"hi\\\"\"", R.windowsArgs(listOf("say \"hi\"")))
        assertEquals("a folder ending in a backslash", "\"C:\\My dir\\\\\"", R.windowsArgs(listOf("C:\\My dir\\")))
        val script = R.windowsScript("C:\\Program Files\\Radon Player\\Radon Player.exe", listOf(R.playlistShortcut("Mum's songs", false)),
            listOf("[Environment]::GetFolderPath('Desktop')"), mapOf(R.playlistShortcut("Mum's songs", false).key to "C:\\Users\\me\\x.ico"))
        assertTrue(script, script.contains("\$s.Arguments = 'play playlist \"Mum''s songs\"'"))
        assertTrue(script.contains("\$s.TargetPath = 'C:\\Program Files\\Radon Player\\Radon Player.exe'"))
        assertTrue(script.contains("\$s.WorkingDirectory = 'C:\\Program Files\\Radon Player'"))
        assertTrue("named plainly", script.contains("\$p = Join-Path \$d 'Mum''s songs.lnk'"))
        assertTrue("its own picture", script.contains("\$s.IconLocation = 'C:\\Users\\me\\x.ico,0'"))
        assertTrue("someone else's file of that name is left alone", script.contains("'Mum''s songs (Radon Player).lnk'"))
        assertEquals("What now.lnk", R.windowsFiles(listOf(DesktopWidgetRules.Shortcut("What now?", listOf("skip")))).single())
        assertTrue(R.windowsRemoveScript(listOf("x")).contains("-like 'Radon Player shortcut*'"))
    }
}
