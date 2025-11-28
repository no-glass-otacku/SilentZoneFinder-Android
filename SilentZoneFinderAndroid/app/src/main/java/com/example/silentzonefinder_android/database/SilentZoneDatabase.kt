package com.example.silentzonefinder_android.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.silentzonefinder_android.database.converter.StringListConverter
import com.example.silentzonefinder_android.database.dao.FavoriteDao
import com.example.silentzonefinder_android.database.dao.PlaceDao
import com.example.silentzonefinder_android.database.dao.ReviewDao
import com.example.silentzonefinder_android.database.entity.FavoriteEntity
import com.example.silentzonefinder_android.database.entity.PlaceEntity
import com.example.silentzonefinder_android.database.entity.ReviewEntity

@Database(
    entities = [
        PlaceEntity::class,
        ReviewEntity::class,
        FavoriteEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class SilentZoneDatabase : RoomDatabase() {
    
    abstract fun placeDao(): PlaceDao
    abstract fun reviewDao(): ReviewDao
    abstract fun favoriteDao(): FavoriteDao
    
    companion object {
        @Volatile
        private var INSTANCE: SilentZoneDatabase? = null
        
        fun getDatabase(context: Context): SilentZoneDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SilentZoneDatabase::class.java,
                    "silent_zone_database"
                )
                    .fallbackToDestructiveMigration() // 개발 중에만 사용 (스키마 변경 시 데이터 삭제)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

