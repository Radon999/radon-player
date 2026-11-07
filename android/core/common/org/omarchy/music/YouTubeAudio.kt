package org.omarchy.music

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo

/** A song's audio on YouTube Music, for when Tidal has no FLAC of it (NewPipe's extractor
 *  finds the address): the best YouTube has (see [best]). A song from another service is found by a search first. */
object YouTubeAudio {
    private var started = false

    private object Net : Downloader() {
        override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
            val body = request.dataToSend()?.toRequestBody()
            val b = Request.Builder().url(request.url()).method(request.httpMethod(), body)
            for ((k, values) in request.headers()) { b.removeHeader(k); values.forEach { b.addHeader(k, it) } }
            if (request.headers().keys.none { it.equals("User-Agent", true) }) b.header("User-Agent", DESKTOP_UA)
            HTTP.newCall(b.build()).execute().use { r ->
                return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
            }
        }
    }

    /** (blocking) (address, "Opus 160 kbps"), or null if YouTube Music hasn't got it. */
    fun find(t: Track): Pair<String, String>? {
        val vid = if (t.source == "ytm") t.id else search(t) ?: return null
        return audio(vid)
    }

    /** YouTube's audio-only streams of a video, the best first: the highest bitrate (Opus ~160 kbps, itag 251; AAC
     *  256 kbps, itag 141, where YouTube offers it; else AAC 128 kbps, itag 140). Only the song's own sound: not a dubbed
     *  or described language track, nor the quieter "stable volume" (DRC) copy YouTube also offers. */
    private fun best(vid: String): List<AudioStream> {
        synchronized(this) { if (!started) { NewPipe.init(Net); started = true } }
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$vid")
        val all = info.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() }
        val original = all.filter { it.audioTrackType?.name.let { n -> n == null || n == "ORIGINAL" } }.ifEmpty { all }
        val plain = original.filterNot { isDrc(it) }.ifEmpty { original }
        return plain.sortedWith(compareByDescending<AudioStream> { it.averageBitrate }.thenByDescending { isOpus(it) })
    }

    private fun isDrc(a: AudioStream) = a.content.contains("drc%3D1") || a.content.contains("drc=1")
    private fun isOpus(a: AudioStream) = a.codec?.startsWith("opus") == true || a.format?.suffix == "opus"
    private fun isAac(a: AudioStream) = a.codec?.startsWith("mp4a") == true || a.format?.suffix == "m4a"
    /** "Opus 160 kbps", "AAC 256 kbps". */
    private fun label(a: AudioStream): String {
        val codec = a.codec?.substringBefore('.')?.let { mapOf("opus" to "Opus", "mp4a" to "AAC")[it] ?: it }
            ?: if (isAac(a)) "AAC" else a.format?.name ?: ""
        return (if (a.averageBitrate > 0) "$codec ${a.averageBitrate} kbps" else codec).trim()
    }

    /** (blocking) The audio of YouTube video vid: (address, "Opus 160 kbps"), or null. */
    fun audio(vid: String): Pair<String, String>? {
        val a = best(vid).firstOrNull() ?: return null
        // YouTube's address answers only a moment after it's made.
        repeat(20) {
            val ok = runCatching {
                HTTP.newCall(Request.Builder().url(a.content).header("Range", "bytes=0-1").build()).execute().use { it.code in 200..206 }
            }.getOrDefault(false)
            if (ok) return a.content to label(a)
            Thread.sleep(500)
        }
        error("YouTube wouldn't send the audio")
    }

    /** A YouTube stream to save: its address, what it is ("Opus 160 kbps") and how it comes: "webm" (Opus, made an
     *  .opus file as it is, no re-encoding) or "m4a" (AAC, kept as it comes). */
    class Pick(val url: String, val label: String, val ext: String)

    /** (blocking) For a download: YouTube's audio, AAC first, then Opus (see [best]), each Opus (WebM) or AAC (M4A) stream
     *  the app can save as it is, so a later one stands in if the first fails. Empty if YouTube Music hasn't got it.
     *  YouTube has no lossless audio: this is the most it has, saved without converting (which would only lose). */
    fun forDownload(t: Track): List<Pick> {
        val vid = if (t.source == "ytm") t.id else search(t) ?: return emptyList()
        val all = best(vid)
        // AAC (.m4a) first: it plays and tags everywhere, as your M4As do (YouTube's best AAC, 256 kbps where offered,
        // else 128). Opus (.opus) only when YouTube has no AAC of it, or the AAC won't come.
        return (all.filter { isAac(it) }.take(1) + all.filter { isOpus(it) }.take(1)).distinct().map { a ->
            Pick(a.content, label(a), if (isAac(a)) "m4a" else "webm")
        }
    }

    private fun search(t: Track): String? {
        val ytm = Music.sources["ytm"] ?: return null
        val lead = t.artist.split(",")[0].trim()
        return ytm.search("$lead ${bareTitle(t.title)}").filter { c ->
            t.duration <= 0 || c.duration <= 0 || kotlin.math.abs(c.duration - t.duration) <= 6
        }.map { c ->
            val title = similar(cleanTitle(t.title), cleanTitle(c.title))
            val artist = artistNames(listOf(lead)).flatMap { x -> artistNames(c.artist.split(",")).map { y -> similar(x, y) } }.maxOrNull() ?: 0.0
            c to (if (title >= 0.75 && artist >= 0.8) 0.6 * title + 0.4 * artist else 0.0)
        }.filter { it.second > 0 }.maxByOrNull { it.second }?.first?.id
    }
}
