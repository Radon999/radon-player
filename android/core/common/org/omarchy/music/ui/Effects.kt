package org.omarchy.music.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A look's effects (Make your own look › Effects): drawn over the window's background and behind everything else, so
 *  they go with every theme, layout and look. Each is its own kind of thing; any of them can be on together. */
object LookFx {
    class Fx(val key: String, val name: String, val desc: String, val moves: Boolean = false)

    val ALL = listOf(
        Fx("twinkle", "Twinkling stars", "Stars that slowly brighten and fade", true),
        Fx("shooting", "Shooting stars", "Now and then a streak of light across the sky", true),
        Fx("aurora", "Aurora", "Soft ribbons of light in your colours swaying across the top", true),
        Fx("grid", "Synthwave grid", "A glowing grid floor running off to the horizon, as in the 80s"),
        Fx("sun", "Retro sun", "A big striped sunset sun low on the screen"),
        Fx("scanlines", "Scanlines", "Fine lines across everything, like an old CRT screen"),
        Fx("grain", "Film grain", "A light speckle of grain, like film"),
        Fx("vignette", "Vignette", "The corners and edges gently darkened"),
        Fx("bokeh", "Bokeh", "Big soft out-of-focus circles of light"),
        Fx("rain", "Rain", "Rain falling across the screen", true),
        Fx("snow", "Snow", "Snowflakes drifting down", true),
        Fx("fireflies", "Fireflies", "Small glowing lights wandering about", true),
        Fx("bubbles", "Bubbles", "Bubbles rising slowly from the bottom", true),
        Fx("waves", "Waves", "Layers of waves rolling along the bottom", true),
        Fx("mountains", "Mountains", "Layered mountain silhouettes along the bottom"),
        Fx("skyline", "City skyline", "A city's silhouette along the bottom, a few windows lit"),
        Fx("halftone", "Halftone", "A dot pattern fading in from one corner, like print"),
        Fx("stripes", "Stripes", "Faint diagonal stripes"),
        Fx("hexagons", "Hexagons", "A faint honeycomb of hexagons"),
        Fx("rays", "Light rays", "Rays of light fanning down from the top"),
        Fx("spotlight", "Spotlight", "A soft glow in the middle, as if lit from above"),
        Fx("sheen", "Glass sheen", "A glossy diagonal shine across the top"),
        Fx("nebula", "Nebula", "Coloured clouds of a nebula far away"),
        Fx("lava", "Lava lamp", "Big soft blobs slowly drifting and blending", true),
        Fx("breathe", "Breathing", "The background slowly brightens and dims, like breathing", true),
        Fx("drift", "Drifting colours", "Your colours slowly sliding around the background", true),
        Fx("digital", "Digital rain", "Columns of falling light, like code", true),
        Fx("confetti", "Confetti", "Little pieces of confetti scattered about"),
        Fx("constellation", "Constellations", "Points of light joined into constellations"),
        Fx("rings", "Rings", "Rings spreading out from the middle, like ripples", true),
        Fx("frame", "Neon frame", "A glowing neon line around the edge of the window"),
        // 3.2.3: thirty more
        Fx("petals", "Falling petals", "Blossom petals drifting down and turning", true),
        Fx("leaves", "Autumn leaves", "Leaves tumbling down in autumn colours", true),
        Fx("embers", "Embers", "Glowing embers rising from the bottom, as from a fire", true),
        Fx("lightning", "Lightning", "Now and then a flash and a fork of lightning", true),
        Fx("comets", "Comets", "Slow comets with long glowing tails crossing the sky", true),
        Fx("meteors", "Meteor shower", "Lots of quick streaks falling at a slant", true),
        Fx("moon", "Moon", "A big glowing moon high in one corner"),
        Fx("planet", "Ringed planet", "A planet with rings hanging in the sky"),
        Fx("clouds", "Clouds", "Soft clouds drifting slowly across", true),
        Fx("fog", "Fog", "Low fog rolling along the bottom", true),
        Fx("sea", "Night sea", "A calm sea at the bottom, the light shimmering on it", true),
        Fx("dunes", "Desert dunes", "Rolling sand dunes along the bottom"),
        Fx("pines", "Pine forest", "A forest of pine trees along the bottom"),
        Fx("graph", "Graph paper", "A faint square grid, like graph paper"),
        Fx("polka", "Polka dots", "Faint dots in neat rows"),
        Fx("checker", "Checkerboard", "A faint checkerboard"),
        Fx("plaid", "Plaid", "Faint crossing bands, like tartan"),
        Fx("circuit", "Circuit board", "Thin traces and pads, like a circuit board"),
        Fx("topo", "Contour lines", "Wavy contour lines, like a map's"),
        Fx("burst", "Starburst", "Rays turning slowly from the middle", true),
        Fx("prism", "Prism", "A rainbow beam across the screen"),
        Fx("sparkle", "Sparkles", "Little four-pointed sparkles glinting", true),
        Fx("hearts", "Floating hearts", "Hearts rising and fading", true),
        Fx("notes", "Music notes", "Music notes floating up", true),
        Fx("orbs", "Glowing orbs", "Big soft orbs of light drifting around", true),
        Fx("spiral", "Spiral galaxy", "A faint spiral galaxy turning in the middle", true),
        Fx("crt", "Old TV", "The rounded glow of an old TV screen"),
        Fx("glitch", "Glitch", "Now and then bands of the screen glitch sideways", true),
        Fx("warp", "Warp speed", "Stars streaking out from the middle", true),
        Fx("sunbeams", "Sunbeams", "Warm beams of light from a top corner", true),
    )
    fun moves(fx: Set<String>) = keys(fx).let { k -> ALL.any { it.moves && it.key in k } }

