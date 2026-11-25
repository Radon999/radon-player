package org.omarchy.music

/** The equalizer as you use it: its settings changed and kept (for each output its own, with when they last changed),
 *  what it plays right now (the output's settings, or the playing song's own on top), the switch when the sound goes
 *  to another output, a song's own EQ, Boost and Dual volume, Bass and Treble with bit-perfect, and ReplayGain's
 *  factor. The settings themselves ([Music.eq]) and what the screens read stay in [Music], which keeps the names the
 *  screens call and hands on. */
object EqControl {
    private fun toast(text: String) = Music.toast(text)
    private const val DUAL_VOLUME = "eq_dual_volume"

    // ---- the rules ----

    /** An output's name as shown, from its key. */
    fun outLabel(key: String) = when (key) { "speaker", "" -> "Built-in speaker"; "wired" -> "Headphones (jack)"
        "headset" -> "Headset (jack, with mic)"; Music.CAR -> "Chevrolet Colorado · Android Auto"; else -> key.substringAfter(':') }

    /** n with the output it is for keeping it as it is now, always (not only when the sound moves away), stamped at
     *  (when) if that changed anything: so a sync carries the latest, and never brings older ones back over yours. */
    fun kept(n: EqSettings, at: Long): EqSettings {
        if (!n.perOutput || n.output.isEmpty()) return n
        val old = n.outputs[n.output]
        val mine = n.toOutput(old?.label?.ifEmpty { null } ?: outLabel(n.output))
        return if (mine.sameAs(old)) n else n.copy(outputs = n.outputs + (n.output to mine.copy(at = at)))
    }

    /** What plays: the settings (eq), or with the playing song's own EQ for this output over them (song: its
     *  correction, Sound and Boost; the bass and treble, crossfeed, dither and the rest stay as they are). Dual volume
     *  off (dual false): a song with its own volume (gained) plays at that volume alone, without the Boost on top. */
    fun played(eq: EqSettings, song: OutputEq?, dual: Boolean, gained: Boolean): EqSettings {
        val e = if (song == null) eq else eq.copy(on = true, headphones = song.headphones, preset = song.preset, bands = song.bands,
            custom = song.custom, count = song.count, boost = song.boost)
        return if (!dual && gained) e.copy(boost = 0.0) else e
    }

    /** The sound moved from the output was to o: the settings with was's kept as they are, and o's own brought in: its
     *  default preset when it has one (each time it connects), else its EQ as you left it (a new output starts flat,
     *  with as many bands as you had). */
    fun moved(e: EqSettings, was: String, o: Outputs.Out): EqSettings {
        val saved = (if (was.isNotEmpty()) e.outputs + (was to e.toOutput(e.outputs[was]?.label ?: outLabel(was)).copy(at = e.outputs[was]?.at ?: 0)) else e.outputs)
        val defaultKey = e.outputDefaults[o.key]?.takeIf { it in e.outputPresets }
        val default = defaultKey?.let { e.outputPresets[it] }?.eq?.copy(at = saved[o.key]?.at ?: 0)
        val mine = default ?: saved[o.key] ?: OutputEq(o.label, count = e.count, bands = List(e.count) { 0.0 }, custom = List(e.count) { 0.0 })
        val from = if (defaultKey != null) e.outputFrom + (o.key to defaultKey) else e.outputFrom
        return e.copy(outputs = saved + (o.key to mine.copy(label = o.label)), outputFrom = from).withOutput(o.key, mine)
    }

    // ---- an output's own presets ----

    /** The presets saved for an output (its key), by name: (their outputPresets key, the preset). */
    fun presetsFor(e: EqSettings, out: String): List<Pair<String, OutputPreset>> =
        e.outputPresets.filter { it.value.output == out }.toList().sortedBy { it.second.name.lowercase() }

    /** The EQ of the output being set (the one picked at the top of the Equalizer) saved as a preset of its own, under
     *  name (one of that name already: replaced). asDefault: switched in each time the sound goes to that output. */
    fun saveOutputPreset(name: String, asDefault: Boolean) {
        val n = name.trim()
        if (n.isEmpty()) return
        set { e ->
            val out = e.output.ifEmpty { Music.outputNow.key }
            val key = e.outputPresets.entries.firstOrNull { it.value.output == out && it.value.name.equals(n, ignoreCase = true) }?.key
                ?: "$out|${System.currentTimeMillis().toString(36)}"
            e.copy(outputPresets = e.outputPresets + (key to OutputPreset(out, n, e.toOutput("").copy(at = 0))),
                outputDefaults = if (asDefault) e.outputDefaults + (out to key) else e.outputDefaults,
                outputFrom = e.outputFrom + (out to key))
        }
        toast("Saved “$n” for ${outLabel(Music.eq.output)}" + if (asDefault) ", its default" else "")
    }

