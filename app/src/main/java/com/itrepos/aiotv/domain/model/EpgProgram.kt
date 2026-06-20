package com.itrepos.aiotv.domain.model

data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String,
    val startMs: Long,
    val endMs: Long,
    val posterUrl: String? = null,
) {
    val durationMs: Long get() = endMs - startMs
}
