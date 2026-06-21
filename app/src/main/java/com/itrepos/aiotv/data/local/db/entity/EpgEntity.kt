package com.itrepos.aiotv.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "epg")
data class EpgEntity(
    @PrimaryKey val channelId: String,
    val nowTitle: String?,
    val nowStartMs: Long,
    val nowEndMs: Long,
    val nextTitle: String?,
    val nextStartMs: Long,
    val fetchedAtMs: Long,
    /** expiresAtMs = the now-programme's end (or fetchedAt + fallback when no EPG). */
    val expiresAtMs: Long,
)
