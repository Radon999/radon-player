package org.omarchy.music.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 4.0.14: thirty more visualizer styles (drawn by [drawVis2]): fifteen that stand along the bottom and fifteen
 *  around the middle. Things rather than meters: organ pipes, an abacus, piano keys, a rose window, a lotus. */
internal val MORE_VIS_BOTTOM: List<Pair<String, String>> = listOf(
    "glasstiles" to "Glass tiles", "pipes" to "Organ pipes", "draftbars" to "Blueprint bars", "abacus" to "Abacus", "ziggurat" to "Ziggurat",
    "coralbars" to "Coral", "stalactites" to "Stalactites", "inkstrokes" to "Brush strokes", "stitches" to "Stitches", "drapefolds" to "Curtain folds",
    "shards" to "Shards", "pennants" to "Pennants", "pianokeys" to "Piano keys", "lanternrow" to "Lantern row", "toadstools" to "Toadstools")
internal val MORE_VIS_CENTRED: List<Pair<String, String>> = listOf(
    "rosewindow" to "Rose window", "compass" to "Compass", "decofan" to "Deco fan", "target" to "Target", "lotus" to "Lotus",
    "anemone" to "Anemone", "enso" to "Ink circle", "stringart" to "String art", "crystal" to "Snow crystal", "gem" to "Gem",
    "pinwheel" to "Pinwheel", "peacockfan" to "Peacock fan", "beacon" to "Beacon", "whirlpool" to "Whirlpool", "kite" to "Kite")

