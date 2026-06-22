package com.itrepos.aiotv.domain

import com.itrepos.aiotv.domain.model.Quality

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
    /** Higher = more likely English. */
    fun languageScore(text: String?): Int {
        val t = text ?: return 0
        var s = 0
        if (english.containsMatchIn(t)) s += 2
        if (foreign.any { it.containsMatchIn(t) }) s -= 2
        return s
    }
}
