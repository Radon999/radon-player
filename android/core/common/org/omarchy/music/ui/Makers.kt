package org.omarchy.music.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.graphicsLayer
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
        MadeSync.forget(key)
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

/** A random colour that looks good as a start: vivid, or (dark) for a background. */
fun randomColor(r: kotlin.random.Random = kotlin.random.Random, dark: Boolean = false): Color =
    if (dark) Color.hsv(r.nextFloat() * 360f, 0.3f + r.nextFloat() * 0.5f, 0.06f + r.nextFloat() * 0.14f)
    else Color.hsv(r.nextFloat() * 360f, 0.55f + r.nextFloat() * 0.45f, 0.7f + r.nextFloat() * 0.3f)

/** Each effect that's on, with its colour: tap one to change its colour with the picker, Look's colours to put its usual
 *  colours back, Randomize colours for a random colour on each. fx: the effects (with their colours, see LookFx). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FxColors(fx: Set<String>, onChange: (Set<String>) -> Unit) {
    val on = LookFx.ALL.filter { LookFx.has(fx, it.key) }
    if (on.isEmpty()) return
    val cols = LookFx.colors(fx)
    var editing by remember { mutableStateOf<String?>(null) }
    val accent = C.accent; val fg = C.fg
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Effects' colours: tap one to change its colour", style = T.sub)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            on.forEach { e ->
                val c = cols[e.key] ?: LookFx.defaultColor(e.key, accent, fg)
                Row(Modifier.clip(R.pill).background(if (editing == e.key) C.accent.copy(alpha = 0.16f) else C.fg.copy(alpha = 0.05f))
                    .clickable { editing = if (editing == e.key) null else e.key }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(R.pill).background(c).border(1.dp, C.line, R.pill))
                    Text(e.name + if (cols.containsKey(e.key)) "" else " (look's)", style = T.tab.copy(color = if (editing == e.key) C.accent else C.fg),
                        modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Randomize colours", quiet = true) {
                val r = kotlin.random.Random
                var next = fx
                on.forEach { e -> next = LookFx.withColor(next, e.key, randomColor(r)) }
                onChange(next)
            }
            if (cols.isNotEmpty()) Pill("Look's colours", quiet = true) {
                var next = fx; on.forEach { e -> next = LookFx.withColor(next, e.key, null) }; editing = null; onChange(next)
            }
        }
        editing?.takeIf { k -> on.any { it.key == k } }?.let { k ->
            Spacer(Modifier.height(8.dp))
            ColorPicker(cols[k] ?: LookFx.defaultColor(k, accent, fg)) { c -> onChange(LookFx.withColor(fx, k, c)) }
            if (cols.containsKey(k)) Box(Modifier.padding(top = 6.dp)) { Pill("Back to the look's colour", quiet = true) { onChange(LookFx.withColor(fx, k, null)) } }
        }
    }
}

/** Colours you saved, and ready-made sets, to pull into a maker: LOOK (accent, background, cards, text, then a fade) or
 *  BLEND (a visualizer's colour blend, 2 to 5). Yours can be deleted; the ready-made ones can't. Any can be a favourite. */
object SavedColours {
    const val LOOK = "saved_look_colours"; const val BLEND = "saved_blend_colours"
    class Set4(val key: String, val name: String, val colors: List<Long>, val mine: Boolean)
    private fun c(vararg v: Long) = v.toList()
    private val LOOK_PRESETS = listOf(
        "Midnight neon" to c(0xFFFF2FA8, 0xFF0B0B1A, 0xFF17172E, 0xFFF2F0FF),
        "Ocean" to c(0xFF2FD5FF, 0xFF06202E, 0xFF0D3346, 0xFFE6F8FF),
        "Forest" to c(0xFF7CDB57, 0xFF0E1A10, 0xFF19291B, 0xFFEAF5E6),
        "Sunset" to c(0xFFFF7A3D, 0xFF24101A, 0xFF371A27, 0xFFFFEFE6),
        "Lavender" to c(0xFF9B6BFF, 0xFFF4F0FF, 0xFFE8E0FF, 0xFF221A38),
        "Mint cream" to c(0xFF14B88A, 0xFFF1FBF6, 0xFFE0F4EA, 0xFF10261D),
        "Gold and black" to c(0xFFFFC21A, 0xFF0D0D0D, 0xFF1C1C1C, 0xFFFFF6DE),
        "Cherry" to c(0xFFFF1F3D, 0xFF180508, 0xFF2A0C12, 0xFFFFE8EB),
        "Arctic" to c(0xFF2F7BFF, 0xFFF3F7FC, 0xFFE3ECF7, 0xFF0F1B2D),
        "Synthwave" to c(0xFFFF4FD8, 0xFF1A0B2E, 0xFF2A1446, 0xFF7CF7FF),
        "Coffee" to c(0xFFD9A066, 0xFF1E1510, 0xFF2E221A, 0xFFF6EBDD),
        "Matrix" to c(0xFF1FFF6E, 0xFF020A04, 0xFF0A180D, 0xFFCCFFDA),
        "Peach" to c(0xFFFF8A65, 0xFFFFF5EF, 0xFFFFE6DA, 0xFF3A1F14),
        "Slate" to c(0xFF8FA8C8, 0xFF1B2028, 0xFF262D38, 0xFFE8EDF3),
        "Bubblegum" to c(0xFFFF6FB5, 0xFFFFF0F7, 0xFFFFDDEC, 0xFF3A1027),
    )
    private val BLEND_PRESETS = listOf(
        "Fire" to c(0xFFFFE14D, 0xFFFF8A1F, 0xFFFF3B1F, 0xFF9E0B2B),
        "Ice" to c(0xFFFFFFFF, 0xFFA6EBFF, 0xFF3FA9FF, 0xFF1E3DFF),
        "Vaporwave" to c(0xFF7CF7FF, 0xFFB98CFF, 0xFFFF6FD8, 0xFFFF9F6B),
        "Jungle" to c(0xFFE6FF5C, 0xFF7CDB57, 0xFF1FB86A, 0xFF0B6B4A),
        "Sunset" to c(0xFFFFD36B, 0xFFFF8A65, 0xFFE8467C, 0xFF6B2FA8),
        "Ocean" to c(0xFF9FFFF0, 0xFF2FD5FF, 0xFF1F7BFF, 0xFF13307A),
        "Candy" to c(0xFFFF9EC7, 0xFFFFD1E8, 0xFFB5F2FF, 0xFF8AE3FF),
        "Toxic" to c(0xFFB6FF1A, 0xFF1FFF6E, 0xFF14E4FF, 0xFFA24DFF),
        "Royal" to c(0xFFFFC21A, 0xFFFFE9A3, 0xFF8C5BFF, 0xFF3A1A8C),
        "Ember" to c(0xFFFFB25C, 0xFFFF5D3D, 0xFFB81F3D, 0xFF3D0B1A),
        "Aurora" to c(0xFF3CFFB4, 0xFF2FD5FF, 0xFF7C4DFF, 0xFFFF4FD8),
        "Neon" to c(0xFFFF2FA8, 0xFF14E4FF, 0xFF1FFF6E, 0xFFFFC21A),
        "Rose gold" to c(0xFFFFE0D1, 0xFFF4B6A0, 0xFFD98C8C, 0xFF9E5A6B),
        "Deep space" to c(0xFFFFFFFF, 0xFF8CA2FF, 0xFF4B3DCC, 0xFF150F4D),
        "Citrus" to c(0xFFFFF35C, 0xFFFFC21A, 0xFFFF8A1F, 0xFF7CDB57),
    )
    fun presets(kind: String) = (if (kind == LOOK) LOOK_PRESETS else BLEND_PRESETS).mapIndexed { i, (n, cs) -> Set4("preset-$kind-$i", n, cs, false) }
    fun mine(kind: String): List<Set4> = Prefs.text(kind).lines().mapNotNull { l -> runCatching { val p = l.split("|")
        Set4(p[0], p[1], p[2].split(",").filter { it.isNotBlank() }.map { it.toLong(16) }, true).takeIf { it.colors.size >= 2 } }.getOrNull() }
    private fun favKind(kind: String) = if (kind == LOOK) Favs.LOOK_COLOURS else Favs.BLEND_COLOURS
    /** Favourites first, then yours (newest first), then the ready-made ones. */
    fun all(kind: String): List<Set4> {
        val f = Favs.of(favKind(kind))
        val every = mine(kind).reversed() + presets(kind)
        return every.filter { it.key in f } + every.filter { it.key !in f }
    }
    fun save(kind: String, colors: List<Color>): Set4 {
        val n = mine(kind).size + 1
        val s = Set4("cols-" + System.currentTimeMillis().toString(36), "My colours $n", colors.map { it.long() }, true)
        Prefs.setText(kind, (mine(kind) + s).joinToString("\n") { encode(it) })
        return s
    }
    fun delete(kind: String, key: String) {
        MadeSync.forget(key)
        Prefs.setText(kind, mine(kind).filter { it.key != key }.joinToString("\n") { encode(it) })
        val fk = favKind(kind); if (Favs.isFav(fk, key)) Prefs.setText(fk, (Favs.of(fk) - key).joinToString(";"))
    }
    /** A new name for one of yours. */
    fun rename(kind: String, key: String, name: String) {
        val n = name.trim().take(40).ifEmpty { return }
        Prefs.setText(kind, mine(kind).joinToString("\n") { encode(if (it.key == key) Set4(it.key, n, it.colors, true) else it) })
    }
    fun favKindOf(kind: String) = favKind(kind)
    private fun encode(s: Set4) = listOf(s.key, s.name.replace('|', '/'), s.colors.joinToString(",") { it.toString(16) }).joinToString("|")
}

