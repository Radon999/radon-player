package org.omarchy.music

import java.util.Locale
import kotlin.math.exp

/** What a song's words say about its mood: its mood ("" when the words don't say clearly), a second one, how sure
 *  (0–1), how glad or gloomy (valence, -1…1) and how calm or charged (energy, 0…1) the words are, a few themes
 *  (heartbreak, night, money…), and who read them ("words": the reader on this device, "ai": the AI reading).
 *  scores: how much the words say each mood (0–1, every mood; a mood left out is 0), version: which reader made it
 *  (a word reading older than [LyricsMood.VERSION] is read again), meaning: the AI's one line about what the singer
 *  means ("" none). instrumental: no words to read (the sound alone decides). Kept in the file's MOOD_LYRICS tag as
 *  one line, see [tag]. */
data class LyricsReading(val mood: String = "", val second: String = "", val confidence: Double = 0.0,
                         val valence: Double = 0.0, val energy: Double = 0.5, val themes: List<String> = emptyList(),
                         val by: String = "words", val instrumental: Boolean = false,
                         val scores: Map<String, Double> = emptyMap(), val version: Int = LyricsMood.VERSION,
                         val meaning: String = "") {

    /** Read by an older word reader: worth reading again (an AI reading never is). */
    val outdated: Boolean get() = by == "words" && !instrumental && version < LyricsMood.VERSION

    /** Which of two readings of the same song to keep: the AI's, then the current reader's, then an old one's. */
    val rank: Int get() = when { by == "ai" -> 3; instrumental || version >= LyricsMood.VERSION -> 2; else -> 1 }

    /** The MOOD_LYRICS tag's text: "Sad;0.82;second=Dark;valence=-0.60;energy=0.30;themes=heartbreak,night;by=words;v=2;
     *  scores=Sad:0.82,Dark:0.40,Calm:0.20". An instrumental: "Instrumental;1.00;by=words". Words that say no mood
     *  clearly: "None;0.10;…". The parts after the first two are name=value, so an older app reads the start and skips
     *  the rest. */
    fun tag(): String {
        if (instrumental) return "Instrumental;1.00;by=$by"
        val parts = arrayListOf(mood.ifEmpty { "None" }, fmt(confidence))
        if (second.isNotEmpty()) parts += "second=$second"
        parts += "valence=" + fmt(valence)
        parts += "energy=" + fmt(energy)
        if (themes.isNotEmpty()) parts += "themes=" + themes.joinToString(",")
        parts += "by=$by"
        if (version > 1) parts += "v=$version"
        val s = MoodRules.ALL.mapNotNull { m -> scores[m]?.takeIf { it >= MIN_SCORE }?.let { "$m:" + fmt(it) } }
        if (s.isNotEmpty()) parts += "scores=" + s.joinToString(",")
        if (meaning.isNotBlank()) parts += "meaning=" + cleanMeaning(meaning)
        return parts.joinToString(";")
    }

    /** The points the words give the moods, for [MoodRules.judge]: every mood by its score (the surer the reading, the
     *  more they count); an older reading without scores: its mood by how sure, its second a bit less. */
    fun points(): Map<String, Double> {
        if (instrumental || confidence <= 0) return emptyMap()
        if (scores.isNotEmpty()) {
            val k = 0.6 + 0.4 * confidence.coerceIn(0.0, 1.0)
            return scores.filter { it.key in MoodRules.ALL && it.value >= 0.1 }.mapValues { it.value.coerceIn(0.0, 1.0) * k }
        }
        if (mood !in MoodRules.ALL) return emptyMap()
        val out = linkedMapOf(mood to confidence)
        if (second in MoodRules.ALL && second != mood) out[second] = 0.6 * confidence
        return out
    }

    companion object {
        /** Scores under this are left out of the tag (and of readings): they say nothing. */
        const val MIN_SCORE = 0.05

        private fun fmt(v: Double) = String.format(Locale.ROOT, "%.2f", v)
        private fun round(v: Double) = Math.round(v * 100) / 100.0

        /** A meaning as the tag can hold it: one line, no ";" (the tag's separator), not too long. */
        fun cleanMeaning(s: String): String = s.replace(Regex("[;\\r\\n\\t]+"), ",").replace(Regex("\\s+"), " ").trim().take(140)

        /** A reading from every mood's score (the AI's answer, or the word reader's): its mood is the top one when it says
         *  enough (0.3), its second the next when that's close (0.6 of it). confidence: as given, else from how high the
         *  top score is. */
        fun fromScores(raw: Map<String, Double>, valence: Double, energy: Double, themes: List<String>, by: String,
                       confidence: Double? = null, meaning: String = ""): LyricsReading {
            val scores = raw.filterKeys { it in MoodRules.ALL }.mapValues { round(it.value.coerceIn(0.0, 1.0)) }.filterValues { it >= MIN_SCORE }
            val ranked = scores.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { MoodRules.ALL.indexOf(it.key) })
            val top = ranked.getOrNull(0)?.takeIf { it.value >= 0.3 }
            val second = ranked.getOrNull(1)?.takeIf { top != null && it.value >= 0.3 && it.value >= 0.6 * top.value }
            val sure = (confidence ?: (0.3 + 0.6 * (top?.value ?: 0.0))).coerceIn(0.0, 1.0)
            return LyricsReading(top?.key.orEmpty(), second?.key.orEmpty(), round(sure), round(valence.coerceIn(-1.0, 1.0)),
                round(energy.coerceIn(0.0, 1.0)), themes, by, scores = scores, meaning = cleanMeaning(meaning))
        }

        /** A MOOD_LYRICS tag read back (see [tag]); null if it isn't one. A tag without "v=" is the first reader's (version 1). */
        fun parse(text: String?): LyricsReading? {
            val parts = text?.trim()?.split(';')?.map { it.trim() }?.takeIf { it.size >= 2 } ?: return null
            val first = parts[0]
            val sure = parts[1].toDoubleOrNull() ?: return null
            val kv = parts.drop(2).filter { '=' in it }.associate { it.substringBefore('=').trim().lowercase() to it.substringAfter('=').trim() }
            val by = kv["by"].orEmpty().ifEmpty { "words" }
            if (first.equals("Instrumental", true)) return LyricsReading(by = by, instrumental = true, confidence = 1.0)
            val mood = MoodRules.ALL.firstOrNull { it.equals(first, true) } ?: if (first.equals("None", true)) "" else return null
            val scores = kv["scores"].orEmpty().split(',').mapNotNull { p ->
                val m = MoodRules.ALL.firstOrNull { it.equals(p.substringBefore(':').trim(), true) } ?: return@mapNotNull null
                val v = p.substringAfter(':', "").trim().toDoubleOrNull() ?: return@mapNotNull null
                m to v.coerceIn(0.0, 1.0)
            }.toMap()
            return LyricsReading(mood, MoodRules.ALL.firstOrNull { it.equals(kv["second"], true) }.orEmpty(), sure.coerceIn(0.0, 1.0),
                kv["valence"]?.toDoubleOrNull()?.coerceIn(-1.0, 1.0) ?: 0.0, kv["energy"]?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 0.5,
                kv["themes"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }, by,
                scores = scores, version = kv["v"]?.toIntOrNull() ?: 1, meaning = kv["meaning"].orEmpty())
        }
    }
}

