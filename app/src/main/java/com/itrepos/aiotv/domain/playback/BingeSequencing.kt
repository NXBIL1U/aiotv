package com.itrepos.aiotv.domain.playback

import com.itrepos.aiotv.domain.model.Episode

object BingeSequencing {
    /** Next episode in the season/number-sorted list; null at the end or if rolling into season-0 specials. */
    fun nextEpisode(episodes: List<Episode>, currentId: String): Episode? {
        val i = episodes.indexOfFirst { it.id == currentId }
        if (i < 0 || i + 1 >= episodes.size) return null
        val next = episodes[i + 1]
        if (episodes[i].season != 0 && next.season == 0) return null
        return next
    }
    /** Stremio behaviour: same non-null bingeGroup string => same release. */
    fun isBingeMatch(a: String?, b: String?): Boolean = a != null && a == b
}
