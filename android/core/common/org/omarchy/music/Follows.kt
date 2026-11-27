package org.omarchy.music

import kotlinx.coroutines.launch

/** Followed artists ([FollowRules]): whose new releases New releases looks for. Kept with the settings (so backups and
 *  Sync carry them to your other device too). Read in Compose: screens change as soon as someone is followed. */
object Follows {
    private val R = FollowRules

    fun list(): List<String> = R.parse(Prefs.text(R.LIST))
    private fun seen(): Set<String> = R.parse(Prefs.text(R.SEEN)).toSet()
    private fun put(p: Pair<List<String>, Set<String>>) {
        if (R.write(p.first) != Prefs.text(R.LIST)) Prefs.setText(R.LIST, R.write(p.first))
        val s = R.write(p.second.sorted())
        if (s != Prefs.text(R.SEEN)) Prefs.setText(R.SEEN, s)
    }

    fun isFollowed(name: String): Boolean = R.isFollowed(list(), name)

    /** Follow artists by themselves (Settings › General): on at first. */
    fun autoOn(): Boolean = Prefs.on(R.AUTO, true)
    fun setAuto(on: Boolean) { Prefs.set(R.AUTO, on); if (on) Music.bg({ refresh() }) { _, _ -> } }

    fun follow(names: List<String>, quiet: Boolean = false) {
        put(R.follow(list(), seen(), names))
        if (!quiet) Music.toast(if (names.size == 1) "Following ${names[0]}: their new releases show in New releases" else "Following ${names.size} artists")
    }

    fun unfollow(names: List<String>) {
        put(R.unfollow(list(), seen(), names))
        NewReleases.dropArtists(names)
        Music.toast(if (names.size == 1) "Unfollowed ${names[0]}: their new releases won't show" else "Unfollowed ${names.size} artists")
    }

    /** (blocking) The artists of your songs on this device and of the playlists you keep in Radon Player (not a
     *  streaming service's own, not the app's discovery or smart ones): the lead artist of each song. */
    fun libraryArtists(): List<String> {
        val songs = runCatching { Music.mySongs() }.getOrDefault(emptyList()) +
            runCatching { Music.lists.all().filter { it.smart == null && it.discovery == null }.flatMap { it.tracks } }.getOrDefault(emptyList())
        return R.library(songs.mapNotNull { splitArtists(it.artist).firstOrNull() })
    }

    /** (the main thread, blocking) The same, at once: before New releases looks, so it looks for whoever's new too. */
    fun refreshNow() { if (autoOn()) put(R.auto(list(), seen(), libraryArtists(), true)) }

    /** (blocking) Following by themselves brought up to date (with it on: anyone new in your songs or playlists). */
    fun refresh() {
        if (!autoOn()) return
        val lib = libraryArtists()
        Music.scope.launch(kotlinx.coroutines.Dispatchers.Main) { put(R.auto(list(), seen(), lib, true)) }
    }
}