/** Save these colours, and the Saved colours menu: yours and the ready-made sets, each shown as its colours. Tap one to
 *  use it; ★ to make it a favourite (to the top); 🗑 deletes one of yours. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.holdOrTap(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    combinedClickable(onClick = onClick, onLongClick = onLongClick)

@Composable
fun SavedColoursBar(kind: String, current: () -> List<Color>, use: (List<Color>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var seen by remember { mutableStateOf(0) }   // redraws the list after a change
    Row(Modifier.padding(top = 4.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            Pill("Saved colours \u25BE", quiet = true) { open = true }
            val body: @Composable ColumnScope.() -> Unit = {
                @Suppress("UNUSED_EXPRESSION") seen
                val fk = SavedColours.favKindOf(kind)
                SavedColours.all(kind).forEach { s ->
                    val fav = Favs.isFav(fk, s.key)
                    // Tap to use it; hold one of yours (or right-click it on the PC, or its ✎) to rename it
                    val rename = { open = false; Ui.current?.askName = Triple("Rename these colours", s.name) { n: String ->
                        SavedColours.rename(kind, s.key, n); Music.toast("Renamed “${n.trim()}”") }; Unit }
                    Box(Modifier.fillMaxWidth().holdOrTap(onClick = { open = false; use(s.colors.map { Color(it) }) },
                        onLongClick = { if (s.mine) rename() }).padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.clip(R.small).border(1.dp, C.line, R.small)) {
                                s.colors.forEach { c -> Box(Modifier.size(width = 18.dp, height = 22.dp).background(Color(c))) }
                            }
                            Text((if (fav) "★ " else "") + s.name + if (s.mine) " (yours)" else "", style = T.body, modifier = Modifier.padding(start = 10.dp).weight(1f))
                            Text(if (fav) "★" else "☆", style = T.title.copy(color = if (fav) C.accent else C.muted),
                                modifier = Modifier.clip(R.pill).clickable { Favs.toggle(fk, s.key); seen++ }.padding(horizontal = 8.dp, vertical = 4.dp))
                            if (s.mine) Text("\u270E", style = T.title.copy(color = C.muted),
                                modifier = Modifier.clip(R.pill).clickable { rename() }.padding(horizontal = 8.dp, vertical = 4.dp))
                            if (s.mine) Box(Modifier.clip(R.pill).clickable { SavedColours.delete(kind, s.key); Music.toast("Deleted “${s.name}”"); seen++ }
                                .padding(6.dp)) { Icon(Ico.TRASH, C.muted, 16.dp) }
                        }
                    }
                }
            }
            // The phone: in a box that ends above the phone's buttons (it scrolls inside); the PC: a dropdown
            if (onPhone) { if (open) androidx.compose.ui.window.Dialog(onDismissRequest = { open = false }) {
                Column(Modifier.fillMaxWidth().heightIn(max = (screenHeightDp() * 0.82f).dp).clip(R.big).background(C.light)
                    .verticalScroll(rememberScrollState()).padding(vertical = 8.dp), content = body) } }
            else DropdownMenu(open, { open = false }, containerColor = C.light, content = body)
        }
        Pill("Save these colours", quiet = true) {
            val s = SavedColours.save(kind, current())
            // Name them straight away (the name it got stays if you just close the box)
            Ui.current?.askName = Triple("Name these colours", s.name) { n: String ->
                SavedColours.rename(kind, s.key, n); Music.toast("Saved as “${n.trim().ifEmpty { s.name }}”: it's in Saved colours") }
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
            // A random look to start from: every colour, the fade, the glow, the stars and the effects (all still yours to change)
            Box(Modifier.padding(bottom = 8.dp)) { Pill("Randomize") {
                val r = kotlin.random.Random
                val dark = r.nextFloat() < 0.75f
                val bgHue = r.nextFloat() * 360f
                bg = if (dark) Color.hsv(bgHue, 0.3f + r.nextFloat() * 0.5f, 0.06f + r.nextFloat() * 0.12f)
                    else Color.hsv(bgHue, 0.04f + r.nextFloat() * 0.2f, 0.9f + r.nextFloat() * 0.08f)
                card = androidx.compose.ui.graphics.lerp(bg, if (dark) Color.White else Color.Black, 0.05f + r.nextFloat() * 0.08f)
                text = if (dark) Color.hsv(bgHue, 0.06f, 0.95f) else Color.hsv(bgHue, 0.15f, 0.12f)
                accent = Color.hsv(r.nextFloat() * 360f, 0.6f + r.nextFloat() * 0.4f, if (dark) 0.85f + r.nextFloat() * 0.15f else 0.5f + r.nextFloat() * 0.25f)
                grad = if (r.nextBoolean()) (if (dark) Color.hsv(r.nextFloat() * 360f, 0.4f + r.nextFloat() * 0.5f, 0.1f + r.nextFloat() * 0.25f)
                    else Color.hsv(r.nextFloat() * 360f, 0.1f + r.nextFloat() * 0.25f, 0.88f + r.nextFloat() * 0.1f)) else null
                glow = dark && r.nextFloat() < 0.4f
                stars = dark && r.nextFloat() < 0.35f
                fx = LookFx.ALL.shuffled(r).take(r.nextInt(0, 4)).map { it.key }.toSet()
                fxShown = null
                tryIt()
            } }
            NameField(name) { name = it }
            Heading("Colours", "Tap one to change it")
            Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Only the colours, at random (still readable): something to build on
                Pill("Randomize colours", quiet = true) {
                    val r = kotlin.random.Random
                    val dark = if (r.nextFloat() < 0.5f) contrast(Color.White, bg) > 4.5f else r.nextFloat() < 0.7f
                    val bgHue = r.nextFloat() * 360f
                    bg = if (dark) Color.hsv(bgHue, 0.3f + r.nextFloat() * 0.5f, 0.06f + r.nextFloat() * 0.12f)
                        else Color.hsv(bgHue, 0.04f + r.nextFloat() * 0.2f, 0.9f + r.nextFloat() * 0.08f)
                    card = androidx.compose.ui.graphics.lerp(bg, if (dark) Color.White else Color.Black, 0.05f + r.nextFloat() * 0.08f)
                    text = if (dark) Color.hsv(bgHue, 0.06f, 0.95f) else Color.hsv(bgHue, 0.15f, 0.12f)
                    accent = Color.hsv(r.nextFloat() * 360f, 0.6f + r.nextFloat() * 0.4f, if (dark) 0.85f + r.nextFloat() * 0.15f else 0.5f + r.nextFloat() * 0.25f)
                    if (grad != null) grad = if (dark) Color.hsv(r.nextFloat() * 360f, 0.4f + r.nextFloat() * 0.5f, 0.1f + r.nextFloat() * 0.25f)
                        else Color.hsv(r.nextFloat() * 360f, 0.1f + r.nextFloat() * 0.25f, 0.88f + r.nextFloat() * 0.1f)
                    tryIt()
                }
            }
            // Saved colours: accent, background, cards, text (and the fade, if there is one)
            SavedColoursBar(SavedColours.LOOK, { listOfNotNull(accent, bg, card, text, grad) }) { cs ->
                accent = cs[0]; bg = cs.getOrElse(1) { bg }; card = cs.getOrElse(2) { card }; text = cs.getOrElse(3) { text }
                grad = cs.getOrNull(4)
                tryIt()
            }
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
                    Chip(e.name, LookFx.has(fx, e.key)) {
                        fx = LookFx.flip(fx, e.key)
                        fxShown = e.key; tryIt()
                    }
                }
            }
            (fxShown?.let { k -> LookFx.ALL.firstOrNull { it.key == k } })?.let { e ->
                Text("${e.name}: ${e.desc}", style = T.sub, modifier = Modifier.padding(top = 6.dp))
            }
            // Each effect that's on: its own colour, if you like
            FxColors(fx) { fx = it; tryIt() }
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
            // A random layout to start from: the way round, the bar's buttons, the mini player, Now playing and every More choice
            Box(Modifier.padding(bottom = 8.dp)) { Pill("Randomize") {
                val r = kotlin.random.Random
                val pick = UiLayouts.SLOTS.map { it.key }.filter { it != "menu" }.shuffled(r).take(r.nextInt(3, 7))
                slots.clear(); slots.addAll(pick + "menu")
                l = l.copy(nav = NavKind.values().random(r), menuRight = r.nextBoolean(), labels = r.nextBoolean(),
                    mini = MiniKind.values().random(r), np = NpKind.values().random(r), toolsTop = r.nextBoolean(),
                    titleCenter = r.nextBoolean(), extras = LayoutExtras.ALL.associate { it.key to it.options.random(r).first })
                tryIt()
            } }
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
    var full by remember { mutableStateOf(startStyle?.full ?: false) }
    var dual by remember { mutableStateOf(startStyle?.dual ?: false) }
    var base2 by remember { mutableStateOf(startStyle?.base2 ?: "bars") }
    // Dual: the bottom one's own shape and colour
    var mirror2 by remember { mutableStateOf(startStyle?.mirror2 ?: false) }
    var flip2 by remember { mutableStateOf(startStyle?.flip2 ?: false) }
    var reflect2 by remember { mutableStateOf(startStyle?.reflect2 ?: false) }
    var echo2 by remember { mutableStateOf(startStyle?.echo2 ?: false) }
    var spin2 by remember { mutableStateOf(startStyle?.spin2 ?: 0f) }
    var amp2 by remember { mutableStateOf(startStyle?.amp2 ?: 1f) }
    var color2 by remember { mutableStateOf(startStyle?.color2 ?: "") }
    // A colour blend
    val stops = remember { mutableStateListOf<Color>().also { l -> l.addAll(startColor?.stops?.map { it.col() } ?: listOf(Color(0xFF7C4DFF), Color(0xFF19F0FF))) } }
    var spread by remember { mutableStateOf(startColor?.mode ?: MyVis.SPREAD_ALONG) }
    var glow by remember { mutableStateOf(startColor?.glow ?: false) }
    var picking by remember { mutableStateOf(0) }

    val draftStyle = MyVisStyle("myvis-preview", name, base, mirror, flip, reflect, spin, amp, echo, ownColor, full, dual, base2,
        mirror2, flip2, reflect2, spin2, amp2, echo2, color2)
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
            Canvas(Modifier.fillMaxWidth().height(if (onPhone) 380.dp else 480.dp).padding(horizontal = 16.dp).clip(R.big).background(Color(0xFF0B0B10))) {
                if (s.frame < 0) return@Canvas
                if (what == "style") {
                    val key = ownColor.ifEmpty { Vis.color() }
                    if (full) drawCoverSpot()   // where the cover sits on Now playing: the visualizer goes over it
                    clipRect { drawVisPreviewStyle(draftStyle, s, VisPalette(key, accent, s.phase * 0.05f), fg) }
                } else {
                    // The style you're making (from the style tab), in the colours you're making: both tried together
                    val pal = previewPalette(draftColor, accent)
                    clipRect { drawVisPreviewStyle(draftStyle, s, pal, fg) }
                }
            }
            Spacer(Modifier.height(12.dp))   // a little room under the preview
            TestSong()   // play any song here (your music or a service) to see the visualizer move with it
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
                if (editing == null) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("A style", what == "style") { what = "style" }
                    Chip("A colour blend", what == "color") { what = "color" }
                }
                Spacer(Modifier.height(10.dp))
                // A random one to start from: the style and every way of shaping it, or the colours and how they spread
                Box(Modifier.padding(bottom = 8.dp)) { Pill("Randomize") {
                    val r = kotlin.random.Random
                    if (what == "style") {
                        dual = r.nextFloat() < 0.3f
                        base = if (dual) VIS_BASES.filter { it.first in Vis.BUILT_CENTRED }.random(r).first else VIS_BASES.random(r).first
                        base2 = VIS_BASES.filter { it.first !in Vis.BUILT_CENTRED }.random(r).first
                        mirror = r.nextBoolean(); flip = r.nextFloat() < 0.3f; reflect = r.nextBoolean(); echo = r.nextBoolean()
                        spin = if (r.nextBoolean()) 0f else 0.1f + r.nextFloat() * 0.9f
                        amp = 0.7f + r.nextFloat() * 0.9f
                        ownColor = (listOf("") + VIS_COLORS.map { it.first }).random(r)
                        full = r.nextFloat() < 0.3f
                        mirror2 = r.nextBoolean(); flip2 = r.nextFloat() < 0.2f; reflect2 = r.nextBoolean(); echo2 = r.nextBoolean()
                        spin2 = 0f; amp2 = 0.7f + r.nextFloat() * 0.9f
                        color2 = (listOf("") + VIS_COLORS.map { it.first }).random(r)
                    } else {
                        stops.clear()
                        repeat(r.nextInt(2, 6)) { stops.add(Color.hsv(r.nextFloat() * 360f, 0.55f + r.nextFloat() * 0.45f, 0.75f + r.nextFloat() * 0.25f)) }
                        picking = 0
                        spread = listOf(MyVis.SPREAD_ALONG, MyVis.SPREAD_LOUD, MyVis.SPREAD_STRIPES).random(r)
                        glow = r.nextBoolean()
                    }
                } }
                NameField(name) { name = it }
                if (what == "style") {
                    Toggle("Dual", "Two at once: one in the middle (over the cover on Now playing) and one along the bottom", dual) { on ->
                        dual = on
                        if (on && base !in Vis.BUILT_CENTRED) base = "ring"   // the middle one is one of the middle styles
                    }
                    Heading("Start from", if (dual) "Off while Dual is on: pick the two below" else "The built-in style it's drawn from")
                    // Greyed out (and not tappable) while Dual is on
                    FlowRow(Modifier.graphicsLayer(alpha = if (dual) 0.35f else 1f), horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        VIS_BASES.forEach { (k, label) -> Chip(label, !dual && base == k) { if (!dual) base = k } }
                    }
                    if (dual) {
                        Heading("In the middle", "The styles drawn around the middle of the screen")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            VIS_BASES.filter { it.first in Vis.BUILT_CENTRED }.forEach { (k, label) -> Chip(label, base == k) { base = k } }
                        }
                        Heading("Along the bottom", "The styles that stand on the bottom of the screen")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            VIS_BASES.filter { it.first !in Vis.BUILT_CENTRED }.forEach { (k, label) -> Chip(label, base2 == k) { base2 = k } }
                        }
                    }
                    Heading(if (dual) "Shape it: the middle one" else "Shape it")
                    Toggle("Mirror", "Its left half mirrored onto the right", mirror) { mirror = it }
                    Toggle("Upside down", "Drawn from the top down", flip) { flip = it }
                    Toggle("Reflection", "A faint reflection of it, upside down", reflect) { reflect = it }
                    Toggle("Glow", "A soft halo around everything it draws", echo) { echo = it }
                    Toggle("Full screen on Now playing", if (full) "Now playing shows it over the whole screen, cover and all, just as " +
                        "the Visualizer tab does (the square in the preview is the cover)" else "Now playing shows it " +
                        (if (dual) "over the cover and along the bottom" else if (base in VIS_CENTRED_BASES) "around the cover" else "faintly along the bottom") + "; on, it fills the whole " +
                        "screen over the cover, as in the Visualizer tab", full) { full = it }
                    SliderLine("Spin", spin, 0f..1f, if (spin < 0.05f) "off" else "%.0f%%".format(spin * 100)) { spin = it }
                    SliderLine("Strength", amp, 0.5f..2f, "%.1f×".format(amp)) { amp = it }
                    Heading(if (dual) "Its colour: the middle one" else "Its colour", "Its own, or whatever the Visualizer's colour is")
                    val acc = C.accent
                    ChoicePill(listOf("" to "The Visualizer's colour") + VIS_COLORS, ownColor, swatch = { k -> if (k.isEmpty()) null else visSwatch(k, acc) }) { ownColor = it }
                    if (dual) {
                        // The bottom one, shaped and coloured on its own
                        Heading("Shape it: the bottom one")
                        Toggle("Mirror", "Its left half mirrored onto the right", mirror2) { mirror2 = it }
                        Toggle("Upside down", "Drawn from the top down", flip2) { flip2 = it }
                        Toggle("Reflection", "A faint reflection of it, upside down", reflect2) { reflect2 = it }
                        Toggle("Glow", "A soft halo around everything it draws", echo2) { echo2 = it }
                        SliderLine("Spin", spin2, 0f..1f, if (spin2 < 0.05f) "off" else "%.0f%%".format(spin2 * 100)) { spin2 = it }
                        SliderLine("Strength", amp2, 0.5f..2f, "%.1f×".format(amp2)) { amp2 = it }
                        Heading("Its colour: the bottom one", "Its own, or the same as the middle one")
                        ChoicePill(listOf("" to "Same as the middle one") + VIS_COLORS, color2, swatch = { k -> if (k.isEmpty()) null else visSwatch(k, acc) }) { color2 = it }
                    }
                } else {
                    Heading("Its colours", "2 to 5, from the bass to the treble (or quiet to loud). Tap one to change it")
                    Row(Modifier.padding(bottom = 4.dp)) {
                        Pill("Randomize colours", quiet = true) {   // the same number of colours, each at random
                            val r = kotlin.random.Random
                            for (i in stops.indices) stops[i] = randomColor(r)
                        }
                    }
                    SavedColoursBar(SavedColours.BLEND, { stops.toList() }) { cs ->
                        stops.clear(); stops.addAll(cs.take(5)); if (stops.size < 2) stops.add(stops.firstOrNull() ?: Color.White)
                        picking = 0
                    }
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
        Pill("New Wrapped") { ui.makerEdit = null; ui.sheet = "wrapmaker" }
        Pill("New lyrics card") { ui.makerEdit = null; ui.sheet = "cardmaker" }
    }
    Text("What you make is in its own menu (Look, Layout, the visualizer menus, Wrapped's Style, the Lyrics card's Style): hold it or tap its \u22EF " +
        "(right-click on the PC) to edit, favourite or delete it", style = T.sub, modifier = Modifier.padding(top = 10.dp))
}

// ───────────────────────────── Personalization presets ─────────────────────────────

/** Your Personalization presets: everything picked on the Personalization tab at once (look, theme, layout, the looks'
 *  own effects or the effects you picked and their colours, the Now playing look while nothing plays and Now playing's
 *  visualizer), saved under a name and put back with one tap. Kept with the app's settings, one a line
 *  (key|name|look|theme|layout|own|effects|np fallback|np visualizer), so backups and Sync carry them. */
