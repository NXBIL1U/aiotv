package com.itrepos.aiotv.domain

/**
 * Tunes a Torrentio addon base URL before a /stream request so the candidate pool includes
 * streamable encodes (not just the 5 biggest REMUX). Only touches `limit`; every other option
 * (the `torbox=` token, `sort`, `qualityfilter`, `debridoptions`) is preserved. Non-Torrentio
 * addons are returned unchanged. The options live in Torrentio's path segment, `|`-separated
 * (URL-encoded as %7C), e.g. .../sort=qualitysize%7Climit=5%7Ctorbox=<token>.
 */
object StreamRequestTuning {
    private const val DESIRED_LIMIT = 30
    private val limitRe = Regex("limit=\\d+")

    fun tuneStreamBase(baseUrl: String): String {
        if (!baseUrl.contains("torrentio", ignoreCase = true)) return baseUrl
        if (limitRe.containsMatchIn(baseUrl)) {
            return limitRe.replace(baseUrl, "limit=$DESIRED_LIMIT")
        }
        // No limit option present — inject one right after the host as a new option segment.
        val schemeEnd = baseUrl.indexOf("://").let { if (it >= 0) it + 3 else 0 }
        val hostEnd = baseUrl.indexOf('/', schemeEnd)
        return if (hostEnd >= 0) {
            baseUrl.substring(0, hostEnd) + "/limit=$DESIRED_LIMIT" + baseUrl.substring(hostEnd)
        } else {
            "$baseUrl/limit=$DESIRED_LIMIT"
        }
    }
}
