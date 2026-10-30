package org.omarchy.music

// Small things the shared code needs that Android and the PC have in different places.

/** Base64, as the services want it: plain, or URL-safe with no padding. */
fun b64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)
fun b64url(bytes: ByteArray): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
/** Base64 read back, line breaks and all. */
fun unb64(s: String): ByteArray = java.util.Base64.getMimeDecoder().decode(s)

/** An address's query parameter (?code=…), decoded; null if it has none. */
fun queryParam(url: String, name: String): String? =
    url.substringAfter('?', "").substringBefore('#').split('&').firstOrNull { it.substringBefore('=') == name }
        ?.substringAfter('=', "")?.let { java.net.URLDecoder.decode(it, "UTF-8") }
