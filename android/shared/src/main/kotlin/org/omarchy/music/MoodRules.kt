package org.omarchy.music

import kotlin.math.abs

/** How a song's mood is worked out, from three clues, each adding points to the moods it suggests:
 *  - its tags (Last.fm's, for the song and, at a bit less, its artist): words like “chill”, “sad”, “party”,
 *    and genres too (most Last.fm tags are genres). The strongest clue: people chose them.
 *  - its file's genre: “Death Metal” leans Intense, “Lo-fi” leans Chill and Focus, “House” leans Party.
 *  - its sound (see [SoundMood]): loud, fast and busy leans Energetic; slow, quiet and sparse leans Calm;
 *    a minor key leans Sad or Dark, a major one Happy; a steady danceable beat leans Party. Sound only says
 *    which moods stand out from the rest, so it can tip a choice but rarely outweighs real tags.
 *  The mood with the most points is the song's mood; up to two more that come close are its other moods. */
object MoodRules {
    /** The moods, in the order the Moods tab shows them. */
    val ALL = listOf("Happy", "Energetic", "Party", "Hype", "Chill", "Calm", "Dreamy", "Focus", "Romantic", "Sad", "Dark", "Intense")

    /** Mood words in tags (whole words): the moods they mean, and how much. */
    private val WORDS: List<Pair<List<String>, Map<String, Double>>> = listOf(
        listOf("chill", "chillout", "chill out", "chilled", "laid back", "laidback", "lounge", "downtempo", "trip hop", "chillhop",
            "lofi", "lo fi", "chillwave", "rainy day") to mapOf("Chill" to 1.0),
        listOf("mellow") to mapOf("Calm" to 0.8, "Chill" to 0.5),
        listOf("relax", "relaxing", "calm", "peaceful", "soft", "gentle", "soothing", "quiet", "tranquil", "serene") to mapOf("Calm" to 1.0),
        listOf("sleep", "sleepy", "lullaby") to mapOf("Calm" to 1.0, "Dreamy" to 0.4),
        listOf("sad", "melancholic", "melancholy", "melancholia", "depressing", "depressive", "heartbreak", "heartbroken", "sorrow",
            "crying", "lonely", "grief", "bittersweet") to mapOf("Sad" to 1.0),
        listOf("emotional") to mapOf("Sad" to 0.5, "Romantic" to 0.2),
        listOf("happy", "feel good", "feelgood", "cheerful", "joyful", "joy", "fun", "sunny", "summer", "uplifting", "optimistic",
            "good vibes", "positive") to mapOf("Happy" to 1.0),
        listOf("upbeat") to mapOf("Happy" to 0.7, "Energetic" to 0.6),
        listOf("energetic", "energy", "workout", "running", "gym", "power", "fast", "adrenaline") to mapOf("Energetic" to 1.0),
        listOf("driving", "road trip") to mapOf("Energetic" to 0.6, "Happy" to 0.3),
        listOf("party", "dance", "club", "dancefloor", "clubbing", "danceable") to mapOf("Party" to 1.0),
        listOf("hype", "banger", "bangers", "turn up", "pump up", "hard hitting") to mapOf("Hype" to 1.0),
        listOf("epic", "anthem", "anthemic") to mapOf("Energetic" to 0.5, "Hype" to 0.4),
        listOf("romantic", "love", "love songs", "love song", "sensual", "sexy", "seductive", "slow jams", "slow jam") to mapOf("Romantic" to 1.0),
        listOf("ballad", "ballads") to mapOf("Romantic" to 0.6, "Sad" to 0.3),
        listOf("angry", "aggressive", "rage", "anger", "heavy", "brutal", "intense", "furious", "violent") to mapOf("Intense" to 1.0),
        listOf("dark", "sinister", "haunting", "creepy", "eerie", "noir", "ominous", "evil") to mapOf("Dark" to 1.0),
        listOf("dreamy", "dream", "ethereal", "atmospheric", "spacey", "hazy", "floating", "hypnotic") to mapOf("Dreamy" to 1.0),
        listOf("focus", "study", "studying", "concentration", "background", "coding", "work") to mapOf("Focus" to 1.0),
        listOf("instrumental") to mapOf("Focus" to 0.6),
    ).map { (ws, m) -> ws.map { " $it " } to m }

