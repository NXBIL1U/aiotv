package com.itrepos.aiotv.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    indices = [
        Index("regionTag"),
        Index("categoryId"),
        Index("name"),
    ]
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val categoryId: String,
    val regionTag: String,
    val epgChannelId: String?,
    val num: Int,
    /** Display channel number (Xtream `num` / M3U `tvg-chno`), shown Sky-style on each row. */
    val channelNo: Int = 0,
)
