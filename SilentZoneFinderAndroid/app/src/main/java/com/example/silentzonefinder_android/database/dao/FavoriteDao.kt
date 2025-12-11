package com.example.silentzonefinder_android.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.silentzonefinder_android.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    
    @Query("SELECT * FROM favorites WHERE user_id = :userId AND kakao_place_id = :placeId")
    suspend fun getFavorite(userId: String, placeId: String): FavoriteEntity?
    
    @Query("SELECT * FROM favorites WHERE user_id = :userId AND kakao_place_id = :placeId")
    fun getFavoriteFlow(userId: String, placeId: String): Flow<FavoriteEntity?>
    
    @Query("SELECT * FROM favorites WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun getFavoritesByUserId(userId: String): List<FavoriteEntity>
    
    @Query("SELECT * FROM favorites WHERE user_id = :userId ORDER BY created_at DESC")
    fun getFavoritesByUserIdFlow(userId: String): Flow<List<FavoriteEntity>>
    
    @Query("SELECT * FROM favorites WHERE user_id = :userId AND kakao_place_id IN (:placeIds)")
    suspend fun getFavoritesByPlaceIds(userId: String, placeIds: List<String>): List<FavoriteEntity>
    
    @Query("SELECT * FROM favorites WHERE is_pending_sync = 1")
    suspend fun getPendingSyncFavorites(): List<FavoriteEntity>
    
    @Query("SELECT COUNT(*) FROM favorites WHERE user_id = :userId")
    suspend fun getFavoriteCountByUserId(userId: String): Int
    
    @Query("SELECT COUNT(*) FROM favorites WHERE user_id = :userId AND kakao_place_id = :placeId")
    suspend fun isFavorite(userId: String, placeId: String): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorites(favorites: List<FavoriteEntity>)
    
    @Update
    suspend fun updateFavorite(favorite: FavoriteEntity)
    
    @Query("DELETE FROM favorites WHERE user_id = :userId AND kakao_place_id = :placeId")
    suspend fun deleteFavorite(userId: String, placeId: String)
    
    @Query("DELETE FROM favorites WHERE user_id = :userId")
    suspend fun deleteFavoritesByUserId(userId: String)
    
    @Query("DELETE FROM favorites")
    suspend fun deleteAllFavorites()
}








