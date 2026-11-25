package org.omarchy.music

/** Sound presets of yours and headphones corrections, as things you keep: a preset saved from the bands, imported
 *  from a file or deleted; the Sound and the correction exported; an AutoEq correction added, and every one AutoEq
 *  has for a pair of headphones collected; a new output's own starting preset. The equalizer itself (what plays,
 *  each output's settings) stays with [Music], which keeps the names the screens call and hands on. */
object SoundPresets {
    private val eq get() = Music.eq
    private val ctx get() = Music.ctx
    private val apiKeys get() = Music.apiKeys
    private val CAR get() = Music.CAR
    private fun toast(text: String) = Music.toast(text)
    private fun <T> bg(work: () -> T, done: (T?, Throwable?) -> Unit = { _, _ -> }) = Music.bg(work, done)
    private fun setEq(f: (EqSettings) -> EqSettings) = Music.setEq(f)
    private fun setEqPreset(key: String) = Music.setEqPreset(key)
    private fun setEqHeadphones(key: String) = Music.setEqHeadphones(key)

    /** What a preset of yours is kept under: its name, tidied, with dashes ("Late Night!" is "late-night"); "" when
     *  the name has no letters or digits (then it gets one made up). */
    fun presetKey(name: String): String = norm(name).replace(" ", "-")

    /** What an AutoEq correction is kept under: the headphones and who measured them ("ae:hifiman-sundara-oratory1990"),
     *  so the same headphones from two sources are two corrections. */
    fun correctionKey(e: AutoEq.Entry): String = "ae:" + norm("${e.name} ${e.source}").replace(" ", "-")

    /** The name for an EQ or preset taken from a file: the file's name without its ending and without the
     *  "ParametricEQ" or "GraphicEQ" other apps put on the end; fallback when that leaves nothing (or it has no name). */
    fun nameFromFile(file: String?, fallback: String): String =
        (file ?: fallback).substringBeforeLast('.').replace(Regex("\\s*(ParametricEQ|GraphicEQ)$"), "").trim().ifEmpty { fallback }

