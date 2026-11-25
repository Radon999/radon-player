package org.omarchy.music.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.omarchy.music.Music
import org.omarchy.music.Prefs
import org.omarchy.music.PresetEditRules
import org.omarchy.music.PresetEditRules.Edit

/** The choices hidden from Personalization's menus ([PresetEditRules]): a menu's kind is its favourites' kind. */
object Hidden {
    /** Goes up when something is hidden or shown again, so the menus draw again. */
    var version by mutableStateOf(0)
        private set
    /** The menus that can hide choices. */
    val KINDS: Set<String> get() = setOf(Favs.LOOKS, Favs.THEMES, Favs.LAYOUTS, Favs.EFFECTS, Favs.VIS, Favs.VIS_COLOURS, Favs.WRAPPED, Favs.PRESETS, Favs.CARDS)
    fun of(kind: String): Set<String> = PresetEditRules.hidden(Prefs.text(PresetEditRules.HIDDEN + kind))
    /** How many of keys were hidden (the ones in use are not). */
    fun hide(kind: String, keys: Collection<String>, inUse: Set<String>): Int {
        val before = of(kind); val now = PresetEditRules.hide(before, keys, inUse)
        Prefs.setText(PresetEditRules.HIDDEN + kind, PresetEditRules.hiddenText(now)); version++
        return now.size - before.size
    }
    fun show(kind: String, key: String) { Prefs.setText(PresetEditRules.HIDDEN + kind, PresetEditRules.hiddenText(of(kind) - key)); version++ }
    fun showAll(kind: String) { Prefs.setText(PresetEditRules.HIDDEN + kind, ""); version++ }
}

/** The ready-made presets' edits ([PresetEditRules]): their effects and visualizers as you changed them. */
object PresetEdits {
    var version by mutableStateOf(0)
        private set
    private var readFrom: String? = null
    private var read: Map<String, Edit> = emptyMap()
    fun all(): Map<String, Edit> {
        val text = Prefs.text(PresetEditRules.PREF)
        if (text != readFrom) { read = PresetEditRules.decode(text); readFrom = text }
        return read
    }
    fun of(key: String): Edit? = all()[key]
    fun count(): Int = all().size
    fun set(edits: List<Edit>) {
        val m = all().toMutableMap()
        edits.forEach { if (it.empty) m.remove(it.key) else m[it.key] = it }
        Prefs.setText(PresetEditRules.PREF, PresetEditRules.write(m)); version++; MyPresets.applied++
    }
    /** Back as they came: these, or all of them. */
    fun reset(keys: Collection<String>? = null) {
        val m = if (keys == null) emptyMap() else all() - keys.toSet()
        Prefs.setText(PresetEditRules.PREF, PresetEditRules.write(m)); version++; MyPresets.applied++
    }
}

/** Edit a ready-made preset, or several at once (picked in the Presets menu): effects off or added, and Now playing's
 *  middle and bottom visualizers kept, changed or taken away. The look, theme, layout and the rest stay as they came. */
@Composable
fun PresetEditDialog(ui: Ui, keys: List<String>) {
    val presets = remember(keys) { keys.mapNotNull { MyPresets.raw(it) } }
    if (presets.isEmpty()) { ui.presetEdit = null; return }
    val one = presets.singleOrNull()
    val was = one?.let { PresetEdits.of(it.key) }
    // every effect the picked presets came with, each once
    val came = remember(keys) { presets.flatMap { p -> p.fx.split(';').map { LookFx.keyOf(it.trim()) }.filter { it.isNotEmpty() } }.distinct() }
    var off by remember(keys) { mutableStateOf(was?.fxOff ?: emptySet()) }
    var add by remember(keys) { mutableStateOf(was?.fxAdd ?: emptySet()) }
    var middle by remember(keys) { mutableStateOf(was?.middle ?: "") }
    var bottom by remember(keys) { mutableStateOf(was?.bottom ?: "") }
    val names = LookFx.MENU.toMap()
    val asIs = if (one != null) "As it came" else "As each one came"
    AlertDialog(onDismissRequest = { ui.presetEdit = null }, containerColor = C.light,
        title = { Text(if (one != null) "Edit “${one.name}”" else "Edit ${presets.size} presets", style = T.head) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text((if (one == null) presets.take(4).joinToString(", ") { it.name } + (if (presets.size > 4) " and ${presets.size - 4} more. " else ". ") else "") +
                    "The look, theme, layout and the rest stay as they came; its effects and Now playing's two visualizers are yours to change. " +
                    "Put the preset on again to see it", style = T.sub)
                Text("EFFECTS IT CAME WITH: TAP ONE TO TAKE IT OFF", style = T.section)
                if (came.isEmpty()) Text("None", style = T.sub)
                else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    came.forEach { k -> Chip(if (k in off) "✕ ${names[k] ?: k}" else (names[k] ?: k), k !in off) { off = if (k in off) off - k else off + k } }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add effects", style = T.body, modifier = Modifier.weight(1f))
                    ChoicePill(LookFx.MENU.filter { it.first !in came }, "", chosen = add, group = { MenuGroups.effect(it) }) { k -> add = if (k in add) add - k else add + k }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Middle visualizer", style = T.body, modifier = Modifier.weight(1f))
                    ChoicePill(listOf("" to asIs, "off" to "None") + Vis.MIDDLE_STYLES, middle, group = { if (it.isEmpty() || it == "off") "Basics" else MenuGroups.vis(it) }) { middle = it }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bottom visualizer", style = T.body, modifier = Modifier.weight(1f))
                    ChoicePill(listOf("" to asIs, "off" to "None") + Vis.BOTTOM_STYLES, bottom, group = { if (it.isEmpty() || it == "off") "Basics" else MenuGroups.vis(it) }) { bottom = it }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton({ PresetEdits.reset(presets.map { it.key }); ui.presetEdit = null
                    Music.toast(if (one != null) "“${one.name}” is back as it came" else "${presets.size} presets are back as they came") }) { Text("Back to default", color = C.muted) }
                TextButton({
                    PresetEdits.set(presets.map { p ->
                        val has = p.fx.split(';').map { LookFx.keyOf(it.trim()) }.toSet()
                        Edit(p.key, off.filter { it in has }.toSet(), add.filter { it !in has }.toSet(), middle.ifEmpty { null }, bottom.ifEmpty { null }) })
                    ui.presetEdit = null
                    Music.toast(if (one != null) "Saved: put “${one.name}” on to see it" else "Saved for ${presets.size} presets")
                }) { Text("Save", color = C.accent) }
            }
        },
        dismissButton = { TextButton({ ui.presetEdit = null }) { Text("Cancel", color = C.muted) } })
}
