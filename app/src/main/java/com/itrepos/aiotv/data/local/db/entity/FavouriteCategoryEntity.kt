package com.itrepos.aiotv.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favourite_categories")
data class FavouriteCategoryEntity(
    @PrimaryKey val categoryId: String,
    val addedAtMs: Long,
)
