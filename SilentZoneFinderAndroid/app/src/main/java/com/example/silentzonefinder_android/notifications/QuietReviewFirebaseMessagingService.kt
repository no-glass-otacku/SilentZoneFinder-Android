package com.example.silentzonefinder_android.notifications

import android.util.Log
import com.example.silentzonefinder_android.SupabaseManager
import com.example.silentzonefinder_android.data.ReviewDto
import com.example.silentzonefinder_android.utils.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QuietReviewFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "QuietReviewFcmService"

        /**
         * 로그인 직후 userId + token 을 확실하게 등록하는 함수
         */
        fun registerTokenForUser(userId: String, token: String) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d(TAG, "registerTokenForUser: userId=$userId token=$token")

                    SupabaseManager.client.postgrest["user_devices"].upsert(
                        mapOf(
                            "user_id" to userId,
                            "fcm_token" to token
                        )
                    )

                    Log.d(TAG, "user_devices upsert success (registerTokenForUser)")
                } catch (e: Exception) {
                    Log.e(TAG, "user_devices upsert failed (registerTokenForUser)", e)
                }
            }
        }

        /**
         * 기존 onNewToken 기반 방식
         */
        fun registerCurrentTokenToSupabase(token: String) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val session = SupabaseManager.client.auth.currentSessionOrNull()
                    val userId = session?.user?.id ?: return@launch

                    Log.d(TAG, "registerCurrentTokenToSupabase: $token (userId=$userId)")

                    SupabaseManager.client.postgrest["user_devices"].upsert(
                        mapOf(
                            "user_id" to userId.toString(),
                            "fcm_token" to token
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "registerCurrentTokenToSupabase failed", e)
                }
            }
        }
    }

    /**
     * FCM 토큰 갱신 시 자동 호출
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM onNewToken: $token")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val session = SupabaseManager.client.auth.currentSessionOrNull()
                val userId = session?.user?.id ?: return@launch

                SupabaseManager.client.postgrest["user_devices"].upsert(
                    mapOf(
                        "user_id" to userId.toString(),
                        "fcm_token" to token
                    )
                )

                Log.d(TAG, "user_devices upsert success (onNewToken)")
            } catch (e: Exception) {
                Log.e(TAG, "user_devices upsert failed (onNewToken)", e)
            }
        }
    }

    /**
     * 메시지 수신
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        Log.d(TAG, "onMessageReceived data: $data")

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

        NotificationHelper.showNewReviewNotification(
            context = applicationContext,
            review = review
        )
    }
}
