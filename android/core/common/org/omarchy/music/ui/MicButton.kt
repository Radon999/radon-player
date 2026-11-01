package org.omarchy.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** 🎤 beside a search bar: say it instead of typing it (the phone's speech recognizer, Windows' dictation on the PC). */
@Composable
fun MicButton(onText: (String) -> Unit) {
    val listen = rememberVoiceInput(onText) ?: return
    IconButton(Ico.MIC, C.muted, 20.dp, 6.dp) { listen() }
}
