package com.itrepos.aiotv.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.itrepos.aiotv.data.local.db.entity.CacheMetaEntity

@Dao
interface CacheMetaDao {

    @Query("SELECT * FROM cache_meta WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): CacheMetaEntity?

    @Upsert
    suspend fun upsert(meta: CacheMetaEntity)

    @Query("DELETE FROM cache_meta")
    suspend fun deleteAll()
}
