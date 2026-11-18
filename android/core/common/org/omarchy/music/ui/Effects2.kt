package org.omarchy.music.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** 4.0.14: thirty more effects, one made for each of the thirty new looks (and free to use with any look, as every
 *  effect is). They are things, not weather: glass panes, folded paper, flags, fruit, stone. Drawn by [drawFx2]. */
internal val MORE_FX: List<LookFx.Fx> = listOf(
    LookFx.Fx("mosaic", "Stained glass", "Panes of coloured glass held in dark lead"),
    LookFx.Fx("patina", "Patina", "Sea-green speckles creeping in from the corners, as on old copper"),
    LookFx.Fx("draft", "Drafting lines", "A blueprint's grid, a measured line and a compass circle"),
    LookFx.Fx("chips", "Terrazzo chips", "Little chips of coloured stone scattered all over"),
    LookFx.Fx("fans", "Deco fans", "Rows of art-deco fans along the bottom"),
    LookFx.Fx("shapes", "Bauhaus shapes", "A big circle, a square, a triangle and two bold lines"),
    LookFx.Fx("swirls", "Cloud curls", "Curled clouds, as carved in jade"),
    LookFx.Fx("coral", "Coral", "Coral branching up from the bottom"),
    LookFx.Fx("drops", "Resin drops", "Slow golden drops running down", true),
    LookFx.Fx("blots", "Ink blots", "Blots of ink soaking into the page"),
    LookFx.Fx("weave", "Denim weave", "A fine diagonal weave with stitching along the edges"),
    LookFx.Fx("drapes", "Stage curtain", "The folds of a heavy curtain, gathered along the top"),
    LookFx.Fx("icicles", "Icicles", "Icicles hanging from the top edge"),
    LookFx.Fx("facets", "Facets", "Flat angled faces, like cut glass or stone"),
    LookFx.Fx("slices", "Fruit slices", "Slices of orange, lime and grapefruit"),
    LookFx.Fx("bunting", "Bunting", "Strings of little flags across the top"),
    LookFx.Fx("cranes", "Paper cranes", "Folded paper birds"),
    LookFx.Fx("veins", "Marble veins", "Fine veins wandering through marble"),
    LookFx.Fx("pebbles", "Pebbles and moss", "River stones along the bottom, some with moss"),
    LookFx.Fx("feathers", "Peacock feathers", "A fan of peacock feathers rising from the bottom"),
    LookFx.Fx("beam", "Lighthouse beam", "A beam of light sweeping slowly round", true),
    LookFx.Fx("steam", "Steam", "Wisps of steam curling up", true),
    LookFx.Fx("scribbles", "Crayon scribbles", "Zigzags and loops in crayon"),
    LookFx.Fx("lanterns", "Paper lanterns", "Glowing lanterns floating up", true),
    LookFx.Fx("pixels", "Falling blocks", "Blocks dropping down, a step at a time", true),
    LookFx.Fx("spores", "Glowing mushrooms", "Mushrooms glowing along the bottom, their spores drifting up", true),
    LookFx.Fx("shimmer", "Opal shimmer", "Soft bands of colour sliding across", true),
    LookFx.Fx("rivets", "Rivets", "Steel plates joined with rows of rivets"),
    LookFx.Fx("eddy", "Swirl", "One big slow swirl, like wine turned in a glass", true),
    LookFx.Fx("kites", "Kites", "Kites drifting on their strings", true),
)

/** The effect's own colour when it has one (lighter and darker shades of it), else each of its usual colours in turn. */
private fun many(i: Int, tint: Color?, vararg cs: Long): Color =
    tint?.let { lerp(it, if (i % 2 == 0) Color.White else Color.Black, (i % 3) * 0.16f) } ?: Color(cs[((i % cs.size) + cs.size) % cs.size])

