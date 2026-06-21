package com.itrepos.aiotv.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.itrepos.aiotv.data.local.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE regionTag IN (:regionTags) ORDER BY name ASC")
    fun observe(regionTags: List<String>): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun all(): Flow<List<CategoryEntity>>

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
