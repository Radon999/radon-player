package org.omarchy.music

import kotlinx.coroutines.launch

/** The streaming services as the app uses them: which one you browse (or your own music), a service coming ready
 *  or signing out, and keeping what you have on the services you sync downloaded. What the screens read (the
 *  service picked, who is signed in, the sync's progress) stays in [Music], which keeps the names and hands on. */
object Services {
    private fun toast(text: String) = Music.toast(text)

    /** What you browse: your own music (local) or a service (source), where the search looks, and where it looked
     *  before you went to your own music (to go back to). */
    data class Browsing(val local: Boolean, val source: String, val searchIn: String, val before: String)

    /** The source bar picked key ("local", or a service's): how browsing changes, or null when nothing does (the one
     *  you are on already, or a key that is no service). Going to your own music makes the search look there, and
     *  remembers where it looked; going to a service from there puts the search back. */
    fun browseTo(now: Browsing, key: String, services: Set<String> = SOURCES.keys): Browsing? = when {
        key == "local" -> if (now.local) null
            else if (now.searchIn != "local") now.copy(local = true, before = now.searchIn, searchIn = "local") else now.copy(local = true)
        key in services -> if (!now.local && key == now.source) null
            else now.copy(local = false, source = key, searchIn = if (now.local && now.searchIn == "local") now.before else now.searchIn)
        else -> null
    }

    /** The source bar: a service (its library in the Library tab, its search) or "local": your own songs, which the
     *  search looks in, with the Library tab hidden until you pick a service. */
    fun browse(key: String) {
        val to = browseTo(Browsing(Music.browseLocal, Music.source, Music.searchIn, Music.searchBeforeLocal), key) ?: return
        Music.browseLocal = to.local; Music.source = to.source; Music.searchIn = to.searchIn; Music.searchBeforeLocal = to.before
        Music.saveState()
    }

    fun setSync(key: String, on: Boolean) {
        Music.syncSources[key] = on
        Music.saveState()
        if (on) syncLibrary()
    }

    /** A service is ready (src), or couldn't be set up (err): its likes are read, and what it changes goes on. */
    fun ready(key: String, src: Source?, err: Throwable?) {
        val likes = Music.likes
        if (src != null) {
            Music.sources[key] = src
            Music.sourceErrors.remove(key)
            if (src.signedIn) Music.bg({ src.likedIds() }) { ids, _ -> if (ids != null) { likes[key] = ids; Music.liked = likes.values.flatten().toSet() } }
            else if (likes.remove(key) != null) Music.liked = likes.values.flatten().toSet()
        } else if (key !in Music.sources) Music.sourceErrors[key] = err?.message ?: "unavailable"
        Music.accountsVersion++
        if (key == "tidal") Music.playerReady()
        if (key == "spotify" && src?.signedIn == true) Music.bg({ SpotifyHistory.sync() })   // its last plays, for your Wrapped
        syncLibrary()
    }

    fun tidalLoggedIn(redirect: String) {
        Music.bg({ Music.tidal.finishLogin(redirect) }) { _, err ->
            if (err != null) { toast("Tidal didn't accept that: ${err.message?.take(80)}"); return@bg }
            toast("Tidal connected: everything plays in FLAC")
            ready("tidal", Music.tidal, null)
        }
    }

    fun tidalDisconnect() {
        Music.tidal.disconnect()
        Music.sources.remove("tidal")
        Music.accountsVersion++
    }

    /** (main thread only) A sync was asked for while one ran: another follows it. */
    private var again = false

    /** Download every song in your playlists and favourites, on each service you sync, in FLAC. */
    fun syncLibrary() {
        if (!Music.tidal.connected) return
        val srcs = Music.sources.filter { (k, s) -> s.signedIn && Music.syncSources[k] == true && k != SoundCloud.KEY }.values.toList()   // SoundCloud's are never saved
        if (srcs.isEmpty()) return
        if (Music.syncing) { again = true; return }
        Music.syncing = true
        again = false
        Music.bg({
            val tracks = ArrayList<Track>()
            val seen = HashSet<String>()
            for (s in srcs) {
                val found = runCatching { s.libraryTracks() }.getOrNull() ?: continue
                for (t in found) if (seen.add(t.id)) tracks += t
                Music.scope.launch { Music.syncTracks = tracks.toList() }
            }
            Music.dl.sync(tracks.filter { Music.autoSaveOk(it) }) { Music.scope.launch { Music.savedVersion++ } }   // songs you have: not unless Copies says so
        }) { _, err ->
            Music.syncing = false
            if (err != null) toast("FLAC sync stopped: ${err.message?.take(80)}")
            Music.savedVersion++
            Music.musicVersion++
            if (again) syncLibrary()
        }
    }
}
