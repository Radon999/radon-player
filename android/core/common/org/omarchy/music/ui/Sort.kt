package org.omarchy.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.omarchy.music.Music
import org.omarchy.music.SORT_NAMES
import org.omarchy.music.SortData
import org.omarchy.music.sortNeedsPlays

// The Sort menu every tab has: a chip naming the sort, which opens the list of the sorts that tab has.

/** tab's sort now (kept per tab), from options, default if none was picked. */
fun sortNow(tab: String, options: List<String>, default: String = options.first()) = Music.sortOf(tab, default, options)

/** "Sort: Recently added ▾": a tap lists the tab's sorts; the one picked is kept for the tab.
 *  names: other words for some of them (the "Default" of a list, say "Your order"). */
@Composable
fun SortMenu(tab: String, options: List<String>, default: String = options.first(), names: Map<String, String> = emptyMap()) {
    var open by remember { mutableStateOf(false) }
    val now = sortNow(tab, options, default)
    fun name(k: String) = names[k] ?: SORT_NAMES[k] ?: k
    Box {
        Chip("Sort: ${name(now)} ▾", false) { open = true }
        DropdownMenu(open, { open = false }, containerColor = C.light) {
            options.forEach { k ->
                DropdownMenuItem({ Text((if (k == now) "✓  " else "     ") + name(k), style = T.body) }, { open = false; Music.setSort(tab, k) })
            }
        }
    }
}

/** items sorted by work, given what the sort needs; sorts by your plays are worked out away from the screen
 *  (the list stays as it was until they're ready). */
@Composable
fun <T> rememberSorted(items: List<T>, sort: String, work: (SortData) -> List<T>): List<T> {
    if (!sortNeedsPlays(sort)) return remember(items, sort) { runCatching { work(SortData.NONE) }.getOrDefault(items) }
    val out by produceState(items, items, sort) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { work(Music.sortData(sort)) }.getOrDefault(items)
        }
    }
    return out
}
