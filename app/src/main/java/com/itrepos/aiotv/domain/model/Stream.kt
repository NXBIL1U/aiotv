package com.itrepos.aiotv.domain.model

enum class Quality(val rank: Int) { UHD_2160(4), HD_1080(3), HD_720(2), SD(1), UNKNOWN(0) }

data class Stream(
    val title: String?,
    val url: String?,
    val infoHash: String?,
    val fileIdx: Int?,
    val behaviorHints: BehaviorHints? = null,
    val isCached: Boolean = false,
    val torBoxTorrentId: Int? = null,
    val torBoxFileId: Int? = null,
    val name: String? = null,
    val quality: Quality = Quality.UNKNOWN,
    val seeders: Int? = null,
    val sizeBytes: Long? = null,
    val languageScore: Int = 0,
    val bingeGroup: String? = null,
)

data class BehaviorHints(val bingeGroup: String? = null, val filename: String? = null)
