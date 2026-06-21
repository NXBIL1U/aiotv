package com.itrepos.aiotv.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.itrepos.aiotv.data.local.db.entity.EpgEntity

@Dao
interface EpgDao {

    @Query("SELECT * FROM epg WHERE channelId = :channelId LIMIT 1")
    suspend fun byId(channelId: String): EpgEntity?

    @Upsert
    suspend fun upsert(epg: EpgEntity)

    @Query("DELETE FROM epg")
    suspend fun deleteAll()
}
