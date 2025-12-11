package com.example.silentzonefinder_android.database.repository

import com.example.silentzonefinder_android.database.dao.PlaceDao
import com.example.silentzonefinder_android.database.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow

class PlaceRepository(private val placeDao: PlaceDao) {
    
    suspend fun getPlaceById(placeId: String): PlaceEntity? {
        return placeDao.getPlaceById(placeId)
    }
    
    fun getPlaceByIdFlow(placeId: String): Flow<PlaceEntity?> {
        return placeDao.getPlaceByIdFlow(placeId)
    }
    
    suspend fun getPlacesByIds(placeIds: List<String>): List<PlaceEntity> {
        return placeDao.getPlacesByIds(placeIds)
    }
    
    suspend fun getAllPlaces(): List<PlaceEntity> {
        return placeDao.getAllPlaces()
    }
    
    fun getAllPlacesFlow(): Flow<List<PlaceEntity>> {
        return placeDao.getAllPlacesFlow()
    }
    
    suspend fun insertPlace(place: PlaceEntity) {
        placeDao.insertPlace(place)
    }
    
    suspend fun insertPlaces(places: List<PlaceEntity>) {
        placeDao.insertPlaces(places)
    }
    
    suspend fun updatePlace(place: PlaceEntity) {
        placeDao.updatePlace(place)
    }
    
    suspend fun deletePlace(placeId: String) {
        placeDao.deletePlace(placeId)
    }
    
    suspend fun deleteAllPlaces() {
        placeDao.deleteAllPlaces()
    }
    
    suspend fun getPlaceCount(): Int {
        return placeDao.getPlaceCount()
    }
}








