package org.omarchy.music

// Same genre, spelt differently ("Hip-Hop/Rap", "Hip-Hop-Rap", "hip hop rap", "Hip Hop & Rap"): Fix genre names finds
// them and puts them all under one name.

/** What a genre name comes down to: its letters and digits only, any case, "&" and "and" dropped
 *  ("Hip-Hop/Rap" and "Hip Hop & Rap" are both "hiphoprap"; "R&B" and "R and B" are "rb"). */
fun genreKey(name: String): String = norm(name).replace("&", " ").split(Regex("[^\\p{L}\\p{N}]+"))
    .filter { it.isNotEmpty() && it != "and" && it != "n" }.joinToString("")

/** The genre names in counts (name -> songs) that are the same genre: each group as (the name to keep: the one most
 *  songs have, then the shortest, then A–Z; the other spellings). Groups with only one spelling aren't given. */
fun genreDupes(counts: Map<String, Int>): List<Pair<String, List<String>>> =
    counts.keys.filter { it.isNotBlank() && it != "Unknown genre" && genreKey(it).isNotEmpty() }.groupBy { genreKey(it) }.values
        .filter { it.size > 1 }
        .map { names ->
            val ranked = names.sortedWith(compareBy<String>({ -(counts[it] ?: 0) }, { it.length }, { it }))
            ranked.first() to ranked.drop(1)
        }
        .sortedByDescending { (keep, others) -> (counts[keep] ?: 0) + others.sumOf { counts[it] ?: 0 } }
