package com.example.silentzonefinder_android.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    primaryKeys = ["user_id", "kakao_place_id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["kakao_place_id"],
            childColumns = ["kakao_place_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FavoriteEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "kakao_place_id")
    val kakaoPlaceId: String,
    
    @ColumnInfo(name = "alert_threshold_db")
    val alertThresholdDb: Double?,
    
    @ColumnInfo(name = "created_at")
    val createdAt: String?,
    
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "is_pending_sync")
    val isPendingSync: Boolean = false
)

