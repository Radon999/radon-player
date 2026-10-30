package org.omarchy.music

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/** The first start of the PC app on a computer that had the Python app (1.x / 2.1): everything it kept, brought
 *  over once, in the shapes this app reads. The old app's files are only read: nothing of theirs is changed,
 *  moved or deleted (its songs, covers and downloads stay where they are, and the library points at them).
 *  A file this app has already is never overwritten. Call it before Music, Prefs, LastFm and the rest read theirs. */
object PythonImport {
    private const val MARKER = "python-import.done"

    /** Bring the Python app's things over, once: what came, a short line each (for a toast); empty if nothing did. */
    fun run(ctx: Ctx): List<String> = run(ctx, File(System.getProperty("user.home")), System::getenv,
        System.getProperty("os.name").lowercase().contains("win"))

    /** The same, with the home folder, the environment and the system given (tests use a made-up home). */
    internal fun run(ctx: Ctx, home: File, env: (String) -> String?, windows: Boolean): List<String> {
        val dir = ctx.filesDir
        if (File(dir, MARKER).exists() || File(dir, "state.json").exists()) return emptyList()
        val old = Old(home, env, windows)
        if (!old.present()) return emptyList()
        val lines = try {
            Importer(ctx, old, home).all()
        } catch (e: Throwable) {
            System.err.println("radon-player: importing the Python app's data: $e")
            return emptyList()   // no marker: tried again next start (what was written stays, and isn't written twice)
        }
        runCatching { writeAtomic(File(dir, MARKER), "Imported from ${old.stateDir} and ${old.music}\n" + lines.joinToString("\n")) }
        return lines
    }

    /** Where the Python app kept what: its STATE_DIR, ~/.local/state (state, plays, skips, scrobbles: there on Windows
     *  too), its Music folder (library, playlists, favorites…) and its cache. The default profile (no SUFFIX) only. */
    private class Old(home: File, env: (String) -> String?, windows: Boolean) {
        val stateDir: File = if (windows) File(env("LOCALAPPDATA")?.takeIf { it.isNotBlank() } ?: File(home, "AppData/Local").path, "omarchy-music")
            else File(home, ".local/state")
        val local = File(home, ".local/state")
        val music = env("OMARCHY_MUSIC_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(home, "Music/Omarchy Music")
        val cache = if (windows) File(stateDir, "cache") else File(home, ".cache/omarchy-music")

        val state get() = File(local, "omarchy-music.json")
        val plays get() = File(local, "omarchy-music-plays.json")
        val skips get() = File(local, "omarchy-music-skips.json")
        val scrobbles get() = File(local, "omarchy-music-scrobbles.json")
        val tidal get() = File(stateDir, "omarchy-music-tidal.json")
        val spotify get() = File(stateDir, "omarchy-music-spotify.json")
        val lastfm get() = File(stateDir, "omarchy-music-lastfm.json")
        val spotifyHistory get() = File(stateDir, "omarchy-music-spotify-history.json")
        val deezerHistory get() = File(stateDir, "omarchy-music-deezer-history.json")
        val appleHistory get() = File(stateDir, "omarchy-music-apple-history.json")
        val newReleases get() = File(stateDir, "omarchy-music-new-releases.json")
        val qualityChecks get() = File(stateDir, "omarchy-music-quality-check.json")
        val library get() = File(music, "library.json")
        val missing get() = File(music, "missing.json")
        val playlists get() = File(music, "playlists.json")
        val folders get() = File(music, "playlist-folders.json")
        val favorites get() = File(music, "favorites.json")
        val speeds get() = File(music, "song-speeds.json")
        val gains get() = File(music, "song-volume.json")
        val appleToken get() = File(cache, "apple-token.json")
        val localIndex get() = File(cache, "local.json")
        val pictures get() = File(cache, "pictures.json")
        val wrapped get() = File(cache, "wrapped")
        val lyrics get() = File(cache, "lyrics")
        val autoEqIndex get() = File(cache, "autoeq-index.md")

        /** Whether the Python app was used here (~/.local/state alone is there on any Linux). */
        fun present() = listOf(state, plays, lastfm, tidal, spotify, library, playlists, favorites).any { it.isFile }
    }

    private class Importer(val ctx: Ctx, val old: Old, home: File) {
        val lines = ArrayList<String>()
        /** Old ids of songs in your folders ("f" + sha1 of the path) -> this app's ("f" + the path's hashCode). */
        val ids = HashMap<String, String>()
        private val homePrefix = home.path.replace('\\', '/').trimEnd('/') + "/"

        fun all(): List<String> {
            val steps: List<Pair<String, () -> Unit>> = listOf(
                "library" to ::library, "playlists" to ::playlists, "playlist folders" to ::playlistFolders,
                "favorites" to ::favorites, "plays" to ::plays, "skips" to ::skips, "speeds" to ::speeds, "volumes" to ::gains,
                "local index" to ::localIndex, "histories" to ::histories, "new releases" to ::newReleases,
                "loudness checks" to ::qualityChecks, "pictures" to ::pictures, "wrapped" to ::wrapped, "last.fm" to ::lastFm,
                "tidal" to ::tidal, "spotify" to ::spotify, "apple" to ::apple, "caches" to ::caches,
                "settings" to ::settings,   // state.json last: a start that stops half way imports again
            )
            for ((what, step) in steps) try { step() } catch (e: Exception) {
                System.err.println("radon-player: importing the Python app's $what: $e")
            }
            return lines
        }

