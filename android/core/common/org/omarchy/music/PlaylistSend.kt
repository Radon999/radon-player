package org.omarchy.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicBoolean

/** Send a playlist to your other device ([PlaylistSendRules]): a copy made there once, not a sync. From the phone it
 *  goes straight to the PC. The PC can't call the phone, so from the PC it waits (while the app is open) until the
 *  phone next asks, within 20 seconds when its app is open on the same network. A normal playlist arrives as a
 *  playlist of that name (one of that name there gets the songs it hasn't); Listen later's songs go on the other's
 *  Listen later; a discovery playlist's into the other's discovery playlist for that artist. Nothing there is ever
 *  taken out or replaced. */
object PlaylistSend {
    @Serializable
    data class Parcel(val name: String = "", val kind: String = "list", val artist: String = "", val tracks: List<Track> = emptyList())

    @Serializable
    data class Result(val name: String = "", val added: Int = 0, val had: Int = 0, val missing: List<String> = emptyList())

    private const val LAST = "playlist_send_last"       // what the last one sent or received said
    private const val MISSING = "playlist_send_missing" // and every song it left out, a line each
    private var version by mutableStateOf(0)
    /** What the last playlist sent or received said ("": none yet), and the songs it left out. */
    val last: String get() = version.let { Prefs.text(LAST) }
    val lastMissing: String get() = version.let { Prefs.text(MISSING) }

    private val pc get() = RemoteHost.supported
    private val other get() = if (pc) "the phone" else Remote.name.ifEmpty { "the PC" }
    private val here get() = if (pc) "this PC" else "this phone"

    /** Whether there is another device to send to (paired, and on the PC its server on). */
    val canSend: Boolean get() = if (pc) RemoteHost.running && RemoteHost.phones.isNotEmpty() else Remote.paired

    private fun parcel(pl: Playlist): Parcel {
        val kind = when {
            pl.discovery?.role == "discover" -> "discover"
            pl.discovery != null -> "discovered"
            pl.id == ListenLater.playlist()?.id -> "later"
            else -> "list"
        }
        // A song that streams (or was downloaded from a service) goes by its address there; one of your files by its
        // name, to be found among the other device's own songs
        val songs = (if (pl.smart != null) SmartLists.songs(pl) ?: pl.tracks else pl.tracks).map { t ->
            if (t.source != "file" && !t.isFile) t.slim() else Track("", "file", t.title, t.artist, t.album, t.duration)
        }
        return Parcel(pl.name, kind, pl.discovery?.artist ?: "", songs)
    }

    private fun note(text: String, missing: List<String>) {
        Prefs.setText(LAST, text); Prefs.setText(MISSING, PlaylistSendRules.missingList(missing))
        version++
        Music.toast(text)
    }

    /** Send this playlist to the other device. */
    fun send(pl: Playlist) {
        if (!canSend) { Music.toast(if (pc) "Pair a phone first (Settings › Remote & sync)" else "Pair with the PC first (Settings › Remote & sync)"); return }
        val p = parcel(pl)
        if (p.tracks.isEmpty()) { Music.toast("“${pl.name}” has no songs to send"); return }
        if (pc) {
            synchronized(outbox) { outbox += p }
            Music.toast("Sending “${pl.name}” to the phone. This way round can take a little while: up to about 20 seconds, with Radon Player open on the phone, on the same network. You'll be told here when it's there")
            return
        }
        Music.toast("Sending “${pl.name}” to $other: from the phone it's there at once…")
        val host = Remote.host; val headers = Remote.headers()
        Music.bg({
            val r = httpJson("http://$host/playlist/send", headers, jsonBody(JSON.encodeToJsonElement(Parcel.serializer(), p)))
            JSON.decodeFromJsonElement(Result.serializer(), r)
        }) { r, err ->
            if (r == null) Music.toast("Couldn't send “${pl.name}”: " + (if (err is HttpError && err.code == 404) "the PC's app is older and can't take a playlist yet" else "the PC didn't answer. Is Radon Player open on it, on the same network?"))
            else note(PlaylistSendRules.said(true, r.name.ifEmpty { pl.name }, other, other, r.added, r.had, r.missing), r.missing)
        }
    }

