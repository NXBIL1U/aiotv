package com.itrepos.aiotv.domain

import com.itrepos.aiotv.domain.model.Stream

object StreamRanker {
    fun rank(streams: List<Stream>): List<Stream> =
        streams.sortedWith(
            compareByDescending<Stream> { it.isCached }
                .thenByDescending { it.languageScore }
                .thenByDescending { it.quality.rank }
                .thenByDescending { it.seeders ?: -1 }
        )
}