object MyPresets {
    const val PREF = "my_presets"
    class Preset(val key: String, val name: String, val look: String, val theme: String, val layout: String,
                 val own: Boolean, val fx: String, val npFallback: String, val npVis: String,
                 val dual: Boolean = false, val npBottom: String = "", val wrapped: String = "", val card: String = "")

    /** Goes up each time a preset is put on, so the tab's lines show the new choices. */
    var applied by mutableStateOf(0)
    /** The preset being edited: picked, then changed on the tab, then saved over with Save changes. */
    var editing by mutableStateOf<String?>(null)

    private fun clean(s: String) = s.replace('|', '/').replace('\n', ' ')
    private fun encode(p: Preset) = listOf(p.key, clean(p.name), p.look, p.theme, p.layout, if (p.own) "1" else "0",
        clean(p.fx), p.npFallback, p.npVis, if (p.dual) "1" else "0", p.npBottom, p.wrapped, p.card).joinToString("|")

    fun all(): List<Preset> = Prefs.text(PREF).lines().mapNotNull { l -> runCatching { val f = l.split("|")
        Preset(f[0], f[1], f[2], f[3], f[4], f[5] == "1", f[6], f[7], f[8], f.getOrNull(9) == "1", f.getOrElse(10) { "" },
            f.getOrElse(11) { "" }, f.getOrElse(12) { "" }) }.getOrNull() }
    fun of(key: String) = all().firstOrNull { it.key == key }

