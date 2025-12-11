package com.example.silentzonefinder_android.database.repository

import com.example.silentzonefinder_android.database.dao.ReviewDao
import com.example.silentzonefinder_android.database.entity.ReviewEntity
import kotlinx.coroutines.flow.Flow

class ReviewRepository(private val reviewDao: ReviewDao) {
    
    suspend fun getReviewById(reviewId: Long): ReviewEntity? {
        return reviewDao.getReviewById(reviewId)
    }
    
    fun getReviewByIdFlow(reviewId: Long): Flow<ReviewEntity?> {
        return reviewDao.getReviewByIdFlow(reviewId)
    }
    
    suspend fun getReviewsByPlaceId(placeId: String): List<ReviewEntity> {
        return reviewDao.getReviewsByPlaceId(placeId)
    }
    
    fun getReviewsByPlaceIdFlow(placeId: String): Flow<List<ReviewEntity>> {
        return reviewDao.getReviewsByPlaceIdFlow(placeId)
    }
    
    suspend fun getReviewsByUserId(userId: String): List<ReviewEntity> {
        return reviewDao.getReviewsByUserId(userId)
    }
    
    fun getReviewsByUserIdFlow(userId: String): Flow<List<ReviewEntity>> {
        return reviewDao.getReviewsByUserIdFlow(userId)
    }
    
    suspend fun getReviewsByPlaceIds(placeIds: List<String>): List<ReviewEntity> {
        return reviewDao.getReviewsByPlaceIds(placeIds)
    }
    
    suspend fun getPendingSyncReviews(): List<ReviewEntity> {
        return reviewDao.getPendingSyncReviews()
    }
    
    suspend fun getAverageNoiseLevel(placeId: String): Double? {
        return reviewDao.getAverageNoiseLevel(placeId)
    }
    
    suspend fun insertReview(review: ReviewEntity) {
        reviewDao.insertReview(review)
    }
    
    suspend fun insertReviews(reviews: List<ReviewEntity>) {
        reviewDao.insertReviews(reviews)
    }
    
    suspend fun updateReview(review: ReviewEntity) {
        reviewDao.updateReview(review)
    }
    
    suspend fun deleteReview(reviewId: Long) {
        reviewDao.deleteReview(reviewId)
    }
    
    suspend fun deleteReviewsByUserId(userId: String) {
        reviewDao.deleteReviewsByUserId(userId)
    }
    
    suspend fun deleteReviewsByPlaceId(placeId: String) {
        reviewDao.deleteReviewsByPlaceId(placeId)
    }
    
    suspend fun deleteAllReviews() {
        reviewDao.deleteAllReviews()
    }
    
    suspend fun getReviewCountByPlaceId(placeId: String): Int {
        return reviewDao.getReviewCountByPlaceId(placeId)
    }
    
    suspend fun getReviewCountByUserId(userId: String): Int {
        return reviewDao.getReviewCountByUserId(userId)
    }
}








