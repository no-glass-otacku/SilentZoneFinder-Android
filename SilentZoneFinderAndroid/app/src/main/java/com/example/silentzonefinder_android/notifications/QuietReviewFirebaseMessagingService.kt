package com.example.silentzonefinder_android.notifications

import android.util.Log
import com.example.silentzonefinder_android.SupabaseManager
import com.example.silentzonefinder_android.data.ReviewDto
import com.example.silentzonefinder_android.utils.NotificationHistoryManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QuietReviewFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "QuietReviewFcmService"

        /**
         * 로그인 직후 등, 이미 만들어진 FCM 토큰을 Supabase에 동기화할 때 쓰는 헬퍼
         */
        fun registerCurrentTokenToSupabase(token: String) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val session =
                        SupabaseManager.client.auth.currentSessionOrNull() ?: return@launch
                    val userId = session.user?.id?.toString() ?: return@launch

                    SupabaseManager.client.postgrest["user_devices"].upsert(
                        mapOf(
                            "user_id" to userId,
                            "fcm_token" to token
                        )
                    )

                    Log.d(TAG, "user_devices upsert success (login): $userId")
                } catch (e: Exception) {
                    Log.e(TAG, "user_devices upsert failed (login)", e)
                }
            }
        }
    }

    /**
     * 토큰이 새로 발급되거나 갱신될 때마다 호출되는 콜백
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM onNewToken: $token")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session =
                    SupabaseManager.client.auth.currentSessionOrNull() ?: return@launch
                val userId = session.user?.id?.toString() ?: return@launch

                SupabaseManager.client.postgrest["user_devices"].upsert(
                    mapOf(
                        "user_id" to userId,
                        "fcm_token" to token
                    )
                )

                Log.d(TAG, "user_devices upsert success (onNewToken): $userId")
            } catch (e: Exception) {
                Log.e(TAG, "user_devices upsert failed (onNewToken)", e)
            }
        }
    }

    /**
     * Edge Function → FCM → 단말로 온 알림 처리
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        Log.d(TAG, "onMessageReceived data: $data")

        // Edge Function에서 data에 넣어주는 키 이름에 맞춰서 파싱
        val review = ReviewDto(
            id = data["review_id"]?.toIntOrNull() ?: 0,
            kakaoPlaceId = data["kakao_place_id"] ?: "",
            rating = data["rating"]?.toIntOrNull() ?: 0,
            text = data["text"],
            images = null,
            noiseLevelDb = data["noise_level_db"]?.toDoubleOrNull() ?: 0.0,
            createdAt = data["created_at"],
            userId = data["user_id"],
            amenities = null
        )

        // 기존에 있던 알림 헬퍼 재사용
        NotificationHelper.showNewReviewNotification(
            context = applicationContext,
            review = review
        )

        // 알림 히스토리 화면에 보여주고 싶으면 기록도 같이 남김
        //NotificationHistoryManager.addNotification(
            //context = applicationContext,
            //placeId = review.kakaoPlaceId,
            //placeName = data["place_name"] ?: "조용한 장소",
            //reviewText = review.text ?: "",
            //noiseLevelDb = review.noiseLevelDb,
            //createdAt = review.createdAt ?: ""
        //)
    }
}
