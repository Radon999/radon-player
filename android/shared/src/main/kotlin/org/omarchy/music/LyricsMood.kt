package org.omarchy.music

import java.util.Locale
import kotlin.math.exp

/** What a song's words say about its mood: its mood ("" when the words don't say clearly), a second one, how sure
 *  (0–1), how glad or gloomy (valence, -1…1) and how calm or charged (energy, 0…1) the words are, a few themes
 *  (heartbreak, night, money…), and who read them ("words": the reader on this device, "ai": the AI reading).
 *  instrumental: no words to read (the sound alone decides). Kept in the file's MOOD_LYRICS tag as one line, see [tag]. */
data class LyricsReading(val mood: String = "", val second: String = "", val confidence: Double = 0.0,
                         val valence: Double = 0.0, val energy: Double = 0.5, val themes: List<String> = emptyList(),
                         val by: String = "words", val instrumental: Boolean = false) {

    /** The MOOD_LYRICS tag's text: "Sad;0.82;second=Dark;valence=-0.60;energy=0.30;themes=heartbreak,night;by=words".
     *  An instrumental: "Instrumental;1.00;by=words". Words that say no mood clearly: "None;0.10;…". */
    fun tag(): String {
        if (instrumental) return "Instrumental;1.00;by=$by"
        val parts = arrayListOf(mood.ifEmpty { "None" }, fmt(confidence))
        if (second.isNotEmpty()) parts += "second=$second"
        parts += "valence=" + fmt(valence)
        parts += "energy=" + fmt(energy)
        if (themes.isNotEmpty()) parts += "themes=" + themes.joinToString(",")
        parts += "by=$by"
        return parts.joinToString(";")
    }

    /** The points the words give the moods, for [MoodRules.decide]: its mood by how sure, its second a bit less. */
    fun points(): Map<String, Double> {
        if (instrumental || mood !in MoodRules.ALL || confidence <= 0) return emptyMap()
        val out = linkedMapOf(mood to confidence)
        if (second in MoodRules.ALL && second != mood) out[second] = 0.6 * confidence
        return out
    }

    companion object {
        private fun fmt(v: Double) = String.format(Locale.ROOT, "%.2f", v)

        /** A MOOD_LYRICS tag read back (see [tag]); null if it isn't one. */
        fun parse(text: String?): LyricsReading? {
            val parts = text?.trim()?.split(';')?.map { it.trim() }?.takeIf { it.size >= 2 } ?: return null
            val first = parts[0]
            val sure = parts[1].toDoubleOrNull() ?: return null
            val kv = parts.drop(2).filter { '=' in it }.associate { it.substringBefore('=').trim().lowercase() to it.substringAfter('=').trim() }
            val by = kv["by"].orEmpty().ifEmpty { "words" }
            if (first.equals("Instrumental", true)) return LyricsReading(by = by, instrumental = true, confidence = 1.0)
            val mood = MoodRules.ALL.firstOrNull { it.equals(first, true) } ?: if (first.equals("None", true)) "" else return null
            return LyricsReading(mood, MoodRules.ALL.firstOrNull { it.equals(kv["second"], true) }.orEmpty(), sure.coerceIn(0.0, 1.0),
                kv["valence"]?.toDoubleOrNull()?.coerceIn(-1.0, 1.0) ?: 0.0, kv["energy"]?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.5,
                kv["themes"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }, by)
        }
    }
}

/** Reads a song's mood from its words, on the device, with nothing sent anywhere: each word that leans to a mood (our
 *  own word lists, below) adds to it; "not", "never", "don't"… just before a word turn it around (and a happy word
 *  turned around leans sad); "so", "really", "too"… make the next word count more; a line sung again (a chorus)
 *  counts a little each time after the first, not in full. How glad and how charged the words are follow from the
 *  moods they lean to. The surer the more words say it and the clearer the lead over the next mood. */
object LyricsMood {
    /** Where each mood sits: (valence -1…1, energy 0…1), for the words' valence and energy. */
    private val PLACE = mapOf(
        "Happy" to (0.8 to 0.65), "Energetic" to (0.3 to 0.9), "Party" to (0.6 to 0.9), "Hype" to (0.3 to 0.95),
        "Chill" to (0.4 to 0.25), "Calm" to (0.35 to 0.1), "Dreamy" to (0.3 to 0.3), "Focus" to (0.1 to 0.4),
        "Romantic" to (0.6 to 0.4), "Sad" to (-0.8 to 0.3), "Dark" to (-0.6 to 0.55), "Intense" to (-0.5 to 0.95))

