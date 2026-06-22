package com.itrepos.aiotv.domain

import com.itrepos.aiotv.domain.model.Codec
import com.itrepos.aiotv.domain.model.Hdr
import com.itrepos.aiotv.domain.model.Quality
import com.itrepos.aiotv.domain.model.SourceType

object StreamParsing {
    private val seedersRe = Regex("👤\\s*(\\d+)")          // 👤 N
    private val sizeRe = Regex("💾\\s*([\\d.]+)\\s*(GB|MB)", RegexOption.IGNORE_CASE) // 💾 N GB/MB
    private val foreign = listOf(
        Regex("[\\u0400-\\u04FF]"),                                  // Cyrillic
        Regex("\\bFRENCH\\b|\\bVOSTFR\\b|\\bVF\\b", RegexOption.IGNORE_CASE),
        Regex("\\bITA\\b|\\bITALIAN\\b", RegexOption.IGNORE_CASE),
        Regex("\\bSPA\\b|\\bSPANISH\\b|\\bLAT\\b", RegexOption.IGNORE_CASE),
    )
    private val english = Regex("\\bENG\\b|\\bENGLISH\\b|\\bAMZN\\b", RegexOption.IGNORE_CASE)
    private val uhdRe = Regex("2160p|\\b4K\\b", RegexOption.IGNORE_CASE)
    private val sdRe  = Regex("480p|\\bSD\\b|DVDRip|XviD", RegexOption.IGNORE_CASE)

    fun quality(text: String?): Quality {
        val t = text ?: return Quality.UNKNOWN
        return when {
            uhdRe.containsMatchIn(t) -> Quality.UHD_2160
            t.contains("1080p", true) -> Quality.HD_1080
            t.contains("720p", true) -> Quality.HD_720
            sdRe.containsMatchIn(t) -> Quality.SD
            else -> Quality.UNKNOWN
        }
    }
    fun seeders(title: String?): Int? = title?.let { seedersRe.find(it)?.groupValues?.get(1)?.toIntOrNull() }
    fun sizeBytes(title: String?): Long? = title?.let {
        val m = sizeRe.find(it) ?: return null
        val n = m.groupValues[1].toDoubleOrNull() ?: return null
        return if (m.groupValues[2].equals("GB", true)) {
            (n * 1024.0 * 1024.0 * 1024.0).toLong()
        } else {
            (n * 1024.0 * 1024.0).toLong()
        }
    }
    // [TB+] is a literal substring (Torrentio's TorBox-cached marker), not a regex character class.
    fun isTbCached(name: String?): Boolean = name?.contains("[TB+]") == true
    // parse-torrent-title patterns (MIT), trimmed to what the ranker needs.
    private val hevcRe = Regex("\\bx265\\b|\\bh\\.?265\\b|\\bHEVC\\b", RegexOption.IGNORE_CASE)
    private val avcRe = Regex("\\bx264\\b|\\bh\\.?264\\b|\\bAVC\\b", RegexOption.IGNORE_CASE)
    private val av1Re = Regex("\\bAV1\\b", RegexOption.IGNORE_CASE)
    private val dvRe = Regex("\\bDV\\b|\\bDolby\\s?Vision\\b|\\bDoVi\\b", RegexOption.IGNORE_CASE)
    private val hdr10Re = Regex("\\bHDR10(\\+)?\\b|\\bHDR\\b", RegexOption.IGNORE_CASE)
    private val remuxRe = Regex("\\bREMUX\\b", RegexOption.IGNORE_CASE)
    private val webdlRe = Regex("\\bWEB[-. ]?DL\\b|\\bWEBDL\\b|\\bAMZN\\b|\\bNF\\b", RegexOption.IGNORE_CASE)
    private val webripRe = Regex("\\bWEB[-. ]?Rip\\b|\\bWEBRip\\b", RegexOption.IGNORE_CASE)
    private val blurayRe = Regex("\\bBlu[-. ]?Ray\\b|\\bBDRip\\b|\\bBRRip\\b|\\bBDRemux\\b", RegexOption.IGNORE_CASE)
    private val hdtvRe = Regex("\\bHDTV\\b", RegexOption.IGNORE_CASE)

    fun codec(text: String?): Codec {
        val t = text ?: return Codec.UNKNOWN
        return when {
            av1Re.containsMatchIn(t) -> Codec.AV1
            hevcRe.containsMatchIn(t) -> Codec.HEVC
            avcRe.containsMatchIn(t) -> Codec.AVC
            else -> Codec.UNKNOWN
        }
    }

    fun hdr(text: String?): Hdr {
        val t = text ?: return Hdr.UNKNOWN
        return when {
            dvRe.containsMatchIn(t) -> Hdr.DOLBY_VISION
            hdr10Re.containsMatchIn(t) -> Hdr.HDR10
            else -> Hdr.UNKNOWN
        }
    }

    fun sourceType(text: String?): SourceType {
        val t = text ?: return SourceType.UNKNOWN
        return when {
            remuxRe.containsMatchIn(t) -> SourceType.REMUX
            webdlRe.containsMatchIn(t) -> SourceType.WEBDL
            webripRe.containsMatchIn(t) -> SourceType.WEBRIP
            blurayRe.containsMatchIn(t) -> SourceType.BLURAY
            hdtvRe.containsMatchIn(t) -> SourceType.HDTV
            else -> SourceType.UNKNOWN
        }
    }

    /** Higher = more likely English. */
    fun languageScore(text: String?): Int {
        val t = text ?: return 0
        var s = 0
        if (english.containsMatchIn(t)) s += 2
        if (foreign.any { it.containsMatchIn(t) }) s -= 2
        return s
    }
}
