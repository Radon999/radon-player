package org.omarchy.music

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File

/** What you've told the app (More like this, Less like this, Not for autoplay) and what it saw you do (skipping a song
 *  in its first seconds, how the new songs the DJ and autoplay brought went), by song and by artist: taste.json, which
 *  syncs. Autoplay, radios and the DJ go by it ([AutoplayRules], [SignalRules]); Stats › Taste shows it. */
object Signals {
    private val ser = MapSerializer(String.serializer(), SignalEntry.serializer())
    private var items: MutableMap<String, SignalEntry>? = null
    private val file get() = File(Music.ctx.filesDir, FILE)
    const val FILE = "taste.json"

    @Synchronized private fun map(): MutableMap<String, SignalEntry> = items ?: run {
        val m = runCatching { readText(file)?.let { JSON.decodeFromString(ser, it) } }.getOrNull()?.toMutableMap() ?: HashMap()
        items = m; m
    }
    @Synchronized private fun save() = runCatching { writeAtomic(file, JSON.encodeToString(ser, map())) }
    /** Read again (a sync brought the other device's). */
    @Synchronized fun reload() { items = null; Music.musicVersion++ }

    @Synchronized fun of(t: Track): SignalEntry? = map()[SignalRules.songKey(t.title, t.artist)]
    @Synchronized fun ofArtist(artist: String): SignalEntry? = map()[SignalRules.artistKey(lead(artist))]
    @Synchronized fun all(): Map<String, SignalEntry> = map().toMap()

    private fun lead(artist: String) = artist.split(",")[0].trim()

    @Synchronized private fun change(key: String, title: String, artist: String, f: (SignalEntry) -> SignalEntry) {
        val m = map()
        val e = f(m[key] ?: SignalEntry(title, artist))
        if (e == SignalEntry(e.title, e.artist)) m.remove(key) else m[key] = e
        save()
    }

    // ---- what you say ----
    /** More like this (on: true), Less like this (false), or neither again (null). */
    fun thumb(t: Track, on: Boolean?) {
        val at = now().toLong()
        change(SignalRules.songKey(t.title, t.artist), t.title, t.artist) {
            when (on) { true -> it.copy(up = at, down = 0); false -> it.copy(down = at, up = 0); null -> it.copy(up = 0, down = 0) }
        }
        Music.musicVersion++
        Music.toast(when (on) { true -> "More like “${t.title}” from now on"; false -> "Less like “${t.title}” from now on: it won't be put in again"; null -> "Back to normal for “${t.title}”" })
    }

    fun artistThumb(artist: String, on: Boolean?) {
        val a = lead(artist); val at = now().toLong()
        change(SignalRules.artistKey(a), "", a) {
            when (on) { true -> it.copy(up = at, down = 0); false -> it.copy(down = at, up = 0); null -> it.copy(up = 0, down = 0) }
        }
        Music.musicVersion++
        Music.toast(when (on) { true -> "More of $a from now on"; false -> "Less of $a from now on"; null -> "Back to normal for $a" })
    }

    /** Not for autoplay: never put in by autoplay, radios or the DJ (you can still play it yourself). */
    fun noAuto(t: Track, on: Boolean) {
        change(SignalRules.songKey(t.title, t.artist), t.title, t.artist) { it.copy(noAuto = if (on) now().toLong() else 0) }
        Music.musicVersion++
        Music.toast(if (on) "“${t.title}” won't be put in by autoplay, radios or the DJ" else "“${t.title}” can be put in by autoplay again")
    }

    fun artistNoAuto(artist: String, on: Boolean) {
        val a = lead(artist)
        change(SignalRules.artistKey(a), "", a) { it.copy(noAuto = if (on) now().toLong() else 0) }
        Music.musicVersion++
        Music.toast(if (on) "$a won't be put in by autoplay, radios or the DJ" else "$a can be put in by autoplay again")
    }

