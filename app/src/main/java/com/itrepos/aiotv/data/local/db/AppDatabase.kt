package com.itrepos.aiotv.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.itrepos.aiotv.data.local.db.dao.CacheMetaDao
import com.itrepos.aiotv.data.local.db.dao.CategoryDao
import com.itrepos.aiotv.data.local.db.dao.ChannelDao
import com.itrepos.aiotv.data.local.db.dao.EpgDao
import com.itrepos.aiotv.data.local.db.dao.FavouriteDao
import com.itrepos.aiotv.data.local.db.dao.RecentlyWatchedDao
import com.itrepos.aiotv.data.local.db.entity.CacheMetaEntity
import com.itrepos.aiotv.data.local.db.entity.CategoryEntity
import com.itrepos.aiotv.data.local.db.entity.ChannelEntity
import com.itrepos.aiotv.data.local.db.entity.EpgEntity
import com.itrepos.aiotv.data.local.db.entity.FavouriteCategoryEntity
import com.itrepos.aiotv.data.local.db.entity.FavouriteChannelEntity
import com.itrepos.aiotv.data.local.db.entity.RecentlyWatchedEntity

@Database(
    entities = [
        ChannelEntity::class,
        CategoryEntity::class,
        EpgEntity::class,
        FavouriteChannelEntity::class,
        FavouriteCategoryEntity::class,
        RecentlyWatchedEntity::class,
        CacheMetaEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun categoryDao(): CategoryDao
    abstract fun epgDao(): EpgDao
    abstract fun favouriteDao(): FavouriteDao
    abstract fun recentlyWatchedDao(): RecentlyWatchedDao
    abstract fun cacheMetaDao(): CacheMetaDao
}
