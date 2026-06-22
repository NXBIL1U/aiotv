package com.itrepos.aiotv.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    // v1→2 added ChannelEntity.channelNo. A real migration (not destructive) so the upgrade
    // keeps the user's favourites + recently-watched (separate tables in the same DB).
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE channels ADD COLUMN channelNo INTEGER NOT NULL DEFAULT 0")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "aiotv.db")
            .addMigrations(MIGRATION_1_2)
            // Backstop only — explicit migrations above preserve favourites/recents on upgrade.
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
