package org.omarchy.music.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    /** Worked out again: called whenever the look is put on, which is also each time the cover's colour changes. */
    fun update() {
        now = if (on() && Music.look == Looks.NOW_PLAYING) CoverSceneRules.scene(Music.coverRgb) else null
    }

    fun set(on: Boolean) { Prefs.set(ON, on); Music.applyLook() }
}