    /** Save over: the output's EQ as it is now put into its preset key (the one it was put on from: [EqSettings.outputFrom]),
     *  its name and whether it's the default kept. */
    fun overwriteOutputPreset(key: String) {
        val p = Music.eq.outputPresets[key] ?: return
        set { e -> e.copy(outputPresets = e.outputPresets + (key to p.copy(eq = e.toOutput("").copy(at = 0))), outputFrom = e.outputFrom + (p.output to key)) }
        toast("Saved over “${p.name}”")
    }

    /** Edit: a preset of an output's renamed, and made its default or not. */
    fun editOutputPreset(key: String, name: String, asDefault: Boolean) {
        val n = name.trim().ifEmpty { return }
        set { e ->
            val p = e.outputPresets[key] ?: return@set e
            val defaults = if (asDefault) e.outputDefaults + (p.output to key)
                else if (e.outputDefaults[p.output] == key) e.outputDefaults - p.output else e.outputDefaults
            e.copy(outputPresets = e.outputPresets + (key to p.copy(name = n)), outputDefaults = defaults)
        }
    }

    /** A preset of an output's put on that output's EQ (heard straight away if it's playing there). */
    fun useOutputPreset(key: String) = set { e ->
        val p = e.outputPresets[key] ?: return@set e
        e.withOutput(p.output, p.eq).copy(outputFrom = e.outputFrom + (p.output to key))
    }

    /** An output's default preset (null: none, it keeps its EQ as you left it). */
    fun setOutputDefault(out: String, key: String?) = set { e ->
        e.copy(outputDefaults = if (key == null) e.outputDefaults - out else e.outputDefaults + (out to key))
    }

    fun deleteOutputPreset(key: String) = set { e ->
        e.copy(outputPresets = e.outputPresets - key, outputDefaults = e.outputDefaults.filterValues { it != key },
            outputFrom = e.outputFrom.filterValues { it != key })
    }

    /** Boost as it is kept: 0 to the most there is, in half decibels. */
    fun boost(db: Double): Double = kotlin.math.round(db.coerceIn(0.0, Eq.BOOST_MAX) * 2) / 2

    /** ReplayGain's factor for a song tagged db (and its peak, when tagged), at the levelling target. Making a quiet
     *  song louder stops where its loudest moment would reach full scale (with Clip guard, a little past it: the
     *  limiter catches the odd peak), and at +6 dB at most when the file doesn't say its peak; without Clip guard a
     *  song is never made louder. */
    fun rgFactor(db: Double, peak: Double?, target: Double, clipGuard: Boolean): Double {
        val g = Math.pow(10.0, (db + target + 18) / 20)
        if (g <= 1.0) return g
        if (!clipGuard) return 1.0
        val most = peak?.let { 1.12 / it } ?: 2.0   // up to 1 dB over the peak, or +6 dB untagged
        return g.coerceAtMost(most.coerceIn(1.0, 2.0))
    }

    // ---- the settings, changed ----

    fun set(f: (EqSettings) -> EqSettings) {
        Music.eq = kept(f(Music.eq), System.currentTimeMillis())
        apply()
        Music.saveState()
    }

    /** The playing song's own EQ on this output (Custom EQ…), if it has one: then it's what's heard. */
    fun songEqNow(t: Track? = Music.current()): OutputEq? = t?.let { if (Music.songEqsReady) Music.songEqs.get(it, Music.outputNow.key) else null }

    /** What the EQ plays, given to the player's processing ([played]). */
    fun apply() {
        val s = songEqNow()
        Music.songEqActive = s != null
        Music.eqProcessor.update(played(Music.eq, s, dualVolume(), Music.eqProcessor.songGain != 1.0))
    }

    /** Dual volume (Equalizer): on, the Boost and a song's own volume go together; off, a song with its own volume
     *  plays at just that one (the songs without one keep the Boost). */
    fun dualVolume() = Prefs.on(DUAL_VOLUME, true)
    fun setDualVolume(on: Boolean) { Prefs.set(DUAL_VOLUME, on); apply(); Music.musicVersion++ }

    /** Custom EQ…: e (null: take it away) for tracks, on the output playing now. */
    fun setSongEq(tracks: List<Track>, e: OutputEq?) {
        if (tracks.isEmpty()) return
        val out = Music.outputNow
        Music.songEqs.set(tracks, out.key, e?.copy(label = out.label))
        apply()
        val what = if (tracks.size == 1) "“${tracks[0].title}”" else "${tracks.size} songs"
        toast(if (e == null) "$what back to the usual EQ on ${out.label}" else "$what with its own EQ on ${out.label}")
        Music.musicVersion++
    }

    fun setOn(on: Boolean) = set { it.copy(on = on) }
    fun setHeadphones(key: String, remember: Boolean = true) = set {
        val out = Music.output
        it.copy(headphones = key, on = true, devices = if (remember && out.isNotEmpty()) it.devices + (out to key) else it.devices)
    }