    /** What's picked on the tab now, as a preset (not saved). */
    fun now(name: String, key: String = "") = Preset(key, name, Music.look,
        Prefs.text(Themes.PREF, Themes.STANDARD).takeIf { Themes.known(it) } ?: Themes.STANDARD,
        UiLayouts.current.key, LookFx.ownOn(), Prefs.text(LookFx.PICK), Music.npFallback, Vis.nowStyle(),
        Vis.nowDual(), Vis.nowBottom(), Music.wrappedStyle, LyricsCards.picked())

    /** The tab's choices saved under name (over the one with key, when it's given). */
    fun save(name: String, key: String? = null): Preset {
        val p = now(name.trim().ifEmpty { "My preset" }, key ?: ("mypreset-" + System.currentTimeMillis().toString(36)))
        val list = all()
        val at = list.indexOfFirst { it.key == p.key }
        val next = if (at >= 0) list.toMutableList().also { it[at] = p } else list + p
        Prefs.setText(PREF, next.joinToString("\n") { encode(it) })
        return p
    }

    fun delete(key: String) {
        MadeSync.forget(key)
        Prefs.setText(PREF, all().filter { it.key != key }.joinToString("\n") { encode(it) })
        if (editing == key) editing = null
    }

    /** Puts every choice of the preset on (the ones it names that are gone now, a deleted look say, are left as they are). */
    fun apply(key: String) {
        val p = of(key) ?: return
        if (Themes.known(p.theme)) Prefs.setText(Themes.PREF, p.theme)
        Prefs.set(LookFx.OWN, p.own)
        Prefs.setText(LookFx.PICK, p.fx)
        if (p.npVis.isNotBlank()) Prefs.setText(Vis.NOW, p.npVis)
        Prefs.set(Vis.NOW_DUAL, p.dual)
        if (p.npBottom.isNotBlank()) Prefs.setText(Vis.NOW_BOTTOM, p.npBottom)
        if (p.wrapped.isNotBlank() && WS.STYLES.any { it.first == p.wrapped }) Music.chooseWrappedStyle(p.wrapped)
        if (p.card.isNotBlank()) LyricsCards.pick(p.card)
        Music.chooseNpFallback(p.npFallback)
        if (Looks.known(p.look)) Music.chooseLook(p.look) else Music.applyLook()
        if (UiLayouts.all().any { it.key == p.layout }) UiLayouts.choose(p.layout)
        applied++
    }