        // ---- reading and writing ----

        fun read(f: File): JsonElement? = if (!f.isFile) null else runCatching { JSON.parseToJsonElement(f.readText()) }.getOrNull()

        /** Write name in the app's folder (or the cache, cache = true) unless it's there already. */
        fun put(name: String, e: JsonElement, cache: Boolean = false): Boolean {
            val f = File(if (cache) ctx.cacheDir else ctx.filesDir, name)
            if (f.exists()) return false
            writeAtomic(f, e.toString())
            return true
        }

        fun copy(from: File, to: File): Boolean {
            if (!from.isFile || to.exists()) return false
            to.parentFile?.mkdirs()
            from.copyTo(to)
            return true
        }

        fun n(i: Int) = "%,d".format(i)
        fun say(line: String) { lines += line }

        // ---- the values, as this app's classes want them (Python left nulls and ints where it liked) ----

        class B {
            val m = LinkedHashMap<String, JsonElement>()
            fun s(k: String, v: String?) { if (v != null) m[k] = JsonPrimitive(v) }
            fun d(k: String, v: Double?) { if (v != null && !v.isNaN() && !v.isInfinite()) m[k] = JsonPrimitive(v) }
            fun l(k: String, v: Long?) { if (v != null) m[k] = JsonPrimitive(v) }
            fun i(k: String, v: Int?) { if (v != null) m[k] = JsonPrimitive(v) }
            fun b(k: String, v: Boolean?) { if (v != null) m[k] = JsonPrimitive(v) }
            fun e(k: String, v: JsonElement?) { if (v != null) m[k] = v }
        }

        fun obj(f: B.() -> Unit) = JsonObject(B().apply(f).m)
        fun arr(items: List<JsonElement>) = JsonArray(items)

        /** A string, a number or a boolean as text; null for null, objects and lists. */
        fun text(e: JsonElement?): String? = (e as? JsonPrimitive)?.str
        fun dbl(e: JsonElement?): Double? = e.dbl?.takeIf { !it.isNaN() && !it.isInfinite() }
        fun int(e: JsonElement?): Int? = dbl(e)?.toLong()?.toInt()
        /** Python's true/false (or 1/0). */
        fun flag(e: JsonElement?): Boolean? = e.bool ?: (e as? JsonPrimitive)?.takeIf { !it.isString }?.let { dbl(it)?.let { d -> d != 0.0 } }
        fun numbers(e: JsonElement?): List<Double> = e.list.mapNotNull { dbl(it) }
        fun strings(e: JsonElement?): List<String> = e.list.mapNotNull { text(it) }
        fun strMap(e: JsonElement?): JsonObject = JsonObject((e as? JsonObject)?.mapNotNull { (k, v) -> text(v)?.let { k to JsonPrimitive(it) } }?.toMap() ?: emptyMap())

        /** A path as this app shows a folder: "Music/Album/" under your home folder, else the whole path (forward slashes). */
        fun shown(path: String): String {
            val p = path.replace('\\', '/').trimEnd('/')
            return (if (p.startsWith(homePrefix, ignoreCase = true)) p.substring(homePrefix.length) else p) + "/"
        }

        /** A song's id in this app: songs in your folders are named by their path's hashCode here. */
        fun newId(oldId: String, source: String, file: String?): String {
            if (source != "file" || file.isNullOrEmpty() || file.startsWith("http") || file.startsWith("content:")) return oldId
            val id = "f" + file.hashCode().toUInt().toString(16)
            if (id != oldId) ids[oldId] = id
            return id
        }

        /** A song (Track), cleaned up; null if it has no id. */
        fun track(e: JsonElement?): JsonObject? {
            val o = e as? JsonObject ?: return null
            val oldId = text(o["id"])?.takeIf { it.isNotEmpty() } ?: return null
            val file = text(o["file"])?.takeIf { it.isNotEmpty() }
            val source = text(o["source"])?.takeIf { it.isNotEmpty() } ?: "ytm"
            return obj {
                s("id", newId(oldId, source, file)); s("source", source)
                s("title", text(o["title"])); s("artist", text(o["artist"])?.takeIf { it.isNotEmpty() }); s("album", text(o["album"]))
                d("duration", dbl(o["duration"])); s("thumb", text(o["thumb"]))
                s("isrc", text(o["isrc"])?.takeIf { it.isNotEmpty() }); l("tidal", o["tidal"].long)
                s("apple", text(o["apple"])?.takeIf { it.isNotEmpty() }); s("spotify", text(o["spotify"])?.takeIf { it.isNotEmpty() })
                s("file", file)
                if (flag(o["queued"]) == true) b("queued", true)
                dbl(o["added"])?.takeIf { it > 0 }?.let { d("added", it) }
                int(o["plays"])?.takeIf { it > 0 }?.let { i("plays", it) }
                int(o["dj"])?.takeIf { it > 0 }?.let { i("dj", it) }
            }
        }

        fun tracks(e: JsonElement?): List<JsonObject> = e.list.mapNotNull { track(it) }
        fun idOf(t: JsonObject) = t["id"].str!!

        // ---- your songs and lists ----

