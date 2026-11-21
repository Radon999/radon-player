package org.omarchy.music

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** This app's own play history, out to a file and in from one ([PlayExportRules]): the plays the app counted itself
 *  (what your stats fall back on when Last.fm isn't connected, and Recently played), the songs you skipped or heard
 *  out, and the listening diary (where your music came from, which devices, your playlists). Not your imported
 *  histories (import those where you want them), your playlists or your settings: Back up and restore has those. */
object PlayExport {
    @Serializable
    private data class Song(val title: String, val artist: String, val album: String = "", val duration: Double = 0.0,
                            val plays: Int = 0, val last: Double = 0.0, val at: List<Long> = emptyList())
    @Serializable
    private data class Skips(val title: String, val artist: String, val album: String = "", val skips: Int = 0, val full: Int = 0,
                             val last_skip: Double = 0.0, val skips_at: List<Long> = emptyList(), val full_at: List<Long> = emptyList())
    @Serializable
    private data class Whole(val radon_player_history: Int = 0, val made: Long = 0, val from: String = "", val version: String = "",
                             val plays: List<Song> = emptyList(), val skips: List<Skips> = emptyList(), val diary: String = "")

    /** (blocking) Write it to uri; how many songs' plays went in. */
    fun export(ctx: Ctx, uri: String): Int {
        val plays = Music.plays.items.values.toList().filter { it.plays > 0 }.map { e ->
            Song(e.track.title, e.track.artist, e.track.album, e.track.duration, e.plays, e.last, e.at) }
        val skips = Music.songStats.kept().mapNotNull { e -> e.track?.let { t ->
            Skips(t.title, t.artist, t.album, e.skips, e.full, e.last_skip, e.skips_at, e.full_at) } }
        val whole = Whole(PlayExportRules.VERSION, now().toLong(), platformName, runCatching { appVersion }.getOrDefault(""),
            plays, skips, runCatching { ListenLog.syncText() }.getOrDefault(""))
        (ctx.openOut(uri, "w") ?: error("couldn't write there")).use { it.write(JSON.encodeToString(Whole.serializer(), whole).toByteArray()) }
        return plays.size
    }

    /** (blocking) Bring the file at uri in; what it added, in words. */
    fun import(ctx: Ctx, uri: String): String {
        val text = (ctx.openIn(uri) ?: error("couldn't open that file")).use { it.readBytes().toString(Charsets.UTF_8) }
        val mark = runCatching { (JSON.parseToJsonElement(text) as? JsonObject)?.get(PlayExportRules.MARK) }.getOrNull()
        if (mark == null) error("that isn't a Radon Player play history (make one with Export on the other device)")
        val w = JSON.decodeFromString(Whole.serializer(), text)
        // A song from another device isn't a file of this one: kept by its name, and shown and played as your own copy
        // of it wherever you have one (as a song from any history is)
        fun track(title: String, artist: String, album: String, duration: Double) =
            Track("imp:" + songKey(Track("", title = title, artist = artist)).let { "${it.first}|${it.second}" }, "lastfm", title, artist, album = album, duration = duration)
        val (songs, plays) = Music.plays.mergeIn(w.plays.filter { it.title.isNotBlank() }.map { s ->
            PlayEntry(s.plays, s.last, track(s.title, s.artist, s.album, s.duration), s.at) })
        val skips = Music.songStats.mergeIn(w.skips.filter { it.title.isNotBlank() }.map { s ->
            SkipEntry(s.skips, s.full, s.last_skip, track(s.title, s.artist, s.album, 0.0), s.skips_at, s.full_at) })
        val before = runCatching { ListenLog.all().size }.getOrDefault(0)
        if (w.diary.isNotBlank()) runCatching { ListenLog.mergeIn(w.diary) }
        val diary = (runCatching { ListenLog.all().size }.getOrDefault(before) - before).coerceAtLeast(0)
        return PlayExportRules.done(songs, plays, skips, diary)
    }
}
