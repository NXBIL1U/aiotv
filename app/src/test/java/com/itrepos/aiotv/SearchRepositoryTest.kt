package com.itrepos.aiotv

import com.itrepos.aiotv.data.remote.stremio.CINEMETA_HOSTS
import com.itrepos.aiotv.data.remote.stremio.StremioApi
import com.itrepos.aiotv.data.remote.stremio.StremioCatalogResponse
import com.itrepos.aiotv.data.remote.stremio.StremioMeta
import com.itrepos.aiotv.data.remote.stremio.StremioMetaResponse
import com.itrepos.aiotv.data.remote.stremio.StremioStreamResponse
import com.itrepos.aiotv.data.remote.stremio.StremioManifest
import com.itrepos.aiotv.data.remote.stremio.fetchSearchFromHosts
import com.itrepos.aiotv.data.remote.stremio.searchUrl
import com.itrepos.aiotv.data.repository.SearchRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SearchRepositoryTest {
    @Test fun searchUrlEncodesQueryWithPercent20() {
        val u = searchUrl("https://cinemeta-live.strem.io", "series", "rick and morty")
        assertEquals("https://cinemeta-live.strem.io/catalog/series/top/search=rick%20and%20morty.json", u)
    }

    @Test fun fetchSearchFromHosts_allHostsFail_returnsNull() = runBlocking {
        val res = fetchSearchFromHosts(CINEMETA_HOSTS, "movie", "x") { throw RuntimeException("000") }
        assertNull(res)
    }

    @Test fun fetchSearchFromHosts_firstResponderWins() = runBlocking {
        var calls = 0
        val res = fetchSearchFromHosts(CINEMETA_HOSTS, "movie", "x") { calls++; listOf(meta("tt1","movie")) }
        assertEquals(1, calls)
        assertEquals(1, res!!.size)
    }

    private fun meta(id: String, type: String) = StremioMeta(id = id, type = type, name = id)

    private class FakeApi(val ok: Boolean) : StremioApi {
        override suspend fun getManifest(url: String): StremioManifest = throw NotImplementedError()
        override suspend fun getMeta(url: String): StremioMetaResponse = throw NotImplementedError()
        override suspend fun getStreams(url: String): StremioStreamResponse = throw NotImplementedError()
        override suspend fun getCatalog(url: String): StremioCatalogResponse {
            if (!ok) throw RuntimeException("000")
            // return one movie + one series, with a duplicate id across types to test dedup
            return if (url.contains("/movie/")) StremioCatalogResponse(listOf(StremioMeta("tt1","movie","M1"), StremioMeta("ttDUP","movie","Dup")))
            else StremioCatalogResponse(listOf(StremioMeta("tt2","series","S2"), StremioMeta("ttDUP","series","Dup")))
        }
    }

    @Test fun search_mergesBothTypes_dedupsById() = runBlocking {
        val out = SearchRepository(FakeApi(ok = true)).search("q")
        assertEquals(3, out.size) // tt1, ttDUP (once), tt2
        assertTrue(out.any { it.id == "tt1" && it.type == "movie" })
        assertTrue(out.any { it.id == "tt2" && it.type == "series" })
        assertEquals(1, out.count { it.id == "ttDUP" })
    }

    @Test fun search_cinemetaUnreachable_throws() {
        var threw = false
        try { runBlocking { SearchRepository(FakeApi(ok = false)).search("q") } }
        catch (e: IOException) { threw = true }
        assertTrue(threw)
    }
}