        fun library() {
            val lib = read(old.library) as? JsonObject
            if (lib != null) {
                val out = LinkedHashMap<String, JsonElement>()
                for ((key, v) in lib) {
                    val o = v as? JsonObject ?: continue
                    val id = text(o["id"]) ?: key
                    val file = text(o["file"]) ?: continue
                    out[id] = obj {
                        s("id", id); s("source", text(o["source"])?.takeIf { it.isNotEmpty() } ?: "ytm")
                        s("title", text(o["title"]) ?: ""); s("artist", text(o["artist"]) ?: ""); s("album", text(o["album"]) ?: "")
                        d("duration", dbl(o["duration"])); s("thumb", text(o["thumb"]) ?: "")
                        s("isrc", text(o["isrc"])?.takeIf { it.isNotEmpty() }); s("apple", text(o["apple"])?.takeIf { it.isNotEmpty() })
                        s("spotify", text(o["spotify"])?.takeIf { it.isNotEmpty() }); s("file", file); s("art", text(o["art"]) ?: "")
                        d("added", dbl(o["added"])); s("format", text(o["format"]) ?: ""); s("quality", text(o["quality"]) ?: "")
                        l("tidal", o["tidal"].long); i("plays", int(o["plays"])); d("last", dbl(o["last"]))
                        i("tagged", int(o["tagged"])); s("genre", text(o["genre"]) ?: "")
                    }
                }
                if (out.isNotEmpty() && put("library.json", JsonObject(out))) say("Library: ${n(out.size)} downloaded songs")
            }
            val missing = read(old.missing) as? JsonObject
            if (missing != null) put("missing.json", JsonObject(missing.mapNotNull { (k, v) -> dbl(v)?.let { k to JsonPrimitive(it) } }.toMap()))
        }

        fun playlists() {
            val out = read(old.playlists).list.mapNotNull { p ->
                val id = text(p["id"]) ?: return@mapNotNull null
                obj {
                    s("id", id); s("name", text(p["name"]) ?: "Playlist")
                    d("created", dbl(p["created"]) ?: 0.0); d("updated", dbl(p["updated"]) ?: dbl(p["created"]) ?: 0.0)
                    e("tracks", arr(tracks(p["tracks"]).distinctBy { idOf(it) }))
                    (p["smart"] as? JsonObject)?.let { sm ->
                        e("smart", obj { e("moods", arr(strings(sm["moods"]).map(::JsonPrimitive))); e("genres", arr(strings(sm["genres"]).map(::JsonPrimitive))) })
                    }
                    (p["discovery"] as? JsonObject)?.let { d ->
                        val artist = text(d["artist"]); val role = text(d["role"])
                        if (artist != null && role != null) e("discovery", obj { s("artist", artist); s("role", role) })
                    }
                }
            }
            if (out.isNotEmpty() && put("playlists.json", arr(out))) say("Playlists: ${n(out.count { it["smart"] == null })}")
        }

        fun folderItem(e: JsonElement?): JsonObject? {
            val kind = text(e["kind"]) ?: return null
            val id = text(e["id"]) ?: return null
            return obj { s("kind", kind); s("source", text(e["source"]) ?: ""); s("id", id); s("title", text(e["title"]) ?: ""); s("thumb", text(e["thumb"]) ?: "") }
        }

        fun playlistFolders() {
            val out = read(old.folders).list.mapNotNull { f ->
                val id = text(f["id"]) ?: return@mapNotNull null
                obj { s("id", id); s("name", text(f["name"]) ?: "Folder"); e("playlists", arr(f["playlists"].list.mapNotNull { folderItem(it) })) }
            }
            if (out.isNotEmpty() && put("playlist-folders.json", arr(out))) say("Playlist folders: ${n(out.size)}")
        }

        fun favorites() {
            val favs = read(old.favorites) as? JsonObject ?: return
            val out = favs.mapNotNull { (k, v) -> track(v["track"])?.let { t -> k to obj { e("track", t); d("added", dbl(v["added"]) ?: 0.0) } } }.toMap()
            if (out.isNotEmpty() && put("favorites.json", JsonObject(out))) say("Favorites: ${n(out.size)}")
        }

        fun plays() {
            val plays = read(old.plays) as? JsonObject ?: return
            val out = LinkedHashMap<String, JsonObject>()
            for ((_, v) in plays) {
                val t = track(v["track"]) ?: continue
                val id = idOf(t)
                val was = out[id]
                val count = (int(v["plays"]) ?: 0) + (was?.get("plays").long?.toInt() ?: 0)
                val last = maxOf(dbl(v["last"]) ?: 0.0, was?.get("last").dbl ?: 0.0)
                val at = (was?.get("at").list.mapNotNull { it.long } + v["at"].list.mapNotNull { dbl(it)?.toLong() }).sorted().takeLast(500)
                out[id] = obj { i("plays", count); d("last", last); e("track", t); e("at", arr(at.map(::JsonPrimitive))) }
            }
            if (out.isNotEmpty() && put("plays.json", JsonObject(out))) say("Play history: ${n(out.size)} songs, ${n(out.values.sumOf { it["plays"].long ?: 0 }.toInt())} plays")
        }

