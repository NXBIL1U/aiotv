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
    val isFavourite: Boolean = false,
)
