package org.omarchy.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.omarchy.music.Prefs
import org.omarchy.music.UiSizeRules

/** Size of everything (Settings › General, [UiSizeRules]) on the PC: what is inside is drawn that much bigger or
 *  smaller, text and all, its boxes and menus too, and changes the moment the setting does. (The phone does the same
 *  through its screen's own density, in MainActivity, so its system boxes follow as well.) */
@Composable
fun UiSized(content: @Composable () -> Unit) {
    val f = UiSizeRules.factor(Prefs.text(UiSizeRules.PREF))
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density * f, d.fontScale), content = content)
}
