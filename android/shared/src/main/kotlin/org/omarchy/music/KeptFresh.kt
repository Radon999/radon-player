package org.omarchy.music

import java.util.concurrent.atomic.AtomicBoolean

/** Something that takes long to make (what your listening adds up to), kept and made again as it gets old, so that
 *  whoever asks hardly ever waits: younger than maxAge it is given as it is; older, it is still given at once and a
 *  new one is made in the background (one at a time); past waitAfter (or with none yet) the asker waits for a new
 *  one. A new one that can't be made in the background leaves the old one in place, and is tried again at the next
 *  asking. What is given is never changed afterwards: a new one takes its place whole. */
class KeptFresh<T : Any>(private val maxAge: Double, private val waitAfter: Double,
                         private val clock: () -> Double = { org.omarchy.music.now() },
                         private val inBackground: (() -> Unit) -> Unit,
                         private val make: () -> T) {
    @Volatile private var value: T? = null
    @Volatile private var at = 0.0
    private val busy = AtomicBoolean(false)

    /** The one there is, however old; null before the first. Never waits. */
    val current: T? get() = value

    fun get(): T {
        val v = value
        val age = clock() - at
        if (v == null || age >= waitAfter) return fresh()
        if (age >= maxAge && busy.compareAndSet(false, true)) {
            try { inBackground { try { fresh() } catch (_: Throwable) { } finally { busy.set(false) } } }
            catch (_: Throwable) { busy.set(false) }
        }
        return v
    }

    /** Forgotten: the next asker waits for a new one. */
    @Synchronized fun clear() { value = null }

    @Synchronized private fun fresh(): T {
        value?.let { if (clock() - at < maxAge) return it }   // made by another while this one waited
        return make().also { value = it; at = clock() }
    }
}
