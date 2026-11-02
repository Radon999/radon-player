package org.omarchy.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.omarchy.music.Music
import org.omarchy.music.Prefs

// The makers (Settings › Personalization): your own looks (colours, with neon glow), your own layouts (which way round, the
// bar's buttons, the mini player, Now playing) and your own visualizers (styles built from the built-in ones, and colour
// blends). All are kept with the app's settings, so backups and Sync carry them, and they show in the same menus as the
// built-in ones, marked (yours).

/** The looks you made, kept with the app's settings: one line each (key|name|accent|bg|card|text|grad|glow|stars, colours in hex). */
object MyLooks {
    const val PREF = "my_looks"
    private var from = "\u0000"
    private var cache: List<Looks.CustomLook> = emptyList()

    fun all(): List<Looks.CustomLook> {
        val text = Prefs.text(PREF)
        if (text != from) {
            cache = text.lines().mapNotNull { l -> runCatching { val p = l.split("|")
                Looks.CustomLook(p[0], p[1], p[2].toLong(16), p[3].toLong(16), p[4].toLong(16), p[5].toLong(16), p[6].toLong(16),
                    p[7].toBoolean(), p[8].toBoolean(), p.getOrNull(9).orEmpty().split(",").filter { it.isNotBlank() }.toSet()) }.getOrNull() }
            from = text
        }
        return cache
    }
    /** Hands the Look menu where to find them. Called once at start, before the saved look is checked. */
    fun hook() { Looks.customSource = { all() } }

    private fun encode(c: Looks.CustomLook) = listOf(c.key, c.name.replace('|', '/'), c.accent.toString(16), c.bg.toString(16),
        c.card.toString(16), c.text.toString(16), c.grad.toString(16), c.glow, c.stars, c.fx.joinToString(",")).joinToString("|")

    fun save(c: Looks.CustomLook): Looks.CustomLook {
        val keyed = if (c.key.startsWith("mylook-")) c else Looks.CustomLook("mylook-" + System.currentTimeMillis().toString(36),
            c.name, c.accent, c.bg, c.card, c.text, c.grad, c.glow, c.stars, c.fx)
        Prefs.setText(PREF, (all().filter { it.key != keyed.key } + keyed).joinToString("\n") { encode(it) })
        return keyed
    }
    fun delete(key: String) {
        Prefs.setText(PREF, all().filter { it.key != key }.joinToString("\n") { encode(it) })
        if (Music.look == key) Music.chooseLook("apple-dark")
    }
}

private fun Long.col() = Color(this)
private fun Color.long(): Long = toArgb().toLong() and 0xFFFFFFFFL

// ───────────────────────────── A colour picker ─────────────────────────────