    /** Genre names (as part of a genre or a tag: “metal” is in “Death Metal” and “metalcore”) and the moods they lean to. */
    private val GENRES: List<Pair<List<String>, Map<String, Double>>> = listOf(
        listOf("metal", "hardcore", "grindcore", "deathcore", "thrash") to mapOf("Intense" to 0.9, "Dark" to 0.3),
        listOf("punk") to mapOf("Energetic" to 0.7, "Intense" to 0.5),
        listOf("hard rock") to mapOf("Energetic" to 0.4, "Intense" to 0.4),
        listOf("rock") to mapOf("Energetic" to 0.5),
        listOf("grunge") to mapOf("Dark" to 0.5, "Intense" to 0.4, "Sad" to 0.3),
        listOf("emo ", "emocore", "midwest emo") to mapOf("Sad" to 0.7, "Intense" to 0.3),
        listOf("goth", "darkwave", "industrial", "dark") to mapOf("Dark" to 0.9),
        listOf("phonk") to mapOf("Hype" to 0.7, "Dark" to 0.5),
        listOf("trap") to mapOf("Hype" to 0.9),
        listOf("hip hop", "hiphop", "rap", "drill", "grime") to mapOf("Hype" to 0.8, "Energetic" to 0.3),
        listOf("drum and bass", "drum & bass", "drum&bass", "dnb", "dubstep", "jungle", "hardstyle", "breakbeat") to mapOf("Energetic" to 0.9, "Hype" to 0.5),
        listOf("house", "techno", "edm", "electro", "disco", "dance", "club", "garage") to mapOf("Party" to 0.8, "Energetic" to 0.5),
        listOf("trance", "psytrance") to mapOf("Energetic" to 0.6, "Dreamy" to 0.4, "Party" to 0.4),
        listOf("reggaeton", "latin", "salsa", "cumbia", "afrobeat", "dancehall", "amapiano", "soca") to mapOf("Party" to 0.8, "Happy" to 0.4),
        listOf("bachata", "bolero") to mapOf("Romantic" to 0.8),
        listOf("funk") to mapOf("Party" to 0.6, "Happy" to 0.5),
        listOf("reggae", "ska") to mapOf("Chill" to 0.6, "Happy" to 0.5),
        listOf("k pop", "kpop", "j pop", "jpop", "bubblegum") to mapOf("Happy" to 0.6, "Party" to 0.5),
        listOf("hyperpop") to mapOf("Hype" to 0.7, "Happy" to 0.3),
        listOf("pop", "synthpop", "electropop", "indiepop", "dancepop") to mapOf("Happy" to 0.5, "Party" to 0.2),
        listOf("r&b", "rnb", "r and b", "rhythm and blues", "neo soul") to mapOf("Romantic" to 0.7, "Chill" to 0.4),
        listOf("soul") to mapOf("Romantic" to 0.6, "Chill" to 0.3),
        listOf("jazz") to mapOf("Chill" to 0.6, "Romantic" to 0.3, "Focus" to 0.2),
        listOf("bossa nova") to mapOf("Chill" to 0.7, "Romantic" to 0.5),
        listOf("blues") to mapOf("Sad" to 0.6, "Romantic" to 0.2),
        listOf("lofi", "lo fi", "chillhop") to mapOf("Chill" to 0.9, "Focus" to 0.6),
        listOf("chill", "lounge") to mapOf("Chill" to 0.9),
        listOf("downtempo", "trip hop") to mapOf("Chill" to 0.8, "Dreamy" to 0.4, "Dark" to 0.2),
        listOf("ambient", "drone") to mapOf("Calm" to 0.6, "Focus" to 0.5, "Dreamy" to 0.5),
        listOf("new age", "meditation") to mapOf("Calm" to 1.0),
        listOf("classical", "baroque", "piano", "chamber") to mapOf("Focus" to 0.7, "Calm" to 0.6),
        listOf("soundtrack", "score", "cinematic", "orchestral") to mapOf("Focus" to 0.5, "Dreamy" to 0.4),
        listOf("opera") to mapOf("Romantic" to 0.4, "Sad" to 0.3),
        listOf("shoegaze", "dream pop", "dreampop") to mapOf("Dreamy" to 1.0),
        listOf("synthwave", "vaporwave", "chillwave", "retrowave") to mapOf("Dreamy" to 0.7, "Chill" to 0.4),
        listOf("psychedelic", "psych") to mapOf("Dreamy" to 0.7),
        listOf("post rock") to mapOf("Dreamy" to 0.6, "Focus" to 0.4),
        listOf("indie") to mapOf("Dreamy" to 0.3, "Chill" to 0.3),
        listOf("folk", "acoustic", "singer songwriter", "americana") to mapOf("Calm" to 0.6, "Sad" to 0.3),
        listOf("country") to mapOf("Happy" to 0.4, "Sad" to 0.2, "Romantic" to 0.2),
        listOf("gospel", "worship", "christian") to mapOf("Happy" to 0.4, "Calm" to 0.4),
        listOf("children", "kids", "christmas", "holiday") to mapOf("Happy" to 0.7),
    )