        fun skips() {
            val skips = read(old.skips) as? JsonObject ?: return
            val out = skips.mapNotNull { (k, v) ->
                if (v !is JsonObject) return@mapNotNull null
                k to obj {
                    i("skips", int(v["skips"]) ?: 0); i("full", int(v["full"]) ?: 0); d("last_skip", dbl(v["last_skip"]) ?: 0.0)
                    e("track", track(v["track"]))
                    e("skips_at", arr(v["skips_at"].list.mapNotNull { dbl(it)?.toLong()?.let(::JsonPrimitive) }))
                    e("full_at", arr(v["full_at"].list.mapNotNull { dbl(it)?.toLong()?.let(::JsonPrimitive) }))
                }
            }.toMap()
            if (out.isNotEmpty() && put("skips.json", JsonObject(out))) say("Skips: ${n(out.size)} songs")
        }

        fun speeds() {
            val sp = read(old.speeds) as? JsonObject ?: return
            val out = sp.mapNotNull { (k, v) -> dbl(v["speed"])?.let { k to obj { d("speed", it); b("pitch", flag(v["pitch"]) ?: false) } } }.toMap()
            if (out.isNotEmpty() && put("song-speeds.json", JsonObject(out))) say("Song speeds: ${n(out.size)}")
        }

        fun gains() {
            val g = read(old.gains) as? JsonObject ?: return
            val out = g.mapNotNull { (k, v) -> dbl(v)?.let { k to JsonPrimitive(it) } }.toMap()
            if (out.isNotEmpty() && put("song-volume.json", JsonObject(out))) say("Song volumes: ${n(out.size)}")
        }

        /** The tags of the songs in your folders, read once: no full rescan, and each song keeps when it was added. */
        fun localIndex() {
            val files = read(old.localIndex)["files"] as? JsonObject ?: return
            val out = LinkedHashMap<String, JsonElement>()
            for ((path, v) in files) {
                val t = v["track"] as? JsonObject ?: continue
                val mtime = dbl(v["mtime"]) ?: continue
                track(t)   // its id, remembered for the lyrics kept under it
                out[path] = obj {
                    l("modified", Math.floor(mtime * 1000 + 0.001).toLong()); d("added", dbl(t["added"]) ?: mtime)
                    s("title", text(t["title"]) ?: ""); s("artist", text(t["artist"]) ?: ""); s("album", text(t["album"]) ?: "")
                    d("duration", dbl(t["duration"]) ?: 0.0); s("genre", text(t["genre"]) ?: "")
                }
            }
            if (out.isNotEmpty()) put("local.json", JsonObject(out), cache = true)
        }

        // ---- histories and what's worked out from them ----

        fun play(r: JsonElement): JsonObject? {
            val p = r.list
            val t = dbl(p.getOrNull(0))?.toLong() ?: return null
            val artist = text(p.getOrNull(1)) ?: return null
            val title = text(p.getOrNull(2)) ?: return null
            return obj { l("t", t); s("artist", artist); s("title", title); s("album", text(p.getOrNull(3)) ?: ""); l("ms", dbl(p.getOrNull(4))?.toLong() ?: 0) }
        }

        fun histories() {
            val sp = read(old.spotifyHistory)["plays"].list.mapNotNull { play(it) }
            if (sp.isNotEmpty() && put("spotify-history.json", obj { e("plays", arr(sp)); l("api_after", 0) })) say("Spotify history: ${n(sp.size)} plays")
            val dz = read(old.deezerHistory)["plays"].list.mapNotNull { play(it) }
            if (dz.isNotEmpty() && put("deezer-history.json", obj { e("plays", arr(dz)) })) say("Deezer history: ${n(dz.size)} plays")
            val rows = read(old.appleHistory)["rows"].list.mapNotNull { r ->
                val base = play(r) ?: return@mapNotNull null
                JsonObject(base + ("count" to JsonPrimitive(int(r.list.getOrNull(5)) ?: 1)))
            }
            if (rows.isNotEmpty() && put("apple-history.json", obj { e("rows", arr(rows)) }))
                say("Apple Music history: ${n(rows.sumOf { it["count"].long ?: 0 }.toInt())} plays")
        }

        fun newReleases() {
            val o = read(old.newReleases) as? JsonObject ?: return
            val found = o["found"].list.mapNotNull { r ->
                val title = text(r["title"]) ?: return@mapNotNull null
                obj { s("title", title); s("artist", text(r["artist"]) ?: ""); s("date", text(r["date"]) ?: ""); s("type", text(r["type"]) ?: "album")
                    s("cover", text(r["cover"]) ?: ""); s("link", text(r["link"]) ?: "") }
            }
            put("new-releases.json", obj {
                e("artists", arr(strings(o["artists"]).map(::JsonPrimitive)))
                e("ids", JsonObject((o["ids"] as? JsonObject)?.mapNotNull { (k, v) -> v.long?.let { k to JsonPrimitive(it) } }?.toMap() ?: emptyMap()))
                e("found", arr(found)); d("scanned", dbl(o["scanned"]) ?: 0.0); s("range", text(o["range"]) ?: "month")
            })
        }

        fun qualityChecks() {
            val o = read(old.qualityChecks) as? JsonObject ?: return
            val out = o.mapNotNull { (f, v) ->
                val m = dbl(v["mtime"]) ?: return@mapNotNull null
                val r = v["res"]
                f to obj {
                    l("mtime", Math.floor(m * 1000 + 0.001).toLong())
                    e("res", obj { i("clipped", int(r["clipped"]) ?: 0); d("peak", dbl(r["peak"])); s("fake", text(r["fake"]) ?: "") })
                }
            }.toMap()
            if (out.isNotEmpty() && put("quality-checks.json", JsonObject(out))) say("Loudness checks: ${n(out.size)} songs")
        }

