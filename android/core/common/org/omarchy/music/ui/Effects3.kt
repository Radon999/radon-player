package org.omarchy.music.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 4.5.3: thirty effects that move to the music, as the visualizers do: they hear the song through the visualizer's
 *  listening (Now playing's visualizer permission on the phone), and while nothing plays they rest, moving gently. */
internal val BEAT_FX: List<LookFx.Fx> = listOf(
    LookFx.Fx("beat-glow", "Bass glow", "The edges of the screen glow with the bass", true, music = true),
    LookFx.Fx("beat-rings", "Beat rings", "A ring spreads out from the middle on every beat", true, music = true),
    LookFx.Fx("beat-wave", "Sound wave", "A wave across the middle, its height following the music", true, music = true),
    LookFx.Fx("beat-flash", "Beat flash", "A soft flash of colour on each beat", true, music = true),
    LookFx.Fx("beat-stars", "Singing stars", "Stars that sparkle brighter with the high notes", true, music = true),
    LookFx.Fx("beat-orbs", "Breathing orbs", "Big soft orbs that swell with the bass", true, music = true),
    LookFx.Fx("beat-sparks", "Rising sparks", "Sparks rising from the bottom, faster as the music gets louder", true, music = true),
    LookFx.Fx("beat-confetti", "Beat confetti", "Little bursts of confetti on the beat", true, music = true),
    LookFx.Fx("beat-frame", "Pulsing frame", "A neon line round the edge, brighter and wider with the music", true, music = true),
    LookFx.Fx("beat-spots", "Stage lights", "Spotlights sweeping down from the top, flaring on the beat", true, music = true),
    LookFx.Fx("beat-lasers", "Lasers", "Laser beams fanning up from the bottom, opening wider with the music", true, music = true),
    LookFx.Fx("beat-disco", "Disco ball", "Spots of light turning around the screen, flashing on the beat", true, music = true),
    LookFx.Fx("beat-speakers", "Speakers", "Two speakers in the bottom corners, their cones thumping with the bass", true, music = true),
    LookFx.Fx("beat-ripples", "Floor ripples", "Ripples spreading over the floor on each beat", true, music = true),
    LookFx.Fx("beat-aurora", "Dancing aurora", "Ribbons of light across the top that dance to the music", true, music = true),
    LookFx.Fx("beat-heart", "Heart monitor", "A heart monitor's line along the bottom, a spike on each beat", true, music = true),
    LookFx.Fx("beat-tremor", "Tremor lines", "Fine lines across the screen that shake with the bass", true, music = true),
    LookFx.Fx("beat-bloom", "Blooming flower", "A flower in the corner, its petals opening with the music", true, music = true),
    LookFx.Fx("beat-burst", "Sound burst", "Rays from the middle, each as long as its part of the music is loud", true, music = true),
    LookFx.Fx("beat-blobs", "Bass lava", "Lava-lamp blobs that swell and drift with the bass", true, music = true),
    LookFx.Fx("beat-comets", "Beat comets", "A comet across the sky on the strong beats", true, music = true),
    LookFx.Fx("beat-fountain", "Fountains", "Fountains along the bottom, jumping higher with the music", true, music = true),
    LookFx.Fx("beat-grid", "Racing grid", "A neon grid floor racing towards you, faster and brighter with the music", true, music = true),
    LookFx.Fx("beat-radar", "Radar", "A radar sweep in the middle, the music showing up on it", true, music = true),
    LookFx.Fx("beat-neon", "Neon shapes", "Neon circles, triangles and squares turning slowly, flaring on the beat", true, music = true),
    LookFx.Fx("beat-fog", "Fog machine", "Stage fog puffing up from the floor on the strong beats, lit in your colour", true, music = true),
    LookFx.Fx("beat-fireworks", "Beat fireworks", "Fireworks bursting high up on the strong beats", true, music = true),
    LookFx.Fx("beat-bubbles", "Popping bubbles", "Bubbles drifting up that pop on the beat", true, music = true),
    LookFx.Fx("beat-fireflies", "Fireflies in time", "Fireflies blinking together on the beat", true, music = true),
    LookFx.Fx("beat-bolt", "Bass lightning", "A bolt of lightning on the hardest hits", true, music = true),
)

/** The music, for the effects: the levels, bass, middle and treble, the beats (when each of the last few came), and
 *  how far things have moved (faster with louder music). Taken once a frame from the visualizer's state; with no
 *  visualizer (not allowed, or nothing playing) a quiet idle of its own, so the effects still move a little. */
internal object FxSound {
    val levels = FloatArray(org.omarchy.music.Spectrum.BANDS)
    var bass = 0f; var mid = 0f; var high = 0f; var loud = 0f
    /** 1 on a beat, fading to 0 in about half a second. */
    var beat = 0f
    /** How hard the last beat was (its bass), 0 … 1. */
    var power = 0f
    var beats = 0
    /** When the last few beats came (the effects' clock, seconds); the newest at [beats] % size. */
    val beatAt = FloatArray(8) { -99f }
    /** How far things have moved: grows faster with louder music. */
    var travel = 0f
    /** The effects' own clock (seconds), the same for every effect on screen: the beats' times are on it. Each preview
     *  used to bring its own clock and its own music state, so an old beat looked new in another one and Bass lightning
     *  struck over and over (22 Nov 2026). */
    var now = 0f
        private set
    private val vs = VisState()
    private var lastNs = 0L
    private var lastSince = 99f

    /** Once a frame, whichever effect asks first: the spectrum (null: not heard) moved on into the one music state. */
    fun take(target: FloatArray?) {
        val ns = System.nanoTime()
        if (lastNs != 0L && ns - lastNs < 8_000_000L) return   // already taken this frame (another effect, a preview)
        val dt = if (lastNs == 0L) 1f / 60f else ((ns - lastNs) / 1e9f).coerceIn(0.001f, 0.1f)
        lastNs = ns; now += dt
        val n = levels.size
        val paused = if (org.omarchy.music.Remote.active) org.omarchy.music.Remote.status.paused else org.omarchy.music.Music.paused
        if (target != null) vs.step(target, paused, dt)
        if (target != null && !paused) {
            vs.levels.copyInto(levels)
            // A new beat: the time since the last one fell back to nothing
            if (vs.sinceBeat < lastSince - 0.05f) { beats++; beatAt[beats % beatAt.size] = now; power = vs.bass.coerceIn(0f, 1f) }
            lastSince = vs.sinceBeat
            beat = exp(-vs.sinceBeat * 5f).coerceIn(0f, 1f)
        } else {
            // Resting: a slow swell so nothing stands dead still, and no beats
            for (b in 0 until n) levels[b] = 0.06f + 0.05f * sin(now * 0.7f + b * 0.35f).let { it * it }
            beat *= 0.9f
        }
        fun avg(a: Int, b: Int): Float { var s = 0f; for (i in a until b) s += levels[i]; return s / (b - a) }
        bass = avg(0, n / 8); mid = avg(n / 8, n / 2); high = avg(n / 2, n); loud = avg(0, n)
        travel += dt * (0.25f + loud * 2.2f)
    }

    /** The level at a point across the spectrum, 0 the bass … 1 the treble. */
    fun at(f: Float): Float = levels[(f.coerceIn(0f, 1f) * (levels.size - 1)).toInt()]
}

/** Draws k if it is one of [BEAT_FX] (true), else nothing (false). */
internal fun DrawScope.drawFx3(k: String, t: Float, s: List<FloatArray>, accent: Color, fg: Color, tint: Color?): Boolean {
    if (!k.startsWith("beat-")) return false
    val W = size.width; val H = size.height; val u = density
    val a = FxSound
    val c = tint ?: accent
    val light = lerp(c, Color.White, 0.45f)
    val tau = 2f * PI.toFloat()
    val mid = Offset(W / 2f, H / 2f)
    val half = min(W, H) / 2f
    fun shade(i: Int) = if (tint != null) lerp(tint, if (i % 2 == 0) Color.White else Color.Black, (i % 3) * 0.15f)
        else Color.hsv(((i * 47f + t * 8f) % 360f + 360f) % 360f, 0.65f, 1f)
    fun since(j: Int) = a.now - a.beatAt[((a.beats - j) % a.beatAt.size + a.beatAt.size) % a.beatAt.size]
    fun hash(i: Int, salt: Int) = s[((i * 31 + salt * 7) % s.size + s.size) % s.size]
    when (k) {
        "beat-glow" -> { val g = (0.06f + 0.5f * a.bass).coerceAtMost(0.6f); val e = min(W, H) * (0.12f + 0.18f * a.bass)
            drawRect(Brush.verticalGradient(listOf(c.copy(alpha = g), Color.Transparent), 0f, e), Offset.Zero, Size(W, e))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = g)), H - e, H), Offset(0f, H - e), Size(W, e))
            drawRect(Brush.horizontalGradient(listOf(c.copy(alpha = g * 0.8f), Color.Transparent), 0f, e), Offset.Zero, Size(e, H))
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, c.copy(alpha = g * 0.8f)), W - e, W), Offset(W - e, 0f), Size(e, H)) }
        "beat-rings" -> for (j in 0 until a.beatAt.size) { val age = since(j); if (age < 0f || age > 3f) continue
            val r = half * 0.1f + age * half * 0.7f
            drawCircle(c.copy(alpha = (0.45f * (1f - age / 3f)).coerceAtLeast(0f)), r, mid, style = Stroke((1.5f + 3f * (1f - age / 3f)) * u)) }
        "beat-wave" -> for (line in 0 until 3) { val p = Path(); val steps = 80
            for (j in 0..steps) { val f = j / steps.toFloat(); val x = f * W
                val y = H * 0.5f + sin(f * tau * (2f + line) + t * (1.2f + line * 0.5f)) * a.at(f) * H * (0.18f - line * 0.04f) + line * 6f * u
                if (j == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            drawPath(p, (if (line == 0) light else c).copy(alpha = 0.5f - line * 0.12f), style = Stroke((2.5f - line * 0.6f) * u)) }
        "beat-flash" -> if (a.beat > 0.02f) drawRect(Brush.radialGradient(listOf(c.copy(alpha = 0.22f * a.beat * (0.5f + a.power)), Color.Transparent), mid, max(W, H) * 0.75f))
        "beat-stars" -> for (i in 0 until 90) { val p = s[i]
            val sp = (0.25f + 0.75f * (0.5f + 0.5f * sin(t * (1f + p[2] * 3f) + p[3] * tau))) * (0.25f + a.high * 2.2f)
            val pos = Offset(p[0] * W, p[1] * H); val r = (0.6f + p[2] * 1.4f + a.high * 3f * p[3]) * u
            drawCircle((tint ?: Color.White).copy(alpha = sp.coerceIn(0.05f, 1f)), r, pos)
            if (sp > 0.8f) { drawLine(Color.White.copy(alpha = 0.4f), pos - Offset(r * 3f, 0f), pos + Offset(r * 3f, 0f), 0.8f * u)
                drawLine(Color.White.copy(alpha = 0.4f), pos - Offset(0f, r * 3f), pos + Offset(0f, r * 3f), 0.8f * u) } }
        "beat-orbs" -> for (i in 0 until 6) { val p = s[i + 100]
            val pos = Offset((p[0] + 0.08f * sin(t * 0.13f + i)) * W, (p[1] + 0.08f * cos(t * 0.11f + i * 2)) * H)
            val r = (50f + 70f * p[2]) * u * (0.7f + a.bass * 0.9f)
            drawCircle(Brush.radialGradient(listOf(shade(i).copy(alpha = 0.16f + 0.2f * a.bass), Color.Transparent), pos, r), r, pos) }
        "beat-sparks" -> for (i in 0 until 70) { val p = s[i + 20]
            val y = H * (1f - ((p[1] + a.travel * (0.08f + p[2] * 0.12f)) % 1f))
            val x = p[0] * W + sin(t * (0.5f + p[3]) + i) * 14f * u
            val c2 = lerp(Color(0xFFFFD27A), Color(0xFFFF5A2A), p[3]).let { if (tint != null) lerp(tint, Color.White, p[3] * 0.4f) else it }
            drawCircle(c2.copy(alpha = (0.3f + 0.6f * a.loud) * (y / H)), (1f + p[2] * 2f) * u * (1f + a.loud), Offset(x, y)) }
        "beat-confetti" -> for (j in 0 until 5) { val age = since(j); if (age < 0f || age > 1.8f) continue
            val b = a.beats - j; val o = hash(b, 3); val cx = (0.15f + o[0] * 0.7f) * W; val cy = (0.15f + o[1] * 0.5f) * H
            for (q in 0 until 16) { val p = hash(b * 16 + q, 5); val ang = p[0] * tau; val v = (60f + 140f * p[1]) * u
                val x = cx + cos(ang) * v * age; val y = cy + sin(ang) * v * age + 160f * u * age * age
                drawRect(shade(q).copy(alpha = (1f - age / 1.8f).coerceIn(0f, 1f) * 0.8f), Offset(x, y), Size(5f * u, 3f * u)) } }
        "beat-frame" -> { val w = (2f + 7f * a.loud + 4f * a.beat) * u
            drawRoundRect(lerp(c, Color.White, a.beat * 0.5f).copy(alpha = 0.35f + 0.55f * a.loud), Offset(w / 2f, w / 2f),
                Size(W - w, H - w), CornerRadius(18f * u), style = Stroke(w))
            drawRoundRect(c.copy(alpha = 0.12f + 0.2f * a.loud), Offset(w * 2f, w * 2f), Size(W - w * 4f, H - w * 4f), CornerRadius(14f * u), style = Stroke(w * 2.5f)) }
        "beat-spots" -> for (i in 0 until 4) { val x0 = W * (0.15f + i * 0.233f); val ang = sin(t * (0.4f + i * 0.13f) + i * 1.7f) * 0.45f
            val len = H * 1.1f; val spread = 0.12f + a.mid * 0.1f
            val tip = Offset(x0 + sin(ang) * len, cos(ang) * len)
            val p = Path(); p.moveTo(x0, -4f); p.lineTo(x0 + sin(ang - spread) * len, cos(ang - spread) * len); p.lineTo(x0 + sin(ang + spread) * len, cos(ang + spread) * len); p.close()
            drawPath(p, Brush.linearGradient(listOf(shade(i).copy(alpha = 0.10f + 0.28f * a.beat + 0.1f * a.loud), Color.Transparent), Offset(x0, 0f), tip))
            drawCircle(shade(i).copy(alpha = 0.6f), 6f * u, Offset(x0, 3f * u)) }
        "beat-lasers" -> { val o = Offset(W / 2f, H + 10f * u); val n = 9; val open = 0.35f + a.mid * 1.3f
            for (i in 0 until n) { val f = i / (n - 1f) - 0.5f; val ang = f * open + sin(t * 0.8f) * 0.15f
                val end = Offset(o.x + sin(ang) * H * 1.4f, o.y - cos(ang) * H * 1.4f)
                drawLine(shade(i).copy(alpha = 0.18f + 0.55f * a.high + 0.2f * a.beat), o, end, (1.2f + 1.5f * a.beat) * u)
                drawLine(shade(i).copy(alpha = 0.08f), o, end, 7f * u) } }
        "beat-disco" -> { val spin = a.travel * 0.25f
            for (i in 0 until 50) { val p = s[i + 40]; val r = (0.2f + p[0] * 0.95f) * max(W, H) * 0.6f; val ang = p[1] * tau + spin * (if (i % 2 == 0) 1f else -0.7f)
                val pos = Offset(mid.x + cos(ang) * r, mid.y + sin(ang) * r * 0.8f)
                val br = (0.15f + 0.5f * a.beat * p[2] + 0.2f * a.loud).coerceAtMost(0.9f)
                drawCircle(shade(i).copy(alpha = br), (3f + p[3] * 5f) * u, pos) } }
        "beat-speakers" -> for (side in 0..1) { val R = min(W, H) * 0.16f; val cx = if (side == 0) R * 1.05f else W - R * 1.05f; val cy = H - R * 1.05f
            val ctr = Offset(cx, cy); val push = 1f + a.bass * 0.18f
            drawCircle(Color.Black.copy(alpha = 0.35f), R, ctr); drawCircle(fg.copy(alpha = 0.25f), R, ctr, style = Stroke(2f * u))
            drawCircle(c.copy(alpha = 0.2f + 0.3f * a.bass), R * 0.78f * push, ctr, style = Stroke(3f * u))
            drawCircle(Brush.radialGradient(listOf(light.copy(alpha = 0.5f), c.copy(alpha = 0.15f)), ctr, R * 0.5f * push), R * 0.5f * push, ctr)
            drawCircle(Color.Black.copy(alpha = 0.5f), R * 0.16f * push, ctr) }
        "beat-ripples" -> for (j in 0 until a.beatAt.size) { val age = since(j); if (age < 0f || age > 2.5f) continue
            val b = a.beats - j; val o = hash(b, 9); val cx = (0.2f + o[0] * 0.6f) * W; val cy = H * (0.78f + o[1] * 0.15f)
            val r = 20f * u + age * W * 0.35f
            drawOval(c.copy(alpha = 0.45f * (1f - age / 2.5f)), Offset(cx - r, cy - r * 0.25f), Size(r * 2f, r * 0.5f), style = Stroke(2f * u)) }
        "beat-aurora" -> for (b in 0 until 3) { val p = Path(); val steps = 50; val top = H * (0.05f + b * 0.06f)
            p.moveTo(0f, 0f)
            for (j in 0..steps) { val f = j / steps.toFloat()
                p.lineTo(f * W, top + H * 0.12f + sin(f * 5f + t * (0.4f + b * 0.15f) + b) * H * 0.05f + a.at(f * 0.8f + b * 0.05f) * H * 0.22f) }
            p.lineTo(W, 0f); p.close()
            drawPath(p, Brush.verticalGradient(listOf(Color.Transparent, shade(b + 2).copy(alpha = 0.10f + 0.18f * a.loud)), 0f, H * 0.5f)) }
        "beat-heart" -> { val base = H * 0.85f; val p = Path(); val steps = 160; val speed = W * 0.35f
            for (j in 0..steps) { val x = j * W / steps; var y = 0f
                for (q in 0 until a.beatAt.size) { val at = W - since(q) * speed; val dx = x - at
                    if (abs(dx) < 24f * u) y += when { dx < -8f * u -> (dx + 24f * u) * 0.25f; dx < 4f * u -> -(8f * u - abs(dx)) * 6f; else -> (24f * u - dx) * 0.3f } }
                if (j == 0) p.moveTo(x, base + y) else p.lineTo(x, base + y) }
            drawPath(p, c.copy(alpha = 0.18f), style = Stroke(6f * u)); drawPath(p, light.copy(alpha = 0.75f), style = Stroke(1.8f * u)) }
        "beat-tremor" -> for (i in 0 until 14) { val y0 = H * (i + 0.5f) / 14f; val p = Path(); val amp = (1f + a.bass * 14f) * u
            for (j in 0..40) { val x = j * W / 40f; val y = y0 + sin(j * 1.7f + t * 25f + i) * amp * sin(j * 0.25f + i)
                if (j == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            drawPath(p, (tint ?: fg).copy(alpha = 0.07f + 0.15f * a.bass), style = Stroke(1f * u)) }
        "beat-bloom" -> { val ctr = Offset(W * 0.12f, H * 0.9f); val n = 12
            for (i in 0 until n) { val f = i / n.toFloat(); val ang = f * tau + t * 0.1f; val len = half * (0.18f + 0.35f * a.at(f))
                val tip = Offset(ctr.x + cos(ang) * len, ctr.y + sin(ang) * len); val side = Offset(-sin(ang), cos(ang)) * (len * 0.22f)
                val p = Path(); p.moveTo(ctr.x, ctr.y); p.quadraticTo(ctr.x + (tip.x - ctr.x) * 0.5f + side.x, ctr.y + (tip.y - ctr.y) * 0.5f + side.y, tip.x, tip.y)
                p.quadraticTo(ctr.x + (tip.x - ctr.x) * 0.5f - side.x, ctr.y + (tip.y - ctr.y) * 0.5f - side.y, ctr.x, ctr.y)
                drawPath(p, shade(i).copy(alpha = 0.22f + 0.2f * a.at(f))) }
            drawCircle(light.copy(alpha = 0.6f), half * 0.05f * (1f + a.bass), ctr) }
        "beat-burst" -> { val n = 48
            for (i in 0 until n) { val f = i / n.toFloat(); val ang = f * tau + t * 0.05f; val v = a.at(abs(f - 0.5f) * 2f)
                val r0 = half * 0.15f; val r1 = r0 + v * max(W, H) * 0.55f
                drawLine(c.copy(alpha = 0.10f + 0.3f * v), Offset(mid.x + cos(ang) * r0, mid.y + sin(ang) * r0), Offset(mid.x + cos(ang) * r1, mid.y + sin(ang) * r1), 3f * u) } }
        "beat-blobs" -> for (i in 0 until 7) { val p = s[i + 130]
            val pos = Offset((0.5f + 0.4f * sin(a.travel * 0.12f * (0.5f + p[2]) + p[0] * tau)) * W, (0.5f + 0.42f * cos(a.travel * 0.1f * (0.5f + p[3]) + p[1] * tau)) * H)
            val r = (60f + 60f * p[2]) * u * (0.75f + a.bass * 0.8f)
            drawCircle(Brush.radialGradient(listOf(shade(i).copy(alpha = 0.28f), shade(i).copy(alpha = 0.08f), Color.Transparent), pos, r), r, pos) }
        "beat-comets" -> for (j in 0 until 4) { val age = since(j); if (age < 0f || age > 2.2f) continue
            val b = a.beats - j; val o = hash(b, 11); if (o[2] < 0.45f) continue   // only some beats
            val ph = age / 2.2f; val y0 = H * (0.08f + o[1] * 0.4f); val right = o[0] > 0.5f
            val head = Offset(if (right) -60f * u + ph * (W + 120f * u) else W + 60f * u - ph * (W + 120f * u), y0 + ph * H * 0.25f)
            val tail = head + Offset(if (right) -160f * u else 160f * u, -40f * u)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, light.copy(alpha = 0.8f)), tail, head), tail, head, 3f * u)
            drawCircle(Color.White.copy(alpha = 0.9f), 3.5f * u, head) }
        "beat-fountain" -> for (jet in 0 until 5) { val x0 = W * (0.1f + jet * 0.2f); val v = a.at(jet / 4f * 0.7f); val top = H * (0.12f + v * 0.5f)
            for (q in 0 until 18) { val p = hash(jet * 18 + q, 13); val ph = (p[0] + a.travel * 0.35f) % 1f
                val x = x0 + (p[1] - 0.5f) * 60f * u * ph; val y = H - 4f * ph * (1f - ph) * top
                drawCircle(shade(jet).copy(alpha = 0.55f * (1f - ph * 0.6f)), (2f + p[2] * 2f) * u, Offset(x, y)) } }
        "beat-grid" -> { val hz = H * 0.62f; val gc = c.copy(alpha = 0.25f + 0.45f * a.bass)
            drawLine(gc, Offset(0f, hz), Offset(W, hz), 1.5f * u)
            for (i in -10..10) drawLine(gc.copy(alpha = gc.alpha * 0.7f), Offset(W / 2f + i * W * 0.03f, hz), Offset(W / 2f + i * W * 0.22f, H), 1f * u)
            for (j in 0 until 10) { val f = ((j + a.travel * 1.5f) % 10f) / 10f; val y = hz + (H - hz) * f * f
                drawLine(gc.copy(alpha = gc.alpha * f), Offset(0f, y), Offset(W, y), (0.8f + f * 1.5f) * u) } }
        "beat-radar" -> { val R = half * 0.8f; val sweep = (a.travel * 1.1f) % tau
            for (r in 1..4) drawCircle(c.copy(alpha = 0.12f), R * r / 4f, mid, style = Stroke(1f * u))
            drawLine(c.copy(alpha = 0.6f), mid, Offset(mid.x + cos(sweep) * R, mid.y + sin(sweep) * R), 2f * u)
            for (q in 1..20) { val ang = sweep - q * 0.04f; drawLine(c.copy(alpha = 0.18f * (1f - q / 20f)), mid, Offset(mid.x + cos(ang) * R, mid.y + sin(ang) * R), 3f * u) }
            for (i in 0 until 24) { val f = i / 24f; val v = a.at(f); if (v < 0.25f) continue; val ang = f * tau
                val behind = ((sweep - ang) % tau + tau) % tau; val fade = (1f - behind / tau)
                drawCircle(light.copy(alpha = 0.8f * fade), (2f + v * 4f) * u, Offset(mid.x + cos(ang) * R * (1f - v * 0.7f), mid.y + sin(ang) * R * (1f - v * 0.7f))) } }
        "beat-bolt" -> { val age = since(0); if (age in 0f..0.35f && a.power > 0.5f) { val o = hash(a.beats, 17)
            val p = Path(); var x = (0.15f + o[0] * 0.7f) * W; var y = 0f; p.moveTo(x, y)
            // All the way down: it ends past the bottom edge (it used to stop three quarters of the way), and never wanders off a side
            var q = 0; while (y < H) { val h = hash(a.beats * 13 + q, 19)
                x = (x + (h[0] - 0.5f) * 70f * u).coerceIn(0.05f * W, 0.95f * W); y += (20f + h[1] * 40f) * u; p.lineTo(x, y); q++ }
            val fade = 1f - age / 0.35f
            drawRect(light.copy(alpha = 0.10f * fade))
            drawPath(p, light.copy(alpha = 0.35f * fade), style = Stroke(8f * u)); drawPath(p, Color.White.copy(alpha = 0.95f * fade), style = Stroke(2f * u)) } }
        "beat-neon" -> for (i in 0 until 6) { val p = s[i + 60]; val ctr = Offset((0.15f + p[0] * 0.7f) * W, (0.12f + p[1] * 0.76f) * H)
            val r = (26f + p[2] * 40f) * u * (1f + 0.25f * a.beat); val sides = 3 + i % 3 + (if (i % 3 == 2) 7 else 0)   // triangle, square, circle
            val rot = t * (0.15f + p[3] * 0.25f) * (if (i % 2 == 0) 1f else -1f)
            val col = shade(i).copy(alpha = 0.25f + 0.55f * a.beat * (0.5f + p[3]) + 0.1f * a.loud)
            val path = Path()
            for (q in 0..sides) { val ang = rot + q * tau / sides; val pt = Offset(ctr.x + cos(ang) * r, ctr.y + sin(ang) * r); if (q == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y) }
            drawPath(path, col.copy(alpha = col.alpha * 0.3f), style = Stroke(9f * u)); drawPath(path, col, style = Stroke(2f * u)) }
        "beat-fog" -> { for (j in 0 until 6) { val age = since(j); if (age < 0f || age > 6f) continue
                val o = hash(a.beats - j, 23); if (o[2] < 0.35f) continue
                val x = (0.1f + o[0] * 0.8f) * W; val rise = age / 6f
                val y = H - rise * H * 0.45f; val r = (60f + 140f * rise) * u
                drawCircle(Brush.radialGradient(listOf(light.copy(alpha = 0.22f * (1f - rise)), Color.Transparent), Offset(x, y), r), r, Offset(x, y)) }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.08f + 0.12f * a.bass)), H * 0.75f, H), Offset(0f, H * 0.75f), Size(W, H * 0.25f)) }
        "beat-fireworks" -> for (j in 0 until 4) { val age = since(j); if (age < 0f || age > 2.2f || a.power < 0.35f) continue
            val b = a.beats - j; val o = hash(b, 29); val cx = (0.15f + o[0] * 0.7f) * W; val cy = (0.1f + o[1] * 0.35f) * H
            val fade = (1f - age / 2.2f).coerceIn(0f, 1f); val col = shade(b)
            for (q in 0 until 26) { val ang = q * tau / 26f + o[2]; val v = (70f + 60f * hash(b * 26 + q, 31)[0]) * u
                val x = cx + cos(ang) * v * age; val y = cy + sin(ang) * v * age + 60f * u * age * age
                drawLine(col.copy(alpha = 0.8f * fade), Offset(x, y), Offset(x - cos(ang) * 6f * u, y - sin(ang) * 6f * u - 2f * u), 2f * u)
                drawCircle(Color.White.copy(alpha = 0.6f * fade), 1.4f * u, Offset(x, y)) } }
        "beat-bubbles" -> for (i in 0 until 28) { val p = s[i + 150]
            val cyc = 9f + p[2] * 8f; val life = ((t + p[3] * cyc) % cyc) / cyc
            val popped = a.beat > 0.6f && p[0] < 0.25f + 0.5f * a.power   // some pop on the beat
            val x = p[0] * W + sin(t * 0.8f + i) * 12f * u; val y = H * (1.05f - life * 1.1f); val r = (6f + p[1] * 14f) * u
            if (popped && life > 0.2f) for (q in 0 until 6) { val ang = q * tau / 6f
                drawLine(light.copy(alpha = 0.6f * a.beat), Offset(x + cos(ang) * r * 0.8f, y + sin(ang) * r * 0.8f), Offset(x + cos(ang) * r * 1.5f, y + sin(ang) * r * 1.5f), 1.5f * u) }
            else { drawCircle(c.copy(alpha = 0.10f), r, Offset(x, y)); drawCircle(light.copy(alpha = 0.45f), r, Offset(x, y), style = Stroke(1.2f * u))
                drawCircle(Color.White.copy(alpha = 0.5f), r * 0.22f, Offset(x - r * 0.35f, y - r * 0.35f)) } }
        "beat-fireflies" -> for (i in 0 until 40) { val p = s[i + 110]
            val x = (p[0] + 0.03f * sin(t * (0.3f + p[2] * 0.4f) + i)) * W; val y = (0.35f + p[1] * 0.6f + 0.03f * cos(t * (0.25f + p[3] * 0.3f) + i)) * H
            val glow = (0.08f + 0.85f * a.beat * (0.4f + 0.6f * p[3]) + 0.1f * a.high).coerceAtMost(1f)
            val col = tint ?: Color(0xFFFFF27A)
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.35f * glow), Color.Transparent), Offset(x, y), 14f * u), 14f * u, Offset(x, y))
            drawCircle(col.copy(alpha = glow), 2.2f * u, Offset(x, y)) }
        else -> return false
    }
    return true
}
