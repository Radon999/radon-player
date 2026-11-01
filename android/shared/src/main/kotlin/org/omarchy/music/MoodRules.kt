package org.omarchy.music

import kotlin.math.abs

/** How a song's moods are worked out, from its clues, each adding points to the moods it suggests:
 *  - its tags (Last.fm's, for the song and, at a bit less, its artist): words like “chill”, “sad”, “party”,
 *    and genres too (most Last.fm tags are genres). The strongest clue: people chose them.
 *  - its file's genre: “Death Metal” leans Intense, “Lo-fi” leans Chill and Focus, “House” leans Party.
 *  - its sound (see [SoundMood]): loud, fast and busy leans Energetic; slow, quiet and sparse leans Calm;
 *    a minor key leans Sad or Dark, a major one Happy; a steady danceable beat leans Party. Sound only says
 *    which moods stand out from the rest, so it can tip a choice but rarely outweighs real tags.
 *  - its words (see [LyricsMood]), when they've been read: Sad, Romantic and Dark most.
 *  Each mood's points become its score (0–1); the song is in every mood that scores high enough, the top one its main
 *  mood (see [judge]). */
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

    /** Moods on the gloomy side and the glad side (for when the sound and the words disagree). */
    private val GLOOMY = setOf("Sad", "Dark")
    private val GLAD = setOf("Happy", "Party", "Energetic", "Hype")

    /** A mood's points turned into its score (0–1): how far it fits. 0.7 points: about half. */
    private const val SPREAD = 0.7
    /** How much the sound counts: how far each mood stands out from the song's average sound, times this. */
    private const val SOUND_K = 1.1
    /** A mood fits the song when its score is at least this… */
    const val FITS = 0.40
    /** …and at least this share of the song's top score (so a song with one very clear mood isn't in many weak ones). */
    const val NEAR_TOP = 0.45
    /** The most moods a song is in (a song in more says little about any of them). */
    const val MOST = 5
    /** Words that say a mood at least this much (0–1) count even against a sound that says otherwise. */
    const val STRONG_WORDS = 0.6

    /** Moods that pull against each other: a song is in both only when it plainly says both ([BOTH] for the weaker one). */
    private val OPPOSITE: Map<String, Set<String>> = listOf("Happy" to "Sad", "Happy" to "Dark", "Party" to "Sad", "Calm" to "Intense",
        "Calm" to "Hype", "Calm" to "Energetic", "Chill" to "Intense").flatMap { (x, y) -> listOf(x to y, y to x) }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
    /** The score the weaker of two opposite moods needs to be a mood of the song too. */
    const val BOTH = 0.55

    /** What the sound says in a few plain numbers: energy, fast, major, minor, bright (each 0–1). */
    private class Cues(f: SoundFeatures) {
        val e = clamp((f.loud + 28) / 18)
        val fast = if (f.bpm <= 0) 0.5 else clamp((f.bpm - 85) / 55)
        val up = clamp(f.major); val down = clamp(-f.major)
        val b = clamp((f.bright - 1000) / 2500)
        /** Clearly upbeat (loud, quick, and major or bright): 0 not at all … 1 plainly. */
        val upbeat = clamp((minOf(e, fast) - 0.4) / 0.3) * clamp((maxOf(up, b) - 0.1) / 0.4)
        /** Clearly downbeat (quiet, slow, and minor or dark): 0 … 1. */
        val downbeat = clamp((minOf(1 - e, 1 - fast) - 0.35) / 0.3) * clamp((maxOf(down, 1 - b) - 0.3) / 0.4)
        /** How glad the sound is, -1 … 1. */
        val valence = (0.6 * f.major + 0.5 * (b - 0.5) + 0.4 * (fast - 0.5)).coerceIn(-1.0, 1.0)
        val energy = (0.5 * e + 0.3 * fast + 0.2 * clamp(f.onsets / 5)).coerceIn(0.0, 1.0)
    }

    /** A song's moods, worked out: [scores], every mood's fit (0–1); [moods], the ones that fit, best first, each with
     *  its score (the first is its main mood); [confidence], how sure (0–1); [conflict], how much its sound and its words
     *  disagree (0 agree or one of them unknown … 1 plainly opposite); [clues], how many kinds of clue there were. */
    data class Judgement(val scores: Map<String, Double>, val moods: List<Pair<String, Double>>, val confidence: Double,
                         val conflict: Double, val clues: Int) {
        val main: String? get() = moods.firstOrNull()?.first
    }

    private val NOTHING = Judgement(emptyMap(), emptyList(), 0.0, 0.0, 0)

    /** A song's moods, best first, each with its score (0–1): every mood that fits (see [judge]); none when nothing
     *  says anything. sound: what its sound says (null: not heard yet); genre: its file's genre ("" none); tags:
     *  Last.fm's tags of the song, and of its artist, each with how much it counts (0–1: Last.fm's count over 100);
     *  lyrics: what its words say ([LyricsReading.points]; empty: none read, or an instrumental). */
    fun decide(sound: SoundFeatures?, genre: String, tags: Map<String, Double> = emptyMap(), artistTags: Map<String, Double> = emptyMap(),
               lyrics: Map<String, Double> = emptyMap()): List<Pair<String, Double>> =
        judge(sound, genre, tags, artistTags, lyrics = lyrics).moods

    /** Works out a song's moods from what's known of it (see [decide] for the clues). Each clue adds points to the moods
     *  it suggests; a mood's points become its score (0–1), and the song is in every mood whose score is at least
     *  [FITS] and [NEAR_TOP] of its top one (at most [MOST]); the top one is its main mood.
     *  words: the words' reading (its points, valence and how sure, for when the sound and the words disagree):
     *  - a clearly upbeat sound with words only a little sad (or dark): the words' sadness doesn't count; strongly sad
     *    words still do (both moods then), at a bit less.
     *  - a clearly slow, quiet, minor sound with words only a little glad: the words' gladness doesn't count, likewise.
     *  - the sound and the words agreeing makes it surer; disagreeing, less sure (see [Judgement.conflict]).
     *  bias: what your fixes in the Tagger taught about this song (see [MoodLearn]), added to its scores; a small nudge
     *  that can't take away a mood its own clues say strongly. */
    fun judge(sound: SoundFeatures?, genre: String, tags: Map<String, Double> = emptyMap(), artistTags: Map<String, Double> = emptyMap(),
              words: LyricsReading? = null, lyrics: Map<String, Double> = words?.points().orEmpty(),
              bias: Map<String, Double> = emptyMap()): Judgement {
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
        val cues = sound?.takeIf { it.loud > -60 }?.let { Cues(it) }
        if (sound != null && cues != null) {
            clues++
            val s = fromSound(sound)
            val avg = s.values.average()
            // With words to go by, the sound counts a bit more for the moods you hear and a bit less for the ones you're told.
            s.forEach { (m, v) -> score.merge(m, SOUND_K * (if (lyrics.isEmpty()) 1.0 else SOUND_SAYS[m] ?: 1.0) * (v - avg), Double::plus) }
        }
        // The words (see [LyricsMood]): each mood by how much they say it, weighed by how much words say about it, unless
        // the sound plainly says otherwise and the words only hint.
        var conflict = 0.0; var agree = 0.0
        if (lyrics.isNotEmpty()) {
            clues++
            for ((m, raw) in lyrics) {
                val v = raw.coerceIn(0.0, 1.0)
                val against = when {
                    cues == null -> 0.0
                    m in GLOOMY -> cues.upbeat
                    m in GLAD -> cues.downbeat
                    else -> 0.0
                }
                val k = if (v >= STRONG_WORDS) 1 - 0.35 * against else 1 - against
                score.merge(m, 1.3 * (WORDS_SAY[m] ?: 0.7) * v * k, Double::plus)
            }
            // Do the sound and the words agree? By how glad each is (the words' only when they say enough).
            if (cues != null && words != null && !words.instrumental) {
                val sure = clamp(words.confidence / 0.6)
                val gap = kotlin.math.abs(cues.valence - words.valence) + 0.5 * kotlin.math.abs(cues.energy - words.energy)
                conflict = clamp((gap - 0.6) / 0.9) * sure
                agree = clamp((0.6 - gap) / 0.6) * sure
            }
        }
        val raw = score.filterValues { it > 0.08 }
        if (raw.isEmpty()) return NOTHING.copy(clues = clues)   // nothing of its own: a nudge alone gives no mood
        // Each mood's score: its points, saturating (0.7 points: about half; 2: nearly all), then your fixes' nudge.
        val fit = HashMap<String, Double>()
        for (m in ALL) {
            val p = raw[m] ?: 0.0
            val own = if (p > 0) 1 - kotlin.math.exp(-p / SPREAD) else 0.0
            val b = bias[m] ?: 0.0
            // A nudge only moves a mood the song's own clues say something of, and down can't take away what they say strongly.
            val nudge = when { b > 0 && own <= 0.0 -> 0.0; b < 0 && own >= 0.7 -> 0.3 * b; else -> b }
            val s = clamp(own + nudge)
            if (s > 0.01) fit[m] = Math.round(s * 100) / 100.0
        }
        if (fit.isEmpty()) return NOTHING.copy(clues = clues)
        val ranked = fit.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { ALL.indexOf(it.key) })
        val top = ranked[0].value
        if (top < 0.1) return NOTHING.copy(scores = fit, clues = clues)
        val moods = ArrayList<Pair<String, Double>>()
        moods += ranked[0].key to top
        for (e in ranked.drop(1)) {
            if (moods.size == MOST || e.value < FITS || e.value < NEAR_TOP * top) break
            // The opposite of a mood it's in already (Happy and Sad): only when that's plainly said too.
            if (e.value < BOTH && moods.any { it.first in OPPOSITE[e.key].orEmpty() }) continue
            moods += e.key to e.value
        }
        val second = ranked.getOrNull(1)?.value ?: 0.0
        val lead = (top - second) / top
        val sure = clamp(0.2 + 0.12 * clues + 0.2 * lead + 0.25 * top + 0.15 * agree - 0.3 * conflict)
        return Judgement(fit, moods, Math.round(sure * 100) / 100.0, Math.round(conflict * 100) / 100.0, clues)
    }
}
