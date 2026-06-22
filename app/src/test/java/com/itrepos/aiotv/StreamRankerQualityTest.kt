package com.itrepos.aiotv

import com.itrepos.aiotv.domain.StreamRanker
import com.itrepos.aiotv.domain.model.Quality
import com.itrepos.aiotv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamRankerQualityTest {
    private fun s(t: String, cached: Boolean, q: Quality, lang: Int = 2, seed: Int? = 0) =
        Stream(title = t, url = "u/$t", infoHash = null, fileIdx = null,
            isCached = cached, name = t, quality = q, seeders = seed, languageScore = lang)

    @Test fun prefers1080pOver4kWhenPreferred() {
        val uhd = s("4k", true, Quality.UHD_2160)
        val hd = s("1080", true, Quality.HD_1080)
        assertEquals("1080", StreamRanker.rank(listOf(uhd, hd), Quality.HD_1080).first().title)
    }
    @Test fun prefers4kWhenPreferred() {
        val uhd = s("4k", true, Quality.UHD_2160)
        val hd = s("1080", true, Quality.HD_1080)
        assertEquals("4k", StreamRanker.rank(listOf(hd, uhd), Quality.UHD_2160).first().title)
    }
    @Test fun cachedStillBeatsPreferredUncached() {
        val uncachedPref = s("1080-uncached", false, Quality.HD_1080)
        val cachedOther = s("4k-cached", true, Quality.UHD_2160)
        assertEquals("4k-cached", StreamRanker.rank(listOf(uncachedPref, cachedOther), Quality.HD_1080).first().title)
    }
    @Test fun nullPreferenceKeepsQualityRankOrder() {
        val uhd = s("4k", true, Quality.UHD_2160)
        val hd = s("1080", true, Quality.HD_1080)
        assertEquals("4k", StreamRanker.rank(listOf(hd, uhd), null).first().title)
    }
}
