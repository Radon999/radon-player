package org.omarchy.music

/** Personalization's menus: choices hidden from a menu, and the ready-made presets edited.
 *
 *  Hidden: any choice of any Personalization menu (a look, a theme, a layout, an effect, a visualizer, a colour blend,
 *  a Wrapped style, a lyrics card, a preset) can be hidden from its menu, several at once, to make the menu shorter.
 *  Nothing is deleted: the hidden ones are listed at the menu's end and come back with a tap. The one in use is never
 *  hidden while it is in use.
 *
 *  Edited presets: a ready-made preset keeps its look, theme, layout and the rest, and its effects and Now playing's two
 *  visualizers can be changed: effects taken off (the others stay) or added, the middle visualizer and the bottom one
 *  each kept, changed, or taken away. The preset itself is never changed or deleted: an edit is kept beside it, and
 *  "Reset default preset edits" puts every preset back as it came. */
object PresetEditRules {
    /** The edits, a line each. */
    const val PREF = "preset_edits"
    /** The hidden choices of a menu: this and the menu's kind ("hidden_fav_themes"), keys joined with ";". */
    const val HIDDEN = "hidden_"

    /** middle, bottom: null as the preset has it, "off" for none, else the style. */
    data class Edit(val key: String, val fxOff: Set<String> = emptySet(), val fxAdd: Set<String> = emptySet(),
                    val middle: String? = null, val bottom: String? = null) {
        val empty: Boolean get() = fxOff.isEmpty() && fxAdd.isEmpty() && middle == null && bottom == null
    }

    private fun set(s: String) = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    fun encode(e: Edit): String = listOf(e.key, e.fxOff.joinToString(","), e.fxAdd.joinToString(","), e.middle ?: "-", e.bottom ?: "-").joinToString("|")
    fun decode(text: String): Map<String, Edit> = text.lines().mapNotNull { l ->
        val f = l.split("|"); if (f.size < 5 || f[0].isBlank()) null
        else Edit(f[0].trim(), set(f[1]), set(f[2]), f[3].trim().takeIf { it != "-" && it.isNotEmpty() }, f[4].trim().takeIf { it != "-" && it.isNotEmpty() })
    }.filter { !it.empty }.associateBy { it.key }
    fun write(edits: Map<String, Edit>): String = edits.values.filter { !it.empty }.joinToString("\n") { encode(it) }

    /** A preset's effects (joined with ";") with the edit: the ones taken off gone, the ones added at the end.
     *  keyOf: an effect's key without its colour. */
    fun fx(base: String, e: Edit, keyOf: (String) -> String): String {
        val kept = base.split(';').map { it.trim() }.filter { it.isNotEmpty() && keyOf(it) !in e.fxOff }
        val have = kept.map(keyOf).toSet()
        return (kept + e.fxAdd.filter { it !in have }).joinToString(";")
    }

    /** A preset's two visualizers with the edit. */
    fun vis(base: PresetVisRules.Trio, e: Edit, isBottom: (String) -> Boolean): PresetVisRules.Trio {
        if (e.middle == null && e.bottom == null) return base
        val m = when (e.middle) { null -> PresetVisRules.middle(base, isBottom); "off" -> null; else -> e.middle }
        val b = when (e.bottom) { null -> PresetVisRules.bottom(base, isBottom); "off" -> null; else -> e.bottom }
        return PresetVisRules.join(m, b, base.bottom)
    }

    // ---- hidden choices ----
    fun hidden(text: String): Set<String> = text.split(';').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    fun hiddenText(keys: Set<String>): String = keys.joinToString(";")
    /** What a menu shows: all but the hidden, the ones in use always. */
    fun <T> shown(options: List<Pair<String, T>>, hidden: Set<String>, inUse: Set<String>): List<Pair<String, T>> =
        if (hidden.isEmpty()) options else options.filter { it.first !in hidden || it.first in inUse }
    /** Hiding these: never the ones in use, nor the empty choice. */
    fun hide(hidden: Set<String>, keys: Collection<String>, inUse: Set<String>): Set<String> = hidden + keys.filter { it.isNotEmpty() && it !in inUse }
}
