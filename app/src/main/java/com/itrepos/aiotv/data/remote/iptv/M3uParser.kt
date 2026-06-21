package com.itrepos.aiotv.data.remote.iptv

import com.itrepos.aiotv.domain.model.Channel
import java.io.BufferedReader
import java.io.StringReader

object M3uParser {

    /**
     * Hard cap on parsed entries. A genuine M3U is normally well under this; the cap stops a
     * pathologically large playlist (e.g. an Xtream `m3u_plus` dump that bundles all VOD) from
     * exhausting memory on low-RAM devices like Fire TV sticks. Xtream `get.php` URLs are
     * routed to the compact JSON API before they ever reach here (see [XtreamCreds]).
     */
    const val MAX_CHANNELS = 20_000

    /** Convenience overload for in-memory content (small playlists, tests). */
    fun parse(content: String): List<Channel> =
        parse(BufferedReader(StringReader(content)), MAX_CHANNELS)

    /**
     * Stream a playlist line-by-line so we never hold the whole body in memory. Stops after
     * [maxChannels] entries. The caller owns [reader] (and should close it).
     */
    fun parse(reader: BufferedReader, maxChannels: Int = MAX_CHANNELS): List<Channel> {
        val channels = mutableListOf<Channel>()
        var line = reader.readLine()

        // Strip a leading UTF-8 BOM, which many providers prepend; otherwise the
        // first line is "﻿#EXTM3U" and the playlist is rejected as invalid.
        if (line?.trimStart()?.removePrefix("﻿")?.startsWith("#EXTM3U") != true) return emptyList()

        var attrs = mutableMapOf<String, String>()
        var displayName = ""

        while (reader.readLine().also { line = it } != null) {
            val l = line!!.trim()
            when {
                l.startsWith("#EXTINF:") -> {
                    attrs = mutableMapOf()
                    displayName = ""
                    val commaIdx = l.indexOf(',')
                    if (commaIdx >= 0) displayName = l.substring(commaIdx + 1).trim()
                    val attrPart = if (commaIdx >= 0) l.substring(0, commaIdx) else l
                    parseAttrs(attrPart, attrs)
                }
                l.startsWith("#") -> Unit
                l.isNotEmpty() -> {
                    val id = attrs["tvg-id"] ?: displayName
                    channels += Channel(
                        id = id.ifEmpty { l.hashCode().toString() },
                        name = attrs["tvg-name"]?.takeIf { it.isNotEmpty() } ?: displayName,
                        logoUrl = attrs["tvg-logo"]?.takeIf { it.isNotEmpty() },
                        groupTitle = attrs["group-title"] ?: "Uncategorised",
                        streamUrl = l,
                        tvgId = attrs["tvg-id"]?.takeIf { it.isNotEmpty() },
                        categoryKey = attrs["group-title"] ?: "Uncategorised",
                    )
                    attrs = mutableMapOf()
                    displayName = ""
                    if (channels.size >= maxChannels) break
                }
            }
        }
        return channels
    }

    private fun parseAttrs(line: String, out: MutableMap<String, String>) {
        val regex = Regex("""([\w-]+)=["']([^"']*)["']""")
        regex.findAll(line).forEach { m ->
            out[m.groupValues[1]] = m.groupValues[2]
        }
    }
}