/** Reads a song's mood from its words, on the device, with nothing sent anywhere. It reads lines and phrases, not only
 *  single words:
 *  - phrases first (our own list, below): "wash my blues away" is relief, not sadness; "can't stop smiling" is glad;
 *    "break my heart" is sad. A phrase's words aren't counted again one by one.
 *  - then single words (our own word lists): "not", "never", "don't"… turn the next few words around (within the same
 *    part of the line), "so", "really"… make the next one count more. "Blue" and "blues" lean sad only when nothing
 *    glad is said around them.
 *  - a line sung again (a chorus) counts more than a line sung once, but not in full each time; a line after "but now"
 *    or with "anymore" counts more, a line about "used to" less (it's the past).
 *  - a mood said across many lines counts more than one line full of it (each line can only give so much).
 *  - a song telling someone else's story (he, she, they) is read a bit less surely than one about "I" and "you".
 *  Every mood gets a score (0–1), and the words' valence and energy follow from them. */
object LyricsMood {
    /** The reader's version, kept in the tag: readings by an older one are read again. */
    const val VERSION = 2

    /** Where each mood sits: (valence -1…1, energy 0…1), for the words' valence and energy. */
    private val PLACE = mapOf(
        "Happy" to (0.8 to 0.65), "Energetic" to (0.3 to 0.9), "Party" to (0.6 to 0.9), "Hype" to (0.3 to 0.95),
        "Chill" to (0.4 to 0.25), "Calm" to (0.35 to 0.1), "Dreamy" to (0.3 to 0.3), "Focus" to (0.1 to 0.4),
        "Romantic" to (0.6 to 0.4), "Sad" to (-0.8 to 0.3), "Dark" to (-0.6 to 0.55), "Intense" to (-0.5 to 0.95))

