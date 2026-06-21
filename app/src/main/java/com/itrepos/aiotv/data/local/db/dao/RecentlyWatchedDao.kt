package com.itrepos.aiotv.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itrepos.aiotv.data.local.db.entity.ChannelEntity
import com.itrepos.aiotv.data.local.db.entity.RecentlyWatchedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyWatchedDao {

    @Query("""
        SELECT c.* FROM channels c
        INNER JOIN recently_watched r ON c.id = r.channelId
        ORDER BY r.watchedAtMs DESC
        LIMIT :limit
    """)
    fun observeRecent(limit: Int): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recent: RecentlyWatchedEntity)

    /** Prune all but the most-recent [keep] entries. */
    @Query("""
        DELETE FROM recently_watched WHERE channelId NOT IN (
            SELECT channelId FROM recently_watched ORDER BY watchedAtMs DESC LIMIT :keep
        )
    """)
    suspend fun prune(keep: Int)

    @Query("DELETE FROM recently_watched")
    suspend fun deleteAll()
}