/** A colour: a strip of every hue, a box for how strong and how light, its hex code to type, and some to start from. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(value: Color, onChange: (Color) -> Unit) {
    val hsl = remember(value) { FloatArray(3).also { colorToHsl(value.toArgb() and 0xFFFFFF, it) } }
    var hue by remember { mutableStateOf(hsl[0]) }
    // The hue strip
    Canvas(Modifier.fillMaxWidth().height(26.dp).clip(R.small)
        .pointerInput(Unit) { detectTapGestures { o -> hue = (o.x / size.width * 360f).coerceIn(0f, 359f); onChange(Color(hslToColor(hue, hsl[1].coerceAtLeast(0.2f), hsl[2]))) } }
        .pointerInput(Unit) { detectDragGestures { ch, _ -> hue = (ch.position.x / size.width * 360f).coerceIn(0f, 359f)
            onChange(Color(hslToColor(hue, hsl[1].coerceAtLeast(0.2f), hsl[2]))) } }) {
        drawRect(Brush.horizontalGradient(List(13) { Color(hslToColor(it * 30f % 360f, 1f, 0.5f)) }))
        val x = hue / 360f * size.width
        drawRect(Color.White, Offset(x - 2f, 0f), Size(4f, size.height))
    }
    Spacer(Modifier.height(8.dp))
    // Saturation across, lightness down
    Canvas(Modifier.fillMaxWidth().height(120.dp).clip(R.small)
        .pointerInput(hue) { detectTapGestures { o -> onChange(Color(hslToColor(hue, (o.x / size.width).coerceIn(0f, 1f), 1f - (o.y / size.height).coerceIn(0f, 1f)))) } }
        .pointerInput(hue) { detectDragGestures { ch, _ -> onChange(Color(hslToColor(hue, (ch.position.x / size.width).coerceIn(0f, 1f),
            1f - (ch.position.y / size.height).coerceIn(0f, 1f)))) } }) {
        val cols = 24; val rows = 12
        val cw = size.width / cols; val rh = size.height / rows
        for (i in 0 until cols) for (j in 0 until rows)
            drawRect(Color(hslToColor(hue, (i + 0.5f) / cols, 1f - (j + 0.5f) / rows)), Offset(i * cw, j * rh), Size(cw + 1f, rh + 1f))
        val px = hsl[1] * size.width; val py = (1f - hsl[2]) * size.height
        drawCircle(Color.White, 7f * density, Offset(px, py), style = androidx.compose.ui.graphics.drawscope.Stroke(2f * density))
        drawCircle(Color.Black, 9f * density, Offset(px, py), style = androidx.compose.ui.graphics.drawscope.Stroke(1f * density))
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(R.small).background(value).border(1.dp, C.line, R.small))
        var hex by remember(value) { mutableStateOf("#%06X".format(value.toArgb() and 0xFFFFFF)) }
        BasicTextField(hex, { t ->
            hex = t
            t.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { onChange(Color(0xFF000000L or it)) }
        }, singleLine = true, textStyle = T.body.copy(fontSize = 16.sp), cursorBrush = SolidColor(C.accent),
            modifier = Modifier.padding(start = 12.dp).width(110.dp).clip(R.small).background(C.fg.copy(alpha = 0.07f)).padding(10.dp).typing())
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0xFFFFFFFF, 0xFF000000, 0xFF121212, 0xFF1E1E2A, 0xFFF5F2EA, 0xFFFF2FA8, 0xFF14E4FF, 0xFF1FFF6E, 0xFFFFC21A, 0xFFFF7A1A,
            0xFFFF1F3D, 0xFFA24DFF, 0xFF2F7BFF, 0xFFB6FF1A).forEach { c ->
            Box(Modifier.size(28.dp).clip(R.pill).background(Color(c)).border(1.dp, C.line, R.pill).clickable { onChange(Color(c)) })
        }
    }
}

/** A name to give what you made. */
@Composable
private fun NameField(name: String, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().clip(R.pill).background(C.fg.copy(alpha = 0.07f)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (name.isEmpty()) Text("Its name", style = T.body.copy(color = C.muted))
        BasicTextField(name, { onChange(it.take(40)) }, singleLine = true, textStyle = T.body.copy(fontSize = 16.sp),
            cursorBrush = SolidColor(C.accent), modifier = Modifier.fillMaxWidth().typing())
    }
}

@Composable
private fun Heading(text: String, sub: String = "") {
    Text(text, style = T.title.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
    if (sub.isNotEmpty()) Text(sub, style = T.sub, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Toggle(name: String, sub: String, on: Boolean, set: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(name, style = T.body.copy(fontSize = 15.sp))
            if (sub.isNotEmpty()) Text(sub, style = T.sub)
        }
        Switch(on, set, colors = swColors())
    }
}

@Composable
private fun SliderLine(name: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, set: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = T.body.copy(fontSize = 15.sp), modifier = Modifier.width(110.dp))
        Slider(value, set, valueRange = range, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.fg.copy(alpha = 0.15f)))
        Text(label, style = T.time, modifier = Modifier.width(48.dp).padding(start = 8.dp))
    }
}

// ───────────────────────────── Your own look ─────────────────────────────

