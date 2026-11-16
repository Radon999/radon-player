package org.omarchy.music

/** Casting to another device: what's playing goes there from where it was, and comes back the same way. The cast
 *  device's player and its name are [Music]'s (the screens read them); [Music] keeps the names and hands on. */
object Casting {
    /** What kind of file the cast device is told it's getting: by the song's own file, else by the address. */
    internal fun mime(t: Track, uri: String): String = CastRules.mime(t.file ?: Music.lib.items[t.id]?.file ?: uri)

    /** A cast device connected: what's playing goes there, from where it was. */
    fun started(p: Engine, name: String) {
        val at = Music.position
        val wasPlaying = !Music.paused
        Music.localPlayer?.pause()
        Music.cast = p; Music.castName = name
        Music.toast("Casting to $name")
        if (Music.current() != null) { Music.resumeAt = at; Music.loadCurrent(autoplay = wasPlaying) }
    }

    /** Back to this phone, where the cast left off. */
    fun stopped() {
        if (Music.cast == null) return
        val at = Music.position
        val wasPlaying = !Music.paused
        Music.cast = null; Music.castName = ""
        Music.toast(if (platformName == "android") "Playing on this phone" else "Playing on this PC")
        if (Music.current() != null) { Music.resumeAt = at; Music.loadCurrent(autoplay = wasPlaying) }
    }
}