    /** Lower case, every run of other characters one space, a space at each end (for whole-word matching). */
    private fun clean(s: String) = " " + s.lowercase().replace(Regex("[^\\p{L}\\p{N}&]+"), " ").trim() + " "

    // The same tags and genres come up again and again: what each one means is worked out once.
    private val wordsSeen = java.util.concurrent.ConcurrentHashMap<String, Map<String, Double>>()
    private val genresSeen = java.util.concurrent.ConcurrentHashMap<String, Map<String, Double>>()

    /** Points from words (whole words of [text]). */
    private fun fromWords(text: String, weight: Double, into: MutableMap<String, Double>) {
        val pts = wordsSeen.getOrPut(text) {
            val t = clean(text)
            val out = HashMap<String, Double>()
            for ((ws, moods) in WORDS) if (ws.any { it in t }) moods.forEach { (m, v) -> out.merge(m, v, Double::plus) }
            out
        }
        pts.forEach { (m, v) -> into.merge(m, v * weight, Double::plus) }
    }

    /** Points from genre names in [text], at the start of any of its words: “metal” is in “Death Metal” and “metalcore”
     *  (a name ending in a space must be a whole word: “emo ” isn't in “emotional”). */
    private fun fromGenre(text: String, weight: Double, into: MutableMap<String, Double>) {
        val pts = genresSeen.getOrPut(text) {
            val t = clean(text)
            val out = HashMap<String, Double>()
            if (t.isNotBlank()) for ((gs, moods) in GENRES) if (gs.any { " $it" in t }) moods.forEach { (m, v) -> out.merge(m, v, Double::plus) }
            out
        }
        pts.forEach { (m, v) -> into.merge(m, v * weight, Double::plus) }
    }

    private fun clamp(v: Double, lo: Double = 0.0, hi: Double = 1.0) = v.coerceIn(lo, hi)

    /** What the sound alone suggests: each mood's fit (0–1), from simple rules a person can check. */
    fun fromSound(f: SoundFeatures): Map<String, Double> {
        val e = clamp((f.loud + 28) / 18)                                  // energy: -28 dB quiet … -10 dB loud
        val fast = if (f.bpm <= 0) 0.5 else clamp((f.bpm - 85) / 55)       // 85 BPM slow … 140 BPM fast
        val slow = 1 - fast
        val b = clamp((f.bright - 1000) / 2500)                            // warm and dark … bright and sharp
        val d = clamp(f.onsets / 5)                                        // sparse … busy (notes and hits a second)
        val beat = clamp(f.beat * 2)                                       // no steady beat … a clear one
        // even … big swings in loudness (within the part heard; for a whole song, from part to part too)
        val r = clamp(maxOf((f.range - 6) / 14, (f.vary - 2) / 6))
        val up = clamp(f.major); val down = clamp(-f.major)                // major key … minor key
        val mid = clamp(1 - abs(e - 0.4) * 2)                              // neither loud nor quiet
        val dance = if (f.bpm <= 0) 0.0 else clamp(1 - abs(f.bpm - 120) / 30)   // 100–135 BPM: danceable
        return mapOf(
            "Happy" to 0.45 * up + 0.2 * fast + 0.2 * b + 0.15 * e,
            "Energetic" to 0.45 * e + 0.3 * fast + 0.25 * d,
            "Party" to 0.35 * beat + 0.3 * dance + 0.25 * e + 0.1 * up,
            "Hype" to 0.35 * e + 0.3 * beat + 0.2 * d + 0.15 * (1 - b),
            "Chill" to 0.3 * mid + 0.3 * slow + 0.2 * (1 - d) + 0.2 * beat,
            "Calm" to 0.4 * (1 - e) + 0.3 * (1 - d) + 0.3 * slow,
            "Dreamy" to 0.3 * (1 - beat) + 0.25 * (1 - d) + 0.2 * (1 - b) + 0.25 * mid,
            "Focus" to 0.4 * (1 - r) + 0.3 * mid + 0.3 * (1 - d),
            "Romantic" to 0.35 * slow + 0.25 * mid + 0.2 * up + 0.2 * (1 - d),
            "Sad" to 0.45 * down + 0.3 * slow + 0.25 * (1 - e),
            "Dark" to 0.4 * down + 0.35 * (1 - b) + 0.25 * e,
            "Intense" to 0.4 * e + 0.35 * b + 0.25 * d,
        )
    }

