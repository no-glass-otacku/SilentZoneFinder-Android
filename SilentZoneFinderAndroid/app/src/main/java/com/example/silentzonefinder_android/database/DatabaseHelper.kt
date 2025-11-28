package com.example.silentzonefinder_android.database

import android.content.Context
import com.example.silentzonefinder_android.database.repository.FavoriteRepository
import com.example.silentzonefinder_android.database.repository.PlaceRepository
import com.example.silentzonefinder_android.database.repository.ReviewRepository

/**
 * Room Database 사용을 위한 헬퍼 클래스
 * 
 * 사용 예시:
 * ```kotlin
 * val database = SilentZoneDatabase.getDatabase(context)
 * val placeRepository = PlaceRepository(database.placeDao())
 * 
 * // 장소 저장
 * lifecycleScope.launch {
 *     val place = PlaceEntity(
 *         kakaoPlaceId = "12345",
 *         name = "카페",
 *         address = "서울시",
 *         lat = 37.5,
 *         lng = 127.0,
 *         noiseLevelDb = 45.0,
 *         createdAt = "2024-01-01"
 *     )
 *     placeRepository.insertPlace(place)
 * }
 * 
 * // 장소 조회 (Flow 사용)
 * placeRepository.getPlaceByIdFlow("12345")
 *     .collect { place ->
 *         // UI 업데이트
 *     }
 * ```
 */
object DatabaseHelper {
    
    fun getPlaceRepository(context: Context): PlaceRepository {
        val database = SilentZoneDatabase.getDatabase(context)
        return PlaceRepository(database.placeDao())
    }
    
    fun getReviewRepository(context: Context): ReviewRepository {
        val database = SilentZoneDatabase.getDatabase(context)
        return ReviewRepository(database.reviewDao())
    }
    
    fun getFavoriteRepository(context: Context): FavoriteRepository {
        val database = SilentZoneDatabase.getDatabase(context)
        return FavoriteRepository(database.favoriteDao())
    }
}

