package com.itrepos.aiotv.domain.model

data class Stream(
    val title: String?,
    val url: String?,
    val infoHash: String?,
    val fileIdx: Int?,
    val behaviorHints: BehaviorHints? = null,
    val isCached: Boolean = false,
    val torBoxTorrentId: Int? = null,
    val torBoxFileId: Int? = null,
)

data class BehaviorHints(
    val bingeGroup: String? = null,
    val filename: String? = null,
)