    private val GLAD = setOf("Happy", "Party", "Chill", "Calm", "Romantic", "Energetic")
    private val GLOOMY = setOf("Sad", "Dark", "Intense")

    /** Our own word lists: word to how much it leans to the mood (1: plainly; less: a hint). Whole words, lower case,
     *  without apostrophes ("dont"); a plural or -ing/-ed form finds its word too. */
    private fun lex(vararg words: Pair<String, Double>) = words.toList()
    private val MOODS: Map<String, List<Pair<String, Double>>> = mapOf(
        "Happy" to lex("happy" to 1.0, "happiness" to 1.0, "smile" to 1.0, "laugh" to 0.9, "laughter" to 0.9, "sunshine" to 1.0,
            "sunny" to 0.8, "joy" to 1.0, "joyful" to 1.0, "glad" to 0.8, "bright" to 0.5, "wonderful" to 0.8, "shine" to 0.5,
            "lucky" to 0.7, "blessed" to 0.6, "fun" to 0.7, "cheer" to 0.8, "grin" to 0.8, "delight" to 0.9, "hope" to 0.5,
            "hopeful" to 0.7, "alright" to 0.4, "good" to 0.3, "great" to 0.4, "beautiful" to 0.4, "paradise" to 0.7, "free" to 0.5,
            "celebrate" to 0.6, "golden" to 0.4, "yay" to 0.8, "sing" to 0.4, "sweet" to 0.3, "summer" to 0.5, "best" to 0.3,
            "thankful" to 0.8, "grateful" to 0.8, "proud" to 0.5, "rainbow" to 0.7, "playful" to 0.8, "carefree" to 1.0,
            "escape" to 0.4, "relief" to 0.7, "freedom" to 0.6, "vacation" to 0.8, "holiday" to 0.6, "sunlight" to 0.6,
            "laughing" to 0.9, "fine" to 0.2),
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
            "beach" to 0.7, "sunday" to 0.6, "sip" to 0.6, "cruise" to 0.7, "mellow" to 0.9, "wave" to 0.4, "unwind" to 1.0,
            "coffee" to 0.5, "smoke" to 0.4, "laid" to 0.5, "hammock" to 1.0, "sunset" to 0.6, "porch" to 0.6, "afternoon" to 0.5,
            "cool" to 0.4, "slowly" to 0.4, "kick" to 0.2, "smooth" to 0.6, "drift" to 0.3, "lounge" to 0.8, "sand" to 0.6,
            "shore" to 0.6, "tide" to 0.5, "island" to 0.7, "tropical" to 0.8, "coast" to 0.5, "boat" to 0.4, "palm" to 0.6),
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
            "regret" to 0.8, "empty" to 0.8, "cold" to 0.35, "rain" to 0.4, "grey" to 0.5, "gray" to 0.5,
            "sorrow" to 1.0, "grief" to 1.0, "mourn" to 1.0, "funeral" to 1.0, "dying" to 0.6, "apart" to 0.7, "leaving" to 0.6,
            "fade" to 0.6, "memories" to 0.6, "memory" to 0.5, "scars" to 0.6, "wound" to 0.6, "numb" to 0.7, "drown" to 0.6,
            "hollow" to 0.8, "ache" to 0.8, "weep" to 1.0, "tired" to 0.5, "sigh" to 0.6, "farewell" to 0.8, "lonesome" to 1.0,
            "depressed" to 1.0, "misery" to 1.0, "blame" to 0.4, "ended" to 0.5, "falling" to 0.25, "used" to 0.15, "wish" to 0.3,
            "longing" to 0.8, "yearn" to 0.8, "without" to 0.2, "worries" to 0.4, "troubles" to 0.5, "trouble" to 0.3, "worry" to 0.4),
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
        for ((mood, ws) in MOODS) for ((w, v) in ws) if (v > 0) m.getOrPut(w) { ArrayList() } += mood to v
    }

    /** Words that turn the next few words around. */
    private val NOT = setOf("not", "no", "never", "dont", "cant", "wont", "aint", "isnt", "wasnt", "didnt", "couldnt", "wouldnt",
        "shouldnt", "doesnt", "arent", "werent", "nobody", "nothing", "nowhere", "neither", "nor", "cannot", "hardly",
        "barely", "havent", "hasnt", "nah", "nope", "stop", "quit")

    /** Words that make the next word count more. */
    private val MORE = setOf("so", "very", "really", "too", "totally", "completely", "truly", "deeply", "extremely", "super",
        "absolutely", "utterly", "such", "always", "endlessly")

    /** "Blue" and "blues": sad only when the line says nothing glad (see [read]). */
    private val BLUE = setOf("blue", "blues")

    /** Who's singing about whom: "I" and "you" (the singer's own feelings) or he, she and they (a story). */
    private val FIRST = setOf("i", "im", "ive", "ill", "id", "me", "my", "mine", "myself", "we", "us", "our", "you", "your", "youre", "yours")
    private val THIRD = setOf("he", "she", "they", "him", "her", "his", "them", "their", "hes", "shes", "theyre", "theyd", "hed", "shed")

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
        "summer" to setOf("summer", "sun", "sunshine", "beach", "july", "ocean", "waves", "sand", "shore", "tide"),
        "friends" to setOf("friends", "friend", "homies", "crew", "squad", "gang", "brother", "brothers"),
        "rebellion" to setOf("fight", "riot", "rules", "rebel", "war", "revolution", "system", "against"),
        "loneliness" to setOf("alone", "lonely", "loneliness", "nobody", "empty", "lonesome"),
        "hope" to setOf("hope", "tomorrow", "someday", "dream", "believe", "light", "rise"),
        "success" to setOf("boss", "king", "winning", "win", "top", "crown", "champion", "made", "fame"),
    )

    /** A phrase: a pattern over a part of a line (its words lower case, without apostrophes, one space between) and the
     *  moods it says. Phrases are tried in order and a word goes to one phrase at most, so the relief phrases come
     *  before the sad ones that share words ("wash my blues away" before "the blues"). Our own list, written for this. */
    private class Phrase(pattern: String, val moods: Map<String, Double>) { val re = Regex("\\b(?:$pattern)\\b") }
    private fun ph(pattern: String, vararg moods: Pair<String, Double>) = Phrase(pattern, moods.toMap())
    private const val POSS = "(?:my|the|our|your|his|her|their|all|those|these|all my|all the|all your)"
    private val PHRASES: List<Phrase> = listOf(
        // Relief and escape: troubles going away, tears ending, feeling good.
        ph("wash(?:es|ed|ing)? (?:$POSS )?(?:blues|worries|troubles|cares|sorrows|pain) (?:away|off)", "Happy" to 1.0, "Chill" to 0.6),
        ph("(?:blues|worries|troubles|cares|sorrows) (?:away|behind|gone|melt away|fade away|drift away)", "Happy" to 0.9, "Chill" to 0.5),
        ph("(?:leave|left|leaving|forget|forgot|forgetting) (?:$POSS )?(?:blues|worries|troubles|cares|problems)(?: behind| at home| at the door)?", "Happy" to 0.8, "Chill" to 0.6),
        ph("no more (?:tears|crying|sorrow|pain|heartache|rain|worries|blues|sadness|lonely nights)", "Happy" to 0.9, "Calm" to 0.3),
        ph("(?:dry|dried|wipe|wiped|drying|wiping) (?:$POSS )?(?:eyes|tears)", "Happy" to 0.5, "Calm" to 0.4),
        ph("(?:cant|cannot|can not|wont|couldnt) stop (?:smiling|laughing|grinning|singing)", "Happy" to 1.1),
        ph("(?:cant|cannot|can not|wont|couldnt) stop (?:dancing|moving|partying|jumping)", "Party" to 1.0, "Energetic" to 0.5, "Happy" to 0.3),
        ph("(?:never|not ever) (?:felt|been|feel) (?:so|this|more) (?:good|happy|alive|free|right|high)", "Happy" to 1.1, "Energetic" to 0.3),
        ph("(?:feel|feels|feeling|felt|fell) (?:so |like |really )?(?:alive|brand new|on fire)", "Energetic" to 0.9, "Happy" to 0.6),
        ph("(?:feel|feels|feeling|felt) (?:so |really |pretty )?(?:good|great|fine|free|happy|right|amazing|lucky)", "Happy" to 1.0),
        ph("nothing to lose", "Energetic" to 0.7, "Intense" to 0.3, "Happy" to 0.2),
        ph("no (?:worries|problems|regrets|cares|stress)", "Happy" to 0.7, "Chill" to 0.6),
        ph("(?:not a|no) care in the world|without a care", "Happy" to 0.8, "Chill" to 0.8),
        ph("(?:dont|do not|never) (?:worry|cry|be sad|you cry|you worry)", "Happy" to 0.5, "Calm" to 0.5),
        ph("(?:everything|it|things|we) (?:will be|is gonna be|s gonna be|is going to be|ll be|gonna be|will turn out) (?:ok|okay|alright|all right|fine)", "Happy" to 0.7, "Calm" to 0.7),
        ph("good (?:times|vibes|day|days|life|old days|feeling|feelings)", "Happy" to 1.0),
        ph("(?:the )?(?:sun|sunshine) (?:is |s )?(?:shining|coming out|comes out|is out|on my face|on our faces)", "Happy" to 0.9, "Chill" to 0.3),
        ph("(?:top|on top) of the world|on cloud nine|over the moon|walking on (?:air|sunshine)", "Happy" to 1.1),
        ph("(?:wanna|want to|gonna|going to|lets|let us|we) (?:celebrate|party|dance)(?: all night| tonight| till dawn| till the morning)?", "Party" to 1.0, "Happy" to 0.3),
        ph("all night long|till the sun comes up|until the sun comes up|till dawn", "Party" to 0.7, "Energetic" to 0.3),
        ph("(?:raise|lift|put) (?:your|our|a|my) (?:glass|glasses|hands|cups|drinks)(?: up)?", "Party" to 1.0),
        ph("turn (?:it|the music|the volume|the bass|this) up", "Party" to 0.8, "Energetic" to 0.5),
        ph("on the dance ?floor", "Party" to 1.0),
        ph("(?:toes|feet) in the (?:sand|water)|sand between (?:my|our|your) toes", "Chill" to 1.0, "Happy" to 0.5),
        ph("(?:on|at|to|down to|by) the (?:beach|shore|coast|sea|seaside|lake)", "Chill" to 0.8, "Happy" to 0.4),
        ph("(?:island|ocean|sea|tropical|summer) (?:breeze|time|life|air|days)", "Chill" to 0.9, "Happy" to 0.4),
        ph("(?:take|taking|takin) it (?:easy|slow)", "Chill" to 1.0),
        ph("(?:kick|kicking|kickin|lay|laying|layin|lie|lying|sit|sitting) back", "Chill" to 0.9),
        ph("(?:ride|riding|cruise|cruising|drive|driving|roll|rolling) (?:with the windows down|down the coast|by the sea|in the sun)", "Chill" to 0.7, "Happy" to 0.5),
        ph("(?:get|got|run|ran|sail|sailing|fly|flying|drive|driving|break|breaking|getting|running) away from (?:it all|the city|the world|everything)", "Happy" to 0.5, "Chill" to 0.5),
        ph("blue (?:sky|skies|water|waters|ocean|sea|lagoon|horizon)", "Happy" to 0.5, "Chill" to 0.5),
        ph("blue (?:eyes|jeans|dress|suede|car|light|lights)", "Romantic" to 0.1),
        // Love.
        ph("(?:fall|falling|fell|fallen) in love", "Romantic" to 1.2, "Happy" to 0.3),
        ph("(?:hold|holding|held) (?:me|you|us) (?:close|tight|closer)", "Romantic" to 1.0),
        ph("(?:be|stay) mine|(?:i|ill) (?:always )?love you|(?:make|making) love|meant to be", "Romantic" to 1.0),
        ph("slow danc(?:e|es|ing|ed)", "Romantic" to 0.7, "Calm" to 0.2),
        // Sadness, loss and longing.
        ph("(?:break|breaks|breaking|broke|broken) (?:my|your|our|his|her) heart|broken[ -]?hearted|heart (?:is |s )?broken", "Sad" to 1.2, "Romantic" to 0.2),
        ph("(?:tears|teardrops) (?:fall|falling|fell|on my|down my|in my)", "Sad" to 1.0),
        ph("cry(?:ing)? (?:myself )?to sleep", "Sad" to 1.2),
        ph("(?:miss|missing|missed) (?:you|her|him|them|us|home)", "Sad" to 0.8, "Romantic" to 0.3),
        ph("(?:left|leave|leaving|walked out on|gave up on) me", "Sad" to 0.8),
        ph("(?:youre|shes|hes|theyre|you are|she is|he is|they are) gone", "Sad" to 0.9),
        ph("(?:feel|feels|feeling|felt) (?:so |really |kinda )?(?:blue|down|low|empty|numb|lonely|lost|hollow|broken)|im (?:so )?(?:lonely|empty|numb|hollow|broken|blue)", "Sad" to 1.0),
        ph("(?:sing|singing|sang|got|have|had|get|getting|gettin) the blues", "Sad" to 0.9),
        ph("(?:lost|losing|lose) (?:you|her|him|them|everything|it all|my way|my love)", "Sad" to 0.9),
        ph("(?:say|said|saying|wave|waved) goodbye", "Sad" to 0.8),
        ph("(?:fall|falling|fell|fallen|came|come|coming) apart", "Sad" to 0.9),
        ph("(?:nothing|no one|nobody) (?:left|to hold|cares|at home|by my side)", "Sad" to 0.8),
        ph("(?:wish|wishing|wished) (?:you|i|she|he|we) (?:were|was|could|had|would)", "Sad" to 0.6, "Romantic" to 0.2),
        ph("(?:never|not) (?:coming|come|came) (?:back|home)", "Sad" to 0.9),
        ph("(?:end|ending) of the (?:road|line)|too late", "Sad" to 0.5),
        ph("(?:lonely|empty|cold) (?:nights|night|bed|room|house|road)", "Sad" to 0.8),
        ph("(?:dead|empty|numb|cold) inside", "Sad" to 0.8, "Dark" to 0.5),
        ph("(?:cant|cannot|can not) (?:breathe|sleep|go on|let go|forget)", "Sad" to 0.6, "Intense" to 0.2),
        ph("in the (?:dark|darkness|shadows)", "Dark" to 0.6, "Sad" to 0.2),
        // Fight, drive and swagger.
        ph("(?:dont|never|wont) (?:give up|back down|surrender|stop believing|look back)", "Energetic" to 0.9, "Hype" to 0.3, "Happy" to 0.2),
        ph("(?:rise|rising|stand|standing) up", "Energetic" to 0.7, "Hype" to 0.3),
        ph("(?:pump|pumped|pumping) (?:it )?up", "Energetic" to 0.9, "Hype" to 0.6),
        ph("(?:go|going|goin) (?:hard|crazy|wild|insane)", "Hype" to 0.8, "Energetic" to 0.5),
        ph("(?:burn|burning|burned|tear|tearing) it (?:all )?down", "Intense" to 1.0),
        ph("(?:fight|fighting) back|out for blood", "Intense" to 0.9, "Energetic" to 0.3),
        ph("(?:lose|losing|lost) (?:my|your) mind|(?:out of|outta) (?:my|your) mind", "Intense" to 0.6, "Dark" to 0.3),
        ph("(?:count|counting|stack|stacking|make|making) (?:my |the |that |up )?(?:money|bands|racks|paper|bread)", "Hype" to 1.0),
        ph("(?:keep|keeping|stay|staying) (?:going|focused|moving|on track)", "Focus" to 0.6, "Energetic" to 0.4),
        // Calm and dreams.
        ph("(?:close|closing|shut) (?:my|your|our) eyes", "Dreamy" to 0.6, "Calm" to 0.5),
        ph("(?:drift|drifting|float|floating) (?:off|away|along)", "Dreamy" to 0.8, "Calm" to 0.5),
        ph("lost in (?:a |the |your |my )?(?:dream|dreams|stars|eyes|music|moment)", "Dreamy" to 1.0, "Romantic" to 0.3),
        ph("(?:in|of) (?:my|your|our) dreams", "Dreamy" to 0.8),
        ph("peace of mind", "Calm" to 1.0),
        ph("(?:slow|slowing) (?:it )?down", "Calm" to 0.7, "Chill" to 0.5),
    )

    /** Section names and timings, not words: "[Chorus]", "[00:12.34]". */
    private val NOISE = Regex("\\[[^\\]]*]|<\\d+:\\d+(?:\\.\\d+)?>")
    private val SECTION = Regex("^\\s*\\[([^\\]]*)]\\s*$")
    private val TOKEN = Regex("[\\p{L}']+")
    /** Where a line breaks into parts (a negation doesn't reach past one). */
    private val CLAUSE = Regex("[,.;:!?()\"“”…]+|\\s[-–—]+\\s")

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

    private fun tokens(text: String): List<String> = TOKEN.findAll(text).map { word(it.value) }.filter { it.isNotEmpty() }.toList()

    /** Whether the text is an instrumental's (no words, or only "Instrumental"). */
    fun isInstrumental(lyrics: String?): Boolean {
        val words = lyrics?.let { NOISE.replace(it, " ") }?.let { tokens(it) }.orEmpty()
        return words.isEmpty() || (words.size <= 3 && words.any { it == "instrumental" })
    }

    /** What one line says: points per mood (each mood at most [LINE_MOST], so one crowded line can't carry a song). */
    private fun readLine(clauses: List<List<String>>): Map<String, Double> {
        val pts = HashMap<String, Double>()
        var glad = 0.0; var gloomy = 0.0
        var blues = 0
        fun add(mood: String, v: Double, turned: Boolean) {
            if (turned) {
                // "not happy": takes from Happy, and a glad word turned around leans sad; "not sad" leans a little glad.
                pts.merge(mood, -0.5 * v, Double::plus)
                if (mood == "Happy" || mood == "Romantic" || mood == "Calm") pts.merge("Sad", 0.3 * v, Double::plus)
                if (mood == "Sad") pts.merge("Happy", 0.2 * v, Double::plus)
            } else {
                pts.merge(mood, v, Double::plus)
                if (mood in GLAD) glad += v else if (mood in GLOOMY) gloomy += v
            }
        }
        for (ws in clauses) {
            if (ws.isEmpty()) continue
            val used = BooleanArray(ws.size)
            // Phrases first.
            val text = ws.joinToString(" ")
            val starts = IntArray(ws.size)
            var o = 0
            for (i in ws.indices) { starts[i] = o; o += ws[i].length + 1 }
            fun at(c: Int): Int { var i = 0; while (i + 1 < ws.size && starts[i + 1] <= c) i++; return i }
            for (p in PHRASES) for (m in p.re.findAll(text)) {
                if (m.value.isBlank()) continue
                val a = at(m.range.first); val b = at(m.range.last)
                if ((a..b).any { used[it] }) continue
                for (i in a..b) used[i] = true
                // A negation just before turns the phrase around ("didn't break my heart").
                val turned = (maxOf(0, a - 2) until a).any { !used[it] && ws[it] in NOT && ws[it] != "stop" && ws[it] != "quit" }
                for ((mood, v) in p.moods) add(mood, PHRASE_K * v, turned)
            }
            // Then single words.
            var boost = 1.0; var boostLeft = 0
            var notLeft = 0
            val inClause = HashSet<String>()   // a word said twice ("alone, alone") counts once
            for ((i, w) in ws.withIndex()) {
                if (used[i]) { notLeft = 0; boostLeft = 0; continue }
                if (w in NOT) { notLeft = if (notLeft > 0 && (w == "stop" || w == "quit")) 0 else 3; continue }   // "can't stop": not turned
                if (w in MORE) { boost = 1.5; boostLeft = 2; continue }
                if (w in BLUE) { if (notLeft == 0 && inClause.add(w)) blues++; if (notLeft > 0) notLeft--; continue }
                val hits = find(w)
                if (hits != null && inClause.add(w)) {
                    val turned = notLeft > 0
                    val k = if (boostLeft > 0) boost else 1.0
                    for ((mood, v) in hits) add(mood, v * k, turned)
                    boostLeft = 0
                }
                if (notLeft > 0) notLeft--
                if (boostLeft > 0) boostLeft--
            }
        }
        // "Blue" on its own: a little sad, unless the line around it is glad.
        if (blues > 0 && glad <= gloomy) pts.merge("Sad", 0.4 * minOf(blues, 2), Double::plus)
        for (k in pts.keys.toList()) pts[k] = pts.getValue(k).coerceAtMost(LINE_MOST)
        return pts
    }

    private const val PHRASE_K = 1.3     // a phrase says more than a word
    private const val LINE_MOST = 1.6    // the most one line gives a mood

    private val PAST = Regex("\\b(?:used to|back then|there was a time|once upon|in the old days|i was once)\\b")
    private val NOW = Regex("\\b(?:but now|now i|now im|now were|these days|anymore|no longer|from now on|not anymore|any more)\\b")
    private val CHORUS = Regex("chorus|hook|refrain", RegexOption.IGNORE_CASE)

    /** The mood of the words (see the object's note): every mood's score, its mood and second, valence, energy, themes. */
    fun read(lyrics: String?): LyricsReading {
        if (isInstrumental(lyrics)) return LyricsReading(instrumental = true, confidence = 1.0)
        // The lines, in order: each one's words in parts (split at commas and the like), its section, how often it comes.
        class Line(val key: String, val clauses: List<List<String>>, val chorus: Boolean, var weight: Double)
        val lines = ArrayList<Line>()
        var inChorus = false
        var nowLeft = 0
        var first = 0; var third = 0
        for (raw in lyrics!!.lines()) {
            val section = SECTION.find(raw)
            if (section != null) { inChorus = CHORUS.containsMatchIn(section.groupValues[1]); continue }
            val clean = NOISE.replace(raw, " ")
            val clauses = clean.split(CLAUSE).map { tokens(it) }.filter { it.isNotEmpty() }
            if (clauses.isEmpty()) continue
            val all = clauses.flatten()
            for (w in all) { if (w in FIRST) first++ else if (w in THIRD) third++ }
            val key = all.joinToString(" ")
            // The past counts less, a turn to now more ("used to cry, but now I smile"), and the line after that too.
            var w = 1.0
            if (PAST.containsMatchIn(key)) w *= 0.6
            if (NOW.containsMatchIn(key)) { w *= 1.4; nowLeft = 1 } else if (nowLeft > 0) { w *= 1.2; nowLeft-- }
            lines += Line(key, clauses, inChorus, w)
        }
        if (lines.isEmpty()) return LyricsReading(instrumental = true, confidence = 1.0)
        // Each different line once, weighed by how often it comes: a chorus counts more than a passing line, not 10 times.
        val times = HashMap<String, Int>()
        for (l in lines) times.merge(l.key, 1, Int::plus)
        val total = HashMap<String, Double>()
        val linesWith = HashMap<String, Double>()   // how many different lines say each mood
        val themeHits = HashMap<String, Double>()
        val done = HashSet<String>()
        for (l in lines) {
            if (!done.add(l.key)) continue
            val n = times.getValue(l.key)
            val repeat = 1 + 0.45 * (minOf(n, 5) - 1)
            val w = l.weight * repeat * (if (l.chorus && n == 1) 1.25 else 1.0)
            for (word in l.clauses.flatten().toSet()) for ((theme, ws) in THEMES) if (word in ws) themeHits.merge(theme, repeat, Double::plus)
            for ((mood, v) in readLine(l.clauses)) {
                total.merge(mood, v * w, Double::plus)
                if (v >= 0.3) linesWith.merge(mood, l.weight.coerceAtMost(1.0), Double::plus)
            }
        }
        val unique = done.size
        // A story about someone else (he, she, they far more than I and you): read a little less surely.
        val story = third >= 6 && third > 1.5 * first
        // Each mood's score: how much is said (saturating), and across how many lines (a theme held through the song).
        val spread = maxOf(3.0, 0.3 * unique)
        val scores = HashMap<String, Double>()
        for (m in MoodRules.ALL) {
            val t = total[m] ?: 0.0
            if (t <= 0) continue
            val cover = minOf(1.0, (linesWith[m] ?: 0.0) / spread)
            var s = (1 - exp(-t / 3.5)) * (0.45 + 0.55 * cover)
            if (story) s *= 0.9
            if (s >= LyricsReading.MIN_SCORE) scores[m] = s
        }
        val themes = themeHits.entries.filter { it.value >= 2.0 }.sortedByDescending { it.value }.take(3).map { it.key }
        // Valence and energy: where the moods said sit, by how much each is said.
        var vSum = 0.0; var eSum = 0.0; var wSum = 0.0
        for ((m, s) in scores) { val (va, en) = PLACE.getValue(m); vSum += va * s; eSum += en * s; wSum += s }
        val valence = if (wSum > 0) vSum / wSum else 0.0
        val energy = if (wSum > 0) eSum / wSum else 0.5
        val ranked = scores.values.sortedDescending()
        val s1 = ranked.getOrElse(0) { 0.0 }; val s2 = ranked.getOrElse(1) { 0.0 }
        val said = total.values.filter { it > 0 }.sum()
        val lead = if (s1 > 0) (s1 - s2) / s1 else 0.0
        var sure = (1 - exp(-said / 6)) * (0.6 + 0.4 * lead) * (if (story) 0.8 else 1.0)
        // Too little said for a mood: a low, capped confidence (the sound decides).
        if (said < 1.2 || s1 < 0.3) sure = minOf(sure, 0.2)
        return LyricsReading.fromScores(scores, valence, energy, themes, "words", sure.coerceIn(0.0, 0.95))
    }
}
