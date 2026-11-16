package org.omarchy.music

/** Settings › Smoothest screen: the app asks the phone's screen for its highest refresh rate (90, 120, 144 Hz…), where
 *  the screen has one above 60. Android decides in the end (battery saver, or the phone's own "standard" motion
 *  setting, can hold it at 60); off, the app asks for nothing and Android picks as it always did. */
object ScreenRate {
    const val PREF = "high_refresh"
    private var win: java.lang.ref.WeakReference<android.view.Window>? = null

    /** The app's window is there (MainActivity): what was chosen is asked for. */
    fun attach(w: android.view.Window) { win = java.lang.ref.WeakReference(w); apply() }

    private fun display(w: android.view.Window): android.view.Display? =
        if (android.os.Build.VERSION.SDK_INT >= 30) runCatching { w.context.display }.getOrNull()
        else @Suppress("DEPRECATION") w.windowManager.defaultDisplay

    /** The most pictures a second this screen can show (60, 90, 120…); 0 when it isn't known yet. */
    fun maxHz(): Int = runCatching {
        val w = win?.get() ?: return@runCatching 0
        Math.round(display(w)?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 0f)
    }.getOrDefault(0)

    /** Ask for the highest rate at the size the screen is showing now (on), or for nothing (off). Main thread. */
    fun apply() {
        val w = win?.get() ?: return
        runCatching {
            val d = display(w) ?: return
            val now = d.mode
            val best = if (!Prefs.on(PREF)) null
                else d.supportedModes.filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight }.maxByOrNull { it.refreshRate }
            val lp = w.attributes
            lp.preferredDisplayModeId = best?.modeId ?: 0
            lp.preferredRefreshRate = best?.refreshRate ?: 0f
            w.attributes = lp
        }
    }
}
