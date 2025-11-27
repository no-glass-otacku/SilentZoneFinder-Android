package com.example.silentzonefinder_android.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.silentzonefinder_android.SupabaseManager
import com.example.silentzonefinder_android.utils.NotificationHelper
import com.example.silentzonefinder_android.utils.PermissionHelper
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class QuietZoneWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "QuietZoneWorker"
        private const val QUIET_THRESHOLD_DB = 60.0 // 조용한 장소 기준 (60 dB 이하)
        private const val SEARCH_RADIUS_M = 1000.0 // 1km 반경
    }

    @Serializable
    private data class FavoriteDto(
        @SerialName("user_id") val userId: String,
        @SerialName("kakao_place_id") val kakaoPlaceId: String,
        @SerialName("alert_threshold_db") val alertThresholdDb: Double? = null
    )

    @Serializable
    private data class PlaceDto(
        @SerialName("kakao_place_id") val kakaoPlaceId: String,
        val name: String? = null,
        val address: String? = null,
        val lat: Double? = null,
        val lng: Double? = null
    )

    @Serializable
    private data class ReviewDto(
        @SerialName("kakao_place_id") val kakaoPlaceId: String,
        @SerialName("noise_level_db") val noiseLevelDb: Double,
        @SerialName("created_at") val createdAt: String
    )

    override suspend fun doWork(): Result {
        return try {
            // 알림 권한 확인
            if (!PermissionHelper.hasNotificationPermission(applicationContext)) {
                Log.d(TAG, "Notification permission not granted, skipping work")
                return Result.success()
            }

            // 로그인 상태 확인
            val session = withContext(Dispatchers.IO) {
                SupabaseManager.client.auth.currentSessionOrNull()
            }

            if (session == null) {
                Log.d(TAG, "User not logged in, skipping work")
                return Result.success()
            }

            val userId = session.user?.id?.toString().orEmpty()
            if (userId.isEmpty()) {
                Log.d(TAG, "User info missing in session, skipping work")
                return Result.success()
            }

            // 사용자의 즐겨찾기 목록 가져오기
            val favorites = withContext(Dispatchers.IO) {
                try {
                    SupabaseManager.client.postgrest["favorites"]
                        .select(Columns.ALL) {
                            filter {
                                eq("user_id", userId)
                            }
                        }
                        .decodeList<FavoriteDto>()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch favorites", e)
                    emptyList()
                }
            }

            if (favorites.isEmpty()) {
                Log.d(TAG, "No favorites found, skipping work")
                return Result.success()
            }

            // 각 즐겨찾기 장소의 최근 리뷰 평균 소음 확인
            for (favorite in favorites) {
                try {
                    val recentReviews = withContext(Dispatchers.IO) {
                        try {
                            // 최근 1시간 이내 리뷰만 조회
                            SupabaseManager.client.postgrest["reviews"]
                                .select(Columns.ALL) {
                                    filter {
                                        eq("kakao_place_id", favorite.kakaoPlaceId)
                                    }
                                    order("created_at", order = Order.DESCENDING)
                                    limit(10)
                                }
                                .decodeList<ReviewDto>()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch reviews for ${favorite.kakaoPlaceId}", e)
                            emptyList()
                        }
                    }

                    if (recentReviews.isEmpty()) continue

                    // 평균 소음 계산
                    val avgNoise = recentReviews.map { it.noiseLevelDb }.average()

                    // 장소 정보 가져오기
                    val place = withContext(Dispatchers.IO) {
                        try {
                            SupabaseManager.client.postgrest["places"]
                                .select(Columns.ALL) {
                                    filter {
                                        eq("kakao_place_id", favorite.kakaoPlaceId)
                                    }
                                }
                                .decodeSingle<PlaceDto>()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to fetch place info", e)
                            null
                        }
                    }

                    if (place == null) continue

                    // 조용한 장소 추천 알림 (60 dB 이하)
                    if (avgNoise <= QUIET_THRESHOLD_DB) {
                        NotificationHelper.showQuietZoneNotification(
                            applicationContext,
                            place.name ?: "알 수 없는 장소",
                            place.address ?: "",
                            avgNoise,
                            favorite.kakaoPlaceId
                        )
                        Log.d(TAG, "Sent quiet zone notification for ${place.name}")
                    }

                    // 임계값 초과 알림
                    val threshold = favorite.alertThresholdDb ?: continue
                    if (avgNoise > threshold) {
                        NotificationHelper.showThresholdAlertNotification(
                            applicationContext,
                            place.name ?: "알 수 없는 장소",
                            place.address ?: "",
                            threshold,
                            avgNoise,
                            favorite.kakaoPlaceId
                        )
                        Log.d(TAG, "Sent threshold alert for ${place.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing favorite ${favorite.kakaoPlaceId}", e)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            Result.retry()
        }
    }
}




