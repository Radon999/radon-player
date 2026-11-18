package org.omarchy.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.omarchy.music.Remote
import org.omarchy.music.CoverSceneRules
import org.omarchy.music.Music
import org.omarchy.music.Prefs

/** The Now playing preset, "Follows the cover" ([CoverSceneRules]): with it on and the Now playing look in use, each
 *  song's cover picks a whole scene, not just the colours: the effects behind everything and Now playing's two
 *  visualizers, in the cover's colour. It changes with every song. Nothing of yours is overwritten: the effects and
 *  visualizers you picked are kept as they are and come back the moment it is switched off (or another look is put
 *  on, or nothing is playing). */
object CoverScene {
    /** The switch (Personalization › Effects › Scenes from the cover; the preset turns it on, any other preset off). */
    const val ON = "np_cover_scene"
    /** The preset's key in the Presets menu. */
    const val PRESET = "preset-cover"

    /** The scene on now (null: none, the app as you set it). Read by the screens, so they redraw when it changes. */
    var now by mutableStateOf<CoverSceneRules.Scene?>(null)
        private set

    fun on(): Boolean = Prefs.on(ON)

    /** Glass cover (its own preset, and the switch Blurred cover behind everything): with the Now playing look, the
     *  cover of the song playing is drawn blurred behind the whole app, and everything sits over it see-through. */
    const val GLASS = "np_cover_glass"
    const val GLASS_PRESET = "preset-glass"
    /** Whether the blurred cover is behind everything now (read by the window, so it redraws when this changes). */
    var glassNow by mutableStateOf(false)
        private set
    fun glassOn(): Boolean = Prefs.on(GLASS)
    fun setGlass(on: Boolean) { Prefs.set(GLASS, on); Music.applyLook() }

    /** Worked out again: called whenever the look is put on, which is also each time the cover's colour changes. */
    fun update() {
        now = if (on() && Music.look == Looks.NOW_PLAYING) CoverSceneRules.scene(Music.coverRgb) else null
        glassNow = glassOn() && Music.look == Looks.NOW_PLAYING
    }

    fun set(on: Boolean) { Prefs.set(ON, on); Music.applyLook() }
}

/** Glass cover's backdrop, behind every screen: the playing song's cover, blurred until it is only its colours and
 *  shapes, with the look's colour (the cover's own) washed over it so text reads on it: lighter at the top, deeper
 *  at the bottom. A small copy of the cover is used, drawn big: soft already, so it is still a blur on a phone too
 *  old to blur pictures, and cheap to draw. Nothing playing, or no cover: nothing (the look's own background). */
@Composable
fun CoverGlassBackdrop() {
    val t = Music.current() ?: return
    val art = (if (Remote.active) Remote.artModel(t) else artModel(t, 96)) ?: return
    AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(46.dp))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(C.bg.copy(alpha = 0.38f), C.bg.copy(alpha = 0.56f), C.darker.copy(alpha = 0.78f)))))
}