/** Make (or change) a look: four colours, an optional second background colour to fade into, neon glow and stars. It's
 *  tried on the whole app as you go, and Save keeps it (or Cancel puts back the look you had). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LookMaker(ui: Ui, editing: String?) {
    val start = MyLooks.all().firstOrNull { it.key == editing }
    val before = remember { Music.look }
    var name by remember { mutableStateOf(start?.name ?: "") }
    var accent by remember { mutableStateOf(start?.accent?.col() ?: C.accent) }
    var bg by remember { mutableStateOf(start?.bg?.col() ?: C.bg) }
    var card by remember { mutableStateOf(start?.card?.col() ?: C.light) }
    var text by remember { mutableStateOf(start?.text?.col() ?: C.fg) }
    var grad by remember { mutableStateOf(start?.grad?.takeIf { it != 0L }?.col()) }
    var glow by remember { mutableStateOf(start?.glow ?: false) }
    var stars by remember { mutableStateOf(start?.stars ?: false) }
    var fx by remember { mutableStateOf(start?.fx ?: emptySet()) }
    var fxShown by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf("accent") }
    fun draft() = Looks.CustomLook(editing ?: "mylook-draft", name.ifBlank { "My look" }, accent.long(), bg.long(), card.long(), text.long(),
        grad?.long() ?: 0L, glow, stars, fx)
    // Tried out live: the draft is kept (under its own key) while you work on it, so the whole app shows it
    fun tryIt() { val d = MyLooks.save(draft()); if (Music.look != d.key) Music.chooseLook(d.key) else Music.applyLook() }
    val close = {
        if (editing == null) MyLooks.delete("mylook-draft")
        Music.chooseLook(before.takeIf { Looks.known(it) } ?: "apple-dark")
        ui.sheet = null
    }
    FullScreen(if (editing == null) "Make your own look" else "Change your look", close, actions = {
        Pill("Save") {
            val d = draft()
            val kept = MyLooks.save(if (editing == null) Looks.CustomLook("new", d.name, d.accent, d.bg, d.card, d.text, d.grad, d.glow, d.stars, d.fx) else d)
            if (editing == null) MyLooks.delete("mylook-draft")
            Music.chooseLook(kept.key)
            Music.toast("Saved “${kept.name}”: it's in the Look menu")
            ui.sheet = null
        }
    }) {
        Column(Modifier.fillMaxSize()) {
        // What it looks like, always in sight: your layout's screens in these colours (with the fade, stars and effects)
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            LayoutPreview(UiLayouts.current, if (onPhone) 210.dp else 300.dp, look = true)
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text("Pick each colour and the whole app shows it as you go. It works with every theme and layout. Glow makes it a " +
                "neon look: the accent glows on what's playing and what's on.", style = T.sub)
            Spacer(Modifier.height(10.dp))
            NameField(name) { name = it }
            Heading("Colours", "Tap one to change it")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("accent" to ("Accent" to accent), "bg" to ("Background" to bg), "card" to ("Cards" to card), "text" to ("Text" to text),
                    "grad" to ("Fade to" to (grad ?: bg))).forEach { (k, v) ->
                    Column(Modifier.clip(R.card).background(if (picking == k) C.accent.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { picking = k; if (k == "grad" && grad == null) { grad = bg; tryIt() } }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(40.dp).clip(R.pill).background(v.second).border(1.dp, C.line, R.pill))
                        Text(v.first, style = T.time.copy(color = if (picking == k) C.accent else C.muted), modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            val now = when (picking) { "bg" -> bg; "card" -> card; "text" -> text; "grad" -> grad ?: bg; else -> accent }
            ColorPicker(now) { c ->
                when (picking) { "bg" -> bg = c; "card" -> card = c; "text" -> text = c; "grad" -> grad = c; else -> accent = c }
                tryIt()
            }
            if (grad != null) Box(Modifier.padding(top = 6.dp)) { Pill("No fade, one background colour", quiet = true) { grad = null; if (picking == "grad") picking = "bg"; tryIt() } }
            // Text must stay readable on the background: a warning when it isn't
            val contrast = contrast(text, bg)
            if (contrast < 4.5f) Text("The text is hard to read on this background (%.1f:1; 4.5:1 or more reads well)".format(contrast),
                style = T.sub.copy(color = C.red), modifier = Modifier.padding(top = 8.dp))
            Heading("Extras")
            Toggle("Neon glow", "What's playing and what's on glows in the accent, as the neon looks do", glow) { glow = it; tryIt() }
            Toggle("Stars", "A few stars over the background", stars) { stars = it; tryIt() }
            Heading("Effects", "Drawn over the background, behind everything else, so they go with every theme and layout. " +
                "Pick as many as you like: each one shows in the whole app as you tap it")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LookFx.ALL.forEach { e ->
                    Chip(e.name, e.key in fx) {
                        fx = if (e.key in fx) fx - e.key else fx + e.key
                        fxShown = e.key; tryIt()
                    }
                }
            }
            (fxShown?.let { k -> LookFx.ALL.firstOrNull { it.key == k } })?.let { e ->
                Text("${e.name}: ${e.desc}", style = T.sub, modifier = Modifier.padding(top = 6.dp))
            }
            if (fx.isNotEmpty()) Box(Modifier.padding(top = 6.dp)) { Pill("No effects", quiet = true) { fx = emptySet(); tryIt() } }
            if (editing != null) Box(Modifier.padding(top = 16.dp)) {
                Pill("Delete this look", quiet = true) { ui.confirm = "Delete “${start?.name}”?" to { MyLooks.delete(editing); ui.sheet = null } }
            }
        }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { tryIt() }
}

/** How well two colours read against each other (1 … 21; 4.5 and up reads well). */
private fun contrast(a: Color, b: Color): Float {
    fun lum(c: Color): Float {
        fun ch(v: Float) = if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        return 0.2126f * ch(c.red) + 0.7152f * ch(c.green) + 0.0722f * ch(c.blue)
    }
    val l1 = lum(a); val l2 = lum(b)
    return (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
}

// ───────────────────────────── Your own layout ─────────────────────────────

/** Make (or change) a layout from slots: the way round (☰ only, bottom bar, side rail, top tabs), which buttons that bar holds
 *  and in what order, the ☰ menu's side, the mini player, Now playing's arrangement and where its tools sit, and titles.
 *  Shown on the app as you go; ☰ is always kept, so nothing can be lost. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutMaker(ui: Ui, editing: String?) {
    val from = UiLayouts.all().firstOrNull { it.key == editing } ?: UiLayouts.current
    val before = remember { org.omarchy.music.Prefs.text(UiLayouts.PREF, UiLayouts.CLASSIC) }
    var l by remember { mutableStateOf(from.copy(name = if (editing != null && editing.startsWith("mine-")) from.name else "",
        key = if (editing != null && editing.startsWith("mine-")) editing else "draft")) }
    val slots = remember { mutableStateListOf<String>().also { it.addAll(from.slots.ifEmpty { listOf("player", "search", "music", "playlists", "menu") }) } }
    fun current() = l.copy(slots = slots.toList())
    // Tried out live: the draft is the layout in use while you work on it
    fun tryIt() { val saved = UiLayouts.save(current().copy(key = if (l.key == "draft") "mine-draft" else l.key, name = l.name.ifBlank { "My layout" })); l = l.copy(key = saved.key) }
    val close = {
        UiLayouts.delete("mine-draft")
        UiLayouts.choose(before.takeIf { k -> UiLayouts.all().any { it.key == k } } ?: UiLayouts.CLASSIC)
        ui.sheet = null
    }
    FullScreen(if (editing?.startsWith("mine-") == true) "Change your layout" else "Make your own layout", close, actions = {
        Pill("Save") {
            val keep = current().copy(key = if (l.key == "mine-draft" || l.key == "draft") "new" else l.key, name = l.name.ifBlank { "My layout" })
            UiLayouts.delete("mine-draft")
            val saved = UiLayouts.save(keep)
            Music.toast("Saved “${saved.name}”: it's in the Layout menu")
            ui.sheet = null
        }
    }) {
        Column(Modifier.fillMaxSize()) {
        // What it looks like, always in sight: the main screen and Now playing, changing with every choice
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            LayoutPreview(current(), if (onPhone) 210.dp else 300.dp, look = true)
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text("Started from ${from.name}. Each choice shows on the app straight away. The ☰ menu is always there (on the bar " +
                "if there is one), so every tab and feature stays a tap or two away.", style = T.sub)
            Spacer(Modifier.height(10.dp))
            NameField(l.name) { l = l.copy(name = it) }
            Heading("Getting around", "How you move between screens")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NavKind.values().forEach { k -> Chip(k.label, l.nav == k) { l = l.copy(nav = k); tryIt() } }
            }
            if (l.nav != NavKind.DRAWER) {
                Heading("The bar's buttons", "Tap one to add it or take it off; ‹ › move it. ☰ is always kept")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    slots.toList().forEachIndexed { i, k ->
                        val s = UiLayouts.SLOTS.firstOrNull { it.key == k } ?: return@forEachIndexed
                        Row(Modifier.clip(R.pill).background(C.accent.copy(alpha = 0.14f)).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (i > 0) Text("‹", style = T.title.copy(color = C.accent), modifier = Modifier.clip(R.pill)
                                .clickable { slots.removeAt(i); slots.add(i - 1, k); tryIt() }.padding(horizontal = 6.dp, vertical = 6.dp))
                            Icon(s.ico, C.accent, 18.dp)
                            Text(s.label, style = T.tab.copy(color = C.fg), modifier = Modifier.padding(horizontal = 6.dp))
                            if (i < slots.size - 1) Text("›", style = T.title.copy(color = C.accent), modifier = Modifier.clip(R.pill)
                                .clickable { slots.removeAt(i); slots.add(i + 1, k); tryIt() }.padding(horizontal = 6.dp, vertical = 6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    UiLayouts.SLOTS.forEach { s ->
                        val on = s.key in slots
                        Chip(s.label, on) {
                            if (s.key == "menu") Music.toast("☰ stays on the bar, so the whole menu is always a tap away")
                            else if (on) slots.remove(s.key)
                            else if (slots.size >= 8 && l.nav == NavKind.BOTTOM) Music.toast("A bottom bar holds up to 8 buttons")
                            else slots.add((slots.indexOf("menu").takeIf { it >= 0 } ?: slots.size), s.key)
                            tryIt()
                        }
                    }
                }
                Toggle("Names under the icons", "Off: icons only", l.labels) { l = l.copy(labels = it); tryIt() }
            } else Toggle("☰ on the right", "The menu's button sits on the right and the menu slides in from the right", l.menuRight) {
                l = l.copy(menuRight = it); tryIt()
            }
            Heading("Mini player", "On the lists, what's playing")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniKind.values().forEach { k -> Chip(k.label, l.mini == k) { l = l.copy(mini = k); tryIt() } }
            }
            Heading("Now playing", l.np.desc)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NpKind.values().forEach { k -> Chip(k.label, l.np == k) { l = l.copy(np = k); tryIt() } }
            }
            Toggle("Tools at the top", "Now playing's lyrics, output and queue buttons above the cover instead of under the controls",
                l.toolsTop) { l = l.copy(toolsTop = it); tryIt() }
            Toggle("Centred titles", "Each screen's name in the middle of its header", l.titleCenter) { l = l.copy(titleCenter = it); tryIt() }
            // More: every finer choice, grouped (the bar, the pages, Now playing's cover and seek bar)
            LayoutExtras.ALL.groupBy { it.group }.forEach { (group, list) ->
                Heading("More: $group")
                list.forEach { e ->
                    if (e.key in setOf("railSide") && l.nav != NavKind.RAIL) return@forEach
                    if (e.key in setOf("barStyle") && l.nav != NavKind.BOTTOM) return@forEach
                    if (e.key in setOf("barFill", "active", "labelsOn", "iconSize") && l.nav !in setOf(NavKind.BOTTOM, NavKind.RAIL)) return@forEach
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(e.name, style = T.body)
                            Text(e.desc, style = T.sub)
                        }
                        ChoicePill(e.options, l.x(e.key)) { v -> l = l.copy(extras = l.extras + (e.key to v)); tryIt() }
                    }
                }
            }
            if (editing?.startsWith("mine-") == true) Box(Modifier.padding(top = 16.dp)) {
                Pill("Delete this layout", quiet = true) { ui.confirm = "Delete “${from.name}”?" to { UiLayouts.delete(editing); ui.sheet = null } }
            }
        }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { tryIt() }
}

// ───────────────────────────── Your own visualizer ─────────────────────────────

/** Make a visualizer style (from a built-in one, drawn your way) or a colour blend, with a live preview of the music. */
@OptIn(ExperimentalLayoutApi::class)
/** Test it with a song: what's playing (play and pause), and Play a song… to find one live, in your music or on a
 *  service, and play it right here, so the preview above moves with it while you shape the visualizer. */
@Composable
private fun TestSong() {
    var open by remember { mutableStateOf(false) }
    var where by remember { mutableStateOf(if (Music.searchIn in org.omarchy.music.SEARCH_IN) Music.searchIn else "local") }
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<org.omarchy.music.Track>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf("") }
    var seq by remember { mutableStateOf(0) }
    // Live: a moment after you stop typing, in the place picked
    LaunchedEffect(q, where) {
        said = ""
        if (q.isBlank()) { hits = emptyList(); busy = false; return@LaunchedEffect }
        kotlinx.coroutines.delay(350)
        val mine = ++seq
        busy = true
        val text = q.trim(); val key = where
        Music.bg({
            if (key == "local") org.omarchy.music.localMatches(Music.mySongs(), text).songs
            else {
                val src = Music.sources[key] ?: error("${org.omarchy.music.SOURCES[key] ?: key} isn't connected yet")
                if (!src.signedIn && key in setOf("tidal", "spotify", "apple", "deezer")) error("connect ${org.omarchy.music.SOURCES[key]} in Settings first")
                src.find(text).songs
            }
        }) { res, err ->
            if (mine != seq) return@bg
            busy = false
            hits = res.orEmpty().take(8)
            said = when { err != null -> "Couldn't search: ${err.message?.take(100)}"; hits.isEmpty() -> "Nothing found"; else -> "" }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        val now = Music.current()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (now != null) IconButton(if (Music.paused) Ico.PLAY else Ico.PAUSE, C.accent, desc = if (Music.paused) "Play" else "Pause") { Music.togglePause() }
            Text(if (now != null) "${now.title} \u00B7 ${now.artist}" else "Play a song to see it move", style = T.sub, maxLines = 1,
                modifier = Modifier.weight(1f))
            Pill(if (open) "Done" else "Play a song\u2026", quiet = open) { open = !open }
        }
        if (open) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoicePill(org.omarchy.music.SEARCH_IN.toList(), where, prefix = "In ") { where = it }
                Box(Modifier.weight(1f)) { Field(q, "A song, an artist\u2026") { q = it } }
            }
            if (busy) Text("Searching\u2026", style = T.sub, modifier = Modifier.padding(top = 4.dp))
            else if (said.isNotEmpty()) Text(said, style = T.sub, modifier = Modifier.padding(top = 4.dp))
            hits.forEachIndexed { i, t ->
                Row(Modifier.fillMaxWidth().clip(R.card).clickable { Music.playFrom(hits, i, radio = false) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    ArtImage(artModel(t), 36.dp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(t.title, style = T.body, maxLines = 1)
                        Text(t.artist, style = T.sub, maxLines = 1)
                    }
                    if (now?.id == t.id) Text("Playing", style = T.sub.copy(color = C.accent))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisMaker(ui: Ui, editing: String?) {
    val startStyle = editing?.let { MyVis.style(it) }
    val startColor = editing?.let { MyVis.color(it) }
    var what by remember { mutableStateOf(if (startColor != null) "color" else "style") }
    var name by remember { mutableStateOf(startStyle?.name ?: startColor?.name ?: "") }
    // A style
    var base by remember { mutableStateOf(startStyle?.base ?: Vis.style().takeIf { !it.startsWith("myvis-") } ?: "bars") }
    var mirror by remember { mutableStateOf(startStyle?.mirror ?: false) }
    var flip by remember { mutableStateOf(startStyle?.flip ?: false) }
    var reflect by remember { mutableStateOf(startStyle?.reflect ?: false) }
    var echo by remember { mutableStateOf(startStyle?.echo ?: false) }
    var spin by remember { mutableStateOf(startStyle?.spin ?: 0f) }
    var amp by remember { mutableStateOf(startStyle?.amp ?: 1f) }
    var ownColor by remember { mutableStateOf(startStyle?.color ?: "") }
    // A colour blend
    val stops = remember { mutableStateListOf<Color>().also { l -> l.addAll(startColor?.stops?.map { it.col() } ?: listOf(Color(0xFF7C4DFF), Color(0xFF19F0FF))) } }
    var spread by remember { mutableStateOf(startColor?.mode ?: MyVis.SPREAD_ALONG) }
    var glow by remember { mutableStateOf(startColor?.glow ?: false) }
    var picking by remember { mutableStateOf(0) }

    val draftStyle = MyVisStyle("myvis-preview", name, base, mirror, flip, reflect, spin, amp, echo, ownColor)
    val draftColor = MyVisColor("mycol-preview", name, stops.map { it.long() }, spread, glow)
    FullScreen(if (editing != null) "Change your visualizer" else "Make your own visualizer", { ui.sheet = null }, actions = {
        Pill("Save") {
            val n = name.ifBlank { if (what == "style") "My style" else "My colours" }
            if (what == "style") {
                val kept = MyVis.saveStyle(draftStyle.copy(key = editing?.takeIf { it.startsWith("myvis-") } ?: "new", name = n))
                Music.chooseVisPreset(kept.key)
                Music.toast("Saved “${kept.name}”: it's with the Visualizer's styles, and Now playing's")
            } else {
                val kept = MyVis.saveColor(draftColor.copy(key = editing?.takeIf { it.startsWith("mycol-") } ?: "new", name = n))
                Prefs.setText(Vis.COLOR, kept.key)
                Music.toast("Saved “${kept.name}”: it's in the Visualizer's colours")
            }
            ui.sheet = null
        }
    }) {
        val s = rememberVis(true) ?: remember { VisState() }
        Column(Modifier.fillMaxSize()) {
            // The preview, moving with what's playing
            val accent = C.accent; val fg = C.fg
            Canvas(Modifier.fillMaxWidth().height(if (onPhone) 280.dp else 360.dp).padding(horizontal = 16.dp).clip(R.big).background(Color(0xFF0B0B10))) {
                if (s.frame < 0) return@Canvas
                if (what == "style") {
                    val key = ownColor.ifEmpty { Vis.color() }
                    clipRect { drawVisPreviewStyle(draftStyle, s, VisPalette(key, accent, s.phase * 0.05f), fg) }
                } else {
                    val pal = previewPalette(draftColor, accent)
                    clipRect { drawVis(Vis.style().takeIf { !it.startsWith("myvis-") } ?: "bars", s, pal, fg, backdrop = false) }
                }
            }
            TestSong()   // play any song here (your music or a service) to see the visualizer move with it
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
                if (editing == null) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("A style", what == "style") { what = "style" }
                    Chip("A colour blend", what == "color") { what = "color" }
                }
                Spacer(Modifier.height(10.dp))
                NameField(name) { name = it }
                if (what == "style") {
                    Heading("Start from", "The built-in style it's drawn from")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        VIS_BASES.forEach { (k, label) -> Chip(label, base == k) { base = k } }
                    }
                    Heading("Shape it")
                    Toggle("Mirror", "Its left half mirrored onto the right", mirror) { mirror = it }
                    Toggle("Upside down", "Drawn from the top down", flip) { flip = it }
                    Toggle("Reflection", "A faint reflection of it, upside down", reflect) { reflect = it }
                    Toggle("Glow", "A soft halo around everything it draws", echo) { echo = it }
                    SliderLine("Spin", spin, 0f..1f, if (spin < 0.05f) "off" else "%.0f%%".format(spin * 100)) { spin = it }
                    SliderLine("Strength", amp, 0.5f..2f, "%.1f×".format(amp)) { amp = it }
                    Heading("Its colour", "Its own, or whatever the Visualizer's colour is")
                    val acc = C.accent
                    ChoicePill(listOf("" to "The Visualizer's colour") + VIS_COLORS, ownColor, swatch = { k -> if (k.isEmpty()) null else visSwatch(k, acc) }) { ownColor = it }
                } else {
                    Heading("Its colours", "2 to 5, from the bass to the treble (or quiet to loud). Tap one to change it")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        stops.forEachIndexed { i, c ->
                            Box(Modifier.size(40.dp).clip(R.pill).background(c)
                                .border(if (picking == i) 3.dp else 1.dp, if (picking == i) C.accent else C.line, R.pill).clickable { picking = i })
                        }
                        if (stops.size < 5) IconButton(Ico.PLUS, C.accent, 20.dp, desc = "Add a colour") { stops.add(stops.last()); picking = stops.lastIndex }
                        if (stops.size > 2) IconButton(Ico.TRASH, C.muted, 18.dp, desc = "Take this colour off") {
                            stops.removeAt(picking.coerceIn(0, stops.lastIndex)); picking = picking.coerceIn(0, stops.lastIndex) }
                    }
                    Spacer(Modifier.height(10.dp))
                    ColorPicker(stops[picking.coerceIn(0, stops.lastIndex)]) { stops[picking.coerceIn(0, stops.lastIndex)] = it }
                    Heading("How they spread")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Bass to treble", spread == MyVis.SPREAD_ALONG) { spread = MyVis.SPREAD_ALONG }
                        Chip("Quiet to loud", spread == MyVis.SPREAD_LOUD) { spread = MyVis.SPREAD_LOUD }
                        Chip("Stripes", spread == MyVis.SPREAD_STRIPES) { spread = MyVis.SPREAD_STRIPES }
                    }
                    Toggle("Neon glow", "The loud parts burn white-hot, as the neon colours do", glow) { glow = it }
                }
                if (editing != null) Box(Modifier.padding(top = 16.dp)) {
                    Pill("Delete it", quiet = true) {
                        ui.confirm = "Delete “$name”?" to { if (startStyle != null) MyVis.deleteStyle(editing) else MyVis.deleteColor(editing); ui.sheet = null }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── In Settings › Personalization ─────────────────────────────

/** The makers' lines: make your own look, layout or visualizer, and change or delete the ones you made. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MakerLines(ui: Ui) {
    Heading("Make your own", "Looks, layouts and visualizers of your own, kept with your settings (backups and Sync carry them)")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("New look") { ui.makerEdit = null; ui.sheet = "lookmaker" }
        Pill("New layout") { ui.makerEdit = null; ui.sheet = "layoutmaker" }
        Pill("New visualizer") { ui.makerEdit = null; ui.sheet = "vismaker" }
    }
    val mine = MyLooks.all().filter { it.key != "mylook-draft" }.map { "lookmaker" to (it.key to it.name) } +
        UiLayouts.mine().filter { it.key != "mine-draft" }.map { "layoutmaker" to (it.key to it.name) } +
        MyVis.styles().map { "vismaker" to (it.key to it.name) } + MyVis.colors().map { "vismaker" to (it.key to it.name) }
    if (mine.isNotEmpty()) {
        Text("Yours (tap one to change it)", style = T.sub, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            mine.forEach { (sheet, kn) ->
                val kind = when (sheet) { "lookmaker" -> "Look"; "layoutmaker" -> "Layout"; else -> "Visualizer" }
                Chip("$kind: ${kn.second}", false) { ui.makerEdit = kn.first; ui.sheet = sheet }
            }
        }
    }
}

/** The preview's palette for a blend that isn't saved yet. */
private fun previewPalette(c: MyVisColor, accent: Color): VisPalette {
    PreviewVis.color = c
    return VisPalette(c.key, accent, 0f)
}

/** The maker's unsaved style or blend, for its preview. */
object PreviewVis {
    var color: MyVisColor? = null
    var style: MyVisStyle? = null
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVisPreviewStyle(d: MyVisStyle, s: VisState, pal: VisPalette, fg: Color) {
    PreviewVis.style = d
    drawVis(d.key, s, pal, fg, backdrop = false)
}
