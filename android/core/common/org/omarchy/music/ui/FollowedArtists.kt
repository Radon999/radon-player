package org.omarchy.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
    fun toggle(n: String) { if (n in names) names.remove(n) else names += n }
    fun done() { on = false; names.clear() }
}

/** Utilities › Followed artists: whose new releases New releases looks for. A tap opens the artist's page; right-click
 *  (or hold) one to unfollow it or to pick several (Select), then Unfollow them together. Follow anyone from their menu
 *  (right-click an artist anywhere). With Follow artists by themselves on (Settings › General), the artists of your songs
 *  and of your Radon Player playlists are followed as they come; an unfollow always stays. */
fun LazyListScope.followedView(ui: Ui) {
    val all = Follows.list().sortedBy { it.lowercase() }
    val shown = all.filter { fits(ui, it) }
    val P = FollowedPicks
    item {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (P.on) {
                Pill(if (P.names.isEmpty()) "Unfollow" else "Unfollow (${P.names.size})") {
                    if (P.names.isEmpty()) Music.toast("Pick the artists to unfollow first")
                    else { val n = P.names.toList(); ui.confirm = "Unfollow ${n.size} artist${if (n.size != 1) "s" else ""}? Their new releases won't show." to { Follows.unfollow(n); P.done() } }
                }
                Pill("Done", quiet = true) { P.done() }
            } else if (all.isNotEmpty()) Pill("Select", quiet = true) { P.on = true }
        }
    }
    item {
        Section(if (P.on) "${P.names.size} PICKED" else "${all.size} FOLLOWED" +
            if (Follows.autoOn()) " · YOUR ARTISTS FOLLOWED BY THEMSELVES" else " · FOLLOWED BY HAND ONLY (SETTINGS › GENERAL)")
    }
    if (all.isEmpty()) {
        item { Empty("No artists followed", "Right-click (or hold) an artist anywhere and pick Follow, or switch on Follow artists by " +
            "themselves in Settings › General to follow the artists of your songs and playlists.") }
        return
    }
    items(shown, key = { "follow-$it" }) { n ->
        val fav = org.omarchy.music.NewReleases.isFavorite(n)
        PlaylistRow(Album(n, n, if (P.on) (if (n in P.names) "Picked" else "") else if (fav) "★ Favorite · new releases looked for" else "New releases looked for", ""), artistArt(n),
            checked = if (P.on) n in P.names else null,
            onLongClick = { ui.choices = n to listOf(
                if (fav) "Remove from favorite artists" to { org.omarchy.music.NewReleases.setFavorite(listOf(n), false) }
                else "Add to favorite artists" to { org.omarchy.music.NewReleases.setFavorite(listOf(n), true) },
                "Unfollow" to { Follows.unfollow(listOf(n)) }, "Select" to { P.on = true; P.toggle(n) }) }) {
            if (P.on) P.toggle(n) else ui.pages += Ui.Page.Artist(n)
        }
    }
}
