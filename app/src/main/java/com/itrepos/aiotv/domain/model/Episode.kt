package com.itrepos.aiotv.domain.model

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val name: String,
    val overview: String?,
    val thumbnail: String?,
    val released: String?,
)