    /** Our own word lists: word to how much it leans to the mood (1: plainly; less: a hint). Whole words, lower case,
     *  without apostrophes ("dont"); a plural or -ing/-ed form finds its word too. */
    private fun lex(vararg words: Pair<String, Double>) = words.toList()
    private val MOODS: Map<String, List<Pair<String, Double>>> = mapOf(
        "Happy" to lex("happy" to 1.0, "happiness" to 1.0, "smile" to 1.0, "laugh" to 0.9, "laughter" to 0.9, "sunshine" to 1.0,
            "sunny" to 0.8, "joy" to 1.0, "joyful" to 1.0, "glad" to 0.8, "bright" to 0.5, "wonderful" to 0.8, "shine" to 0.5,
            "lucky" to 0.7, "blessed" to 0.6, "fun" to 0.7, "cheer" to 0.8, "grin" to 0.8, "delight" to 0.9, "hope" to 0.5,
            "hopeful" to 0.7, "alright" to 0.4, "good" to 0.3, "great" to 0.4, "beautiful" to 0.4, "paradise" to 0.6, "free" to 0.4,
            "celebrate" to 0.6, "golden" to 0.4, "yay" to 0.8, "sing" to 0.4, "sweet" to 0.3, "summer" to 0.5, "best" to 0.3,
            "thankful" to 0.8, "grateful" to 0.8, "proud" to 0.5, "rainbow" to 0.7, "playful" to 0.8, "carefree" to 1.0),
        "Energetic" to lex("run" to 0.6, "fast" to 0.8, "faster" to 0.9, "speed" to 0.8, "alive" to 0.7, "jump" to 0.8,
            "rush" to 0.7, "race" to 0.7, "adrenaline" to 1.0, "electric" to 0.8, "energy" to 1.0, "unstoppable" to 1.0,
            "rise" to 0.5, "ignite" to 0.9, "thunder" to 0.6, "louder" to 0.8, "loud" to 0.5, "charge" to 0.6, "rocket" to 0.7,
            "drive" to 0.5, "engine" to 0.6, "power" to 0.7, "go" to 0.15, "move" to 0.5, "wild" to 0.5, "fire" to 0.4,
            "higher" to 0.4, "sprint" to 0.9, "pump" to 0.7, "spark" to 0.6, "lightning" to 0.8, "awake" to 0.5),
        "Party" to lex("party" to 1.0, "dance" to 1.0, "club" to 1.0, "dancefloor" to 1.0, "drink" to 0.7, "shot" to 0.5,
            "tequila" to 0.9, "vodka" to 0.8, "champagne" to 0.7, "bottle" to 0.5, "celebrate" to 0.6, "weekend" to 0.7,
            "tonight" to 0.35, "dj" to 1.0, "groove" to 0.8, "disco" to 0.9, "bounce" to 0.7, "shake" to 0.6, "lit" to 0.6,
            "turnt" to 0.9, "crowd" to 0.5, "friday" to 0.6, "saturday" to 0.6, "beat" to 0.4, "floor" to 0.3, "drunk" to 0.6,
            "cheers" to 0.7, "wasted" to 0.4, "music" to 0.3, "speakers" to 0.6, "bass" to 0.5),
        "Hype" to lex("boss" to 0.9, "money" to 0.8, "cash" to 0.8, "racks" to 0.9, "stacks" to 0.7, "grind" to 0.7,
            "hustle" to 0.9, "flex" to 1.0, "drip" to 0.9, "diamonds" to 0.6, "chain" to 0.4, "king" to 0.5, "goat" to 0.6,
            "winning" to 0.8, "win" to 0.6, "champion" to 0.9, "legend" to 0.6, "savage" to 0.9, "gang" to 0.7, "squad" to 0.7,
            "rich" to 0.8, "bling" to 0.9, "ballin" to 0.9, "hype" to 1.0, "crown" to 0.6, "gold" to 0.4, "whip" to 0.4,
            "lambo" to 0.9, "rolex" to 0.9, "hater" to 0.6, "top" to 0.3, "built" to 0.3, "unstoppable" to 0.5, "bag" to 0.4),
        "Chill" to lex("chill" to 1.0, "relax" to 1.0, "easy" to 0.6, "slow" to 0.5, "vibe" to 0.8, "lazy" to 0.8, "breeze" to 0.8,
            "beach" to 0.6, "sunday" to 0.6, "sip" to 0.6, "cruise" to 0.7, "mellow" to 0.9, "wave" to 0.4, "unwind" to 1.0,
            "coffee" to 0.5, "smoke" to 0.4, "laid" to 0.5, "hammock" to 1.0, "sunset" to 0.6, "porch" to 0.6, "afternoon" to 0.5,
            "cool" to 0.4, "slowly" to 0.4, "kick" to 0.2, "smooth" to 0.6, "drift" to 0.3, "lounge" to 0.8),
        "Calm" to lex("calm" to 1.0, "quiet" to 0.9, "peace" to 1.0, "peaceful" to 1.0, "still" to 0.4, "silence" to 0.7,
            "silent" to 0.6, "gentle" to 0.8, "soft" to 0.7, "softly" to 0.8, "rest" to 0.7, "sleep" to 0.8, "lullaby" to 1.0,
            "hush" to 0.9, "breathe" to 0.6, "whisper" to 0.5, "serene" to 1.0, "safe" to 0.5, "home" to 0.3, "warm" to 0.4,
            "snow" to 0.4, "morning" to 0.3, "river" to 0.4, "ocean" to 0.4, "meadow" to 0.8, "candle" to 0.5, "patience" to 0.5,
            "heal" to 0.6, "healing" to 0.7, "slumber" to 0.9, "tranquil" to 1.0),
        "Dreamy" to lex("dream" to 1.0, "star" to 0.6, "sky" to 0.6, "moon" to 0.7, "moonlight" to 0.9, "cloud" to 0.7,
            "galaxy" to 0.9, "space" to 0.5, "heaven" to 0.4, "float" to 0.8, "drift" to 0.8, "haze" to 0.8, "hazy" to 0.9,
            "glow" to 0.6, "fantasy" to 0.9, "magic" to 0.7, "wonder" to 0.5, "angel" to 0.4, "universe" to 0.7, "stardust" to 1.0,
            "kaleidoscope" to 1.0, "neon" to 0.4, "wings" to 0.5, "fly" to 0.4, "comet" to 0.8, "planet" to 0.6, "orbit" to 0.8,
            "daydream" to 1.0, "mist" to 0.7, "ethereal" to 1.0, "shimmer" to 0.8, "sparkle" to 0.6, "cosmic" to 0.9),
        "Focus" to lex("focus" to 1.0, "think" to 0.4, "mind" to 0.4, "work" to 0.4, "learn" to 0.7, "study" to 0.9, "clear" to 0.4,
            "plan" to 0.6, "build" to 0.5, "steady" to 0.6, "patient" to 0.6, "goal" to 0.8, "concentrate" to 1.0, "wisdom" to 0.7,
            "path" to 0.4, "thought" to 0.4, "question" to 0.4, "answer" to 0.4, "discipline" to 1.0, "determined" to 0.8,
            "progress" to 0.8, "patience" to 0.4),
        "Romantic" to lex("love" to 1.0, "lover" to 1.0, "kiss" to 1.0, "baby" to 0.5, "babe" to 0.6, "darling" to 0.9,
            "darlin" to 0.9, "honey" to 0.7, "heart" to 0.5, "hold" to 0.5, "touch" to 0.7, "together" to 0.7, "forever" to 0.5,
            "yours" to 0.5, "eyes" to 0.4, "lips" to 0.8, "embrace" to 0.9, "romance" to 1.0, "valentine" to 1.0, "marry" to 0.9,
            "wedding" to 0.8, "sweetheart" to 1.0, "desire" to 0.7, "skin" to 0.5, "bed" to 0.4, "crush" to 0.7, "adore" to 1.0,
            "cherish" to 1.0, "beloved" to 1.0, "soulmate" to 1.0, "hug" to 0.8, "cuddle" to 1.0, "dear" to 0.5, "mine" to 0.3,
            "close" to 0.3, "body" to 0.4, "flower" to 0.4, "roses" to 0.6, "candlelight" to 0.9),
        "Sad" to lex("sad" to 1.0, "sadness" to 1.0, "cry" to 1.0, "tears" to 1.0, "tear" to 0.5, "alone" to 0.9, "lonely" to 1.0,
            "loneliness" to 1.0, "hurt" to 0.8, "pain" to 0.8, "broken" to 1.0, "heartbreak" to 1.0, "heartache" to 1.0,
            "heartbroken" to 1.0, "goodbye" to 0.9, "gone" to 0.6, "lost" to 0.6, "lose" to 0.5, "miss" to 0.7, "sorry" to 0.6,
            "regret" to 0.8, "empty" to 0.8, "cold" to 0.35, "rain" to 0.4, "grey" to 0.5, "gray" to 0.5, "blue" to 0.4,
            "sorrow" to 1.0, "grief" to 1.0, "mourn" to 1.0, "funeral" to 1.0, "dying" to 0.6, "apart" to 0.7, "leaving" to 0.6,
            "fade" to 0.6, "memories" to 0.6, "memory" to 0.5, "scars" to 0.6, "wound" to 0.6, "numb" to 0.7, "drown" to 0.6,
            "hollow" to 0.8, "ache" to 0.8, "weep" to 1.0, "tired" to 0.5, "sigh" to 0.6, "farewell" to 0.8, "lonesome" to 1.0,
            "depressed" to 1.0, "misery" to 1.0, "blame" to 0.4, "ended" to 0.5, "falling" to 0.25, "used" to 0.15, "wish" to 0.3),
        "Dark" to lex("dark" to 1.0, "darkness" to 1.0, "shadow" to 0.8, "night" to 0.3, "devil" to 1.0, "demon" to 1.0, "hell" to 0.9,
            "evil" to 1.0, "blood" to 0.7, "death" to 0.9, "grave" to 0.9, "sin" to 0.7, "poison" to 0.8, "monster" to 0.9,
            "nightmare" to 1.0, "haunt" to 1.0, "ghost" to 0.6, "curse" to 0.9, "black" to 0.4, "venom" to 0.9, "wicked" to 0.9,
            "knife" to 0.7, "bury" to 0.8, "coffin" to 1.0, "abyss" to 1.0, "void" to 0.8, "doom" to 1.0, "sinister" to 1.0,
            "fear" to 0.7, "afraid" to 0.6, "midnight" to 0.4, "cage" to 0.6, "prison" to 0.6, "bones" to 0.7, "skull" to 0.8,
            "prey" to 0.7, "villain" to 0.8, "toxic" to 0.6, "paranoid" to 0.8, "crime" to 0.5, "dead" to 0.6, "corpse" to 1.0,
            "creep" to 0.6, "shadows" to 0.9, "lies" to 0.4, "secret" to 0.3, "chains" to 0.4, "wolf" to 0.5),
        "Intense" to lex("rage" to 1.0, "fight" to 1.0, "war" to 0.8, "scream" to 0.8, "kill" to 0.7, "destroy" to 1.0, "burn" to 0.6,
            "hate" to 1.0, "hatred" to 1.0, "anger" to 1.0, "angry" to 1.0, "mad" to 0.6, "fury" to 1.0, "furious" to 1.0,
            "break" to 0.5, "smash" to 0.9, "crash" to 0.7, "explode" to 0.9, "riot" to 1.0, "violence" to 1.0, "violent" to 1.0,
            "enemy" to 0.8, "revenge" to 1.0, "attack" to 0.9, "battle" to 0.8, "storm" to 0.5, "chaos" to 0.9, "insane" to 0.7,
            "bullet" to 0.8, "gun" to 0.5, "punch" to 0.9, "beast" to 0.6, "fist" to 0.8, "bleed" to 0.5, "blood" to 0.4,
            "rebel" to 0.7, "warrior" to 0.7, "shout" to 0.6, "pressure" to 0.5, "unleash" to 0.9, "wrath" to 1.0),
    )