        fun pictures() {
            val o = read(old.pictures) as? JsonObject ?: return
            val artists = (o["artists"] as? JsonObject)?.mapNotNull { (k, a) ->
                if (a !is JsonObject) return@mapNotNull null
                k to obj {
                    s("image", text(a["image"]) ?: ""); s("imageFrom", text(a["image_from"]) ?: ""); s("bio", text(a["bio"]) ?: "")
                    s("bioFrom", text(a["bio_from"]) ?: ""); s("bioUrl", text(a["bio_url"]) ?: ""); b("chosen", flag(a["chosen"]) ?: false)
                    d("t", dbl(a["t"]) ?: 0.0); i("v", int(a["v"]) ?: 0); b("failed", flag(a["failed"]) ?: false)
                }
            }?.toMap() ?: emptyMap()
            val covers = strMap(o["covers"])
            if ((artists.isNotEmpty() || covers.isNotEmpty()) && put("pictures.json", obj { e("artists", JsonObject(artists)); e("covers", covers) })) {
                val chosen = artists.values.count { it["chosen"].bool == true } + covers.size
                say("Artist pictures and bios: ${n(artists.size)}" + if (chosen > 0) " (${n(chosen)} you picked)" else "")
            }
        }

        /** Wrapped's kept scrobbles (a year, month or week a file), song lengths and artists' tags. */
        fun wrapped() {
            val dir = old.wrapped.takeIf { it.isDirectory } ?: return
            val period = Regex(".+-(\\d{4}|m\\d{4}-\\d{2}|w\\d{4}-\\d{2}-\\d{2})\\.json")
            var kept = 0
            for (f in dir.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }) when {
                f.name == "durations.json" -> (read(f) as? JsonObject)?.let { o ->
                    put("wrapped/durations.json", JsonObject(o.mapNotNull { (k, v) -> dbl(v)?.let { k to JsonPrimitive(it) } }.toMap()))
                }
                f.name == "tags.json" -> (read(f) as? JsonObject)?.let { o ->
                    put("wrapped/tags.json", JsonObject(o.mapValues { (_, v) ->
                        arr(v.list.mapNotNull { p -> val t = text(p[0]) ?: return@mapNotNull null
                            obj { s("first", t); i("second", int(p[1]) ?: 0) } })
                    }))
                }
                period.matches(f.name) -> {
                    val o = read(f) ?: continue
                    val rows = o["rows"].list.mapNotNull { r ->
                        val p = r.list
                        val t = dbl(p.getOrNull(0))?.toLong() ?: return@mapNotNull null
                        obj { l("t", t); s("artist", text(p.getOrNull(1)) ?: ""); s("title", text(p.getOrNull(2)) ?: "")
                            s("album", text(p.getOrNull(3)) ?: ""); s("image", text(p.getOrNull(4)) ?: "") }
                    }
                    if (put("wrapped/${f.name}", obj { e("rows", arr(rows)); l("until", dbl(o["until"])?.toLong() ?: 0) })) kept += rows.size
                }
            }
            if (kept > 0) say("Scrobbles kept for Wrapped: ${n(kept)}")
        }

        // ---- logins ----

        fun lastFm() {
            val o = read(old.lastfm) as? JsonObject
            if (o != null) {
                val key = text(o["key"]) ?: ""; val session = text(o["session"]) ?: ""; val user = text(o["user"]) ?: ""
                if (key.isNotEmpty() && put("lastfm.json", obj { s("key", key); s("secret", text(o["secret"]) ?: ""); s("session", session); s("user", user) }))
                    say(if (session.isNotEmpty()) "Last.fm: signed in as $user" else "Last.fm: your API account")
            }
            val waiting = read(old.scrobbles).list.mapNotNull { sc ->
                val artist = text(sc["artist"]) ?: return@mapNotNull null
                val title = text(sc["title"]) ?: return@mapNotNull null
                val ts = dbl(sc["timestamp"])?.toLong() ?: return@mapNotNull null
                obj { s("artist", artist); s("title", title); s("album", text(sc["album"]) ?: ""); s("albumartist", text(sc["albumartist"]) ?: "")
                    i("duration", int(sc["duration"]) ?: 0); s("mbid", text(sc["mbid"]) ?: ""); l("timestamp", ts) }
            }
            if (waiting.isNotEmpty() && put("scrobbles.json", arr(waiting))) say("Scrobbles waiting to send: ${n(waiting.size)}")
        }

        /** The claims of a JWT (Tidal's access token says whose it is and until when). */
        fun jwt(token: String): JsonElement? = runCatching {
            val part = token.split(".")[1]
            JSON.parseToJsonElement(String(java.util.Base64.getUrlDecoder().decode(part.trimEnd('='))))
        }.getOrNull()

        /** tidalapi's session file ({"access_token": {"data": …}, …}) as this app's Tidal session. */
        fun tidal() {
            val o = read(old.tidal) as? JsonObject ?: return
            fun data(k: String) = text(o[k]["data"])?.takeIf { it.isNotEmpty() }
            val access = data("access_token") ?: return
            val refresh = data("refresh_token") ?: return
            val claims = jwt(access)
            val user = claims["uid"].long ?: claims["userId"].long ?: 0
            if (put("tidal.json", obj {
                    s("access", access); s("refresh", refresh); s("type", data("token_type") ?: "Bearer")
                    d("expiry", claims["exp"].dbl ?: 0.0); s("session", data("session_id") ?: text(claims["sid"]) ?: "")
                    s("country", text(claims["cc"]) ?: text(claims["countryCode"]) ?: "US"); l("user", user)
                })) say("Tidal: signed in")
        }

