package org.omarchy.music.ui

import org.omarchy.music.Music
import org.omarchy.music.Prefs
import org.omarchy.music.ShareCodeRules
import org.omarchy.music.ShareCodeRules.Made
import org.omarchy.music.ShareCodeRules.Parts

/** Share codes ([ShareCodeRules]) for Personalization: a code for any choice of any of its menus (Copy code, on a
 *  choice or on several ticked), for the effects on now, and for everything on now as one preset; and what pasting a
 *  code does. */
object ShareCodes {
    /** Where each store of things made is kept. */
    private val PREFS: Map<String, String> get() = mapOf("look" to MyLooks.PREF, "layout" to UiLayouts.MINE, "vis" to MyVis.STYLES, "viscolor" to MyVis.COLORS,
        "wrapped" to MyWraps.PREF, "card" to LyricsCards.PREF, "preset" to MyPresets.PREF)

    /** Something you made, by its key: its store and the line it is kept in. */
    private fun find(key: String): Made? {
        val store = ShareCodeRules.STORES.entries.firstOrNull { key.startsWith(it.value) }?.key ?: return null
        val line = Prefs.text(PREFS.getValue(store)).lines().firstOrNull { ShareCodeRules.keyOf(it) == key } ?: return null
        return Made(store, line)
    }

    /** What a code for these choices of a menu holds (kind: the menu's favourites' kind). One you made goes as it is,
     *  with what it uses; a ready-made preset goes whole, as you have it (your edits of it too), and arrives as a preset
     *  of theirs; any other ready-made choice goes by name, and pasting puts it on. */
    fun parts(kind: String, keys: List<String>): Parts {
        val made = ArrayList<Made>(); val picks = ArrayList<Pair<String, String>>()
        for (k in keys) {
            if (k.isEmpty()) continue
            val mine = find(k)
            if (mine != null) made += mine
            else if (kind == Favs.PRESETS) MyPresets.of(k)?.let { made += Made("preset", MyPresets.line(it)) }
            else picks += kind to k
        }
        return Parts(ShareCodeRules.withUsed(made, ::find), picks)
    }

    fun copy(kind: String, keys: List<String>) {
        val p = parts(kind, keys)
        if (p.empty) { Music.toast("Nothing to make a code of: tick some first"); return }
        copyText("A Radon Player share code", ShareCodeRules.encode(p))
        val n = p.made.count { it.main } + p.picks.size
        Music.toast("Copied the code" + (if (n > 1) " of $n things" else "") + ": paste it into a message. They paste it in Settings › Personalization › Share")
    }

    /** The effects on now. */
    fun copyEffects() {
        copyText("A Radon Player share code", ShareCodeRules.encode(Parts(effects = (if (LookFx.ownOn()) "1" else "0") + "|" + Prefs.text(LookFx.PICK))))
        Music.toast("Copied the code of your effects: paste it into a message")
    }

    /** Everything on now (look, theme, layout, effects, visualizers, colour, Wrapped style, lyrics card) as one preset. */
    fun copySetup() {
        val now = Made("preset", MyPresets.line(MyPresets.now("Shared setup", "mypreset-shared")))
        copyText("A Radon Player share code", ShareCodeRules.encode(Parts(ShareCodeRules.withUsed(listOf(now), ::find))))
        Music.toast("Copied the code of everything on now: it arrives as a preset called Shared setup")
    }

    /** Puts a ready-made choice of a menu on. False: this app doesn't have it. */
    private fun pick(kind: String, key: String): Boolean = runCatching {
        when (kind) {
            Favs.LOOKS -> Looks.known(key).also { if (it) Music.chooseLook(key) }
            Favs.THEMES -> Themes.known(key).also { if (it) { Prefs.setText(Themes.PREF, key); Music.applyLook() } }
            Favs.LAYOUTS -> UiLayouts.all().any { it.key == key }.also { if (it) UiLayouts.choose(key) }
            Favs.EFFECTS -> LookFx.MENU.any { it.first == key }.also { if (it) { if (LookFx.ownOn()) LookFx.setOwn(false); if (!LookFx.has(LookFx.picked(), key)) LookFx.toggle(key) } }
            Favs.VIS -> NP_VIS_STYLES.any { it.first == key }.also { if (it) Prefs.setText(Vis.NOW, key) }
            Favs.VIS_COLOURS -> key.isNotBlank().also { if (it) Prefs.setText(Vis.COLOR, key) }
            Favs.WRAPPED -> WS.STYLES.any { it.first == key }.also { if (it) Music.chooseWrappedStyle(key) }
            Favs.CARDS -> (key == LyricsCards.LOOK || key == LyricsCards.NOW || LyricsCards.of(key) != null).also { if (it) LyricsCards.pick(key) }
            else -> false
        }
    }.getOrDefault(false)

    /** A code pasted: what it holds is added (things made, to yours under keys of their own) and put on (ready-made
     *  choices, effects). What it did, in words; null: that isn't a code. A look's old code (RadonLook:) still works. */
    fun add(code: String): String? {
        val text = code.trim()
        if (text.startsWith(MyLooks.CODE)) return MyLooks.add(text)?.let { "Added “${it.name}” to your looks: it is in the Look menu, under Yours" }
        val p = ShareCodeRules.decode(text) ?: return null
        val t = System.currentTimeMillis(); var n = 0
        val made = ShareCodeRules.rekeyed(p.made, { s -> ShareCodeRules.STORES.getValue(s) + (t + n++).toString(36) },
            { s -> Prefs.text(PREFS.getValue(s)).lines().map { ShareCodeRules.nameOf(it) }.toSet() })
        for (m in made) {
            val pref = PREFS.getValue(m.store); val old = Prefs.text(pref).trimEnd('\n', '\r')
            Prefs.setText(pref, if (old.isBlank()) m.line else old + "\n" + m.line)
        }
        val picked = p.picks.count { pick(it.first, it.second) }
        p.effects?.let { Prefs.set(LookFx.OWN, it.substringBefore('|') == "1"); Prefs.setText(LookFx.PICK, it.substringAfter('|', "")); Music.applyLook() }
        MyPresets.applied++
        val said = ShareCodeRules.added(made.filter { it.main }.map { ShareCodeRules.nameOf(it.line) }, picked, p.effects != null)
        return said.ifEmpty { "The code's choices aren't in this version of the app" }
    }
}
