package com.itrepos.aiotv.domain

import com.itrepos.aiotv.domain.model.Quality
import com.itrepos.aiotv.domain.model.Stream

object StreamRanker {
    fun rank(streams: List<Stream>, preferred: Quality? = null): List<Stream> =
        streams.sortedWith(
            compareByDescending<Stream> { it.isCached }
                .thenByDescending { it.languageScore }
                .thenByDescending { if (preferred != null && it.quality == preferred) 1 else 0 }
                .thenByDescending { it.quality.rank }
                .thenByDescending { it.seeders ?: -1 }
        )
}
