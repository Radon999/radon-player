package org.omarchy.music.ui

import androidx.compose.foundation.combinedClickable

/** Which one this is: the phone's app (Android) or the PC's. The screens are the same; a few lines differ. */
val onPhone: Boolean get() = org.omarchy.music.platformName == "android"

/** What the screens call this device: "phone" or "PC". */
val DEVICE: String get() = if (onPhone) "phone" else "PC"

/** Your own songs' place, as the source chips name it: "Phone" or "This PC". */
val DEVICE_NAME: String get() = if (onPhone) "Phone" else "This PC"

/** A tap, and a long press (on the PC also a right-click) for the menu. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun androidx.compose.ui.Modifier.pressable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): androidx.compose.ui.Modifier =
    (if (onLongClick != null) this.rightClick(onLongClick) else this)
        .then(androidx.compose.ui.Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick))

/** What the PC's words are for a touch: "tap"/"click", "long-press"/"right-click". */
val TAP: String get() = if (onPhone) "tap" else "click"
val LONG_PRESS: String get() = if (onPhone) "long-press" else "right-click"
/** Where removed files go: Android's trash, or the Recycle Bin. */
val TRASH: String get() = if (onPhone) "Android's trash" else "the Recycle Bin"

/** "right-click" → "Right-click". */
fun String.cap(): String = replaceFirstChar { it.uppercase() }
