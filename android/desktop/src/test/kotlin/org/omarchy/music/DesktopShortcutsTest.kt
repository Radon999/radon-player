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
            val dj = DesktopWidgetRules.SHORTCUTS.first { it.args == listOf("dj", "new") }
            val releases = DesktopWidgetRules.SHORTCUTS.first { it.name == "New releases" }
            val list = listOf(dj, DesktopWidgetRules.playlistShortcut("Road trip", shuffled = true), releases)
            assertEquals("each shortcut in each folder", 6, Shortcuts.writeLinux("/opt/radon-player/bin/Radon Player", list, listOf(menu, desk), null))
            val road = File(desk, "radon-player-shortcut-shuffle-road-trip.desktop")
            assertTrue("a desktop file is a program to run", road.canExecute())
            assertTrue(road.readText(), "Exec=\"/opt/radon-player/bin/Radon Player\" play playlist \"Road trip\" shuffle" in road.readText().lines())
            val djFile = File(menu, "radon-player-shortcut-dj-with-new-songs-too.desktop")
            assertTrue(djFile.readText(), "Exec=\"/opt/radon-player/bin/Radon Player\" dj new" in djFile.readText().lines())
            val nrFile = File(menu, "radon-player-shortcut-new-releases.desktop")
            assertTrue(nrFile.readText(), "Exec=\"/opt/radon-player/bin/Radon Player\" show \"new releases\"" in nrFile.readText().lines())
            assertEquals(6, Shortcuts.removeLinux(listOf(menu, desk)))
            assertFalse(road.exists())
            assertTrue("a file with our name but not made here stays", notOurs.exists())
        } finally { dir.deleteRecursively() }
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
        } finally {
            Harness.ui {
                Music.lists.all().filter { it.name == name }.forEach { Music.lists.delete(it.id) }; Music.musicVersion++
                // Left as found: the other tests start with no queue
                Music.queue = emptyList(); Music.index = -1; Music.shuffled = false; PlaybackCore.loadedFor = null; Music.paused = true
            }
        }
    }
}
