package org.omarchy.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.omarchy.music.SearchHistory

/** A place's last searches (SearchHistory), newest first, in a row under its search bar: tap one to search it again,
 *  × beside it to forget it, Clear to forget them all. Nothing shown while there are none. */
@Composable
fun RecentSearches(place: String, onPick: (String) -> Unit) {
    val list = SearchHistory.of(place)   // (a setting: read in Compose, so a change shows at once)
    if (list.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Recent", style = T.sub)
        for (q in list) Row(Modifier.clip(R.pill).background(if (C.isLight) C.light else C.fg.copy(alpha = 0.07f)),
            verticalAlignment = Alignment.CenterVertically) {
            Text(q, style = T.sub.copy(color = C.fg), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(R.pill).clickable { onPick(q) }.padding(start = 12.dp, end = 4.dp, top = 7.dp, bottom = 7.dp))
            Text("×", style = T.sub, modifier = Modifier.clip(R.pill).clickable { SearchHistory.remove(place, q) }
                .padding(start = 4.dp, end = 10.dp, top = 7.dp, bottom = 7.dp))
        }
        Text("Clear", style = T.sub.copy(color = C.accent), modifier = Modifier.clip(R.pill)
            .clickable { SearchHistory.clear(place) }.padding(horizontal = 8.dp, vertical = 7.dp))
    }
}
