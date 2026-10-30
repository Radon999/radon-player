package org.omarchy.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Whether the app may read your music, and a way to ask: on the PC it always may. */
class MusicAccess(val granted: Boolean, val ask: () -> Unit)

@Composable
fun rememberMusicAccess(): MusicAccess = remember { MusicAccess(true) {} }
