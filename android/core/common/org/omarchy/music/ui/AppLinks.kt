package org.omarchy.music.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.omarchy.music.Lyrics
import org.omarchy.music.Music
import org.omarchy.music.Pin
import org.omarchy.music.PhoneWidgetRules
import org.omarchy.music.Wrapped

/** A place in the app asked for from outside it (the phone's home screen widgets): the window opens straight on it.
 *  What is asked is written as [PhoneWidgetRules] says: "player" (Now playing), "top" (Most played), "pinned", "playlists",
 *  "newreleases", "wrapped:week|month|all" (Wrapped on that period; "wrapped" alone: its own choice), "lyrics" (Now playing's
 *  Lyrics), "card" (the lyrics card maker for the song playing), "list:<id>" (a playlist of yours), "folder:<id>" (a folder
 *  of playlists), "pin:<key>" (something pinned), "release:<key>" (a new release's page, as New releases opens it).
 *  The window takes it as it shows ([App]). */
object AppLinks {
    var asked by mutableStateOf<String?>(null)

    suspend fun open(ui: Ui, link: String) {
        val (kind, rest) = PhoneWidgetRules.split(link)
        // Whatever was open over the screens (Settings, a dialog) is closed, so the place asked for is the one you see
        if (kind != "wrapped") { ui.sheet = null; ui.lyricsCard = null }
        when (kind) {
            "player" -> { goTo(ui, "player"); ui.pane = "art" }
            "top" -> goTo(ui, "music", "top")
            "pinned" -> goTo(ui, "music", "pinned")
            "playlists" -> goTo(ui, "music", "playlists")
            "newreleases" -> goTo(ui, "music", "newreleases")
            "release" -> {
                goTo(ui, "music", "newreleases")
                val nr = org.omarchy.music.NewReleases
                val r = (nr.told + nr.found).firstOrNull { nr.key(it) == rest } ?: run { Music.toast("That release isn't in New releases any more"); return }
                Music.toast("Opening “${r.title}”…")
                Music.bg({ Music.releaseFind(r) }) { found, err ->
                    if (found == null) { Music.toast("Couldn't find “${r.title}” on your services" + (err?.message?.let { ": ${it.take(80)}" } ?: "")); return@bg }
                    val (key, al, songs) = found
                    ui.pages += if (al != null) Ui.Page.AlbumPage(al, key)
                        else Ui.Page.AlbumPage(org.omarchy.music.Album("single-${songs[0].id}", r.title, "${r.artist} · Single", songs[0].thumb.ifEmpty { r.cover }), key, songs)
                }
            }
            "wrapped" -> {
                val now = java.time.LocalDate.now()
                WrappedAsk.period = when (rest) {
                    "week" -> Wrapped.Period.Week(now.with(java.time.DayOfWeek.MONDAY))
                    "month" -> Wrapped.Period.Month(java.time.YearMonth.from(now))
                    "all" -> Wrapped.Period.All
                    else -> rest.toIntOrNull()?.let { Wrapped.Period.Year(it) }
                }
                // Wrapped open already: opened again, so it starts on the period asked for
                if (ui.sheet == "wrapped") { ui.sheet = null; delay(80) }
                ui.drawer = false
                ui.sheet = "wrapped"
            }
            "lyrics" -> { goTo(ui, "player"); ui.pane = "lyrics" }
            "card" -> {
                goTo(ui, "player"); ui.pane = "lyrics"
                val t = Music.shownTrack() ?: run { Music.toast("Nothing is playing to make a lyrics card of"); return }
                Music.bg({ Lyrics.get(Music.ctx, t) }) { g, _ ->
                    if (Music.shownTrack()?.id != t.id) return@bg
                    if (g == null || (g.plain == null && g.synced == null)) Music.toast("“${t.title}” has no lyrics to make a card of")
                    else ui.lyricsCard = t to g
                }
            }
            "list" -> { goTo(ui, "music", "playlists"); openPin(ui, Pin("mine", rest, Music.lists.get(rest)?.name ?: "That playlist")) }
            "folder" -> { goTo(ui, "music", "playlists"); openPin(ui, Pin("listfolder", rest, Music.playlistFolders.get(rest)?.name ?: "That folder")) }
            "pin" -> {
                val p = Music.pins.firstOrNull { it.key == rest }
                goTo(ui, "music", "pinned")
                if (p == null) Music.toast("That isn't pinned any more") else openPin(ui, p)
            }
        }
    }
}