    /** Word to (mood, weight), built from the lists. */
    private val WORD: Map<String, List<Pair<String, Double>>> = HashMap<String, MutableList<Pair<String, Double>>>().also { m ->
        for ((mood, ws) in MOODS) for ((w, v) in ws) m.getOrPut(w) { ArrayList() } += mood to v
    }

    /** Words that turn the next few words around. */
    private val NOT = setOf("not", "no", "never", "dont", "cant", "wont", "aint", "isnt", "wasnt", "didnt", "couldnt", "wouldnt",
        "shouldnt", "doesnt", "arent", "werent", "nobody", "nothing", "nowhere", "neither", "nor", "without", "cannot", "hardly",
        "barely", "havent", "hasnt", "nah", "nope", "stop", "quit")

    /** Words that make the next word count more. */
    private val MORE = setOf("so", "very", "really", "too", "totally", "completely", "truly", "deeply", "extremely", "super",
        "absolutely", "utterly", "such", "always", "endlessly")

    /** Themes: a few words each; a theme counts when its words come up at least twice. */
    private val THEMES: Map<String, Set<String>> = mapOf(
        "heartbreak" to setOf("heartbreak", "heartache", "heartbroken", "broken", "goodbye", "apart", "tears", "ex", "leaving", "lie"),
        "love" to setOf("love", "lover", "kiss", "darling", "baby", "babe", "heart", "romance", "together", "forever"),
        "party" to setOf("party", "dance", "club", "drink", "tequila", "weekend", "dj", "shot", "drunk", "dancefloor"),
        "night" to setOf("night", "midnight", "moon", "moonlight", "stars", "star", "dark", "tonight", "streetlights"),
        "money" to setOf("money", "cash", "rich", "racks", "bands", "stacks", "paid", "bag", "dollar", "dollars"),
        "freedom" to setOf("free", "freedom", "escape", "fly", "away", "road", "highway", "wings", "open"),
        "home" to setOf("home", "mama", "mom", "mother", "father", "dad", "family", "hometown", "house"),
        "loss" to setOf("death", "dead", "funeral", "grave", "gone", "miss", "heaven", "lost", "mourn"),
        "faith" to setOf("god", "lord", "pray", "prayer", "faith", "jesus", "church", "soul", "amen", "heaven"),
        "summer" to setOf("summer", "sun", "sunshine", "beach", "july", "ocean", "waves", "sand"),
        "friends" to setOf("friends", "friend", "homies", "crew", "squad", "gang", "brother", "brothers"),
        "rebellion" to setOf("fight", "riot", "rules", "rebel", "war", "revolution", "system", "against"),
        "loneliness" to setOf("alone", "lonely", "loneliness", "nobody", "empty", "lonesome"),
        "hope" to setOf("hope", "tomorrow", "someday", "dream", "believe", "light", "rise"),
        "success" to setOf("boss", "king", "winning", "win", "top", "crown", "champion", "made", "fame"),
    )

