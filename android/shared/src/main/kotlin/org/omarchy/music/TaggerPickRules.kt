package org.omarchy.music

/** The Tagger works on files, so of the songs picked for it only the ones you have (your files and downloads) are
 *  tagged. Streaming songs among them are passed over, and it says so: they have to be downloaded to be tagged. */
object TaggerPickRules {
    /** What is said about the streaming songs passed over ("" when there are none). local: how many will be tagged. */
    fun skipped(streaming: Int, local: Int): String = when {
        streaming <= 0 -> ""
        local <= 0 -> if (streaming == 1) "This is a streaming song: download it to be able to tag it"
            else "These %,d are streaming songs: download them to be able to tag them".format(streaming)
        streaming == 1 -> "1 streaming song was skipped: download it to be able to tag it"
        else -> "%,d streaming songs were skipped: download them to be able to tag them".format(streaming)
    }
}
