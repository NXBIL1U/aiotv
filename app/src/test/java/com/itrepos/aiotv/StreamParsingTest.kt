package com.itrepos.aiotv

import com.itrepos.aiotv.domain.StreamParsing
import com.itrepos.aiotv.domain.model.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class StreamParsingTest {
    @Test fun parsesQuality() {
        assertEquals(Quality.HD_1080, StreamParsing.quality("[TB+] Torrentio\n1080p"))
        assertEquals(Quality.HD_720, StreamParsing.quality("HIMYM.S01E01.720p.WEB-DL.mkv"))
        assertEquals(Quality.UHD_2160, StreamParsing.quality("Show.2160p.4K"))
        assertEquals(Quality.UNKNOWN, StreamParsing.quality("[TB+] Torrentio\nDLMux"))
    }
    @Test fun parsesSeeders() {
        assertEquals(27, StreamParsing.seeders("Complete 720p 👤 27 💾 4 GB"))
        assertEquals(null, StreamParsing.seeders("no seeder marker here"))
    }
    @Test fun parsesSize() {
        assertEquals(4L * 1024 * 1024 * 1024, StreamParsing.sizeBytes("x 💾 4 GB y"))
        assertEquals((1.5 * 1024.0 * 1024.0 * 1024.0).toLong(), StreamParsing.sizeBytes("x 💾 1.5 GB"))
    }
    @Test fun detectsTbCached() {
        assertTrue(StreamParsing.isTbCached("[TB+] Torrentio\n1080p"))
        assertFalse(StreamParsing.isTbCached("[TB download] Torrentio\n1080p"))
    }
    @Test fun englishScoresHigherThanForeign() {
        val eng = StreamParsing.languageScore("How.I.Met.Your.Mother.S01.1080p.AMZN.WEBRip.DDP5.1.x264-NOGRP")
        val rus = StreamParsing.languageScore("Как я встретил вашу маму / How I Met Your Mother VO (Кураж-Бамбей)")
        val fra = StreamParsing.languageScore("How I Met Your Mother (Integrale) FRENCH HDTV")
        assertEquals(2, eng)
        assertTrue(eng > rus)
        assertTrue(eng > fra)
    }
}
