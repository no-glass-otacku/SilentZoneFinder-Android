package com.example.silentzonefinder_android.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey
    @ColumnInfo(name = "kakao_place_id")
    val kakaoPlaceId: String,
    
    @ColumnInfo(name = "name")
    val name: String?,
    
    @ColumnInfo(name = "address")
    val address: String?,
    
    @ColumnInfo(name = "lat")
    val lat: Double?,
    
    @ColumnInfo(name = "lng")
    val lng: Double?,
    
    @ColumnInfo(name = "noise_level_db")
    val noiseLevelDb: Double?,
    
    @ColumnInfo(name = "created_at")
    val createdAt: String?,
    
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = System.currentTimeMillis()
)








