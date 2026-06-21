package com.itrepos.aiotv.domain.model

/**
 * A live-channel group. [id] is the unified filter key — Xtream `category_id` or, for plain
 * M3U providers, the `group-title` — matched against [Channel.categoryKey].
 */
data class ChannelCategory(
    val id: String,
    val name: String,
)
