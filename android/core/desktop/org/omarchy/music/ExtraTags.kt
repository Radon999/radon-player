package org.omarchy.music

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/** Lyrics and our own named tags (MOOD_LYRICS) for the files the app's own writers don't do (M4A, Ogg…), through
 *  the tag library: an M4A's ©lyr and iTunes "----" items, an Ogg's comments. (FLAC, MP3 and WAV: [LyricsTags.writeTags].) */
object ExtraTags {
    /** Lyrics (null: leave them) and named tags ("" takes one off) into the file; true if it wrote. */
    fun write(ctx: Ctx, uri: String, lyrics: String?, named: Map<String, String>): Boolean = runCatching {
        val real = File(if (uri.startsWith("file:")) java.net.URI(uri).path else uri)
        // On a checked copy, then put over the file (SafeWrite): one writer at a time, never half a file
        SafeWrite.edit(ctx, real.path, real.extension.lowercase(), unchanged = { !it }) { f -> into(f, lyrics, named) }
    }.getOrDefault(false)

    private fun into(f: File, lyrics: String?, named: Map<String, String>): Boolean {
        val af = AudioFileIO.read(f)
        val tag = af.tagOrCreateAndSetDefault
        if (lyrics != null) { if (lyrics.isEmpty()) tag.deleteField(FieldKey.LYRICS) else tag.setField(FieldKey.LYRICS, lyrics) }
        for ((k, v) in named) when (tag) {
            is org.jaudiotagger.tag.mp4.Mp4Tag -> {
                val id = "----:com.apple.iTunes:$k"
                tag.deleteField(id)
                if (v.isNotEmpty()) tag.setField(org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField(id, "com.apple.iTunes", k, v))
            }
            is org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag -> {
                tag.deleteField(k)
                if (v.isNotEmpty()) tag.setField(org.jaudiotagger.tag.vorbiscomment.VorbisCommentTagField(k, v))
            }
            else -> return false   // a kind of file we don't put named tags in
        }
        af.commit()
        return true
    }
}
