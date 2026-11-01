package org.omarchy.music

/** The PC writes lyrics and MOOD_LYRICS into M4As and Ogg files through its tag library. On the phone the app only
 *  changes its own downloads (FLACs, done by [LyricsTags.writeTags]), so there's nothing to do here. */
object ExtraTags {
    @Suppress("UNUSED_PARAMETER")
    fun write(ctx: Ctx, uri: String, lyrics: String?, named: Map<String, String>): Boolean = false
}
