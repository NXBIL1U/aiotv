package com.itrepos.aiotv

import com.itrepos.aiotv.domain.StreamRanker
import com.itrepos.aiotv.domain.model.Quality
import com.itrepos.aiotv.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamRankerTest {
    private fun s(name: String, title: String, cached: Boolean, q: Quality, lang: Int, seed: Int?) =
        Stream(title = title, url = "u/$title", infoHash = null, fileIdx = null,
            isCached = cached, name = name, quality = q, seeders = seed, languageScore = lang)

    @Test fun englishCached1080pWins() {
        val rus = s("[TB+] Torrentio\n1080p", "Как я встретил VO (Кураж)", true, Quality.HD_1080, -2, 0)
        val eng = s("[TB+] Torrentio\n1080p", "HIMYM S01 1080p AMZN", true, Quality.HD_1080, 2, 7)
        val eng720 = s("[TB+] Torrentio\n720p", "HIMYM S01 720p ENG", true, Quality.HD_720, 2, 27)
        val ranked = StreamRanker.rank(listOf(rus, eng720, eng))
        assertEquals("HIMYM S01 1080p AMZN", ranked.first().title)
    }
    @Test fun cachedBeatsUncachedEvenIfLowerQuality() {
        val uncached1080 = s("[TB download]", "A 1080p", false, Quality.HD_1080, 2, 50)
        val cached720 = s("[TB+]", "B 720p", true, Quality.HD_720, 2, 3)
        assertEquals("B 720p", StreamRanker.rank(listOf(uncached1080, cached720)).first().title)
    }
}