    // ---- what it sees ----
    /** You pressed Next this many seconds into it: in its first seconds, it's written down. */
    fun leftAt(t: Track, secs: Double) {
        if (secs >= SignalRules.EARLY_SECS) return
        change(SignalRules.songKey(t.title, t.artist), t.title, t.artist) { SignalRules.withEarly(it, now().toLong()) }
    }

    /** A song started: one the DJ or autoplay brought that's new to you (not yours, never played) is an introduction,
     *  and how it goes is written down. kind: ListenLog's (dj, radio…). */
    fun started(t: Track, kind: String) {
        if (kind != "dj" && kind != "radio") return
        if (t.isFile || Music.lib.has(t.id)) return
        val k = Stats.statsKey(t.title, t.artist)
        val played = TasteService.current?.facts?.count?.get(k) ?: 0
        if (played > 0) return
        val key = SignalRules.songKey(t.title, t.artist)
        if (map()[key]?.introAt?.let { it > 0 } == true) return
        change(key, t.title, t.artist) { it.copy(introAt = now().toLong(), introVia = kind) }
    }

    private fun outcome(t: Track, what: String) {
        val key = SignalRules.songKey(t.title, t.artist)
        val e = synchronized(this) { map()[key] } ?: return
        if (e.introAt <= 0) return
        change(key, t.title, t.artist) { it.copy(introOut = SignalRules.outcome(it.introOut, what)) }
    }
    fun heardOut(t: Track) = outcome(t, "heard")
    fun skipped(t: Track) = outcome(t, "skipped")
    /** Saved, favourited or put in a playlist. */
    fun kept(ts: List<Track>) { for (t in ts) outcome(t, "kept") }

    /** How well the new songs from each artist went (lead artist key → 0 … 1). */
    fun introSuccess(): Map<String, Double> = all().values.filter { it.introAt > 0 }.groupBy { statsKey("", it.artist).second }
        .mapNotNull { (a, es) -> SignalRules.introSuccess(es.map { it.introOut })?.let { a to it } }.toMap()

