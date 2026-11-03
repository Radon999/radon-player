package org.omarchy.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.omarchy.music.Lyrics
import org.omarchy.music.Music
import org.omarchy.music.Track

/** A lyrics card, as the desktop draws it: the lines you tick, big, on the cover's colour, with the cover,
 *  title and artist; 1080×1350, to share or save. */
fun lyricsCard(lines: List<String>, t: Track, style: MyCard? = LyricsCards.of(LyricsCards.picked()),
               nowPlaying: Boolean = LyricsCards.picked() == LyricsCards.NOW): ImageBitmap {
    val w = 1080; val h = 1350; val pad = 96f
    val pt = Painter(w, h)
    val artPath = Music.lib.items[t.id]?.art?.takeIf { it.isNotEmpty() }
    val art = artPath?.let { runCatching { decodeImage(java.io.File(it).readBytes()) }.getOrNull() }
        ?: t.file?.let { f -> runCatching { org.omarchy.music.FileTags.cover(Music.ctx, f)?.let { decodeImage(it) } }.getOrNull() }   // your own file's cover
    if (style != null) return styledCard(pt, w, h, pad, lines, t, art, style)
    // The cover's colour: its average, made darker for white words to read on.
    val hsv = FloatArray(3)
    if (art != null) {
        val (px, _, _) = pixelsOf(art)
        var r = 0L; var g = 0L; var b = 0L
        for (p in px) { r += Rgb.red(p); g += Rgb.green(p); b += Rgb.blue(p) }
        val n = px.size.coerceAtLeast(1)
        Rgb.toHsv(Rgb.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt()), hsv)
    } else { hsv[0] = 240f; hsv[1] = 0.25f }
    hsv[1] = hsv[1].coerceIn(0.2f, 0.7f).let { if (hsv[1] < 0.08f) hsv[1] else it }
    hsv[2] = 0.42f
    val coverTop = Rgb.fromHsv(hsv)
    hsv[2] = 0.22f
    val coverBottom = Rgb.fromHsv(hsv)
    // The look in use on this device: its background (and fade), its text, accent and muted colours. The Now playing
    // look takes the cover's colours, as it does on every screen.
    // The Now playing style: the card in the cover's colours, as with the Now playing look, whatever the look in use
    val coverLook = Music.look == Looks.NOW_PLAYING || nowPlaying
    // The cover's own bright colour (as the Now playing look makes its accent) when the look in use isn't the Now playing one
    val coverAccent = if (Music.look == Looks.NOW_PLAYING) LOOK_ACCENT else hsv.copyOf().let { c ->
        c[1] = (c[1] + 0.15f).coerceIn(0.25f, 0.9f); c[2] = 0.92f; androidx.compose.ui.graphics.Color(Rgb.fromHsv(c)) }
    // The Now playing look's Lyrics screen is black (plain), its words in the cover's colour: the card is too
    val plain = coverLook && Vis.plainNow()
    val grad = C.gradient
    val top = if (plain) Rgb.rgb(0, 0, 0) else if (coverLook) coverTop else (grad?.firstOrNull() ?: C.bg).toArgb()
    val bottom = if (plain) Rgb.rgb(0, 0, 0) else if (coverLook) coverBottom else (grad?.lastOrNull() ?: C.bg).toArgb()
    pt.gradient(0f, 0f, w.toFloat(), h.toFloat(), top, bottom)
    // The look's stars and effects on the card too, as they are on the screen (standing still), the Now playing look's
    // as well (the ones picked in Personalization, in the cover's colours)
    run {
        val seeds = java.util.Random(11).let { r -> List(200) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
        val fx = LookFx.keys(C.fx); val tints = LookFx.colors(C.fx)
        val acc = if (coverLook) coverAccent else C.accent; val fg = C.fg; val bgc = C.bg
        pt.draw {
            if (C.stars) for (i in 0 until 70) { val p = seeds[i]
                drawCircle(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f + p[3] * 0.5f), (1f + p[2] * 2.2f), androidx.compose.ui.geometry.Offset(p[0] * size.width, p[1] * size.height)) }
            for (k in LookFx.ALL.map { it.key }.filter { it in fx }) drawFx(k, 3f, seeds, acc, fg, bgc, tints[k])
        }
    }
    fun ink(c: androidx.compose.ui.graphics.Color, alpha: Int) = Rgb.argb(alpha, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
    var left = pad
    if (art != null) {
        pt.picture(art, pad, pad, 132f, 14f)
        left = pad + 132f + 32f
    }
    fun white(alpha: Int) = Rgb.argb(alpha, 255, 255, 255)
    val words = if (plain) ink(coverAccent, 255) else if (coverLook) white(255) else ink(C.fg, 255)        // the lyrics: the look's text colour
    val titleInk = if (plain) ink(coverAccent, 255) else if (coverLook) white(255) else ink(C.accent, 255) // the song's name: its accent
    val soft = if (coverLook) white(190) else ink(C.muted, 230)
    val th = pt.block(t.title, 44f, true, titleInk, left, pad + 18f, w - left - pad, maxLines = 4)
    pt.block(t.artist, 36f, false, soft, left, pad + 26f + th, w - left - pad, maxLines = 4)
    // As the Lyrics screen looks: the words on a rounded card in the look's colours (over its background, stars and
    // effects), a small LYRICS heading at the card's top, and the song above it.
    val cardTop = pad + 132f + 56f; val cardBottom = h - 130f
    val cardLeft = 48f; val cardRight = w - 48f
    val panel = when {
        plain -> androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f)
        coverLook -> androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f)
        C.isLight -> C.light.copy(alpha = 0.94f)
        else -> C.light.copy(alpha = 0.6f)
    }
    pt.draw { drawRoundRect(panel, androidx.compose.ui.geometry.Offset(cardLeft, cardTop),
        androidx.compose.ui.geometry.Size(cardRight - cardLeft, cardBottom - cardTop), androidx.compose.ui.geometry.CornerRadius(44f)) }
    pt.text("L Y R I C S", 26f, true, soft, cardLeft + 48f, cardTop + 40f, cardRight - cardLeft - 96f)
    val body = lines.joinToString("\n")
    val inner = cardRight - cardLeft - 96f
    val size = (if (body.length < 120) 64f else if (body.length < 220) 56f else if (body.length < 380) 46f else if (body.length < 520) 40f else 34f).let { if (lines.size > 12) minOf(it, 40f) else if (lines.size > 9) minOf(it, 48f) else it }
    val bh = pt.blockHeight(body, size, false, inner, spacing = 14f)
    val room = cardBottom - (cardTop + 110f) - 40f
    val y = cardTop + 110f + maxOf(0f, (room - bh) / 2f)
    pt.block(body, size, false, words, cardLeft + 48f, y, inner, spacing = 14f)
    pt.text("Radon Player", 30f, true, if (plain) ink(coverAccent, 200) else if (coverLook) white(140) else ink(C.accent, 200), pad, h - 72f, w - 2 * pad)
    return pt.img
}

