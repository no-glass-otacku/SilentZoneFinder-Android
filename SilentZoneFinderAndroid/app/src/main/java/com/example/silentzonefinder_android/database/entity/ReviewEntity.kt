package com.example.silentzonefinder_android.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.silentzonefinder_android.database.converter.StringListConverter

@Entity(
    tableName = "reviews",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["kakao_place_id"],
            childColumns = ["kakao_place_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
@TypeConverters(StringListConverter::class)
data class ReviewEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,
    
    @ColumnInfo(name = "kakao_place_id")
    val kakaoPlaceId: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "rating")
    val rating: Int,
    
    @ColumnInfo(name = "text")
    val text: String?,
    
    @ColumnInfo(name = "images")
    val images: List<String>?,
    
    @ColumnInfo(name = "noise_level_db")
    val noiseLevelDb: Double,
    
    @ColumnInfo(name = "created_at")
    val createdAt: String?,
    
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "is_pending_sync")
    val isPendingSync: Boolean = false
)








