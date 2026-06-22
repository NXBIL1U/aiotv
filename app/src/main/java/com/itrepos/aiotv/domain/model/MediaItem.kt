package com.itrepos.aiotv.domain.model

data class MediaItem(
    val id: String,
    val type: String,
    val name: String,
    val description: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: String?,
    val genres: List<String> = emptyList(),
    val imdbRating: String? = null,
)
