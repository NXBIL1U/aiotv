package com.itrepos.aiotv

import com.itrepos.aiotv.domain.model.Episode
import com.itrepos.aiotv.domain.playback.BingeSequencing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class BingeSequencingTest {
    private fun ep(s: Int, n: Int) = Episode("tt:$s:$n", s, n, "E$n", null, null, null)
    private val eps = listOf(ep(1,1), ep(1,2), ep(2,1)) // sorted

    @Test fun nextWithinSeason() { assertEquals("tt:1:2", BingeSequencing.nextEpisode(eps, "tt:1:1")!!.id) }
    @Test fun nextCrossesSeasonBoundary() { assertEquals("tt:2:1", BingeSequencing.nextEpisode(eps, "tt:1:2")!!.id) }
    @Test fun lastEpisodeHasNoNext() { assertNull(BingeSequencing.nextEpisode(eps, "tt:2:1")) }
    @Test fun unknownIdHasNoNext() { assertNull(BingeSequencing.nextEpisode(eps, "tt:9:9")) }
    @Test fun doesNotRollFromRealSeasonIntoSpecials() {
        val withSpecials = listOf(ep(0,1), ep(1,1)) // specials sort first
        // last real episode -> nothing after it
        assertNull(BingeSequencing.nextEpisode(withSpecials, "tt:1:1"))
    }
    @Test fun bingeMatch() {
        assertTrue(BingeSequencing.isBingeMatch("torrentio|1080p", "torrentio|1080p"))
        assertFalse(BingeSequencing.isBingeMatch("a", "b"))
        assertFalse(BingeSequencing.isBingeMatch(null, null))
    }
}
