package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** One-click shortcuts on the PC: the Linux files Make desktop shortcuts writes (and takes away again), and the
 *  commands they and the desktop widgets send, done by the app. The files and the playlist are the test's own. */
class DesktopShortcutsTest {
    @Test fun aLinuxShortcutStartsTheAppWithItsCommandAndOnlyOursAreTakenAway() {
        val dir = java.nio.file.Files.createTempDirectory("radon-shortcuts").toFile()
        try {
            val menu = File(dir, "applications"); val desk = File(dir, "Desktop")
            val notOurs = File(desk.apply { mkdirs() }, "${DesktopWidgetRules.LINUX_PREFIX}notes.desktop").apply { writeText("[Desktop Entry]\nName=Mine\n") }
            val exe = "/opt/radon-player/bin/Radon Player"
            val dj = DesktopWidgetRules.SHORTCUTS.first { it.args == listOf("dj", "new") }
            val releases = DesktopWidgetRules.SHORTCUTS.first { it.name == "New releases" }
            val road = DesktopWidgetRules.playlistShortcut("Road trip", shuffled = true)
            // One made before this version, under its old name, for the same command: replaced, not left beside the new one
            val old = File(menu.apply { mkdirs() }, "${DesktopWidgetRules.LINUX_PREFIX}dj-with-new-songs-too.desktop")
                .apply { writeText("[Desktop Entry]\nName=Radon Player: DJ with new songs too\nExec=\"$exe\" dj new\n${DesktopWidgetRules.MARK}\n") }
            val list = listOf(dj, road, releases)
            assertEquals("each shortcut in each folder", 6, Shortcuts.writeLinux(exe, list, listOf(menu, desk)) { "/icons/${it.name}.png" })
            assertFalse("the old one for the same command went", old.exists())
            val roadFile = File(desk, DesktopWidgetRules.linuxFile(road))
            assertTrue("a desktop file is a program to run", roadFile.canExecute())
            val roadText = roadFile.readText().lines()
            assertTrue(roadText.toString(), "Exec=\"$exe\" play playlist \"Road trip\" shuffle" in roadText)
            assertTrue("named plainly, with its own picture", "Name=Road trip (shuffled)" in roadText && "Icon=/icons/Road trip (shuffled).png" in roadText)
            val djText = File(menu, DesktopWidgetRules.linuxFile(dj)).readText().lines()
            assertTrue(djText.toString(), "Exec=\"$exe\" dj new" in djText && "Name=DJ with new songs too" in djText)
            val nrText = File(menu, DesktopWidgetRules.linuxFile(releases)).readText().lines()
            assertTrue(nrText.toString(), "Exec=\"$exe\" show \"new releases\"" in nrText)
            assertEquals(6, Shortcuts.removeLinux(listOf(menu, desk)))
            assertFalse(roadFile.exists())
            assertTrue("a file with our name but not made here stays", notOurs.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun launchersMadeBeforePlainNamesAreMadeAgainAsTheyAreNow() {
        Harness.ctx
        val dir = java.nio.file.Files.createTempDirectory("radon-old-shortcuts").toFile()
        try {
            val exe = "/home/me/.local/opt/radon-player/bin/Radon Player"
            // As 5.2.1 wrote them (this PC's own, copied)
            val old = File(dir, "${DesktopWidgetRules.LINUX_PREFIX}dj-with-only-my-songs.desktop").apply { writeText("[Desktop Entry]\nType=Application\n" +
                "Name=Radon Player: DJ with only my songs\nComment=One click: Radon Player does this\nExec=\"$exe\" dj mine\n" +
                "Icon=/home/me/.local/opt/radon-player/lib/Radon_Player.png\nTerminal=false\nCategories=AudioVideo;Audio;Player;\n${DesktopWidgetRules.MARK}\n") }
            assertEquals(1, Shortcuts.upgrade(dir))
            assertFalse("the old file went", old.exists())
            val now = File(dir, DesktopWidgetRules.linuxFile(DesktopWidgetRules.SHORTCUTS.first { it.args == listOf("dj", "mine") })).readText().lines()
            assertTrue(now.toString(), "Name=DJ with only my songs" in now && "Exec=\"$exe\" dj mine" in now)
            assertTrue("its own picture", now.any { it.startsWith("Icon=") && it.endsWith(".png") && "shortcut-icons" in it })
            assertEquals("done once", 0, Shortcuts.upgrade(dir))
        } finally { dir.deleteRecursively(); Harness.ui { Prefs.setText(Shortcuts.LIST, "") } }
    }

    @Test fun eachShortcutHasAPictureOfItsOwn() {
        Harness.ctx
        val pics = DesktopWidgetRules.SHORTCUTS.map { org.omarchy.music.ui.ShortcutIcons.draw(it) }
        pics.forEach { png ->
            val img = org.jetbrains.skia.Image.makeFromEncoded(png)
            assertEquals("a square PNG", 256 to 256, img.width to img.height)
        }
        assertEquals("each looks different", pics.size, pics.map { it.toList() }.distinct().size)
        val ico = org.omarchy.music.ui.ShortcutIcons.ico(DesktopWidgetRules.SHORTCUTS[0])!!
        assertTrue(ico.name.endsWith(".ico") && ico.length() > 22)
    }

    @Test fun theCommandsPlayAPlaylistAndOpenAWidget() {
        Harness.ctx
        val name = "Shortcut test road trip"
        val songs = Harness.songs(5, from = 7100)
        try {
            Harness.ui {
                Music.lists.add(Music.lists.create(name).id, songs)
                Instance.act(listOf("play", "playlist", "shortcut test road", "shuffle"))
                assertEquals("the playlist's songs, shuffled", songs.map { it.id }.toSet(), Music.queue.map { it.id }.toSet())
                assertTrue(Music.shuffled)
                Instance.act(listOf("play", "playlist", name))
                assertEquals("in order, from the top", songs.map { it.id }, Music.queue.map { it.id })
                assertEquals(0, Music.index)
                Instance.act(listOf("widget", "lyrics"))
                assertTrue(org.omarchy.music.ui.DesktopWidgets.isOpen("lyrics"))
                Instance.act(listOf("widget", "Lyrics", "off"))
                assertFalse(org.omarchy.music.ui.DesktopWidgets.isOpen("lyrics"))
                // New releases and the Visualizer, by their names as the tray says them too
                Instance.act(listOf("widget", "new", "releases"))
                assertTrue(org.omarchy.music.ui.DesktopWidgets.isOpen("releases"))
                Instance.act(listOf("widget", "visualizer"))
                assertTrue(org.omarchy.music.ui.DesktopWidgets.isOpen("visualizer"))
                Instance.act(listOf("widget", "visualizer", "off")); Instance.act(listOf("widget", "releases", "off"))
                assertFalse(org.omarchy.music.ui.DesktopWidgets.isOpen("visualizer") || org.omarchy.music.ui.DesktopWidgets.isOpen("releases"))
            }
            // Made for the Shortcuts widget only: kept (the widget shows them) and the widget opened
            val made = listOf(DesktopWidgetRules.SHORTCUTS[0], DesktopWidgetRules.playlistShortcut(name, shuffled = false))
            assertEquals("Made 2 shortcuts on the Shortcuts widget", Shortcuts.make(made, menu = false, desktop = false, widget = true))
            Harness.until("the Shortcuts widget open with them") { org.omarchy.music.ui.DesktopWidgets.isOpen("shortcuts") && Shortcuts.made() == made }
            Harness.ui {
                Instance.act(listOf("widget", "shortcuts", "off"))
                assertFalse(org.omarchy.music.ui.DesktopWidgets.isOpen("shortcuts"))
                Prefs.setText(Shortcuts.LIST, "")
            }
        } finally {
            Harness.ui {
                Music.lists.all().filter { it.name == name }.forEach { Music.lists.delete(it.id) }; Music.musicVersion++
                // Left as found: the other tests start with no queue
                Music.queue = emptyList(); Music.index = -1; Music.shuffled = false; PlaybackCore.loadedFor = null; Music.paused = true
            }
        }
    }
}
