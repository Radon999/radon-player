package org.omarchy.music

/** Settings › Prevent album cover changes: on, nothing changes a song's cover in its file (no sync, scan or auto
 *  tagger) except a cover you pick yourself (the Tagger's Cover, Cover…), which runs [byHand]. */
object CoverGuard {
    const val PREVENT = "prevent_cover_changes"
    val prevented: Boolean get() = Prefs.on(PREVENT)
    private val hand = ThreadLocal<Boolean>()

    /** run as a cover you picked yourself: it goes in whatever the switch says. */
    fun <T> byHand(run: () -> T): T { hand.set(true); try { return run() } finally { hand.set(false) } }

    /** Whether a cover may be written now. */
    fun allowed(): Boolean = !prevented || hand.get() == true

    /** The note a scan that could change covers shows while the switch is on. */
    const val NOTE = "Prevent album cover changes is on (Settings › Scanner): this scan won't change any covers"
}
