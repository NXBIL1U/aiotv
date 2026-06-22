package com.itrepos.aiotv

import com.itrepos.aiotv.data.remote.stremio.StremioMeta
import com.itrepos.aiotv.data.remote.stremio.StremioVideo
import com.itrepos.aiotv.data.repository.toSeriesMeta
import org.junit.Assert.assertEquals
import org.junit.Test

class CinemetaMappingTest {
    private val meta = StremioMeta(
        id = "tt0460649", type = "series", name = "How I Met Your Mother",
        videos = listOf(
            StremioVideo(id = "tt0460649:1:1", season = 1, episode = 1, name = "Pilot", thumbnail = "t1"),
            StremioVideo(id = "tt0460649:1:2", season = 1, episode = 2, name = "Purple Giraffe"),
            StremioVideo(id = "tt0460649:2:1", season = 2, episode = 1, name = "Where Were We?"),
            StremioVideo(id = "tt0460649:0:1", season = 0, episode = 1, name = "Special"),
        )
    )
    @Test fun groupsSeasonsSkippingSpecialsToEnd() {
        val sm = meta.toSeriesMeta()
        assertEquals(listOf(1, 2, 0), sm.seasons) // specials (0) last
        assertEquals(2, sm.episodesIn(1).size)
        assertEquals("Pilot", sm.episodesIn(1).first().name)
        assertEquals("tt0460649:2:1", sm.episodesIn(2).first().id)
    }
}