    fun rgFactor(db: Double, peak: Double? = null): Double =
        rgFactor(db, peak, Prefs.num(Prefs.RG_TARGET, -18f).toDouble(), Prefs.on(Prefs.CLIP_GUARD))

    /** A Sound preset put on. One of yours is remembered (soundFrom) so the bands changed from it can be saved over it;
     *  a built-in one forgets it (Custom keeps it: those are the bands changed from it). */
    fun setPreset(key: String) = set { it.copy(preset = key, bands = Eq.presetOf(it, key, it.count) ?: it.custom.takeIf { c -> c.size == it.count }
        ?: List(it.count) { 0.0 }, on = true,
        soundFrom = if (key.startsWith("my:")) key.removePrefix("my:") else if (key == "custom") it.soundFrom else "") }
    fun setBand(i: Int, gain: Double) = set {
        if (i !in it.bands.indices) return@set it
        val b = it.bands.toMutableList().also { l -> l[i] = Eq.clamp(gain) }
        it.copy(bands = b, custom = b, preset = "custom", on = true)
    }
    /** 3, 5, 10, 15 or 31 bands: the curve you had, read onto the new ones. */
    fun setCount(n: Int) = set {
        if (n == it.count || n !in Eq.COUNTS) return@set it
        val from = Eq.bandsFor(it.count)
        it.copy(count = n, bands = Eq.resample(it.bands, from, n), custom = Eq.resample(it.custom.takeIf { c -> c.size == it.count } ?: it.bands, from, n))
    }
    /** A separate EQ for each output, or one for all. */
    fun setPerOutput(on: Boolean) = set { it.copy(perOutput = on) }
    /** Boost: dB louder than normal (0-12) on everything, kept for this output. */
    fun setBoost(db: Double) = set { it.copy(boost = boost(db)) }
    fun setTone(name: String, gain: Double) = set {
        if (name == "bass") it.copy(bass = Eq.clamp(gain), on = true) else it.copy(treble = Eq.clamp(gain), on = true)
    }
    fun forgetOutput(key: String) = set { it.copy(outputs = it.outputs - key) }

    // ---- the outputs ----

    /** Edit (and, if it's the one playing, hear) another output's EQ. */
    fun editOutput(key: String) {
        if (key == Music.eq.output) return
        val isNew = Music.eq.outputs[key] == null
        set { e ->
            val k = e.output.ifEmpty { "speaker" }
            val saved = e.outputs + (k to e.toOutput(e.outputs[e.output]?.label ?: outLabel(e.output)).copy(at = e.outputs[k]?.at ?: 0))
            e.copy(outputs = saved).withOutput(key, saved[key])
        }
        if (isNew && key == Music.CAR) setPreset("colorado")   // the truck, set up before it's been driven with: its tuning
    }

    /** The sound goes somewhere else: that output's own EQ, just as you left it (its headphones correction only if you
     *  picked one for it, its Boost…). A correction is never picked for you: the Equalizer says when AutoEq has one. */
    fun outputChanged() {
        switchOutput()
        Music.output = AutoEq.output(Music.ctx)
    }

    /** The sound went somewhere else: that output's own EQ comes in (a separate EQ for each output). */
    private fun switchOutput() {
        val o = Outputs.now(Music.ctx)
        Music.outputNow = o
        Music.eqProcessor.bypass = Music.bitPerfectNow()
        Music.current()?.let { Music.eqProcessor.songGain = Music.gainOf(it) }   // a song's volume change for this output, if it has one
        apply()
        val eq = Music.eq
        if (!eq.perOutput || o.key == eq.output) {
            if (eq.output.isEmpty()) set { it.copy(output = o.key, outputs = it.outputs + (o.key to it.toOutput(o.label))) }
            return
        }
        val was = eq.output
        val isNew = eq.outputs[o.key] == null
        set { e -> moved(e, was, o) }
        if (isNew && o.key == Music.CAR) setPreset("colorado")   // the Chevy starts on the Colorado's tuning
        if (isNew) SoundPresets.addDevicePreset(o)   // its own Sound preset to start from
        // (a pop-up said "its own equalizer" each time the sound moved to another output: taken out; the Equalizer shows it)
        SoundLog.note("sound on ${o.label}")
    }

    // ---- Bass and Treble with bit-perfect ----

    fun chooseBpTones(on: Boolean) {
        Music.bpTones = on; Prefs.set(Prefs.BP_TONES, on)
        Music.eqProcessor.update(Music.eq)   // the PC's filters again
        toast(if (on) "Bass and Treble on with bit-perfect (the rest of the sound untouched)" else "Bit-perfect: every sample untouched again")
    }
    fun bpTonesNow() = Music.bitPerfect && Music.bpTones && Music.eq.on && (Music.eq.bass != 0.0 || Music.eq.treble != 0.0)
}
