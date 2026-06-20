package com.itrepos.aiotv.domain.model

data class WatchProgress(
    val id: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedMs: Long,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
