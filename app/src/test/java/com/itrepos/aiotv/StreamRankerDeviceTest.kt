package com.itrepos.aiotv

import com.itrepos.aiotv.domain.StreamRanker
import com.itrepos.aiotv.domain.model.Codec
import com.itrepos.aiotv.domain.model.Quality
import com.itrepos.aiotv.domain.model.Stream
import com.itrepos.aiotv.domain.playback.DeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRankerDeviceTest {
    private val GB = 1024L * 1024 * 1024
    private fun s(t: String, q: Quality, codec: Codec, sizeGb: Double, cached: Boolean = true, lang: Int = 2, seed: Int? = 10) =
        Stream(title = t, url = "u/$t", infoHash = null, fileIdx = null, isCached = cached,
            name = t, quality = q, seeders = seed, sizeBytes = (sizeGb * GB).toLong(), languageScore = lang, codec = codec)

    private val phone = DeviceProfile(Quality.HD_1080, setOf(Codec.AVC, Codec.HEVC), hdrCapable = false)
    private val tv = DeviceProfile(Quality.UHD_2160, setOf(Codec.AVC, Codec.HEVC, Codec.AV1), hdrCapable = true)

    @Test fun excludesOver20GbFromAutoPick() {
        val remux = s("1080p REMUX", Quality.HD_1080, Codec.AVC, 24.0)
        assertFalse(StreamRanker.isAutoEligible(remux, phone))
    }

    @Test fun picksStreamableUnderCapOverRemux() {
        val remux = s("1080p REMUX", Quality.HD_1080, Codec.AVC, 24.0)
        val webdl = s("1080p WEB", Quality.HD_1080, Codec.AVC, 8.0)
        val ranked = StreamRanker.rank(listOf(remux, webdl), phone, Quality.HD_1080)
        assertEquals("1080p WEB", ranked.filter { StreamRanker.isAutoEligible(it, phone) }.first().title)
    }

    @Test fun within1080pPrefersSmaller() {
        val big = s("1080p big", Quality.HD_1080, Codec.AVC, 18.0)
        val small = s("1080p small", Quality.HD_1080, Codec.AVC, 5.0)
        assertEquals("1080p small", StreamRanker.rank(listOf(big, small), phone, Quality.HD_1080).first().title)
    }

    @Test fun notUltraLowBitratePotato() {
        val potato = s("1080p potato", Quality.HD_1080, Codec.AVC, 1.5)
        val healthy = s("1080p healthy", Quality.HD_1080, Codec.AVC, 8.0)
        assertEquals("1080p healthy", StreamRanker.rank(listOf(potato, healthy), phone, Quality.HD_1080).first().title)
    }

    @Test fun resolutionCeilingExcludes4kOn1080pProfile() {
        val uhd = s("2160p", Quality.UHD_2160, Codec.HEVC, 18.0)
        assertFalse(StreamRanker.isAutoEligible(uhd, phone))
        assertTrue(StreamRanker.isAutoEligible(uhd, tv))
    }

    @Test fun codecHardFilterDropsUndecodable() {
        val av1 = s("1080p AV1", Quality.HD_1080, Codec.AV1, 6.0) // phone has no AV1
        assertFalse(StreamRanker.isAutoEligible(av1, phone))
        assertTrue(StreamRanker.isAutoEligible(av1, tv))
    }

    @Test fun allOverCapMeansNoAutoEligible() {
        val a = s("a", Quality.HD_1080, Codec.AVC, 24.0)
        val b = s("b", Quality.HD_1080, Codec.AVC, 30.0)
        assertTrue(StreamRanker.rank(listOf(a, b), phone, Quality.HD_1080).none { StreamRanker.isAutoEligible(it, phone) })
    }

    @Test fun oneSeederPenalised() {
        val oneSeed = s("oneSeed", Quality.HD_1080, Codec.AVC, 8.0, seed = 1)
        val many = s("many", Quality.HD_1080, Codec.AVC, 8.0, seed = 40)
        assertEquals("many", StreamRanker.rank(listOf(oneSeed, many), phone, Quality.HD_1080).first().title)
    }
}