    /** Section names and timings, not words: "[Chorus]", "[00:12.34]". */
    private val NOISE = Regex("\\[[^\\]]*]|<\\d+:\\d+(?:\\.\\d+)?>")
    private val TOKEN = Regex("[\\p{L}']+")

    /** A word as the lists have it: lower case, no apostrophes ("don't": "dont"; "runnin'": "running"). */
    internal fun word(raw: String): String {
        var w = raw.lowercase().replace('’', '\'')
        if (w.endsWith("in'")) w = w.dropLast(1) + "g"
        return w.replace("'", "")
    }

    /** The lists' word for w, trying its plain form ("tears", "dreaming", "kissed"): null if it's in none. */
    private fun find(w: String): List<Pair<String, Double>>? {
        WORD[w]?.let { return it }
        val tries = ArrayList<String>()
        if (w.length > 3 && w.endsWith("s")) { tries += w.dropLast(1); if (w.endsWith("es")) tries += w.dropLast(2) }
        if (w.length > 5 && w.endsWith("ing")) { val b = w.dropLast(3); tries += b; tries += b + "e"; if (b.length > 2 && b.last() == b[b.length - 2]) tries += b.dropLast(1) }
        if (w.length > 4 && w.endsWith("ed")) { val b = w.dropLast(2); tries += b; tries += b + "e"; if (b.length > 2 && b.last() == b[b.length - 2]) tries += b.dropLast(1) }
        if (w.length > 4 && w.endsWith("ly")) tries += w.dropLast(2)
        if (w.length > 4 && (w.endsWith("ies") || w.endsWith("ied"))) tries += w.dropLast(3) + "y"
        for (t in tries) WORD[t]?.let { return it }
        return null
    }

