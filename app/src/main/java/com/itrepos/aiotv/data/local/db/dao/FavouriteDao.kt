package com.itrepos.aiotv.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.itrepos.aiotv.data.local.db.entity.CategoryEntity
import com.itrepos.aiotv.data.local.db.entity.ChannelEntity
import com.itrepos.aiotv.data.local.db.entity.FavouriteCategoryEntity
import com.itrepos.aiotv.data.local.db.entity.FavouriteChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouriteDao {

    @Query("""
        SELECT c.* FROM channels c
        INNER JOIN favourite_channels f ON c.id = f.channelId
        ORDER BY f.addedAtMs DESC
    """)
    fun observeFavChannels(): Flow<List<ChannelEntity>>

    @Query("""
        SELECT cat.* FROM categories cat
        INNER JOIN favourite_categories f ON cat.id = f.categoryId
        ORDER BY f.addedAtMs DESC
    """)
    fun observeFavCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) > 0 FROM favourite_channels WHERE channelId = :channelId")
    fun isChannelFav(channelId: String): Flow<Boolean>

    @Query("SELECT COUNT(*) > 0 FROM favourite_categories WHERE categoryId = :categoryId")
    fun isCategoryFav(categoryId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavChannel(fav: FavouriteChannelEntity)

    @Query("DELETE FROM favourite_channels WHERE channelId = :channelId")
    suspend fun deleteFavChannel(channelId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavCategory(fav: FavouriteCategoryEntity)

    @Query("DELETE FROM favourite_categories WHERE categoryId = :categoryId")
    suspend fun deleteFavCategory(categoryId: String)

    @Query("DELETE FROM favourite_channels")
    suspend fun deleteAllFavChannels()

    @Query("DELETE FROM favourite_categories")
    suspend fun deleteAllFavCategories()
}