    // An effect's own colour rides in its entry: "rain@ff4fd8ff" (ARGB hex); a plain "rain" draws in the look's colours.
    fun keyOf(e: String) = e.substringBefore('@')
    fun keys(fx: Set<String>): Set<String> = fx.map { keyOf(it) }.toSet()
    fun colors(fx: Set<String>): Map<String, Color> = fx.mapNotNull { e ->
        e.substringAfter('@', "").takeIf { it.isNotEmpty() }?.toLongOrNull(16)?.let { keyOf(e) to Color(it) } }.toMap()
    fun has(fx: Set<String>, k: String) = fx.any { keyOf(it) == k }
    /** Rain and snow fall over everything (the seek bar and buttons too); the others stay behind. */
    private val OVER = setOf("rain", "snow")
    fun behind(fx: Set<String>): Set<String> = fx.filter { keyOf(it) !in OVER }.toSet()
    fun over(fx: Set<String>): Set<String> = fx.filter { keyOf(it) in OVER }.toSet()
    /** The effects with k's colour set (null: back to the look's colours). */
    fun withColor(fx: Set<String>, k: String, c: Color?): Set<String> = fx.map { e ->
        if (keyOf(e) != k) e else if (c == null) k else k + "@" + (c.toArgb().toLong() and 0xFFFFFFFFL).toString(16) }.toSet()
    /** Turns k on or off, keeping the others (and their colours). */
    fun flip(fx: Set<String>, k: String): Set<String> = if (has(fx, k)) fx.filter { keyOf(it) != k }.toSet() else fx + k
    /** The colour an effect is drawn in by default, to start its picker from. */
    fun defaultColor(k: String, accent: Color, fg: Color): Color = when (k) {
        "twinkle", "shooting", "snow", "sheen", "constellation" -> Color.White
        "rain", "stripes", "hexagons" -> fg
        "fireflies" -> Color(0xFFFFF27A); "skyline" -> Color(0xFFFFE08A); "digital" -> Color(0xFF3CFF7A)
        "scanlines", "vignette", "mountains" -> Color.Black
        else -> accent
    }

    // ---- Settings › Personalization › Effects: any of these with any look or theme ----

    /** On (the default): each look keeps its own effects and stars. Off: the effects picked here, whatever the look. */
    const val OWN = "look_fx_own"
    /** The effects picked here ("stars" for the plain stars, else LookFx keys), joined with ";". */
    const val PICK = "look_fx_pick"
    fun ownOn(): Boolean = org.omarchy.music.Prefs.on(OWN, true)
    fun picked(): Set<String> = org.omarchy.music.Prefs.text(PICK).split(';').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    /** The menu's choices: the stars, then every effect. */
    val MENU: List<Pair<String, String>> get() = listOf("stars" to "Stars") + ALL.map { it.key to it.name }
    fun describe(k: String): String = if (k == "stars") "A few stars over the background, as the galaxy looks have" else ALL.firstOrNull { it.key == k }?.desc.orEmpty()
    /** After a look is put on: with its own effects off, the ones picked here instead. */
    fun override() {
        if (ownOn()) return
        // A look you made keeps the effects you gave it: the ones picked here go with the ready-made looks only
        if (org.omarchy.music.Music.look.startsWith("mylook-")) return
        val p = picked()
        C.stars = has(p, "stars")
        C.fx = p.filter { keyOf(it) != "stars" }.toSet()
    }
    fun setOwn(on: Boolean) { org.omarchy.music.Prefs.set(OWN, on); org.omarchy.music.Music.applyLook() }
    fun toggle(k: String) {
        val p = flip(picked(), k)
        org.omarchy.music.Prefs.setText(PICK, p.joinToString(";"))
        org.omarchy.music.Music.applyLook()
    }
}

