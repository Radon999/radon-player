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
