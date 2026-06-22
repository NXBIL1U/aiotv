package com.itrepos.aiotv.domain.model

data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val groupTitle: String,
    val streamUrl: String,
    val tvgId: String?,
    // Unified category filter key: Xtream `category_id` or M3U `group-title`. Matches
    // ChannelCategory.id so filtering is source-agnostic.
    val categoryKey: String = "",
    /** Display channel number (Xtream `num` / M3U `tvg-chno`); 0 if unknown. */
    val channelNo: Int = 0,
    val isFavourite: Boolean = false,
)
