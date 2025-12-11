package com.example.silentzonefinder_android.notifications

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.silentzonefinder_android.data.ReviewDto
import com.example.silentzonefinder_android.utils.NotificationHelper
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ReviewNotificationWatcher(
    private val context: Context,
    private val client: SupabaseClient
) {

    companion object {
        private const val TAG = "ReviewNotificationWatcher"
        private const val PREFS_NAME = "notification_prefs"
        private const val DEFAULT_THRESHOLD_KEY = "default_threshold"
        private const val FALLBACK_THRESHOLD = 65f
    }

    private var channel: RealtimeChannel? = null
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        val address: String? = null
    )

    fun start() {
        // 이미 시작돼 있으면 다시 만들지 않음
        if (channel != null) return

        // Realtime 채널 생성 (채널 이름은 아무거나 가능)
        val ch = client.realtime.channel("reviews_listener")
        channel = ch

        // Postgres 변경사항 Flow 생성
        val changeFlow = ch.postgresChangeFlow<PostgresAction>(
            schema = "public"
        ) {
            table = "reviews"
        }

        // Flow 수집
        changeFlow.onEach { action ->
            // INSERT 이벤트만 사용
            if (action is PostgresAction.Insert) {
                // record를 ReviewDto로 디코딩
                val review = action.decodeRecord<ReviewDto>()

                // 즐겨찾기 임계값 기반으로 알림 여부 판단
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val session = client.auth.currentSessionOrNull()
                        val userId = session?.user?.id ?: return@launch

                        val favorites = client.postgrest["favorites"]
                            .select(Columns.ALL) {
                                filter {
                                    eq("user_id", userId)
                                    eq("kakao_place_id", review.kakaoPlaceId)
                                }
                            }
                            .decodeList<FavoriteDto>()

                        if (favorites.isEmpty()) {
                            Log.d(TAG, "No favorites match for place ${review.kakaoPlaceId}, skip notification")
                            return@launch
                        }

                        val threshold = favorites.firstOrNull()?.alertThresholdDb
                            ?: prefs.getFloat(DEFAULT_THRESHOLD_KEY, FALLBACK_THRESHOLD).toDouble()

                        // 장소 정보 조회 (알림/히스토리용 이름/주소)
                        val (placeName, placeAddress) = try {
                            val place = client.postgrest["places"]
                                .select(Columns.ALL) {
                                    filter { eq("kakao_place_id", review.kakaoPlaceId) }
                                    limit(1)
                                }
                                .decodeList<PlaceDto>()
                                .firstOrNull()
                            place?.name.orEmpty() to place?.address.orEmpty()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to fetch place info for ${review.kakaoPlaceId}", e)
                            "" to ""
                        }

                        if (review.noiseLevelDb <= threshold) {
                            Log.d(
                                TAG,
                                "Trigger threshold alert for place=${review.kakaoPlaceId}, noise=${review.noiseLevelDb}, threshold=$threshold"
                            )
                            NotificationHelper.showThresholdAlertNotification(
                                context = context,
                                kakaoPlaceId = review.kakaoPlaceId,
                                placeName = if (!placeName.isNullOrBlank()) placeName else "Unknown place",
                                thresholdDb = threshold,
                                detectedDb = review.noiseLevelDb
                            )
                        } else {
                            Log.d(
                                TAG,
                                "Skip alert: noise ${review.noiseLevelDb} > threshold $threshold for ${review.kakaoPlaceId}"
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process review notification", e)
                    }
                }
            }
        }.launchIn(GlobalScope)

        // subscribe 는 suspend 함수라 코루틴에서 호출
        GlobalScope.launch {
            ch.subscribe(blockUntilSubscribed = false)
        }
    }

    fun stop() {
        val ch = channel ?: return
        GlobalScope.launch {
            ch.unsubscribe()
        }
        channel = null
    }
}
