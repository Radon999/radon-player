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
    )
    fun moves(fx: Set<String>) = ALL.any { it.moves && it.key in fx }

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
        val p = picked()
        C.stars = "stars" in p
        C.fx = p - "stars"
    }
    fun setOwn(on: Boolean) { org.omarchy.music.Prefs.set(OWN, on); org.omarchy.music.Music.applyLook() }
    fun toggle(k: String) {
        val p = picked().let { if (k in it) it - k else it + k }
        org.omarchy.music.Prefs.setText(PICK, p.joinToString(";"))
        org.omarchy.music.Music.applyLook()
    }
}

/** The look's effects over the background (C.fx in the colours of the look in use). */
@Composable
fun LookEffects(fx: Set<String> = C.fx, modifier: Modifier = Modifier.fillMaxSize()) {
    if (fx.isEmpty()) return
    val accent = C.accent; val fg = C.fg; val bg = C.bg
    // The time is read while drawing, not while composing: a moving effect redraws each frame without recomposing anything
    val time: androidx.compose.runtime.State<Float>? = if (LookFx.moves(fx)) {
        val tr = rememberInfiniteTransition(label = "fx")
        tr.animateFloat(0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    } else null
    val seeds = remember { java.util.Random(11).let { r -> List(200) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    val keys = remember(fx) { LookFx.ALL.map { it.key }.filter { it in fx } }
    Canvas(modifier) {
        val t = time?.value ?: 0f
        for (k in keys) drawFx(k, t, seeds, accent, fg, bg)
    }
}

private fun DrawScope.drawFx(k: String, t: Float, s: List<FloatArray>, accent: Color, fg: Color, bg: Color) {
    val W = size.width; val H = size.height; val d = density
    val light = lerp(accent, Color.White, 0.5f)
    when (k) {
        "twinkle" -> for (i in 0 until 90) { val p = s[i]
            val a = 0.15f + 0.75f * (0.5f + 0.5f * sin(t * (0.6f + p[2] * 1.4f) + p[3] * 6.28f))
            drawCircle(Color.White.copy(alpha = a), (0.6f + p[2] * 1.6f) * d, Offset(p[0] * W, p[1] * H)) }
        "shooting" -> for (i in 0 until 3) { val p = s[i]
            val cycle = 7f + p[2] * 6f; val ph = ((t + p[3] * cycle) % cycle) / 1.2f
            if (ph < 1f) {
                val x0 = p[0] * W * 0.7f + W * 0.3f; val y0 = p[1] * H * 0.4f
                val len = 140 * d; val head = Offset(x0 - ph * W * 0.5f, y0 + ph * W * 0.25f)
                drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 1 - ph)), Offset(head.x + len, head.y - len / 2), head),
                    Offset(head.x + len, head.y - len / 2), head, 2 * d)
            } }
        "aurora" -> for (b in 0 until 3) {
            val path = Path(); val base = H * (0.12f + b * 0.07f)
            path.moveTo(0f, base)
            var x = 0f
            while (x <= W) { path.lineTo(x, base + sin(x / W * 6f + t * 0.3f + b) * H * 0.04f); x += 8 * d }
            path.lineTo(W, base + H * 0.18f); path.lineTo(0f, base + H * 0.18f); path.close()
            val col = if (b == 1) Color(0xFF3CFFB4) else accent
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
        "scanlines" -> { var y = 0f; while (y < H) { drawLine(Color.Black.copy(alpha = 0.16f), Offset(0f, y), Offset(W, y), 1 * d); y += 3 * d } }
        "grain" -> { val r = java.util.Random(5); repeat(1400) {
            drawRect((if (r.nextBoolean()) Color.White else Color.Black).copy(alpha = 0.05f + r.nextFloat() * 0.06f),
                Offset(r.nextFloat() * W, r.nextFloat() * H), Size(1.2f * d, 1.2f * d)) } }
        "vignette" -> drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), Offset(W / 2, H / 2),
            sqrt(W * W + H * H) / 1.6f))
        "bokeh" -> for (i in 0 until 14) { val p = s[i + 20]
            val r = (30 + p[2] * 70) * d
            drawCircle(Brush.radialGradient(listOf((if (i % 2 == 0) accent else fg).copy(alpha = 0.16f), Color.Transparent), Offset(p[0] * W, p[1] * H), r),
                r, Offset(p[0] * W, p[1] * H)) }
        "rain" -> for (i in 0 until 80) { val p = s[i]
            val speed = 0.6f + p[2]; val y = ((p[1] + t * speed * 0.5f) % 1.2f - 0.1f) * H; val x = p[0] * W
            drawLine(fg.copy(alpha = 0.18f), Offset(x, y), Offset(x - 3 * d, y + 16 * d), 1 * d) }
        "snow" -> for (i in 0 until 70) { val p = s[i + 40]
            val y = ((p[1] + t * (0.03f + p[2] * 0.05f)) % 1.1f - 0.05f) * H
            val x = p[0] * W + sin(t * 0.8f + p[3] * 6f) * 12 * d
            drawCircle(Color.White.copy(alpha = 0.55f), (1.2f + p[2] * 2.2f) * d, Offset(x, y)) }
        "fireflies" -> for (i in 0 until 24) { val p = s[i + 60]
            val x = (p[0] + 0.05f * sin(t * 0.3f + p[3] * 9)) * W; val y = (p[1] + 0.05f * cos(t * 0.25f + p[2] * 9)) * H
            val a = 0.3f + 0.6f * (0.5f + 0.5f * sin(t * 1.3f + p[3] * 7))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF27A).copy(alpha = a), Color.Transparent), Offset(x, y), 10 * d), 10 * d, Offset(x, y)) }
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
            drawPath(path, lerp(bg, Color.Black, 0.25f + b * 0.2f).copy(alpha = 0.85f))
        }
        "skyline" -> { var x = 0f; var i = 0
            while (x < W) { val p = s[(i++) % 200]; val w = (24 + p[0] * 40) * d; val h = H * (0.08f + p[1] * 0.18f)
                drawRect(lerp(bg, Color.Black, 0.55f), Offset(x, H - h), Size(w, h))
                var wy = H - h + 6 * d
                while (wy < H - 8 * d) { var wx = x + 5 * d
                    while (wx < x + w - 6 * d) { if (((wx + wy) * 7).toInt() % 5 == 0) drawRect(Color(0xFFFFE08A).copy(alpha = 0.5f), Offset(wx, wy), Size(3 * d, 3 * d)); wx += 8 * d }
                    wy += 9 * d }
                x += w + 2 * d } }
        "halftone" -> { val step = 14 * d; var y = 0f
            while (y < H) { var x = 0f
                while (x < W) { val f = 1f - ((x / W) + (y / H)) / 2f; if (f > 0.35f) drawCircle(accent.copy(alpha = 0.16f), step * 0.45f * (f - 0.35f) * 1.5f, Offset(x, y)); x += step }
                y += step } }
        "stripes" -> { var x = -H; while (x < W) { drawLine(fg.copy(alpha = 0.04f), Offset(x, H), Offset(x + H, 0f), 10 * d); x += 28 * d } }
        "hexagons" -> { val r = 22 * d; val hh = r * sqrt(3f); var row = 0; var y = 0f
            while (y < H + hh) { var x = if (row % 2 == 0) 0f else r * 1.5f
                while (x < W + r) { val path = Path()
                    for (j in 0..6) { val a = PI.toFloat() / 3 * j; val px = x + r * cos(a); val py = y + r * sin(a); if (j == 0) path.moveTo(px, py) else path.lineTo(px, py) }
                    drawPath(path, fg.copy(alpha = 0.05f), style = Stroke(1 * d)); x += r * 3 }
                y += hh / 2; row++ } }
        "rays" -> for (i in 0 until 9) { val a = -0.6f + i * 0.15f; val path = Path()
            path.moveTo(W / 2, -10 * d); path.lineTo(W / 2 + sin(a - 0.04f) * H * 1.4f, H); path.lineTo(W / 2 + sin(a + 0.04f) * H * 1.4f, H); path.close()
            drawPath(path, Brush.verticalGradient(listOf(light.copy(alpha = 0.10f), Color.Transparent), 0f, H)) }
        "spotlight" -> drawCircle(Brush.radialGradient(listOf(light.copy(alpha = 0.16f), Color.Transparent), Offset(W / 2, H * 0.35f), minOf(W, H) * 0.6f),
            minOf(W, H) * 0.6f, Offset(W / 2, H * 0.35f))
        "sheen" -> drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent),
            Offset(W * 0.2f, 0f), Offset(W * 0.6f, H * 0.4f)))
        "nebula" -> for (i in 0 until 6) { val p = s[i + 150]; val r = (90 + p[2] * 160) * d
            val col = listOf(accent, Color(0xFFFF4FD8), Color(0xFF4F7BFF))[i % 3]
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
                    drawRect(Color(0xFF3CFF7A).copy(alpha = (0.35f - j * 0.028f).coerceAtLeast(0f)), Offset(c * 14 * d + 4 * d, y), Size(5 * d, 9 * d)) } } }
        "confetti" -> { val cs = listOf(accent, Color(0xFFFF5D8F), Color(0xFFFFD34F), Color(0xFF4FD8FF), Color(0xFF8BFF6A))
            for (i in 0 until 60) { val p = s[i + 100]
                rotate(p[3] * 360, Offset(p[0] * W, p[1] * H)) { drawRect(cs[i % cs.size].copy(alpha = 0.35f), Offset(p[0] * W, p[1] * H), Size(6 * d, 3 * d)) } } }
        "constellation" -> { val pts = (0 until 22).map { Offset(s[it + 170][0] * W, s[it + 170][1] * H) }
            for (i in pts.indices) for (j in i + 1 until pts.size) { val a = pts[i]; val b = pts[j]
                if (abs(a.x - b.x) + abs(a.y - b.y) < minOf(W, H) * 0.22f) drawLine(light.copy(alpha = 0.18f), a, b, 0.8f * d) }
            pts.forEach { drawCircle(Color.White.copy(alpha = 0.7f), 1.6f * d, it) } }
        "rings" -> for (i in 0 until 4) { val f = ((t * 0.08f + i / 4f) % 1f)
            drawCircle(accent.copy(alpha = 0.18f * (1 - f)), f * maxOf(W, H) * 0.7f, Offset(W / 2, H / 2), style = Stroke(1.5f * d)) }
        "frame" -> { drawRect(accent.copy(alpha = 0.25f), style = Stroke(6 * d)); drawRect(light.copy(alpha = 0.8f), style = Stroke(1.5f * d)) }
    }
}