        fun spotify() {
            val o = read(old.spotify) as? JsonObject ?: return
            val client = text(o["client_id"]) ?: return
            val refresh = text(o["refresh_token"]) ?: ""
            if (put("spotify.json", obj {
                    s("client_id", client); s("user", text(o["user"]) ?: ""); s("access_token", text(o["access_token"]) ?: "")
                    s("refresh_token", refresh); d("expires", dbl(o["expires"]) ?: 0.0)
                })) say(if (refresh.isNotEmpty()) "Spotify: signed in" + (text(o["user"])?.takeIf { it.isNotEmpty() }?.let { " as $it" } ?: "") else "Spotify: your app's Client ID")
        }

        fun apple() {
            val o = read(old.appleToken) as? JsonObject ?: return
            val tok = text(o["token"]) ?: return
            put("apple-token.json", obj { s("token", tok); l("exp", o["exp"].long ?: 0) })
        }

        /** AutoEq's index and the lyrics found (those you searched for by hand among them), under this app's song ids. */
        fun caches() {
            copy(old.autoEqIndex, File(ctx.cacheDir, "autoeq-index.md"))
            val dir = old.lyrics.takeIf { it.isDirectory } ?: return
            val renamed = ids.entries.associate { (o, nw) -> safe(o) to safe(nw) }
            for (f in dir.listFiles().orEmpty()) {
                if (!f.isFile || !f.name.endsWith(".json")) continue
                val stem = f.name.removeSuffix(".json")
                runCatching { copy(f, File(File(ctx.cacheDir, "lyrics"), (renamed[stem] ?: stem) + ".json")) }
            }
        }

        fun safe(id: String) = Regex("[^\\w.-]").replace(id, "_")

        // ---- the settings: state.json, and 2.0's switches (Prefs, radon-2.json) ----

        fun eqOutput(o: JsonElement?): JsonObject? {
            val count = int(o["count"]) ?: return null
            if (count !in EQ_COUNTS) return null
            val bands = numbers(o["bands"]).takeIf { it.size == count } ?: return null
            return obj {
                s("label", text(o["label"]) ?: ""); b("on", flag(o["on"]) ?: false); s("headphones", text(o["headphones"]) ?: "off")
                s("preset", text(o["preset"]) ?: "flat"); e("bands", arr(bands.map(::JsonPrimitive)))
                e("custom", arr((numbers(o["custom"]).takeIf { it.size == count } ?: List(count) { 0.0 }).map(::JsonPrimitive)))
                d("bass", dbl(o["bass"]) ?: 0.0); d("treble", dbl(o["treble"]) ?: 0.0); i("count", count); d("boost", dbl(o["boost"]) ?: 0.0)
            }
        }

        /** The EQ: the Python app's (snake_case, AutoEq profiles as [label, [[kind, Hz, dB, Q]…]]) as EqSettings. */
        fun eq(src: JsonElement?): JsonObject? {
            val o = src as? JsonObject ?: return null
            val count = int(o["count"])?.takeIf { it in EQ_COUNTS } ?: 10
            fun gains(k: String) = (numbers(o[k]).takeIf { it.size == count } ?: List(count) { 0.0 }).map { JsonPrimitive(it.coerceIn(-12.0, 12.0)) }
            val profiles = (o["profiles"] as? JsonObject)?.mapNotNull { (k, v) ->
                val p = v.list
                if (p.size != 2) return@mapNotNull null
                val fs = p[1].list.filter { it.list.size == 4 }
                k to obj {
                    s("name", text(p[0]) ?: k)
                    e("filters", arr(fs.map { f -> arr(f.list.drop(1).map { JsonPrimitive(dbl(it) ?: 0.0) }) }))
                    e("kinds", arr(fs.map { f -> JsonPrimitive(text(f.list[0]) ?: "pk") }))
                }
            }?.toMap() ?: emptyMap()
            return obj {
                b("on", flag(o["on"]) ?: false); s("headphones", text(o["headphones"]) ?: "off"); s("preset", text(o["preset"]) ?: "flat")
                e("bands", arr(gains("bands"))); e("custom", arr(gains("custom")))
                d("bass", dbl(o["bass"])?.coerceIn(-12.0, 12.0) ?: 0.0); d("treble", dbl(o["treble"])?.coerceIn(-12.0, 12.0) ?: 0.0)
                e("profiles", JsonObject(profiles)); e("devices", strMap(o["devices"])); i("count", count)
                b("perOutput", flag(o["per_output"]) ?: true)
                e("outputs", JsonObject((o["outputs"] as? JsonObject)?.mapNotNull { (k, v) -> eqOutput(v)?.let { k to it } }?.toMap() ?: emptyMap()))
                d("boost", dbl(o["boost"])?.coerceIn(0.0, 12.0) ?: 0.0)
            }
        }