    /** One preset's short summary for its menu. */
    fun describe(key: String): String {
        val p = of(key) ?: return ""
        val lay = UiLayouts.all().firstOrNull { it.key == p.layout }?.name ?: p.layout
        val n = LookFx.keys(p.fx.split(';').map { it.trim() }.filter { it.isNotEmpty() }.toSet()).size
        val fx = if (p.own) "the look's own effects" else if (n == 0) "no effects" else "$n effects"
        val look = Looks.ALL.firstOrNull { it.first == p.look }?.second ?: p.look
        return "$look look, ${Themes.ALL[p.theme]?.name ?: p.theme} theme, $lay layout, $fx"
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

/** The bases drawn around a middle (over the cover on Now playing). */
private val VIS_CENTRED_BASES = setOf("circle", "ring", "particles", "pulse", "starfield", "sunburst", "galaxy", "flower", "vinyl",
    "lissajous", "kaleido", "tunnel")

/** The cover's place on Now playing, under a full screen visualizer, in its preview. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCoverSpot() {
    val side = minOf(size.width * 0.62f, size.height * 0.55f)
    val tl = Offset((size.width - side) / 2, (size.height - side) / 2)
    val r = androidx.compose.ui.geometry.CornerRadius(side * 0.06f)
    drawRoundRect(Color.Black.copy(alpha = 0.55f), tl, androidx.compose.ui.geometry.Size(side, side), r)
    drawRoundRect(Color.White.copy(alpha = 0.35f), tl, androidx.compose.ui.geometry.Size(side, side), r,
        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVisPreviewStyle(d: MyVisStyle, s: VisState, pal: VisPalette, fg: Color) {
    PreviewVis.style = d
    drawVis(d.key, s, pal, fg, backdrop = false)
}


// ───────────────────────────── Favourites, and right-click (or hold) on a choice ─────────────────────────────

/** Your favourite looks, layouts, visualizers and saved colours: they go to the top of their lists, marked ★. Kept with the
 *  settings (backups and Sync carry them). */
object Favs {
    const val LOOKS = "fav_looks"; const val LAYOUTS = "fav_layouts"; const val VIS = "fav_vis"
    const val LOOK_COLOURS = "fav_look_colours"; const val BLEND_COLOURS = "fav_blend_colours"
    const val THEMES = "fav_themes"; const val EFFECTS = "fav_effects"; const val WRAPPED = "fav_wrapped"
    const val VIS_COLOURS = "fav_vis_colours"; const val PRESETS = "fav_presets"; const val CARDS = "fav_cards"
    fun of(kind: String): Set<String> = Prefs.text(kind).split(';').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    fun isFav(kind: String, key: String) = key in of(kind)
    fun toggle(kind: String, key: String) {
        val now = of(kind)
        Prefs.setText(kind, (if (key in now) now - key else now + key).joinToString(";"))
        Music.toast(if (key in now) "Taken off your favourites" else "A favourite: it's at the top of the list now")
    }
    /** The favourites first (★ before their names), the rest after, each in their own order. */
    fun sorted(kind: String, options: List<Pair<String, String>>): List<Pair<String, String>> {
        val f = of(kind)
        if (f.isEmpty()) return options
        return options.filter { it.first in f }.map { it.first to "★ ${it.second}" } + options.filter { it.first !in f }
    }
    /** What right-click (or holding a finger on it) offers for a look, layout or visualizer: Favourite for all of them,
     *  Delete for the ones you made. */
    fun menu(kind: String, key: String, label: String): List<Pair<String, () -> Unit>> {
        val name = label.removePrefix("★ ").removeSuffix(" (yours)")
        val fav = (if (isFav(kind, key)) "☆ Take off favourites" else "★ Favourite") to { toggle(kind, key) }
        val mine = when (kind) {
            LOOKS -> key.startsWith("mylook-") && key != "mylook-draft"
            LAYOUTS -> key.startsWith("mine-") && key != "mine-draft"
            VIS -> key.startsWith("myvis-")
            WRAPPED -> key.startsWith("mywrap-")
            VIS_COLOURS -> key.startsWith("mycol-")
            PRESETS -> key.startsWith("mypreset-")
            CARDS -> key.startsWith("mycard-")
            else -> false
        }
        if (!mine) return listOf(fav)
        // Edit: straight to its maker, with every setting it has (and its name) to change
        val edit = "Edit" to {
            if (kind == PRESETS) { MyPresets.apply(key); MyPresets.editing = key; Music.toast("Change anything on the tab, then Save changes") }
            else Ui.current?.let { ui -> ui.makerEdit = key; if (kind == CARDS) ui.lyricsCard = null
                ui.sheet = when (kind) { LOOKS -> "lookmaker"; LAYOUTS -> "layoutmaker"; WRAPPED -> "wrapmaker"; CARDS -> "cardmaker"; else -> "vismaker" } }
            Unit
        }
        return listOf(edit, fav, "Delete" to {
            val gone = {
                when (kind) { LOOKS -> MyLooks.delete(key); LAYOUTS -> UiLayouts.delete(key); VIS -> MyVis.deleteStyle(key); WRAPPED -> MyWraps.delete(key)
                    VIS_COLOURS -> MyVis.deleteColor(key); PRESETS -> MyPresets.delete(key); CARDS -> LyricsCards.delete(key) }
                if (isFav(kind, key)) Prefs.setText(kind, (of(kind) - key).joinToString(";"))
                Music.toast("Deleted “$name”")
            }
            // In a menu (ChoicePill) it's asked right there, so the menu stays open; elsewhere a box asks
            if (direct) gone() else Ui.current?.let { ui -> ui.confirm = "Delete “$name”?" to gone }
            Unit
        })
    }
    /** Set while a menu runs a Delete it already asked about itself. */
    var direct = false
}

// ───────────────────────────── Your own looks, layouts and visualizers, kept on every device ─────────────────────────────

/** What you made (looks, layouts, visualizer styles and colour blends) comes together across devices: a sync or a
 *  restore adds the other side's to yours, one by one (each by its key), instead of one device's list replacing the
 *  other's. One you delete is remembered as gone, so a sync doesn't bring it back. */
object MadeSync {
    val KEYS: List<String> get() = listOf(MyLooks.PREF, MyVis.STYLES, MyVis.COLORS, UiLayouts.MINE, MyWraps.PREF, MyPresets.PREF, LyricsCards.PREF,
        SavedColours.LOOK, SavedColours.BLEND)
    /** The keys of the ones you deleted, one a line (synced and backed up with the rest). */
    const val GONE = "made_gone"

    private fun keyOf(line: String) = line.substringBefore('|').trim()
    private fun draft(key: String) = key.endsWith("-draft") || key.endsWith("-preview")
    private fun gone(): Set<String> = Prefs.text(GONE).lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    /** A deletion, remembered so the other device's copy goes too. */
    fun forget(key: String) {
        if (draft(key) || key.isBlank()) return
        Prefs.setText(GONE, (gone() + key).joinToString("\n"))
    }

    /** Both sides' lines: theirs for the ones both have (the newer, as the sync decided), and the ones only one side has,
     *  less the deleted ones and the drafts. */
    fun merge(mine: String, theirs: String, goneToo: Set<String> = emptySet()): String {
        val out = LinkedHashMap<String, String>()
        val dead = gone() + goneToo
        for (l in mine.lines() + theirs.lines()) {
            val k = keyOf(l)
            if (k.isEmpty() || draft(k) || k in dead) continue
            out[k] = l   // theirs come second: theirs win where both have it
        }
        return out.values.joinToString("\n")
    }

    /** What's yours now, before a restore. */
    fun snapshot(): Map<String, String> = (KEYS + GONE).associateWith { Prefs.text(it) }

    /** After a restore: yours kept beside the backup's (the backup's own win where both have one). */
    fun afterRestore(before: Map<String, String>) {
        val goneNow = (before[GONE].orEmpty().lines() + Prefs.text(GONE).lines()).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        Prefs.setText(GONE, goneNow.joinToString("\n"))
        for (k in KEYS) Prefs.setText(k, merge(before[k].orEmpty(), Prefs.text(k)))
    }

    /** A sync's value for one of these keys, put together with what's here (null: not one of them). */
    fun incoming(key: String, here: String, theirs: String): String? = when (key) {
        GONE -> (here.lines() + theirs.lines()).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
        in KEYS -> merge(here, theirs)
        else -> null
    }
}

// ───────────────────────────── Your own Wrapped ─────────────────────────────

/** Make (or change) a Wrapped style: its colours (background, text, muted text, accent and a colour for each slide), how
 *  the slides glow, its type, corners, covers, bars, tiles and spacing, and stars and effects behind every slide. A slide
 *  in it shows at the top as you go; Save puts it in Wrapped's Style menu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WrapMaker(ui: Ui, editing: String?) {
    val start = editing?.let { MyWraps.style(it) }
    var name by remember { mutableStateOf(start?.name ?: "") }
    var bg by remember { mutableStateOf(start?.bg?.col() ?: Color(0xFF0E0B14)) }
    var fg by remember { mutableStateOf(start?.fg?.col() ?: Color(0xFFF5F2F2)) }
    var muted by remember { mutableStateOf(start?.muted?.col() ?: Color(0xFFA09AAE)) }
    var accent by remember { mutableStateOf(start?.accent?.col() ?: C.accent) }
    val slides = remember { mutableStateListOf<Color>().also { l -> l.addAll(start?.slides?.map { it.col() }
        ?: listOf(0xFF7C4DFF, 0xFFFF4FD8, 0xFF1FFF6E, 0xFFFFC21A, 0xFFFF1F3D, 0xFF2F7BFF, 0xFF14E4FF, 0xFFFF7A1A).map { Color(it) }) } }
    var gradients by remember { mutableStateOf(start?.gradients ?: true) }
    var glow by remember { mutableStateOf(start?.glow ?: 0.5f) }
    var neonText by remember { mutableStateOf(start?.neonText ?: false) }
    var heavy by remember { mutableStateOf(start?.heavy ?: true) }
    var textScale by remember { mutableStateOf(start?.textScale ?: 1f) }
    var corners by remember { mutableStateOf((start?.corners ?: 6).toFloat()) }
    var roundCovers by remember { mutableStateOf(start?.roundCovers ?: false) }
    var coverSize by remember { mutableStateOf(start?.coverSize ?: 1f) }
    var bar by remember { mutableStateOf(start?.bar ?: 5f) }
    var tiles by remember { mutableStateOf(start?.tiles ?: true) }
    var spacing by remember { mutableStateOf(start?.spacing ?: 1f) }
    var stars by remember { mutableStateOf(start?.stars ?: false) }
    var fx by remember { mutableStateOf(start?.fx ?: emptySet()) }
    var picking by remember { mutableStateOf("bg") }   // "bg", "fg", "muted", "accent" or "s0".."s7"
    fun draft(key: String = editing ?: "mywrap-draft") = MyWrap(key, name.ifBlank { "My Wrapped" }, bg.long(), fg.long(), muted.long(), accent.long(),
        slides.map { it.long() }, gradients, glow, neonText, heavy, textScale, corners.toInt(), roundCovers, fx, stars, bar, tiles, coverSize, spacing)
    // The preview draws the one being made
    // Made here, in the composition, so this recomposes (and the pictures follow) whenever any setting changes; made
    // inside the SideEffect it only caught the changes that happened to redraw this whole screen
    val now = draft()
    androidx.compose.runtime.SideEffect { MyWraps.draft = now }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { MyWraps.draft = null } }
    val slideShown = picking.removePrefix("s").toIntOrNull() ?: 0
    FullScreen(if (start != null) "Change your Wrapped" else "Make your own Wrapped", { ui.sheet = null }, actions = {
        Pill("Save") {
            val kept = MyWraps.save(draft(if (start != null) start.key else "new"))
            Music.chooseWrappedStyle(kept.key)
            Music.toast("Saved “${kept.name}”: it's in Wrapped's Style menu")
            ui.sheet = null
        }
    }) {
        Column(Modifier.fillMaxSize()) {
            // Every slide of your Wrapped in this style, to swipe through (the sample slide until yours has loaded)
            WrapSlidesPreview(Modifier.fillMaxWidth().height(if (onPhone) 430.dp else 540.dp).padding(vertical = 8.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WrapStylePreview(slideShown, Modifier.width(if (onPhone) 230.dp else 300.dp).height(if (onPhone) 380.dp else 480.dp))
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
                Text("Your own style for Wrapped's slides. The pictures show your Wrapped's slides in it as you go: swipe them left and right.", style = T.sub)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.padding(bottom = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Your look's colours (its background, text, soft text and accent, the slides in shades of its accent),
                    // its glow, stars and effects: a start to add your own effects and changes on top of
                    Pill("Copy my look", quiet = true) {
                        bg = C.bg; fg = C.fg; muted = C.muted; accent = C.accent
                        val a = C.accent
                        val mx = maxOf(a.red, a.green, a.blue); val mn = minOf(a.red, a.green, a.blue); val d = mx - mn
                        val h = when { d == 0f -> 0f; mx == a.red -> 60f * (((a.green - a.blue) / d) % 6f); mx == a.green -> 60f * ((a.blue - a.red) / d + 2f)
                            else -> 60f * ((a.red - a.green) / d + 4f) }
                        val sat = if (mx == 0f) 0f else d / mx
                        for (i in slides.indices) slides[i] = Color.hsv(((h + i * 24f) % 360f + 360f) % 360f, sat.coerceIn(0.35f, 1f), mx.coerceIn(0.5f, 1f))
                        C.gradient?.firstOrNull()?.let { if (it != C.bg) slides[0] = it }
                        neonText = C.glow
                        stars = C.stars
                        fx = C.fx
                        Music.toast("Your look's colours, stars and effects: add yours on top")
                    }
                    Pill("Randomize") {
                        val r = kotlin.random.Random
                        val dark = r.nextFloat() < 0.75f
                        val hue = r.nextFloat() * 360f
                        bg = if (dark) Color.hsv(hue, 0.3f + r.nextFloat() * 0.5f, 0.05f + r.nextFloat() * 0.1f) else Color.hsv(hue, 0.05f + r.nextFloat() * 0.2f, 0.92f + r.nextFloat() * 0.06f)
                        fg = if (dark) Color.hsv(hue, 0.05f, 0.96f) else Color.hsv(hue, 0.2f, 0.1f)
                        muted = androidx.compose.ui.graphics.lerp(fg, bg, 0.4f)
                        accent = randomColor(r)
                        for (i in slides.indices) slides[i] = randomColor(r)
                        gradients = r.nextFloat() < 0.8f; glow = 0.2f + r.nextFloat() * 0.6f; neonText = dark && r.nextFloat() < 0.35f
                        heavy = r.nextFloat() < 0.7f; textScale = 0.85f + r.nextFloat() * 0.35f; corners = (r.nextInt(0, 7) * 4).toFloat()
                        roundCovers = r.nextFloat() < 0.3f; coverSize = 0.85f + r.nextFloat() * 0.3f; bar = (3 + r.nextInt(0, 7)).toFloat()
                        tiles = r.nextFloat() < 0.75f; spacing = 0.85f + r.nextFloat() * 0.3f
                        stars = dark && r.nextFloat() < 0.4f
                        fx = LookFx.ALL.shuffled(r).take(r.nextInt(0, 3)).map { it.key }.toSet()
                    }
                    Pill("Randomize colours", quiet = true) {
                        val r = kotlin.random.Random
                        accent = randomColor(r); for (i in slides.indices) slides[i] = randomColor(r)
                        val dark = bg.luminanceOf() < 0.5f
                        bg = if (dark) randomColor(r, dark = true) else Color.hsv(r.nextFloat() * 360f, 0.08f, 0.95f)
                    }
                }
                NameField(name) { name = it }
                Heading("Colours", "Tap one to change it")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("bg" to ("Background" to bg), "fg" to ("Text" to fg), "muted" to ("Soft text" to muted), "accent" to ("Accent" to accent)).forEach { (k, v) ->
                        Column(Modifier.clip(R.card).background(if (picking == k) C.accent.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable { picking = k }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(40.dp).clip(R.pill).background(v.second).border(1.dp, C.line, R.pill))
                            Text(v.first, style = T.time.copy(color = if (picking == k) C.accent else C.muted), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                SavedColoursBar(SavedColours.LOOK, { listOf(accent, bg, muted, fg) }) { cs ->
                    accent = cs[0]; bg = cs.getOrElse(1) { bg }; muted = cs.getOrElse(2) { muted }; fg = cs.getOrElse(3) { fg }
                }
                Heading("Each slide's colour", "Wrapped's slides take these in turn (top songs, artists, albums, moods…): the glow and the top corner")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    slides.forEachIndexed { i, c ->
                        Box(Modifier.size(36.dp).clip(R.pill).background(c).border(if (picking == "s$i") 3.dp else 1.dp, if (picking == "s$i") C.accent else C.line, R.pill)
                            .clickable { picking = "s$i" })
                    }
                }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("All in the accent", quiet = true) { for (i in slides.indices) slides[i] = accent }
                    Pill("Rainbow", quiet = true) { for (i in slides.indices) slides[i] = Color.hsv(i * 45f, 0.75f, 1f) }
                }
                Spacer(Modifier.height(10.dp))
                val now = when (picking) { "bg" -> bg; "fg" -> fg; "muted" -> muted; "accent" -> accent; else -> slides[slideShown] }
                ColorPicker(now) { c ->
                    when (picking) { "bg" -> bg = c; "fg" -> fg = c; "muted" -> muted = c; "accent" -> accent = c; else -> slides[slideShown] = c }
                }
                Heading("Glow")
                Toggle("Coloured glow", "Each slide glows in its colour from the corners", gradients) { gradients = it }
                if (gradients) SliderLine("Strength", glow, 0.05f..1f, "%.0f%%".format(glow * 100)) { glow = it }
                Toggle("Neon text", "The big numbers and ranks glow in the accent", neonText) { neonText = it }
                Heading("Type")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Bold", heavy) { heavy = true }
                    Chip("Light", !heavy) { heavy = false }
                }
                SliderLine("Text size", textScale, 0.8f..1.4f, "%.0f%%".format(textScale * 100)) { textScale = it }
                SliderLine("Spacing", spacing, 0.7f..1.5f, "%.0f%%".format(spacing * 100)) { spacing = it }
                Heading("Shapes")
                SliderLine("Corners", corners, 0f..28f, "%.0f".format(corners)) { corners = it }
                Toggle("Round covers", "Every cover in a circle", roundCovers) { roundCovers = it }
                SliderLine("Cover size", coverSize, 0.7f..1.3f, "%.0f%%".format(coverSize * 100)) { coverSize = it }
                SliderLine("Bar thickness", bar, 2f..12f, "%.0f".format(bar)) { bar = it }
                Toggle("Tiles", "The numbers on soft tiles (off: just the numbers)", tiles) { tiles = it }
                Heading("Behind the slides", "Stars and any of the effects, drawn behind every slide")
                Toggle("Stars", "A few stars over the background", stars) { stars = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LookFx.ALL.forEach { e -> Chip(e.name, LookFx.has(fx, e.key)) { fx = LookFx.flip(fx, e.key) } }
                }
                FxColors(fx) { fx = it }
                if (start != null) Box(Modifier.padding(top = 16.dp)) {
                    Pill("Delete it", quiet = true) { ui.confirm = "Delete “${start.name}”?" to { MyWraps.delete(start.key); ui.sheet = null } }
                }
            }
        }
    }
}

private fun Color.luminanceOf(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

// ───────────────────────────── Your own lyrics card ─────────────────────────────

/** Make (or change) a lyrics card style: its background fade, the card behind the words, every colour (words, song name,
 *  soft text, the Radon Player mark), stars and any effects each in its own colour, the type and the cover. The card shows
 *  at the top as you go; Save puts it in the Lyrics card's Style menu and picks it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardMaker(ui: Ui, editing: String?) {
    val start = editing?.let { k -> LyricsCards.mine().firstOrNull { it.key == k } }
    val first = start ?: LyricsCards.PRESETS[0]
    var name by remember { mutableStateOf(start?.name ?: "") }
    var top by remember { mutableStateOf(first.top.col()) }
    var bottom by remember { mutableStateOf(first.bottom.col()) }
    var panel by remember { mutableStateOf(first.panel.col()) }
    var panelAlpha by remember { mutableStateOf(first.panelAlpha) }
    var words by remember { mutableStateOf(first.words.col()) }
    var title by remember { mutableStateOf(first.title.col()) }
    var soft by remember { mutableStateOf(first.soft.col()) }
    var brand by remember { mutableStateOf(first.brand.col()) }
    var fx by remember { mutableStateOf(first.fx) }
    var stars by remember { mutableStateOf(first.stars) }
    var corners by remember { mutableStateOf(first.corners) }
    var heavy by remember { mutableStateOf(first.heavy) }
    var round by remember { mutableStateOf(first.roundCover) }
    var textScale by remember { mutableStateOf(first.textScale) }
    var picking by remember { mutableStateOf("top") }
    fun use(c: MyCard) {
        top = c.top.col(); bottom = c.bottom.col(); panel = c.panel.col(); panelAlpha = c.panelAlpha; words = c.words.col()
        title = c.title.col(); soft = c.soft.col(); brand = c.brand.col(); fx = c.fx; stars = c.stars; corners = c.corners
        heavy = c.heavy; round = c.roundCover; textScale = c.textScale
    }
    fun draft(key: String = editing ?: "mycard-draft") = MyCard(key, name.ifBlank { "My lyrics card" }, top.long(), bottom.long(), panel.long(),
        panelAlpha, words.long(), title.long(), soft.long(), brand.long(), fx, stars, corners, heavy, round, textScale)
    val now = draft()
    androidx.compose.runtime.SideEffect { LyricsCards.draft = now }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { LyricsCards.draft = null } }
    val song = Music.current() ?: org.omarchy.music.Track("sample", title = "Song title", artist = "Artist")
    val sample = listOf("Your words go here", "on the card you share", "in the colours you pick")
    val card = remember(now, song.id) { lyricsCard(sample, song, now, false) }
    FullScreen(if (start != null) "Change your lyrics card" else "Make your own lyrics card", { ui.sheet = null }, actions = {
        Pill("Save") {
            val kept = LyricsCards.save(draft(if (start != null) start.key else "new"))
            LyricsCards.pick(kept.key)
            Music.toast("Saved “${kept.name}”: it's in the Lyrics card's Style menu")
            ui.sheet = null
        }
    }) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Image(card, "The card", Modifier.size(if (onPhone) 240.dp else 300.dp, if (onPhone) 300.dp else 375.dp))
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
                Text("Your own style for lyrics cards (Lyrics › Card). The card above shows it as you go.", style = T.sub)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.padding(bottom = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    // Start from any of the ready-made ones (or yours), then change what you like
                    ChoicePill(LyricsCards.all().map { it.key to "Start from: " + it.name }, "", describe = { LyricsCards.describe(it) }) { k ->
                        LyricsCards.all().firstOrNull { it.key == k }?.let { use(it) }
                    }
                    Pill("Copy my look", quiet = true) {
                        top = C.gradient?.firstOrNull() ?: C.bg; bottom = C.gradient?.lastOrNull() ?: C.bg
                        panel = C.light; panelAlpha = if (C.isLight) 0.94f else 0.6f
                        words = C.fg; title = C.accent; soft = C.muted; brand = C.accent; stars = C.stars; fx = C.fx
                    }
                    Pill("Randomize") {
                        val r = kotlin.random.Random
                        val dark = r.nextFloat() < 0.75f
                        val hue = r.nextFloat() * 360f
                        top = if (dark) Color.hsv(hue, 0.4f + r.nextFloat() * 0.5f, 0.08f + r.nextFloat() * 0.2f) else Color.hsv(hue, 0.1f + r.nextFloat() * 0.3f, 0.9f + r.nextFloat() * 0.1f)
                        bottom = if (dark) Color.hsv((hue + r.nextFloat() * 60f) % 360f, 0.4f + r.nextFloat() * 0.5f, 0.03f + r.nextFloat() * 0.1f)
                            else Color.hsv((hue + r.nextFloat() * 60f) % 360f, 0.15f + r.nextFloat() * 0.3f, 0.8f + r.nextFloat() * 0.15f)
                        panel = if (dark) Color.Black else Color.White; panelAlpha = 0.2f + r.nextFloat() * 0.5f
                        words = if (dark) Color.hsv(hue, 0.08f, 0.97f) else Color.hsv(hue, 0.3f, 0.15f)
                        title = randomColor(r); soft = androidx.compose.ui.graphics.lerp(words, top, 0.4f); brand = title
                        fx = LookFx.ALL.shuffled(r).take(r.nextInt(1, 4)).map { it.key }.toSet()
                        stars = dark && r.nextFloat() < 0.4f; corners = (r.nextInt(0, 9) * 8).toFloat()
                        heavy = r.nextFloat() < 0.4f; round = r.nextFloat() < 0.35f; textScale = 0.9f + r.nextFloat() * 0.25f
                    }
                    Pill("Randomize colours", quiet = true) {
                        val r = kotlin.random.Random
                        val dark = top.luminanceOf() < 0.5f
                        top = if (dark) randomColor(r, dark = true) else Color.hsv(r.nextFloat() * 360f, 0.15f, 0.95f)
                        bottom = if (dark) randomColor(r, dark = true) else Color.hsv(r.nextFloat() * 360f, 0.2f, 0.85f)
                        title = randomColor(r); brand = title
                        var next = fx; LookFx.ALL.filter { LookFx.has(fx, it.key) }.forEach { e -> next = LookFx.withColor(next, e.key, randomColor(r)) }; fx = next
                    }
                }
                NameField(name) { name = it }
                Heading("Colours", "Tap one to change it")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("top" to ("Top" to top), "bottom" to ("Bottom" to bottom), "panel" to ("Card" to panel), "words" to ("Words" to words),
                        "title" to ("Song name" to title), "soft" to ("Soft text" to soft), "brand" to ("Radon Player" to brand)).forEach { (k, v) ->
                        Column(Modifier.clip(R.card).background(if (picking == k) C.accent.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable { picking = k }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(40.dp).clip(R.pill).background(v.second).border(1.dp, C.line, R.pill))
                            Text(v.first, style = T.time.copy(color = if (picking == k) C.accent else C.muted), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                SavedColoursBar(SavedColours.LOOK, { listOf(title, top, soft, words) }) { cs ->
                    title = cs[0]; brand = cs[0]; top = cs.getOrElse(1) { top }; soft = cs.getOrElse(2) { soft }; words = cs.getOrElse(3) { words }
                    cs.getOrNull(4)?.let { bottom = it }
                }
                Spacer(Modifier.height(10.dp))
                val shown = when (picking) { "top" -> top; "bottom" -> bottom; "panel" -> panel; "words" -> words; "title" -> title; "soft" -> soft; else -> brand }
                ColorPicker(shown) { c ->
                    when (picking) { "top" -> top = c; "bottom" -> bottom = c; "panel" -> panel = c; "words" -> words = c; "title" -> title = c
                        "soft" -> soft = c; else -> brand = c }
                }
                Heading("The card behind the words")
                SliderLine("See-through", 1f - panelAlpha, 0f..1f, "%.0f%%".format((1f - panelAlpha) * 100)) { panelAlpha = 1f - it }
                SliderLine("Corners", corners, 0f..120f, "%.0f".format(corners)) { corners = it }
                Heading("Type and cover")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Regular", !heavy) { heavy = false }
                    Chip("Bold", heavy) { heavy = true }
                }
                SliderLine("Text size", textScale, 0.7f..1.4f, "%.0f%%".format(textScale * 100)) { textScale = it }
                Toggle("Round cover", "The song's cover in a circle", round) { round = it }
                Heading("Behind the card", "Stars and any of the effects, each in its own colour")
                Toggle("Stars", "A few stars over the background", stars) { stars = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LookFx.ALL.forEach { e -> Chip(e.name, LookFx.has(fx, e.key)) { fx = LookFx.flip(fx, e.key) } }
                }
                FxColors(fx) { fx = it }
                if (start != null) Box(Modifier.padding(top = 16.dp)) {
                    Pill("Delete it", quiet = true) { ui.confirm = "Delete “${start.name}”?" to { LyricsCards.delete(start.key); ui.sheet = null } }
                }
            }
        }
    }
}