    // ---- for autoplay and radios ----
    /** The songs offered by a service, in an order that fits you ([AutoplayRules]); seed: the song they're like. Your
     *  habits add songs of yours you often play straight after the seed. */
    fun rank(seed: Track?, offered: List<Track>, habitsToo: Boolean = true): List<Track> = runCatching {
        val taste = TasteService.current
        val habits = taste?.habits
        val nowS = now().toLong()
        val seedKey = seed?.let { SignalRules.songKey(it.title, it.artist) }
        val seedLead = seed?.let { statsKey("", it.artist).second } ?: ""
        val near = habits?.near?.get(seedLead).orEmpty().toMap()
        val after = seedKey?.let { habits?.after?.get(it) }.orEmpty().toMap()
        // Songs of yours you play right after this one: in the running too
        val extra = if (!habitsToo || taste == null) emptyList() else after.keys.mapNotNull { k ->
            val (title, artist) = k.split("\t").let { if (it.size == 2) it[0] to it[1] else return@mapNotNull null }
            taste.mine[title to artist]
        }.take(5)
        val all = (offered + extra).distinctBy { SignalRules.songKey(it.title, it.artist) }
        val sigs = all()
        val seedSound = seed?.let { soundOf(it) }
        val seedMood = seed?.let { runCatching { Moods.of(Music.localOf(it)) }.getOrNull() }
        val hourSlot = (java.time.LocalTime.now().hour / 4).coerceIn(0, 5)
        val bad = introSuccess().filterValues { it < 0.25 }.keys
        val places = placeShares()
        val outKind = Music.outputNow.kind
        val seedGenre = (seed?.let { runCatching { Music.genreOf(Music.localOf(it)) }.getOrDefault("") } ?: "").takeIf { it != ReleaseTabRules.UNKNOWN_GENRE } ?: ""
        val cands = all.mapIndexed { i, t ->
            val key = SignalRules.songKey(t.title, t.artist)
            val lead = statsKey("", t.artist).second
            val s = sigs[key]; val a = sigs[SignalRules.artistKey(t.artist.split(",")[0].trim())]
            val (skips, full) = runCatching { Music.songStats.get(t) }.getOrDefault(0 to 0)
            val local = Music.localOf(t)
            val snd = soundOf(local)
            AutoplayRules.Cand(key, lead, if (i < offered.size) i else offered.size / 2,
                own = local.isFile, skips = skips, full = full, early = SignalRules.earlyLately(s, nowS),
                thumb = SignalRules.thumb(s), blocked = (s?.noAuto ?: 0) > 0,
                lastPlayed = taste?.last?.get(Stats.statsKey(t.title, t.artist)) ?: 0L,
                artistPlays = taste?.facts?.artistAll?.entries?.firstOrNull { statsKey("", it.key).second == lead }?.value ?: 0,
                near = near[lead] ?: 0.0, after = after[key] ?: 0,
                sound = if (seedSound != null && snd != null) AutoplayRules.soundClose(seedSound.bpm, seedSound.loud, snd.bpm, snd.loud) else null,
                sameMood = seedMood != null && runCatching { Moods.of(local) }.getOrNull() == seedMood,
                hourFit = habits?.hours?.get(lead)?.get(hourSlot) ?: 0.0,
                artistDown = a != null && (a.noAuto > 0 || SignalRules.thumb(a) < 0), artistUp = SignalRules.thumb(a) > 0,
                introFail = lead in bad,
                placeFit = places[lead]?.get(outKind) ?: 0.0,
                sameGenre = seedGenre.isNotEmpty() && runCatching { Music.genreOf(local) }.getOrDefault("").equals(seedGenre, ignoreCase = true))
        }
        val byKey = all.associateBy { SignalRules.songKey(it.title, it.artist) }
        val ranked = AutoplayRules.rank(cands, nowS, seedLead).mapNotNull { byKey[it.key] }
        // Never nothing at all: when everything was left out, the service's own list (minus what you said no to)
        ranked.ifEmpty { offered.filter { (sigs[SignalRules.songKey(it.title, it.artist)]?.noAuto ?: 0) == 0L && SignalRules.thumb(sigs[SignalRules.songKey(it.title, it.artist)]) >= 0 } }
    }.getOrElse { offered }

    /** Whether the DJ may put a song in: not one you said no to, nor one you keep skipping in its first seconds. */
    fun djMay(t: Track): Boolean {
        val s = of(t); val a = ofArtist(t.artist)
        if ((s?.noAuto ?: 0) > 0 || SignalRules.thumb(s) < 0) return false
        if (a != null && (a.noAuto > 0 || SignalRules.thumb(a) < 0)) return false
        return SignalRules.earlyLately(s, now().toLong()) < 2
    }

    /** Where you play each artist (lead key → output kind → share of their plays: car, bluetooth, speaker…), from this
     *  device's diary of listening; made again every half hour. */
    @Volatile private var placesAt = 0L
    @Volatile private var placesKept: Map<String, Map<String, Double>> = emptyMap()
    private fun placeShares(): Map<String, Map<String, Double>> {
        val t = System.currentTimeMillis()
        if (t - placesAt < 30 * 60_000L) return placesKept
        placesAt = t
        placesKept = runCatching {
            ListenLog.all().filter { it.outKind.isNotEmpty() }.groupBy { statsKey("", it.artist).second }
                .filterValues { it.size >= 5 }
                .mapValues { (_, es) -> es.groupingBy { it.outKind }.eachCount().mapValues { it.value.toDouble() / es.size } }
        }.getOrDefault(emptyMap())
        return placesKept
    }

    private fun soundOf(t: Track): SoundFeatures? = if (!t.isFile) null else runCatching { Moods.sound(t) }.getOrNull()
}
