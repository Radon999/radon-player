package org.omarchy.music

/** Which picture beside a song is its cover, for a song with no picture inside its file. */
object FolderCoverRules {
    /** The names a cover goes by, the likeliest first. */
    val NAMES = listOf("cover.jpg", "cover.png", "folder.jpg", "folder.png", "front.jpg", "front.png", "album.jpg", "albumartsmall.jpg",
        "cover.jpeg", "folder.jpeg", "front.jpeg")
    private val PICTURE = Regex(".*\\.(jpe?g|png|webp)$", RegexOption.IGNORE_CASE)

    /** Of a folder's file names: the one named as a cover is (any case), by [NAMES]' order; else the folder's only
     *  picture, whatever its name (a cover saved from the web under its address); null when there are none or several
     *  with no such name (which one would be a guess). */
    fun pick(names: List<String>): String? =
        NAMES.firstNotNullOfOrNull { n -> names.firstOrNull { it.equals(n, ignoreCase = true) } }
            ?: names.filter { PICTURE.matches(it) }.singleOrNull()
}