        /** A pin, as this app keeps one (kind, id, title, sub, thumb, source); null for the ones it has no place for. */
        fun pin(p: JsonElement?): JsonObject? {
            fun mk(kind: String, id: String?, title: String?, sub: String = "", thumb: String? = "", source: String? = "") =
                if (id.isNullOrEmpty()) null else obj { s("kind", kind); s("id", id); s("title", title ?: id); s("sub", sub); s("thumb", thumb ?: ""); s("source", source ?: "") }
            return when (text(p["kind"])) {
                "artist" -> (text(p["name"]) ?: text(p["id"])).let { mk("artist", it, it, thumb = text(p["thumb"]), source = text(p["source"])) }
                "album" -> p["album"].let { a -> mk("album", text(a["id"]), text(p["title"]) ?: text(a["title"]), text(a["sub"]) ?: text(a["artist"]) ?: "", text(a["thumb"]), text(a["source"])) }
                "playlist" -> p["item"].let { it -> text(it["kind"])?.takeIf { k -> k == "mine" || k == "service" }
                    ?.let { k -> mk(k, text(it["id"]), text(p["title"]) ?: text(it["title"]), thumb = text(it["thumb"]), source = text(it["source"])) } }
                "listfolder" -> mk("listfolder", text(p["id"]), text(p["title"]))
                "group" -> when (text(p["view"])) {
                    "genres" -> (text(p["title"]) ?: text(p["id"])).let { mk("genre", it, it) }
                    "folders" -> text(p["id"])?.takeIf { it.isNotEmpty() }?.let { mk("folder", shown(it), text(p["title"])) }
                    else -> null   // your albums are keyed differently here: pin them again
                }
                else -> null
            }
        }

        fun dj(src: JsonElement?): JsonObject? {
            val o = src as? JsonObject ?: return null
            val sets = (o["sets"] as? JsonObject)?.mapNotNull { (k, v) ->
                val kind = text(v["kind"]) ?: return@mapNotNull null
                k to obj { s("kind", kind); s("line", text(v["line"]) ?: "") }
            }?.toMap() ?: emptyMap()
            val from = (o["from"] as? JsonObject)?.let { f ->
                val kind = text(f["kind"]) ?: return@let null
                obj {
                    s("kind", kind); s("id", text(f["id"]) ?: "")
                    s("path", text(f["path"])?.takeIf { it.isNotEmpty() }?.let { shown(it) } ?: "")
                    s("name", text(f["name"]) ?: ""); e("songs", arr(tracks(f["songs"])))
                }
            }
            return obj { b("on", flag(o["on"]) ?: false); e("sets", JsonObject(sets)); s("shown", text(o["shown"])); e("from", from) }
        }

        fun settings() {
            val st = read(old.state) as? JsonObject ?: JsonObject(emptyMap())
            val queue = tracks(st["queue"])
            val folders = strings(st["folders"]).takeIf { st["folders"] is JsonArray }
            val home = homePrefix.trimEnd('/')
            // Downloads go into a folder under Music/ here: the Python app's, if it was one, else Music/.
            val dl = text(st["download_dir"])?.takeIf { it.isNotEmpty() }
            val dlShown = shown(dl ?: folders?.firstOrNull() ?: "$home/Music")
            val downloadDir = if (dlShown.startsWith("Music/")) (if (dl != null) dlShown else "") else "Music/"
            val state = obj {
                e("queue", arr(queue)); i("index", int(st["index"])?.coerceAtMost(queue.size - 1) ?: -1)
                text(st["radio_from"])?.let { s("radio_from", ids[it] ?: it) }
                s("repeat", text(st["repeat"])); eq(st["eq"])?.let { e("eq", it) }; s("source", text(st["source"]))
                (st["sync_sources"] as? JsonObject)?.let { m -> e("sync_sources", JsonObject(m.mapNotNull { (k, v) -> flag(v)?.let { k to JsonPrimitive(it) } }.toMap())) }
                s("lyrics_mode", text(st["lyrics_mode"]))
                folders?.let { f -> e("folders", arr(f.map { JsonPrimitive(shown(it)) }.distinct())) }
                b("keep_played", flag(st["keep_played"])); s("music_sort", text(st["music_sort"])); s("search_in", text(st["search_in"]))
                dj(st["dj"])?.let { e("dj", it) }
                (st["api_keys"] as? JsonObject)?.let { e("api_keys", strMap(it)) }
                b("shuffled", flag(st["shuffled"])); s("playlist_sort", text(st["playlist_sort"])); b("browse_local", flag(st["browse_local"]))
                s("search_before_local", text(st["search_before_local"])); s("look", text(st["look"])); s("vis_preset", text(st["vis_preset"]))
                s("wrapped_style", text(st["wrapped_style"])); s("skip_order", text(st["skip_order"])); s("download_dir", downloadDir)
                s("dupe_action", text(st["dupe_action"]))
                e("queues", arr(st["queues"].list.mapNotNull { q ->
                    val id = text(q["id"]) ?: return@mapNotNull null
                    val ts = tracks(q["tracks"]).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    obj { s("id", id); s("name", text(q["name"]) ?: "Songs"); e("tracks", arr(ts)); i("index", int(q["index"]) ?: 0)
                        d("position", dbl(q["position"]) ?: 0.0); d("time", dbl(q["time"]) ?: 0.0) }
                }))
                s("queue_name", text(st["queue_name"]) ?: ""); s("queue_id", text(st["queue_id"]) ?: "")
                b("multi_queue", flag(st["multi_queue"])); b("styled", true); b("lyrics_to_files", flag(st["lyrics_to_files"]) ?: false)
                b("apple_ui", flag(st["apple_ui"])); b("autoplay", flag(st["autoplay"])); s("np_fallback", text(st["np_fallback"]))
                (st["drawer_folds"] as? JsonObject)?.let { m -> e("drawer_folds", JsonObject(m.mapNotNull { (k, v) -> flag(v)?.let { k to JsonPrimitive(it) } }.toMap())) }
                e("pins", arr(st["pins"].list.mapNotNull { pin(it) }.distinctBy { it["kind"].str + ":" + it["source"].str + ":" + it["id"].str }))
                (st["layouts"] as? JsonObject)?.let { e("layouts", strMap(it)) }
                s("skip_period", text(st["skip_period"])); b("bit_perfect", flag(st["bitperfect"])); s("replay_gain", text(st["replaygain"]))
                b("dj_new", flag(st["dj_new"])); b("top_mine_only", flag(st["top_mine_only"])); s("top_period", text(st["top_period"]))
                s("top_kind", text(st["top_kind"])); i("collage_size", int(st["collage_size"]))
                (st["artist_picks"] as? JsonObject)?.let { e("artist_picks", strMap(it)) }
                e("similar_kept", arr(strings(st["similar_kept"]).map(::JsonPrimitive)))
                (st["album_picks"] as? JsonObject)?.let { e("album_picks", strMap(it)) }
                // A duplicate song's pick: {"title", "artist"} there, "title\u0000artist" here ("": left alone).
                (st["song_picks"] as? JsonObject)?.let { m -> e("song_picks", JsonObject(m.mapNotNull { (k, v) ->
                    (if (v is JsonObject) "${text(v["title"]) ?: ""}\u0000${text(v["artist"]) ?: ""}" else text(v))?.let { k to JsonPrimitive(it) } }.toMap())) }
                e("singles_kept", arr(strings(st["singles_kept"]).map(::JsonPrimitive)))
            }
            prefs(st)
            remoteTokens(st)
            if (put("state.json", state)) {
                say(listOfNotNull("Settings", if (st["eq"] is JsonObject) "equalizer" else null,
                    if (queue.isNotEmpty()) "your queue (${n(queue.size)} songs)" else null,
                    if (state["pins"].list.isNotEmpty()) "pins" else null).joinToString(", "))
            }
        }

