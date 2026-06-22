package com.itrepos.aiotv.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val groupTitle: String,
    val streamUrl: String,
    val tvgId: String?,
    val isFavourite: Boolean = false,
)
