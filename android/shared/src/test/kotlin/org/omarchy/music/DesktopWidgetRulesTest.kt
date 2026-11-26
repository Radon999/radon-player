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
        val p = DesktopWidgetRules.Place(open = true, x = -1200, y = 40, w = 330, h = 410, onTop = false, choice = "pl;id=7 choice=x", shuffled = true)
        assertEquals(p, R.decode("playlist", R.encode(p)))
        val unmoved = DesktopWidgetRules.Place(open = true, w = 300, h = 200)
        assertEquals("a widget never moved stays unplaced", unmoved, R.decode("dj", R.encode(unmoved)))
    }

    @Test fun nothingKeptOrGarbledTakesTheFirstTimePlace() {
        assertEquals(R.start("lyrics"), R.decode("lyrics", ""))
        val g = R.decode("queue", "open=1;w=abc;h=5;top=")
        assertTrue(g.open)
        assertEquals("a size that isn't a number: the first-time one", R.startSize("queue").first, g.w)
        assertEquals("never smaller than the buttons need", R.MIN_H, g.h)
        assertFalse(g.onTop)
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
        val text = R.desktopEntry("/opt/radon-player/bin/Radon Player", s, "/opt/radon-player/lib/Radon Player.png")
        assertTrue(text.startsWith("[Desktop Entry]\nType=Application\n"))
        assertTrue(text, text.contains("\nExec=\"/opt/radon-player/bin/Radon Player\" play playlist \"Road trip\" shuffle\n"))
        assertTrue(text.contains("\nName=Radon Player: Shuffle Road trip\n"))
        assertTrue(text.contains("\n${R.MARK}\n"))
        assertEquals("radon-player-shortcut-shuffle-road-trip.desktop", R.linuxFile(s.name))
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
        assertEquals("Radon Player - Play AC-DC.lnk", R.windowsFile("Play AC/DC".replace("/", "-")))
        assertEquals("Radon Player - What now.lnk", R.windowsFile("What now?"))
        val script = R.windowsScript("C:\\Program Files\\Radon Player\\Radon Player.exe", listOf(R.playlistShortcut("Mum's songs", false)),
            listOf("[Environment]::GetFolderPath('Desktop')"))
        assertTrue(script, script.contains("\$s.Arguments = 'play playlist \"Mum''s songs\"'"))
        assertTrue(script.contains("\$s.TargetPath = 'C:\\Program Files\\Radon Player\\Radon Player.exe'"))
        assertTrue(script.contains("\$s.WorkingDirectory = 'C:\\Program Files\\Radon Player'"))
        assertTrue(script.contains("CreateShortcut((Join-Path \$d 'Radon Player - Play Mum''s songs.lnk'))"))
    }
}
