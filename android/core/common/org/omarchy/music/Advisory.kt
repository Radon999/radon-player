package org.omarchy.music

/** A file's explicit tag (iTunes advisory, or an EXPLICIT comment): true explicit, false clean, null if it says neither. */
fun advisory(v: String?): Boolean? = when (v?.trim()?.lowercase()) {
    "1", "4", "explicit", "true", "yes", "e" -> true
    "2", "clean", "edited", "c" -> false
    else -> null
}