/** Draws k if it is one of [MORE_FX] (true), else nothing (false). accent: the look's, or the effect's own colour. */
internal fun DrawScope.drawFx2(k: String, t: Float, s: List<FloatArray>, accent: Color, fg: Color, bg: Color, tint: Color?): Boolean {
    val W = size.width; val H = size.height; val u = density
    fun m(def: Color) = tint ?: def
    val tau = 2f * PI.toFloat()
    when (k) {
        "mosaic" -> { val cols = 7; val rows = 10; val cw = W / cols; val ch = H / rows
            for (r in 0 until rows) for (c in 0 until cols) { val p = s[(r * cols + c) % s.size]
                if (p[2] < 0.42f) continue
                val x = c * cw; val y = r * ch; val path = Path()
                path.moveTo(x + p[0] * cw * 0.3f, y); path.lineTo(x + cw, y + p[1] * ch * 0.3f)
                path.lineTo(x + cw - p[3] * cw * 0.3f, y + ch); path.lineTo(x, y + ch - p[0] * ch * 0.3f); path.close()
                drawPath(path, many(r * 3 + c, tint, 0xFFE23D5A, 0xFF2F8FFF, 0xFF37D67A, 0xFFFFD23F, 0xFF9B5CFF).copy(alpha = 0.10f + 0.12f * p[3]))
                drawPath(path, Color.Black.copy(alpha = 0.4f), style = Stroke(1.6f * u)) } }
        "patina" -> for (i in 0 until 140) { val p = s[i]
            // towards the corners: both coordinates pushed to an edge
            val x = (if (p[2] > 0.5f) p[0] * p[0] else 1f - p[0] * p[0]) * W; val y = (if (p[3] > 0.5f) p[1] * p[1] else 1f - p[1] * p[1]) * H
            drawCircle(m(Color(0xFF3FD0B0)).copy(alpha = 0.04f + 0.10f * p[2]), (2f + p[3] * 16f) * u, Offset(x, y)) }
        "draft" -> { val c = m(Color.White); val step = 48f * u
            var x = 0f; while (x < W) { drawLine(c.copy(alpha = 0.07f), Offset(x, 0f), Offset(x, H), 1f); x += step }
            var y = 0f; while (y < H) { drawLine(c.copy(alpha = 0.07f), Offset(0f, y), Offset(W, y), 1f); y += step }
            // a measured line with its end ticks, a compass circle with a cross, and a dashed diagonal
            val a = Offset(W * 0.08f, H * 0.14f); val b = Offset(W * 0.62f, H * 0.14f)
            drawLine(c.copy(alpha = 0.35f), a, b, 1.2f * u)
            for (e in listOf(a, b)) drawLine(c.copy(alpha = 0.35f), Offset(e.x, e.y - 7 * u), Offset(e.x, e.y + 7 * u), 1.2f * u)
            val o = Offset(W * 0.74f, H * 0.7f); val r = minOf(W, H) * 0.2f
            drawCircle(c.copy(alpha = 0.28f), r, o, style = Stroke(1.2f * u)); drawCircle(c.copy(alpha = 0.16f), r * 0.55f, o, style = Stroke(1f * u))
            drawLine(c.copy(alpha = 0.22f), Offset(o.x - r * 1.2f, o.y), Offset(o.x + r * 1.2f, o.y), 1f * u)
            drawLine(c.copy(alpha = 0.22f), Offset(o.x, o.y - r * 1.2f), Offset(o.x, o.y + r * 1.2f), 1f * u)
            drawLine(c.copy(alpha = 0.25f), Offset(W * 0.1f, H * 0.9f), Offset(W * 0.55f, H * 0.4f), 1.2f * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * u, 7f * u))) }
        "chips" -> for (i in 0 until 90) { val p = s[i]; val o = Offset(p[0] * W, p[1] * H); val r = (4f + p[2] * 11f) * u; val a0 = p[3] * tau
            val path = Path(); val n = 3 + (i % 3)
            for (j in 0 until n) { val a = a0 + j * tau / n; val rr = r * (0.6f + 0.4f * s[(i + j * 7) % s.size][0])
                if (j == 0) path.moveTo(o.x + cos(a) * rr, o.y + sin(a) * rr) else path.lineTo(o.x + cos(a) * rr, o.y + sin(a) * rr) }
            path.close(); drawPath(path, many(i, tint, 0xFFC8553D, 0xFF3D7A8A, 0xFFE2A53B, 0xFF8C6BB1, 0xFF5A5A5A).copy(alpha = 0.32f)) }
        "fans" -> { val r = minOf(W, H) * 0.2f; val c = m(Color(0xFFE8C25A))
            for (row in 0 until 3) { val y = H - row * r * 0.62f; var x = if (row % 2 == 0) 0f else -r
                while (x < W + r) { val o = Offset(x, y)
                    for (j in 1..4) drawArc(c.copy(alpha = 0.26f - row * 0.06f), 180f, 180f, false, Offset(o.x - r * j / 4f, o.y - r * j / 4f), Size(r * j / 2f, r * j / 2f), style = Stroke(1.3f * u))
                    for (j in 1..5) { val a = PI.toFloat() + j * PI.toFloat() / 6f; drawLine(c.copy(alpha = 0.16f - row * 0.03f), o, Offset(o.x + cos(a) * r, o.y + sin(a) * r), 1f * u) }
                    x += r * 2f } } }
        "shapes" -> { val r = minOf(W, H)
            drawCircle(many(0, tint, 0xFFE63329).copy(alpha = 0.22f), r * 0.3f, Offset(W * 0.78f, H * 0.2f))
            rotate(18f, Offset(W * 0.2f, H * 0.55f)) { drawRect(many(1, tint, 0xFF1F5FBF, 0xFF1F5FBF).copy(alpha = 0.2f), Offset(W * 0.2f - r * 0.16f, H * 0.55f - r * 0.16f), Size(r * 0.32f, r * 0.32f)) }
            val tri = Path(); tri.moveTo(W * 0.5f, H * 0.68f); tri.lineTo(W * 0.82f, H * 0.95f); tri.lineTo(W * 0.3f, H * 0.95f); tri.close()
            drawPath(tri, many(2, tint, 0xFFF5C518, 0xFFF5C518, 0xFFF5C518).copy(alpha = 0.24f))
            drawLine(fg.copy(alpha = 0.3f), Offset(W * 0.05f, H * 0.32f), Offset(W * 0.6f, H * 0.12f), 5f * u)
            drawLine(fg.copy(alpha = 0.2f), Offset(W * 0.55f, H * 0.5f), Offset(W * 0.97f, H * 0.62f), 2.5f * u)
            drawCircle(fg.copy(alpha = 0.25f), r * 0.07f, Offset(W * 0.15f, H * 0.82f), style = Stroke(3f * u)) }
        "swirls" -> for (i in 0 until 16) { val p = s[i]; val o = Offset(p[0] * W, p[1] * H); val r = (16f + p[2] * 26f) * u; val c = m(Color(0xFF9FE8B8))
            for (j in 0 until 3) { val rr = r * (1f - j * 0.3f)
                drawArc(c.copy(alpha = 0.22f - j * 0.05f), 90f * j + p[3] * 90f, 250f, false, Offset(o.x - rr + j * r * 0.14f, o.y - rr), Size(rr * 2, rr * 2), style = Stroke((2.2f - j * 0.5f) * u)) }
            drawLine(c.copy(alpha = 0.16f), Offset(o.x - r, o.y + r), Offset(o.x + r * 1.6f, o.y + r), 1.4f * u) }
        "coral" -> { val c = m(Color(0xFFFF7A6B))
            fun branch(o: Offset, a: Float, len: Float, depth: Int, seed: Int) {
                val e = Offset(o.x + cos(a) * len, o.y + sin(a) * len)
                drawLine(c.copy(alpha = 0.16f + 0.07f * depth), o, e, (1f + depth * 0.9f) * u)
                if (depth == 0) { drawCircle(c.copy(alpha = 0.4f), 2.2f * u, e); return }
                val p = s[seed % s.size]
                branch(e, a - 0.35f - p[0] * 0.3f, len * 0.72f, depth - 1, seed * 2 + 1)
                branch(e, a + 0.3f + p[1] * 0.35f, len * 0.7f, depth - 1, seed * 2 + 2)
            }
            for (i in 0 until 6) { val p = s[i + 20]; branch(Offset((i + 0.5f) * W / 6f + (p[0] - 0.5f) * W * 0.08f, H), -PI.toFloat() / 2f + (p[1] - 0.5f) * 0.4f, H * (0.07f + p[2] * 0.05f), 4, i + 3) } }
        "drops" -> for (i in 0 until 16) { val p = s[i]; val c = m(Color(0xFFFFB000))
            val cyc = 40f + p[2] * 50f; val ph = ((t + p[3] * cyc) % cyc) / cyc
            val x = p[0] * W; val y = ph * (H + 60 * u) - 30 * u; val r = (4f + p[2] * 6f) * u
            drawLine(c.copy(alpha = 0.10f), Offset(x, 0f), Offset(x, y - r), 1.5f * u)
            val path = Path(); path.moveTo(x, y - r * 2.4f); path.lineTo(x + r * 0.8f, y - r * 0.4f); path.lineTo(x - r * 0.8f, y - r * 0.4f); path.close()
            drawPath(path, c.copy(alpha = 0.34f)); drawCircle(c.copy(alpha = 0.34f), r, Offset(x, y))
            drawCircle(Color.White.copy(alpha = 0.35f), r * 0.28f, Offset(x - r * 0.3f, y - r * 0.3f)) }
        "blots" -> for (i in 0 until 9) { val p = s[i]; val o = Offset(p[0] * W, p[1] * H); val r = (26f + p[2] * 50f) * u; val c = m(fg)
            for (j in 0 until 7) { val q = s[(i * 11 + j) % s.size]; val a = q[0] * tau
                drawCircle(c.copy(alpha = 0.05f + 0.03f * q[2]), r * (0.35f + q[1] * 0.5f), Offset(o.x + cos(a) * r * 0.5f * q[3], o.y + sin(a) * r * 0.5f * q[3])) }
            for (j in 0 until 6) { val q = s[(i * 17 + j + 60) % s.size]; val a = q[0] * tau
                drawCircle(c.copy(alpha = 0.16f), (0.8f + q[2] * 1.8f) * u, Offset(o.x + cos(a) * r * (1f + q[1]), o.y + sin(a) * r * (1f + q[1]))) } }
        "weave" -> { val step = 5f * u; var x = -H
            while (x < W) { drawLine(fg.copy(alpha = 0.05f), Offset(x, 0f), Offset(x + H, H), 1f); x += step }
            val c = m(Color(0xFFF2A93B)); val dash = PathEffect.dashPathEffect(floatArrayOf(9f * u, 6f * u))
            for (yy in listOf(10f * u, 16f * u, H - 10f * u, H - 16f * u)) drawLine(c.copy(alpha = 0.5f), Offset(8f * u, yy), Offset(W - 8f * u, yy), 1.6f * u, pathEffect = dash) }
        "drapes" -> { val folds = 11; val fw = W / folds
            for (i in 0 until folds) { val x = i * fw
                drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.26f), Color.Transparent, Color.White.copy(alpha = 0.05f), Color.Transparent, Color.Black.copy(alpha = 0.26f)), x, x + fw), Offset(x, 0f), Size(fw, H)) }
            val c = m(Color(0xFFF2C14E)); val sw = W / 4f
            for (i in 0 until 4) drawArc(c.copy(alpha = 0.3f), 0f, 180f, false, Offset(i * sw, -H * 0.05f), Size(sw, H * 0.1f), style = Stroke(3f * u))
            drawLine(c.copy(alpha = 0.35f), Offset(0f, 2f * u), Offset(W, 2f * u), 4f * u) }
        "icicles" -> for (i in 0 until 30) { val p = s[i]; val w0 = W / 30f; val x = i * w0; val len = H * (0.04f + p[1] * p[1] * 0.2f); val c = m(Color(0xFFBFF3FF))
            val path = Path(); path.moveTo(x, 0f); path.lineTo(x + w0, 0f); path.lineTo(x + w0 * (0.3f + p[0] * 0.4f), len); path.close()
            drawPath(path, Brush.verticalGradient(listOf(c.copy(alpha = 0.5f), c.copy(alpha = 0.08f)), 0f, len))
            drawLine(Color.White.copy(alpha = 0.3f), Offset(x + w0 * 0.3f, 0f), Offset(x + w0 * (0.3f + p[0] * 0.3f), len * 0.8f), 1f) }
        "facets" -> { val cols = 4; val rows = 6
            fun pt(c: Int, r: Int): Offset { val p = s[(r * (cols + 1) + c + 40) % s.size]
                val jx = if (c == 0 || c == cols) 0f else (p[0] - 0.5f) * 0.6f; val jy = if (r == 0 || r == rows) 0f else (p[1] - 0.5f) * 0.6f
                return Offset((c + jx) * W / cols, (r + jy) * H / rows) }
            for (r in 0 until rows) for (c in 0 until cols) { val q = s[(r * cols + c + 90) % s.size]
                val a = pt(c, r); val b = pt(c + 1, r); val e = pt(c, r + 1); val f = pt(c + 1, r + 1)
                val t1 = Path(); t1.moveTo(a.x, a.y); t1.lineTo(b.x, b.y); t1.lineTo(e.x, e.y); t1.close()
                val t2 = Path(); t2.moveTo(b.x, b.y); t2.lineTo(f.x, f.y); t2.lineTo(e.x, e.y); t2.close()
                drawPath(t1, (if (q[0] > 0.85f) m(accent) else Color.White).copy(alpha = 0.02f + 0.07f * q[1]))
                drawPath(t2, Color.Black.copy(alpha = 0.05f + 0.16f * q[2]))
                drawPath(t1, Color.White.copy(alpha = 0.07f), style = Stroke(1f)) } }
        "slices" -> for (i in 0 until 9) { val p = s[i + 5]; val o = Offset(p[0] * W, p[1] * H); val r = (22f + p[2] * 30f) * u
            val c = many(i, tint, 0xFFFF9F1C, 0xFF8AC926, 0xFFE8336D, 0xFFFFD23F)
            drawCircle(c.copy(alpha = 0.3f), r, o); drawCircle(Color.White.copy(alpha = 0.35f), r * 0.86f, o); drawCircle(c.copy(alpha = 0.22f), r * 0.8f, o)
            for (j in 0 until 8) { val a = p[3] * tau + j * tau / 8f; drawLine(Color.White.copy(alpha = 0.5f), o, Offset(o.x + cos(a) * r * 0.8f, o.y + sin(a) * r * 0.8f), 1.5f * u) }
            drawCircle(Color.White.copy(alpha = 0.5f), r * 0.1f, o) }
        "bunting" -> for (row in 0 until 2) { val y0 = H * (0.05f + row * 0.09f); val sag = H * 0.05f; val n = 12 + row * 3
            fun at(f: Float) = Offset(f * W, y0 + sag * 4f * f * (1f - f))
            val line = Path(); line.moveTo(0f, y0); for (j in 1..40) { val o = at(j / 40f); line.lineTo(o.x, o.y) }
            drawPath(line, fg.copy(alpha = 0.3f), style = Stroke(1.2f * u))
            for (j in 0 until n) { val a = at((j + 0.15f) / n); val b = at((j + 0.85f) / n); val tip = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f + W / n * 0.9f)
                val f = Path(); f.moveTo(a.x, a.y); f.lineTo(b.x, b.y); f.lineTo(tip.x, tip.y); f.close()
                drawPath(f, many(j + row, tint, 0xFFFF3B6B, 0xFFFFD400, 0xFF2ED1FF, 0xFF5BE36A, 0xFFFF8A1A).copy(alpha = 0.5f - row * 0.15f)) } }
        "cranes" -> for (i in 0 until 11) { val p = s[i + 12]; val o = Offset(p[0] * W, p[1] * H); val r = (14f + p[2] * 20f) * u; val c = m(Color(0xFFE8590C))
            rotate((p[3] - 0.5f) * 50f, o) {
                val body = Path(); body.moveTo(o.x - r, o.y); body.lineTo(o.x + r * 0.9f, o.y - r * 0.15f); body.lineTo(o.x, o.y + r * 0.45f); body.close()
                val wing = Path(); wing.moveTo(o.x - r * 0.25f, o.y); wing.lineTo(o.x + r * 0.35f, o.y - r * 1.05f); wing.lineTo(o.x + r * 0.45f, o.y); wing.close()
                val neck = Path(); neck.moveTo(o.x + r * 0.9f, o.y - r * 0.15f); neck.lineTo(o.x + r * 1.35f, o.y - r * 0.6f); neck.lineTo(o.x + r * 1.1f, o.y - r * 0.05f); neck.close()
                drawPath(body, fg.copy(alpha = 0.14f)); drawPath(wing, c.copy(alpha = 0.3f)); drawPath(neck, fg.copy(alpha = 0.2f))
                drawPath(wing, fg.copy(alpha = 0.25f), style = Stroke(1f)); drawPath(body, fg.copy(alpha = 0.25f), style = Stroke(1f)) } }
        "veins" -> for (i in 0 until 8) { val p = s[i + 30]; val c = m(Color(0xFF6C7078)); val path = Path()
            var x = p[0] * W; var y = -10f; var a = PI.toFloat() / 2f + (p[1] - 0.5f) * 1.2f; path.moveTo(x, y)
            for (j in 0 until 46) { val q = s[(i * 23 + j) % s.size]; a += (q[0] - 0.5f) * 0.7f; a = a.coerceIn(0.5f, 2.6f)
                x += cos(a) * H / 40f; y += sin(a) * H / 40f; path.lineTo(x, y) }
            drawPath(path, c.copy(alpha = 0.05f), style = Stroke(7f * u)); drawPath(path, c.copy(alpha = 0.22f), style = Stroke((0.6f + p[2]) * u)) }
        "pebbles" -> for (i in 0 until 46) { val p = s[i]; val w0 = (18f + p[2] * 34f) * u; val h0 = w0 * (0.5f + p[3] * 0.3f)
            val x = p[0] * W; val y = H - p[1] * p[1] * H * 0.28f - h0
            drawOval(lerp(Color(0xFF8A8F7C), Color(0xFF4A4E44), p[3]).copy(alpha = 0.42f), Offset(x, y), Size(w0, h0))
            drawOval(Color.White.copy(alpha = 0.08f), Offset(x + w0 * 0.15f, y + h0 * 0.1f), Size(w0 * 0.5f, h0 * 0.3f))
            if (i % 3 == 0) for (j in 0 until 5) { val q = s[(i + j * 5 + 100) % s.size]
                drawCircle(m(Color(0xFF8FBF4A)).copy(alpha = 0.5f), (2f + q[2] * 3f) * u, Offset(x + q[0] * w0 * 0.8f + w0 * 0.1f, y + q[1] * h0 * 0.35f)) } }
        "feathers" -> { val o = Offset(W / 2f, H * 1.06f)
            for (i in 0 until 11) { val a = -PI.toFloat() / 2f + (i - 5) * 0.17f; val len = H * (0.5f - kotlin.math.abs(i - 5) * 0.022f)
                val e = Offset(o.x + cos(a) * len, o.y + sin(a) * len); val r = minOf(W, H) * 0.055f
                drawLine(fg.copy(alpha = 0.13f), o, e, 1.2f * u)
                rotate(a * 180f / PI.toFloat() + 90f, e) {
                    drawOval(m(Color(0xFF2FE0C2)).copy(alpha = 0.22f), Offset(e.x - r, e.y - r * 1.5f), Size(r * 2f, r * 3f))
                    drawOval(Color(0xFFFFD166).copy(alpha = 0.3f), Offset(e.x - r * 0.68f, e.y - r * 1.05f), Size(r * 1.36f, r * 2.1f))
                    drawOval(Color(0xFF1E6BFF).copy(alpha = 0.42f), Offset(e.x - r * 0.42f, e.y - r * 0.7f), Size(r * 0.84f, r * 1.3f))
                    drawOval(Color(0xFF0A1440).copy(alpha = 0.6f), Offset(e.x - r * 0.2f, e.y - r * 0.4f), Size(r * 0.4f, r * 0.6f)) } } }
        "beam" -> { val o = Offset(W * 0.82f, H * 0.2f); val c = m(Color(0xFFFFE28A)); val r = maxOf(W, H) * 1.3f
            for (side in 0 until 2) { val a = t * 0.22f + side * PI.toFloat(); val wd = 0.16f
                val path = Path(); path.moveTo(o.x, o.y); path.lineTo(o.x + cos(a - wd) * r, o.y + sin(a - wd) * r); path.lineTo(o.x + cos(a + wd) * r, o.y + sin(a + wd) * r); path.close()
                drawPath(path, Brush.radialGradient(listOf(c.copy(alpha = 0.26f), Color.Transparent), o, r * 0.7f)) }
            drawCircle(c.copy(alpha = 0.2f), 22f * u, o); drawCircle(c.copy(alpha = 0.85f), 5f * u, o) }
        "steam" -> for (i in 0 until 6) { val p = s[i + 8]; val c = m(Color.White); val x0 = (i + 0.5f) * W / 6f; val path = Path()
            val rise = (t * (0.035f + p[2] * 0.02f) + p[3]) % 1f
            for (j in 0..30) { val f = j / 30f; val y = H * (1f - f * 0.55f) - rise * H * 0.12f
                val x = x0 + sin(f * 7f + t * 0.5f + p[0] * 6f) * W * 0.035f * (0.3f + f)
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y) }
            drawPath(path, Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.2f)), H * 0.3f, H), style = Stroke((5f + p[2] * 7f) * u, cap = androidx.compose.ui.graphics.StrokeCap.Round)) }
        "scribbles" -> for (i in 0 until 9) { val p = s[i + 3]; val c = many(i, tint, 0xFFF23B3B, 0xFF2F6FED, 0xFFF7C325, 0xFF27AE60, 0xFF9B51E0); val path = Path()
            val o = Offset(p[0] * W, p[1] * H); val len = (50f + p[2] * 70f) * u
            if (i % 3 == 2) { for (j in 0..40) { val a = j * 0.45f; val r = j * len / 110f; val x = o.x + cos(a) * r; val y = o.y + sin(a) * r; if (j == 0) path.moveTo(x, y) else path.lineTo(x, y) } }
            else { val a = (p[3] - 0.5f) * 1.2f
                for (j in 0..10) { val f = j / 10f; val off = (if (j % 2 == 0) -1f else 1f) * len * 0.16f
                    val x = o.x + cos(a) * f * len - sin(a) * off; val y = o.y + sin(a) * f * len + cos(a) * off; if (j == 0) path.moveTo(x, y) else path.lineTo(x, y) } }
            drawPath(path, c.copy(alpha = 0.4f), style = Stroke(3.2f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)) }
        "lanterns" -> for (i in 0 until 14) { val p = s[i + 16]; val c = m(Color(0xFFFF9F45))
            val cyc = 50f + p[2] * 60f; val ph = ((t + p[3] * cyc) % cyc) / cyc
            val w0 = (12f + p[2] * 12f) * u; val h0 = w0 * 1.35f
            val x = p[0] * W + sin(t * 0.3f + p[1] * 6f) * 10f * u; val y = H + h0 - ph * (H + h0 * 3f)
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.28f), Color.Transparent), Offset(x, y), w0 * 2.2f), w0 * 2.2f, Offset(x, y))
            drawRoundRect(c.copy(alpha = 0.62f), Offset(x - w0 / 2f, y - h0 / 2f), Size(w0, h0), CornerRadius(w0 * 0.3f, w0 * 0.3f))
            drawRect(Color.Black.copy(alpha = 0.3f), Offset(x - w0 * 0.3f, y - h0 / 2f - 2f * u), Size(w0 * 0.6f, 2.5f * u))
            drawRect(Color(0xFFFFF2C0).copy(alpha = 0.5f), Offset(x - w0 * 0.15f, y - h0 * 0.2f), Size(w0 * 0.3f, h0 * 0.4f)) }
        "pixels" -> { val g = 14f * u
            val forms = arrayOf(intArrayOf(0, 0, 1, 0, 2, 0, 3, 0), intArrayOf(0, 0, 1, 0, 0, 1, 1, 1), intArrayOf(0, 0, 1, 0, 2, 0, 1, 1), intArrayOf(0, 0, 0, 1, 0, 2, 1, 2), intArrayOf(0, 0, 1, 0, 1, 1, 2, 1))
            for (i in 0 until 14) { val p = s[i + 24]; val f = forms[i % forms.size]; val c = many(i, tint, 0xFF9D8CFF, 0xFFE94F64, 0xFF53D8A8, 0xFFF2C94C, 0xFF5AA9F0)
                val cyc = 30f + p[2] * 30f; val ph = ((t + p[3] * cyc) % cyc) / cyc
                val x = floor(p[0] * W / g) * g; val y = floor((ph * (H + g * 6f) - g * 4f) / g) * g
                for (j in 0 until 4) { val o = Offset(x + f[j * 2] * g, y + f[j * 2 + 1] * g)
                    drawRect(c.copy(alpha = 0.34f), o, Size(g - 1.5f * u, g - 1.5f * u)); drawRect(Color.White.copy(alpha = 0.14f), o, Size(g - 1.5f * u, 2.5f * u)) } } }
        "spores" -> { val c = m(Color(0xFF5CF2E0))
            for (i in 0 until 6) { val p = s[i + 44]; val x = (i + 0.3f + p[0] * 0.5f) * W / 6f; val hh = H * (0.05f + p[1] * 0.08f); val r = (12f + p[2] * 16f) * u
                drawRect(c.copy(alpha = 0.22f), Offset(x - r * 0.14f, H - hh), Size(r * 0.28f, hh))
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.3f), Color.Transparent), Offset(x, H - hh), r * 2.4f), r * 2.4f, Offset(x, H - hh))
                drawArc(c.copy(alpha = 0.6f), 180f, 180f, true, Offset(x - r, H - hh - r * 0.7f), Size(r * 2f, r * 1.4f)) }
            for (i in 0 until 60) { val p = s[i + 60]; val cyc = 26f + p[2] * 30f; val ph = ((t + p[3] * cyc) % cyc) / cyc
                drawCircle(c.copy(alpha = (1f - ph) * 0.6f), (1f + p[2] * 1.6f) * u, Offset(p[0] * W + sin(t * 0.5f + p[1] * 9f) * 14f * u, H * (0.95f - ph * 0.8f))) } }
        "shimmer" -> for (i in 0 until 4) { val c = many(i, tint, 0xFF5CC8D6, 0xFFF08FC0, 0xFFF2C879, 0xFF9B8CFF)
            val span = W + H; val bw = span * 0.22f; val x = ((t * (9f + i * 3f) * u + i * span / 4f) % (span + bw)) - bw
            val path = Path(); path.moveTo(x, 0f); path.lineTo(x + bw, 0f); path.lineTo(x + bw - H, H); path.lineTo(x - H, H); path.close()
            drawPath(path, Brush.linearGradient(listOf(Color.Transparent, c.copy(alpha = 0.2f), Color.Transparent), Offset(x - H / 2f, H / 2f), Offset(x + bw - H / 2f, H / 2f))) }
        "rivets" -> { val c = m(Color(0xFFB0B6BD)); val rows = 5; val ph = H / rows
            for (r in 0..rows) { val y = r * ph
                drawLine(Color.Black.copy(alpha = 0.3f), Offset(0f, y), Offset(W, y), 2f * u); drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, y + 2f * u), Offset(W, y + 2f * u), 1f * u)
                val vx = if (r % 2 == 0) W * 0.34f else W * 0.7f
                drawLine(Color.Black.copy(alpha = 0.26f), Offset(vx, y), Offset(vx, y + ph), 2f * u)
                var x = 16f * u; while (x < W) { val o = Offset(x, y + 9f * u)
                    drawCircle(Color.Black.copy(alpha = 0.3f), 3.6f * u, Offset(o.x + 0.8f * u, o.y + 0.8f * u)); drawCircle(c.copy(alpha = 0.4f), 3f * u, o); drawCircle(Color.White.copy(alpha = 0.3f), 1f * u, Offset(o.x - 1f * u, o.y - 1f * u)); x += 38f * u } } }
        "eddy" -> { val o = Offset(W * 0.38f, H * 0.6f); val c = m(Color(0xFFFF8FB8)); val r = maxOf(W, H) * 0.75f
            for (arm in 0 until 4) { val path = Path()
                for (j in 0..70) { val f = j / 70f; val a = t * 0.08f + arm * tau / 4f + f * 6.5f; val rr = f * r
                    val x = o.x + cos(a) * rr; val y = o.y + sin(a) * rr * 0.8f; if (j == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                drawPath(path, c.copy(alpha = 0.05f), style = Stroke(26f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawPath(path, c.copy(alpha = 0.12f), style = Stroke(2f * u)) } }
        "kites" -> for (i in 0 until 6) { val p = s[i + 70]; val c = many(i, tint, 0xFFE8452C, 0xFF2F80ED, 0xFFF2C94C, 0xFF27AE60, 0xFF9B51E0)
            val o = Offset(p[0] * W * 0.8f + W * 0.1f + sin(t * 0.25f + p[2] * 6f) * W * 0.04f, p[1] * H * 0.5f + H * 0.08f + cos(t * 0.2f + p[3] * 6f) * H * 0.02f)
            val r = (16f + p[2] * 14f) * u
            val k0 = Path(); k0.moveTo(o.x, o.y - r * 1.2f); k0.lineTo(o.x + r * 0.8f, o.y); k0.lineTo(o.x, o.y + r * 1.5f); k0.lineTo(o.x - r * 0.8f, o.y); k0.close()
            drawPath(k0, c.copy(alpha = 0.55f)); drawLine(Color.White.copy(alpha = 0.4f), Offset(o.x, o.y - r * 1.2f), Offset(o.x, o.y + r * 1.5f), 1f * u)
            drawLine(Color.White.copy(alpha = 0.4f), Offset(o.x - r * 0.8f, o.y), Offset(o.x + r * 0.8f, o.y), 1f * u)
            val tail = Path(); tail.moveTo(o.x, o.y + r * 1.5f)
            for (j in 1..14) { val f = j / 14f; tail.lineTo(o.x + sin(f * 6f + t * 0.8f + p[0] * 6f) * r * 0.6f * f - f * r, o.y + r * 1.5f + f * H * 0.3f) }
            drawPath(tail, fg.copy(alpha = 0.3f), style = Stroke(1f * u)) }
        else -> return false
    }
    return true
}
