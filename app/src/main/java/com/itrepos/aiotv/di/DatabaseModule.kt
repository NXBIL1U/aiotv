package com.itrepos.aiotv.di

import android.content.Context
import androidx.room.Room
import com.itrepos.aiotv.data.local.db.AppDatabase
import com.itrepos.aiotv.data.local.db.dao.CacheMetaDao
import com.itrepos.aiotv.data.local.db.dao.CategoryDao
import com.itrepos.aiotv.data.local.db.dao.ChannelDao
import com.itrepos.aiotv.data.local.db.dao.EpgDao
import com.itrepos.aiotv.data.local.db.dao.FavouriteDao
import com.itrepos.aiotv.data.local.db.dao.RecentlyWatchedDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "aiotv.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideChannelDao(db: AppDatabase): ChannelDao = db.channelDao()

    @Provides
    @Singleton
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    @Singleton
    fun provideEpgDao(db: AppDatabase): EpgDao = db.epgDao()

    @Provides
    @Singleton
    fun provideFavouriteDao(db: AppDatabase): FavouriteDao = db.favouriteDao()

    @Provides
    @Singleton
    fun provideRecentlyWatchedDao(db: AppDatabase): RecentlyWatchedDao = db.recentlyWatchedDao()

    @Provides
    @Singleton
    fun provideCacheMetaDao(db: AppDatabase): CacheMetaDao = db.cacheMetaDao()
}