/** Draws style if it is one of the thirty (true), else nothing (false). */
internal fun DrawScope.drawVis2(style: String, s: VisState, pal: VisPalette, fg: Color, backdrop: Boolean): Boolean {
    val lv = s.levels; val n = lv.size
    val w = size.width; val h = size.height; val u = density
    val base = if (backdrop) h else h * 0.8f
    val c = Offset(w / 2, h / 2); val half = min(w, h) / 2
    val tau = 2f * PI.toFloat()
    fun t(b: Int) = b / (n - 1f)
    fun band(lo: Int, hi: Int): Float { var sum = 0f; for (b in lo until max(hi, lo + 1)) sum += lv[min(b, n - 1)]; return sum / max(hi - lo, 1) }
    /** The spectrum in k parts: part i's level. */
    fun part(i: Int, k: Int) = band(i * n / k, (i + 1) * n / k)
    fun dir(a: Float) = Offset(cos(a), sin(a))
    when (style) {
        // ───── along the bottom ─────
        "glasstiles" -> { val cols = min(n, 22); val rows = 9; val cw = w / cols; val ch = base / rows; val lead = 1.5f * u
            for (i in 0 until cols) { val v = part(i, cols); val lit = (v * rows + 0.5f).toInt()
                for (r in 0 until rows) { val on = r < lit; val col = pal.at(i / (cols - 1f), (r + 1f) / rows)
                    drawRect(col.copy(alpha = if (on) 0.5f + 0.4f * v else 0.07f), Offset(i * cw + lead, base - (r + 1) * ch + lead), Size(cw - lead * 2, ch - lead * 2)) } } }
        "pipes" -> { val k = min(n, 26); val pw = w / k
            for (i in 0 until k) { val v = part(i, k); val ph = base * (0.16f + 0.8f * v); val x = i * pw + pw * 0.12f; val ww = pw * 0.76f; val col = pal.at(i / (k - 1f), v)
                drawRoundRect(col.copy(alpha = 0.4f + 0.55f * v), Offset(x, base - ph), Size(ww, ph), CornerRadius(ww / 2, ww / 2))
                drawRect(Color.White.copy(alpha = 0.18f), Offset(x + ww * 0.18f, base - ph + ww / 2), Size(ww * 0.14f, max(0f, ph - ww)))
                drawRect(Color.Black.copy(alpha = 0.55f), Offset(x + ww * 0.2f, base - min(ph * 0.5f, base * 0.1f)), Size(ww * 0.6f, 3f * u)) } }
        "draftbars" -> { val k = min(n, 20); val pw = w / k; val dash = PathEffect.dashPathEffect(floatArrayOf(5f * u, 4f * u))
            drawLine(fg.copy(alpha = 0.4f), Offset(0f, base), Offset(w, base), 1.2f * u)
            for (i in 0 until k) { val v = part(i, k); val bh = max(3f * u, v * base * 0.9f); val x = i * pw + pw * 0.2f; val ww = pw * 0.6f; val col = pal.at(i / (k - 1f), v)
                drawRect(col.copy(alpha = 0.10f + 0.15f * v), Offset(x, base - bh), Size(ww, bh))
                drawRect(col.copy(alpha = 0.5f + 0.5f * v), Offset(x, base - bh), Size(ww, bh), style = Stroke(1.2f * u))
                drawLine(col.copy(alpha = 0.5f), Offset(x + ww / 2, base - bh - 8f * u), Offset(x + ww / 2, base), 1f * u, pathEffect = dash)
                drawLine(col.copy(alpha = 0.8f), Offset(x - 3f * u, base - bh - 8f * u), Offset(x + ww + 3f * u, base - bh - 8f * u), 1f * u) } }
        "abacus" -> { val k = min(n, 14); val pw = w / k; val beads = 9; val bhh = base * 0.085f
            for (i in 0 until k) { val v = part(i, k); val x = (i + 0.5f) * pw; val col = pal.at(i / (k - 1f), v)
                drawLine(fg.copy(alpha = 0.25f), Offset(x, base * 0.06f), Offset(x, base), 1.5f * u)
                val up = (v * beads + 0.5f).toInt()
                // the beads counted are pushed up against the top; the rest sit at the bottom
                for (j in 0 until beads) { val y = if (j < up) base * 0.08f + j * bhh else base - (beads - j) * bhh
                    drawOval(col.copy(alpha = if (j < up) 0.55f + 0.4f * v else 0.2f), Offset(x - pw * 0.36f, y), Size(pw * 0.72f, bhh * 0.86f)) } }
            drawLine(fg.copy(alpha = 0.4f), Offset(0f, base * 0.06f), Offset(w, base * 0.06f), 2f * u) }
        "ziggurat" -> { val k = min(n, 16); val pw = w / k
            for (i in 0 until k) { val v = part(i, k); val col = pal.at(i / (k - 1f), v); val x = i * pw
                for (st in 0 until 4) { val f = 1f - st * 0.22f; val top = v * base * 0.92f * (st + 1) / 4f; val bot = v * base * 0.92f * st / 4f
                    drawRect(col.copy(alpha = 0.3f + 0.16f * st + 0.2f * v), Offset(x + pw * (1f - f) / 2f, base - top), Size(pw * f, max(1f, top - bot - 1f * u))) } } }
        "coralbars" -> { val k = min(n, 20); val pw = w / k
            for (i in 0 until k) { val v = part(i, k); val x = (i + 0.5f) * pw; val bh = max(4f * u, v * base * 0.9f); val col = pal.at(i / (k - 1f), v).copy(alpha = 0.45f + 0.5f * v)
                val lean = sin(s.phase * 0.6f + i) * pw * 0.12f
                drawLine(col, Offset(x, base), Offset(x + lean, base - bh), 3f * u, cap = StrokeCap.Round)
                for (j in 1..2) { val f = j / 3f; val o = Offset(x + lean * f, base - bh * f); val sd = if ((i + j) % 2 == 0) 1f else -1f
                    val e = Offset(o.x + sd * pw * 0.42f, o.y - bh * 0.2f)
                    drawLine(col, o, e, 2f * u, cap = StrokeCap.Round); drawCircle(col, 2.4f * u, e) }
                drawCircle(col, 3.4f * u, Offset(x + lean, base - bh)) } }
        "stalactites" -> { val k = min(n, 28); val pw = w / k
            for (i in 0 until k) { val v = part(i, k); val col = pal.at(i / (k - 1f), v); val x = i * pw
                val down = Path(); down.moveTo(x, 0f); down.lineTo(x + pw, 0f); down.lineTo(x + pw / 2, v * base * 0.7f + 3f * u); down.close()
                drawPath(down, Brush.verticalGradient(listOf(col.copy(alpha = 0.85f), col.copy(alpha = 0.15f)), 0f, v * base * 0.7f + 3f * u))
                val up = Path(); up.moveTo(x + pw * 0.15f, base); up.lineTo(x + pw * 0.85f, base); up.lineTo(x + pw / 2, base - v * base * 0.3f - 2f * u); up.close()
                drawPath(up, col.copy(alpha = 0.35f + 0.4f * v)) } }
        "inkstrokes" -> { val k = min(n, 16); val pw = w / k
            for (i in 0 until k) { val v = part(i, k); val bh = max(6f * u, v * base * 0.92f); val x = (i + 0.5f) * pw; val lean = (if (i % 2 == 0) 1f else -1f) * pw * 0.2f
                val p = Path(); p.moveTo(x - pw * 0.34f, base); p.quadraticBezierTo(x - pw * 0.3f + lean, base - bh * 0.5f, x + lean, base - bh)
                p.quadraticBezierTo(x + pw * 0.26f + lean, base - bh * 0.45f, x + pw * 0.3f, base); p.close()
                drawPath(p, pal.at(i / (k - 1f), v).copy(alpha = 0.3f + 0.6f * v))
                // the dry tail of the stroke, flicked on past its tip
                drawLine(pal.at(i / (k - 1f), v).copy(alpha = 0.25f * v), Offset(x + lean, base - bh), Offset(x + lean * 1.8f, base - bh - 12f * u * v), 1.4f * u, cap = StrokeCap.Round) } }
        "stitches" -> { val k = min(n, 24); val dash = PathEffect.dashPathEffect(floatArrayOf(7f * u, 5f * u)); val path = Path()
            for (i in 0 until k) { val x = (i + 0.5f) * w / k; val y = base - part(i, k) * base * 0.88f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
            drawPath(path, pal.brush(0.95f), style = Stroke(2.4f * u, pathEffect = dash))
            for (i in 0 until k) { val v = part(i, k); val x = (i + 0.5f) * w / k; val y = base - v * base * 0.88f; val col = pal.at(i / (k - 1f), v); val r = (3f + 4f * v) * u
                drawLine(col.copy(alpha = 0.35f), Offset(x, y), Offset(x, base), 1.2f * u, pathEffect = dash)
                drawLine(col, Offset(x - r, y - r), Offset(x + r, y + r), 1.8f * u); drawLine(col, Offset(x + r, y - r), Offset(x - r, y + r), 1.8f * u) }
            drawLine(fg.copy(alpha = 0.3f), Offset(0f, base), Offset(w, base), 1.2f * u, pathEffect = dash) }
        "drapefolds" -> { val k = min(n, 24); val path = Path(); path.moveTo(0f, 0f)
            for (i in 0..k) { val v = part(min(i, k - 1), k); val x = i * w / k; val y = base * (0.12f + 0.8f * v)
                if (i == 0) path.lineTo(x, y) else { val px = (i - 1) * w / k; path.quadraticBezierTo((px + x) / 2f, y + base * 0.05f, x, y) } }
            path.lineTo(w, 0f); path.close()
            drawPath(path, Brush.verticalGradient(listOf(pal.main.copy(alpha = 0.75f), pal.main.copy(alpha = 0.25f)), 0f, base))
            for (i in 0 until k) { val x = (i + 0.5f) * w / k; val y = base * (0.12f + 0.8f * part(i, k))
                drawLine(Color.Black.copy(alpha = 0.25f), Offset(x, 0f), Offset(x, y), 2f * u); drawLine(Color.White.copy(alpha = 0.12f), Offset(x + 3f * u, 0f), Offset(x + 3f * u, y * 0.9f), 1f * u) }
            drawPath(path, pal.brush(0.9f), style = Stroke(2f * u)) }
        "shards" -> { val k = min(n, 22); val pw = w / k
            for (i in 0 until k) { val v = part(i, k); val col = pal.at(i / (k - 1f), v); val x = i * pw; val bh = max(4f * u, v * base * 0.95f)
                val a = Path(); a.moveTo(x - pw * 0.2f, base); a.lineTo(x + pw * 0.8f, base); a.lineTo(x + pw * 0.15f, base - bh); a.close()
                val b = Path(); b.moveTo(x + pw * 0.3f, base); b.lineTo(x + pw * 1.2f, base); b.lineTo(x + pw * 0.95f, base - bh * 0.62f); b.close()
                drawPath(a, col.copy(alpha = 0.35f + 0.5f * v)); drawPath(b, col.copy(alpha = 0.18f + 0.3f * v))
                drawLine(Color.White.copy(alpha = 0.3f * v), Offset(x + pw * 0.3f, base), Offset(x + pw * 0.15f, base - bh), 1f * u) } }
        "pennants" -> { val k = min(n, 20); val y0 = base * 0.1f; val sag = base * (0.06f + 0.08f * s.bass)
            fun at(f: Float) = Offset(f * w, y0 + sag * 4f * f * (1f - f))
            val line = Path(); line.moveTo(0f, y0); for (j in 1..40) { val o = at(j / 40f); line.lineTo(o.x, o.y) }
            drawPath(line, fg.copy(alpha = 0.5f), style = Stroke(1.4f * u))
            for (i in 0 until k) { val v = part(i, k); val a = at((i + 0.1f) / k); val b = at((i + 0.9f) / k)
                val sway = sin(s.phase * 1.4f + i * 0.7f) * w / k * 0.25f * v
                val f = Path(); f.moveTo(a.x, a.y); f.lineTo(b.x, b.y); f.lineTo((a.x + b.x) / 2f + sway, (a.y + b.y) / 2f + base * (0.08f + 0.72f * v)); f.close()
                drawPath(f, pal.at(i / (k - 1f), v).copy(alpha = 0.4f + 0.55f * v)) } }
        "pianokeys" -> { val k = min(n, 21); val kw = w / k; val kh = base * 0.2f; val top = base - kh
            for (i in 0 until k) { val v = part(i, k); val col = pal.at(i / (k - 1f), v); val x = i * kw
                // the note sounding, rising off its key
                drawRoundRect(col.copy(alpha = 0.25f + 0.7f * v), Offset(x + kw * 0.18f, top - 6f * u - v * top * 0.9f), Size(kw * 0.64f, v * top * 0.9f + 2f * u), CornerRadius(kw * 0.2f, kw * 0.2f))
                drawRect(lerpTo(Color.White, col, v).copy(alpha = 0.55f + 0.4f * v), Offset(x + 1f * u, top), Size(kw - 2f * u, kh)) }
            for (i in 0 until k - 1) if (i % 7 != 2 && i % 7 != 6) drawRect(Color.Black.copy(alpha = 0.85f), Offset((i + 1) * kw - kw * 0.28f, top), Size(kw * 0.56f, kh * 0.6f)) }
        "lanternrow" -> { val k = min(n, 12); val pw = w / k
            for (i in 0 until k) { val v = part(i, k); val col = pal.at(i / (k - 1f), v); val x = (i + 0.5f) * pw
                val y = base * (0.82f - 0.66f * v); val lw = pw * 0.56f; val lh = lw * 1.3f
                drawLine(fg.copy(alpha = 0.3f), Offset(x, 0f), Offset(x, y - lh / 2), 1f * u)
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.2f + 0.4f * v), Color.Transparent), Offset(x, y), lw * 1.9f), lw * 1.9f, Offset(x, y))
                drawRoundRect(col.copy(alpha = 0.5f + 0.45f * v), Offset(x - lw / 2, y - lh / 2), Size(lw, lh), CornerRadius(lw * 0.35f, lw * 0.35f))
                for (r in 1..3) drawLine(Color.Black.copy(alpha = 0.18f), Offset(x - lw / 2, y - lh / 2 + lh * r / 4f), Offset(x + lw / 2, y - lh / 2 + lh * r / 4f), 1f * u)
                drawLine(col.copy(alpha = 0.7f), Offset(x, y + lh / 2), Offset(x, y + lh / 2 + lw * 0.5f), 1.4f * u) } }
        "toadstools" -> { val k = min(n, 14); val pw = w / k
            for (i in 0 until k) { val v = part(i, k); val col = pal.at(i / (k - 1f), v); val x = (i + 0.5f) * pw
                val st = max(4f * u, v * base * 0.7f); val r = pw * (0.28f + 0.2f * v)
                drawRoundRect(lerpTo(Color.White, col, 0.25f).copy(alpha = 0.4f + 0.4f * v), Offset(x - pw * 0.09f, base - st), Size(pw * 0.18f, st), CornerRadius(pw * 0.06f, pw * 0.06f))
                drawArc(col.copy(alpha = 0.5f + 0.5f * v), 180f, 180f, true, Offset(x - r, base - st - r * 0.75f), Size(r * 2, r * 1.5f))
                for (j in -1..1) drawCircle(Color.White.copy(alpha = 0.55f), r * 0.11f, Offset(x + j * r * 0.5f, base - st - r * (0.3f + (if (j == 0) 0.2f else 0f)))) } }
        // ───── around the middle ─────
        "rosewindow" -> { val panes = 12
            for (ring in 0 until 3) { val r0 = half * (0.14f + ring * 0.26f); val r1 = half * (0.38f + ring * 0.26f)
                for (j in 0 until panes) { val v = lv[((j + ring * 5) * n / panes / 2 + ring * n / 3) % n]; val a0 = j * tau / panes + ring * 0.26f; val a1 = a0 + tau / panes
                    val p = Path(); p.moveTo(c.x + cos(a0) * r0, c.y + sin(a0) * r0); p.lineTo(c.x + cos(a0) * r1, c.y + sin(a0) * r1)
                    val am = (a0 + a1) / 2f; p.quadraticBezierTo(c.x + cos(am) * r1 * 1.08f, c.y + sin(am) * r1 * 1.08f, c.x + cos(a1) * r1, c.y + sin(a1) * r1)
                    p.lineTo(c.x + cos(a1) * r0, c.y + sin(a1) * r0); p.close()
                    drawPath(p, pal.at(j / (panes - 1f), v).copy(alpha = 0.12f + 0.8f * v)); drawPath(p, Color.Black.copy(alpha = 0.6f), style = Stroke(1.6f * u)) } }
            drawCircle(pal.at(0f, s.bass).copy(alpha = 0.4f + 0.6f * s.bass), half * 0.13f, c) }
        "compass" -> { val r = half * 0.82f
            drawCircle(pal.main.copy(alpha = 0.5f), r, c, style = Stroke(1.5f * u))
            for (b in 0 until n * 2) { val bb = if (b < n) b else 2 * n - 1 - b; val v = lv[bb]; val a = b * tau / (n * 2) - PI.toFloat() / 2f
                drawLine(pal.at(t(bb), v).copy(alpha = 0.3f + 0.7f * v), c + dir(a) * r, c + dir(a) * (r - half * (0.03f + 0.2f * v)), 1.6f * u) }
            val rot = s.phase * 0.25f + s.bass * 0.5f
            for (q in 0 until 4) { val a = rot + q * tau / 4f; val len = half * (if (q % 2 == 0) 0.6f else 0.34f) * (0.8f + 0.3f * s.bass)
                val p = Path(); val tip = c + dir(a) * len; val l = c + dir(a + tau / 4f) * (half * 0.06f); val rr = c + dir(a - tau / 4f) * (half * 0.06f)
                p.moveTo(tip.x, tip.y); p.lineTo(l.x, l.y); p.lineTo(rr.x, rr.y); p.close()
                drawPath(p, pal.at(q / 3f, 0.8f).copy(alpha = if (q == 0) 0.95f else 0.5f)) }
            drawCircle(fg.copy(alpha = 0.8f), half * 0.03f, c) }
        "decofan" -> { val o = Offset(c.x, c.y + half * 0.7f); val reach = half * 1.5f
            for (b in 0 until n) { val v = lv[b]; val a = PI.toFloat() + (b + 0.5f) * PI.toFloat() / n
                drawLine(pal.at(t(b), v).copy(alpha = 0.3f + 0.7f * v), o + dir(a) * (half * 0.12f), o + dir(a) * (half * 0.12f + reach * (0.12f + 0.85f * v)), max(2f, w / n * 0.45f), cap = StrokeCap.Butt) }
            for (j in 1..4) { val r = reach * j / 4.2f
                drawArc(pal.main.copy(alpha = 0.3f), 180f, 180f, false, Offset(o.x - r, o.y - r), Size(r * 2, r * 2), style = Stroke(1.2f * u)) }
            drawArc(pal.at(0f, s.bass).copy(alpha = 0.6f + 0.4f * s.bass), 180f, 180f, true, Offset(o.x - half * 0.12f, o.y - half * 0.12f), Size(half * 0.24f, half * 0.24f)) }
        "target" -> for (k in 0 until 7) { val v = if (k == 0) s.bass else part(k - 1, 6); val r = half * (0.1f + k * 0.13f)
            if (k == 0) drawCircle(pal.at(0f, v).copy(alpha = 0.5f + 0.5f * v), r * (0.7f + 0.5f * v), c)
            else drawCircle(pal.at(k / 6f, v).copy(alpha = 0.2f + 0.75f * v), r, c, style = Stroke(half * 0.11f * (0.15f + 0.85f * v))) }
        "lotus" -> for (layer in 2 downTo 0) { val petals = 8
            for (j in 0 until petals) { val v = lv[((j + layer * 3) * n / (petals * 2) + layer * n / 4) % n]
                val a = j * tau / petals + layer * tau / (petals * 2) + sin(s.phase * 0.3f) * 0.05f
                val len = half * (0.34f + layer * 0.24f) * (0.6f + 0.5f * v); val wd = len * 0.36f
                val tip = c + dir(a) * len; val l = c + dir(a + 0.9f) * wd; val r = c + dir(a - 0.9f) * wd
                val p = Path(); p.moveTo(c.x, c.y); p.quadraticBezierTo(l.x, l.y, tip.x, tip.y); p.quadraticBezierTo(r.x, r.y, c.x, c.y); p.close()
                drawPath(p, pal.at(layer / 2f, v).copy(alpha = 0.16f + 0.5f * v)); drawPath(p, pal.at(layer / 2f, v).copy(alpha = 0.5f + 0.4f * v), style = Stroke(1.2f * u)) } }
        "anemone" -> for (j in 0 until 30) { val bb = (j * n / 30) % n; val v = lv[bb]; val a0 = j * tau / 30f; val len = half * (0.25f + 0.7f * v); val p = Path()
            for (q in 0..14) { val f = q / 14f; val a = a0 + sin(f * 5f + s.phase * 2f + j) * 0.16f * f
                val pt = c + dir(a) * (half * 0.1f + len * f); if (q == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y) }
            drawPath(p, pal.at(t(bb), v).copy(alpha = 0.35f + 0.6f * v), style = Stroke((1.5f + 3f * v) * u, cap = StrokeCap.Round))
            drawCircle(pal.at(t(bb), v), (2f + 2.5f * v) * u, c + dir(a0 + sin(5f + s.phase * 2f + j) * 0.16f) * (half * 0.1f + len)) }
        "enso" -> { val r = half * (0.62f + 0.05f * s.bass); val segs = n * 2; val gap = 0.5f; val start = s.phase * 0.2f
            for (b in 0 until segs) { val bb = if (b < n) b else 2 * n - 1 - b; val v = lv[bb]
                val a0 = start + gap / 2f + b * (tau - gap) / segs; val a1 = a0 + (tau - gap) / segs * 1.3f
                // a brush stroke: it starts fat and wet and thins towards its end, swelling where the music is loud
                val wd = half * (0.035f + 0.13f * v) * (1.15f - 0.6f * b / segs)
                drawLine(pal.at(t(bb), v).copy(alpha = 0.45f + 0.5f * v), c + dir(a0) * r, c + dir(a1) * r, wd, cap = StrokeCap.Round) } }
        "stringart" -> { val pts = min(n, 48); val r = half * 0.88f; val mult = 2f + sin(s.phase * 0.12f) * 0.9f
            for (i in 0 until pts) { val v = part(i, pts); val a = i * tau / pts; val b = (i * mult) % pts * tau / pts
                drawLine(pal.at(i / (pts - 1f), v).copy(alpha = 0.08f + 0.75f * v), c + dir(a) * r, c + dir(b) * r, (0.6f + 1.6f * v) * u) }
            drawCircle(pal.main.copy(alpha = 0.35f), r, c, style = Stroke(1f * u)) }
        "crystal" -> for (arm in 0 until 6) { val a = arm * tau / 6f + s.phase * 0.05f; val len = half * (0.5f + 0.4f * s.bass)
            drawLine(pal.main.copy(alpha = 0.85f), c, c + dir(a) * len, 2.2f * u, cap = StrokeCap.Round)
            for (j in 1..5) { val v = part(j - 1, 5); val o = c + dir(a) * (len * j / 5.5f); val bl = half * (0.05f + 0.26f * v) * (1.15f - j * 0.12f)
                for (sd in intArrayOf(-1, 1)) drawLine(pal.at((j - 1) / 4f, v).copy(alpha = 0.4f + 0.6f * v), o, o + dir(a + sd * tau / 6f) * bl, 1.6f * u, cap = StrokeCap.Round) } }
        "gem" -> { val sides = 8; val outer = half * (0.72f + 0.1f * s.bass); val inner = outer * 0.5f
            fun pt(i: Int, r: Float) = c + dir(i * tau / sides - PI.toFloat() / 2f + tau / (sides * 2)) * r
            for (i in 0 until sides) { val v = part(i, sides); val a = pt(i, outer); val b = pt(i + 1, outer); val ia = pt(i, inner); val ib = pt(i + 1, inner)
                val f = Path(); f.moveTo(a.x, a.y); f.lineTo(b.x, b.y); f.lineTo(ib.x, ib.y); f.lineTo(ia.x, ia.y); f.close()
                drawPath(f, pal.at(i / (sides - 1f), v).copy(alpha = 0.1f + 0.75f * v)); drawPath(f, Color.White.copy(alpha = 0.35f), style = Stroke(1f * u))
                val k = Path(); k.moveTo(ia.x, ia.y); k.lineTo(ib.x, ib.y); k.lineTo(c.x, c.y); k.close()
                drawPath(k, pal.at(i / (sides - 1f), v).copy(alpha = 0.05f + 0.3f * lv[(i * 3) % n])); drawPath(k, Color.White.copy(alpha = 0.18f), style = Stroke(1f * u)) } }
        "pinwheel" -> { val blades = 8; val rot = s.spin
            for (j in 0 until blades) { val v = part(j, blades); val a = rot + j * tau / blades; val len = half * (0.3f + 0.6f * v)
                val tip = c + dir(a) * len; val side = c + dir(a + tau / blades) * (len * 0.55f)
                val p = Path(); p.moveTo(c.x, c.y); p.lineTo(tip.x, tip.y); p.lineTo(side.x, side.y); p.close()
                drawPath(p, pal.at(j / (blades - 1f), v).copy(alpha = 0.3f + 0.65f * v)) }
            drawCircle(fg.copy(alpha = 0.85f), half * 0.035f, c) }
        "peacockfan" -> { val o = Offset(c.x, c.y + half * 0.75f); val fe = 11
            for (j in 0 until fe) { val v = part(j, fe); val a = -PI.toFloat() / 2f + (j - (fe - 1) / 2f) * 0.2f; val len = half * (0.8f + 0.6f * v)
                val e = o + dir(a) * len; val r = half * (0.05f + 0.07f * v); val col = pal.at(j / (fe - 1f), v)
                drawLine(col.copy(alpha = 0.35f), o, e, 1.2f * u)
                drawCircle(col.copy(alpha = 0.3f + 0.3f * v), r * 1.6f, e); drawCircle(lerpTo(col, Color.White, 0.5f).copy(alpha = 0.5f + 0.4f * v), r, e); drawCircle(Color.Black.copy(alpha = 0.7f), r * 0.45f, e) } }
        "beacon" -> { val reach = half * 1.05f; val a = s.phase * 0.9f
            for (k in 0 until 4) { val f = ((s.phase * 0.35f + k / 4f) % 1f); drawCircle(pal.at(f, 0.6f).copy(alpha = (1f - f) * (0.25f + 0.5f * part(k, 4))), reach * f, c, style = Stroke((1f + 3f * part(k, 4)) * u)) }
            for (sd in 0 until 2) { val aa = a + sd * PI.toFloat(); val wd = 0.12f + 0.3f * s.bass
                val p = Path(); p.moveTo(c.x, c.y); val l = c + dir(aa - wd) * reach; val r = c + dir(aa + wd) * reach; p.lineTo(l.x, l.y); p.lineTo(r.x, r.y); p.close()
                drawPath(p, Brush.radialGradient(listOf(pal.main.copy(alpha = 0.3f + 0.6f * s.bass), Color.Transparent), c, reach)) }
            drawCircle(pal.main, half * (0.05f + 0.05f * s.bass), c) }
        "whirlpool" -> for (arm in 0 until 3) { val steps = n
            for (b in 0 until steps - 1) { val v = lv[b]; val f0 = b / (steps - 1f); val f1 = (b + 1) / (steps - 1f)
                val a0 = s.phase * 0.5f + arm * tau / 3f + f0 * 5.2f; val a1 = s.phase * 0.5f + arm * tau / 3f + f1 * 5.2f
                drawLine(pal.at(f0, v).copy(alpha = 0.3f + 0.65f * v), c + dir(a0) * (half * 0.06f + half * 0.9f * f0), c + dir(a1) * (half * 0.06f + half * 0.9f * f1), (1.5f + 9f * v) * u, cap = StrokeCap.Round) } }
        "kite" -> { val sway = sin(s.phase * 0.8f) * half * 0.06f; val o = Offset(c.x + sway, c.y - half * 0.15f)
            for (k in 3 downTo 0) { val v = part(k, 4); val sc = half * (0.16f + k * 0.15f) * (1f + 0.2f * v)
                val p = Path(); p.moveTo(o.x, o.y - sc * 1.15f); p.lineTo(o.x + sc * 0.8f, o.y); p.lineTo(o.x, o.y + sc * 1.4f); p.lineTo(o.x - sc * 0.8f, o.y); p.close()
                drawPath(p, pal.at(k / 3f, v).copy(alpha = 0.12f + 0.35f * v)); drawPath(p, pal.at(k / 3f, v).copy(alpha = 0.5f + 0.5f * v), style = Stroke((1.2f + 2f * v) * u)) }
            val tail = Path(); val y0 = o.y + half * 0.62f * 1.4f; tail.moveTo(o.x, y0)
            for (b in 0 until n) { val f = b / (n - 1f); tail.lineTo(o.x - sway * 2f * f + (lv[b] - 0.3f) * half * 0.3f * sin(b * 0.9f + s.phase * 3f), y0 + f * half * 0.55f) }
            drawPath(tail, pal.brush(0.9f), style = Stroke(1.8f * u, cap = StrokeCap.Round)) }
        else -> return false
    }
    return true
}

private fun lerpTo(a: Color, b: Color, f: Float): Color = androidx.compose.ui.graphics.lerp(a, b, f.coerceIn(0f, 1f))
