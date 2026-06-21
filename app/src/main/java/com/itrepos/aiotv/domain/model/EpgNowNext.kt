package com.itrepos.aiotv.domain.model

/** A single programme on a channel's schedule. Times are epoch millis. */
data class EpgEntry(
    val title: String,
    val startMs: Long,
    val endMs: Long,
)

/** The currently-airing and upcoming programme for a channel (either may be null). */
data class EpgNowNext(
    val now: EpgEntry?,
    val next: EpgEntry?,
)
