package com.example.silentzonefinder_android.database.repository

import com.example.silentzonefinder_android.database.dao.FavoriteDao
import com.example.silentzonefinder_android.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

class FavoriteRepository(private val favoriteDao: FavoriteDao) {
    
    suspend fun getFavorite(userId: String, placeId: String): FavoriteEntity? {
        return favoriteDao.getFavorite(userId, placeId)
    }
    
    fun getFavoriteFlow(userId: String, placeId: String): Flow<FavoriteEntity?> {
        return favoriteDao.getFavoriteFlow(userId, placeId)
    }
    
    suspend fun getFavoritesByUserId(userId: String): List<FavoriteEntity> {
        return favoriteDao.getFavoritesByUserId(userId)
    }
    
    fun getFavoritesByUserIdFlow(userId: String): Flow<List<FavoriteEntity>> {
        return favoriteDao.getFavoritesByUserIdFlow(userId)
    }
    
    suspend fun getFavoritesByPlaceIds(userId: String, placeIds: List<String>): List<FavoriteEntity> {
        return favoriteDao.getFavoritesByPlaceIds(userId, placeIds)
    }
    
    suspend fun getPendingSyncFavorites(): List<FavoriteEntity> {
        return favoriteDao.getPendingSyncFavorites()
    }
    
    suspend fun getFavoriteCountByUserId(userId: String): Int {
        return favoriteDao.getFavoriteCountByUserId(userId)
    }
    
    suspend fun isFavorite(userId: String, placeId: String): Boolean {
        return favoriteDao.isFavorite(userId, placeId) > 0
    }
    
    suspend fun insertFavorite(favorite: FavoriteEntity) {
        favoriteDao.insertFavorite(favorite)
    }
    
    suspend fun insertFavorites(favorites: List<FavoriteEntity>) {
        favoriteDao.insertFavorites(favorites)
    }
    
    suspend fun updateFavorite(favorite: FavoriteEntity) {
        favoriteDao.updateFavorite(favorite)
    }
    
    suspend fun deleteFavorite(userId: String, placeId: String) {
        favoriteDao.deleteFavorite(userId, placeId)
    }
    
    suspend fun deleteFavoritesByUserId(userId: String) {
        favoriteDao.deleteFavoritesByUserId(userId)
    }
    
    suspend fun deleteAllFavorites() {
        favoriteDao.deleteAllFavorites()
    }
}