        /** 2.0's switches (opts), the volume and the few settings this app keeps as switches, into radon-2.json:
         *  added to what's there, never over it. */
        fun prefs(st: JsonObject) {
            val f = File(ctx.filesDir, "radon-2.json")
            val have = (runCatching { JSON.parseToJsonElement(f.readText()) }.getOrNull() as? JsonObject) ?: JsonObject(emptyMap())
            val add = LinkedHashMap<String, JsonElement>()
            val opts = st["opts"] as? JsonObject ?: JsonObject(emptyMap())
            for ((k, v) in opts) when (k) {
                in BOOL_OPTS -> flag(v)?.let { add[k] = JsonPrimitive(it) }
                in NUM_OPTS -> dbl(v)?.let { add[k] = JsonPrimitive(it.toFloat()) }
            }
            dbl(st["volume"])?.let { add["pc_volume"] = JsonPrimitive((it / 100).coerceIn(0.0, 1.0).toFloat()) }
            flag(st["close_after"])?.let { add["close_after"] = JsonPrimitive(it) }
            add["discord"] = JsonPrimitive(flag(st["discord"]) ?: true)   // on unless you turned it off there
            flag(st["remote"])?.let { add["phone_remote"] = JsonPrimitive(it) }
            strings(st["fixup_skip"]).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
                ?.let { add["fixup_skip"] = JsonPrimitive(it.joinToString("\n") { p -> p.replace('\\', '/') }) }
            strings(st["live_kept"]).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
                ?.let { add["live_kept"] = JsonPrimitive(it.joinToString("\n") { p -> p.replace('\\', '/') }) }
            val merged = LinkedHashMap<String, JsonElement>(have)
            for ((k, v) in add) if (k !in merged) merged[k] = v
            if (merged.size != have.size) writeAtomic(f, JsonObject(merged).toString())
            val on = opts.count { (k, v) -> k in BOOL_OPTS && flag(v) == true }
            if (on > 0) say("Playback switches: ${n(on)} on")
        }

        /** The phones paired as remotes: {token: {"name", "added"}} there, {token: name} here. */
        fun remoteTokens(st: JsonObject) {
            val toks = (st["remote_tokens"] as? JsonObject)?.mapNotNull { (tok, v) ->
                (text(v["name"]) ?: "Phone").let { tok to JsonPrimitive(it) } }?.toMap() ?: return
            if (toks.isNotEmpty() && put("remote-tokens.json", JsonObject(toks))) say("Paired phones: ${n(toks.size)}")
        }
    }

    private val EQ_COUNTS = setOf(3, 5, 10, 15, 31)
    private val BOOL_OPTS = setOf("crossfade", "smart_transitions", "crossfeed", "continue_across", "smart_shuffle", "loudness_check",
        "mono", "clip_guard", "loudness_comp", "hq_resample", "detail", "dither", "auto_mono")
    private val NUM_OPTS = setOf("crossfade_secs", "balance", "crossfeed_k", "tilt", "rg_target")
}
