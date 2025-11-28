package com.example.silentzonefinder_android.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.silentzonefinder_android.database.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    
    @Query("SELECT * FROM places WHERE kakao_place_id = :placeId")
    suspend fun getPlaceById(placeId: String): PlaceEntity?
    
    @Query("SELECT * FROM places WHERE kakao_place_id = :placeId")
    fun getPlaceByIdFlow(placeId: String): Flow<PlaceEntity?>
    
    @Query("SELECT * FROM places WHERE kakao_place_id IN (:placeIds)")
    suspend fun getPlacesByIds(placeIds: List<String>): List<PlaceEntity>
    
    @Query("SELECT * FROM places")
    suspend fun getAllPlaces(): List<PlaceEntity>
    
    @Query("SELECT * FROM places")
    fun getAllPlacesFlow(): Flow<List<PlaceEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: PlaceEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaces(places: List<PlaceEntity>)
    
    @Update
    suspend fun updatePlace(place: PlaceEntity)
    
    @Query("DELETE FROM places WHERE kakao_place_id = :placeId")
    suspend fun deletePlace(placeId: String)
    
    @Query("DELETE FROM places")
    suspend fun deleteAllPlaces()
    
    @Query("SELECT COUNT(*) FROM places")
    suspend fun getPlaceCount(): Int
}