/** The look's effects over the background (C.fx in the colours of the look in use). */
@Composable
fun LookEffects(fx: Set<String> = C.fx, modifier: Modifier = Modifier.fillMaxSize(),
                accent: Color = C.accent, fg: Color = C.fg, bg: Color = C.bg) {   // a Wrapped style passes its own colours
    if (fx.isEmpty()) return
    // The time is read while drawing, not while composing: a moving effect redraws each frame without recomposing anything
    val time: androidx.compose.runtime.State<Float>? = if (LookFx.moves(fx)) {
        val tr = rememberInfiniteTransition(label = "fx")
        tr.animateFloat(0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    } else null
    val seeds = remember { java.util.Random(11).let { r -> List(200) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    val keys = remember(fx) { LookFx.keys(fx).let { on -> LookFx.ALL.map { it.key }.filter { it in on } } }
    val tints = remember(fx) { LookFx.colors(fx) }
    Canvas(modifier) {
        val t = time?.value ?: 0f
        for (k in keys) drawFx(k, t, seeds, accent, fg, bg, tints[k])
    }
}

internal fun DrawScope.drawFx(k: String, t: Float, s: List<FloatArray>, lookAccent: Color, fg: Color, bg: Color, tint: Color? = null) {
    val W = size.width; val H = size.height; val d = density
    // Its own colour (Effects › its colour) in place of its usual main colour
    val accent = tint ?: lookAccent
    fun m(def: Color) = tint ?: def
    val light = lerp(accent, Color.White, 0.5f)
    when (k) {
        "twinkle" -> for (i in 0 until 90) { val p = s[i]
            val a = 0.15f + 0.75f * (0.5f + 0.5f * sin(t * (0.6f + p[2] * 1.4f) + p[3] * 6.28f))
            drawCircle(m(Color.White).copy(alpha = a), (0.6f + p[2] * 1.6f) * d, Offset(p[0] * W, p[1] * H)) }
        "shooting" -> for (i in 0 until 3) { val p = s[i]
            val cycle = 7f + p[2] * 6f; val ph = ((t + p[3] * cycle) % cycle) / 1.2f
            if (ph < 1f) {
                val x0 = p[0] * W * 0.7f + W * 0.3f; val y0 = p[1] * H * 0.4f
                val len = 140 * d; val head = Offset(x0 - ph * W * 0.5f, y0 + ph * W * 0.25f)
                drawLine(Brush.linearGradient(listOf(Color.Transparent, m(Color.White).copy(alpha = 1 - ph)), Offset(head.x + len, head.y - len / 2), head),
                    Offset(head.x + len, head.y - len / 2), head, 2 * d)
            } }
        "aurora" -> for (b in 0 until 3) {
            val path = Path(); val base = H * (0.12f + b * 0.07f)
            path.moveTo(0f, base)
            var x = 0f
            while (x <= W) { path.lineTo(x, base + sin(x / W * 6f + t * 0.3f + b) * H * 0.04f); x += 8 * d }
            path.lineTo(W, base + H * 0.18f); path.lineTo(0f, base + H * 0.18f); path.close()
            val col = if (b == 1) (tint?.let { lerp(it, Color.White, 0.35f) } ?: Color(0xFF3CFFB4)) else accent
            drawPath(path, Brush.verticalGradient(listOf(col.copy(alpha = 0.22f), Color.Transparent), base - H * 0.05f, base + H * 0.18f))
        }
        "grid" -> {
            val hz = H * 0.66f; val c = accent.copy(alpha = 0.35f)
            for (i in 0..14) { val f = i / 14f; val y = hz + (H - hz) * f * f; drawLine(c, Offset(0f, y), Offset(W, y), 1 * d) }
            for (i in -12..12) { drawLine(c, Offset(W / 2, hz), Offset(W / 2 + i * W / 8, H), 1 * d) }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.15f)), hz - 40 * d, hz), Offset(0f, hz - 40 * d), Size(W, 40 * d))
        }
        "sun" -> {
            val r = minOf(W, H) * 0.22f; val c = Offset(W / 2, H * 0.62f)
            drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFD36B), accent), c.y - r, c.y + r), r, c, alpha = 0.55f)
            for (i in 0 until 6) { val y = c.y + r * (0.1f + i * 0.16f); drawRect(bg, Offset(c.x - r, y), Size(2 * r, (2f + i * 1.5f) * d)) }
        }
        "scanlines" -> { var y = 0f; while (y < H) { drawLine(m(Color.Black).copy(alpha = 0.16f), Offset(0f, y), Offset(W, y), 1 * d); y += 3 * d } }
        "grain" -> { val r = java.util.Random(5); repeat(1400) {
            drawRect((if (r.nextBoolean()) m(Color.White) else Color.Black).copy(alpha = 0.05f + r.nextFloat() * 0.06f),
                Offset(r.nextFloat() * W, r.nextFloat() * H), Size(1.2f * d, 1.2f * d)) } }
        "vignette" -> drawRect(Brush.radialGradient(listOf(Color.Transparent, m(Color.Black).copy(alpha = 0.55f)), Offset(W / 2, H / 2),
            sqrt(W * W + H * H) / 1.6f))
        "bokeh" -> for (i in 0 until 14) { val p = s[i + 20]
            val r = (30 + p[2] * 70) * d
            drawCircle(Brush.radialGradient(listOf((if (i % 2 == 0) accent else (if (tint != null) light else fg)).copy(alpha = 0.16f), Color.Transparent), Offset(p[0] * W, p[1] * H), r),
                r, Offset(p[0] * W, p[1] * H)) }
        "rain" -> for (i in 0 until 80) { val p = s[i]
            val speed = 0.6f + p[2]; val y = ((p[1] + t * speed * 0.5f) % 1.2f - 0.1f) * H; val x = p[0] * W
            drawLine(m(fg).copy(alpha = if (tint != null) 0.4f else 0.18f), Offset(x, y), Offset(x - 3 * d, y + 16 * d), 1 * d) }
        "snow" -> for (i in 0 until 70) { val p = s[i + 40]
            val y = ((p[1] + t * (0.03f + p[2] * 0.05f)) % 1.1f - 0.05f) * H
            val x = p[0] * W + sin(t * 0.8f + p[3] * 6f) * 12 * d
            drawCircle(m(Color.White).copy(alpha = 0.55f), (1.2f + p[2] * 2.2f) * d, Offset(x, y)) }
        "fireflies" -> for (i in 0 until 24) { val p = s[i + 60]
            val x = (p[0] + 0.05f * sin(t * 0.3f + p[3] * 9)) * W; val y = (p[1] + 0.05f * cos(t * 0.25f + p[2] * 9)) * H
            val a = 0.3f + 0.6f * (0.5f + 0.5f * sin(t * 1.3f + p[3] * 7))
            drawCircle(Brush.radialGradient(listOf(m(Color(0xFFFFF27A)).copy(alpha = a), Color.Transparent), Offset(x, y), 10 * d), 10 * d, Offset(x, y)) }
        "bubbles" -> for (i in 0 until 26) { val p = s[i + 90]
            val y = H - ((p[1] + t * (0.02f + p[2] * 0.03f)) % 1.1f) * H * 1.05f
            val x = p[0] * W + sin(t * 0.7f + p[3] * 6) * 8 * d
            drawCircle(light.copy(alpha = 0.3f), (4 + p[2] * 12) * d, Offset(x, y), style = Stroke(1.2f * d)) }
        "waves" -> for (b in 0 until 3) {
            val path = Path(); val base = H * (0.82f + b * 0.05f)
            path.moveTo(0f, H); var x = 0f
            while (x <= W) { path.lineTo(x, base + sin(x / W * (5f + b) * PI.toFloat() + t * (0.8f + b * 0.3f)) * 10 * d); x += 6 * d }
            path.lineTo(W, H); path.close()
            drawPath(path, accent.copy(alpha = 0.10f + b * 0.06f))
        }
        "mountains" -> for (b in 0 until 3) {
            val path = Path(); val base = H * (0.7f + b * 0.08f); path.moveTo(0f, H)
            for (j in 0..8) { val p = s[j + b * 9 + 120]; path.lineTo(W * j / 8f, base - p[0] * H * 0.12f) }
            path.lineTo(W, H); path.close()
            drawPath(path, lerp(tint ?: bg, Color.Black, 0.25f + b * 0.2f).copy(alpha = 0.85f))
        }
        "skyline" -> { var x = 0f; var i = 0
            while (x < W) { val p = s[(i++) % 200]; val w = (24 + p[0] * 40) * d; val h = H * (0.08f + p[1] * 0.18f)
                drawRect(lerp(bg, Color.Black, 0.55f), Offset(x, H - h), Size(w, h))
                var wy = H - h + 6 * d
                while (wy < H - 8 * d) { var wx = x + 5 * d
                    while (wx < x + w - 6 * d) { if (((wx + wy) * 7).toInt() % 5 == 0) drawRect(m(Color(0xFFFFE08A)).copy(alpha = 0.5f), Offset(wx, wy), Size(3 * d, 3 * d)); wx += 8 * d }
                    wy += 9 * d }
                x += w + 2 * d } }
        "halftone" -> { val step = 14 * d; var y = 0f
            while (y < H) { var x = 0f
                while (x < W) { val f = 1f - ((x / W) + (y / H)) / 2f; if (f > 0.35f) drawCircle(accent.copy(alpha = 0.16f), step * 0.45f * (f - 0.35f) * 1.5f, Offset(x, y)); x += step }
                y += step } }
        "stripes" -> { var x = -H; while (x < W) { drawLine(m(fg).copy(alpha = if (tint != null) 0.08f else 0.04f), Offset(x, H), Offset(x + H, 0f), 10 * d); x += 28 * d } }
        "hexagons" -> { val r = 22 * d; val hh = r * sqrt(3f); var row = 0; var y = 0f
            while (y < H + hh) { var x = if (row % 2 == 0) 0f else r * 1.5f
                while (x < W + r) { val path = Path()
                    for (j in 0..6) { val a = PI.toFloat() / 3 * j; val px = x + r * cos(a); val py = y + r * sin(a); if (j == 0) path.moveTo(px, py) else path.lineTo(px, py) }
                    drawPath(path, m(fg).copy(alpha = if (tint != null) 0.1f else 0.05f), style = Stroke(1 * d)); x += r * 3 }
                y += hh / 2; row++ } }
        "rays" -> for (i in 0 until 9) { val a = -0.6f + i * 0.15f; val path = Path()
            path.moveTo(W / 2, -10 * d); path.lineTo(W / 2 + sin(a - 0.04f) * H * 1.4f, H); path.lineTo(W / 2 + sin(a + 0.04f) * H * 1.4f, H); path.close()
            drawPath(path, Brush.verticalGradient(listOf(light.copy(alpha = 0.10f), Color.Transparent), 0f, H)) }
        "spotlight" -> drawCircle(Brush.radialGradient(listOf(light.copy(alpha = 0.16f), Color.Transparent), Offset(W / 2, H * 0.35f), minOf(W, H) * 0.6f),
            minOf(W, H) * 0.6f, Offset(W / 2, H * 0.35f))
        "sheen" -> drawRect(Brush.linearGradient(listOf(Color.Transparent, m(Color.White).copy(alpha = 0.07f), Color.Transparent),
            Offset(W * 0.2f, 0f), Offset(W * 0.6f, H * 0.4f)))
        "nebula" -> for (i in 0 until 6) { val p = s[i + 150]; val r = (90 + p[2] * 160) * d
            val col = if (tint != null) listOf(tint, light, lerp(tint, Color.Black, 0.3f))[i % 3] else listOf(accent, Color(0xFFFF4FD8), Color(0xFF4F7BFF))[i % 3]
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.14f), Color.Transparent), Offset(p[0] * W, p[1] * H), r), r, Offset(p[0] * W, p[1] * H)) }
        "lava" -> for (i in 0 until 5) { val p = s[i + 160]
            val c = Offset((0.5f + 0.4f * sin(t * 0.07f * (1 + p[2]) + p[3] * 6)) * W, (0.5f + 0.4f * cos(t * 0.05f * (1 + p[0]) + p[1] * 6)) * H)
            val r = (110 + p[2] * 90) * d
            drawCircle(Brush.radialGradient(listOf((if (i % 2 == 0) accent else light).copy(alpha = 0.18f), Color.Transparent), c, r), r, c) }
        "breathe" -> drawRect(accent.copy(alpha = 0.03f + 0.05f * (0.5f + 0.5f * sin(t * 0.6f))))
        "drift" -> { val a = t * 0.05f
            drawRect(Brush.linearGradient(listOf(accent.copy(alpha = 0.14f), Color.Transparent, light.copy(alpha = 0.10f)),
                Offset(W / 2 + cos(a) * W, H / 2 + sin(a) * H), Offset(W / 2 - cos(a) * W, H / 2 - sin(a) * H))) }
        "digital" -> { val cols = (W / (14 * d)).toInt().coerceAtLeast(1)
            for (c in 0 until cols) { val p = s[c % 200]; val head = ((p[1] + t * (0.08f + p[2] * 0.1f)) % 1.3f) * H
                for (j in 0 until 12) { val y = head - j * 14 * d; if (y < 0 || y > H) continue
                    drawRect(m(Color(0xFF3CFF7A)).copy(alpha = (0.35f - j * 0.028f).coerceAtLeast(0f)), Offset(c * 14 * d + 4 * d, y), Size(5 * d, 9 * d)) } } }
        "confetti" -> { val cs = if (tint != null) listOf(tint, light, lerp(tint, Color.Black, 0.3f)) else listOf(accent, Color(0xFFFF5D8F), Color(0xFFFFD34F), Color(0xFF4FD8FF), Color(0xFF8BFF6A))
            for (i in 0 until 60) { val p = s[i + 100]
                rotate(p[3] * 360, Offset(p[0] * W, p[1] * H)) { drawRect(cs[i % cs.size].copy(alpha = 0.35f), Offset(p[0] * W, p[1] * H), Size(6 * d, 3 * d)) } } }
        "constellation" -> { val pts = (0 until 22).map { Offset(s[it + 170][0] * W, s[it + 170][1] * H) }
            for (i in pts.indices) for (j in i + 1 until pts.size) { val a = pts[i]; val b = pts[j]
                if (abs(a.x - b.x) + abs(a.y - b.y) < minOf(W, H) * 0.22f) drawLine(light.copy(alpha = 0.18f), a, b, 0.8f * d) }
            pts.forEach { drawCircle(m(Color.White).copy(alpha = 0.7f), 1.6f * d, it) } }
        "rings" -> for (i in 0 until 4) { val f = ((t * 0.08f + i / 4f) % 1f)
            drawCircle(accent.copy(alpha = 0.18f * (1 - f)), f * maxOf(W, H) * 0.7f, Offset(W / 2, H / 2), style = Stroke(1.5f * d)) }
        "frame" -> { drawRect(accent.copy(alpha = 0.25f), style = Stroke(6 * d)); drawRect(light.copy(alpha = 0.8f), style = Stroke(1.5f * d)) }
        // ---- 3.2.3 ----
        "petals" -> for (i in 0 until 34) { val p = s[i + 10]
            val y = ((p[1] + t * (0.03f + p[2] * 0.04f)) % 1.1f - 0.05f) * H
            val x = p[0] * W + sin(t * 0.7f + p[3] * 6f) * 22 * d; val c = Offset(x, y)
            rotate(t * 40f * (p[2] - 0.5f) + p[3] * 360f, c) {
                drawOval(m(Color(0xFFFFB7D5)).copy(alpha = 0.5f), Offset(x - 5 * d, y - 3 * d), Size(10 * d, 6 * d)) } }
        "leaves" -> { val cs = if (tint != null) listOf(tint, lerp(tint, Color.Black, 0.3f)) else listOf(Color(0xFFE0782A), Color(0xFFC9402A), Color(0xFFE8B23A))
            for (i in 0 until 26) { val p = s[i + 30]
                val y = ((p[1] + t * (0.04f + p[2] * 0.05f)) % 1.1f - 0.05f) * H
                val x = p[0] * W + sin(t * 0.9f + p[3] * 6f) * 30 * d; val c = Offset(x, y)
                rotate(t * 70f * (p[2] - 0.5f) + p[3] * 360f, c) {
                    val path = Path(); path.moveTo(x, y - 7 * d); path.quadraticBezierTo(x + 6 * d, y, x, y + 7 * d); path.quadraticBezierTo(x - 6 * d, y, x, y - 7 * d)
                    drawPath(path, cs[i % cs.size].copy(alpha = 0.55f)) } } }
        "embers" -> for (i in 0 until 46) { val p = s[i + 50]
            val f = ((p[1] + t * (0.05f + p[2] * 0.06f)) % 1f)
            val x = p[0] * W + sin(t * 1.4f + p[3] * 7f) * 10 * d; val y = H * (1f - f * 0.8f)
            drawCircle(Brush.radialGradient(listOf(m(Color(0xFFFF8A2A)).copy(alpha = 0.8f * (1 - f)), Color.Transparent), Offset(x, y), 5 * d), 5 * d, Offset(x, y)) }
        "lightning" -> { val cycle = 9f; val ph = (t % cycle) / cycle
            if (ph < 0.03f || ph in 0.05f..0.07f) {
                drawRect(m(Color.White).copy(alpha = 0.10f))
                val r = java.util.Random((t / cycle).toLong())
                var x = W * (0.2f + r.nextFloat() * 0.6f); var y = 0f; val path = Path(); path.moveTo(x, y)
                while (y < H * 0.7f) { x += (r.nextFloat() - 0.5f) * 40 * d; y += 18 * d + r.nextFloat() * 22 * d; path.lineTo(x, y) }
                drawPath(path, light.copy(alpha = 0.35f), style = Stroke(6 * d)); drawPath(path, m(Color.White).copy(alpha = 0.9f), style = Stroke(1.6f * d))
            } }
        "comets" -> for (i in 0 until 2) { val p = s[i + 4]
            val cycle = 16f + p[2] * 10f; val ph = ((t + p[3] * cycle) % cycle) / cycle
            val head = Offset(W * (1.1f - ph * 1.3f), H * (0.1f + p[1] * 0.3f) + ph * H * 0.15f); val tail = Offset(head.x + 220 * d, head.y - 50 * d)
            drawLine(Brush.linearGradient(listOf(light.copy(alpha = 0.5f), Color.Transparent), head, tail), head, tail, 4 * d)
            drawCircle(Brush.radialGradient(listOf(m(Color.White), Color.Transparent), head, 8 * d), 8 * d, head) }
        "meteors" -> for (i in 0 until 14) { val p = s[i + 120]
            val ph = ((t * (0.5f + p[2] * 0.6f) + p[3] * 5f) % 3f) / 3f
            if (ph < 0.4f) { val f = ph / 0.4f
                val head = Offset(W * (p[0] * 1.2f) - f * W * 0.4f, H * p[1] * 0.6f + f * H * 0.4f); val tail = Offset(head.x + 60 * d, head.y - 60 * d)
                drawLine(Brush.linearGradient(listOf(m(Color.White).copy(alpha = 0.8f * (1 - f)), Color.Transparent), head, tail), head, tail, 1.6f * d) } }
        "moon" -> { val r = minOf(W, H) * 0.12f; val c = Offset(W * 0.8f, H * 0.16f)
            drawCircle(Brush.radialGradient(listOf(light.copy(alpha = 0.22f), Color.Transparent), c, r * 3), r * 3, c)
            drawCircle(m(Color(0xFFF4F1E4)).copy(alpha = 0.85f), r, c)
            for (j in 0 until 5) { val p = s[j + 140]; drawCircle(Color.Black.copy(alpha = 0.06f), r * (0.1f + p[2] * 0.15f), Offset(c.x + (p[0] - 0.5f) * r, c.y + (p[1] - 0.5f) * r)) } }
        "planet" -> { val r = minOf(W, H) * 0.09f; val c = Offset(W * 0.22f, H * 0.2f)
            drawOval(light.copy(alpha = 0.35f), Offset(c.x - r * 2.1f, c.y - r * 0.45f), Size(r * 4.2f, r * 0.9f), style = Stroke(3 * d))
            drawCircle(Brush.linearGradient(listOf(accent.copy(alpha = 0.8f), lerp(accent, Color.Black, 0.6f).copy(alpha = 0.8f)), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)), r, c)
            rotate(0f) { drawArc(light.copy(alpha = 0.5f), 0f, 180f, false, Offset(c.x - r * 2.1f, c.y - r * 0.45f), Size(r * 4.2f, r * 0.9f), style = Stroke(3 * d)) } }
        "clouds" -> for (i in 0 until 6) { val p = s[i + 150]
            val x = ((p[0] + t * (0.004f + p[2] * 0.006f)) % 1.4f - 0.2f) * W; val y = H * (0.06f + p[1] * 0.35f); val r = (40 + p[3] * 50) * d
            for (j in -1..1) drawCircle(m(Color.White).copy(alpha = 0.07f), r * (if (j == 0) 1f else 0.7f), Offset(x + j * r * 0.9f, y + (if (j == 0) 0f else r * 0.2f))) }
        "fog" -> for (b in 0 until 3) { val y = H * (0.72f + b * 0.08f); val x = sin(t * 0.1f + b * 2f) * W * 0.15f
            drawOval(Brush.radialGradient(listOf(m(Color.White).copy(alpha = 0.09f), Color.Transparent), Offset(W / 2 + x, y), W * 0.7f),
                Offset(-W * 0.2f + x, y - H * 0.12f), Size(W * 1.4f, H * 0.24f)) }
        "sea" -> { val hz = H * 0.78f
            drawRect(Brush.verticalGradient(listOf(lerp(bg, accent, 0.25f).copy(alpha = 0.6f), lerp(bg, Color.Black, 0.3f).copy(alpha = 0.8f)), hz, H), Offset(0f, hz), Size(W, H - hz))
            for (j in 0 until 18) { val p = s[j + 60]; val y = hz + (H - hz) * p[1]; val w = (20 + p[2] * 60) * d * (0.3f + p[1])
                val x = W / 2 + (p[0] - 0.5f) * W * 0.3f * (0.3f + p[1]) + sin(t * 0.8f + p[3] * 6f) * 6 * d
                drawLine(light.copy(alpha = 0.25f + 0.2f * sin(t * 2f + p[3] * 9f)), Offset(x - w / 2, y), Offset(x + w / 2, y), 1.5f * d) } }
        "dunes" -> for (b in 0 until 3) { val path = Path(); val base = H * (0.78f + b * 0.07f); path.moveTo(0f, H)
            var x = 0f; while (x <= W) { path.lineTo(x, base - sin(x / W * (2.5f + b) + b * 1.7f) * H * 0.05f); x += 8 * d }
            path.lineTo(W, H); path.close()
            drawPath(path, lerp(m(Color(0xFFD9A066)), bg, 0.35f + b * 0.2f).copy(alpha = 0.55f)) }
        "pines" -> {
            // Two rows of firs standing on the bottom edge (none float above it): the back row taller and fainter, the front
            // darker; each a trunk and three tiers that overlap, so a tree reads as one tree
            val ground = lerp(m(bg), Color.Black, 0.55f)
            for (b in 0 until 2) { val n = 14 + b * 6
                // Solid colours (a see-through one showed every tier's overlap as a darker patch)
                val col = if (tint != null) lerp(tint, Color.Black, 0.35f + b * 0.3f) else lerp(bg, Color.Black, 0.3f + b * 0.35f)
                for (i in 0 until n) { val p = s[(i + 100 + b * 30) % s.size]
                    val x = (i + 0.2f + p[0] * 0.6f) * W / n
                    // (never more than a third of the space up: a small preview gets small trees, not a wall of them)
                    val h = minOf((70 + p[2] * 80) * d, H * (0.22f + p[2] * 0.12f)) * (if (b == 0) 1.15f else 0.85f)
                    val w = h * 0.3f
                    drawRect(col, Offset(x - w * 0.07f, H - h * 0.06f), Size(w * 0.14f, h * 0.06f))   // a short trunk, under the branches
                    for (k in 0 until 3) {   // the tiers, from the bottom up, each smaller and overlapping the one below
                        val bottom = H - h * (0.05f + k * 0.28f); val top = bottom - h * (0.5f - k * 0.06f); val half = w * (1f - k * 0.25f)
                        val path = Path(); path.moveTo(x, top); path.lineTo(x + half, bottom); path.lineTo(x - half, bottom); path.close()
                        drawPath(path, col)
                    } } }
            drawRect(ground.copy(alpha = 0.8f), Offset(0f, H - 3 * d), Size(W, 3 * d)) }
        "graph" -> { val g = 22 * d; var x = 0f; while (x < W) { drawLine(m(fg).copy(alpha = 0.05f), Offset(x, 0f), Offset(x, H), 1f); x += g }
            var y = 0f; while (y < H) { drawLine(m(fg).copy(alpha = 0.05f), Offset(0f, y), Offset(W, y), 1f); y += g } }
        "polka" -> { val g = 26 * d; var row = 0; var y = 0f
            while (y < H + g) { var x = if (row % 2 == 0) 0f else g / 2; while (x < W + g) { drawCircle(m(fg).copy(alpha = 0.06f), 3.5f * d, Offset(x, y)); x += g }; y += g / 2 * 1.7f; row++ } }
        "checker" -> { val g = 30 * d; var j = 0; var y = 0f
            while (y < H) { var i = 0; var x = 0f; while (x < W) { if ((i + j) % 2 == 0) drawRect(m(fg).copy(alpha = 0.035f), Offset(x, y), Size(g, g)); x += g; i++ }; y += g; j++ } }
        "plaid" -> { val g = 60 * d; var x = 0f
            while (x < W) { drawRect(accent.copy(alpha = 0.05f), Offset(x, 0f), Size(18 * d, H)); drawLine(m(fg).copy(alpha = 0.05f), Offset(x + 26 * d, 0f), Offset(x + 26 * d, H), 2 * d); x += g }
            var y = 0f; while (y < H) { drawRect(accent.copy(alpha = 0.05f), Offset(0f, y), Size(W, 18 * d)); drawLine(m(fg).copy(alpha = 0.05f), Offset(0f, y + 26 * d), Offset(W, y + 26 * d), 2 * d); y += g } }
        "circuit" -> for (i in 0 until 26) { val p = s[i + 20]
            var x = p[0] * W; var y = p[1] * H; val path = Path(); path.moveTo(x, y)
            repeat(3) { k -> if ((k + i) % 2 == 0) x += (p[2] - 0.5f) * 160 * d else y += (p[3] - 0.5f) * 160 * d; path.lineTo(x, y) }
            drawPath(path, accent.copy(alpha = 0.16f), style = Stroke(1.2f * d)); drawCircle(accent.copy(alpha = 0.3f), 2.5f * d, Offset(x, y)) }
        "topo" -> for (b in 0 until 12) { val path = Path(); val base = H * (b + 0.5f) / 12f; path.moveTo(0f, base)
            var x = 0f; while (x <= W) { path.lineTo(x, base + sin(x / W * 5f + b * 0.6f) * H * 0.03f + cos(x / W * 11f + b) * H * 0.01f); x += 6 * d }
            drawPath(path, m(fg).copy(alpha = 0.06f), style = Stroke(1 * d)) }
        "burst" -> rotate(t * 3f, Offset(W / 2, H / 2)) { val n = 24; val r = maxOf(W, H)
            for (i in 0 until n step 2) { val a0 = i * 2 * PI.toFloat() / n; val a1 = (i + 1) * 2 * PI.toFloat() / n
                val path = Path(); path.moveTo(W / 2, H / 2); path.lineTo(W / 2 + cos(a0) * r, H / 2 + sin(a0) * r); path.lineTo(W / 2 + cos(a1) * r, H / 2 + sin(a1) * r); path.close()
                drawPath(path, accent.copy(alpha = 0.04f)) } }
        "prism" -> { val cs = listOf(Color(0xFFFF4B4B), Color(0xFFFF9F3B), Color(0xFFFFE14D), Color(0xFF4DFF7A), Color(0xFF3FA9FF), Color(0xFF8C5BFF))
            cs.forEachIndexed { i, c -> drawLine((if (tint != null) lerp(c, tint, 0.5f) else c).copy(alpha = 0.10f), Offset(0f, H * 0.35f + i * 7 * d), Offset(W, H * 0.55f + i * 14 * d), 7 * d) } }
        "sparkle" -> for (i in 0 until 26) { val p = s[i + 80]
            val a = (0.5f + 0.5f * sin(t * (1f + p[2] * 2f) + p[3] * 6.28f)).let { it * it }
            val c = Offset(p[0] * W, p[1] * H); val r = (4 + p[2] * 7) * d * a
            drawLine(m(Color.White).copy(alpha = a * 0.8f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.2f * d)
            drawLine(m(Color.White).copy(alpha = a * 0.8f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.2f * d) }
        "hearts" -> for (i in 0 until 16) { val p = s[i + 130]
            val f = ((p[1] + t * (0.03f + p[2] * 0.03f)) % 1f); val x = p[0] * W + sin(t + p[3] * 6f) * 14 * d; val y = H * (1.05f - f * 1.1f); val r = (6 + p[2] * 8) * d
            val path = Path(); path.moveTo(x, y + r); path.cubicTo(x - r * 1.6f, y - r * 0.2f, x - r * 0.6f, y - r * 1.4f, x, y - r * 0.4f)
            path.cubicTo(x + r * 0.6f, y - r * 1.4f, x + r * 1.6f, y - r * 0.2f, x, y + r); drawPath(path, m(Color(0xFFFF5D8F)).copy(alpha = 0.4f * (1 - f))) }
        "notes" -> for (i in 0 until 14) { val p = s[i + 160]
            val f = ((p[1] + t * (0.025f + p[2] * 0.03f)) % 1f); val x = p[0] * W + sin(t * 0.8f + p[3] * 6f) * 16 * d; val y = H * (1.05f - f * 1.1f); val r = (5 + p[2] * 4) * d
            val col = (if (i % 2 == 0) accent else light).copy(alpha = 0.45f * (1 - f))
            drawOval(col, Offset(x - r, y - r * 0.7f), Size(r * 2, r * 1.4f)); drawLine(col, Offset(x + r * 0.9f, y), Offset(x + r * 0.9f, y - r * 3.2f), 1.6f * d)
            drawLine(col, Offset(x + r * 0.9f, y - r * 3.2f), Offset(x + r * 2f, y - r * 2.4f), 1.6f * d) }
        "orbs" -> for (i in 0 until 7) { val p = s[i + 190]
            val c = Offset((p[0] + 0.12f * sin(t * 0.07f + p[3] * 9)) * W, (p[1] + 0.12f * cos(t * 0.06f + p[2] * 9)) * H); val r = (50 + p[2] * 90) * d
            drawCircle(Brush.radialGradient(listOf((if (i % 2 == 0) accent else light).copy(alpha = 0.18f), Color.Transparent), c, r), r, c) }
        "spiral" -> rotate(t * 2f, Offset(W / 2, H / 2)) { for (arm in 0 until 2) for (j in 0 until 90) { val f = j / 90f
            val a = arm * PI.toFloat() + f * 9f; val r = f * minOf(W, H) * 0.45f
            drawCircle(light.copy(alpha = 0.22f * (1 - f)), (1f + (1 - f) * 2f) * d, Offset(W / 2 + cos(a) * r, H / 2 + sin(a) * r * 0.6f)) } }
        "crt" -> { drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.05f), Color.Transparent, m(Color.Black).copy(alpha = 0.5f)), Offset(W / 2, H / 2), maxOf(W, H) * 0.75f))
            drawRoundRect(m(Color.Black).copy(alpha = 0.6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(40 * d), style = Stroke(14 * d)) }
        "glitch" -> { val slot = (t * 3f).toInt(); val r = java.util.Random(slot.toLong())
            if (r.nextFloat() < 0.35f) repeat(3) { val y = r.nextFloat() * H; val h = (4 + r.nextFloat() * 18) * d
                drawRect((if (it % 2 == 0) Color(0xFFFF2A6D) else Color(0xFF14E4FF)).copy(alpha = 0.18f), Offset((r.nextFloat() - 0.5f) * 30 * d, y), Size(W, h)) } }
        "warp" -> for (i in 0 until 60) { val p = s[i + 40]
            val f = ((p[1] + t * (0.15f + p[2] * 0.2f)) % 1f); val a = p[0] * 2 * PI.toFloat(); val reach = sqrt(W * W + H * H) / 2
            val r0 = f * f * reach; val r1 = r0 + (6 + f * 40) * d; val c = Offset(W / 2, H / 2)
            drawLine(m(Color.White).copy(alpha = 0.6f * f), Offset(c.x + cos(a) * r0, c.y + sin(a) * r0), Offset(c.x + cos(a) * r1, c.y + sin(a) * r1), (0.6f + f * 1.6f) * d) }
        "sunbeams" -> { val o = Offset(W * 0.95f, -H * 0.05f)
            for (i in 0 until 6) { val a = (100f + i * 9f + sin(t * 0.2f + i) * 2f) * PI.toFloat() / 180f; val r = maxOf(W, H) * 1.4f
                val path = Path(); path.moveTo(o.x, o.y); path.lineTo(o.x + cos(a) * r, o.y + sin(a) * r); path.lineTo(o.x + cos(a + 0.05f) * r, o.y + sin(a + 0.05f) * r); path.close()
                drawPath(path, Brush.linearGradient(listOf(m(Color(0xFFFFE6A8)).copy(alpha = 0.14f), Color.Transparent), o, Offset(o.x + cos(a) * r, o.y + sin(a) * r))) } }
    }
}
