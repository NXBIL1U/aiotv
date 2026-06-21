package com.itrepos.aiotv.data.remote.iptv

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Credentials extracted from an Xtream Codes `get.php` playlist URL.
 *
 * Providers expose two ways to read the same account:
 *  - `get.php?username=..&password=..&type=m3u_plus` → one giant M3U dump that bundles
 *    **live + every VOD + every series** (hundreds of MB for large providers — loading it
 *    whole OOM-kills the app, especially on a Fire TV stick).
 *  - `player_api.php?username=..&password=..&action=get_live_streams` → compact JSON of
 *    **live channels only** (a few MB).
 *
 * When the user pastes a `get.php` URL into the M3U field we detect it here and take the
 * efficient JSON path instead of downloading the dump.
 */
data class XtreamCreds(
    val server: String,
    val user: String,
    val pass: String,
) {
    companion object {
        /** Parse an Xtream `get.php` URL into credentials, or null if [url] isn't one. */
        fun fromGetPhp(url: String): XtreamCreds? {
            val httpUrl = url.trim().toHttpUrlOrNull() ?: return null
            if (!httpUrl.encodedPath.endsWith("/get.php")) return null
            val user = httpUrl.queryParameter("username")?.takeIf { it.isNotEmpty() } ?: return null
            val pass = httpUrl.queryParameter("password")?.takeIf { it.isNotEmpty() } ?: return null

            // Rebuild "scheme://host[:port]" — keep a non-default port (IPTV panels often run
            // on 8080/25461/etc.), drop it when it's the protocol default.
            val defaultPort = HttpUrl.defaultPort(httpUrl.scheme)
            val server = buildString {
                append(httpUrl.scheme).append("://").append(httpUrl.host)
                if (httpUrl.port != defaultPort) append(':').append(httpUrl.port)
            }
            return XtreamCreds(server, user, pass)
        }
    }
}
