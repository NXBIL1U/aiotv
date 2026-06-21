package com.itrepos.aiotv.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recently_watched")
data class RecentlyWatchedEntity(
    @PrimaryKey val channelId: String,
    val watchedAtMs: Long,
)
