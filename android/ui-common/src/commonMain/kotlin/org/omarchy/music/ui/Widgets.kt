package org.omarchy.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The app's small pieces, the same on the phone and the PC: ticks, badges, chips, pills, buttons, fields.

/** A round tick: filled in the accent when on. */
@Composable
fun Tick(on: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape)
        .background(if (on) C.accent else C.light)
        .then(if (on) Modifier else Modifier.border(1.5.dp, C.muted, androidx.compose.foundation.shape.CircleShape)),
        contentAlignment = Alignment.Center) {
        if (on) Icon(Ico.CHECK, C.darker, 16.dp)
    }
}

/** A small mark: FLAC, ✓, a quality. */
@Composable
fun Badge(text: String, on: Boolean = true) {
    Text(text, style = T.time.copy(color = if (on) C.accent else C.muted, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        modifier = Modifier.border(1.dp, (if (on) C.accent else C.muted).copy(alpha = 0.55f), R.small).padding(horizontal = 5.dp, vertical = 1.dp))
}

@Composable
fun Section(text: String) {
    Text(text, style = T.section, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
fun BackRow(text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Ico.BACK, C.accent, 18.dp)
        Text(text, style = T.title.copy(color = C.accent), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun ActionRow(text: String, onClick: () -> Unit) {
    Text(text, style = T.body.copy(color = C.accent, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(R.card).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 13.dp))
}

@Composable
fun Empty(title: String, sub: String = "") {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = T.title.copy(fontSize = T.title.fontSize * 1.1f), textAlign = TextAlign.Center)
        if (sub.isNotEmpty()) Text(sub, style = T.sub, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

/** The desktop's chips: a quiet word, the accent with a frame when on. */
@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(text, style = T.tab.copy(color = if (on) C.accent else C.muted, shadow = if (on) C.shadow else null),
        modifier = Modifier
            .clip(R.pill)
            .background(if (on) C.accent.copy(alpha = 0.16f) else C.fg.copy(alpha = 0.05f))
            .border(1.dp, if (on) C.accent.copy(alpha = 0.5f) else Color.Transparent, R.pill)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
fun Pill(text: String, quiet: Boolean = false, onClick: () -> Unit) {
    Text(text, style = T.tab.copy(color = if (quiet) C.fg else if (C.isLight) C.light else C.darker,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        modifier = Modifier.clip(R.pill)
            .background(if (quiet) C.fg.copy(alpha = 0.07f) else C.accent)
            .border(1.dp, if (quiet) C.line else C.accent, R.pill)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp))
}


/** A round icon button. */
@Composable
fun IconButton(ico: Ico, color: Color = C.fg, size: Dp = 24.dp, pad: Dp = 10.dp, desc: String = DESC[ico] ?: "", onClick: () -> Unit) {
    Box(Modifier.clip(R.pill).clickable(onClick = onClick).semantics { contentDescription = desc }.padding(pad),
        contentAlignment = Alignment.Center) { Icon(ico, color, size) }
}

/** What an icon button says to TalkBack. */
val DESC = mapOf(Ico.MENU to "Menu", Ico.SEARCH to "Search", Ico.BACK to "Back", Ico.DOWN to "Close",
    Ico.MORE to "More", Ico.PLAY to "Play", Ico.PAUSE to "Pause", Ico.NEXT to "Next", Ico.PREV to "Previous",
    Ico.SHUFFLE to "Shuffle", Ico.REPEAT to "Repeat", Ico.REPEAT_ONE to "Repeat one", Ico.CLOSE to "Close",
    Ico.SETTINGS to "Settings", Ico.EQ to "Equalizer")


@Composable
fun Field(value: String, hint: String, password: Boolean = false, multiLine: Boolean = false, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 3.dp).border(1.dp, C.line).background(C.fg.copy(alpha = 0.04f))
        .padding(horizontal = 10.dp, vertical = 10.dp)) {
        if (value.isEmpty()) Text(hint, style = T.body.copy(color = C.muted))
        BasicTextField(value, onChange, singleLine = !multiLine, textStyle = T.body, cursorBrush = SolidColor(C.accent),
            visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().typing())
    }
}

/** Whether you're typing in a box (the PC's one-key shortcuts, Space and the letters, wait while you are). */
object Typing {
    var boxes = 0
        internal set
    val now: Boolean get() = boxes > 0
}

/** A text box's modifier: counts it in Typing while it has the focus. */
fun Modifier.typing(): Modifier = composed {
    val was = remember { booleanArrayOf(false) }
    DisposableEffect(Unit) { onDispose { if (was[0]) { was[0] = false; Typing.boxes-- } } }
    Modifier.onFocusChanged { f -> if (f.isFocused != was[0]) { was[0] = f.isFocused; Typing.boxes += if (was[0]) 1 else -1 } }
}
