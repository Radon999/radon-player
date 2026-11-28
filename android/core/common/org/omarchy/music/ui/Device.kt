package org.omarchy.music.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

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

/** Only a long press (on the PC also a right-click), for the menu: a tap or a left click does nothing at all, with no
 *  press shown (Wrapped's songs: a tap there lit the row and shook the slide). A tap is still taken, so it doesn't
 *  turn the slide either. */
fun androidx.compose.ui.Modifier.holdOnly(onHold: () -> Unit): androidx.compose.ui.Modifier = this.composed {
    val hold = androidx.compose.runtime.rememberUpdatedState(onHold)   // (the latest, without starting the touch over)
    rightClick { hold.value() }.then(androidx.compose.ui.Modifier.pointerInput(Unit) {
        detectTapGestures(onLongPress = { hold.value() }, onTap = { })
    })
}

/** What the PC's words are for a touch: "tap"/"click", "long-press"/"right-click". */
val TAP: String get() = if (onPhone) "tap" else "click"
val LONG_PRESS: String get() = if (onPhone) "long-press" else "right-click"
/** Where removed files go: Android's trash, or the Recycle Bin. */
val TRASH: String get() = if (onPhone) "Android's trash" else "the Recycle Bin"

/** "right-click" → "Right-click". */
fun String.cap(): String = replaceFirstChar { it.uppercase() }