    /** Keep an AutoEq profile among your headphones and use it. */
    /** An EQ file of yours (AutoEq, Squiglink, Wavelet, Equalizer APO), picked: among your headphones, and used. */
    fun importEqFile(uri: String) {
        bg({
            val text = ctx.openIn(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            val name = nameFromFile(ctx.displayName(uri), "My EQ")
            name to AutoEq.parseFile(text)
        }) { r, err ->
            if (r != null) addAutoEq(AutoEq.Entry(r.first, "imported"), r.second)
            else toast("Couldn't import it: ${err?.message?.take(80)}")
        }
    }

    /** Save as preset…: the bands as they are now, as a Sound preset of yours named name (used straight away). */
    fun savePreset(name: String, bands: List<Double> = eq.bands, count: Int = eq.count) {
        val n = name.trim().ifEmpty { return }
        val key = presetKey(n).ifEmpty { randomHex(4) }
        val ten = Eq.resample(bands, Eq.bandsFor(count), 10)
        setEq { it.copy(myPresets = it.myPresets + (key to MyPreset(n, ten))) }
        setEqPreset("my:$key")
        toast("“$n” saved among your Sound presets")
    }

    /** Save over: the bands as they are now put into the Sound preset of yours they were changed from (eq.soundFrom),
     *  its name kept, and it's the one in use again. */
    fun overwritePreset() {
        val k = eq.soundFrom
        val p = eq.myPresets[k] ?: return
        val ten = Eq.resample(eq.bands, Eq.bandsFor(eq.count), 10)
        setEq { it.copy(myPresets = it.myPresets + (k to p.copy(bands = ten))) }
        setEqPreset("my:$k")
        toast("Saved over “${p.name}”")
    }

    /** Edit: a Sound preset of yours renamed (it keeps its place, and stays in use if it was). */
    fun renamePreset(key: String, name: String) {
        val k = key.removePrefix("my:")
        val n = name.trim().ifEmpty { return }
        val p = eq.myPresets[k] ?: return
        setEq { it.copy(myPresets = it.myPresets + (k to p.copy(name = n))) }
        toast("Renamed “$n”")
    }

    fun deletePreset(key: String) {
        val k = key.removePrefix("my:")
        val name = eq.myPresets[k]?.name ?: return
        setEq { it.copy(myPresets = it.myPresets - k, preset = if (it.preset == key) "custom" else it.preset,
            soundFrom = if (it.soundFrom == k) "" else it.soundFrom) }
        toast("“$name” deleted")
    }

    /** Import a Sound preset: a GraphicEQ (Wavelet, Equalizer APO, AutoEq) or a parametric file, read at the ten bands. */
    fun importPresetFile(uri: String) {
        bg({
            val text = ctx.openIn(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            val name = nameFromFile(ctx.displayName(uri), "My preset")
            name to Eq.curveToBands(AutoEq.parseFile(text))
        }) { r, err ->
            if (r != null) savePreset(r.first, r.second, 10) else toast("Couldn't import it: ${err?.message?.take(80)}")
        }
    }

    /** Export the Sound as it is now (a GraphicEQ file other EQ apps read, and this one imports). */
    fun exportPreset(uri: String) {
        val text = Eq.graphicEqText(Eq.bandsFor(eq.count), eq.bands)
        bg({ ctx.openOut(uri)!!.use { it.write(text.toByteArray()) } }) { _, err ->
            toast(if (err != null) "Couldn't export it: ${err.message?.take(80)}" else "Sound exported")
        }
    }

    /** Export the headphones correction in use (a ParametricEQ file: AutoEq's and Equalizer APO's format). */
    fun exportCorrection(uri: String) {
        val f = Eq.headphones(eq)[eq.headphones]?.second.orEmpty()
        if (f.isEmpty()) { toast("No headphones correction is in use to export"); return }
        val text = Eq.parametricText(f)
        bg({ ctx.openOut(uri)!!.use { it.write(text.toByteArray()) } }) { _, err ->
            toast(if (err != null) "Couldn't export it: ${err.message?.take(80)}" else "Correction exported")
        }
    }

    /** Collect: every source AutoEq has for these headphones (oratory1990, Rtings, crinacle, Super Review…), each added
     *  among your corrections so you can compare them; the closest match used unless you've picked one for them. */
    fun collectCorrections(dev: String, quiet: Boolean = false, done: (Int) -> Unit = {}) {
        val api = apiKeys["autoeq_api"].orEmpty()
        bg({
            val all = if (api.isNotEmpty()) AutoEq.apiSearch(api, dev) else AutoEq.index(ctx)
            val best = AutoEq.matchDevice(listOf(dev), all) ?: (if (api.isEmpty()) AutoEq.search(all, dev, 1).firstOrNull() else all.firstOrNull())
                ?: return@bg emptyList<Pair<AutoEq.Entry, List<Filter>>>()
            val same = all.filter { norm(it.name) == norm(best.name) }.ifEmpty { listOf(best) }
            (listOf(best) + same.filter { it != best }).mapNotNull { e -> runCatching { e to AutoEq.profile(e, api) }.getOrNull() }
        }) { got, err ->
            if (got.isNullOrEmpty()) {
                if (!quiet) toast(if (err != null) "AutoEq couldn't be reached: ${err.message?.take(80)}" else "AutoEq has nothing for “$dev”")
                done(0); return@bg
            }
            val keys = got.map { (e, _) -> correctionKey(e) }
            setEq { s -> s.copy(profiles = s.profiles + got.zip(keys).map { (p, k) ->
                k to AutoEq.profileOf("${p.first.name} · ${p.first.source.ifEmpty { "AutoEq" }}", p.second) }) }
            // Kept among your corrections, not used: you pick one (under Headphones) if you want it
            toast("${got[0].first.name}: ${got.size} correction${if (got.size != 1) "s" else ""} collected (" +
                got.joinToString(", ") { it.first.source.ifEmpty { "AutoEq" } } + "): pick one under Headphones to use it")
            done(got.size)
        }
    }

    /** A new output's own starting Sound preset, named after it (for every kind of music; tune it as you like). */
    internal fun addDevicePreset(o: Outputs.Out) {
        if (o.kind == "speaker" && o.key == "speaker" || o.key == CAR) return   // these have built-in ones
        val key = presetKey(o.label)
        if (key.isEmpty() || eq.myPresets.containsKey(key) || Eq.PRESETS.values.any { norm(it.first) == norm(o.label) }) return
        setEq { it.copy(myPresets = it.myPresets + (key to MyPreset(o.label, Eq.deviceCurve(o.kind)))) }
    }

    fun addAutoEq(e: AutoEq.Entry, filters: List<Filter>) {
        val key = correctionKey(e)
        setEq { it.copy(profiles = it.profiles + (key to AutoEq.profileOf("${e.name} · ${e.source.ifEmpty { "AutoEq" }}", filters))) }
        setEqHeadphones(key)
        toast("${e.name}: corrected with AutoEq (${e.source.ifEmpty { "AutoEq" }})")
    }
}
