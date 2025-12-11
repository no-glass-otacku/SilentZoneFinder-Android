package com.example.silentzonefinder_android.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.silentzonefinder_android.database.entity.ReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {
    
    @Query("SELECT * FROM reviews WHERE id = :reviewId")
    suspend fun getReviewById(reviewId: Long): ReviewEntity?
    
    @Query("SELECT * FROM reviews WHERE id = :reviewId")
    fun getReviewByIdFlow(reviewId: Long): Flow<ReviewEntity?>
    
    @Query("SELECT * FROM reviews WHERE kakao_place_id = :placeId ORDER BY created_at DESC")
    suspend fun getReviewsByPlaceId(placeId: String): List<ReviewEntity>
    
    @Query("SELECT * FROM reviews WHERE kakao_place_id = :placeId ORDER BY created_at DESC")
    fun getReviewsByPlaceIdFlow(placeId: String): Flow<List<ReviewEntity>>
    
    @Query("SELECT * FROM reviews WHERE user_id = :userId ORDER BY created_at DESC")
    suspend fun getReviewsByUserId(userId: String): List<ReviewEntity>
    
    @Query("SELECT * FROM reviews WHERE user_id = :userId ORDER BY created_at DESC")
    fun getReviewsByUserIdFlow(userId: String): Flow<List<ReviewEntity>>
    
    @Query("SELECT * FROM reviews WHERE kakao_place_id IN (:placeIds) ORDER BY created_at DESC")
    suspend fun getReviewsByPlaceIds(placeIds: List<String>): List<ReviewEntity>
    
    @Query("SELECT * FROM reviews WHERE is_pending_sync = 1")
    suspend fun getPendingSyncReviews(): List<ReviewEntity>
    
    @Query("SELECT AVG(noise_level_db) FROM reviews WHERE kakao_place_id = :placeId")
    suspend fun getAverageNoiseLevel(placeId: String): Double?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: ReviewEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviews(reviews: List<ReviewEntity>)
    
    @Update
    suspend fun updateReview(review: ReviewEntity)
    
    @Query("DELETE FROM reviews WHERE id = :reviewId")
    suspend fun deleteReview(reviewId: Long)
    
    @Query("DELETE FROM reviews WHERE user_id = :userId")
    suspend fun deleteReviewsByUserId(userId: String)
    
    @Query("DELETE FROM reviews WHERE kakao_place_id = :placeId")
    suspend fun deleteReviewsByPlaceId(placeId: String)
    
    @Query("DELETE FROM reviews")
    suspend fun deleteAllReviews()
    
    @Query("SELECT COUNT(*) FROM reviews WHERE kakao_place_id = :placeId")
    suspend fun getReviewCountByPlaceId(placeId: String): Int
    
    @Query("SELECT COUNT(*) FROM reviews WHERE user_id = :userId")
    suspend fun getReviewCountByUserId(userId: String): Int
}








