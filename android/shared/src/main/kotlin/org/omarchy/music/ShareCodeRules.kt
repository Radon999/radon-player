package org.omarchy.music

/** Share codes: anything from Personalization's menus as one line of text to send to someone (or to your other device)
 *  and paste in Settings › Personalization › Share. A code holds:
 *  - things made (a look, a layout, a visualizer, a colour blend, a Wrapped style, a lyrics card, a preset), each as
 *    the line the app keeps it in. A preset is whole: the look, layout, visualizers, colours, Wrapped style and lyrics
 *    card it uses come along when they are ones you made, since the other person doesn't have them;
 *  - ready-made choices picked from a menu (which menu, which one): pasting puts them on;
 *  - a set of effects.
 *  Pasting adds what was made to yours, under keys of its own (nothing of yours is replaced), with every mention of
 *  the old keys pointed at the new ones. No names of songs, no settings, nothing about you is in a code. */
object ShareCodeRules {
    const val CODE = "RadonShare:"

    /** The stores of things made, and what their keys begin with. */
    val STORES = linkedMapOf("look" to "mylook-", "layout" to "mine-", "vis" to "myvis-", "viscolor" to "mycol-",
        "wrapped" to "mywrap-", "card" to "mycard-", "preset" to "mypreset-")

    /** What a code holds. made: (store, the kept line, key first); main: whether it is what was shared (true) or came
     *  along because something shared uses it. picks: (menu, key). effects: the set, or null. */
    data class Made(val store: String, val line: String, val main: Boolean = true)
    data class Parts(val made: List<Made> = emptyList(), val picks: List<Pair<String, String>> = emptyList(), val effects: String? = null) {
        val empty: Boolean get() = made.isEmpty() && picks.isEmpty() && effects == null
    }

    fun encode(p: Parts): String {
        val text = buildString {
            p.made.forEach { append(if (it.main) "I|" else "D|").append(it.store).append('|').append(it.line.replace("\n", " ")).append('\n') }
            p.picks.forEach { append("P|").append(it.first).append('|').append(it.second).append('\n') }
            p.effects?.let { append("F|").append(it.replace("\n", " ")).append('\n') }
        }
        return CODE + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))
    }

    /** null: that isn't a share code. */
    fun decode(code: String): Parts? {
        val body = code.trim().substringAfter(CODE, "").trim().takeIf { it.isNotEmpty() } ?: return null
        val text = runCatching { String(java.util.Base64.getUrlDecoder().decode(body.filter { !it.isWhitespace() }), Charsets.UTF_8) }.getOrNull() ?: return null
        val made = ArrayList<Made>(); val picks = ArrayList<Pair<String, String>>(); var fx: String? = null
        for (l in text.lines()) {
            val f = l.split("|", limit = 3)
            when {
                (f[0] == "I" || f[0] == "D") && f.size == 3 && f[1] in STORES && f[2].contains('|') -> made += Made(f[1], f[2].take(4000), f[0] == "I")
                f[0] == "P" && f.size == 3 && f[2].isNotBlank() -> picks += f[1] to f[2].take(120)
                f[0] == "F" -> fx = l.substringAfter('|').take(2000)
            }
        }
        return Parts(made.take(60), picks.take(60), fx).takeIf { !it.empty }
    }

    fun keyOf(line: String): String = line.substringBefore('|').trim()
    fun nameOf(line: String): String = line.split("|").getOrNull(1)?.trim().orEmpty()

    /** The keys of things made that a line mentions, other than its own: between its bars, and in lists with commas or
     *  semicolons (a preset names its look, layout and visualizers; a visualizer its colour blend). */
    fun mentions(line: String): Set<String> =
        line.split("|").drop(1).flatMap { it.split(',', ';') }.map { it.trim().substringBefore('@') }.filter { k -> STORES.values.any { k.startsWith(it) } }.toSet()

    /** What goes in a code for these: each, and whatever it uses that was made too (and what those use). find: a key's
     *  store and line, or null (it isn't there any more). */
    fun withUsed(mains: List<Made>, find: (String) -> Made?): List<Made> {
        val out = LinkedHashMap<String, Made>()
        mains.forEach { out[keyOf(it.line)] = it }
        val todo = ArrayDeque(mains)
        while (todo.isNotEmpty() && out.size < 60) {
            for (k in mentions(todo.removeFirst().line)) if (k !in out) find(k)?.let { m -> val dep = m.copy(main = false); out[k] = dep; todo += dep }
        }
        return out.values.toList()
    }

    /** The lines as they are added here: each under a new key, and every mention of an old key pointed at its new one.
     *  newKey: a store's next key. names: the names already taken in a store (one the same gets " (2)"). */
    fun rekeyed(made: List<Made>, newKey: (String) -> String, taken: (String) -> Set<String>): List<Made> {
        val keys = made.associate { keyOf(it.line) to newKey(it.store) }
        fun swap(field: String): String = if (field.contains(',') || field.contains(';')) {
            val sep = if (field.contains(';')) ';' else ','
            field.split(sep).joinToString(sep.toString()) { part -> val k = part.trim().substringBefore('@'); keys[k]?.let { part.replace(k, it) } ?: part }
        } else keys[field.trim()] ?: field
        return made.map { m ->
            val f = m.line.split("|").toMutableList()
            f[0] = keys.getValue(keyOf(m.line))
            if (f.size > 1) { val name = f[1].trim().removeSuffix(" (yours)").take(40).ifBlank { "Shared" }; f[1] = if (name in taken(m.store)) "$name (2)" else name }
            for (i in 2 until f.size) f[i] = swap(f[i])
            m.copy(line = f.joinToString("|"))
        }
    }

    /** What pasting a code did, in words. */
    fun added(names: List<String>, picks: Int, effects: Boolean): String {
        val bits = ArrayList<String>()
        if (names.isNotEmpty()) bits += "Added " + (if (names.size <= 3) names.joinToString(", ") { "“$it”" } else "${names.size} things") + " to yours (each is in its menu, under Yours)"
        if (picks > 0) bits += "put on $picks ready-made choice${if (picks != 1) "s" else ""}"
        if (effects) bits += "put on its effects"
        return bits.joinToString("; ").replaceFirstChar { it.uppercase() }
    }
}
