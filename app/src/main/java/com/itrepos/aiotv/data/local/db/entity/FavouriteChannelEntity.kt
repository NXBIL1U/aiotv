package com.itrepos.aiotv.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favourite_channels")
data class FavouriteChannelEntity(
    @PrimaryKey val channelId: String,
    val addedAtMs: Long,
)
