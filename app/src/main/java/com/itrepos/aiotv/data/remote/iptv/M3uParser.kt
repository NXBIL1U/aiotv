package com.itrepos.aiotv.data.remote.iptv

import com.itrepos.aiotv.domain.model.Channel
import java.io.BufferedReader
import java.io.StringReader

object M3uParser {

    fun parse(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val reader = BufferedReader(StringReader(content))
        var line = reader.readLine()

        if (line?.trimStart()?.startsWith("#EXTM3U") != true) return emptyList()

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
                    )
                    attrs = mutableMapOf()
                    displayName = ""
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
