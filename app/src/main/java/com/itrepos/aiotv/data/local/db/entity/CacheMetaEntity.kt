package com.itrepos.aiotv.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cache_meta")
data class CacheMetaEntity(
    @PrimaryKey val key: String,
    val refreshedAtMs: Long,
)
