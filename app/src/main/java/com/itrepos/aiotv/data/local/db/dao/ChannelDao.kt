package com.itrepos.aiotv.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.itrepos.aiotv.data.local.db.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    /** Observe channels filtered by regionTags; optionally scoped to a categoryId. */
    @Query("""
        SELECT * FROM channels
        WHERE regionTag IN (:regionTags)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY num ASC, name ASC
    """)
    fun observe(regionTags: List<String>, categoryId: String? = null): Flow<List<ChannelEntity>>

    /** Global search across all channels (ignores region/category). */
    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun search(query: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): ChannelEntity?

    /** One-shot, region-agnostic list of all channels (for Home/Search cache reads). */
    @Query("SELECT * FROM channels ORDER BY num ASC, name ASC")
    suspend fun getAll(): List<ChannelEntity>

    @Upsert
    suspend fun upsertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun deleteAll()
}