    /** How much a song's words say about each mood (Sad, Romantic, Dark: a lot; Energetic, Party, Chill, Focus: little,
     *  the sound says those), and how much the sound counts when there are words too. */
    private val WORDS_SAY = mapOf("Sad" to 1.3, "Romantic" to 1.3, "Dark" to 1.2, "Happy" to 1.0, "Intense" to 0.9, "Hype" to 0.8,
        "Dreamy" to 0.7, "Calm" to 0.7, "Energetic" to 0.5, "Party" to 0.5, "Chill" to 0.45, "Focus" to 0.3)
    private val SOUND_SAYS = mapOf("Energetic" to 1.2, "Party" to 1.2, "Chill" to 1.2, "Focus" to 1.2, "Sad" to 0.8, "Romantic" to 0.8, "Dark" to 0.8)

    /** A song's moods, best first, each with how sure (0–1): at most three, none when nothing says anything.
     *  sound: what its sound says (null: not heard yet); genre: its file's genre ("" none); tags: Last.fm's tags of
     *  the song, and of its artist, each with how much it counts (0–1: Last.fm's count over 100); lyrics: what its words
     *  say ([LyricsReading.points]; empty: none read, or an instrumental, so the sound alone decides). */
    fun decide(sound: SoundFeatures?, genre: String, tags: Map<String, Double> = emptyMap(), artistTags: Map<String, Double> = emptyMap(),
               lyrics: Map<String, Double> = emptyMap()): List<Pair<String, Double>> {
        val score = HashMap<String, Double>()
        var clues = 0
        // Tags: mood words count most; genre names in tags a bit less. A mood gets at most 1.5 from tags.
        val tagPts = HashMap<String, Double>()
        for ((t, w) in tags) { fromWords(t, w, tagPts); fromGenre(t, 0.5 * w, tagPts) }
        for ((t, w) in artistTags) { fromWords(t, 0.6 * w, tagPts); fromGenre(t, 0.3 * w, tagPts) }
        if (tagPts.isNotEmpty()) clues++
        tagPts.forEach { (m, v) -> score.merge(m, 1.2 * minOf(v, 1.5), Double::plus) }
        // The file's genre
        val genrePts = HashMap<String, Double>()
        fromGenre(genre, 1.0, genrePts); fromWords(genre, 0.8, genrePts)   // "Chill", "Sad songs" as a genre
        if (genrePts.isNotEmpty()) clues++
        genrePts.forEach { (m, v) -> score.merge(m, 0.8 * minOf(v, 1.2), Double::plus) }
        // The sound: only how far each mood stands out from the song's average, so it tips rather than decides.
        if (sound != null && sound.loud > -60) {
            clues++
            val s = fromSound(sound)
            val avg = s.values.average()
            // With words to go by, the sound counts a bit more for the moods you hear and a bit less for the ones you're told.
            s.forEach { (m, v) -> score.merge(m, 1.5 * (if (lyrics.isEmpty()) 1.0 else SOUND_SAYS[m] ?: 1.0) * (v - avg), Double::plus) }
        }
        // The words (see [LyricsMood]): each mood by how sure the reading is, weighed by how much words say about it.
        if (lyrics.isNotEmpty()) {
            clues++
            lyrics.forEach { (m, v) -> score.merge(m, 1.3 * (WORDS_SAY[m] ?: 0.7) * v.coerceIn(0.0, 1.0), Double::plus) }
        }
        val ranked = score.entries.filter { it.value > 0.08 }.sortedByDescending { it.value }
        if (ranked.isEmpty()) return emptyList()
        val top = ranked[0].value
        val second = ranked.getOrNull(1)?.value ?: 0.0
        val sure = clamp(0.25 + 0.12 * clues + 0.4 * (top - second) / top + 0.1 * minOf(top, 1.0))
        val out = ArrayList<Pair<String, Double>>()
        out += ranked[0].key to Math.round(sure * 100) / 100.0
        for (e in ranked.drop(1)) {
            if (out.size == 3 || e.value < 0.55 * top) break
            out += e.key to Math.round(sure * e.value / top * 100) / 100.0
        }
        return out
    }
}