/** A card in one of the lyrics card styles (a ready-made one or yours): laid out as the look's card is. */
private fun styledCard(pt: Painter, w: Int, h: Int, pad: Float, lines: List<String>, t: Track, art: ImageBitmap?, c: MyCard): ImageBitmap {
    fun col(v: Long) = androidx.compose.ui.graphics.Color(v)
    fun ink(v: Long, alpha: Int) = Rgb.argb(alpha, ((v shr 16) and 0xFF).toInt(), ((v shr 8) and 0xFF).toInt(), (v and 0xFF).toInt())
    pt.gradient(0f, 0f, w.toFloat(), h.toFloat(), ink(c.top, 255), ink(c.bottom, 255))
    val seeds = java.util.Random(11).let { r -> List(200) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    val fx = LookFx.keys(c.fx); val tints = LookFx.colors(c.fx)
    pt.draw {
        if (c.stars) for (i in 0 until 70) { val p = seeds[i]
            drawCircle(col(c.words).copy(alpha = 0.25f + p[3] * 0.5f), (1f + p[2] * 2.2f), androidx.compose.ui.geometry.Offset(p[0] * size.width, p[1] * size.height)) }
        for (k in LookFx.ALL.map { it.key }.filter { it in fx }) drawFx(k, 3f, seeds, col(c.title), col(c.words), col(c.top), tints[k])
    }
    var left = pad
    if (art != null) {
        pt.picture(art, pad, pad, 132f, if (c.roundCover) 66f else 14f)
        left = pad + 132f + 32f
    }
    val th = pt.block(t.title, 44f, true, ink(c.title, 255), left, pad + 18f, w - left - pad, maxLines = 4)
    pt.block(t.artist, 36f, false, ink(c.soft, 255), left, pad + 26f + th, w - left - pad, maxLines = 4)
    val cardTop = pad + 132f + 56f; val cardBottom = h - 130f
    val cardLeft = 48f; val cardRight = w - 48f
    if (c.panelAlpha > 0.01f) pt.draw { drawRoundRect(col(c.panel).copy(alpha = c.panelAlpha.coerceIn(0f, 1f)), androidx.compose.ui.geometry.Offset(cardLeft, cardTop),
        androidx.compose.ui.geometry.Size(cardRight - cardLeft, cardBottom - cardTop), androidx.compose.ui.geometry.CornerRadius(c.corners.coerceIn(0f, 120f))) }
    pt.text("L Y R I C S", 26f, true, ink(c.soft, 230), cardLeft + 48f, cardTop + 40f, cardRight - cardLeft - 96f)
    val body = lines.joinToString("\n")
    val inner = cardRight - cardLeft - 96f
    val base = (if (body.length < 120) 64f else if (body.length < 220) 56f else if (body.length < 380) 46f else if (body.length < 520) 40f else 34f).let { if (lines.size > 12) minOf(it, 40f) else if (lines.size > 9) minOf(it, 48f) else it }
    val room = cardBottom - (cardTop + 110f) - 40f
    // Bigger type only as far as the words still fit on the card
    var size = base * c.textScale.coerceIn(0.7f, 1.4f)
    while (size > 24f && pt.blockHeight(body, size, c.heavy, inner, spacing = 14f) > room) size -= 2f
    val bh = pt.blockHeight(body, size, c.heavy, inner, spacing = 14f)
    val y = cardTop + 110f + maxOf(0f, (room - bh) / 2f)
    pt.block(body, size, c.heavy, ink(c.words, 255), cardLeft + 48f, y, inner, spacing = 14f)
    pt.text("Radon Player", 30f, true, ink(c.brand, 200), pad, h - 72f, w - 2 * pad)
    return pt.img
}

/** Lyrics card…: tick up to 15 lines, see the card, then share or save it. */
@Composable
fun LyricsCardDialog(ui: Ui, t: Track, got: Lyrics.Got) {
    val words = remember(got) {
        (got.plain ?: Lyrics.parseLrc(got.synced).joinToString("\n") { it.second })
            .lines().filter { it.isNotBlank() && !(it.startsWith("[") && it.endsWith("]")) }
    }
    val picked = remember(got) { mutableStateListOf<String>() }
    val lines = picked.ifEmpty { words.take(1) }
    // The card's style: Your look, a ready-made one or yours (the last one picked stays)
    var styleKey by remember { mutableStateOf(LyricsCards.picked()) }
    val mine = LyricsCards.mine()
    val bmp = remember(lines.toList(), t.id, Music.look, C.bg, C.fg, C.accent, styleKey, mine) { lyricsCard(lines.toList(), t, LyricsCards.of(styleKey), styleKey == LyricsCards.NOW) }
    val box = CheckboxDefaults.colors(checkedColor = C.accent, checkmarkColor = C.darker, uncheckedColor = C.muted)
    AlertDialog(onDismissRequest = { ui.lyricsCard = null }, containerColor = C.light,
        title = { Text("Lyrics card", style = T.head) },
        text = {
            Column {
                Image(bmp, "The card", Modifier.size(208.dp, 260.dp).align(Alignment.CenterHorizontally))
                Text("Tick the lines for the card (up to 15):", style = T.sub, modifier = Modifier.padding(vertical = 6.dp))
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(words.size) { i ->
                        val wd = words[i]
                        val on = wd in picked
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, { if (it) { if (picked.size < 15) picked += wd } else picked -= wd },
                                enabled = on || picked.size < 15, colors = box)
                            Text(wd, style = T.body)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Style: hold one or tap its ⋯ (right-click on the PC) to favourite it; yours can be edited or deleted too
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Text("Style", style = T.sub, modifier = Modifier.padding(end = 8.dp))
                    ChoicePill(Favs.sorted(Favs.CARDS, LyricsCards.menu()), styleKey, describe = { LyricsCards.describe(it) },
                        preview = { k -> val b = remember(k, t.id, mine) { lyricsCard(lines.toList().take(3), t, LyricsCards.of(k), k == LyricsCards.NOW) }
                            Image(b, null, Modifier.size(144.dp, 180.dp)) },
                        menu = { k, l -> Favs.menu(Favs.CARDS, k, l) }) {
                        styleKey = it; LyricsCards.pick(it)
                    }
                }
                if (onPhone) Row {
                    TextButton({ if (!sharePicture(bmp, "${t.title} lyrics", "com.instagram.android")) Music.toast("Instagram isn't on this $DEVICE") }) {
                        Text("Instagram", color = C.accent) }
                    TextButton({ if (!sharePicture(bmp, "${t.title} lyrics", "com.facebook.katana")) Music.toast("Facebook isn't on this $DEVICE") }) {
                        Text("Facebook", color = C.accent) }
                }
                Row {
                    TextButton({ Music.toast(if (savePicture(bmp, "${t.title} lyrics")) savedPictureNote() else "Couldn't save it") }) {
                        Text("Save", color = C.muted) }
                    TextButton({ sharePicture(bmp, "${t.title} lyrics", null) }) { Text(if (onPhone) "Messages & more…" else SHARE_PICTURE, color = C.accent) }
                }
            }
        },
        dismissButton = { TextButton({ ui.lyricsCard = null }) { Text("Close", color = C.muted) } })
}