    /** (blocking) A playlist that came from the other device, put with this device's: the songs this one can play are
     *  added (its own copy of each where it has one), the ones it can't are named. */
    private fun take(p: Parcel): Result {
        val index = runCatching { Music.songIndex() }.getOrDefault(emptyMap())
        val ok = ArrayList<Track>(); val missing = ArrayList<String>()
        for (t in p.tracks) {
            if (t.source == SoundCloud.KEY) { missing += PlaylistSendRules.songName(t.title, t.artist); continue }
            if (t.id.isNotEmpty() && t.source != "file") { ok += runCatching { Music.mineOf(t) }.getOrDefault(t); continue }
            val f = runCatching { Music.fmSong(t.title, t.artist, "", index, t.album) }.getOrNull()
            if (f != null && (f.isFile || Music.lib.has(f.id))) ok += f else missing += PlaylistSendRules.songName(t.title, t.artist)
        }
        val into: Playlist? = when (p.kind) {
            "later" -> ListenLater.ensure()
            "discover" -> Discoveries.keep(p.artist, emptyList()).first
            "discovered" -> { Discoveries.keep(p.artist, emptyList())
                Discoveries.pairs().firstOrNull { norm(it.first) == norm(p.artist) }?.third }
            // (a copy, not a sync: made as this device's own, whatever New playlists sync says)
            else -> Music.lists.named(p.name) ?: Music.lists.create(p.name.ifBlank { "Playlist from $other" }, synced = false)
        }
        val new = if (into == null) 0 else Music.lists.add(into.id, ok)
        return Result(into?.name ?: p.name, new, ok.size - new, missing)
    }

    private fun taken(r: Result) = Music.scope.launch {
        Music.musicVersion++
        note(PlaylistSendRules.said(false, r.name, other, here, r.added, r.had, r.missing), r.missing)
    }

    // ---- the PC's side (the phone remote's server) ----

    private val outbox = ArrayList<Parcel>()

    /** For GET /sync/wanted: how many playlists wait for the phone. */
    fun waiting(): Int = synchronized(outbox) { outbox.size }

    /** For GET /playlist/outbox: the playlists waiting for the phone, handed over. */
    fun handOver(): JsonObject {
        val all = synchronized(outbox) { outbox.toList().also { outbox.clear() } }
        return buildJsonObject { put("parcels", JsonArray(all.map { JSON.encodeToJsonElement(Parcel.serializer(), it) })) }
    }

    /** For POST /playlist/send (the server's thread): a playlist from the phone, put with this PC's; back, what happened. */
    fun received(body: JsonObject): JsonObject {
        val r = take(JSON.decodeFromJsonElement(Parcel.serializer(), body))
        taken(r)
        return JSON.encodeToJsonElement(Result.serializer(), r) as JsonObject
    }

    /** For POST /playlist/taken: what the phone did with a playlist this PC sent it. */
    fun told(body: JsonObject) {
        val r = runCatching { JSON.decodeFromJsonElement(Result.serializer(), body) }.getOrNull() ?: return
        Music.scope.launch { note(PlaylistSendRules.said(true, r.name, other, other, r.added, r.had, r.missing), r.missing) }
    }

    // ---- the phone's side ----

    private val fetching = AtomicBoolean(false)

    /** (the phone) The PC has playlists waiting for this phone ([Sync.tick] saw so): they are taken, and the PC told
     *  what became of each. */
    fun fetch() {
        if (!Remote.paired || !fetching.compareAndSet(false, true)) return
        val host = Remote.host; val headers = Remote.headers()
        Music.bg({
            val got = (httpJson("http://$host/playlist/outbox", headers) as? JsonObject)?.get("parcels") as? JsonArray
            got.orEmpty().mapNotNull { e -> runCatching { JSON.decodeFromJsonElement(Parcel.serializer(), e) }.getOrNull() }.map { p ->
                take(p).also { r -> runCatching { httpJson("http://$host/playlist/taken", headers, jsonBody(JSON.encodeToJsonElement(Result.serializer(), r))) } }
            }
        }) { rs, _ ->
            fetching.set(false)
            rs?.forEach { taken(it) }
        }
    }
}
