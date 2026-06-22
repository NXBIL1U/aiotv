package com.itrepos.aiotv

import com.itrepos.aiotv.domain.StreamParsing
import com.itrepos.aiotv.domain.model.Codec
import com.itrepos.aiotv.domain.model.Hdr
import com.itrepos.aiotv.domain.model.Quality
import com.itrepos.aiotv.domain.model.SourceType
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
    @Test fun parsesCodec() {
        assertEquals(Codec.AVC, StreamParsing.codec("Her 2013 1080p BluRay REMUX AVC DTS-HD MA"))
        assertEquals(Codec.AVC, StreamParsing.codec("Her.2013.1080p.BluRay.x264-SPARKS"))
        assertEquals(Codec.HEVC, StreamParsing.codec("Her 2013 1080p BluRay x265-YAWNTiC"))
        assertEquals(Codec.HEVC, StreamParsing.codec("Movie 2160p HDR10 HEVC"))
        assertEquals(Codec.AV1, StreamParsing.codec("Movie 1080p WEB-DL AV1"))
        assertEquals(Codec.UNKNOWN, StreamParsing.codec("Movie 1080p BluRay"))
    }
    @Test fun parsesHdr() {
        assertEquals(Hdr.HDR10, StreamParsing.hdr("Movie 2160p HDR10 HEVC"))
        assertEquals(Hdr.DOLBY_VISION, StreamParsing.hdr("Movie 2160p DV Dolby Vision HEVC"))
        assertEquals(Hdr.UNKNOWN, StreamParsing.hdr("Her 2013 1080p BluRay x264"))
    }
    @Test fun parsesSourceType() {
        assertEquals(SourceType.REMUX, StreamParsing.sourceType("Her 2013 1080p BluRay REMUX AVC"))
        assertEquals(SourceType.WEBDL, StreamParsing.sourceType("Movie 1080p WEB-DL x264"))
        assertEquals(SourceType.WEBRIP, StreamParsing.sourceType("Movie 1080p WEBRip x265"))
        assertEquals(SourceType.BLURAY, StreamParsing.sourceType("Her.2013.1080p.BluRay.x264-SPARKS"))
        assertEquals(SourceType.UNKNOWN, StreamParsing.sourceType("Some random title"))
    }
}
