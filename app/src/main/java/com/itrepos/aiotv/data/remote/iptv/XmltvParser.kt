package com.itrepos.aiotv.data.remote.iptv

import android.util.Xml
import com.itrepos.aiotv.domain.model.EpgProgram
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object XmltvParser {

    private val dateFormats = listOf(
        "yyyyMMddHHmmss Z",
        "yyyyMMddHHmmss",
    ).map {
        SimpleDateFormat(it, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }

    fun parse(content: String): List<EpgProgram> {
        val programs = mutableListOf<EpgProgram>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(content))

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "programme") {
                parseProgramme(parser)?.let { programs += it }
            }
            eventType = parser.next()
        }
        return programs
    }

    private fun parseProgramme(parser: XmlPullParser): EpgProgram? {
        val channelId = parser.getAttributeValue(null, "channel") ?: return null
        val startStr = parser.getAttributeValue(null, "start") ?: return null
        val stopStr = parser.getAttributeValue(null, "stop") ?: return null
        val startMs = parseDate(startStr) ?: return null
        val endMs = parseDate(stopStr) ?: return null

        var title = ""
        var desc = ""
        var poster: String? = null
        var depth = 1

        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when (parser.name) {
                        "title" -> title = parser.nextText().also { depth-- }
                        "desc" -> desc = parser.nextText().also { depth-- }
                        "icon" -> poster = parser.getAttributeValue(null, "src")
                    }
                }
                XmlPullParser.END_TAG -> depth--
            }
        }
        if (title.isEmpty()) return null
        return EpgProgram(channelId, title, desc, startMs, endMs, poster)
    }

    private fun parseDate(s: String): Long? {
        val trimmed = s.trim()
        for (fmt in dateFormats) {
            try { return fmt.parse(trimmed)?.time } catch (_: Exception) {}
        }
        return null
    }
}