    /** Whether the text is an instrumental's (no words, or only "Instrumental"). */
    fun isInstrumental(lyrics: String?): Boolean {
        val words = lyrics?.let { NOISE.replace(it, " ") }?.let { TOKEN.findAll(it).map { m -> word(m.value) }.toList() }.orEmpty()
        return words.isEmpty() || (words.size <= 3 && words.any { it == "instrumental" })
    }

    /** The mood of the words (see the object's note). */
    fun read(lyrics: String?): LyricsReading {
        if (isInstrumental(lyrics)) return LyricsReading(instrumental = true, confidence = 1.0)
        val score = HashMap<String, Double>()
        var vSum = 0.0; var eSum = 0.0; var wSum = 0.0
        val themeHits = HashMap<String, Double>()
        val seen = HashMap<String, Int>()   // each line, how often it's come so far
        for (raw in lyrics!!.lines()) {
            val line = NOISE.replace(raw, " ")
            val tokens = TOKEN.findAll(line).map { word(it.value) }.filter { it.isNotEmpty() }.toList()
            if (tokens.isEmpty()) continue
            // A line sung again counts less each time: the chorus says the song's mood, but not ten times over.
            val n = seen.merge(tokens.joinToString(" "), 1, Int::plus)!!
            val lineWeight = when (n) { 1 -> 1.0; 2 -> 0.4; 3 -> 0.2; else -> 0.05 }
            var boost = 1.0; var boostLeft = 0
            var notLeft = 0
            val inLine = HashSet<String>()   // a word said twice in a line ("alone, alone") counts once
            for (w in tokens) {
                if (w in NOT) { notLeft = if (notLeft > 0 && (w == "stop" || w == "quit")) 0 else 3; continue }   // "can't stop": not turned
                if (w in MORE) { boost = 1.5; boostLeft = 2; continue }
                for ((theme, ws) in THEMES) if (w in ws) themeHits.merge(theme, lineWeight, Double::plus)
                val hits = find(w)
                if (hits != null && inLine.add(w)) {
                    val turned = notLeft > 0
                    val k = lineWeight * (if (boostLeft > 0) boost else 1.0)
                    for ((mood, v) in hits) {
                        val pts = v * k
                        if (turned) {
                            // "not happy": takes from Happy; a glad or loving word turned around leans sad.
                            score.merge(mood, -0.5 * pts, Double::plus)
                            if (mood == "Happy" || mood == "Romantic" || mood == "Calm") score.merge("Sad", 0.3 * pts, Double::plus)
                        } else score.merge(mood, pts, Double::plus)
                        val (va, en) = PLACE.getValue(mood)
                        vSum += (if (turned) -0.5 * va else va) * pts
                        eSum += en * pts
                        wSum += pts
                    }
                    boostLeft = 0
                }
                if (notLeft > 0) notLeft--
                if (boostLeft > 0) boostLeft--
            }
        }
        val ranked = score.entries.filter { it.value > 0 }.sortedByDescending { it.value }
        val themes = themeHits.entries.filter { it.value >= 2.0 }.sortedByDescending { it.value }.take(3).map { it.key }
        val valence = if (wSum > 0) (vSum / wSum).coerceIn(-1.0, 1.0) else 0.0
        val energy = if (wSum > 0) (eSum / wSum).coerceIn(0.0, 1.0) else 0.5
        val top = ranked.firstOrNull()?.value ?: 0.0
        if (top < 1.2) return LyricsReading("", "", round(minOf(0.2, top / 6)), round(valence), round(energy), themes)
        val second = ranked.getOrNull(1)
        val lead = (top - (second?.value ?: 0.0)) / top
        val sure = ((1 - exp(-top / 4)) * (0.45 + 0.55 * lead)).coerceIn(0.0, 0.95)
        return LyricsReading(ranked[0].key, second?.takeIf { it.value >= 0.5 * top }?.key.orEmpty(), round(sure), round(valence), round(energy), themes)
    }

    private fun round(v: Double) = Math.round(v * 100) / 100.0
}
