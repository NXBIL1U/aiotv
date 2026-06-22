package com.itrepos.aiotv

import com.itrepos.aiotv.domain.model.Episode
import com.itrepos.aiotv.domain.usecase.DirectPlaySequencing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectPlaySequencingTest {
    private fun ep(id: String, s: Int, n: Int) =
        Episode(id = id, season = s, number = n, name = "E$n", overview = null, thumbnail = null, released = null)

    private val eps = listOf(ep("tt:1:1", 1, 1), ep("tt:1:2", 1, 2), ep("tt:1:3", 1, 3))

    @Test fun picksFirstInProgressEpisode() {
        assertEquals("tt:1:3", DirectPlaySequencing.resumeEpisode(eps, setOf("tt:1:3"))!!.id)
    }

    @Test fun fallsBackToFirstEpisode() {
        assertEquals("tt:1:1", DirectPlaySequencing.resumeEpisode(eps, emptySet())!!.id)
    }

    @Test fun nullWhenNoEpisodes() {
        assertNull(DirectPlaySequencing.resumeEpisode(emptyList(), setOf("x")))
    }
}
