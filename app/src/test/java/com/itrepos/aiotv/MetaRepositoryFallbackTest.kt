package com.itrepos.aiotv

import com.itrepos.aiotv.data.remote.stremio.CINEMETA_HOSTS
import com.itrepos.aiotv.data.remote.stremio.StremioMeta
import com.itrepos.aiotv.data.remote.stremio.StremioVideo
import com.itrepos.aiotv.data.repository.fetchFromHosts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MetaRepositoryFallbackTest {

    private fun metaWithEp() = StremioMeta(
        id = "tt1", type = "series", name = "S",
        videos = listOf(StremioVideo(id = "tt1:1:1", season = 1, episode = 1, name = "P"))
    )

    @Test fun fallsBackToSecondHost() = runBlocking {
        var lastUrl: String? = null
        val sm = fetchFromHosts(
            hosts = CINEMETA_HOSTS,
            id = "tt1",
            fetch = { url ->
                lastUrl = url
                if (url.startsWith(CINEMETA_HOSTS[0])) throw RuntimeException("network error")
                metaWithEp()
            }
        )
        assertNotNull(sm)
        assertEquals(1, sm!!.episodes.size)
        assertEquals(CINEMETA_HOSTS[1], lastUrl!!.substringBefore("/meta/"))
    }

    @Test fun returnsNullWhenAllHostsFail() = runBlocking {
        val sm = fetchFromHosts(
            hosts = CINEMETA_HOSTS,
            id = "tt1",
            fetch = { _ -> throw RuntimeException("all down") }
        )
        assertNull(sm)
    }

    @Test fun returnsFirstSuccessfulHost() = runBlocking {
        var callCount = 0
        val sm = fetchFromHosts(
            hosts = CINEMETA_HOSTS,
            id = "tt1",
            fetch = { _ ->
                callCount++
                metaWithEp()
            }
        )
        assertNotNull(sm)
        // Should stop after first success
        assertEquals(1, callCount)
    }
}
