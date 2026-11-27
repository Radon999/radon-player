package org.omarchy.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.omarchy.music.Album
import org.omarchy.music.Follows
import org.omarchy.music.Music

/** The artists picked on Followed artists (Select), to unfollow together. */
object FollowedPicks {
    val names = mutableStateListOf<String>()
    var on by mutableStateOf(false)
    /** Only the favorite artists for New releases shown (★ Favorites only). */
    var favOnly by mutableStateOf(false)
    fun toggle(n: String) { if (n in names) names.remove(n) else names += n }
    fun done() { on = false; names.clear() }
}

/** Utilities › Followed artists: whose new releases New releases looks for. A tap opens the artist's page; right-click
 *  (or hold) one to unfollow it or to pick several (Select), then Unfollow them together. Follow anyone from their menu
 *  (right-click an artist anywhere). With Follow artists by themselves on (Settings › General), the artists of your songs
 *  and of your Radon Player playlists are followed as they come; an unfollow always stays. */
fun LazyListScope.followedView(ui: Ui) {
    val P = FollowedPicks
    val NR = org.omarchy.music.NewReleases
    val all = Follows.list().sortedBy { it.lowercase() }
    val shown = all.filter { fits(ui, it) && (!P.favOnly || NR.isFavorite(it)) }
    item {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (P.on) {
                Pill(if (P.names.isEmpty()) "Unfollow" else "Unfollow (${P.names.size})") {
                    if (P.names.isEmpty()) Music.toast("Pick the artists to unfollow first")
                    else { val n = P.names.toList(); ui.confirm = "Unfollow ${n.size} artist${if (n.size != 1) "s" else ""}? Their new releases won't show." to { Follows.unfollow(n); P.done() } }
                }
                // The picked made favorites for New releases (a shorter list with its own bell), or not any more
                if (P.names.isNotEmpty()) Pill("Add to favorites (${P.names.size})", quiet = true) { NR.setFavorite(P.names.toList(), true); P.done() }
                if (P.names.any { NR.isFavorite(it) }) Pill("Remove from favorites", quiet = true) {
                    NR.setFavorite(P.names.filter { NR.isFavorite(it) }, false); P.done() }
                Pill("Done", quiet = true) { P.done() }
            } else if (all.isNotEmpty()) Pill("Select", quiet = true) { P.on = true }
            Chip("★ Favorites only", P.favOnly) { P.favOnly = !P.favOnly }
        }
    }
    item {
        Section(if (P.on) "${P.names.size} PICKED" else if (P.favOnly) "${shown.size} FAVORITE ARTISTS FOR NEW RELEASES" else "${all.size} FOLLOWED" +
            if (Follows.autoOn()) " · YOUR ARTISTS FOLLOWED BY THEMSELVES" else " · FOLLOWED BY HAND ONLY (SETTINGS › GENERAL)")
    }
    if (all.isEmpty()) {
        item { Empty("No artists followed", "Right-click (or hold) an artist anywhere and pick Follow, or switch on Follow artists by " +
            "themselves in Settings › General to follow the artists of your songs and playlists.") }
        return
    }
    fun menu(n: String, fav: Boolean) {
        ui.choices = n to listOf(
            if (fav) "Remove from favorite artists" to { NR.setFavorite(listOf(n), false) }
            else "Add to favorite artists" to { NR.setFavorite(listOf(n), true) },
            "Unfollow" to { Follows.unfollow(listOf(n)) }, "Select" to { P.on = true; P.toggle(n) })
    }
    fun open(n: String) { if (P.on) P.toggle(n) else ui.pages += Ui.Page.Artist(n) }
    // As a list (each with whether its new releases are looked for, and prioritized), or a grid of pictures (the
    // favorites' names with a ★), as the page's layout says
    laidOutItems(shown, ui, key = { "follow-${shown[it]}" }, names = { shown[it] }, row = { i ->
        val n = shown[i]
        val fav = NR.isFavorite(n)
        PlaylistRow(Album(n, n, if (P.on) (if (n in P.names) "Picked" else "") else if (fav) "★ New releases looked for and prioritized" else "New releases looked for", ""),
            artistArt(n), checked = if (P.on) n in P.names else null, onLongClick = { menu(n, fav) }) { open(n) }
    }) { i, mod, compact ->
        val n = shown[i]
        val fav = NR.isFavorite(n)
        val on = P.on && n in P.names
        Tile(artistArt(n), if (fav) "★ $n" else n, "", mod, compact, round = true, ico = Ico.PERSON, lit = on,
            onLongClick = { menu(n, fav) },
            over = { if (P.on) Tick(on, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(6.dp)) }) { open(n) }
    }
}
